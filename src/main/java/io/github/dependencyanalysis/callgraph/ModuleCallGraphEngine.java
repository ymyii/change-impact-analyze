package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.analysis.reflection.java7.MethodHandles;
import com.ibm.wala.classLoader.BinaryDirectoryTreeModule;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.ClassLoaderReference;

import io.github.dependencyanalysis.dependency.ResolvedArtifact;
import io.github.dependencyanalysis.diagnostic.DiagnosticCollector;
import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.impact.ModuleAnalysisUnit;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarFile;

/** Builds one independent Vanilla 0-1-CFA graph per analysis module. */
public final class ModuleCallGraphEngine {

    /** Diagnostics. */
    private final DiagnosticCollector diagnostics;

    /** Target JDK 8. */
    private final JavaRuntimeDescriptor javaRuntime;

    /** User-selected PROJECT root boundary. */
    private final EntrypointSelection entrypointSelection;

    /**
     * Creates a module Call Graph engine.
     *
     * @param collector diagnostics
     * @param runtime target JDK runtime
     */
    public ModuleCallGraphEngine(
            final DiagnosticCollector collector,
            final JavaRuntimeDescriptor runtime) {
        this(collector, runtime, EntrypointSelection.allProjectClasses());
    }

    /**
     * Creates an engine with a user-selected PROJECT root boundary.
     *
     * @param collector diagnostics
     * @param runtime target JDK runtime
     * @param selection entrypoint class selection
     */
    public ModuleCallGraphEngine(
            final DiagnosticCollector collector,
            final JavaRuntimeDescriptor runtime,
            final EntrypointSelection selection) {
        diagnostics = Objects.requireNonNull(collector, "collector");
        javaRuntime = Objects.requireNonNull(runtime, "runtime");
        entrypointSelection = Objects.requireNonNull(selection, "selection");
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
        final DiagnosticContext context = DiagnosticContext.task(
                "module-analysis", "call-graph").withModule(
                unit.getModuleId().stableKey());
        diagnostics.startStage(context);
        final long start = System.currentTimeMillis();
        final long startedNanos = System.nanoTime();
        final long initialMemory = usedMemory();
        try {
            final ClassOwnershipIndex ownership = ownership(unit);
            final AnalysisScope scope = scope(unit);
            final IClassHierarchy hierarchy = hierarchy(scope);
            final List<Entrypoint> entrypoints = entrypoints(
                    hierarchy, ownership);
            if (entrypoints.isEmpty()) {
                throw new CallGraphException(
                        "Module has zero PROJECT entrypoints: "
                                + unit.getModuleId());
            }
            final AnalysisOptions options = new AnalysisOptions(
                    scope, entrypoints);
            options.setReflectionOptions(
                    AnalysisOptions.ReflectionOptions.FULL);
            final IAnalysisCacheView cache = new AnalysisCacheImpl(
                    SSAOptions.defaultOptions());
            final SSAPropagationCallGraphBuilder builder =
                    Util.makeVanillaZeroOneCFABuilder(
                            Language.JAVA, options,
                            cache, hierarchy);
            MethodHandles.analyzeMethodHandles(options, builder);
            final com.ibm.wala.ipa.callgraph.CallGraph graph;
            final Duration remaining = remainingTimeout(
                    timeoutSeconds, startedNanos);
            if (remaining.isNegative() || remaining.isZero()
                    && timeoutSeconds > 0L) {
                throw new CallGraphException(
                        "Module Call Graph timed out after "
                                + timeoutSeconds + " seconds");
            }
            try (CallGraphProgressMonitor monitor =
                         new CallGraphProgressMonitor(
                                 diagnostics, context, remaining,
                                 Duration.ofSeconds(
                                         CallGraphProgressMonitor
                                                 .HEARTBEAT_SECONDS))) {
                try {
                    graph = builder.makeCallGraph(options, monitor);
                } catch (Exception exception) {
                    if (monitor.isTimedOut()) {
                        throw new CallGraphException(
                                "Module Call Graph timed out after "
                                        + timeoutSeconds + " seconds",
                                exception);
                    }
                    throw exception;
                }
            }
            final CallGraphStats stats = new CallGraphStats(
                    graph.getNumberOfNodes(), edgeCount(graph),
                    System.currentTimeMillis() - start,
                    Math.max(0L, usedMemory() - initialMemory));
            final int parameterCandidates = parameterCandidateCount(
                    entrypoints);
            final int selectedClasses = selectedProjectClassCount(
                    hierarchy, ownership);
            diagnostics.info(context, "algorithm=vanilla-0-1-cfa; nodes="
                    + stats.methodCount() + "; edges="
                    + stats.edgeCount() + "; entrypoints="
                    + entrypoints.size());
            diagnostics.endStage(context);
            return new ModuleCallGraphSession(graph, hierarchy, scope,
                    ownership, cache, stats,
                    new EntrypointSelectionMetrics(selectedClasses,
                            entrypoints.size(), parameterCandidates));
        } catch (CallGraphException exception) {
            diagnostics.failStage(context, exception.getMessage());
            throw exception;
        } catch (Exception exception) {
            diagnostics.failStage(context, exception.getMessage());
            throw new CallGraphException(
                    "Module Call Graph construction failed", exception);
        }
    }

    private ClassOwnershipIndex ownership(final ModuleAnalysisUnit unit)
            throws IOException {
        final ClassOwnershipIndex result = new ClassOwnershipIndex();
        result.addDirectory(unit.getProjectClasses(), CodeOrigin.PROJECT);
        for (Path path : unit.getReactorDependencyClasses()) {
            result.addDirectory(path, CodeOrigin.REACTOR_DEPENDENCY);
        }
        for (ResolvedArtifact artifact : unit.getTargetArtifacts()) {
            if (isJar(artifact)) {
                result.addJar(artifact.getPath(), CodeOrigin.DEPENDENCY);
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

    private AnalysisScope scope(final ModuleAnalysisUnit unit)
            throws IOException {
        final AnalysisScope result = AnalysisScope.createJavaAnalysisScope();
        final SpringBackendJdkExclusions exclusions =
                new SpringBackendJdkExclusions();
        result.setExclusions(exclusions);
        addJdkJars(result, ClassLoaderReference.Primordial,
                javaRuntime.getBootClassPath(), exclusions);
        addJdkJars(result, ClassLoaderReference.Extension,
                javaRuntime.getExtensionClassPath(), exclusions);
        result.addToScope(ClassLoaderReference.Application,
                new BinaryDirectoryTreeModule(
                        unit.getProjectClasses().toFile()));
        for (Path path : unit.getReactorDependencyClasses()) {
            result.addToScope(ClassLoaderReference.Application,
                    new BinaryDirectoryTreeModule(path.toFile()));
        }
        for (ResolvedArtifact artifact : unit.getTargetArtifacts()) {
            if (isJar(artifact)) {
                result.addToScope(ClassLoaderReference.Application,
                        new JarFile(artifact.getPath().toFile(), false));
            }
        }
        return result;
    }

    private void addJdkJars(
            final AnalysisScope scope,
            final ClassLoaderReference loader,
            final List<Path> paths,
            final SpringBackendJdkExclusions exclusions)
            throws IOException {
        for (Path path : paths) {
            if (!exclusions.excludesJar(path)) {
                scope.addToScope(loader,
                        new JarFile(path.toFile(), false));
            }
        }
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

    private List<Entrypoint> entrypoints(
            final IClassHierarchy hierarchy,
            final ClassOwnershipIndex ownership) {
        final List<IMethod> methods = new ArrayList<>();
        for (IClass type : hierarchy) {
            final ClassOwnership value = ownership.ownershipOf(
                    type.getName().toString());
            if (value == null || value.getOrigin() != CodeOrigin.PROJECT) {
                continue;
            }
            if (!entrypointSelection.matchesInternalName(
                    type.getName().toString())) {
                continue;
            }
            for (IMethod method : type.getDeclaredMethods()) {
                if (!method.isAbstract()) {
                    methods.add(method);
                }
            }
        }
        methods.sort(Comparator.comparing(
                method -> method.getReference().toString()));
        return methods.stream()
                .map(method -> (Entrypoint)
                        new DeterministicSubtypesEntrypoint(
                                method, hierarchy))
                .toList();
    }

    private int selectedProjectClassCount(
            final IClassHierarchy hierarchy,
            final ClassOwnershipIndex ownership) {
        int result = 0;
        for (IClass type : hierarchy) {
            final ClassOwnership value = ownership.ownershipOf(
                    type.getName().toString());
            if (value != null && value.getOrigin() == CodeOrigin.PROJECT
                    && entrypointSelection.matchesInternalName(
                    type.getName().toString())) {
                result++;
            }
        }
        return result;
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

    private boolean isJar(final ResolvedArtifact artifact) {
        return "jar".equals(artifact.getArtifact().getType())
                && Files.isRegularFile(artifact.getPath());
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
