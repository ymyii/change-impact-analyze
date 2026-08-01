package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.build.BuildResult;
import io.github.dependencyanalysis.build.BuildRunner;
import io.github.dependencyanalysis.build.ModuleBuildOutput;
import io.github.dependencyanalysis.bytecode.BytecodeDiffEngine;
import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.bytecode.MemberDescriptors;
import io.github.dependencyanalysis.callgraph.CallGraphException;
import io.github.dependencyanalysis.callgraph.ModuleCallGraphEngine;
import io.github.dependencyanalysis.callgraph.ModuleCallGraphSession;
import io.github.dependencyanalysis.callgraph.ModuleScopeValidator;
import io.github.dependencyanalysis.callgraph.ScopeValidationException;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ChangeType;
import io.github.dependencyanalysis.dependency.DependencyAnalysisResult;
import io.github.dependencyanalysis.dependency.DependencyAnalyzer;
import io.github.dependencyanalysis.dependency.DependencyChange;
import io.github.dependencyanalysis.dependency.DependencyDiffEngine;
import io.github.dependencyanalysis.dependency.DependencyNode;
import io.github.dependencyanalysis.dependency.ModuleDependencyTree;
import io.github.dependencyanalysis.dependency.ResolvedArtifact;
import io.github.dependencyanalysis.diagnostic.DiagnosticCollector;
import io.github.dependencyanalysis.jar.JarLocationResult;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.MavenRuntimeDescriptor;
import io.github.dependencyanalysis.workspace.WorkspaceResult;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Executes the Spring backend per-module Vanilla 0-1-CFA pipeline. */
final class PerModuleImpactPipeline {

    /** Maximum wait for canceled preparation tasks to release processes. */
    private static final long PREPARATION_SHUTDOWN_SECONDS = 30L;

    /** Diagnostics. */
    private final DiagnosticCollector diagnostics;

    /** Included bytecode changes. */
    private final Set<ChangePointKind> kinds;

    /** Maven runtime. */
    private final MavenRuntimeDescriptor mavenRuntime;

    /** Safe Maven arguments. */
    private final List<String> mavenArguments;

    /** Target JDK. */
    private final JavaRuntimeDescriptor javaRuntime;

    /** Module Call Graph timeout. */
    private final long callGraphTimeoutSeconds;

    /** Configured module parallelism. */
    private final int moduleParallelism;

    /** Command temporary directory. */
    private final Path temporaryDirectory;

    /**
     * Creates the per-module pipeline.
     *
     * @param collector diagnostics
     * @param includedKinds bytecode changes
     * @param runtime Maven runtime
     * @param arguments Maven arguments
     * @param targetJava target JDK
     * @param options runtime controls
     */
    PerModuleImpactPipeline(
            final DiagnosticCollector collector,
            final Set<ChangePointKind> includedKinds,
            final MavenRuntimeDescriptor runtime,
            final List<String> arguments,
            final JavaRuntimeDescriptor targetJava,
            final PerModulePipelineOptions options) {
        diagnostics = Objects.requireNonNull(collector, "collector");
        kinds = Set.copyOf(includedKinds);
        mavenRuntime = Objects.requireNonNull(runtime, "mavenRuntime");
        mavenArguments = List.copyOf(arguments);
        javaRuntime = Objects.requireNonNull(targetJava, "javaRuntime");
        callGraphTimeoutSeconds = options.callGraphTimeoutSeconds();
        moduleParallelism = options.moduleParallelism();
        temporaryDirectory = options.temporaryDirectory();
    }

    /**
     * Runs preparation, parallel module analysis, and serial SSA filtering.
     *
     * @param workspace prepared Git workspaces
     * @return complete analysis run
     * @throws Exception on global preparation failure
     */
    AnalysisRunResult run(final WorkspaceResult workspace) throws Exception {
        final Map<String, Long> elapsed = new LinkedHashMap<>();
        final long planningStart = System.currentTimeMillis();
        final ModuleScopePlanner planner = new ModuleScopePlanner();
        final ReactorAnalysisScope baselineScope = planner.plan(
                workspace.getBaseline().getPath(), mavenArguments);
        final ReactorAnalysisScope targetScope = planner.plan(
                workspace.getTarget().getPath(), mavenArguments);
        elapsed.put("scope-planning",
                System.currentTimeMillis() - planningStart);
        if (targetScope.getMode() != baselineScope.getMode()) {
            diagnostics.warn("pipeline",
                    "Baseline and target analysis modes differ; "
                            + "target mode controls reporting");
        }
        final long frontStart = System.currentTimeMillis();
        final FrontPreparation front = prepareFront(
                baselineScope, targetScope);
        elapsed.put("front-parallel",
                System.currentTimeMillis() - frontStart);
        elapsed.put("baseline-dependency",
                front.baselineDependencyMillis());
        elapsed.put("target-build", front.targetBuildMillis());
        final long targetDependencyStart = System.currentTimeMillis();
        final DependencyAnalysisResult targetDependencies =
                dependency("target", targetScope).analyzeResolved();
        elapsed.put("target-dependency",
                System.currentTimeMillis() - targetDependencyStart);
        final List<ModuleDependencyTree> baselineTrees = selectedTrees(
                front.baselineDependencies().getTrees(), baselineScope);
        final List<ModuleDependencyTree> targetTrees = selectedTrees(
                targetDependencies.getTrees(), targetScope);
        final Set<String> reactorKeys = new LinkedHashSet<>();
        baselineScope.getAllModules().forEach(module ->
                reactorKeys.add(module.coordinateKey()));
        targetScope.getAllModules().forEach(module ->
                reactorKeys.add(module.coordinateKey()));
        final List<DependencyChange> changes =
                new DependencyDiffEngine().diff(
                        externalTrees(baselineTrees, reactorKeys),
                        externalTrees(targetTrees, reactorKeys));
        final long diffStart = System.currentTimeMillis();
        final BindingResult bindings = bindAndDiff(changes,
                baselineScope, targetScope,
                front.baselineDependencies(), targetDependencies,
                baselineTrees, targetTrees);
        elapsed.put("jar-diff", System.currentTimeMillis() - diffStart);
        final List<ModuleAnalysisUnit> units = units(
                new PreparedAnalysis(baselineScope, targetScope,
                        front.targetBuild(), front.baselineDependencies(),
                        targetDependencies, baselineTrees, targetTrees),
                bindings);
        final int relevantCount = (int) units.stream()
                .filter(unit -> !unit.getChangePoints().isEmpty()
                        || bindings.failedModules().contains(
                        unit.getModuleId().coordinateKey()))
                .count();
        final int actualParallelism = relevantCount == 0 ? 0
                : Math.min(moduleParallelism, relevantCount);
        if (moduleParallelism
                > Runtime.getRuntime().availableProcessors()) {
            diagnostics.warn("module-analysis",
                    "--module-parallelism exceeds availableProcessors: "
                            + moduleParallelism);
        }
        final long modulesStart = System.currentTimeMillis();
        final List<ModuleAnalysisResult> analyzed = analyzeModules(
                units, bindings.failedModules(), actualParallelism);
        elapsed.put("module-analysis",
                System.currentTimeMillis() - modulesStart);
        final long ssaStart = System.currentTimeMillis();
        final List<ModuleAnalysisResult> filtered =
                new SsaEquivalenceEngine(diagnostics, javaRuntime)
                        .filter(analyzed);
        elapsed.put("ssa-equivalence",
                System.currentTimeMillis() - ssaStart);
        return new AnalysisRunResult(targetScope.getMode(),
                overallStatus(filtered), changes,
                filtered, new AnalysisConcurrency(
                        moduleParallelism, actualParallelism,
                        bindings.configuredWorkers(),
                        bindings.actualWorkers()), elapsed);
    }

    private FrontPreparation prepareFront(
            final ReactorAnalysisScope baselineScope,
            final ReactorAnalysisScope targetScope) throws Exception {
        final ExecutorService executor = Executors.newFixedThreadPool(2);
        final CompletionService<PreparationBranch> completion =
                new ExecutorCompletionService<>(executor);
        final List<Future<PreparationBranch>> futures = new ArrayList<>();
        futures.add(completion.submit(() -> prepareBaseline(
                baselineScope)));
        futures.add(completion.submit(() -> prepareTarget(
                targetScope)));
        DependencyAnalysisResult baseline = null;
        BuildResult targetBuild = null;
        long baselineMillis = 0L;
        long targetBuildMillis = 0L;
        try {
            for (int index = 0; index < futures.size(); index++) {
                final PreparationBranch branch = completion.take().get();
                if (branch.dependencies() != null) {
                    baseline = branch.dependencies();
                    baselineMillis = branch.elapsedMillis();
                }
                if (branch.build() != null) {
                    targetBuild = branch.build();
                    targetBuildMillis = branch.elapsedMillis();
                }
            }
        } catch (InterruptedException exception) {
            futures.forEach(value -> value.cancel(true));
            Thread.currentThread().interrupt();
            throw exception;
        } catch (ExecutionException exception) {
            futures.forEach(value -> value.cancel(true));
            final Throwable cause = exception.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw new IllegalStateException("Preparation failed", cause);
        } finally {
            executor.shutdownNow();
            awaitPreparationShutdown(executor);
        }
        if (baseline == null || targetBuild == null) {
            throw new IllegalStateException(
                    "Incomplete parallel preparation result");
        }
        return new FrontPreparation(baseline, targetBuild,
                baselineMillis, targetBuildMillis);
    }

    private PreparationBranch prepareBaseline(
            final ReactorAnalysisScope scope) throws Exception {
        final long start = System.currentTimeMillis();
        return new PreparationBranch("baseline-dependency",
                dependency("baseline", scope).analyzeResolved(), null,
                System.currentTimeMillis() - start);
    }

    private PreparationBranch prepareTarget(
            final ReactorAnalysisScope scope) throws Exception {
        final long start = System.currentTimeMillis();
        return new PreparationBranch("target-build", null, build(scope),
                System.currentTimeMillis() - start);
    }

    private void awaitPreparationShutdown(
            final ExecutorService executor) {
        boolean interrupted = false;
        try {
            final long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(
                    PREPARATION_SHUTDOWN_SECONDS);
            while (!executor.isTerminated()
                    && System.nanoTime() < deadline) {
                try {
                    executor.awaitTermination(1L, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (!executor.isTerminated()) {
                diagnostics.warn("front-parallel",
                        "Preparation task did not terminate within "
                                + PREPARATION_SHUTDOWN_SECONDS + "s");
            }
        } finally {
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private DependencyAnalyzer dependency(
            final String side,
            final ReactorAnalysisScope scope) {
        final File javaHome = mavenRuntime.getJavaHome() == null
                ? null : mavenRuntime.getJavaHome().toFile();
        return new DependencyAnalyzer(side, scope.getReactorRoot(),
                Collections.emptySet(), diagnostics, javaHome,
                mavenRuntime.getExecutable(), mavenArguments)
                .withTemporaryDirectory(temporaryDirectory)
                .withProjectArguments(scope.getProjectArguments());
    }

    private BuildResult build(final ReactorAnalysisScope scope)
            throws Exception {
        final File javaHome = mavenRuntime.getJavaHome() == null
                ? null : mavenRuntime.getJavaHome().toFile();
        return new BuildRunner("target", scope.getReactorRoot(),
                diagnostics, javaHome, mavenRuntime.getExecutable(),
                mavenArguments, temporaryDirectory)
                .withProjectArguments(scope.getProjectArguments())
                .build();
    }

    private List<ModuleDependencyTree> selectedTrees(
            final List<ModuleDependencyTree> trees,
            final ReactorAnalysisScope scope) {
        final Set<String> keys = scope.getModules().stream()
                .map(ModuleId::coordinateKey)
                .collect(java.util.stream.Collectors.toSet());
        return trees.stream()
                .filter(tree -> keys.contains(tree.getModule().diffKey()))
                .sorted(Comparator.comparing(tree ->
                        tree.getModule().diffKey()))
                .toList();
    }

    private List<ModuleDependencyTree> externalTrees(
            final List<ModuleDependencyTree> trees,
            final Set<String> reactorKeys) {
        return trees.stream().map(tree -> new ModuleDependencyTree(
                        tree.getModule(), tree.getModulePath(),
                        externalNodes(tree.getDependencies(), reactorKeys)))
                .toList();
    }

    private List<DependencyNode> externalNodes(
            final List<DependencyNode> nodes,
            final Set<String> reactorKeys) {
        final List<DependencyNode> result = new ArrayList<>();
        for (DependencyNode node : nodes) {
            final List<DependencyNode> children = externalNodes(
                    node.getChildren(), reactorKeys);
            if (reactorKeys.contains(node.getArtifact().diffKey())) {
                result.addAll(children);
            } else {
                result.add(new DependencyNode(node.getArtifact(),
                        node.getScope(), children));
            }
        }
        return List.copyOf(result);
    }

    private BindingResult bindAndDiff(
            final List<DependencyChange> changes,
            final ReactorAnalysisScope baselineScope,
            final ReactorAnalysisScope targetScope,
            final DependencyAnalysisResult baselineDependencies,
            final DependencyAnalysisResult targetDependencies,
            final List<ModuleDependencyTree> baselineTrees,
            final List<ModuleDependencyTree> targetTrees) throws Exception {
        final Map<String, ModuleId> targetModules = moduleMap(
                targetScope.getModules());
        final Map<String, ModuleDependencyTree> baselineTreeMap =
                treeMap(baselineTrees);
        final Map<String, ModuleDependencyTree> targetTreeMap =
                treeMap(targetTrees);
        final Map<String, List<DependencyUpgradeKey>> groups =
                new LinkedHashMap<>();
        for (DependencyChange change : changes) {
            if (change.getChangeType() != ChangeType.VERSION_CHANGED
                    || !"jar".equals(change.getOldArtifact().getType())
                    || !"jar".equals(change.getNewArtifact().getType())) {
                continue;
            }
            final String moduleKey = ArtifactCoord.parse(
                    change.getModule()).diffKey();
            final ModuleId module = targetModules.get(moduleKey);
            if (module == null) {
                continue;
            }
            final ModuleDependencyTree baselineTree =
                    baselineTreeMap.get(moduleKey);
            final ModuleDependencyTree targetTree =
                    targetTreeMap.get(moduleKey);
            final ResolvedArtifact oldArtifact = binding(
                    baselineDependencies, baselineTree,
                    change.getOldArtifact(), change);
            final ResolvedArtifact newArtifact = binding(
                    targetDependencies, targetTree,
                    change.getNewArtifact(), change);
            final DependencyUpgradeKey key = new DependencyUpgradeKey(
                    module, change.getScope(), change.getOldArtifact(),
                    change.getNewArtifact(), oldArtifact.getPath(),
                    newArtifact.getPath());
            groups.computeIfAbsent(pairKey(key), ignored ->
                    new ArrayList<>()).add(key);
        }
        return parallelJarDiff(groups);
    }

    private BindingResult parallelJarDiff(
            final Map<String, List<DependencyUpgradeKey>> groups)
            throws InterruptedException {
        if (groups.isEmpty()) {
            return new BindingResult(Map.of(), Set.of(), Map.of(),
                    jarDiffWorkerLimit(), 0);
        }
        final int configuredWorkers = jarDiffWorkerLimit();
        final int workers = Math.min(groups.size(), configuredWorkers);
        final ExecutorService executor = Executors.newFixedThreadPool(workers);
        final List<Future<PairDiff>> futures = new ArrayList<>();
        for (Map.Entry<String, List<DependencyUpgradeKey>> entry
                : groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            futures.add(executor.submit(() -> diffPair(
                    entry.getKey(), entry.getValue().get(0))));
        }
        final Map<String, List<ChangePoint>> pairPoints =
                new LinkedHashMap<>();
        final Set<String> failedModules = new LinkedHashSet<>();
        final Map<String, List<String>> failuresByModule =
                new LinkedHashMap<>();
        try {
            for (Future<PairDiff> future : futures) {
                final PairDiff pair;
                try {
                    pair = future.get();
                } catch (ExecutionException exception) {
                    throw new IllegalStateException(
                            "Unexpected JAR diff task failure",
                            exception.getCause());
                }
                if (pair.failure() == null) {
                    pairPoints.put(pair.key(), pair.points());
                } else {
                    for (DependencyUpgradeKey key : groups.get(pair.key())) {
                        final String moduleKey = key.getModuleId()
                                .coordinateKey();
                        failedModules.add(moduleKey);
                        failuresByModule.computeIfAbsent(moduleKey,
                                ignored -> new ArrayList<>()).add(
                                "Skipped physical JAR pair " + pair.key()
                                        + ": " + pair.failure());
                    }
                    diagnostics.warn("jar-diff",
                            "Skipped JAR pair " + pair.key() + ": "
                                    + pair.failure());
                }
            }
        } finally {
            executor.shutdownNow();
        }
        final Map<String, List<BoundChangePoint>> byModule =
                new LinkedHashMap<>();
        for (Map.Entry<String, List<DependencyUpgradeKey>> entry
                : groups.entrySet()) {
            final List<ChangePoint> points = pairPoints.get(entry.getKey());
            if (points == null) {
                continue;
            }
            for (DependencyUpgradeKey key : entry.getValue()) {
                final List<BoundChangePoint> target = byModule
                        .computeIfAbsent(key.getModuleId().coordinateKey(),
                                ignored -> new ArrayList<>());
                for (ChangePoint point : points) {
                    target.add(new BoundChangePoint(key,
                            rebind(point, key.getNewArtifact())));
                }
            }
        }
        byModule.values().forEach(values -> values.sort(
                Comparator.comparing(BoundChangePoint::stableKey)));
        failuresByModule.values().forEach(values -> values.sort(
                String::compareTo));
        return new BindingResult(byModule, failedModules, failuresByModule,
                configuredWorkers, workers);
    }

    private int jarDiffWorkerLimit() {
        return Math.max(1,
                Runtime.getRuntime().availableProcessors() / 2);
    }

    private PairDiff diffPair(
            final String key,
            final DependencyUpgradeKey upgrade) {
        try {
            final DependencyChange change = new DependencyChange(
                    ChangeType.VERSION_CHANGED,
                    upgrade.getOldArtifact(), upgrade.getNewArtifact(),
                    upgrade.getScope(), upgrade.getModuleId().stableKey());
            final List<ChangePoint> points = new BytecodeDiffEngine(kinds)
                    .diff(new JarLocationResult(change,
                            upgrade.getOldPath(), upgrade.getNewPath()));
            return new PairDiff(key, points, null);
        } catch (Exception exception) {
            return new PairDiff(key, List.of(),
                    exception.getClass().getSimpleName() + ": "
                            + exception.getMessage());
        }
    }

    private ChangePoint rebind(
            final ChangePoint point,
            final ArtifactCoord artifact) {
        return ChangePoint.withDescriptors(artifact, point.getKind(),
                point.getOwner(), point.getName(),
                new MemberDescriptors(point.getOldDescriptor(),
                        point.getNewDescriptor()),
                point.getOldHash(), point.getNewHash());
    }

    private ResolvedArtifact binding(
            final DependencyAnalysisResult analysis,
            final ModuleDependencyTree tree,
            final ArtifactCoord artifact,
            final DependencyChange change) {
        if (tree == null) {
            throw new IllegalStateException(
                    "Dependency tree missing for " + change.getModule());
        }
        final List<ResolvedArtifact> matches = analysis
                .artifactsFor(tree.getModulePath()).stream()
                .filter(value -> value.getArtifact().equals(artifact))
                .filter(value -> value.getScope() == change.getScope())
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                    "Artifact path binding must be unique: module="
                            + change.getModule() + "; artifact=" + artifact
                            + "; matches=" + matches.size());
        }
        return matches.get(0);
    }

    private List<ModuleAnalysisUnit> units(
            final PreparedAnalysis prepared,
            final BindingResult bindings) {
        final ReactorAnalysisScope baselineScope = prepared.baselineScope();
        final ReactorAnalysisScope targetScope = prepared.targetScope();
        final BuildResult build = prepared.targetBuild();
        final DependencyAnalysisResult baselineDependencies =
                prepared.baselineDependencies();
        final DependencyAnalysisResult targetDependencies =
                prepared.targetDependencies();
        final List<ModuleDependencyTree> baselineTrees =
                prepared.baselineTrees();
        final List<ModuleDependencyTree> targetTrees =
                prepared.targetTrees();
        final Map<String, ModuleId> baselineModules = moduleMap(
                baselineScope.getModules());
        final Map<String, ModuleId> targetModules = moduleMap(
                targetScope.getModules());
        final Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(baselineModules.keySet());
        allKeys.addAll(targetModules.keySet());
        final Map<String, ModuleDependencyTree> baselineTreeMap =
                treeMap(baselineTrees);
        final Map<String, ModuleDependencyTree> targetTreeMap =
                treeMap(targetTrees);
        final Map<String, ModuleBuildOutput> outputs = outputMap(
                build.getOutputs(), targetScope);
        final List<ModuleAnalysisUnit> result = new ArrayList<>();
        for (String key : allKeys.stream().sorted().toList()) {
            final ModuleId target = targetModules.get(key);
            final ModuleId baseline = baselineModules.get(key);
            final ModuleId identity = target == null ? baseline : target;
            final Path classes = target == null
                    ? targetScope.getReactorRoot().resolve(
                    identity.getRelativePath()).resolve("target/classes")
                    : classesPath(target, targetScope, outputs);
            final ModuleDependencyTree targetTree = targetTreeMap.get(key);
            final ModuleDependencyTree baselineTree = baselineTreeMap.get(key);
            final List<Path> reactorClasses = targetTree == null
                    ? List.of() : reactorClasses(targetTree,
                    targetScope, outputs, key);
            final List<ResolvedArtifact> targetArtifacts = targetTree == null
                    ? List.of() : externalArtifacts(
                    targetDependencies, targetTree, targetScope);
            final List<ResolvedArtifact> baselineArtifacts =
                    baselineTree == null ? List.of() : externalArtifacts(
                    baselineDependencies, baselineTree, baselineScope);
            final ModulePresence presence = target == null
                    ? ModulePresence.BASELINE_ONLY
                    : baseline == null ? ModulePresence.TARGET_ONLY
                    : ModulePresence.BOTH;
            result.add(new ModuleAnalysisUnit(identity, presence, classes,
                    reactorClasses, targetArtifacts, baselineArtifacts,
                    new ModuleChangeSet(
                            bindings.pointsByModule().getOrDefault(
                                    key, List.of()),
                            bindings.failuresByModule().getOrDefault(
                                    key, List.of()))));
        }
        result.sort(Comparator.comparing(unit ->
                unit.getModuleId().stableKey()));
        return result;
    }

    private List<ModuleAnalysisResult> analyzeModules(
            final List<ModuleAnalysisUnit> units,
            final Set<String> failedDiffModules,
            final int actualParallelism) throws InterruptedException {
        final Map<String, ModuleAnalysisUnit> unitsByKey = new HashMap<>();
        units.forEach(unit -> unitsByKey.put(
                unit.getModuleId().coordinateKey(), unit));
        final List<ModuleAnalysisResult> result = new ArrayList<>();
        final List<ModuleAnalysisUnit> active = new ArrayList<>();
        for (ModuleAnalysisUnit unit : units) {
            if (unit.getPresence() == ModulePresence.BASELINE_ONLY) {
                result.add(skipped(unit,
                        ModuleAnalysisReason.SKIPPED_NOT_IN_TARGET));
            } else if (unit.getPresence() == ModulePresence.TARGET_ONLY) {
                result.add(skipped(unit,
                        ModuleAnalysisReason.SKIPPED_NO_BASELINE));
            } else if (unit.getChangePoints().isEmpty()
                    && !failedDiffModules.contains(
                    unit.getModuleId().coordinateKey())) {
                result.add(skipped(unit,
                        ModuleAnalysisReason.SKIPPED_NO_RELEVANT_CHANGE));
            } else {
                active.add(unit);
            }
        }
        if (active.isEmpty()) {
            return result.stream().sorted(moduleResultComparator()).toList();
        }
        final ExecutorService executor = Executors.newFixedThreadPool(
                Math.max(1, actualParallelism));
        final List<Future<ModuleAnalysisResult>> futures = new ArrayList<>();
        for (ModuleAnalysisUnit unit : active) {
            futures.add(executor.submit(() -> analyzeModule(
                    unit, failedDiffModules.contains(
                            unit.getModuleId().coordinateKey()))));
        }
        try {
            for (Future<ModuleAnalysisResult> future : futures) {
                try {
                    result.add(future.get());
                } catch (ExecutionException exception) {
                    throw new IllegalStateException(
                            "Module task escaped isolation",
                            exception.getCause());
                }
            }
        } finally {
            executor.shutdownNow();
        }
        result.sort(moduleResultComparator());
        return List.copyOf(result);
    }

    private ModuleAnalysisResult analyzeModule(
            final ModuleAnalysisUnit unit,
            final boolean diffFailed) {
        final long start = System.currentTimeMillis();
        final Map<String, Long> stageElapsed = new LinkedHashMap<>();
        if (unit.getChangePoints().isEmpty()) {
            return new ModuleAnalysisResult.Builder(unit)
                    .status(ModuleAnalysisStatus.INCONCLUSIVE,
                            ModuleAnalysisReason.INCONCLUSIVE_BYTECODE_DIFF,
                            "All relevant physical JAR diffs failed")
                    .limitations(unit.getJarDiffFailures())
                    .elapsedMillis(System.currentTimeMillis() - start)
                    .stageElapsedMillis(stageElapsed)
                    .build();
        }
        final List<BoundChangePoint> graphPoints = unit.getChangePoints()
                .stream().filter(point -> !isAdded(
                        point.getChangePoint().getKind())).toList();
        if (graphPoints.isEmpty()) {
            final Map<BoundChangePoint, ChangePointDisposition> dispositions =
                    new LinkedHashMap<>();
            unit.getChangePoints().forEach(point -> dispositions.put(point,
                    ChangePointDisposition.CHANGE_KIND_NOT_ANALYZED));
            return new ModuleAnalysisResult.Builder(unit)
                    .status(diffFailed ? ModuleAnalysisStatus.INCONCLUSIVE
                                    : ModuleAnalysisStatus.SUCCESS,
                            diffFailed
                                    ? ModuleAnalysisReason
                                    .INCONCLUSIVE_BYTECODE_DIFF
                                    : ModuleAnalysisReason.NONE,
                            "No removal or modification ChangePoint")
                    .dispositions(dispositions)
                    .limitations(diffFailed
                            ? unit.getJarDiffFailures()
                            : List.of())
                    .elapsedMillis(System.currentTimeMillis() - start)
                    .stageElapsedMillis(stageElapsed)
                    .build();
        }
        try {
            long stageStart = System.currentTimeMillis();
            new ModuleScopeValidator().validate(unit);
            stageElapsed.put("scope-validation",
                    System.currentTimeMillis() - stageStart);
            stageStart = System.currentTimeMillis();
            final ModuleCallGraphSession session =
                    new ModuleCallGraphEngine(diagnostics, javaRuntime)
                            .build(unit, callGraphTimeoutSeconds);
            stageElapsed.put("call-graph",
                    System.currentTimeMillis() - stageStart);
            stageStart = System.currentTimeMillis();
            session.attachServiceLoaderOverlay(
                    new ModuleServiceLoaderEnricher().enrich(unit, session));
            stageElapsed.put("service-loader",
                    System.currentTimeMillis() - stageStart);
            stageStart = System.currentTimeMillis();
            final ModuleImpactQueryResult query =
                    new ModuleImpactTracer(diagnostics).trace(unit, session);
            stageElapsed.put("call-graph-query",
                    System.currentTimeMillis() - stageStart);
            final List<String> limitations = new ArrayList<>(
                    session.getServiceLoaderOverlay().getLimitations());
            if (diffFailed) {
                limitations.addAll(unit.getJarDiffFailures());
            }
            final boolean inconclusive = diffFailed
                    || session.getServiceLoaderOverlay().isInconclusive();
            final ModuleAnalysisReason reason = diffFailed
                    ? ModuleAnalysisReason.INCONCLUSIVE_BYTECODE_DIFF
                    : session.getServiceLoaderOverlay().isInconclusive()
                    ? ModuleAnalysisReason.INCONCLUSIVE_SERVICE_LOADER
                    : ModuleAnalysisReason.NONE;
            return new ModuleAnalysisResult.Builder(unit)
                    .status(inconclusive
                                    ? ModuleAnalysisStatus.INCONCLUSIVE
                                    : ModuleAnalysisStatus.SUCCESS,
                            reason,
                            "Analysis completed within declared model")
                    .session(session)
                    .candidatePaths(query.getPaths())
                    .finalPaths(query.getPaths())
                    .structuralImpacts(query.getStructuralImpacts())
                    .dispositions(query.getDispositions())
                    .limitations(limitations)
                    .elapsedMillis(System.currentTimeMillis() - start)
                    .stageElapsedMillis(stageElapsed)
                    .build();
        } catch (ScopeValidationException exception) {
            return failed(unit,
                    ModuleAnalysisReason.FAILED_SCOPE_VALIDATION,
                    exception, start, stageElapsed);
        } catch (CallGraphException exception) {
            final ModuleAnalysisReason reason = exception.getMessage() != null
                    && exception.getMessage().contains("timed out")
                    ? ModuleAnalysisReason.FAILED_CALL_GRAPH_TIMEOUT
                    : ModuleAnalysisReason.FAILED_ANALYSIS;
            return failed(unit, reason, exception, start, stageElapsed);
        } catch (RuntimeException exception) {
            return failed(unit, ModuleAnalysisReason.FAILED_ANALYSIS,
                    exception, start, stageElapsed);
        }
    }

    private ModuleAnalysisResult failed(
            final ModuleAnalysisUnit unit,
            final ModuleAnalysisReason reason,
            final RuntimeException failure,
            final long start,
            final Map<String, Long> completedStages) {
        diagnostics.error("module-analysis",
                unit.getModuleId() + ": " + failure.getMessage());
        final Map<String, Long> stageElapsed = new LinkedHashMap<>(
                completedStages);
        stageElapsed.put("failed-analysis-total",
                System.currentTimeMillis() - start);
        return new ModuleAnalysisResult.Builder(unit)
                .status(ModuleAnalysisStatus.FAILED, reason,
                        failure.getMessage() == null
                                ? failure.getClass().getSimpleName()
                                : failure.getMessage())
                .elapsedMillis(System.currentTimeMillis() - start)
                .stageElapsedMillis(stageElapsed)
                .build();
    }

    private ModuleAnalysisResult skipped(
            final ModuleAnalysisUnit unit,
            final ModuleAnalysisReason reason) {
        return new ModuleAnalysisResult.Builder(unit)
                .status(ModuleAnalysisStatus.SKIPPED, reason, reason.name())
                .build();
    }

    private AnalysisStatus overallStatus(
            final List<ModuleAnalysisResult> modules) {
        final long failed = modules.stream()
                .filter(value -> value.getStatus()
                        == ModuleAnalysisStatus.FAILED).count();
        final long completed = modules.stream()
                .filter(value -> value.getStatus()
                        == ModuleAnalysisStatus.SUCCESS
                        || value.getStatus()
                        == ModuleAnalysisStatus.INCONCLUSIVE).count();
        final boolean inconclusive = modules.stream()
                .anyMatch(value -> value.getStatus()
                        == ModuleAnalysisStatus.INCONCLUSIVE);
        if (failed > 0L) {
            return completed > 0L
                    ? AnalysisStatus.PARTIAL_SUCCESS : AnalysisStatus.FAILED;
        }
        return inconclusive ? AnalysisStatus.INCONCLUSIVE
                : AnalysisStatus.SUCCESS;
    }

    private Map<String, ModuleId> moduleMap(
            final Collection<ModuleId> modules) {
        final Map<String, ModuleId> result = new LinkedHashMap<>();
        for (ModuleId module : modules) {
            final ModuleId previous = result.putIfAbsent(
                    module.coordinateKey(), module);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate module coordinate key: "
                                + module.coordinateKey());
            }
        }
        return result;
    }

    private Map<String, ModuleDependencyTree> treeMap(
            final List<ModuleDependencyTree> trees) {
        final Map<String, ModuleDependencyTree> result =
                new LinkedHashMap<>();
        trees.forEach(tree -> result.put(
                tree.getModule().diffKey(), tree));
        return result;
    }

    private Map<String, ModuleBuildOutput> outputMap(
            final List<ModuleBuildOutput> outputs,
            final ReactorAnalysisScope scope) {
        final Map<String, ModuleBuildOutput> result = new HashMap<>();
        for (ModuleBuildOutput output : outputs) {
            final Path relative = scope.getReactorRoot().relativize(
                    output.getModulePath().toAbsolutePath().normalize());
            result.put(relative.toString(), output);
        }
        return result;
    }

    private Path classesPath(
            final ModuleId module,
            final ReactorAnalysisScope scope,
            final Map<String, ModuleBuildOutput> outputs) {
        final ModuleBuildOutput output = outputs.get(
                module.getRelativePath().toString());
        return output == null
                ? scope.getReactorRoot().resolve(module.getRelativePath())
                .resolve("target/classes")
                : output.getClassesDir();
    }

    private List<Path> reactorClasses(
            final ModuleDependencyTree tree,
            final ReactorAnalysisScope scope,
            final Map<String, ModuleBuildOutput> outputs,
            final String currentKey) {
        final Map<String, ModuleId> modules = moduleMap(
                scope.getAllModules());
        final Set<String> closure = new LinkedHashSet<>();
        collectReactor(tree.getDependencies(),
                scope.getReactorCoordinates(), closure);
        closure.remove(currentKey);
        return closure.stream().sorted()
                .map(modules::get)
                .filter(Objects::nonNull)
                .map(module -> classesPath(module, scope, outputs))
                .toList();
    }

    private void collectReactor(
            final List<DependencyNode> nodes,
            final Set<ArtifactCoord> reactor,
            final Set<String> result) {
        final Set<String> keys = reactor.stream()
                .map(ArtifactCoord::diffKey)
                .collect(java.util.stream.Collectors.toSet());
        for (DependencyNode node : nodes) {
            if (keys.contains(node.getArtifact().diffKey())) {
                result.add(node.getArtifact().diffKey());
            }
            collectReactor(node.getChildren(), reactor, result);
        }
    }

    private List<ResolvedArtifact> externalArtifacts(
            final DependencyAnalysisResult analysis,
            final ModuleDependencyTree tree,
            final ReactorAnalysisScope scope) {
        final Set<String> reactor = scope.getReactorCoordinates().stream()
                .map(ArtifactCoord::diffKey)
                .collect(java.util.stream.Collectors.toSet());
        return analysis.artifactsFor(tree.getModulePath()).stream()
                .filter(value -> !reactor.contains(
                        value.getArtifact().diffKey()))
                .sorted(Comparator.comparing(value ->
                        value.getArtifact().toString()))
                .toList();
    }

    private boolean isAdded(final ChangePointKind kind) {
        return kind == ChangePointKind.CLASS_ADDED
                || kind == ChangePointKind.METHOD_ADDED
                || kind == ChangePointKind.FIELD_ADDED;
    }

    private String pairKey(final DependencyUpgradeKey key) {
        return key.getOldArtifact() + "@" + key.getOldPath()
                + "->" + key.getNewArtifact() + "@"
                + key.getNewPath();
    }

    private Comparator<ModuleAnalysisResult> moduleResultComparator() {
        return Comparator.comparing(value ->
                value.getModuleId().stableKey());
    }

    /**
     * Parallel front branch result.
     *
     * @param name branch name
     * @param dependencies baseline dependencies, nullable
     * @param build target build, nullable
     * @param elapsedMillis branch elapsed milliseconds
     */
    private record PreparationBranch(
            String name,
            DependencyAnalysisResult dependencies,
            BuildResult build,
            long elapsedMillis) {
    }

    /**
     * Joined front preparation.
     *
     * @param baselineDependencies baseline dependencies
     * @param targetBuild target build
     * @param baselineDependencyMillis baseline dependency elapsed
     * @param targetBuildMillis target build elapsed
     */
    private record FrontPreparation(
            DependencyAnalysisResult baselineDependencies,
            BuildResult targetBuild,
            long baselineDependencyMillis,
            long targetBuildMillis) {
    }

    /**
     * Unique physical pair diff.
     *
     * @param key physical pair key
     * @param points raw ChangePoints
     * @param failure failure detail, nullable
     */
    private record PairDiff(
            String key,
            List<ChangePoint> points,
            String failure) {
    }

    /**
     * Bound ChangePoints and isolated diff failures.
     *
     * @param pointsByModule points keyed by module coordinate key
     * @param failedModules modules with at least one failed pair
     * @param failuresByModule stable failed pair evidence per module
     * @param configuredWorkers configured JAR diff worker limit
     * @param actualWorkers actual JAR diff workers
     */
    private record BindingResult(
            Map<String, List<BoundChangePoint>> pointsByModule,
            Set<String> failedModules,
            Map<String, List<String>> failuresByModule,
            int configuredWorkers,
            int actualWorkers) {
    }

    /**
     * Joined immutable preparation input for module planning.
     *
     * @param baselineScope baseline reactor scope
     * @param targetScope target reactor scope
     * @param targetBuild target build
     * @param baselineDependencies baseline dependency analysis
     * @param targetDependencies target dependency analysis
     * @param baselineTrees selected baseline trees
     * @param targetTrees selected target trees
     */
    private record PreparedAnalysis(
            ReactorAnalysisScope baselineScope,
            ReactorAnalysisScope targetScope,
            BuildResult targetBuild,
            DependencyAnalysisResult baselineDependencies,
            DependencyAnalysisResult targetDependencies,
            List<ModuleDependencyTree> baselineTrees,
            List<ModuleDependencyTree> targetTrees) {
    }
}
