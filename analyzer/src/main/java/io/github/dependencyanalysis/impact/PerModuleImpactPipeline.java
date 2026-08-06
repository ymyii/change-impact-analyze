package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.build.BuildResult;
import io.github.dependencyanalysis.build.BuildRunner;
import io.github.dependencyanalysis.build.ModuleBuildOutput;
import io.github.dependencyanalysis.bytecode.BytecodeDiffEngine;
import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.bytecode.MemberDescriptors;
import io.github.dependencyanalysis.callgraph.CallGraphException;
import io.github.dependencyanalysis.callgraph.EntrypointClassIndex;
import io.github.dependencyanalysis.callgraph.EntrypointClassScanner;
import io.github.dependencyanalysis.callgraph.EntrypointSelection;
import io.github.dependencyanalysis.callgraph.ModuleCallGraphEngine;
import io.github.dependencyanalysis.callgraph.ModuleCallGraphSession;
import io.github.dependencyanalysis.callgraph.ModuleScopeValidator;
import io.github.dependencyanalysis.callgraph.ScopeValidationResult;
import io.github.dependencyanalysis.callgraph.ScopeValidationWarning;
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
import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.jar.CoordinateJarRepository;
import io.github.dependencyanalysis.jar.IJarRepository;
import io.github.dependencyanalysis.metrics.ManagedExecutorRegistry;
import io.github.dependencyanalysis.metrics.ManagedExecutorRegistry
        .ManagedExecutor;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.MavenDependencyPluginRuntime;
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
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Executes the Spring backend per-module Vanilla 0-1-CFA pipeline. */
final class PerModuleImpactPipeline {

    /** Maximum wait for canceled preparation tasks to release processes. */
    private static final long PREPARATION_SHUTDOWN_SECONDS = 30L;

    /** Diagnostics. */
    private final DiagnosticLog diagnostics;

    /** Analyzer-owned thread pool registry. */
    private final ManagedExecutorRegistry executors;

    /** Included bytecode changes. */
    private final Set<ChangePointKind> kinds;

    /** Maven runtime. */
    private final MavenRuntimeDescriptor mavenRuntime;

    /** Embedded Maven Dependency Plugin runtime. */
    private final MavenDependencyPluginRuntime pluginRuntime;

    /** Safe Maven arguments. */
    private final List<String> mavenArguments;

    /** Target JDK. */
    private final JavaRuntimeDescriptor javaRuntime;

    /** Module Call Graph timeout. */
    private final long callGraphTimeoutSeconds;

    /** Configured safe analysis-stage parallelism. */
    private final int analysisParallelism;

    /** User-selected PROJECT entrypoint boundary. */
    private final EntrypointSelection entrypointSelection;

    /** Command temporary directory. */
    private final Path temporaryDirectory;

    /** Immutable JAR repository for the active command. */
    private IJarRepository jarRepository;

    /**
     * Creates the per-module pipeline.
     *
     * @param collector diagnostics
     * @param includedKinds bytecode changes
     * @param runtime Maven runtime
     * @param dependencyPlugin embedded Dependency Plugin runtime
     * @param arguments Maven arguments
     * @param targetJava target JDK
     * @param options runtime controls and entrypoint selection
     */
    PerModuleImpactPipeline(
            final DiagnosticLog collector,
            final Set<ChangePointKind> includedKinds,
            final MavenRuntimeDescriptor runtime,
            final MavenDependencyPluginRuntime dependencyPlugin,
            final List<String> arguments,
            final JavaRuntimeDescriptor targetJava,
            final PerModulePipelineOptions options) {
        diagnostics = Objects.requireNonNull(collector, "collector");
        executors = Objects.requireNonNull(options.executors(), "executors");
        kinds = Set.copyOf(includedKinds);
        mavenRuntime = Objects.requireNonNull(runtime, "mavenRuntime");
        pluginRuntime = Objects.requireNonNull(
                dependencyPlugin, "dependencyPlugin");
        mavenArguments = List.copyOf(arguments);
        javaRuntime = Objects.requireNonNull(targetJava, "javaRuntime");
        callGraphTimeoutSeconds = options.callGraphTimeoutSeconds();
        analysisParallelism = options.analysisParallelism();
        temporaryDirectory = options.temporaryDirectory();
        entrypointSelection = Objects.requireNonNull(
                options.entrypointSelection(), "entrypointSelection");
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
        final List<ResolvedArtifact> repositoryInputs = new ArrayList<>();
        repositoryInputs.addAll(front.baselineDependencies().getArtifacts());
        repositoryInputs.addAll(targetDependencies.getArtifacts());
        try (IJarRepository repository = CoordinateJarRepository.create(
                repositoryInputs,
                warning -> diagnostics.warn("jar-repository", warning))) {
            jarRepository = repository;
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
        final Map<String, List<ArtifactCoord>> baselineArtifactsByModule =
                externalArtifactsByModule(
                        front.baselineDependencies(), baselineTrees,
                        baselineScope);
        final Map<String, List<ArtifactCoord>> targetArtifactsByModule =
                externalArtifactsByModule(
                        targetDependencies, targetTrees, targetScope);
        final List<ModuleAnalysisUnit> units = units(
                new PreparedAnalysis(baselineScope, targetScope,
                        front.targetBuild(), baselineArtifactsByModule,
                        targetArtifactsByModule,
                        baselineTrees, targetTrees),
                bindings, changes);
        final EntrypointPreparation entrypoints = prepareEntrypoints(
                units, bindings.failedModules());
        final Set<String> unmatchedEntrypointModules =
                entrypoints.unmatchedModules();
        final int relevantCount = (int) units.stream()
                .filter(unit -> !unit.getChangePoints().isEmpty()
                        || bindings.failedModules().contains(
                        unit.getModuleId().coordinateKey()))
                .filter(unit -> !unmatchedEntrypointModules.contains(
                        unit.getModuleId().coordinateKey()))
                .count();
        final int actualParallelism = relevantCount == 0 ? 0
                : Math.min(analysisParallelism, relevantCount);
        if (analysisParallelism
                > Runtime.getRuntime().availableProcessors()) {
            diagnostics.warn("module-analysis",
                    "--analysis-parallelism exceeds availableProcessors: "
                            + analysisParallelism);
        }
        final long modulesStart = System.currentTimeMillis();
        final List<ModuleAnalysisResult> analyzed = analyzeModules(
                units, bindings.failedModules(), unmatchedEntrypointModules,
                entrypoints, actualParallelism);
        elapsed.put("module-analysis",
                System.currentTimeMillis() - modulesStart);
        final long ssaStart = System.currentTimeMillis();
        final List<ModuleAnalysisResult> filtered =
                new SsaEquivalenceEngine(
                        diagnostics, javaRuntime, repository())
                        .filter(analyzed);
        elapsed.put("ssa-equivalence",
                System.currentTimeMillis() - ssaStart);
        final long codeStart = System.currentTimeMillis();
        final CodeEvidenceResult codeEvidence = buildCodeComparisons(filtered);
        elapsed.put("code-comparison",
                System.currentTimeMillis() - codeStart);
        return new AnalysisRunResult(targetScope.getMode(),
                overallStatus(codeEvidence.modules()), changes,
                codeEvidence.modules(), new AnalysisConcurrency(
                        analysisParallelism, actualParallelism,
                        bindings.actualWorkers(),
                        codeEvidence.actualWorkers()), elapsed,
                entrypointSelection);
        } finally {
            jarRepository = null;
        }
    }

    private CodeEvidenceResult buildCodeComparisons(
            final List<ModuleAnalysisResult> modules)
            throws InterruptedException {
        final Map<String, List<BoundChangePoint>> requests =
                new LinkedHashMap<>();
        for (ModuleAnalysisResult module : modules) {
            final Set<BoundChangePoint> relevant = new LinkedHashSet<>();
            module.getCandidatePaths().forEach(path -> relevant.add(
                    path.getTerminal().getChangePoint()));
            module.getStructuralPaths().forEach(path -> relevant.add(
                    path.getChangePoint()));
            relevant.stream().sorted(Comparator.comparing(
                    BoundChangePoint::stableKey)).forEach(point -> requests
                    .computeIfAbsent(codeEvidenceKey(point), ignored ->
                            new ArrayList<>()).add(point));
        }
        if (requests.isEmpty()) {
            return new CodeEvidenceResult(modules, 0);
        }
        final int workers = Math.min(analysisParallelism, requests.size());
        final ManagedExecutor managed = executors.fixed(
                "code-comparison", workers);
        final ExecutorService executor = managed.executor();
        final List<Future<MemberCodeEvidence>> futures = new ArrayList<>();
        for (Map.Entry<String, List<BoundChangePoint>> request
                : requests.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            futures.add(executor.submit(() -> codeComparison(
                    request.getKey(), request.getValue().get(0))));
        }
        final Map<String, CodeComparisonEvidence> evidence =
                new LinkedHashMap<>();
        try {
            for (Future<MemberCodeEvidence> future : futures) {
                try {
                    final MemberCodeEvidence value = future.get();
                    evidence.put(value.key(), value.evidence());
                } catch (ExecutionException exception) {
                    throw new IllegalStateException(
                            "Unexpected code comparison task failure",
                            exception.getCause());
                }
            }
        } finally {
            managed.close();
        }
        final List<ModuleAnalysisResult> enriched = new ArrayList<>();
        for (ModuleAnalysisResult module : modules) {
            final Map<BoundChangePoint, CodeComparisonEvidence> bound =
                    new LinkedHashMap<>();
            final Set<BoundChangePoint> relevant = new LinkedHashSet<>();
            module.getCandidatePaths().forEach(path -> relevant.add(
                    path.getTerminal().getChangePoint()));
            module.getStructuralPaths().forEach(path -> relevant.add(
                    path.getChangePoint()));
            relevant.stream().sorted(Comparator.comparing(
                    BoundChangePoint::stableKey)).forEach(point -> bound.put(
                    point, evidence.get(codeEvidenceKey(point))));
            enriched.add(module.toBuilder().codeComparisons(bound).build());
        }
        return new CodeEvidenceResult(enriched, workers);
    }

    private MemberCodeEvidence codeComparison(
            final String key, final BoundChangePoint point) {
        final DiagnosticContext context = DiagnosticContext.of(
                "code-comparison", "decompile")
                .withModule(point.getDependencyUpgradeKey().getModuleId()
                        .stableKey())
                .withArtifact(point.getDependencyUpgradeKey()
                        .getNewArtifact().toString());
        diagnostics.debug(context, "started");
        final CodeComparisonEvidence evidence;
        try {
            evidence = new CodeComparisonBuilder(
                    diagnostics, javaRuntime, repository()).build(point);
        } catch (RuntimeException exception) {
            diagnostics.warn(context, "unavailable: "
                    + exception.getClass().getSimpleName());
            return new MemberCodeEvidence(key,
                    new CodeComparisonEvidence(
                            CodeComparisonStatus.UNAVAILABLE, List.of(), "",
                            exception.getMessage() == null
                                    ? exception.getClass().getSimpleName()
                                    : exception.getMessage()));
        }
        if (evidence.getStatus() == CodeComparisonStatus.UNAVAILABLE) {
            diagnostics.warn(context, "completed; status=UNAVAILABLE; reason="
                    + evidence.getReason());
        } else {
            diagnostics.debug(context, "completed; status="
                    + evidence.getStatus());
        }
        return new MemberCodeEvidence(key, evidence);
    }

    private String codeEvidenceKey(final BoundChangePoint bound) {
        final DependencyUpgradeKey key = bound.getDependencyUpgradeKey();
        final ChangePoint point = bound.getChangePoint();
        return key.getOldArtifact() + "->" + key.getNewArtifact() + "|"
                + point.getKind() + "|" + point.getOwner() + "|"
                + point.getName() + "|" + point.getOldDescriptor() + "|"
                + point.getNewDescriptor() + "|" + point.getOldHash() + "|"
                + point.getNewHash();
    }

    private EntrypointPreparation prepareEntrypoints(
            final List<ModuleAnalysisUnit> units,
            final Set<String> failedDiffModules) {
        if (!entrypointSelection.isFiltered()) {
            return EntrypointPreparation.empty();
        }
        final Set<String> unmatched = new LinkedHashSet<>();
        final Map<String, EntrypointClassIndex> indexes =
                new LinkedHashMap<>();
        final Map<String, CallGraphException> failures =
                new LinkedHashMap<>();
        int relevant = 0;
        int matched = 0;
        final EntrypointClassScanner scanner = new EntrypointClassScanner();
        for (ModuleAnalysisUnit unit : units) {
            if (unit.getPresence() != ModulePresence.BOTH
                    || unit.getChangePoints().isEmpty()
                    && !failedDiffModules.contains(
                    unit.getModuleId().coordinateKey())) {
                continue;
            }
            relevant++;
            final DiagnosticContext context = DiagnosticContext.of(
                    "module-analysis", "entrypoint-selection").withModule(
                    unit.getModuleId().stableKey());
            final EntrypointClassIndex index;
            try {
                index = scanner.scan(
                        unit.getProjectClasses(), entrypointSelection);
            } catch (CallGraphException exception) {
                failures.put(unit.getModuleId().coordinateKey(), exception);
                diagnostics.error(context, exception.getMessage());
                continue;
            }
            indexes.put(unit.getModuleId().coordinateKey(), index);
            final var metrics = index.metrics();
            diagnostics.info(context, "selectedClasses="
                    + metrics.selectedClassCount() + "; entrypoints="
                    + metrics.entrypointCount());
            if (metrics.entrypointCount() == 0) {
                unmatched.add(unit.getModuleId().coordinateKey());
            } else {
                matched++;
            }
        }
        if (relevant > 0 && matched == 0 && failures.isEmpty()) {
            throw new EntrypointSelectionException(
                    "No relevant Module matched the configured PROJECT "
                            + "entrypoint selectors");
        }
        return new EntrypointPreparation(
                indexes, unmatched, failures);
    }

    private FrontPreparation prepareFront(
            final ReactorAnalysisScope baselineScope,
            final ReactorAnalysisScope targetScope) throws Exception {
        final ManagedExecutor managed = executors.fixed(
                "front-preparation", 2);
        final ExecutorService executor = managed.executor();
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
            managed.shutdownNow();
            awaitPreparationShutdown(executor);
            managed.close();
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
                .withPluginRuntime(pluginRuntime)
                .withProjectArguments(scope.getProjectArguments())
                .withDiagnosticContext("baseline".equals(side)
                        ? DiagnosticContext.of(
                        "front", "baseline-dependency")
                        : DiagnosticContext.of(
                        "dependency", "target-dependency"));
    }

    private BuildResult build(final ReactorAnalysisScope scope)
            throws Exception {
        final File javaHome = mavenRuntime.getJavaHome() == null
                ? null : mavenRuntime.getJavaHome().toFile();
        return new BuildRunner("target", scope.getReactorRoot(),
                diagnostics, javaHome, mavenRuntime.getExecutable(),
                mavenArguments)
                .withProjectArguments(scope.getProjectArguments())
                .withDiagnosticContext(DiagnosticContext.of(
                        "front", "target-build"))
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
            ArtifactPathBindingResolver.require(
                    "baseline", baselineDependencies, baselineTree,
                    change.getOldArtifact(), change.getModule());
            ArtifactPathBindingResolver.require(
                    "target", targetDependencies, targetTree,
                    change.getNewArtifact(), change.getModule());
            final DependencyUpgradeKey key = new DependencyUpgradeKey(
                    module, change.getScope(), change.getOldArtifact(),
                    change.getNewArtifact());
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
        final ManagedExecutor managed = executors.fixed("jar-diff", workers);
        final ExecutorService executor = managed.executor();
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
        final Map<String, List<JarDiffFailure>> failuresByModule =
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
                                new JarDiffFailure(key, pair.failure()));
                    }
                }
            }
        } finally {
            managed.close();
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
                Comparator.comparing(JarDiffFailure::stableKey)));
        return new BindingResult(byModule, failedModules, failuresByModule,
                configuredWorkers, workers);
    }

    private int jarDiffWorkerLimit() {
        return analysisParallelism;
    }

    private PairDiff diffPair(
            final String key,
            final DependencyUpgradeKey upgrade) {
        final String artifact = upgrade.getOldArtifact().getGroupId()
                + ":" + upgrade.getOldArtifact().getArtifactId()
                + ":" + upgrade.getOldArtifact().getVersion()
                + "->" + upgrade.getNewArtifact().getVersion();
        final DiagnosticContext context = DiagnosticContext.of(
                "jar-diff", "pair").withArtifact(artifact);
        diagnostics.debug(context, "JAR comparison started");
        try {
            final DependencyChange change = new DependencyChange(
                    ChangeType.VERSION_CHANGED,
                    upgrade.getOldArtifact(), upgrade.getNewArtifact(),
                    upgrade.getScope(), upgrade.getModuleId().stableKey());
            final List<ChangePoint> points = new BytecodeDiffEngine(kinds)
                    .diff(change, repository());
            diagnostics.debug(context, "JAR comparison completed; changes="
                    + points.size());
            return new PairDiff(key, points, null);
        } catch (Exception exception) {
            diagnostics.warn(context, "JAR comparison failed: "
                    + exception.getClass().getSimpleName());
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

    private List<ModuleAnalysisUnit> units(
            final PreparedAnalysis prepared,
            final BindingResult bindings,
            final List<DependencyChange> changes) {
        final ReactorAnalysisScope baselineScope = prepared.baselineScope();
        final ReactorAnalysisScope targetScope = prepared.targetScope();
        final BuildResult build = prepared.targetBuild();
        final Map<String, List<ArtifactCoord>> baselineArtifactsByModule =
                prepared.baselineArtifactsByModule();
        final Map<String, List<ArtifactCoord>> targetArtifactsByModule =
                prepared.targetArtifactsByModule();
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
        final Map<String, List<DependencyChange>> changesByModule =
                changesByModule(changes);
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
            final List<ArtifactCoord> targetArtifacts =
                    targetArtifactsByModule.getOrDefault(key, List.of());
            final List<ArtifactCoord> baselineArtifacts =
                    baselineArtifactsByModule.getOrDefault(key, List.of());
            final ModulePresence presence = target == null
                    ? ModulePresence.BASELINE_ONLY
                    : baseline == null ? ModulePresence.TARGET_ONLY
                    : ModulePresence.BOTH;
            result.add(new ModuleAnalysisUnit(identity, presence, classes,
                    reactorClasses, targetArtifacts, baselineArtifacts,
                    new ModuleChangeSet(
                            changesByModule.getOrDefault(key, List.of()),
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
            final Set<String> unmatchedEntrypointModules,
            final EntrypointPreparation entrypoints,
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
            } else if (unmatchedEntrypointModules.contains(
                    unit.getModuleId().coordinateKey())) {
                result.add(skipped(unit,
                        ModuleAnalysisReason.SKIPPED_USER_ENTRYPOINT_SCOPE));
            } else {
                active.add(unit);
            }
        }
        if (active.isEmpty()) {
            return result.stream().sorted(moduleResultComparator()).toList();
        }
        final ManagedExecutor managed = executors.fixed(
                "module-analysis", Math.max(1, actualParallelism));
        final ExecutorService executor = managed.executor();
        final List<Future<ModuleAnalysisResult>> futures = new ArrayList<>();
        for (ModuleAnalysisUnit unit : active) {
            final String key = unit.getModuleId().coordinateKey();
            futures.add(executor.submit(() -> analyzeModuleTask(
                    unit, failedDiffModules.contains(key),
                    entrypoints.indexes().get(key),
                    entrypoints.failures().get(key))));
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
            managed.close();
        }
        result.sort(moduleResultComparator());
        return List.copyOf(result);
    }

    private ModuleAnalysisResult analyzeModuleTask(
            final ModuleAnalysisUnit unit,
            final boolean diffFailed,
            final EntrypointClassIndex preparedEntrypoints,
            final CallGraphException entrypointFailure) {
        final DiagnosticContext context = DiagnosticContext.of(
                "module-analysis", "module").withModule(
                unit.getModuleId().stableKey());
        diagnostics.startStage(context);
        try {
            return analyzeModule(unit, diffFailed,
                    preparedEntrypoints, entrypointFailure);
        } finally {
            diagnostics.endStage(context);
        }
    }

    private ModuleAnalysisResult analyzeModule(
            final ModuleAnalysisUnit unit,
            final boolean diffFailed,
            final EntrypointClassIndex preparedEntrypoints,
            final CallGraphException entrypointFailure) {
        final long start = System.currentTimeMillis();
        final Map<String, Long> stageElapsed = new LinkedHashMap<>();
        if (unit.getChangePoints().isEmpty()) {
            return new ModuleAnalysisResult.Builder(unit)
                    .status(ModuleAnalysisStatus.INCONCLUSIVE,
                            ModuleAnalysisReason.INCONCLUSIVE_BYTECODE_DIFF,
                            "All relevant coordinate-pair JAR diffs failed")
                    .limitations(unit.getJarDiffFailureSummaries())
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
                            ? unit.getJarDiffFailureSummaries()
                            : List.of())
                    .elapsedMillis(System.currentTimeMillis() - start)
                    .stageElapsedMillis(stageElapsed)
                    .build();
        }
        try {
            if (entrypointFailure != null) {
                throw entrypointFailure;
            }
            final EntrypointClassIndex entrypointIndex =
                    preparedEntrypoints == null
                            ? new EntrypointClassScanner().scan(
                            unit.getProjectClasses(), entrypointSelection)
                            : preparedEntrypoints;
            long stageStart = System.currentTimeMillis();
            final ScopeValidationResult scopeValidation =
                    new ModuleScopeValidator(repository()).validate(unit);
            stageElapsed.put("scope-validation",
                    System.currentTimeMillis() - stageStart);
            reportScopeWarnings(unit, scopeValidation);
            stageStart = System.currentTimeMillis();
            final ModuleCallGraphSession session =
                    new ModuleCallGraphEngine(diagnostics, javaRuntime,
                            entrypointSelection, repository())
                            .build(unit, entrypointIndex,
                                    callGraphTimeoutSeconds);
            stageElapsed.put("call-graph",
                    System.currentTimeMillis() - stageStart);
            stageStart = System.currentTimeMillis();
            final ModuleImpactQueryResult query =
                    new ModuleImpactTracer(diagnostics).trace(unit, session);
            stageElapsed.put("call-graph-query",
                    System.currentTimeMillis() - stageStart);
            final List<String> limitations = new ArrayList<>(
                    scopeValidation.limitations());
            limitations.addAll(session.getModelLimitations());
            if (diffFailed) {
                limitations.addAll(unit.getJarDiffFailureSummaries());
            }
            final ModuleAnalysisReason reason = coverageReason(
                    diffFailed,
                    session.hasDynamicModelLimitations(),
                    session.hasServiceLoaderLimitations(),
                    scopeValidation.hasWarnings());
            final boolean inconclusive = reason
                    != ModuleAnalysisReason.NONE;
            return new ModuleAnalysisResult.Builder(unit)
                    .status(inconclusive
                                    ? ModuleAnalysisStatus.INCONCLUSIVE
                                    : ModuleAnalysisStatus.SUCCESS,
                            reason,
                            inconclusive
                                    ? "Analysis completed with coverage "
                                    + "limitations"
                                    : "Analysis completed within declared "
                                    + "model")
                    .session(session)
                    .duplicateClassResolutions(
                            session.getDuplicateClassResolutions())
                    .candidatePaths(query.getPaths())
                    .finalPaths(query.getPaths())
                    .structuralPaths(query.getStructuralPaths())
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

    static ModuleAnalysisReason coverageReason(
            final boolean diffFailed,
            final boolean serviceLoaderInconclusive,
            final boolean scopeValidationInconclusive) {
        return coverageReason(diffFailed, false,
                serviceLoaderInconclusive, scopeValidationInconclusive);
    }

    static ModuleAnalysisReason coverageReason(
            final boolean diffFailed,
            final boolean dynamicModelInconclusive,
            final boolean serviceLoaderInconclusive,
            final boolean scopeValidationInconclusive) {
        if (diffFailed) {
            return ModuleAnalysisReason.INCONCLUSIVE_BYTECODE_DIFF;
        }
        if (dynamicModelInconclusive) {
            return ModuleAnalysisReason.INCONCLUSIVE_INVOKEDYNAMIC_MODEL;
        }
        if (serviceLoaderInconclusive) {
            return ModuleAnalysisReason.INCONCLUSIVE_SERVICE_LOADER;
        }
        if (scopeValidationInconclusive) {
            return ModuleAnalysisReason.INCONCLUSIVE_SCOPE_VALIDATION;
        }
        return ModuleAnalysisReason.NONE;
    }

    private void reportScopeWarnings(
            final ModuleAnalysisUnit unit,
            final ScopeValidationResult result) {
        for (ScopeValidationWarning warning : result.warnings()) {
            diagnostics.warn(DiagnosticContext.of(
                            "scope-validation", "module")
                            .withModule(unit.getModuleId().stableKey())
                            .withArtifact(warning.artifact().toString()),
                    warning.summary());
        }
    }

    private ModuleAnalysisResult failed(
            final ModuleAnalysisUnit unit,
            final ModuleAnalysisReason reason,
            final RuntimeException failure,
            final long start,
            final Map<String, Long> completedStages) {
        diagnostics.error(DiagnosticContext.of(
                        "module-analysis", "module")
                        .withModule(unit.getModuleId().stableKey()),
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

    private Map<String, List<DependencyChange>> changesByModule(
            final List<DependencyChange> changes) {
        final Map<String, List<DependencyChange>> result =
                new LinkedHashMap<>();
        for (DependencyChange change : changes) {
            final String key = ArtifactCoord.parse(
                    change.getModule()).diffKey();
            result.computeIfAbsent(key, ignored ->
                    new ArrayList<>()).add(change);
        }
        result.values().forEach(values -> values.sort(
                Comparator.comparing(DependencyChange::toString)));
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
        final Set<String> closure = new LinkedHashSet<>(
                ModuleClasspathOrder.reactorKeys(
                        tree, scope.getReactorCoordinates()));
        closure.remove(currentKey);
        return closure.stream()
                .map(modules::get)
                .filter(Objects::nonNull)
                .map(module -> classesPath(module, scope, outputs))
                .toList();
    }

    private List<ArtifactCoord> externalArtifacts(
            final DependencyAnalysisResult analysis,
            final ModuleDependencyTree tree,
            final ReactorAnalysisScope scope) {
        return ModuleClasspathOrder.externalArtifacts(
                analysis, tree, scope.getReactorCoordinates());
    }

    private Map<String, List<ArtifactCoord>> externalArtifactsByModule(
            final DependencyAnalysisResult analysis,
            final List<ModuleDependencyTree> trees,
            final ReactorAnalysisScope scope) {
        final Map<String, List<ArtifactCoord>> result =
                new LinkedHashMap<>();
        for (ModuleDependencyTree tree : trees) {
            result.put(tree.getModule().diffKey(),
                    externalArtifacts(analysis, tree, scope));
        }
        return Map.copyOf(result);
    }

    private boolean isAdded(final ChangePointKind kind) {
        return kind == ChangePointKind.CLASS_ADDED
                || kind == ChangePointKind.METHOD_ADDED
                || kind == ChangePointKind.FIELD_ADDED;
    }

    private String pairKey(final DependencyUpgradeKey key) {
        return key.getOldArtifact() + "->" + key.getNewArtifact();
    }

    private IJarRepository repository() {
        return Objects.requireNonNull(jarRepository,
                "Command JAR repository is not initialized");
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
     * Unique logical coordinate-pair diff.
     *
     * @param key coordinate-pair key
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
            Map<String, List<JarDiffFailure>> failuresByModule,
            int configuredWorkers,
            int actualWorkers) {
    }

    /**
     * Module results enriched with code comparison evidence.
     *
     * @param modules enriched module results
     * @param actualWorkers actual decompilation workers
     */
    private record CodeEvidenceResult(
            List<ModuleAnalysisResult> modules,
            int actualWorkers) {
    }

    /**
     * One unique logical member code comparison result.
     *
     * @param key logical member identity
     * @param evidence comparison evidence
     */
    private record MemberCodeEvidence(
            String key,
            CodeComparisonEvidence evidence) {
    }

    /**
     * Prepared filtered entrypoint indexes and isolated scan failures.
     *
     * @param indexes selected indexes keyed by module coordinate
     * @param unmatchedModules modules with zero selected executable methods
     * @param failures isolated scanner failures keyed by module coordinate
     */
    private record EntrypointPreparation(
            Map<String, EntrypointClassIndex> indexes,
            Set<String> unmatchedModules,
            Map<String, CallGraphException> failures) {

        EntrypointPreparation {
            indexes = Map.copyOf(indexes);
            unmatchedModules = Set.copyOf(unmatchedModules);
            failures = Map.copyOf(failures);
        }

        static EntrypointPreparation empty() {
            return new EntrypointPreparation(
                    Map.of(), Set.of(), Map.of());
        }
    }

    /**
     * Joined immutable preparation input for module planning.
     *
     * @param baselineScope baseline reactor scope
     * @param targetScope target reactor scope
     * @param targetBuild target build
     * @param baselineArtifactsByModule baseline logical classpath
     * @param targetArtifactsByModule target logical classpath
     * @param baselineTrees selected baseline trees
     * @param targetTrees selected target trees
     */
    private record PreparedAnalysis(
            ReactorAnalysisScope baselineScope,
            ReactorAnalysisScope targetScope,
            BuildResult targetBuild,
            Map<String, List<ArtifactCoord>> baselineArtifactsByModule,
            Map<String, List<ArtifactCoord>> targetArtifactsByModule,
            List<ModuleDependencyTree> baselineTrees,
            List<ModuleDependencyTree> targetTrees) {
    }
}
