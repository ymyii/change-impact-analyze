package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.BinaryDirectoryTreeModule;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.classLoader.ShrikeClass;
import com.ibm.wala.classLoader.SyntheticClass;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.impact.ModuleAnalysisUnit;
import io.github.dependencyanalysis.impact.StructuralImpactScanner;
import io.github.dependencyanalysis.impact.StructuralReferenceIndex;
import io.github.dependencyanalysis.jar.IJarRepository;
import io.github.dependencyanalysis.jar.JarLease;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarFile;

/** Builds one selected WALA Call Graph per analysis module. */
public final class ModuleCallGraphEngine {

    /** Duplicate winner examples retained in one warning. */
    private static final int MAX_DUPLICATE_EXAMPLES = 3;

    /** Diagnostics. */
    private final DiagnosticLog diagnostics;

    /** Target JDK 8. */
    private final JavaRuntimeDescriptor javaRuntime;

    /** User-selected PROJECT root boundary. */
    private final EntrypointSelection entrypointSelection;

    /** Command-wide Call Graph algorithm. */
    private final CallGraphAlgorithm algorithm;

    /** Command-wide WALA ReflectionOptions. */
    private final WalaReflectionOptions reflectionOptions;

    /** Command-scoped dependency repository. */
    private final IJarRepository jarRepository;

    /** Exact invokedynamic models. */
    private final InvokeDynamicBootstrapModelRegistry dynamicModels;

    /**
     * Creates a module Call Graph engine.
     *
     * @param collector diagnostics
     * @param runtime target JDK runtime
     * @param repository dependency repository
     */
    public ModuleCallGraphEngine(
            final DiagnosticLog collector,
            final JavaRuntimeDescriptor runtime,
            final IJarRepository repository) {
        this(collector, runtime, EntrypointSelection.allProjectClasses(),
                CallGraphAlgorithm.defaultAlgorithm(), repository,
                InvokeDynamicBootstrapModelRegistry.jdk8Defaults());
    }

    /**
     * Creates an engine with an explicit Call Graph algorithm.
     *
     * @param collector diagnostics
     * @param runtime target JDK runtime
     * @param selectedAlgorithm Call Graph algorithm
     * @param repository dependency repository
     */
    public ModuleCallGraphEngine(
            final DiagnosticLog collector,
            final JavaRuntimeDescriptor runtime,
            final CallGraphAlgorithm selectedAlgorithm,
            final IJarRepository repository) {
        this(collector, runtime, EntrypointSelection.allProjectClasses(),
                selectedAlgorithm, repository,
                InvokeDynamicBootstrapModelRegistry.jdk8Defaults());
    }

    /**
     * Creates an engine with a user-selected PROJECT root boundary.
     *
     * @param collector diagnostics
     * @param runtime target JDK runtime
     * @param selection entrypoint class selection
     * @param repository dependency repository
     */
    public ModuleCallGraphEngine(
            final DiagnosticLog collector,
            final JavaRuntimeDescriptor runtime,
            final EntrypointSelection selection,
            final IJarRepository repository) {
        this(collector, runtime, selection, repository,
                InvokeDynamicBootstrapModelRegistry.jdk8Defaults());
    }

    /**
     * Creates an engine with explicit roots and Call Graph algorithm.
     *
     * @param collector diagnostics
     * @param runtime target JDK runtime
     * @param selection entrypoint class selection
     * @param selectedAlgorithm Call Graph algorithm
     * @param repository dependency repository
     */
    public ModuleCallGraphEngine(
            final DiagnosticLog collector,
            final JavaRuntimeDescriptor runtime,
            final EntrypointSelection selection,
            final CallGraphAlgorithm selectedAlgorithm,
            final IJarRepository repository) {
        this(collector, runtime, selection, selectedAlgorithm, repository,
                InvokeDynamicBootstrapModelRegistry.jdk8Defaults());
    }

    /**
     * Creates an engine with explicit roots, algorithm and ReflectionOptions.
     *
     * @param collector diagnostics
     * @param runtime target JDK runtime
     * @param selection entrypoint class selection
     * @param selectedAlgorithm Call Graph algorithm
     * @param selectedReflectionOptions WALA ReflectionOptions
     * @param repository dependency repository
     */
    public ModuleCallGraphEngine(
            final DiagnosticLog collector,
            final JavaRuntimeDescriptor runtime,
            final EntrypointSelection selection,
            final CallGraphAlgorithm selectedAlgorithm,
            final WalaReflectionOptions selectedReflectionOptions,
            final IJarRepository repository) {
        this(collector, runtime, selection, selectedAlgorithm,
                selectedReflectionOptions, repository,
                InvokeDynamicBootstrapModelRegistry.jdk8Defaults());
    }

    /**
     * Creates an engine with an explicit invokedynamic model registry.
     *
     * @param collector diagnostics
     * @param runtime target JDK runtime
     * @param selection entrypoint class selection
     * @param repository dependency repository
     * @param models exact invokedynamic models
     */
    public ModuleCallGraphEngine(
            final DiagnosticLog collector,
            final JavaRuntimeDescriptor runtime,
            final EntrypointSelection selection,
            final IJarRepository repository,
            final InvokeDynamicBootstrapModelRegistry models) {
        this(collector, runtime, selection,
                CallGraphAlgorithm.defaultAlgorithm(), repository, models);
    }

    /**
     * Creates an engine with an explicit algorithm and dynamic registry.
     *
     * @param collector diagnostics
     * @param runtime target JDK runtime
     * @param selection entrypoint class selection
     * @param selectedAlgorithm Call Graph algorithm
     * @param repository dependency repository
     * @param models exact invokedynamic models
     */
    public ModuleCallGraphEngine(
            final DiagnosticLog collector,
            final JavaRuntimeDescriptor runtime,
            final EntrypointSelection selection,
            final CallGraphAlgorithm selectedAlgorithm,
            final IJarRepository repository,
            final InvokeDynamicBootstrapModelRegistry models) {
        this(collector, runtime, selection, selectedAlgorithm,
                WalaReflectionOptions.defaultOptions(), repository, models);
    }

    /**
     * Creates an engine with all command-wide graph policies.
     *
     * @param collector diagnostics
     * @param runtime target JDK runtime
     * @param selection entrypoint class selection
     * @param selectedAlgorithm Call Graph algorithm
     * @param selectedReflectionOptions WALA ReflectionOptions
     * @param repository dependency repository
     * @param models exact invokedynamic models
     */
    public ModuleCallGraphEngine(
            final DiagnosticLog collector,
            final JavaRuntimeDescriptor runtime,
            final EntrypointSelection selection,
            final CallGraphAlgorithm selectedAlgorithm,
            final WalaReflectionOptions selectedReflectionOptions,
            final IJarRepository repository,
            final InvokeDynamicBootstrapModelRegistry models) {
        diagnostics = Objects.requireNonNull(collector, "collector");
        javaRuntime = Objects.requireNonNull(runtime, "runtime");
        entrypointSelection = Objects.requireNonNull(selection, "selection");
        algorithm = Objects.requireNonNull(
                selectedAlgorithm, "selectedAlgorithm");
        reflectionOptions = Objects.requireNonNull(
                selectedReflectionOptions, "selectedReflectionOptions");
        jarRepository = Objects.requireNonNull(repository, "repository");
        dynamicModels = Objects.requireNonNull(models, "models");
    }

    /**
     * Builds an independent per-module graph.
     *
     * @param unit module analysis input
     * @param timeoutSeconds module build timeout, zero for unlimited
     * @return live graph session
     */
    public ModuleCallGraphSession build(
            final ModuleAnalysisUnit unit,
            final long timeoutSeconds) {
        final EntrypointClassIndex index = new EntrypointClassScanner().scan(
                unit.getProjectClasses(), entrypointSelection);
        return build(unit, index, timeoutSeconds);
    }

    /**
     * Builds an independent per-module graph from a prepared PROJECT index.
     *
     * @param unit module analysis input
     * @param entrypointIndex selected target/classes index
     * @param timeoutSeconds module build timeout, zero for unlimited
     * @return live graph session
     */
    public ModuleCallGraphSession build(
            final ModuleAnalysisUnit unit,
            final EntrypointClassIndex entrypointIndex,
            final long timeoutSeconds) {
        return build(unit, entrypointIndex, timeoutSeconds, false);
    }

    /**
     * Builds a graph and optionally captures read-only benchmark topology.
     *
     * @param unit module analysis input
     * @param entrypointIndex selected target/classes index
     * @param timeoutSeconds module build timeout, zero for unlimited
     * @param captureTopology whether to capture benchmark topology
     * @return live graph session
     */
    public ModuleCallGraphSession build(
            final ModuleAnalysisUnit unit,
            final EntrypointClassIndex entrypointIndex,
            final long timeoutSeconds,
            final boolean captureTopology) {
        final DiagnosticContext context = DiagnosticContext.of(
                "module-analysis", "call-graph").withModule(
                unit.getModuleId().stableKey());
        diagnostics.startStage(context);
        final long start = System.currentTimeMillis();
        final long startedNanos = System.nanoTime();
        final long initialMemory = usedMemory();
        try {
            final ClassOwnershipIndex ownership = ownership(unit);
            final StructuralReferenceIndex structuralReferences =
                    new StructuralImpactScanner(jarRepository).scan(
                            unit, ownership);
            reportDuplicateResolutions(context, ownership);
            final AnalysisScope scope = scope(unit, ownership);
            final IClassHierarchy hierarchy = hierarchy(scope);
            final DependencyBodyBoundary dependencyBoundary =
                    new DependencyBodyBoundary(unit, ownership, hierarchy);
            final List<Entrypoint> entrypoints = entrypoints(
                    hierarchy, entrypointIndex);
            if (entrypoints.isEmpty()) {
                throw new CallGraphException(
                        "Module has zero PROJECT entrypoints: "
                                + unit.getModuleId());
            }
            final IAnalysisCacheView cache = new AnalysisCacheImpl(
                    SSAOptions.defaultOptions());
            final Duration remaining = remainingTimeout(
                    timeoutSeconds, startedNanos);
            if (remaining.isNegative() || remaining.isZero()
                    && timeoutSeconds > 0L) {
                throw CallGraphException.timeout(
                        "Module Call Graph timed out after "
                                + timeoutSeconds + " seconds");
            }
            final CallGraphTimeoutMonitor monitor =
                    new CallGraphTimeoutMonitor(remaining);
            final ServiceLoaderProtocolIndex serviceLoaderIndex =
                    ServiceLoaderProtocolIndex.create(scope, hierarchy);
            final CallGraphStrategyResult strategyResult;
            try {
                strategyResult = new CallGraphStrategyFactory()
                        .create(algorithm)
                        .build(new CallGraphBuildRequest(
                                scope, hierarchy, entrypoints, cache,
                                serviceLoaderIndex, dynamicModels,
                                reflectionOptions, dependencyBoundary,
                                monitor));
            } catch (Exception exception) {
                if (monitor.isTimedOut()) {
                    throw CallGraphException.timeout(
                            "Module Call Graph timed out after "
                                    + timeoutSeconds + " seconds",
                            exception);
                }
                throw exception;
            }
            final com.ibm.wala.ipa.callgraph.CallGraph graph =
                    strategyResult.graph();
            final CallGraphStats stats = new CallGraphStats(
                    graph.getNumberOfNodes(), edgeCount(graph),
                    System.currentTimeMillis() - start,
                    Math.max(0L, usedMemory() - initialMemory));
            final int parameterCandidates = parameterCandidateCount(
                    entrypoints);
            final int selectedClasses =
                    entrypointIndex.selectedClassNames().size();
            final CallGraphTopologySnapshot topology = captureTopology
                    ? new CallGraphTopologyAnalyzer().analyze(
                            graph, type -> originOf(ownership, type)) : null;
            diagnostics.info(context, "algorithm="
                    + algorithm.identifier() + "; reflectionOptions="
                    + reflectionOptions.identifier() + "; nodes="
                    + stats.methodCount() + "; edges="
                    + stats.edgeCount() + "; entrypoints="
                    + entrypoints.size());
            diagnostics.endStage(context);
            return new ModuleCallGraphSession(graph, hierarchy, scope,
                    ownership, cache, new ModuleCallGraphMetadata(
                            stats, new EntrypointSelectionMetrics(
                            selectedClasses, entrypoints.size(),
                            parameterCandidates),
                            strategyResult.metadata(),
                            dependencyBoundary.metadata(graph),
                            structuralReferences, topology));
        } catch (CallGraphException exception) {
            diagnostics.failStage(context, exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            diagnostics.failStage(context, exception.getMessage());
            throw new CallGraphException(
                    "Module Call Graph construction failed", exception);
        }
    }

    private CodeOrigin originOf(
            final ClassOwnershipIndex ownership,
            final IClass type) {
        final ClassOwnership value = ownership.ownershipOf(
                type.getName().toString());
        if (value != null) {
            return value.getOrigin();
        }
        if (type instanceof SyntheticClass) {
            return CodeOrigin.SYNTHETIC;
        }
        final ClassLoaderReference loader = type.getClassLoader()
                .getReference();
        return ClassLoaderReference.Primordial.equals(loader)
                || ClassLoaderReference.Extension.equals(loader)
                ? CodeOrigin.JDK : CodeOrigin.SYNTHETIC;
    }

    private ClassOwnershipIndex ownership(final ModuleAnalysisUnit unit)
            throws IOException {
        final ClassOwnershipIndex result = new ClassOwnershipIndex();
        result.addDirectory(unit.getProjectClasses(), CodeOrigin.PROJECT);
        for (Path path : unit.getReactorDependencyClasses()) {
            result.addDirectory(path, CodeOrigin.REACTOR_DEPENDENCY);
        }
        for (ArtifactCoord artifact : unit.getTargetArtifacts()) {
            try (JarLease lease = jarRepository.open(artifact)) {
                result.addJar(artifact, lease.jarFile(),
                        CodeOrigin.DEPENDENCY);
            }
        }
        final SpringBackendJdkExclusions exclusions =
                new SpringBackendJdkExclusions();
        validateJdkDuplicates(result, javaRuntime.getBootClassPath(),
                exclusions);
        validateJdkDuplicates(result, javaRuntime.getExtensionClassPath(),
                exclusions);
        return result;
    }

    private void validateJdkDuplicates(
            final ClassOwnershipIndex ownership,
            final List<Path> paths,
            final SpringBackendJdkExclusions exclusions) throws IOException {
        for (Path path : paths) {
            if (!exclusions.excludesJar(path)) {
                ownership.validateJarDuplicates(path, CodeOrigin.JDK,
                        name -> !exclusions.test(name));
            }
        }
    }

    private AnalysisScope scope(
            final ModuleAnalysisUnit unit,
            final ClassOwnershipIndex ownership)
            throws IOException {
        final AnalysisScope result = AnalysisScope.createJavaAnalysisScope();
        final SpringBackendJdkExclusions exclusions =
                new SpringBackendJdkExclusions();
        result.setExclusions(exclusions);
        addJdkJars(result, ClassLoaderReference.Primordial,
                javaRuntime.getBootClassPath(), exclusions, ownership);
        addJdkJars(result, ClassLoaderReference.Extension,
                javaRuntime.getExtensionClassPath(), exclusions, ownership);
        result.addToScope(ClassLoaderReference.Application,
                filteredDirectory(unit.getProjectClasses(), ownership));
        addServiceResources(result, unit.getProjectClasses());
        for (Path path : unit.getReactorDependencyClasses()) {
            result.addToScope(ClassLoaderReference.Application,
                    filteredDirectory(path, ownership));
            addServiceResources(result, path);
        }
        for (ArtifactCoord artifact : unit.getTargetArtifacts()) {
            result.addToScope(ClassLoaderReference.Application,
                    filteredJar(artifact, ownership));
        }
        return result;
    }

    private void addServiceResources(
            final AnalysisScope scope,
            final Path classes) throws IOException {
        final ServiceResourceDirectoryModule resources =
                new ServiceResourceDirectoryModule(classes);
        if (resources.getEntries().hasNext()) {
            scope.addToScope(
                    ClassLoaderReference.Application, resources);
        }
    }

    private void addJdkJars(
            final AnalysisScope scope,
            final ClassLoaderReference loader,
            final List<Path> paths,
            final SpringBackendJdkExclusions exclusions,
            final ClassOwnershipIndex ownership)
            throws IOException {
        for (Path path : paths) {
            if (!exclusions.excludesJar(path)) {
                scope.addToScope(loader,
                        filteredJar(path, ownership));
            }
        }
    }

    private OwnershipFilteredModule filteredDirectory(
            final Path path,
            final ClassOwnershipIndex ownership) {
        return new OwnershipFilteredModule(
                new BinaryDirectoryTreeModule(path.toFile()),
                path, ownership);
    }

    private OwnershipFilteredModule filteredJar(
            final Path path,
            final ClassOwnershipIndex ownership) throws IOException {
        return new OwnershipFilteredModule(
                new JarFileModule(new JarFile(path.toFile(), false)),
                path, ownership);
    }

    private OwnershipFilteredModule filteredJar(
            final ArtifactCoord artifact,
            final ClassOwnershipIndex ownership) throws IOException {
        final JarLease lease = jarRepository.open(artifact);
        return new OwnershipFilteredModule(
                new JarFileModule(lease.jarFile()),
                ClassSource.artifact(artifact), ownership);
    }

    private void reportDuplicateResolutions(
            final DiagnosticContext context,
            final ClassOwnershipIndex ownership) {
        final List<DuplicateClassResolution> resolutions =
                ownership.duplicateClassResolutions();
        if (resolutions.isEmpty()) {
            return;
        }
        final String examples = resolutions.stream()
                .limit(MAX_DUPLICATE_EXAMPLES)
                .map(value -> value.getBinaryName() + " -> "
                        + value.getWinner().getSource())
                .reduce((left, right) -> left + "; " + right)
                .orElse("");
        diagnostics.warn(context,
                "Resolved conflicting duplicate classes by classpath "
                        + "precedence; count=" + resolutions.size()
                        + "; winners=" + examples
                        + (resolutions.size() > MAX_DUPLICATE_EXAMPLES
                        ? "; omitted=" + (resolutions.size()
                        - MAX_DUPLICATE_EXAMPLES) : ""));
    }

    private IClassHierarchy hierarchy(final AnalysisScope scope) {
        try {
            return ClassHierarchyFactory.make(scope);
        } catch (ClassHierarchyException exception) {
            throw new CallGraphException(
                    "Unable to construct module class hierarchy",
                    exception);
        }
    }

    // Wiki: wiki/features/call-graph-engine.md - entrypoint roots and types.
    private List<Entrypoint> entrypoints(
            final IClassHierarchy hierarchy,
            final EntrypointClassIndex index) {
        final List<IMethod> methods = new ArrayList<>();
        for (String name : index.selectedClassNames()) {
            final TypeReference reference = TypeReference.findOrCreate(
                    ClassLoaderReference.Application, "L" + name);
            final IClass type = hierarchy.lookupClass(reference);
            if (type == null) {
                throw new CallGraphException(
                        "Unable to resolve indexed PROJECT entrypoint class: "
                                + name);
            }
            final String resolved = type.getName().toString();
            final String normalized = resolved.startsWith("L")
                    ? resolved.substring(1) : resolved;
            if (!name.equals(normalized) || type.isInterface()) {
                throw new CallGraphException(
                        "Indexed PROJECT entrypoint class resolved "
                                + "inconsistently: expected=" + name
                                + "; actual=" + resolved);
            }
            if (isPrivateEntrypointType(type)) {
                continue;
            }
            for (IMethod method : type.getDeclaredMethods()) {
                if (!method.isAbstract() && !method.isPrivate()) {
                    methods.add(method);
                }
            }
        }
        methods.sort(Comparator.comparing(
                method -> method.getReference().toString()));
        final EntrypointSyntheticTypeRegistry syntheticTypes =
                new EntrypointSyntheticTypeRegistry(hierarchy);
        return methods.stream()
                .map(method -> (Entrypoint)
                        new DeclaredTypesEntrypoint(
                                method, hierarchy, syntheticTypes))
                .toList();
    }

    private boolean isPrivateEntrypointType(final IClass type) {
        if (type.isPrivate()) {
            return true;
        }
        if (!(type instanceof ShrikeClass shrikeClass)) {
            return false;
        }
        final ClassReader reader = new ClassReader(
                shrikeClass.getReader().getBytes());
        final boolean[] privateNested = new boolean[1];
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visitInnerClass(
                    final String name,
                    final String outerName,
                    final String innerName,
                    final int access) {
                if (reader.getClassName().equals(name)
                        && (access & Opcodes.ACC_PRIVATE) != 0) {
                    privateNested[0] = true;
                }
            }
        }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG
                | ClassReader.SKIP_FRAMES);
        return privateNested[0];
    }

    private int parameterCandidateCount(
            final List<Entrypoint> entrypoints) {
        int result = 0;
        for (Entrypoint entrypoint : entrypoints) {
            for (int index = 0;
                    index < entrypoint.getNumberOfParameters(); index++) {
                result += entrypoint.getParameterTypes(index).length;
            }
        }
        return result;
    }

    private int edgeCount(
            final com.ibm.wala.ipa.callgraph.CallGraph graph) {
        int result = 0;
        for (com.ibm.wala.ipa.callgraph.CGNode node : graph) {
            result += graph.getSuccNodeCount(node);
        }
        return result;
    }

    private long usedMemory() {
        final Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private Duration remainingTimeout(
            final long timeoutSeconds,
            final long startedNanos) {
        if (timeoutSeconds == 0L) {
            return Duration.ZERO;
        }
        return Duration.ofSeconds(timeoutSeconds).minusNanos(
                System.nanoTime() - startedNanos);
    }
}
