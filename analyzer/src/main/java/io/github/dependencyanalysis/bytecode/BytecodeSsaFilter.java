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
import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLevel;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Pair-local normalized SSA filter used while collecting ChangePoints. */
final class BytecodeSsaFilter {

    /** Target JDK used to supply the WALA hierarchy root. */
    private final JavaRuntimeDescriptor javaRuntime;

    /** Canonical normalized comparator. */
    private final NormalizedSsaComparator comparator =
            new NormalizedSsaComparator();

    /** Command diagnostic destination. */
    private final DiagnosticLog diagnostics;

    /**
     * Creates a pair-local filter.
     *
     * @param runtime target JDK 8 runtime
     * @param log command diagnostic destination
     */
    BytecodeSsaFilter(
            final JavaRuntimeDescriptor runtime,
            final DiagnosticLog log) {
        javaRuntime = Objects.requireNonNull(runtime, "runtime");
        diagnostics = Objects.requireNonNull(log, "log");
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
                result.add(compare(change, oldJar, newJar,
                        oldSession, newSession, candidate));
            }
            return List.copyOf(result);
        } catch (IOException | ClassHierarchyException
                 | RuntimeException exception) {
            final String reason = "SSA_SESSION_FAILED:"
                    + exception.getClass().getSimpleName();
            final List<SsaComparisonEvidence> result = new ArrayList<>();
            for (SsaMethodCandidate candidate : ordered) {
                emitAudit(new AuditInput(change, oldJar, newJar, candidate),
                        new AuditResult(SsaComparisonStatus.UNKNOWN,
                                reason, 0L, null, null));
                result.add(evidence(change, candidate,
                        SsaComparisonStatus.UNKNOWN, reason, 0L));
            }
            return List.copyOf(result);
        }
    }

    private SsaComparisonEvidence compare(
            final DependencyChange change,
            final JarFile oldJar,
            final JarFile newJar,
            final SsaSession oldSession,
            final SsaSession newSession,
            final SsaMethodCandidate candidate) {
        final long start = System.nanoTime();
        final ChangePoint point = candidate.changePoint();
        SsaComparisonOutcome outcome;
        IR oldIr = null;
        IR newIr = null;
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
                oldIr = oldSession.cache().getIR(oldMethod);
                newIr = newSession.cache().getIR(newMethod);
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
        emitAudit(new AuditInput(change, oldJar, newJar, candidate),
                new AuditResult(outcome.status(), outcome.reason(),
                        elapsed, oldIr, newIr));
        return evidence(change, candidate, outcome.status(),
                outcome.reason(), elapsed);
    }

    private void emitAudit(
            final AuditInput input,
            final AuditResult result) {
        if (result.status() == SsaComparisonStatus.MATCHED
                || !diagnostics.getVerbosity().includes(
                LogVerbosity.TRACE)) {
            return;
        }
        try {
            final ChangePoint point = input.candidate().changePoint();
            final String method = point.getOwner().replace('/', '.')
                    + "#" + point.getName() + point.getOldDescriptor();
            final DiagnosticContext context = DiagnosticContext.of(
                    "jar-diff", "ssa-equivalence-audit")
                    .withArtifact(input.change().getOldArtifact() + "->"
                            + input.change().getNewArtifact())
                    .with("method", method);
            final String retention = result.status()
                    == SsaComparisonStatus.DIFFERENT
                    ? "ssaDifferentCandidate" : "ssaUnknownCandidate";
            final StringBuilder message = new StringBuilder()
                    .append("audit=started; retention=").append(retention)
                    .append("; status=").append(result.status())
                    .append("; reason=").append(result.reason())
                    .append("; oldHash=").append(point.getOldHash())
                    .append("; newHash=").append(point.getNewHash())
                    .append("; oldMajorVersion=")
                    .append(input.candidate().oldMajorVersion())
                    .append("; newMajorVersion=")
                    .append(input.candidate().newMajorVersion())
                    .append("; elapsedMs=").append(result.elapsedMillis())
                    .append('\n');
            section(message, "old-bytecode", auditValue(() ->
                    bytecode(input.oldJar(), point, true)));
            section(message, "new-bytecode", auditValue(() ->
                    bytecode(input.newJar(), point, false)));
            section(message, "old-ir", auditValue(() -> rawIr(
                    result.oldIr(), result.reason())));
            section(message, "new-ir", auditValue(() -> rawIr(
                    result.newIr(), result.reason())));
            section(message, "old-normalized-ir", auditValue(() ->
                    normalizedIr(result.oldIr(), result.reason())));
            section(message, "new-normalized-ir", auditValue(() ->
                    normalizedIr(result.newIr(), result.reason())));
            message.append("audit=completed; retention=")
                    .append(retention).append("; status=")
                    .append(result.status());
            diagnostics.transientLog(context, DiagnosticLevel.TRACE,
                    LogVerbosity.TRACE, message.toString());
        } catch (RuntimeException ignored) {
            // Audit evidence must never change fail-open comparison behavior.
        }
    }

    private String bytecode(
            final JarFile jar,
            final ChangePoint point,
            final boolean oldSide) throws IOException {
        final JarEntry entry = jar.getJarEntry(point.getOwner() + ".class");
        if (entry == null) {
            throw new IOException("Class entry not found");
        }
        try (InputStream input = jar.getInputStream(entry)) {
            return MethodBytecodeTextRenderer.render(
                    input.readAllBytes(), point.getName(), oldSide
                            ? point.getOldDescriptor()
                            : point.getNewDescriptor());
        }
    }

    private String rawIr(final IR ir, final String reason) {
        if (ir == null) {
            return unavailable(reason);
        }
        return ir.toString();
    }

    private String normalizedIr(final IR ir, final String reason) {
        if (ir == null) {
            return unavailable(reason);
        }
        return comparator.renderNormalized(ir);
    }

    private String auditValue(final AuditValue supplier) {
        try {
            return normalizeNewlines(supplier.get());
        } catch (Exception exception) {
            return unavailable(exception.getClass().getSimpleName()
                    + (exception.getMessage() == null
                    || exception.getMessage().isBlank() ? ""
                    : ":" + exception.getMessage()));
        }
    }

    private String unavailable(final String reason) {
        return "<unavailable reason=" + normalizeNewlines(reason)
                .replace('\n', ' ') + ">";
    }

    private void section(
            final StringBuilder target,
            final String name,
            final String value) {
        target.append("section=").append(name).append("; begin\n")
                .append(value);
        if (!value.endsWith("\n")) {
            target.append('\n');
        }
        target.append("section=").append(name).append("; end\n");
    }

    private String normalizeNewlines(final String value) {
        return Objects.requireNonNullElse(value, "")
                .replace("\r\n", "\n").replace('\r', '\n');
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

    /** Checked audit value supplier. */
    @FunctionalInterface
    private interface AuditValue {

        /** @return audit text */
        String get() throws Exception;
    }

    /**
     * Stable inputs required to render one method audit.
     *
     * @param change dependency upgrade
     * @param oldJar baseline JAR
     * @param newJar target JAR
     * @param candidate compared method
     */
    private record AuditInput(
            DependencyChange change,
            JarFile oldJar,
            JarFile newJar,
            SsaMethodCandidate candidate) {
    }

    /**
     * Comparison outputs required to render one method audit.
     *
     * @param status comparison status
     * @param reason stable comparison reason
     * @param elapsedMillis comparison elapsed time
     * @param oldIr baseline Intermediate Representation
     * @param newIr target Intermediate Representation
     */
    private record AuditResult(
            SsaComparisonStatus status,
            String reason,
            long elapsedMillis,
            IR oldIr,
            IR newIr) {
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
