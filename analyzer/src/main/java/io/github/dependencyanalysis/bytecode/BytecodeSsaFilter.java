package io.github.dependencyanalysis.bytecode;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.ClassLoaderReference;

import io.github.dependencyanalysis.dependency.DependencyChange;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarFile;

// Wiki: wiki/features/bytecode-diff-engine.md - ChangePoint 收集期 SSA
/** Pair-local normalized SSA filter used while collecting ChangePoints. */
final class BytecodeSsaFilter {

    /** Target JDK used to supply the WALA hierarchy root. */
    private final JavaRuntimeDescriptor javaRuntime;

    /** Canonical normalized comparator. */
    private final NormalizedSsaComparator comparator =
            new NormalizedSsaComparator();

    /** @param runtime target JDK 8 runtime */
    BytecodeSsaFilter(final JavaRuntimeDescriptor runtime) {
        javaRuntime = Objects.requireNonNull(runtime, "runtime");
    }

    /**
     * Compares all eligible methods while the repository JARs remain open.
     *
     * @param change logical artifact upgrade
     * @param oldJar baseline JAR
     * @param newJar target JAR
     * @param candidates stable eligible methods
     * @return stable comparison evidence
     */
    List<SsaComparisonEvidence> compare(
            final DependencyChange change,
            final JarFile oldJar,
            final JarFile newJar,
            final List<SsaMethodCandidate> candidates) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        final List<SsaMethodCandidate> ordered = candidates.stream()
                .sorted(Comparator.comparing(value -> value.changePoint()
                        .getOwner() + "|" + value.changePoint().getName()
                        + value.changePoint().getOldDescriptor()))
                .toList();
        try (SsaSession oldSession = session(oldJar);
             SsaSession newSession = session(newJar)) {
            final List<SsaComparisonEvidence> result = new ArrayList<>();
            for (SsaMethodCandidate candidate : ordered) {
                result.add(compare(change, oldSession, newSession,
                        candidate));
            }
            return List.copyOf(result);
        } catch (IOException | ClassHierarchyException
                 | RuntimeException exception) {
            final String reason = "SSA_SESSION_FAILED:"
                    + exception.getClass().getSimpleName();
            return ordered.stream().map(candidate -> evidence(
                    change, candidate, SsaComparisonStatus.UNKNOWN,
                    reason, 0L)).toList();
        }
    }

    private SsaComparisonEvidence compare(
            final DependencyChange change,
            final SsaSession oldSession,
            final SsaSession newSession,
            final SsaMethodCandidate candidate) {
        final long start = System.nanoTime();
        final ChangePoint point = candidate.changePoint();
        SsaComparisonOutcome outcome;
        try {
            final IMethod oldMethod = findMethod(
                    oldSession.hierarchy(), point, true);
            final IMethod newMethod = findMethod(
                    newSession.hierarchy(), point, false);
            if (oldMethod == null || newMethod == null) {
                outcome = new SsaComparisonOutcome(
                        SsaComparisonStatus.UNKNOWN,
                        oldMethod == null ? "BASELINE_METHOD_NOT_FOUND"
                                : "TARGET_METHOD_NOT_FOUND");
            } else {
                final IR oldIr = oldSession.cache().getIR(oldMethod);
                final IR newIr = newSession.cache().getIR(newMethod);
                outcome = comparator.compare(oldIr, newIr);
            }
        } catch (RuntimeException exception) {
            outcome = new SsaComparisonOutcome(
                    SsaComparisonStatus.UNKNOWN,
                    "SSA_COMPARISON_FAILED:"
                            + exception.getClass().getSimpleName());
        }
        final long elapsed = TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - start);
        return evidence(change, candidate, outcome.status(),
                outcome.reason(), elapsed);
    }

    private SsaComparisonEvidence evidence(
            final DependencyChange change,
            final SsaMethodCandidate candidate,
            final SsaComparisonStatus status,
            final String reason,
            final long elapsedMillis) {
        final ChangePoint point = candidate.changePoint();
        return new SsaComparisonEvidence(
                change, point, candidate.oldMajorVersion(),
                candidate.newMajorVersion(), status, reason,
                elapsedMillis);
    }

    private IMethod findMethod(
            final IClassHierarchy hierarchy,
            final ChangePoint point,
            final boolean oldSide) {
        final String descriptor = oldSide
                ? point.getOldDescriptor() : point.getNewDescriptor();
        for (IClass type : hierarchy) {
            if (!point.getOwner().equals(owner(type.getName().toString()))) {
                continue;
            }
            for (IMethod method : type.getDeclaredMethods()) {
                if (point.getName().equals(method.getName().toString())
                        && descriptor.equals(
                        method.getDescriptor().toString())) {
                    return method;
                }
            }
        }
        return null;
    }

    private String owner(final String walaName) {
        return walaName.startsWith("L") ? walaName.substring(1) : walaName;
    }

    private SsaSession session(final JarFile artifact)
            throws IOException, ClassHierarchyException {
        final AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();
        final List<JarFile> ownedJars = new ArrayList<>();
        try {
            addRuntime(scope, ClassLoaderReference.Primordial,
                    javaRuntime.getBootClassPath(), ownedJars);
            addRuntime(scope, ClassLoaderReference.Extension,
                    javaRuntime.getExtensionClassPath(), ownedJars);
            scope.addToScope(ClassLoaderReference.Application,
                    new JarFileModule(artifact));
            final IClassHierarchy hierarchy =
                    ClassHierarchyFactory.make(scope);
            return new SsaSession(hierarchy,
                    new AnalysisCacheImpl(SSAOptions.defaultOptions()),
                    ownedJars);
        } catch (IOException | ClassHierarchyException
                 | RuntimeException exception) {
            close(ownedJars);
            throw exception;
        }
    }

    private void addRuntime(
            final AnalysisScope scope,
            final ClassLoaderReference loader,
            final List<Path> paths,
            final List<JarFile> ownedJars) throws IOException {
        for (Path path : paths) {
            final JarFile jar = new JarFile(path.toFile(), false);
            ownedJars.add(jar);
            scope.addToScope(loader, new JarFileModule(jar));
        }
    }

    private static void close(final List<JarFile> jars) {
        for (JarFile jar : jars) {
            try {
                jar.close();
            } catch (IOException ignored) {
                // Best-effort cleanup after the primary comparison result.
            }
        }
    }

    /**
     * Pair-side WALA state and owned JDK handles.
     *
     * @param hierarchy side-local class hierarchy
     * @param cache side-local SSA cache
     * @param ownedJars owned JDK JAR handles
     */
    private record SsaSession(
            IClassHierarchy hierarchy,
            AnalysisCacheImpl cache,
            List<JarFile> ownedJars) implements AutoCloseable {

        @Override
        public void close() {
            BytecodeSsaFilter.close(ownedJars);
        }
    }
}
