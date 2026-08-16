package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.impact.refinement.ResultRefinementAlgorithm;
import io.github.dependencyanalysis.impact.refinement.ResultRefinementSelection;

import com.fasterxml.jackson.core.JsonGenerator;

import io.github.dependencyanalysis.build.BuildResult;
import io.github.dependencyanalysis.build.BuildRunner;
import io.github.dependencyanalysis.build.ModuleBuildOutput;
import io.github.dependencyanalysis.bytecode.BytecodeDiffEngine;
import io.github.dependencyanalysis.bytecode.BytecodeDiffResult;
import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.bytecode.ServiceLoaderResourceDiffEngine;
import io.github.dependencyanalysis.bytecode.ServiceLoaderResourceDiffResult;
import io.github.dependencyanalysis.bytecode.ServiceLoaderResourceIssue;
import io.github.dependencyanalysis.bytecode.ServiceProviderRegistration;
import io.github.dependencyanalysis.bytecode.SsaComparisonEvidence;
import io.github.dependencyanalysis.callgraph.engine.CallGraphException;
import io.github.dependencyanalysis.callgraph.engine.CallGraphFailureKind;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphConfiguration;
import io.github.dependencyanalysis.callgraph.entrypoint.EntrypointClassIndex;
import io.github.dependencyanalysis.callgraph.entrypoint.EntrypointClassScanner;
import io.github.dependencyanalysis.callgraph.entrypoint.EntrypointSelection;
import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphEngine;
import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphInput;
import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphSession;
import io.github.dependencyanalysis.callgraph.scope.ModuleScopeValidator;
import io.github.dependencyanalysis.callgraph.scope.ScopeValidationResult;
import io.github.dependencyanalysis.callgraph.scope.ScopeValidationWarning;
import io.github.dependencyanalysis.callgraph.jdk.JdkModelSelection;
import io.github.dependencyanalysis.callgraph.strategy.WalaReflectionOptions;
import io.github.dependencyanalysis.callgraph.scope.ScopeValidationException;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ChangeType;
import io.github.dependencyanalysis.dependency.DependencyAnalysisResult;
import io.github.dependencyanalysis.dependency.DependencyAnalyzer;
import io.github.dependencyanalysis.dependency.DependencyChange;
import io.github.dependencyanalysis.dependency.DependencyDiffEngine;
import io.github.dependencyanalysis.dependency.ModuleDependencyEvidence;
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
import io.github.dependencyanalysis.runtime.ReportCache;
import io.github.dependencyanalysis.workspace.WorkspaceResult;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
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
import java.util.concurrent.atomic.AtomicInteger;

// Wiki: wiki/architecture/dependency-analysis-pipelines.md - Concurrency
/** Executes the Spring backend per-module Call Graph pipeline. */
final class PerModuleImpactPipeline implements ImpactExecutionEngine {

    /** Maximum wait for common-pool cancellation. */
    private static final long COMMON_SHUTDOWN_SECONDS = 30L;

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

    /** Command-wide Call Graph algorithm. */
    private final CallGraphAlgorithm callGraphAlgorithm;

    /** Command-wide k-object receiver allocation-string depth. */
    private final int kObjDepth;

    /** Command-wide WALA ReflectionOptions. */
    private final WalaReflectionOptions reflectionOptions;

    /** Requested dependency method-body scope. */
    private final DependencyAnalysisScopeMode dependencyAnalysisScope;

    /** Command-wide JDK Method Model selection. */
    private final JdkModelSelection jdkModel;

    /** Command-wide result-refinement selection. */
    private final ResultRefinementSelection resultRefinements;

    /** Command temporary directory. */
    private final Path temporaryDirectory;

    /** Optional Call Graph benchmark diagnostics JSON. */
    private final Path callGraphDiagnosticsOutput;

    /** Optional command-owned report cache. */
    private final ReportCache reportCache;

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
        callGraphDiagnosticsOutput = options.callGraphDiagnosticsOutput();
        reportCache = options.reportCache();
        entrypointSelection = Objects.requireNonNull(
                options.entrypointSelection(), "entrypointSelection");
        callGraphAlgorithm = Objects.requireNonNull(
                options.callGraphAlgorithm(), "callGraphAlgorithm");
        kObjDepth = CallGraphAlgorithm.requireValidKObjDepth(
                options.kObjDepth());
        reflectionOptions = Objects.requireNonNull(
                options.reflectionOptions(), "reflectionOptions");
        dependencyAnalysisScope = Objects.requireNonNull(
                options.dependencyAnalysisScope(),
                "dependencyAnalysisScope");
        jdkModel = Objects.requireNonNull(options.jdkModel(), "jdkModel");
        resultRefinements = Objects.requireNonNull(
                options.resultRefinements(), "resultRefinements");
    }

    /**
     * Runs all concurrent stages through one command-wide common pool.
     *
     * @param workspace prepared Git workspaces
     * @return complete analysis run
     * @throws Exception on global preparation failure
     */
    @Override
    public AnalysisRunResult run(final WorkspaceResult workspace)
            throws Exception {
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
        try (ManagedExecutor common = executors.fixed(
                "common", analysisParallelism)) {
            try {
                return runWithCommonPool(baselineScope, targetScope,
                        elapsed, common.executor());
            } catch (Exception | Error failure) {
                common.shutdownNow();
                awaitCommonShutdown(common.executor());
                throw failure;
            }
        }
    }

    private AnalysisRunResult runWithCommonPool(
            final ReactorAnalysisScope baselineScope,
            final ReactorAnalysisScope targetScope,
            final Map<String, Long> elapsed,
            final ExecutorService commonExecutor) throws Exception {
        final long frontStart = System.currentTimeMillis();
        final FrontPreparation front = prepareFront(
                baselineScope, targetScope, commonExecutor);
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
        final List<ModuleDependencyEvidence> baselineEvidence =
                selectedEvidence(front.baselineDependencies().getModules(),
                        baselineScope);
        final List<ModuleDependencyEvidence> targetEvidence =
                selectedEvidence(targetDependencies.getModules(),
                        targetScope);
        final List<DependencyChange> changes =
                new DependencyDiffEngine().diff(
                        baselineEvidence, targetEvidence);
        final long diffStart = System.currentTimeMillis();
        final BindingResult bindings = bindAndDiff(changes,
                targetScope, baselineEvidence, targetEvidence,
                commonExecutor);
        elapsed.put("jar-diff", System.currentTimeMillis() - diffStart);
        final List<ModuleAnalysisUnit> units = units(
                new PreparedAnalysis(baselineScope, targetScope,
                        front.targetBuild(), baselineEvidence,
                        targetEvidence),
                bindings, changes);
        final EntrypointPreparation entrypoints = prepareEntrypoints(
                units, bindings.failedModules());
        final Set<String> unmatchedEntrypointModules =
                entrypoints.unmatchedModules();
        final long modulesStart = System.currentTimeMillis();
        final ModuleAnalysisBatch analyzed = analyzeModules(
                units, bindings.failedModules(),
                unmatchedEntrypointModules, entrypoints,
                commonExecutor);
        elapsed.put("module-analysis",
                System.currentTimeMillis() - modulesStart);
        final long codeStart = System.currentTimeMillis();
        final CodeEvidenceResult codeEvidence = buildCodeComparisons(
                analyzed.modules(), commonExecutor);
        elapsed.put("code-comparison",
                System.currentTimeMillis() - codeStart);
        final AnalysisRunConfiguration configuration =
                new AnalysisRunConfiguration(
                        entrypointSelection, callGraphAlgorithm,
                        kObjDepth, reflectionOptions,
                        dependencyAnalysisScope, jdkModel,
                        resultRefinements);
        if (callGraphDiagnosticsOutput != null && reportCache != null) {
            new CallGraphDiagnosticsExporter(
                    diagnostics, javaRuntime, repository()).writeFragments(
                    callGraphDiagnosticsOutput, configuration,
                    reportCache.fragments());
        } else if (callGraphDiagnosticsOutput != null) {
            new CallGraphDiagnosticsExporter(
                    diagnostics, javaRuntime, repository()).write(
                    callGraphDiagnosticsOutput, configuration,
                    codeEvidence.modules());
        }
        return new AnalysisRunResult(targetScope.getMode(),
                overallStatus(codeEvidence.modules()), changes,
                codeEvidence.modules(), new AnalysisConcurrency(
                        analysisParallelism,
                        bindings.actualWorkers(),
                        analyzed.actualImpactQueryWorkers(),
                        codeEvidence.actualWorkers()), elapsed,
                configuration);
        } finally {
            jarRepository = null;
        }
    }

    private CodeEvidenceResult buildCodeComparisons(
            final List<ModuleAnalysisResult> modules,
            final ExecutorService commonExecutor)
            throws InterruptedException {
        final Map<String, List<BoundChangePoint>> requests =
                new LinkedHashMap<>();
        for (ModuleAnalysisResult module : modules) {
            codeComparisonPoints(module).forEach(point -> requests
                    .computeIfAbsent(codeEvidenceKey(point), ignored ->
                            new ArrayList<>()).add(point));
        }
        if (requests.isEmpty()) {
            return new CodeEvidenceResult(modules, 0);
        }
        final int workers = Math.min(analysisParallelism, requests.size());
        final CompletionService<MemberCodeEvidence> completion =
                new ExecutorCompletionService<>(commonExecutor);
        final List<Map.Entry<String, List<BoundChangePoint>>> ordered =
                requests.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey()).toList();
        int next = 0;
        int completed = 0;
        final List<Future<MemberCodeEvidence>> submitted = new ArrayList<>();
        while (next < ordered.size() && next < workers) {
            submitted.add(submitCodeComparison(
                    completion, ordered.get(next++)));
        }
        final Map<String, CodeComparisonEvidence> evidence =
                new LinkedHashMap<>();
        try {
            while (completed < ordered.size()) {
                try {
                    final MemberCodeEvidence value = completion.take().get();
                    evidence.put(value.key(), value.evidence());
                } catch (ExecutionException exception) {
                    throw new IllegalStateException(
                            "Unexpected code comparison failure",
                            exception.getCause());
                }
                completed++;
                if (next < ordered.size()) {
                    submitted.add(submitCodeComparison(
                            completion, ordered.get(next++)));
                }
            }
        } catch (InterruptedException exception) {
            submitted.forEach(value -> value.cancel(true));
            Thread.currentThread().interrupt();
            throw exception;
        } catch (RuntimeException exception) {
            submitted.forEach(value -> value.cancel(true));
            throw exception;
        }
        final List<ModuleAnalysisResult> enriched = new ArrayList<>();
        for (ModuleAnalysisResult module : modules) {
            final Map<BoundChangePoint, CodeComparisonEvidence> bound =
                    new LinkedHashMap<>();
            codeComparisonPoints(module).forEach(point -> bound.put(
                    point, evidence.get(codeEvidenceKey(point))));
            enriched.add(module.toBuilder().codeComparisons(bound).build());
        }
        return new CodeEvidenceResult(enriched, workers);
    }

    static List<BoundChangePoint> codeComparisonPoints(
            final ModuleAnalysisResult module) {
        final Set<BoundChangePoint> relevant = new LinkedHashSet<>();
        module.getImpactPaths().forEach(path -> relevant.add(
                path.getTerminal().getChangePoint()));
        module.getStructuralPaths().forEach(path -> relevant.add(
                path.getChangePoint()));
        return relevant.stream().sorted(Comparator.comparing(
                BoundChangePoint::stableKey)).toList();
    }

    private Future<MemberCodeEvidence> submitCodeComparison(
            final CompletionService<MemberCodeEvidence> completion,
            final Map.Entry<String, List<BoundChangePoint>> request) {
        return completion.submit(() -> codeComparison(
                request.getKey(), request.getValue().get(0)));
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
                            CodeComparisonStatus.UNAVAILABLE, List.of(),
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
                + point.getNewHash() + "|" + point.getAccessTransition()
                .map(value -> value.stableKey()).orElse("NO_ACCESS");
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
            final ReactorAnalysisScope targetScope,
            final ExecutorService commonExecutor) throws Exception {
        final CompletionService<PreparationBranch> completion =
                new ExecutorCompletionService<>(commonExecutor);
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
        } catch (RuntimeException exception) {
            futures.forEach(value -> value.cancel(true));
            throw exception;
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

    private void awaitCommonShutdown(
            final ExecutorService executor) {
        boolean interrupted = false;
        try {
            final long deadline = System.nanoTime()
                    + TimeUnit.SECONDS.toNanos(
                    COMMON_SHUTDOWN_SECONDS);
            while (!executor.isTerminated()
                    && System.nanoTime() < deadline) {
                try {
                    executor.awaitTermination(1L, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (!executor.isTerminated()) {
                diagnostics.warn("pipeline",
                        "Common executor did not terminate within "
                                + COMMON_SHUTDOWN_SECONDS + "s");
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
                scope.getReactorCoordinates(), diagnostics, javaHome,
                mavenRuntime.getExecutable(), mavenArguments)
                .withPluginRuntime(pluginRuntime)
                .withEvidenceDirectory(temporaryDirectory
                        .resolve("dependency-evidence")
                        .resolve(side))
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

    private List<ModuleDependencyEvidence> selectedEvidence(
            final List<ModuleDependencyEvidence> evidence,
            final ReactorAnalysisScope scope) {
        final Set<String> keys = scope.getModules().stream()
                .map(ModuleId::coordinateKey)
                .collect(java.util.stream.Collectors.toSet());
        return evidence.stream()
                .filter(value -> keys.contains(
                        value.getModule().diffKey()))
                .sorted(Comparator.comparing(value ->
                        value.getModule().diffKey()))
                .toList();
    }

    private BindingResult bindAndDiff(
            final List<DependencyChange> changes,
            final ReactorAnalysisScope targetScope,
            final List<ModuleDependencyEvidence> baselineEvidence,
            final List<ModuleDependencyEvidence> targetEvidence,
            final ExecutorService commonExecutor)
            throws Exception {
        final Map<String, ModuleId> targetModules = moduleMap(
                targetScope.getModules());
        final Map<String, ModuleDependencyEvidence> baselineEvidenceMap =
                evidenceMap(baselineEvidence);
        final Map<String, ModuleDependencyEvidence> targetEvidenceMap =
                evidenceMap(targetEvidence);
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
            final ModuleDependencyEvidence baseline =
                    baselineEvidenceMap.get(moduleKey);
            final ModuleDependencyEvidence target =
                    targetEvidenceMap.get(moduleKey);
            if (baseline == null || target == null) {
                throw new IllegalStateException(
                        "Merged dependency evidence is missing: module="
                                + change.getModule());
            }
            baseline.requireArtifact("baseline", change.getOldArtifact());
            target.requireArtifact("target", change.getNewArtifact());
            final DependencyUpgradeKey key = new DependencyUpgradeKey(
                    module, change.getScope(), change.getOldArtifact(),
                    change.getNewArtifact());
            groups.computeIfAbsent(pairKey(key), ignored ->
                    new ArrayList<>()).add(key);
        }
        final int workers = groups.isEmpty() ? 0
                : Math.min(groups.size(), jarDiffWorkerLimit());
        final DiagnosticContext context = DiagnosticContext.of(
                "jar-diff", "aggregate");
        diagnostics.startStage(context, "pairs="
                + groups.size() + "; workers=" + workers);
        try {
            final BindingResult result = parallelJarDiff(
                    groups, commonExecutor);
            diagnostics.endStage(context, "changes="
                    + result.changeCount() + "; pairs="
                    + result.pairCount() + "; failedPairs="
                    + result.failedPairCount() + "; workers="
                    + result.actualWorkers());
            return result;
        } catch (InterruptedException | RuntimeException exception) {
            diagnostics.failStage(context, "pairs="
                    + groups.size() + "; workers=" + workers + "; reason="
                    + Objects.requireNonNullElse(exception.getMessage(),
                    exception.getClass().getName()));
            throw exception;
        }
    }

    private BindingResult parallelJarDiff(
            final Map<String, List<DependencyUpgradeKey>> groups,
            final ExecutorService commonExecutor)
            throws InterruptedException {
        if (groups.isEmpty()) {
            return new BindingResult(Map.of(), Set.of(), Map.of(),
                    Map.of(), Map.of(), Map.of(), Map.of(),
                    0, 0, 0, 0);
        }
        final int configuredWorkers = jarDiffWorkerLimit();
        final int workers = Math.min(groups.size(), configuredWorkers);
        final List<Future<PairDiff>> futures = new ArrayList<>();
        for (Map.Entry<String, List<DependencyUpgradeKey>> entry
                : groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey()).toList()) {
            futures.add(commonExecutor.submit(() -> diffPair(
                    entry.getKey(), entry.getValue().get(0))));
        }
        final Map<String, List<ChangePoint>> pairPoints =
                new LinkedHashMap<>();
        final Set<String> failedModules = new LinkedHashSet<>();
        final Map<String, List<JarDiffFailure>> failuresByModule =
                new LinkedHashMap<>();
        final Map<String, List<ServiceProviderRegistration>> baselineByPair =
                new LinkedHashMap<>();
        final Map<String, List<ServiceProviderRegistration>> removedByPair =
                new LinkedHashMap<>();
        final Map<String, List<ServiceLoaderResourceIssue>> issuesByPair =
                new LinkedHashMap<>();
        final Map<String, List<SsaComparisonEvidence>> ssaByPair =
                new LinkedHashMap<>();
        try {
            for (Future<PairDiff> future : futures) {
                final PairDiff pair;
                try {
                    pair = future.get();
                } catch (ExecutionException exception) {
                    throw new IllegalStateException(
                            "Unexpected JAR comparison failure",
                            exception.getCause());
                }
                if (pair.failure() == null) {
                    pairPoints.put(pair.key(), pair.points());
                    baselineByPair.put(pair.key(),
                            pair.baselineServiceRegistrations());
                    removedByPair.put(pair.key(),
                            pair.removedServiceRegistrations());
                    issuesByPair.put(pair.key(),
                            pair.serviceLoaderResourceIssues());
                    ssaByPair.put(pair.key(), pair.ssaComparisons());
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
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw exception;
        } finally {
            futures.forEach(value -> value.cancel(true));
        }
        final Map<String, List<BoundChangePoint>> byModule =
                new LinkedHashMap<>();
        final Map<String, List<ServiceProviderRegistration>>
                baselineByModule = new LinkedHashMap<>();
        final Map<String, List<ServiceProviderRegistration>>
                removedByModule = new LinkedHashMap<>();
        final Map<String, List<ServiceLoaderResourceIssue>> issuesByModule =
                new LinkedHashMap<>();
        final Map<String, List<SsaComparisonEvidence>> ssaByModule =
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
                    target.add(new BoundChangePoint(key, point));
                }
                final String moduleKey = key.getModuleId().coordinateKey();
                baselineByModule.computeIfAbsent(moduleKey,
                        ignored -> new ArrayList<>()).addAll(
                        baselineByPair.getOrDefault(entry.getKey(),
                                List.of()));
                removedByModule.computeIfAbsent(moduleKey,
                        ignored -> new ArrayList<>()).addAll(
                        removedByPair.getOrDefault(entry.getKey(),
                                List.of()));
                issuesByModule.computeIfAbsent(moduleKey,
                        ignored -> new ArrayList<>()).addAll(
                        issuesByPair.getOrDefault(entry.getKey(), List.of()));
                ssaByModule.computeIfAbsent(moduleKey,
                        ignored -> new ArrayList<>()).addAll(
                        ssaByPair.getOrDefault(entry.getKey(), List.of()));
            }
        }
        byModule.values().forEach(values -> values.sort(
                Comparator.comparing(BoundChangePoint::stableKey)));
        failuresByModule.values().forEach(values -> values.sort(
                Comparator.comparing(JarDiffFailure::stableKey)));
        final int changeCount = pairPoints.values().stream()
                .mapToInt(List::size).sum();
        final int failedPairCount = groups.size() - pairPoints.size();
        return new BindingResult(byModule, failedModules, failuresByModule,
                baselineByModule, removedByModule, issuesByModule,
                ssaByModule,
                workers, changeCount, groups.size(), failedPairCount);
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
            final BytecodeDiffEngine engine = resultRefinements.isEnabled(
                    ResultRefinementAlgorithm.SSA_EQUIVALENCE)
                    ? new BytecodeDiffEngine(kinds, javaRuntime)
                    : new BytecodeDiffEngine(kinds);
            final BytecodeDiffResult bytecode = engine.diff(
                    change, repository());
            final List<ChangePoint> points = bytecode.changePoints();
            final ServiceLoaderResourceDiffResult services =
                    new ServiceLoaderResourceDiffEngine(kinds).diff(
                            upgrade, repository(), points);
            final Set<ChangePoint> unique = new LinkedHashSet<>(points);
            unique.addAll(services.changePoints());
            final List<ChangePoint> combined = List.copyOf(unique);
            diagnostics.debug(context, "JAR comparison completed; rawChanges="
                    + bytecode.rawChangePointCount() + "; changes="
                    + combined.size() + "; ssaEligible="
                    + bytecode.ssaComparisons().size()
                    + "; ssaMatchedSuppressed="
                    + bytecode.matchedSuppressedCount()
                    + "; ssaDifferentRetained="
                    + bytecode.differentRetainedCount()
                    + "; ssaUnknownRetained="
                    + bytecode.unknownRetainedCount()
                    + "; ssaElapsedMillis="
                    + bytecode.ssaElapsedMillis());
            return new PairDiff(key, combined,
                    services.baselineRegistrations(),
                    services.removedRegistrations(), services.issues(),
                    bytecode.ssaComparisons(), null);
        } catch (Exception exception) {
            return new PairDiff(key, List.of(), List.of(), List.of(),
                    List.of(), List.of(),
                    JarDiffFailureDiagnostic.emit(
                            diagnostics, context, exception));
        }
    }

    private List<ModuleAnalysisUnit> units(
            final PreparedAnalysis prepared,
            final BindingResult bindings,
            final List<DependencyChange> changes) {
        final ReactorAnalysisScope baselineScope = prepared.baselineScope();
        final ReactorAnalysisScope targetScope = prepared.targetScope();
        final BuildResult build = prepared.targetBuild();
        final List<ModuleDependencyEvidence> baselineEvidence =
                prepared.baselineEvidence();
        final List<ModuleDependencyEvidence> targetEvidence =
                prepared.targetEvidence();
        final Map<String, ModuleId> baselineModules = moduleMap(
                baselineScope.getModules());
        final Map<String, ModuleId> targetModules = moduleMap(
                targetScope.getModules());
        final Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(baselineModules.keySet());
        allKeys.addAll(targetModules.keySet());
        final Map<String, ModuleDependencyEvidence> baselineEvidenceMap =
                evidenceMap(baselineEvidence);
        final Map<String, ModuleDependencyEvidence> targetEvidenceMap =
                evidenceMap(targetEvidence);
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
            final ModuleDependencyEvidence targetDependencies =
                    targetEvidenceMap.get(key);
            final ModuleDependencyEvidence baselineDependencies =
                    baselineEvidenceMap.get(key);
            final List<Path> reactorClasses = targetDependencies == null
                    ? List.of() : reactorClasses(targetDependencies,
                    targetScope, outputs, key);
            final ModulePresence presence = target == null
                    ? ModulePresence.BASELINE_ONLY
                    : baseline == null ? ModulePresence.TARGET_ONLY
                    : ModulePresence.BOTH;
            final ModuleChangeSet changeSet = new ModuleChangeSet(
                    changesByModule.getOrDefault(key, List.of()),
                    bindings.pointsByModule().getOrDefault(
                            key, List.of()),
                    bindings.failuresByModule().getOrDefault(
                            key, List.of()),
                    bindings.baselineServiceRegistrationsByModule()
                            .getOrDefault(key, List.of()),
                    bindings.removedServiceRegistrationsByModule()
                            .getOrDefault(key, List.of()),
                    bindings.serviceLoaderResourceIssuesByModule()
                            .getOrDefault(key, List.of()),
                    bindings.ssaComparisonsByModule()
                            .getOrDefault(key, List.of()));
            result.add(new ModuleAnalysisUnit(identity, presence, classes,
                    reactorClasses, ModuleDependencyInputs.fromEvidence(
                    targetDependencies, baselineDependencies,
                    changedArtifacts(changeSet.changePoints()),
                    dependencyAnalysisScope),
                    changeSet));
        }
        result.sort(Comparator.comparing(unit ->
                unit.getModuleId().stableKey()));
        return result;
    }

    private Set<ArtifactCoord> changedArtifacts(
            final List<BoundChangePoint> points) {
        final Set<ArtifactCoord> result = new LinkedHashSet<>();
        points.stream().sorted(Comparator.comparing(
                BoundChangePoint::stableKey)).forEach(point -> result.add(
                point.getDependencyUpgradeKey().getNewArtifact()));
        return Set.copyOf(result);
    }

    private ModuleAnalysisBatch analyzeModules(
            final List<ModuleAnalysisUnit> units,
            final Set<String> failedDiffModules,
            final Set<String> unmatchedEntrypointModules,
            final EntrypointPreparation entrypoints,
            final ExecutorService commonExecutor)
            throws InterruptedException {
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
            return new ModuleAnalysisBatch(result.stream()
                    .sorted(moduleResultComparator()).toList(), 0);
        }
        active.sort(Comparator.comparing(unit ->
                unit.getModuleId().stableKey()));
        final AtomicInteger actualImpactQueryWorkers = new AtomicInteger();
        final ModuleAnalysisSnapshotter snapshotter =
                new ModuleAnalysisSnapshotter();
        final CallGraphDiagnosticsExporter diagnosticsExporter =
                callGraphDiagnosticsOutput == null || reportCache == null
                ? null : new CallGraphDiagnosticsExporter(
                diagnostics, javaRuntime, repository());
        for (ModuleAnalysisUnit unit : active) {
            final String key = unit.getModuleId().coordinateKey();
            ModuleAnalysisResult module = analyzeModuleStage(
                    unit, failedDiffModules.contains(key),
                    entrypoints.indexes().get(key),
                    entrypoints.failures().get(key), commonExecutor,
                    actualImpactQueryWorkers);
            if (diagnosticsExporter != null
                    && module.getSession() != null
                    && module.getSession().getTopology().isPresent()) {
                final ModuleAnalysisResult live = module;
                reportCache.writeJsonLines("diagnostic-module",
                        module.getModuleId().stableKey(),
                        List.of(jsonRecord(json -> diagnosticsExporter
                                .writeModuleRecord(json, live,
                                        resultRefinements))));
            }
            module = snapshotter.detach(module);
            result.add(module);
        }
        result.sort(moduleResultComparator());
        return new ModuleAnalysisBatch(List.copyOf(result),
                actualImpactQueryWorkers.get());
    }

    private java.util.function.Consumer<JsonGenerator> jsonRecord(
            final CheckedJsonWriter writer) {
        return json -> {
            try {
                writer.write(json);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        };
    }

    @FunctionalInterface
    private interface CheckedJsonWriter {
        void write(JsonGenerator json) throws IOException;
    }

    private ModuleAnalysisResult analyzeModuleStage(
            final ModuleAnalysisUnit unit,
            final boolean diffFailed,
            final EntrypointClassIndex preparedEntrypoints,
            final CallGraphException entrypointFailure,
            final ExecutorService queryExecutor,
            final AtomicInteger actualImpactQueryWorkers) {
        final DiagnosticContext context = DiagnosticContext.of(
                "module-analysis", "module").withModule(
                unit.getModuleId().stableKey());
        diagnostics.startStage(context);
        try {
            return executeModuleAnalysis(unit, diffFailed,
                    preparedEntrypoints, entrypointFailure, queryExecutor,
                    actualImpactQueryWorkers);
        } finally {
            diagnostics.endStage(context);
        }
    }

    private ModuleAnalysisResult executeModuleAnalysis(
            final ModuleAnalysisUnit unit,
            final boolean diffFailed,
            final EntrypointClassIndex preparedEntrypoints,
            final CallGraphException entrypointFailure,
            final ExecutorService queryExecutor,
            final AtomicInteger actualImpactQueryWorkers) {
        final long start = System.currentTimeMillis();
        final Map<String, Long> stageElapsed = new LinkedHashMap<>();
        if (unit.getChangePoints().isEmpty()) {
            return noChangePoints(unit, start, stageElapsed);
        }
        final List<BoundChangePoint> graphPoints = unit.getChangePoints()
                .stream().filter(point -> !isAdded(
                        point.getChangePoint().getKind())).toList();
        if (graphPoints.isEmpty()) {
            return noGraphPoints(unit, diffFailed, start, stageElapsed);
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
            final ModuleCallGraphInput graphInput =
                    new ModuleCallGraphInputAdapter().adapt(unit);
            long stageStart = System.currentTimeMillis();
            final ScopeValidationResult scopeValidation =
                    new ModuleScopeValidator(repository()).validate(
                            graphInput);
            stageElapsed.put("scope-validation",
                    System.currentTimeMillis() - stageStart);
            reportScopeWarnings(unit, scopeValidation);
            stageStart = System.currentTimeMillis();
            final ModuleCallGraphSession session =
                    new ModuleCallGraphEngine(diagnostics, javaRuntime,
                            entrypointSelection, new CallGraphConfiguration(
                            callGraphAlgorithm, kObjDepth, reflectionOptions),
                            jdkModel,
                            repository())
                            .build(graphInput, entrypointIndex,
                                    callGraphTimeoutSeconds,
                                    callGraphDiagnosticsOutput != null);
            stageElapsed.put("call-graph",
                    System.currentTimeMillis() - stageStart);
            stageStart = System.currentTimeMillis();
            final DiagnosticContext evidenceContext = DiagnosticContext.of(
                    "module-analysis", "evidence-analysis").withModule(
                    unit.getModuleId().stableKey());
            diagnostics.startStage(evidenceContext,
                    "changes=" + unit.getChangePoints().size()
                            + "; graphNodes="
                            + session.getGraph().getNumberOfNodes());
            final StructuralReferenceIndex structuralReferences;
            final ChangePointEvidenceIndex changePointEvidence;
            try {
                structuralReferences = new StructuralImpactScanner(
                        repository()).scan(unit, session.getOwnership());
                changePointEvidence = new ChangePointEvidenceCollector(
                        diagnostics).collect(
                            unit, session.getGraph(), session.getOwnership(),
                            session.getDynamicEvidence(), structuralReferences,
                            session.getStrategyCapabilities(),
                            session::isBodyAvailable);
                final int evidenceCount = changePointEvidence.resolutions()
                        .stream().mapToInt(value -> value.evidence().size())
                        .sum();
                final int bindingCount = changePointEvidence
                        .reverseBfsBindings().values().stream()
                        .mapToInt(List::size).sum();
                diagnostics.endStage(evidenceContext,
                        "structuralReferences="
                                + structuralReferences.references().size()
                                + "; evidence=" + evidenceCount
                                + "; queryNodes=" + changePointEvidence
                                .reverseBfsBindings().size()
                                + "; bindings=" + bindingCount);
            } catch (RuntimeException exception) {
                diagnostics.failStage(evidenceContext,
                        "reason="
                                + Objects.requireNonNullElse(
                                exception.getMessage(),
                                exception.getClass().getName()));
                throw exception;
            }
            stageElapsed.put("evidence-analysis",
                    System.currentTimeMillis() - stageStart);
            stageStart = System.currentTimeMillis();
            final ModuleImpactQueryResult query =
                    new ModuleImpactTracer(diagnostics, queryExecutor,
                            analysisParallelism, workers ->
                            actualImpactQueryWorkers.accumulateAndGet(
                                    workers, Math::max),
                            resultRefinements).trace(
                                    unit, session, changePointEvidence);
            stageElapsed.put("impact-query",
                    System.currentTimeMillis() - stageStart);
            return finalizeModule(new ModuleFinalizationInput(
                    unit, diffFailed, start, stageElapsed,
                    scopeValidation, session, changePointEvidence, query));
        } catch (ScopeValidationException exception) {
            return failed(unit,
                    ModuleAnalysisReason.FAILED_SCOPE_VALIDATION,
                    exception, start, stageElapsed);
        } catch (CallGraphException exception) {
            final ModuleAnalysisReason reason = exception.getKind()
                    == CallGraphFailureKind.TIMEOUT
                    ? ModuleAnalysisReason.FAILED_CALL_GRAPH_TIMEOUT
                    : ModuleAnalysisReason.FAILED_ANALYSIS;
            return failed(unit, reason, exception, start, stageElapsed);
        } catch (RuntimeException exception) {
            if (Thread.currentThread().isInterrupted()) {
                throw exception;
            }
            return failed(unit, ModuleAnalysisReason.FAILED_ANALYSIS,
                    exception, start, stageElapsed);
        }
    }

    private ModuleAnalysisResult finalizeModule(
            final ModuleFinalizationInput input) {
        final long stageStart = System.currentTimeMillis();
        final ModuleAnalysisUnit unit = input.unit();
        final DiagnosticContext context = DiagnosticContext.of(
                "module-analysis", "module-finalization").withModule(
                unit.getModuleId().stableKey());
        diagnostics.startStage(context);
        try {
            final ModuleCallGraphSession session = input.session();
            final ModuleImpactQueryResult query = input.query();
            final List<String> limitations = new ArrayList<>(
                    input.scopeValidation().limitations());
            limitations.addAll(session.getModelLimitations());
            final CallGraphCoverageMapper coverageMapper =
                    new CallGraphCoverageMapper();
            final List<CoverageLimitation> boundaryLimitations =
                    coverageMapper.boundary(session.getDependencyBoundary());
            limitations.addAll(boundaryLimitations.stream()
                    .map(CoverageLimitation::summary).toList());
            limitations.addAll(query.getLimitations().stream()
                    .map(QueryLimitation::summary).toList());
            if (input.diffFailed()) {
                limitations.addAll(unit.getJarDiffFailureSummaries());
            }
            final List<CoverageLimitation> coverage = new ArrayList<>();
            input.scopeValidation().warnings().stream()
                    .map(coverageMapper::scope).forEach(coverage::add);
            session.getCoverageLimitations().stream()
                    .map(coverageMapper::model).forEach(coverage::add);
            coverage.addAll(boundaryLimitations);
            coverage.addAll(input.evidence().limitations());
            coverage.addAll(query.getLimitations());
            final ModuleAnalysisReason reason = ModuleCoverageReducer.reduce(
                    input.diffFailed(), coverage);
            final boolean inconclusive = reason != ModuleAnalysisReason.NONE;
            input.stageElapsed().put("module-finalization",
                    System.currentTimeMillis() - stageStart);
            final ModuleAnalysisResult result =
                    new ModuleAnalysisResult.Builder(unit)
                            .status(inconclusive
                                            ? ModuleAnalysisStatus.INCONCLUSIVE
                                            : ModuleAnalysisStatus.SUCCESS,
                                    reason, inconclusive
                                            ? "Analysis completed with "
                                            + "coverage limitations"
                                            : "Analysis completed within "
                                            + "declared model")
                            .session(session)
                            .changePointEvidence(input.evidence())
                            .duplicateClassResolutions(
                                    session.getDuplicateClassResolutions())
                            .impactPaths(query.getPaths())
                            .structuralPaths(query.getStructuralPaths())
                            .dispositions(query.getDispositions())
                            .observations(query.getObservations())
                            .receiverRefinement(query.getReceiverRefinement())
                            .limitations(limitations)
                            .elapsedMillis(System.currentTimeMillis()
                                    - input.moduleStart())
                            .stageElapsedMillis(input.stageElapsed())
                            .build();
            diagnostics.endStage(context,
                    "status=" + result.getStatus()
                            + "; impactPaths="
                            + result.getImpactPaths().size()
                            + "; structuralPaths="
                            + result.getStructuralPaths().size());
            return result;
        } catch (RuntimeException exception) {
            diagnostics.failStage(context, "reason="
                    + Objects.requireNonNullElse(exception.getMessage(),
                    exception.getClass().getName()));
            throw exception;
        }
    }

    private ModuleAnalysisResult noChangePoints(
            final ModuleAnalysisUnit unit,
            final long start,
            final Map<String, Long> stageElapsed) {
        return new ModuleAnalysisResult.Builder(unit)
                .status(ModuleAnalysisStatus.INCONCLUSIVE,
                        ModuleAnalysisReason.INCONCLUSIVE_BYTECODE_DIFF,
                        "All relevant coordinate-pair JAR diffs failed")
                .limitations(unit.getJarDiffFailureSummaries())
                .elapsedMillis(System.currentTimeMillis() - start)
                .stageElapsedMillis(stageElapsed)
                .build();
    }

    private ModuleAnalysisResult noGraphPoints(
            final ModuleAnalysisUnit unit,
            final boolean diffFailed,
            final long start,
            final Map<String, Long> stageElapsed) {
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
                        ? unit.getJarDiffFailureSummaries() : List.of())
                .elapsedMillis(System.currentTimeMillis() - start)
                .stageElapsedMillis(stageElapsed)
                .build();
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

    private Map<String, ModuleDependencyEvidence> evidenceMap(
            final List<ModuleDependencyEvidence> evidence) {
        final Map<String, ModuleDependencyEvidence> result =
                new LinkedHashMap<>();
        evidence.forEach(value -> result.put(
                value.getModule().diffKey(), value));
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
            final ModuleDependencyEvidence evidence,
            final ReactorAnalysisScope scope,
            final Map<String, ModuleBuildOutput> outputs,
            final String currentKey) {
        final Map<String, ModuleId> modules = moduleMap(
                scope.getAllModules());
        final Set<String> closure = new LinkedHashSet<>(
                ModuleClasspathOrder.reactorKeys(evidence));
        closure.remove(currentKey);
        return closure.stream()
                .map(modules::get)
                .filter(Objects::nonNull)
                .map(module -> classesPath(module, scope, outputs))
                .toList();
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
     * @param baselineServiceRegistrations valid baseline provider facts
     * @param removedServiceRegistrations removed provider facts
     * @param serviceLoaderResourceIssues non-fatal resource Diff issues
     * @param ssaComparisons ChangePoint-collection SSA evidence
     * @param failure failure detail, nullable
     */
    private record PairDiff(
            String key,
            List<ChangePoint> points,
            List<ServiceProviderRegistration> baselineServiceRegistrations,
            List<ServiceProviderRegistration> removedServiceRegistrations,
            List<ServiceLoaderResourceIssue> serviceLoaderResourceIssues,
            List<SsaComparisonEvidence> ssaComparisons,
            String failure) {
    }

    /**
     * Bound ChangePoints and isolated diff failures.
     *
     * @param pointsByModule points keyed by module coordinate key
     * @param failedModules modules with at least one failed pair
     * @param failuresByModule stable failed pair evidence per module
     * @param baselineServiceRegistrationsByModule baseline provider facts
     * @param removedServiceRegistrationsByModule removed provider facts
     * @param serviceLoaderResourceIssuesByModule resource Diff issues
     * @param ssaComparisonsByModule ChangePoint-collection SSA evidence
     * @param actualWorkers actual JAR diff workers
     * @param changeCount unique successful ChangePoints
     * @param pairCount unique logical JAR pairs
     * @param failedPairCount failed logical JAR pairs
     */
    private record BindingResult(
            Map<String, List<BoundChangePoint>> pointsByModule,
            Set<String> failedModules,
            Map<String, List<JarDiffFailure>> failuresByModule,
            Map<String, List<ServiceProviderRegistration>>
                    baselineServiceRegistrationsByModule,
            Map<String, List<ServiceProviderRegistration>>
                    removedServiceRegistrationsByModule,
            Map<String, List<ServiceLoaderResourceIssue>>
                    serviceLoaderResourceIssuesByModule,
            Map<String, List<SsaComparisonEvidence>>
                    ssaComparisonsByModule,
            int actualWorkers,
            int changeCount,
            int pairCount,
            int failedPairCount) {
    }

    /**
     * Typed Module finalization input.
     *
     * @param unit canonical Module input
     * @param diffFailed whether any relevant JAR diff failed
     * @param moduleStart Module analysis wall-clock start
     * @param stageElapsed mutable completed stage timings
     * @param scopeValidation validated target scope
     * @param session live Call Graph session
     * @param evidence complete ChangePoint Evidence index
     * @param query completed Impact Query result
     */
    private record ModuleFinalizationInput(
            ModuleAnalysisUnit unit,
            boolean diffFailed,
            long moduleStart,
            Map<String, Long> stageElapsed,
            ScopeValidationResult scopeValidation,
            ModuleCallGraphSession session,
            ChangePointEvidenceIndex evidence,
            ModuleImpactQueryResult query) {
    }

    /**
     * Serial Module results and observed QueryNode concurrency.
     *
     * @param modules stable Module results
     * @param actualImpactQueryWorkers maximum active QueryNode workers
     */
    private record ModuleAnalysisBatch(
            List<ModuleAnalysisResult> modules,
            int actualImpactQueryWorkers) {
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
     * @param baselineEvidence selected baseline dependency evidence
     * @param targetEvidence selected target dependency evidence
     */
    private record PreparedAnalysis(
            ReactorAnalysisScope baselineScope,
            ReactorAnalysisScope targetScope,
            BuildResult targetBuild,
            List<ModuleDependencyEvidence> baselineEvidence,
            List<ModuleDependencyEvidence> targetEvidence) {
    }
}
