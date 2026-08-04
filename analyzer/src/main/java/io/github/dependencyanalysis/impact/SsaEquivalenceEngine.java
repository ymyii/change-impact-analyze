package io.github.dependencyanalysis.impact;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.ClassLoaderReference;

import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.callgraph.ClassOwnershipIndex;
import io.github.dependencyanalysis.callgraph.CodeOrigin;
import io.github.dependencyanalysis.callgraph.OwnershipFilteredModule;
import io.github.dependencyanalysis.callgraph.SpringBackendJdkExclusions;
import io.github.dependencyanalysis.dependency.ResolvedArtifact;
import io.github.dependencyanalysis.diagnostic.DiagnosticCollector;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarFile;

/** Global serial, candidate-only normalized SSA equivalence stage. */
public final class SsaEquivalenceEngine {

    /** Diagnostics. */
    private final DiagnosticCollector diagnostics;

    /** Target JDK. */
    private final JavaRuntimeDescriptor javaRuntime;

    /** Comparator. */
    private final NormalizedSsaComparator comparator =
            new NormalizedSsaComparator();

    /**
     * Creates the global serial equivalence stage.
     *
     * @param collector diagnostics
     * @param runtime target JDK 8
     */
    public SsaEquivalenceEngine(
            final DiagnosticCollector collector,
            final JavaRuntimeDescriptor runtime) {
        diagnostics = Objects.requireNonNull(collector, "collector");
        javaRuntime = Objects.requireNonNull(runtime, "runtime");
    }

    /**
     * Compares each unique impacted body change once and filters only
     * {@link MethodEquivalenceStatus#PROVEN_EQUIVALENT} paths.
     *
     * @param moduleResults module query results
     * @return deterministically ordered filtered results
     */
    public List<ModuleAnalysisResult> filter(
            final List<ModuleAnalysisResult> moduleResults) {
        diagnostics.startStage("ssa-equivalence");
        final List<ModuleAnalysisResult> result = new ArrayList<>();
        for (ModuleAnalysisResult module : moduleResults.stream()
                .sorted(Comparator.comparing(value ->
                        value.getModuleId().stableKey())).toList()) {
            result.add(filterModule(module));
        }
        diagnostics.info("ssa-equivalence",
                "Compared impacted METHOD_BODY_CHANGED serially");
        diagnostics.endStage("ssa-equivalence");
        return List.copyOf(result);
    }

    private ModuleAnalysisResult filterModule(
            final ModuleAnalysisResult module) {
        if (module.getSession() == null
                || module.getCandidatePaths().isEmpty()) {
            return module;
        }
        final Set<BoundChangePoint> requested = new LinkedHashSet<>();
        for (ImpactPath path : module.getCandidatePaths()) {
            final BoundChangePoint point = path.getTerminal()
                    .getChangePoint();
            if (point.getChangePoint().getKind()
                    == ChangePointKind.METHOD_BODY_CHANGED) {
                requested.add(point);
            }
        }
        if (requested.isEmpty()) {
            return module;
        }
        final Map<BoundChangePoint, MethodEquivalenceResult> comparisons =
                new LinkedHashMap<>();
        OldSsaSession oldSession = null;
        try {
            oldSession = oldSession(module.getUnit());
            for (BoundChangePoint point : requested.stream()
                    .sorted(Comparator.comparing(BoundChangePoint::stableKey))
                    .toList()) {
                comparisons.put(point, compare(
                        module, oldSession, point));
            }
        } catch (RuntimeException exception) {
            for (BoundChangePoint point : requested) {
                comparisons.putIfAbsent(point,
                        new MethodEquivalenceResult(
                                MethodEquivalenceStatus.UNKNOWN,
                                "OLD_SSA_SESSION_FAILED:"
                                        + exception.getClass()
                                        .getSimpleName()));
            }
        }
        final List<ImpactPath> finalPaths = module.getCandidatePaths()
                .stream()
                .filter(path -> !isEquivalent(
                        comparisons, path))
                .toList();
        final Map<BoundChangePoint, ChangePointDisposition> dispositions =
                new LinkedHashMap<>(module.getDispositions());
        for (Map.Entry<BoundChangePoint,
                MethodEquivalenceResult> entry : comparisons.entrySet()) {
            if (entry.getValue().getStatus()
                    == MethodEquivalenceStatus.PROVEN_EQUIVALENT) {
                dispositions.put(entry.getKey(),
                        ChangePointDisposition.FILTERED_EQUIVALENT);
            }
        }
        final List<String> limitations =
                new ArrayList<>(module.getLimitations());
        comparisons.forEach((point, comparison) -> {
            if (comparison.getStatus() == MethodEquivalenceStatus.UNKNOWN) {
                limitations.add("SSA UNKNOWN " + point.stableKey()
                        + ": " + comparison.getReason());
            }
        });
        final boolean unknown = comparisons.values().stream()
                .anyMatch(value -> value.getStatus()
                        == MethodEquivalenceStatus.UNKNOWN);
        final ModuleAnalysisStatus status = unknown
                && module.getStatus() == ModuleAnalysisStatus.SUCCESS
                ? ModuleAnalysisStatus.INCONCLUSIVE : module.getStatus();
        final ModuleAnalysisReason reason = unknown
                && module.getReason() == ModuleAnalysisReason.NONE
                ? ModuleAnalysisReason.INCONCLUSIVE_SSA_UNKNOWN
                : module.getReason();
        return module.toBuilder()
                .status(status, reason, module.getDetail())
                .finalPaths(finalPaths)
                .dispositions(dispositions)
                .equivalenceResults(comparisons)
                .limitations(limitations)
                .build();
    }

    private MethodEquivalenceResult compare(
            final ModuleAnalysisResult module,
            final OldSsaSession oldSession,
            final BoundChangePoint point) {
        final IMethod target = findMethod(
                module.getSession().getHierarchy(), point, false);
        final IMethod baseline = findMethod(
                oldSession.hierarchy(), point, true);
        if (target == null || baseline == null) {
            return new MethodEquivalenceResult(
                    MethodEquivalenceStatus.UNKNOWN,
                    target == null ? "TARGET_METHOD_NOT_FOUND"
                            : "BASELINE_METHOD_NOT_FOUND");
        }
        final IR targetIr = module.getSession().getAnalysisCache()
                .getIR(target);
        final IR oldIr = oldSession.cache().getIR(baseline);
        return comparator.compare(oldIr, targetIr);
    }

    private IMethod findMethod(
            final IClassHierarchy hierarchy,
            final BoundChangePoint point,
            final boolean oldSide) {
        final String descriptor = oldSide
                ? point.getChangePoint().getOldDescriptor()
                : point.getChangePoint().getNewDescriptor();
        for (IClass type : hierarchy) {
            if (!point.getChangePoint().getOwner().equals(
                    owner(type.getName().toString()))) {
                continue;
            }
            for (IMethod method : type.getDeclaredMethods()) {
                if (Objects.equals(point.getChangePoint().getName(),
                        method.getName().toString())
                        && Objects.equals(descriptor,
                        method.getDescriptor().toString())) {
                    return method;
                }
            }
        }
        return null;
    }

    private OldSsaSession oldSession(final ModuleAnalysisUnit unit) {
        final AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();
        final SpringBackendJdkExclusions exclusions =
                new SpringBackendJdkExclusions();
        scope.setExclusions(exclusions);
        try {
            addJdk(scope, ClassLoaderReference.Primordial,
                    javaRuntime.getBootClassPath(), exclusions);
            addJdk(scope, ClassLoaderReference.Extension,
                    javaRuntime.getExtensionClassPath(), exclusions);
            final Set<Path> paths = new LinkedHashSet<>();
            for (ResolvedArtifact artifact : unit.getBaselineArtifacts()) {
                if ("jar".equals(artifact.getArtifact().getType())) {
                    paths.add(artifact.getPath());
                }
            }
            for (BoundChangePoint point : unit.getChangePoints()) {
                paths.add(point.getDependencyUpgradeKey().getOldPath());
            }
            final ClassOwnershipIndex ownership = new ClassOwnershipIndex();
            for (Path path : paths) {
                if (Files.isRegularFile(path)) {
                    ownership.addJar(path, CodeOrigin.DEPENDENCY);
                    scope.addToScope(ClassLoaderReference.Application,
                            new OwnershipFilteredModule(
                                    new JarFileModule(new JarFile(
                                            path.toFile(), false)),
                                    path, ownership));
                }
            }
            final IClassHierarchy hierarchy =
                    ClassHierarchyFactory.make(scope);
            return new OldSsaSession(hierarchy,
                    new AnalysisCacheImpl(SSAOptions.defaultOptions()));
        } catch (IOException | ClassHierarchyException exception) {
            throw new IllegalStateException(
                    "Unable to create old-side SSA hierarchy", exception);
        }
    }

    private void addJdk(
            final AnalysisScope scope,
            final ClassLoaderReference loader,
            final List<Path> paths,
            final SpringBackendJdkExclusions exclusions) throws IOException {
        for (Path path : paths) {
            if (!exclusions.excludesJar(path)) {
                scope.addToScope(loader,
                        new JarFile(path.toFile(), false));
            }
        }
    }

    private boolean isEquivalent(
            final Map<BoundChangePoint,
                    MethodEquivalenceResult> comparisons,
            final ImpactPath path) {
        final MethodEquivalenceResult result = comparisons.get(
                path.getTerminal().getChangePoint());
        return result != null && result.getStatus()
                == MethodEquivalenceStatus.PROVEN_EQUIVALENT;
    }

    private String owner(final String value) {
        return value.startsWith("L") ? value.substring(1) : value;
    }

    /**
     * Old hierarchy and independent cache.
     *
     * @param hierarchy old class hierarchy
     * @param cache old independent SSA cache
     */
    private record OldSsaSession(
            IClassHierarchy hierarchy,
            IAnalysisCacheView cache) {
    }
}
