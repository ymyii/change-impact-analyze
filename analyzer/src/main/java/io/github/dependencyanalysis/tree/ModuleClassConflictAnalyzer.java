package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.bytecode.DecompiledMethod;
import io.github.dependencyanalysis.bytecode.MethodBodyDecompiler;
import io.github.dependencyanalysis.classpath.ClassConflictResolution;
import io.github.dependencyanalysis.classpath.ClassConflictRisk;
import io.github.dependencyanalysis.classpath.ClassOwnership;
import io.github.dependencyanalysis.classpath.ClassOwnershipIndex;
import io.github.dependencyanalysis.classpath.ClassSource;
import io.github.dependencyanalysis.classpath.CodeOrigin;
import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// Wiki: wiki/features/repository-dependency-tree-report.md - 冲突类扫描
/** Scans and decompiles class conflicts for one effective Module classpath. */
final class ModuleClassConflictAnalyzer {

    /** Maximum individual decompilation warnings per Module. */
    private static final int MAX_DECOMPILE_WARNINGS = 20;

    /** Suppresses the decompiler's unbounded per-definition warnings. */
    private static final PrintStream DISCARD = new PrintStream(
            OutputStream.nullOutputStream());

    /** Diagnostics. */
    private final DiagnosticLog diagnostics;

    ModuleClassConflictAnalyzer(final DiagnosticLog diagnosticLog) {
        diagnostics = diagnosticLog;
    }

    Analysis analyze(final ModuleClasspathEvidence evidence) {
        final DiagnosticContext context = DiagnosticContext.of(
                "analysis", "class-scan")
                .with("module", evidence.module().toString());
        if (diagnostics != null) {
            diagnostics.startStage(context, "entries="
                    + evidence.entries().size());
        }
        final ClassOwnershipIndex index = new ClassOwnershipIndex(
                evidence.javaMajor());
        final Map<String, ClasspathEvidenceEntry> bySource =
                new LinkedHashMap<>();
        final List<String> issues = new ArrayList<>(evidence.issues());
        scanEntries(evidence, context, index, bySource, issues);
        if (traceEnabled()) {
            for (String binaryName : index.binaryNames()) {
                diagnostics.trace(context, "class; name=" + binaryName);
            }
        }
        final MethodBodyDecompiler decompiler = new MethodBodyDecompiler(
                new DiagnosticLog(DISCARD, diagnostics == null
                        ? LogVerbosity.INFO : diagnostics.getVerbosity()),
                evidence.entries().stream()
                        .map(ClasspathEvidenceEntry::physicalPath).toList());
        if (diagnostics != null) {
            diagnostics.endStage(context, "classes="
                    + index.binaryNames().size() + "; issues="
                    + issues.size());
        }
        final DiagnosticContext decompileContext = DiagnosticContext.of(
                "analysis", "class-decompile")
                .with("module", evidence.module().toString());
        final List<ClassConflictResolution> resolutions =
                index.classConflictResolutions();
        if (diagnostics != null) {
            diagnostics.startStage(decompileContext,
                    "conflicts=" + resolutions.size());
        }
        final Map<String, DecompiledMethod> byDigest = new LinkedHashMap<>();
        final List<TreeClassConflict> conflicts = new ArrayList<>();
        int unavailableCount = 0;
        int warningCount = 0;
        for (ClassConflictResolution resolution : resolutions) {
            final List<TreeClassConflictCandidate> candidates =
                    new ArrayList<>();
            TreeClassConflictCandidate winner = null;
            for (ClassOwnership ownership : resolution.getCandidates()) {
                final ClasspathEvidenceEntry entry = bySource.get(
                        ownership.getSource().stableKey());
                if (entry == null) {
                    issues.add("Missing classpath binding for "
                            + ownership.getSource().stableKey());
                    continue;
                }
                DecompiledMethod source = byDigest.get(
                        ownership.getDigest());
                final boolean cached = source != null;
                if (!cached) {
                    source = decompiler.decompileClass(
                            entry.physicalPath(),
                            resolution.getBinaryName(),
                            ownership.getEntryName(),
                            entry.logicalSource());
                    byDigest.put(ownership.getDigest(), source);
                    if (!source.isAvailable()) {
                        unavailableCount++;
                        if (diagnostics != null
                                && warningCount
                                < MAX_DECOMPILE_WARNINGS) {
                            warningCount++;
                            diagnostics.warn(decompileContext,
                                    "Decompiled code unavailable; class="
                                            + resolution.getBinaryName()
                                            + "; source="
                                            + entry.logicalSource()
                                            + "; reason="
                                            + message(source
                                            .getFailureReason()));
                        }
                    }
                }
                if (diagnostics != null) {
                    diagnostics.debug(decompileContext,
                            "candidate; class="
                                    + resolution.getBinaryName()
                                    + "; origin=" + entry.origin()
                                    + "; source=" + entry.coordinates()
                                    + "; digest="
                                    + ownership.getDigest()
                                    + "; effectiveEntry="
                                    + ownership.getEntryName()
                                    + "; cached=" + cached
                                    + "; available="
                                    + source.isAvailable());
                }
                final TreeClassConflictCandidate candidate =
                        new TreeClassConflictCandidate(entry.origin(),
                                entry.coordinates().toString(), entry.scope(),
                                ownership.getDigest(),
                                ownership.getEntryName(),
                                entry.physicalPath(), source);
                candidates.add(candidate);
                if (ownership == resolution.getWinner()) {
                    winner = candidate;
                }
            }
            if (winner != null && candidates.size() >= 2) {
                final ClassConflictRisk risk = risk(
                        resolution, candidates);
                conflicts.add(new TreeClassConflict(
                        resolution.getBinaryName(), risk,
                        winner, candidates,
                        resolution.getPrecedenceReason()));
                if (diagnostics != null) {
                    diagnostics.debug(decompileContext, "conflict; class="
                            + resolution.getBinaryName() + "; risk="
                            + risk + "; candidates="
                            + candidates.size());
                }
            }
        }
        if (diagnostics != null) {
            if (unavailableCount > warningCount) {
                diagnostics.warn(decompileContext,
                        "Additional decompiled code unavailable warnings"
                                + " suppressed; count="
                                + (unavailableCount - warningCount));
            }
            final long high = conflicts.stream().filter(value ->
                    value.risk().name().equals("HIGH")).count();
            diagnostics.debug(decompileContext,
                    "decompile summary; uniqueDigests="
                            + byDigest.size() + "; unavailable="
                            + unavailableCount);
            diagnostics.endStage(decompileContext, "conflicts="
                    + conflicts.size() + "; high=" + high
                    + "; uniqueDigests=" + byDigest.size());
        }
        return new Analysis(conflicts, issues);
    }

    private ClassConflictRisk risk(
            final ClassConflictResolution resolution,
            final List<TreeClassConflictCandidate> candidates) {
        if (candidates.size() != resolution.getCandidates().size()
                || candidates.stream().anyMatch(candidate ->
                !candidate.decompiled().isAvailable())) {
            return resolution.getRisk();
        }
        final String reference = candidates.get(0)
                .decompiled().getSource();
        return candidates.stream().skip(1).anyMatch(candidate ->
                !reference.equals(candidate.decompiled().getSource()))
                ? ClassConflictRisk.HIGH : ClassConflictRisk.LOW;
    }

    private void scanEntries(
            final ModuleClasspathEvidence evidence,
            final DiagnosticContext context,
            final ClassOwnershipIndex index,
            final Map<String, ClasspathEvidenceEntry> bySource,
            final List<String> issues) {
        int entryIndex = 0;
        for (ClasspathEvidenceEntry entry : evidence.entries()) {
            entryIndex++;
            bySource.put(sourceKey(entry), entry);
            try {
                if (traceEnabled()) {
                    diagnostics.trace(context, "artifact scan started;"
                            + " progress=" + entryIndex + "/"
                            + evidence.entries().size() + "; origin="
                            + entry.origin() + "; source="
                            + entry.coordinates());
                }
                scan(index, entry);
                if (diagnostics != null) {
                    diagnostics.debug(context, "classpath entry; order="
                            + entry.order() + "; origin=" + entry.origin()
                            + "; source=" + entry.coordinates());
                    if (traceEnabled()) {
                        diagnostics.trace(context,
                                "artifact scan completed; progress="
                                        + entryIndex + "/"
                                        + evidence.entries().size()
                                        + "; classes="
                                        + index.binaryNames().size());
                    }
                }
            } catch (IOException exception) {
                issues.add("Unable to scan classpath source "
                        + entry.coordinates() + ": " + message(exception));
            }
        }
    }

    private void scan(
            final ClassOwnershipIndex index,
            final ClasspathEvidenceEntry entry) throws IOException {
        if (Files.isDirectory(entry.physicalPath())) {
            index.addDirectory(entry.physicalPath(), entry.origin());
        } else if ("jar".equals(entry.coordinates().getType())
                || entry.physicalPath().getFileName().toString()
                .endsWith(".jar")) {
            index.addJar(entry.coordinates(), entry.physicalPath(),
                    entry.origin());
        }
    }

    private String sourceKey(final ClasspathEvidenceEntry entry) {
        return entry.origin() == CodeOrigin.DEPENDENCY
                ? ClassSource.artifact(entry.coordinates()).stableKey()
                : ClassSource.path(entry.physicalPath()).stableKey();
    }

    private String message(final Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private String message(final String value) {
        if (value == null || value.isBlank()) {
            return "unspecified";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    private boolean traceEnabled() {
        return diagnostics != null && diagnostics.getVerbosity()
                .includes(LogVerbosity.TRACE);
    }

    /**
     * Module conflict analysis output.
     *
     * @param conflicts stable conflicts
     * @param issues classpath completeness issues
     */
    record Analysis(
            List<TreeClassConflict> conflicts,
            List<String> issues) {

        Analysis {
            conflicts = List.copyOf(conflicts);
            issues = List.copyOf(issues);
        }
    }
}
