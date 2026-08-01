package io.github.dependencyanalysis.report;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.callgraph.CallGraphStats;
import io.github.dependencyanalysis.diagnostic.DiagnosticEvent;
import io.github.dependencyanalysis.impact.AnalysisRunResult;
import io.github.dependencyanalysis.impact.BoundChangePoint;
import io.github.dependencyanalysis.impact.DependencyUpgradeKey;
import io.github.dependencyanalysis.impact.ImpactPath;
import io.github.dependencyanalysis.impact.ImpactClassification;
import io.github.dependencyanalysis.impact.MethodEquivalenceResult;
import io.github.dependencyanalysis.impact.MethodEquivalenceStatus;
import io.github.dependencyanalysis.impact.ModuleAnalysisResult;
import io.github.dependencyanalysis.impact.QueryEdge;
import io.github.dependencyanalysis.impact.QueryNode;
import io.github.dependencyanalysis.impact.StructuralImpact;
import io.github.dependencyanalysis.preflight.PreflightReport;
import io.github.dependencyanalysis.preflight.PreflightResult;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.MavenRuntimeDescriptor;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Atomically publishes the HTML Index and owned per-module pages. */
public final class PerModuleHtmlReportGenerator {

    /** SHA-256 algorithm. */
    private static final String SHA_256 = "SHA-256";

    /** Stable hash prefix length. */
    private static final int HASH_LENGTH = 12;

    /** CSS shared by Index and module pages. */
    private static final String CSS =
            "body{font-family:sans-serif;margin:20px;}"
            + "table{border-collapse:collapse;width:100%;}"
            + "th,td{border:1px solid #ddd;padding:7px;text-align:left;}"
            + "th{background:#f4f4f4;}pre{overflow:auto;background:#f6f8fa;"
            + "border:1px solid #ddd;padding:10px;}"
            + ".ok{color:#167d36}.warn{color:#a15c00}.fail{color:#b42318}"
            + "code{overflow-wrap:anywhere}";

    /**
     * Writes all report files to staging and atomically replaces owned output.
     *
     * @param run analysis result
     * @param events diagnostics
     * @param preflight canonical preflight report
     * @param maven selected Maven runtime
     * @param javaRuntime target JDK
     * @param output Index output file
     */
    public void generate(
            final AnalysisRunResult run,
            final List<DiagnosticEvent> events,
            final PreflightReport preflight,
            final MavenRuntimeDescriptor maven,
            final JavaRuntimeDescriptor javaRuntime,
            final Path output) {
        final Path absolute = output.toAbsolutePath().normalize();
        final Path parent = absolute.getParent() == null
                ? Path.of(".").toAbsolutePath().normalize()
                : absolute.getParent();
        final String moduleDirectoryName = moduleDirectoryName(absolute);
        Path staging = null;
        try {
            Files.createDirectories(parent);
            staging = Files.createTempDirectory(parent, ".cia-report-");
            final Path stagingModules = staging.resolve(moduleDirectoryName);
            Files.createDirectories(stagingModules);
            final Map<ModuleAnalysisResult, String> pages =
                    writeModulePages(run, stagingModules, events);
            Files.writeString(staging.resolve(absolute.getFileName()),
                    index(run, events, preflight, maven,
                            javaRuntime, moduleDirectoryName, pages));
            publish(staging, absolute, moduleDirectoryName);
        } catch (IOException exception) {
            throw new ReportException(
                    "Failed to publish HTML report: " + absolute,
                    exception);
        } finally {
            deleteTree(staging);
        }
    }

    private Map<ModuleAnalysisResult, String> writeModulePages(
            final AnalysisRunResult run,
            final Path directory,
            final List<DiagnosticEvent> events) throws IOException {
        final Map<ModuleAnalysisResult, String> result =
                new LinkedHashMap<>();
        for (ModuleAnalysisResult module : run.getModuleResults()) {
            if (module.getStatus()
                    == io.github.dependencyanalysis.impact
                    .ModuleAnalysisStatus.SKIPPED) {
                continue;
            }
            final String fileName = pageName(module);
            Files.writeString(directory.resolve(fileName),
                    modulePage(module, events));
            result.put(module, fileName);
        }
        return result;
    }

    private String index(
            final AnalysisRunResult run,
            final List<DiagnosticEvent> events,
            final PreflightReport preflight,
            final MavenRuntimeDescriptor maven,
            final JavaRuntimeDescriptor javaRuntime,
            final String moduleDirectory,
        final Map<ModuleAnalysisResult, String> pages) {
        final StringBuilder value = documentStart(
                "Impact Analysis Report");
        value.append("<h1>Impact Analysis Report</h1>")
                .append("<h2>Analysis Model Boundaries</h2><ul>")
                .append("<li>algorithm=vanilla-0-1-cfa; result is an ")
                .append("over-approximation.</li>")
                .append("<li>Entrypoints: all non-abstract PROJECT methods ")
                .append("with deterministic concrete subtype candidates.</li>")
                .append("<li>ReflectionOptions.FULL and MethodHandle analysis ")
                .append("are best-effort.</li>")
                .append("<li>ServiceLoader uses a conservative ")
                .append("Module overlay.</li>")
                .append("<li>Spring DI/AOP/annotation/XML/config and custom ")
                .append("classloader semantics are not completely ")
                .append("modeled.</li>")
                .append("<li>JDK exclusions: Swing, Applet, jfxrt/deploy/")
                .append("javaws/plugin JARs.</li>")
                .append("<li>Only target Call Graphs are built; baseline is ")
                .append("used for dependencies, old bytecode, and ")
                .append("deferred SSA.</li>")
                .append("<li>PROVEN_EQUIVALENT is limited to the supported ")
                .append("normalized WALA SSA/CFG model.</li></ul>")
                .append("<h2>Run</h2><table>")
                .append(row("status", run.getStatus()))
                .append(row("mode", run.getMode()))
                .append(row("algorithm", "vanilla-0-1-cfa"))
                .append(row("WALA", walaVersion()))
                .append(row("JDK", javaRuntime.getVersion()))
                .append(row("Maven", maven.getVersion()))
                .append(row("configured module parallelism",
                        run.getConfiguredParallelism()))
                .append(row("actual module parallelism",
                        run.getActualParallelism()))
                .append(row("configured JAR diff workers",
                        run.getConfiguredJarDiffWorkers()))
                .append(row("actual JAR diff workers",
                        run.getActualJarDiffWorkers()))
                .append(row("SSA equivalence workers", 1))
                .append(row("raw ChangePoints", run.getModuleResults()
                        .stream().mapToInt(module -> module.getUnit()
                                .getChangePoints().size()).sum()))
                .append(row("candidate Impact Paths",
                        run.getModuleResults().stream().mapToInt(module ->
                                module.getCandidatePaths().size()).sum()))
                .append(row("equivalent-filtered Impact Paths",
                        run.getModuleResults().stream().mapToInt(module ->
                                module.getCandidatePaths().size()
                                        - module.getFinalPaths().size())
                                .sum()))
                .append(row("final Impact Paths",
                        run.getModuleResults().stream().mapToInt(module ->
                                module.getFinalPaths().size()).sum()))
                .append(row("SSA PROVEN_EQUIVALENT / DIFFERENT / UNKNOWN",
                        ssaCounts(run.getModuleResults())))
                .append("</table>")
                .append("<h2>Stage Metrics</h2><table><tr><th>stage</th>")
                .append("<th>elapsedMillis</th></tr>");
        run.getStageElapsedMillis().forEach((stage, millis) -> value
                .append("<tr><td>").append(escape(stage))
                .append("</td><td>").append(millis).append("</td></tr>"));
        value.append("</table><h2>Modules</h2>")
                .append("<table><tr><th>Module</th><th>Status</th>")
                .append("<th>Reason</th><th>ChangePoints</th>")
                .append("<th>Candidate / Filtered / Final</th>")
                .append("<th>Direct / Transitive</th><th>Affected Methods</th>")
                .append("<th>Affected Classes / Structural</th>")
                .append("<th>Scope P / R / D; Entrypoints</th>")
                .append("<th>CG Nodes / Edges / Contexts</th>")
                .append("<th>SSA E / D / U; Limitations</th></tr>");
        for (ModuleAnalysisResult module : run.getModuleResults()) {
            final String page = pages.get(module);
            final long filtered = module.getCandidatePaths().size()
                    - module.getFinalPaths().size();
            final long direct = module.getFinalPaths().stream()
                    .filter(path -> path.getClassification()
                            == ImpactClassification.DIRECT).count();
            final long transitive = module.getFinalPaths().stream()
                    .filter(path -> path.getClassification()
                            == ImpactClassification.TRANSITIVE).count();
            final long affected = module.getFinalPaths().stream()
                    .map(ImpactPath::getAffectedMethod).distinct().count();
            final long affectedClasses = module.getFinalPaths().stream()
                    .map(path -> path.getAffectedMethod().owner())
                    .distinct().count();
            final CallGraphStats stats = module.getCallGraphStats();
            value.append("<tr><td>");
            if (page == null) {
                value.append(escape(module.getModuleId().stableKey()));
            } else {
                value.append("<a href=\"")
                        .append(escape(moduleDirectory + "/" + page))
                        .append("\">")
                        .append(escape(module.getModuleId().stableKey()))
                        .append("</a>");
            }
            value.append("</td><td>").append(module.getStatus())
                    .append("</td><td>").append(module.getReason())
                    .append("</td><td>")
                    .append(module.getUnit().getChangePoints().size())
                    .append("</td><td>")
                    .append(module.getCandidatePaths().size()).append(" / ")
                    .append(filtered).append(" / ")
                    .append(module.getFinalPaths().size())
                    .append("</td><td>").append(direct).append(" / ")
                    .append(transitive).append("</td><td>")
                    .append(affected).append("</td><td>")
                    .append(affectedClasses).append(" / ")
                    .append(module.getStructuralImpacts().size())
                    .append("</td><td>")
                    .append("1 / ")
                    .append(module.getUnit()
                            .getReactorDependencyClasses().size())
                    .append(" / ")
                    .append(module.getUnit().getTargetArtifacts().size())
                    .append("; ")
                    .append(module.getSession() == null ? "-"
                            : module.getSession().getEntrypointCount())
                    .append("</td><td>")
                    .append(stats == null ? "-" : stats.methodCount()
                            + " / " + stats.edgeCount() + " / "
                            + contextCount(module))
                    .append("</td><td>")
                    .append(ssaCounts(List.of(module)))
                    .append("; ")
                    .append(module.getLimitations().size())
                    .append("</td></tr>");
        }
        value.append("</table><h2>Dependency Changes</h2><p>")
                .append(run.getDependencyChanges().size())
                .append(" resolved changes.</p>");
        appendPreflight(value, preflight);
        value.append("<h2>Diagnostics</h2><ul>");
        for (DiagnosticEvent event : events) {
            value.append("<li>").append(escape(event.getStage()))
                    .append(": ").append(escape(event.getMessage()))
                    .append("</li>");
        }
        return value.append("</ul></body></html>").toString();
    }

    private String modulePage(
            final ModuleAnalysisResult module,
            final List<DiagnosticEvent> events) {
        final StringBuilder value = documentStart(
                module.getModuleId().stableKey());
        value.append("<h1>")
                .append(escape(module.getModuleId().stableKey()))
                .append("</h1><table>")
                .append(row("status", module.getStatus()))
                .append(row("reason", module.getReason()))
                .append(row("detail", module.getDetail()))
                .append(row("PROJECT classes",
                        module.getUnit().getProjectClasses()))
                .append(row("REACTOR_DEPENDENCY entries",
                        module.getUnit().getReactorDependencyClasses().size()))
                .append(row("DEPENDENCY entries",
                        module.getUnit().getTargetArtifacts().size()))
                .append(row("JDK exclusions",
                        "Swing/Applet classes; "
                                + "jfxrt/deploy/javaws/plugin JARs"))
                .append(row("elapsedMillis", module.getElapsedMillis()))
                .append(sessionRows(module))
                .append("</table><h2>Stage Metrics</h2><table>")
                .append("<tr><th>stage</th><th>elapsedMillis</th></tr>");
        module.getStageElapsedMillis().forEach((stage, millis) -> value
                .append("<tr><td>").append(escape(stage))
                .append("</td><td>").append(millis).append("</td></tr>"));
        value.append("</table><h2>Classpath</h2><ul>");
        module.getUnit().getReactorDependencyClasses().forEach(path -> value
                .append("<li>REACTOR_DEPENDENCY: ")
                .append(escape(path.toString())).append("</li>"));
        module.getUnit().getTargetArtifacts().forEach(artifact -> value
                .append("<li>DEPENDENCY: ")
                .append(escape(artifact.getArtifact().toString()))
                .append(" [").append(artifact.getScope()).append("] ")
                .append(escape(artifact.getPath().toString()))
                .append("</li>"));
        value.append("</ul><h2>Dependency Upgrades</h2>")
                .append("<table><tr><th>Scope</th><th>Old artifact</th>")
                .append("<th>New artifact</th><th>Old physical path</th>")
                .append("<th>New physical path</th></tr>");
        module.getUnit().getChangePoints().stream()
                .map(BoundChangePoint::getDependencyUpgradeKey)
                .distinct()
                .sorted(Comparator.comparing(DependencyUpgradeKey::stableKey))
                .forEach(key -> value.append("<tr><td>")
                        .append(key.getScope()).append("</td><td>")
                        .append(escape(key.getOldArtifact().toString()))
                        .append("</td><td>")
                        .append(escape(key.getNewArtifact().toString()))
                        .append("</td><td><code>")
                        .append(escape(key.getOldPath().toString()))
                        .append("</code></td><td><code>")
                        .append(escape(key.getNewPath().toString()))
                        .append("</code></td></tr>"));
        value.append("</table><h2>BoundChangePoints</h2>")
                .append("<table><tr><th>Kind</th><th>Member</th>")
                .append("<th>Old Descriptor</th><th>New Descriptor</th>")
                .append("<th>Old / New Hash</th>")
                .append("<th>Disposition</th><th>SSA</th></tr>");
        for (BoundChangePoint bound : module.getUnit().getChangePoints()) {
            final ChangePoint point = bound.getChangePoint();
            final MethodEquivalenceResult equivalence =
                    module.getEquivalenceResults().get(bound);
            value.append("<tr><td>").append(point.getKind())
                    .append("</td><td>")
                    .append(escape(point.getOwner() + "#"
                            + String.valueOf(point.getName())))
                    .append("</td><td>")
                    .append(escape(String.valueOf(point.getOldDescriptor())))
                    .append("</td><td>")
                    .append(escape(String.valueOf(point.getNewDescriptor())))
                    .append("</td><td><code>")
                    .append(escape(String.valueOf(point.getOldHash())))
                    .append("</code><br><code>")
                    .append(escape(String.valueOf(point.getNewHash())))
                    .append("</code>")
                    .append("</td><td>")
                    .append(module.getDispositions().get(bound))
                    .append("</td><td>")
                    .append(equivalence == null ? "-"
                            : equivalence.getStatus() + ": "
                            + escape(equivalence.getReason()))
                    .append("</td></tr>");
        }
        value.append("</table><h2>Impact Paths</h2>");
        if (module.getFinalPaths().isEmpty()) {
            value.append("<p>在声明的analysis model内未发现Impact Path。</p>");
        }
        for (ImpactPath path : module.getFinalPaths()) {
            value.append("<h3>")
                    .append(escape(path.getAffectedMethod().toString()))
                    .append(" — ").append(path.getClassification())
                    .append("</h3><ol>");
            for (QueryNode node : path.getNodes()) {
                value.append("<li><code>")
                        .append(escape(node.methodId().toString()))
                        .append("</code> [").append(node.origin())
                        .append("]</li>");
            }
            value.append("<li><code>")
                    .append(escape(path.getTerminal().getChangePoint()
                            .stableKey()))
                    .append("</code> [").append(path.getTerminal()
                            .getEdgeKind()).append("] ")
                    .append(escape(path.getTerminal().getEvidence()))
                    .append("</li></ol><ul>");
            for (QueryEdge edge : path.getOrderedEdges()) {
                value.append("<li>").append(edge.getKind())
                        .append(" pc=").append(edge.getBytecodePc())
                        .append(" ").append(escape(edge.getEvidence()))
                        .append("</li>");
            }
            value.append("</ul>");
        }
        value.append("<h2>Structural Impacts</h2><ul>");
        for (StructuralImpact impact : module.getStructuralImpacts()) {
            value.append("<li>").append(escape(
                            impact.getReferencingClass()))
                    .append(" [").append(impact.getOrigin()).append("] ")
                    .append(escape(impact.getEvidence())).append("</li>");
        }
        value.append("</ul><h2>Coverage Limitations</h2><ul>");
        for (String limitation : module.getLimitations()) {
            value.append("<li>").append(escape(limitation)).append("</li>");
        }
        value.append("</ul><h2>Module Diagnostics</h2><ul>");
        for (DiagnosticEvent event : moduleEvents(module, events)) {
            value.append("<li>").append(event.getLevel()).append(" ")
                    .append(escape(event.getStage())).append(": ")
                    .append(escape(event.getMessage())).append("</li>");
        }
        return value.append("</ul></body></html>").toString();
    }

    private String ssaCounts(final List<ModuleAnalysisResult> modules) {
        final long equivalent = equivalenceCount(
                modules, MethodEquivalenceStatus.PROVEN_EQUIVALENT);
        final long different = equivalenceCount(
                modules, MethodEquivalenceStatus.DIFFERENT);
        final long unknown = equivalenceCount(
                modules, MethodEquivalenceStatus.UNKNOWN);
        return equivalent + " / " + different + " / " + unknown;
    }

    private long equivalenceCount(
            final List<ModuleAnalysisResult> modules,
            final MethodEquivalenceStatus status) {
        return modules.stream()
                .flatMap(module -> module.getEquivalenceResults()
                        .values().stream())
                .filter(result -> result.getStatus() == status)
                .count();
    }

    private List<DiagnosticEvent> moduleEvents(
            final ModuleAnalysisResult module,
            final List<DiagnosticEvent> events) {
        final String key = module.getModuleId().stableKey();
        return events.stream().filter(event -> key.equals(event.getModule())
                        || event.getStage() != null
                        && event.getStage().contains(key)
                        || event.getMessage() != null
                        && event.getMessage().contains(key))
                .toList();
    }

    private void appendPreflight(
            final StringBuilder value,
            final PreflightReport preflight) {
        value.append("<h2>Preflight</h2><table><tr><th>Check</th>")
                .append("<th>Status</th><th>Evidence</th></tr>");
        for (PreflightResult result : preflight.getResults()) {
            value.append("<tr><td>").append(escape(result.getCheckId()))
                    .append("</td><td>").append(result.getStatus())
                    .append("</td><td>")
                    .append(escape(result.getEvidence()))
                    .append("</td></tr>");
        }
        value.append("</table>");
    }

    private long contextCount(final ModuleAnalysisResult module) {
        return module.getSession() == null ? 0L
                : module.getSession().getGraph().stream()
                .map(com.ibm.wala.ipa.callgraph.CGNode::getContext)
                .distinct().count();
    }

    private String sessionRows(final ModuleAnalysisResult module) {
        if (module.getSession() == null) {
            return "";
        }
        return row("entrypoints",
                module.getSession().getEntrypointCount())
                + row("parameter candidates",
                module.getSession().getParameterCandidateCount())
                + row("Call Graph nodes",
                module.getSession().getStats().methodCount())
                + row("Call Graph edges",
                module.getSession().getStats().edgeCount())
                + row("Call Graph contexts", contextCount(module));
    }

    private String row(final String name, final Object value) {
        return "<tr><th>" + escape(name) + "</th><td>"
                + escape(String.valueOf(value)) + "</td></tr>";
    }

    private StringBuilder documentStart(final String title) {
        return new StringBuilder(
                "<!DOCTYPE html><html><head><meta charset=\"UTF-8\">")
                .append("<title>").append(escape(title))
                .append("</title><style>").append(CSS)
                .append("</style></head><body>");
    }

    private String pageName(final ModuleAnalysisResult module) {
        final String sanitized = module.getModuleId().getCoordinate()
                .getGroupId().concat("-")
                .concat(module.getModuleId().getCoordinate().getArtifactId())
                .replaceAll("[^A-Za-z0-9._-]", "-");
        return sanitized + "-" + stableHash(
                module.getModuleId().stableKey()) + ".html";
    }

    private String moduleDirectoryName(final Path output) {
        final String name = output.getFileName().toString();
        final int dot = name.lastIndexOf('.');
        return (dot < 0 ? name : name.substring(0, dot)) + "-modules";
    }

    private String stableHash(final String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest
                    .getInstance(SHA_256)
                    .digest(value.getBytes(
                            java.nio.charset.StandardCharsets.UTF_8)))
                    .substring(0, HASH_LENGTH);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(SHA_256 + " unavailable",
                    exception);
        }
    }

    private String walaVersion() {
        final String value = com.ibm.wala.ipa.callgraph.CallGraph.class
                .getPackage().getImplementationVersion();
        return value == null ? "1.8.0" : value;
    }

    private String escape(final String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private void publish(
            final Path staging,
            final Path output,
            final String moduleDirectoryName) throws IOException {
        final Path parent = output.getParent();
        final Path owned = parent.resolve(moduleDirectoryName);
        final Path stagedModules = staging.resolve(moduleDirectoryName);
        final Path stagedIndex = staging.resolve(output.getFileName());
        final Path backup = parent.resolve("." + moduleDirectoryName
                + "-backup-" + UUID.randomUUID());
        final boolean hadOwned = Files.exists(owned);
        if (hadOwned) {
            move(owned, backup, false);
        }
        try {
            move(stagedModules, owned, false);
            move(stagedIndex, output, true);
            deleteTree(backup);
        } catch (IOException exception) {
            deleteTree(owned);
            if (hadOwned && Files.exists(backup)) {
                move(backup, owned, false);
            }
            throw exception;
        }
    }

    private void move(
            final Path source,
            final Path target,
            final boolean replace) throws IOException {
        try {
            if (replace) {
                Files.move(source, target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, target,
                        StandardCopyOption.ATOMIC_MOVE);
            }
        } catch (AtomicMoveNotSupportedException exception) {
            if (replace) {
                Files.move(source, target,
                        StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, target);
            }
        }
    }

    private void deleteTree(final Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> stream = Files.walk(root)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup of command-owned staging.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup of command-owned staging.
        }
    }
}
