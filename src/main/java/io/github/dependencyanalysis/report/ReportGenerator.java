package io.github.dependencyanalysis.report;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.DecompiledMethod;
import io.github.dependencyanalysis.bytecode.MethodBodyEvidence;
import io.github.dependencyanalysis.callgraph.CallEdge;
import io.github.dependencyanalysis.cli.OutputFormat;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ChangeType;
import io.github.dependencyanalysis.dependency.DependencyChange;
import io.github.dependencyanalysis.diagnostic.DiagnosticEvent;
import io.github.dependencyanalysis.impact.ImpactPath;
import io.github.dependencyanalysis.impact.ImpactResult;
import io.github.dependencyanalysis.preflight.PreflightResult;
import io.github.dependencyanalysis.runtime
        .MavenRuntimeDescriptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Wiki: wiki/features/report-generator.md - 报告生成核心类，HTML/Markdown 双格式输出
/**
 * Generates HTML or Markdown impact
 * analysis reports.
 * generate() writes an index file and
 * three sub-files; generateToString()
 * returns a single-file string.
 */
public final class ReportGenerator {

    /** CSS styles for HTML. */
    private static final String CSS =
            "body{font-family:sans-serif;"
            + "margin:20px;}"
            + "h1,h2,h3{color:#333;}"
            + "table{border-collapse:"
            + "collapse;width:100%;}"
            + "th,td{border:1px solid #ddd;"
            + "padding:8px;text-align:left;}"
            + "th{background:#f4f4f4;}"
            + ".added{color:green;}"
            + ".removed{color:red;}"
            + ".changed{color:orange;}"
            + ".warn{color:orange;}"
            + ".info{color:#666;}"
            + ".method-body-grid{display:grid;"
            + "grid-template-columns:repeat(2,minmax(0,1fr));"
            + "gap:16px;}"
            + ".method-body-side{min-width:0;}"
            + "pre{background:#f6f8fa;border:1px solid #ddd;"
            + "overflow:auto;padding:12px;}"
            + "@media(max-width:800px){.method-body-grid{"
            + "grid-template-columns:1fr;}}";

    /** Date formatter. */
    private static final DateTimeFormatter
            DATE_FMT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Creates a new report generator.
     */
    public ReportGenerator() {
    }

    /**
     * Generates a report to file.
     *
     * @param changes dependency changes
     * @param points  change points
     * @param result  impact result
     * @param events  diagnostic events
     * @param format  output format
     * @param output  output path
     */
    public void generate(
            final List<DependencyChange>
                    changes,
            final List<ChangePoint>
                    points,
            final ImpactResult result,
            final List<DiagnosticEvent>
                    events,
            final OutputFormat format,
            final Path output) {
        generate(changes, points, result,
                Collections.emptyList(), events,
                format, output);
    }

    /**
     * Generates a report with method body evidence.
     *
     * @param changes dependency changes
     * @param points change points
     * @param result impact result
     * @param evidence method body evidence
     * @param events diagnostic events
     * @param format output format
     * @param output output path
     */
    public void generate(
            final List<DependencyChange> changes,
            final List<ChangePoint> points,
            final ImpactResult result,
            final List<MethodBodyEvidence> evidence,
            final List<DiagnosticEvent> events,
            final OutputFormat format,
            final Path output) {
        Objects.requireNonNull(changes,
                "changes");
        Objects.requireNonNull(points,
                "points");
        Objects.requireNonNull(result,
                "result");
        Objects.requireNonNull(evidence,
                "evidence");
        Objects.requireNonNull(events,
                "events");
        Objects.requireNonNull(format,
                "format");
        Objects.requireNonNull(output,
                "output");
        try {
            if (format == OutputFormat.MD) {
                writeIndexMd(output,
                        changes, points,
                        result, events);
                writeMdSub(
                        resolveSubPath(output,
                                "-dependencies"),
                        "Dependency Changes",
                        depBodyMd(changes));
                writeMdSub(
                        resolveSubPath(output,
                                "-internal-changes"),
                        "Internal Changes",
                        intBodyMd(points));
                writeMdSub(
                        resolveSubPath(output,
                                "-impact-paths"),
                        "Impact Paths",
                        impBodyMd(result, evidence));
            } else {
                writeIndexHtml(output,
                        changes, points,
                        result, events);
                writeHtmlSub(
                        resolveSubPath(output,
                                "-dependencies"),
                        "Dependency Changes",
                        depBody(changes));
                writeHtmlSub(
                        resolveSubPath(output,
                                "-internal-changes"),
                        "Internal Changes",
                        intBody(points));
                writeHtmlSub(
                        resolveSubPath(output,
                                "-impact-paths"),
                        "Impact Paths",
                        impBody(result, evidence));
            }
        } catch (IOException e) {
            throw new ReportException(
                    "Failed to write: "
                    + output, e);
        }
    }

    /**
     * Generates an impact report with canonical preflight metadata.
     *
     * @param changes dependency changes
     * @param points change points
     * @param result impact result
     * @param events diagnostics
     * @param metadata preflight and runtime metadata
     * @param format output format
     * @param output output path
     */
    public void generate(
            final List<DependencyChange> changes,
            final List<ChangePoint> points,
            final ImpactResult result,
            final List<DiagnosticEvent> events,
            final ImpactReportMetadata metadata,
            final OutputFormat format,
            final Path output) {
        generate(changes, points, result,
                metadata.getMethodBodyEvidence(),
                events, format, output);
        try {
            final String content =
                    Files.readString(output);
            final String section = format
                    == OutputFormat.MD
                    ? preflightMarkdown(
                            metadata.getPreflight(),
                            metadata.getRuntime())
                    : preflightHtml(
                            metadata.getPreflight(),
                            metadata.getRuntime());
            final String updated;
            if (format == OutputFormat.MD) {
                updated = content + section;
            } else {
                updated = content.replace(
                        "</body>",
                        section + "</body>");
            }
            Files.writeString(output, updated);
        } catch (IOException exception) {
            throw new ReportException(
                    "Failed to add preflight: "
                            + output, exception);
        }
    }

    private String preflightHtml(
            final io.github.dependencyanalysis
                    .preflight.PreflightReport report,
            final MavenRuntimeDescriptor runtime) {
        final StringBuilder value =
                new StringBuilder();
        value.append("<h2>Preflight</h2>\n")
                .append("<p>Maven: ")
                .append(escapeHtml(runtime.getSource()
                        + " "
                        + runtime.getExecutable()
                        + " "
                        + runtime.getVersion()))
                .append("</p>\n<table><tr>")
                .append("<th>checkId</th>")
                .append("<th>status</th>")
                .append("<th>decision</th>")
                .append("<th>evidence</th>")
                .append("</tr>\n");
        for (PreflightResult item
                : report.getResults()) {
            value.append("<tr><td>")
                    .append(escapeHtml(
                            item.getCheckId()))
                    .append("</td><td>")
                    .append(item.getStatus())
                    .append("</td><td>")
                    .append(item.getDecision())
                    .append("</td><td>")
                    .append(escapeHtml(
                            item.getEvidence()))
                    .append("</td></tr>\n");
        }
        return value.append("</table>\n")
                .toString();
    }

    private String preflightMarkdown(
            final io.github.dependencyanalysis
                    .preflight.PreflightReport report,
            final MavenRuntimeDescriptor runtime) {
        final StringBuilder value =
                new StringBuilder();
        value.append("\n## Preflight\n\n")
                .append("Maven: `")
                .append(runtime.getSource())
                .append(" ")
                .append(runtime.getExecutable())
                .append(" ")
                .append(runtime.getVersion())
                .append("`\n\n")
                .append("| checkId | status | decision |")
                .append(" evidence |\n")
                .append("|---|---|---|---|\n");
        for (PreflightResult item
                : report.getResults()) {
            value.append("| ")
                    .append(item.getCheckId())
                    .append(" | ")
                    .append(item.getStatus())
                    .append(" | ")
                    .append(item.getDecision())
                    .append(" | ")
                    .append(item.getEvidence()
                            .replace("|", "\\|"))
                    .append(" |\n");
        }
        return value.toString();
    }

    private String escapeHtml(final String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * Generates report content as a string.
     *
     * @param changes dependency changes
     * @param points  change points
     * @param result  impact result
     * @param events  diagnostic events
     * @param format  output format
     * @return report content string
     */
    public String generateToString(
            final List<DependencyChange>
                    changes,
            final List<ChangePoint>
                    points,
            final ImpactResult result,
            final List<DiagnosticEvent>
                    events,
            final OutputFormat format) {
        return generateToString(changes, points,
                result, Collections.emptyList(),
                events, format);
    }

    /**
     * Generates report content with method body evidence.
     *
     * @param changes dependency changes
     * @param points change points
     * @param result impact result
     * @param evidence method body evidence
     * @param events diagnostic events
     * @param format output format
     * @return report content string
     */
    public String generateToString(
            final List<DependencyChange> changes,
            final List<ChangePoint> points,
            final ImpactResult result,
            final List<MethodBodyEvidence> evidence,
            final List<DiagnosticEvent> events,
            final OutputFormat format) {
        Objects.requireNonNull(changes,
                "changes");
        Objects.requireNonNull(points,
                "points");
        Objects.requireNonNull(result,
                "result");
        Objects.requireNonNull(evidence,
                "evidence");
        Objects.requireNonNull(events,
                "events");
        Objects.requireNonNull(format,
                "format");
        if (format == OutputFormat.MD) {
            return generateMarkdown(
                    changes, points,
                    result, evidence,
                    events);
        }
        return generateHtml(
                changes, points,
                result, evidence,
                events);
    }

    /**
     * Generates HTML report content.
     *
     * @param changes dependency changes
     * @param points  change points
     * @param result  impact result
     * @param evidence method body evidence
     * @param events  diagnostic events
     * @return HTML string
     */
    private String generateHtml(
            final List<DependencyChange>
                    changes,
            final List<ChangePoint>
                    points,
            final ImpactResult result,
            final List<MethodBodyEvidence>
                    evidence,
            final List<DiagnosticEvent>
                    events) {
        final StringBuilder sb =
                new StringBuilder();
        sb.append("<!DOCTYPE html>\n")
                .append("<html>\n<head>\n")
                .append("<meta charset=")
                .append("\"UTF-8\">\n")
                .append("<title>Impact ")
                .append("Analysis ")
                .append("Report</title>\n")
                .append("<style>")
                .append(CSS)
                .append("</style>\n")
                .append("</head>\n<body>\n");
        appendHtmlHeader(sb);
        appendHtmlSummary(sb, changes,
                points, result);
        appendHtmlDependencyChanges(sb,
                changes);
        appendHtmlChangePoints(sb,
                points);
        appendHtmlImpactPaths(sb, result,
                evidence);
        appendHtmlDiagnostics(sb,
                events);
        sb.append("</body>\n</html>\n");
        return sb.toString();
    }

    /**
     * Appends HTML header section.
     *
     * @param sb string builder
     */
    private void appendHtmlHeader(
            final StringBuilder sb) {
        sb.append("<h1>Impact ")
                .append("Analysis ")
                .append("Report</h1>\n")
                .append("<p class=\"info\">")
                .append("Generated: ")
                .append(LocalDateTime.now()
                        .format(DATE_FMT))
                .append("</p>\n");
    }

    /**
     * Appends HTML summary section.
     *
     * @param sb      string builder
     * @param changes dependency changes
     * @param points  change points
     * @param result  impact result
     */
    private void appendHtmlSummary(
            final StringBuilder sb,
            final List<DependencyChange>
                    changes,
            final List<ChangePoint>
                    points,
            final ImpactResult result) {
        sb.append("<h2>Summary</h2>\n")
                .append("<ul>\n")
                .append("<li>Dependency ")
                .append("Changes: ")
                .append(changes.size())
                .append("</li>\n")
                .append("<li>Change ")
                .append("Points: ")
                .append(points.size())
                .append("</li>\n")
                .append("<li>Impact ")
                .append("Paths: ")
                .append(result.getPaths()
                        .size())
                .append("</li>\n")
                .append("</ul>\n");
    }

    /**
     * Appends HTML summary with links.
     *
     * @param sb      string builder
     * @param changes dependency changes
     * @param points  change points
     * @param result  impact result
     * @param depFile dependency sub-file
     * @param intFile internal sub-file
     * @param impFile impact sub-file
     */
    private void appendHtmlSummary(
            final StringBuilder sb,
            final List<DependencyChange>
                    changes,
            final List<ChangePoint>
                    points,
            final ImpactResult result,
            final String depFile,
            final String intFile,
            final String impFile) {
        sb.append("<h2>Summary</h2>\n")
                .append("<ul>\n")
                .append("<li>Dependency ")
                .append("Changes: ")
                .append(changes.size())
                .append(" (<a href=\"")
                .append(depFile)
                .append("\">details</a>)")
                .append("</li>\n")
                .append("<li>Internal ")
                .append("Changes: ")
                .append(points.size())
                .append(" (<a href=\"")
                .append(intFile)
                .append("\">details</a>)")
                .append("</li>\n")
                .append("<li>Impact ")
                .append("Paths: ")
                .append(result.getPaths()
                        .size())
                .append(" (<a href=\"")
                .append(impFile)
                .append("\">details</a>)")
                .append("</li>\n")
                .append("</ul>\n");
    }

    /**
     * Appends HTML dependency
     * changes section grouped
     * by module.
     *
     * @param sb      string builder
     * @param changes dependency changes
     */
    private void
            appendHtmlDependencyChanges(
                    final StringBuilder sb,
                    final List<DependencyChange>
                            changes) {
        sb.append("<h2>Dependency ")
                .append("Changes</h2>\n");
        if (changes.isEmpty()) {
            sb.append("<p>No dependency ")
                    .append("changes.")
                    .append("</p>\n");
            return;
        }
        final Map<String,
                List<DependencyChange>> grouped =
                groupChangesByModule(changes);
        for (Map.Entry<String,
                List<DependencyChange>> entry
                : grouped.entrySet()) {
            sb.append("<h3>Module: ")
                    .append(entry.getKey())
                    .append("</h3>\n")
                    .append("<table>\n<tr>")
                    .append("<th>Type</th>")
                    .append("<th>Artifact")
                    .append("</th>")
                    .append("<th>Old ")
                    .append("Version</th>")
                    .append("<th>Scope</th>")
                    .append("</tr>\n");
            for (DependencyChange ch
                    : entry.getValue()) {
                appendHtmlChangeRow(sb, ch);
            }
            sb.append("</table>\n");
        }
    }

    /**
     * Groups dependency changes by
     * module preserving order.
     *
     * @param changes dependency changes
     * @return map of module to changes
     */
    private Map<String,
            List<DependencyChange>>
            groupChangesByModule(
                    final List<DependencyChange>
                            changes) {
        final Map<String,
                List<DependencyChange>> map =
                new LinkedHashMap<>();
        for (DependencyChange ch : changes) {
            final String mod = ch.getModule();
            List<DependencyChange> list =
                    map.get(mod);
            if (list == null) {
                list = new ArrayList<>();
                map.put(mod, list);
            }
            list.add(ch);
        }
        return map;
    }

    /**
     * Appends HTML change row.
     *
     * @param sb string builder
     * @param ch dependency change
     */
    private void appendHtmlChangeRow(
            final StringBuilder sb,
            final DependencyChange ch) {
        final ChangeType type =
                ch.getChangeType();
        final String cssClass;
        final String typeLabel;
        final ArtifactCoord art;
        if (type == ChangeType.ADDED) {
            cssClass = "added";
            typeLabel = "ADDED";
            art = ch.getNewArtifact();
        } else if (type
                == ChangeType.REMOVED) {
            cssClass = "removed";
            typeLabel = "REMOVED";
            art = ch.getOldArtifact();
        } else {
            cssClass = "changed";
            typeLabel = "VERSION_CHANGED";
            art = ch.getNewArtifact();
        }
        final String oldVer;
        if (type == ChangeType
                .VERSION_CHANGED) {
            oldVer = ch.getOldArtifact()
                    .getVersion();
        } else {
            oldVer = "N/A";
        }
        sb.append("<tr class=\"")
                .append(cssClass)
                .append("\"><td>")
                .append(typeLabel)
                .append("</td><td>")
                .append(formatArtifact(art))
                .append("</td><td>")
                .append(oldVer)
                .append("</td><td>")
                .append(ch.getScope());
        if (ch.isCompileTimeApiRisk()) {
            sb.append(" <span class=")
                    .append("\"warn\">")
                    .append("[API risk]")
                    .append("</span>");
        }
        sb.append("</td></tr>\n");
    }

    /**
     * Appends HTML change points
     * section.
     *
     * @param sb     string builder
     * @param points change points
     */
    private void
            appendHtmlChangePoints(
                    final StringBuilder sb,
                    final List<ChangePoint>
                            points) {
        sb.append("<h2>Internal ")
                .append("Changes")
                .append("</h2>\n");
        if (points.isEmpty()) {
            sb.append("<p>No static ")
                    .append("confirmed ")
                    .append("impact.")
                    .append("</p>\n");
            return;
        }
        sb.append("<table>\n<tr>")
                .append("<th>Artifact")
                .append("</th>")
                .append("<th>Kind</th>")
                .append("<th>Owner</th>")
                .append("<th>Name</th>")
                .append("<th>Descriptor")
                .append("</th>")
                .append("</tr>\n");
        for (ChangePoint cp : points) {
            sb.append("<tr><td>")
                    .append(formatArtifact(
                            cp.getArtifact()))
                    .append("</td><td>")
                    .append(cp.getKind())
                    .append("</td><td>")
                    .append(cp.getOwner())
                    .append("</td><td>")
                    .append(cp.getName())
                    .append("</td><td>")
                    .append(cp.getDescriptor())
                    .append("</td></tr>\n");
        }
        sb.append("</table>\n");
    }

    /**
     * Appends HTML impact paths
     * section.
     *
     * @param sb     string builder
     * @param result impact result
     * @param evidence method body evidence
     */
    private void appendHtmlImpactPaths(
            final StringBuilder sb,
            final ImpactResult result,
            final List<MethodBodyEvidence>
                    evidence) {
        sb.append("<h2>Impact ")
                .append("Paths</h2>\n");
        final List<ImpactPath> paths =
                result.getPaths();
        if (paths.isEmpty()) {
            sb.append("<p>No static ")
                    .append("confirmed ")
                    .append("impact.")
                    .append("</p>\n");
            return;
        }
        final Map<ChangePoint, String> ids =
                evidenceIds(evidence);
        for (int i = 0; i < paths.size();
                i++) {
            appendHtmlImpactPath(sb,
                    paths.get(i), i + 1,
                    ids);
        }
        appendHtmlMethodBodyEvidence(sb,
                evidence, ids);
    }

    /**
     * Appends single HTML impact path.
     *
     * @param sb   string builder
     * @param path impact path
     * @param num  path number
     * @param evidenceIds evidence identifiers
     */
    private void appendHtmlImpactPath(
            final StringBuilder sb,
            final ImpactPath path,
            final int num,
            final Map<ChangePoint, String>
                    evidenceIds) {
        sb.append("<h3>Path #")
                .append(num)
                .append("</h3>\n")
                .append("<p><b>Affected:")
                .append("</b> ")
                .append(formatMethod(
                        path.getAffectedMethod()))
                .append("</p>\n");
        appendHtmlCallChain(sb, path);
        sb.append("<p><b>Change:")
                .append("</b> ")
                .append(path.getChangePoint()
                        .getKind())
                .append(" in ")
                .append(formatChangePointLocation(
                        path.getChangePoint()))
                .append("</p>\n");
        final String evidenceId = evidenceIds.get(
                path.getChangePoint());
        if (evidenceId != null) {
            sb.append("<p><b>Method body evidence:</b> ")
                    .append("<a href=\"#method-body-evidence-")
                    .append(evidenceId.toLowerCase())
                    .append("\">")
                    .append(evidenceId)
                    .append("</a></p>\n");
        }
        if (!path.getModules().isEmpty()) {
            sb.append("<p><b>Modules:")
                    .append("</b> ")
                    .append(path.getModules())
                    .append("</p>\n");
        }
        if (!path.getCrossModuleBoundaries()
                .isEmpty()) {
            sb.append("<p class=\"warn\">")
                    .append("<b>Cross-")
                    .append("module:</b> ")
                    .append(path
                            .getCrossModuleBoundaries()
                            .size())
                    .append(" boundary edges")
                    .append("</p>\n");
        }
    }

    /**
     * Appends decompiled old/new method body evidence.
     *
     * @param sb string builder
     * @param evidence evidence list
     * @param ids stable evidence identifiers
     */
    private void appendHtmlMethodBodyEvidence(
            final StringBuilder sb,
            final List<MethodBodyEvidence> evidence,
            final Map<ChangePoint, String> ids) {
        if (ids.isEmpty()) {
            return;
        }
        sb.append("<h2>Method Body Evidence</h2>\n")
                .append("<p class=\"info\">")
                .append("Decompiler output is Java-like evidence; ")
                .append("it may differ from the original source.")
                .append("</p>\n");
        for (MethodBodyEvidence item
                : uniqueEvidence(evidence)) {
            final String id = ids.get(
                    item.getChangePoint());
            sb.append("<section id=\"method-body-evidence-")
                    .append(id.toLowerCase())
                    .append("\">\n<h3>")
                    .append(id)
                    .append("</h3>\n<p><b>Method:</b> ")
                    .append(escapeHtml(formatEvidenceMethod(
                            item.getChangePoint())))
                    .append("</p>\n<p><b>Artifacts:</b> ")
                    .append(escapeHtml(formatArtifact(
                            item.getOldArtifact())))
                    .append(" &rarr; ")
                    .append(escapeHtml(formatArtifact(
                            item.getNewArtifact())))
                    .append("</p>\n<p><b>Body hashes:</b> ")
                    .append(escapeHtml(String.valueOf(
                            item.getChangePoint().getOldHash())))
                    .append(" &rarr; ")
                    .append(escapeHtml(String.valueOf(
                            item.getChangePoint().getNewHash())))
                    .append("</p>\n<div class=\"method-body-grid\">\n");
            appendHtmlMethodSide(sb, "Old",
                    item.getOldMethod());
            appendHtmlMethodSide(sb, "New",
                    item.getNewMethod());
            sb.append("</div>\n</section>\n");
        }
    }

    /**
     * Appends one HTML evidence side.
     *
     * @param sb string builder
     * @param label side label
     * @param method decompiled method
     */
    private void appendHtmlMethodSide(
            final StringBuilder sb,
            final String label,
            final DecompiledMethod method) {
        sb.append("<div class=\"method-body-side\"><h4>")
                .append(label)
                .append("</h4>\n");
        if (method.isAvailable()) {
            sb.append("<pre><code>")
                    .append(escapeHtml(method.getSource()))
                    .append("</code></pre>\n");
        } else {
            sb.append("<p class=\"warn\">unavailable: ")
                    .append(escapeHtml(
                            method.getFailureReason()))
                    .append("</p>\n");
        }
        sb.append("</div>\n");
    }

    /**
     * Appends HTML diagnostics
     * section.
     *
     * @param sb     string builder
     * @param events diagnostic events
     */
    private void
            appendHtmlDiagnostics(
                    final StringBuilder sb,
                    final List<DiagnosticEvent>
                            events) {
        sb.append("<h2>Diagnostics")
                .append("</h2>\n");
        if (events.isEmpty()) {
            sb.append("<p>No diagnostic ")
                    .append("events.")
                    .append("</p>\n");
            return;
        }
        sb.append("<table>\n<tr>")
                .append("<th>Stage</th>")
                .append("<th>Level</th>")
                .append("<th>Message</th>")
                .append("<th>Elapsed(ms)")
                .append("</th>")
                .append("</tr>\n");
        for (DiagnosticEvent ev
                : events) {
            sb.append("<tr><td>")
                    .append(ev.getStage())
                    .append("</td><td>")
                    .append(ev.getLevel())
                    .append("</td><td>")
                    .append(ev.getMessage())
                    .append("</td><td>")
                    .append(ev.getElapsedMillis())
                    .append("</td></tr>\n");
        }
        sb.append("</table>\n");
    }

    /**
     * Generates Markdown report.
     *
     * @param changes dependency changes
     * @param points  change points
     * @param result  impact result
     * @param evidence method body evidence
     * @param events  diagnostic events
     * @return Markdown string
     */
    private String generateMarkdown(
            final List<DependencyChange>
                    changes,
            final List<ChangePoint>
                    points,
            final ImpactResult result,
            final List<MethodBodyEvidence>
                    evidence,
            final List<DiagnosticEvent>
                    events) {
        final StringBuilder sb =
                new StringBuilder();
        appendMdHeader(sb);
        appendMdSummary(sb, changes,
                points, result);
        appendMdDependencyChanges(sb,
                changes);
        appendMdChangePoints(sb, points);
        appendMdImpactPaths(sb, result,
                evidence);
        appendMdDiagnostics(sb, events);
        return sb.toString();
    }

    /**
     * Appends Markdown header.
     *
     * @param sb string builder
     */
    private void appendMdHeader(
            final StringBuilder sb) {
        sb.append("# Impact Analysis ")
                .append("Report\n\n")
                .append("*Generated: ")
                .append(LocalDateTime.now()
                        .format(DATE_FMT))
                .append("*\n\n");
    }

    /**
     * Appends Markdown summary.
     *
     * @param sb      string builder
     * @param changes dependency changes
     * @param points  change points
     * @param result  impact result
     */
    private void appendMdSummary(
            final StringBuilder sb,
            final List<DependencyChange>
                    changes,
            final List<ChangePoint>
                    points,
            final ImpactResult result) {
        sb.append("## Summary\n\n")
                .append("- Dependency ")
                .append("Changes: ")
                .append(changes.size())
                .append("\n")
                .append("- Change ")
                .append("Points: ")
                .append(points.size())
                .append("\n")
                .append("- Impact ")
                .append("Paths: ")
                .append(result.getPaths()
                        .size())
                .append("\n\n");
    }

    /**
     * Appends Markdown summary with links.
     *
     * @param sb      string builder
     * @param changes dependency changes
     * @param points  change points
     * @param result  impact result
     * @param depFile dependency sub-file
     * @param intFile internal sub-file
     * @param impFile impact sub-file
     */
    private void appendMdSummary(
            final StringBuilder sb,
            final List<DependencyChange>
                    changes,
            final List<ChangePoint>
                    points,
            final ImpactResult result,
            final String depFile,
            final String intFile,
            final String impFile) {
        sb.append("## Summary\n\n")
                .append("- Dependency ")
                .append("Changes: ")
                .append(changes.size())
                .append(" ([details](")
                .append(depFile)
                .append("))\n")
                .append("- Internal ")
                .append("Changes: ")
                .append(points.size())
                .append(" ([details](")
                .append(intFile)
                .append("))\n")
                .append("- Impact ")
                .append("Paths: ")
                .append(result.getPaths()
                        .size())
                .append(" ([details](")
                .append(impFile)
                .append("))\n\n");
    }

    /**
     * Appends Markdown dependency
     * changes grouped by module.
     *
     * @param sb      string builder
     * @param changes dependency changes
     */
    private void
            appendMdDependencyChanges(
                    final StringBuilder sb,
                    final List<DependencyChange>
                            changes) {
        sb.append("## Dependency ")
                .append("Changes\n\n");
        if (changes.isEmpty()) {
            sb.append("No dependency ")
                    .append("changes.\n\n");
            return;
        }
        final Map<String,
                List<DependencyChange>> grouped =
                groupChangesByModule(changes);
        for (Map.Entry<String,
                List<DependencyChange>> entry
                : grouped.entrySet()) {
            sb.append("### Module: ")
                    .append(entry.getKey())
                    .append("\n\n")
                    .append("| Type | ")
                    .append("Artifact | ")
                    .append("Old Version | ")
                    .append("Scope |\n")
                    .append("|------|")
                    .append("----------|")
                    .append("-------------|")
                    .append("-------|\n");
            for (DependencyChange ch
                    : entry.getValue()) {
                appendMdChangeRow(sb, ch);
            }
            sb.append("\n");
        }
    }

    /**
     * Appends Markdown change row.
     *
     * @param sb string builder
     * @param ch dependency change
     */
    private void appendMdChangeRow(
            final StringBuilder sb,
            final DependencyChange ch) {
        final ChangeType type =
                ch.getChangeType();
        final String typeLabel;
        final ArtifactCoord art;
        if (type == ChangeType.ADDED) {
            typeLabel = "ADDED";
            art = ch.getNewArtifact();
        } else if (type
                == ChangeType.REMOVED) {
            typeLabel = "REMOVED";
            art = ch.getOldArtifact();
        } else {
            typeLabel = "VERSION_CHANGED";
            art = ch.getNewArtifact();
        }
        final String oldVer;
        if (type == ChangeType
                .VERSION_CHANGED) {
            oldVer = ch.getOldArtifact()
                    .getVersion();
        } else {
            oldVer = "N/A";
        }
        sb.append("| ").append(typeLabel)
                .append(" | ")
                .append(formatArtifact(art))
                .append(" | ")
                .append(oldVer)
                .append(" | ")
                .append(ch.getScope());
        if (ch.isCompileTimeApiRisk()) {
            sb.append(" [API risk]");
        }
        sb.append(" |\n");
    }

    /**
     * Appends Markdown change points.
     *
     * @param sb     string builder
     * @param points change points
     */
    private void
            appendMdChangePoints(
                    final StringBuilder sb,
                    final List<ChangePoint>
                            points) {
        sb.append("## Internal ")
                .append("Changes\n\n");
        if (points.isEmpty()) {
            sb.append("No static ")
                    .append("confirmed ")
                    .append("impact.\n\n");
            return;
        }
        sb.append("| Artifact | Kind | ")
                .append("Owner | Name | ")
                .append("Descriptor |\n")
                .append("|----------|")
                .append("------|-------|")
                .append("------|------------|\n");
        for (ChangePoint cp : points) {
            sb.append("| ")
                    .append(formatArtifact(
                            cp.getArtifact()))
                    .append(" | ")
                    .append(cp.getKind())
                    .append(" | ")
                    .append(cp.getOwner())
                    .append(" | ")
                    .append(cp.getName())
                    .append(" | ")
                    .append(cp.getDescriptor())
                    .append(" |\n");
        }
        sb.append("\n");
    }

    /**
     * Appends Markdown impact paths.
     *
     * @param sb     string builder
     * @param result impact result
     * @param evidence method body evidence
     */
    private void appendMdImpactPaths(
            final StringBuilder sb,
            final ImpactResult result,
            final List<MethodBodyEvidence>
                    evidence) {
        sb.append("## Impact Paths\n\n");
        final List<ImpactPath> paths =
                result.getPaths();
        if (paths.isEmpty()) {
            sb.append("No static ")
                    .append("confirmed ")
                    .append("impact.\n\n");
            return;
        }
        final Map<ChangePoint, String> ids =
                evidenceIds(evidence);
        for (int i = 0; i < paths.size();
                i++) {
            appendMdImpactPath(sb,
                    paths.get(i), i + 1,
                    ids);
        }
        appendMdMethodBodyEvidence(sb,
                evidence, ids);
    }

    /**
     * Appends single Markdown impact path.
     *
     * @param sb   string builder
     * @param path impact path
     * @param num  path number
     * @param evidenceIds evidence identifiers
     */
    private void appendMdImpactPath(
            final StringBuilder sb,
            final ImpactPath path,
            final int num,
            final Map<ChangePoint, String>
                    evidenceIds) {
        sb.append("### Path #")
                .append(num).append("\n\n")
                .append("**Affected:** ")
                .append(formatMethod(
                        path.getAffectedMethod()))
                .append("\n\n");
        appendMdCallChain(sb, path);
        sb.append("**Change:** ")
                .append(path.getChangePoint()
                        .getKind())
                .append(" in ")
                .append(formatChangePointLocation(
                        path.getChangePoint()))
                .append("\n\n");
        final String evidenceId = evidenceIds.get(
                path.getChangePoint());
        if (evidenceId != null) {
            sb.append("**Method body evidence:** [")
                    .append(evidenceId)
                    .append("](#")
                    .append(evidenceId.toLowerCase())
                    .append(")\n\n");
        }
        if (!path.getModules().isEmpty()) {
            sb.append("**Modules:** ")
                    .append(path.getModules())
                    .append("\n\n");
        }
        if (!path.getCrossModuleBoundaries()
                .isEmpty()) {
            sb.append("**Cross-module:** ")
                    .append(path
                            .getCrossModuleBoundaries()
                            .size())
                    .append(" boundary edges")
                    .append("\n\n");
        }
    }

    /**
     * Appends Markdown method body evidence.
     *
     * @param sb string builder
     * @param evidence evidence list
     * @param ids stable evidence identifiers
     */
    private void appendMdMethodBodyEvidence(
            final StringBuilder sb,
            final List<MethodBodyEvidence> evidence,
            final Map<ChangePoint, String> ids) {
        if (ids.isEmpty()) {
            return;
        }
        sb.append("## Method Body Evidence\n\n")
                .append("Decompiler output is Java-like evidence; ")
                .append("it may differ from the original source.\n\n");
        for (MethodBodyEvidence item
                : uniqueEvidence(evidence)) {
            final ChangePoint point =
                    item.getChangePoint();
            sb.append("### ")
                    .append(ids.get(point))
                    .append("\n\n**Method:** `")
                    .append(formatEvidenceMethod(point))
                    .append("`\n\n**Artifacts:** `")
                    .append(formatArtifact(
                            item.getOldArtifact()))
                    .append("` → `")
                    .append(formatArtifact(
                            item.getNewArtifact()))
                    .append("`\n\n**Body hashes:** `")
                    .append(point.getOldHash())
                    .append("` → `")
                    .append(point.getNewHash())
                    .append("`\n\n");
            appendMdMethodSide(sb, "Old",
                    item.getOldMethod());
            appendMdMethodSide(sb, "New",
                    item.getNewMethod());
        }
    }

    /**
     * Appends one Markdown evidence side.
     *
     * @param sb string builder
     * @param label side label
     * @param method decompiled method
     */
    private void appendMdMethodSide(
            final StringBuilder sb,
            final String label,
            final DecompiledMethod method) {
        sb.append("#### ")
                .append(label)
                .append("\n\n");
        if (method.isAvailable()) {
            sb.append("```java\n")
                    .append(method.getSource())
                    .append("\n```\n\n");
        } else {
            sb.append("_unavailable: ")
                    .append(method.getFailureReason())
                    .append("_\n\n");
        }
    }

    /**
     * Appends Markdown diagnostics.
     *
     * @param sb     string builder
     * @param events diagnostic events
     */
    private void
            appendMdDiagnostics(
                    final StringBuilder sb,
                    final List<DiagnosticEvent>
                            events) {
        sb.append("## Diagnostics\n\n");
        if (events.isEmpty()) {
            sb.append("No diagnostic ")
                    .append("events.\n");
            return;
        }
        sb.append("| Stage | Level | ")
                .append("Message | ")
                .append("Elapsed(ms) |\n")
                .append("|-------|")
                .append("-------|")
                .append("---------|")
                .append("------------|\n");
        for (DiagnosticEvent ev
                : events) {
            sb.append("| ")
                    .append(ev.getStage())
                    .append(" | ")
                    .append(ev.getLevel())
                    .append(" | ")
                    .append(ev.getMessage())
                    .append(" | ")
                    .append(ev.getElapsedMillis())
                    .append(" |\n");
        }
    }

    /**
     * Formats change point location
     * as owner or owner#name.
     *
     * @param cp change point
     * @return location string
     */
    private String
            formatChangePointLocation(
                    final ChangePoint cp) {
        if (cp.getName() != null) {
            return cp.getOwner() + "#"
                    + cp.getName();
        }
        return cp.getOwner();
    }

    /**
     * Builds ordered call chain
     * node labels from an impact
     * path.
     *
     * @param path impact path
     * @return list of node labels
     */
    private List<String>
            buildCallChainNodes(
                    final ImpactPath path) {
        final List<String> nodes =
                new ArrayList<>();
        nodes.add(formatMethod(
                path.getAffectedMethod()));
        for (CallEdge edge
                : path.getEdges()) {
            nodes.add(formatMethod(
                    edge.getCallee()));
        }
        nodes.add(
                formatChangePointLocation(
                        path.getChangePoint()));
        return nodes;
    }

    /**
     * Appends HTML call chain
     * ordered list for an impact
     * path.
     *
     * @param sb   string builder
     * @param path impact path
     */
    private void appendHtmlCallChain(
            final StringBuilder sb,
            final ImpactPath path) {
        final List<String> nodes =
                buildCallChainNodes(path);
        sb.append("<p><b>Call ")
                .append("Chain:</b></p>\n")
                .append("<ol>\n");
        for (String node : nodes) {
            sb.append("  <li>")
                    .append(node)
                    .append("</li>\n");
        }
        sb.append("</ol>\n");
    }

    /**
     * Appends Markdown call chain
     * arrow chain for an impact
     * path.
     *
     * @param sb   string builder
     * @param path impact path
     */
    private void appendMdCallChain(
            final StringBuilder sb,
            final ImpactPath path) {
        final List<String> nodes =
                buildCallChainNodes(path);
        sb.append("**Call Chain:**")
                .append("\n\n");
        for (int i = 0; i < nodes.size();
                i++) {
            if (i > 0) {
                sb.append(" \u2192 ");
            }
            sb.append(nodes.get(i));
        }
        sb.append("\n\n");
    }

    /**
     * Formats artifact coordinate.
     *
     * @param art artifact or null
     * @return formatted string
     */
    private String formatArtifact(
            final ArtifactCoord art) {
        if (art == null) {
            return "N/A";
        }
        return art.getGroupId() + ":"
                + art.getArtifactId() + ":"
                + art.getVersion();
    }

    /**
     * Builds stable evidence IDs from sorted unique change points.
     *
     * @param evidence evidence list
     * @return change point to ID map
     */
    private Map<ChangePoint, String> evidenceIds(
            final List<MethodBodyEvidence> evidence) {
        final Map<ChangePoint, String> result =
                new LinkedHashMap<>();
        int index = 1;
        for (MethodBodyEvidence item
                : uniqueEvidence(evidence)) {
            result.put(item.getChangePoint(),
                    String.format("MB-%03d", index));
            index++;
        }
        return result;
    }

    /**
     * Returns evidence sorted by method identity and deduplicated.
     *
     * @param evidence evidence list
     * @return ordered unique evidence
     */
    private List<MethodBodyEvidence> uniqueEvidence(
            final List<MethodBodyEvidence> evidence) {
        final List<MethodBodyEvidence> ordered =
                new ArrayList<>(evidence);
        ordered.sort(Comparator
                .comparing((MethodBodyEvidence item)
                        -> item.getChangePoint()
                        .getArtifact().toString())
                .thenComparing(item -> item.getChangePoint()
                        .getOwner())
                .thenComparing(item -> item.getChangePoint()
                        .getName(),
                        Comparator.nullsFirst(
                                Comparator.naturalOrder()))
                .thenComparing(item -> item.getChangePoint()
                        .getDescriptor(),
                        Comparator.nullsFirst(
                                Comparator.naturalOrder())));
        final Map<ChangePoint, MethodBodyEvidence> unique =
                new LinkedHashMap<>();
        for (MethodBodyEvidence item : ordered) {
            unique.putIfAbsent(item.getChangePoint(), item);
        }
        return new ArrayList<>(unique.values());
    }

    /**
     * Formats exact JVM method identity.
     *
     * @param point method change point
     * @return readable method identity
     */
    private String formatEvidenceMethod(
            final ChangePoint point) {
        return point.getOwner().replace('/', '.')
                + "." + point.getName()
                + point.getDescriptor();
    }

    /**
     * Formats method identifier.
     *
     * @param method method id
     * @return formatted string
     */
    private String formatMethod(
            final io.github.dependencyanalysis
                    .callgraph
                    .MethodId method) {
        return method.owner() + "."
                + method.name()
                + method.descriptor();
    }

    /**
     * Resolves sub-file path.
     *
     * @param output base output path
     * @param suffix suffix to insert
     * @return resolved sub-file path
     */
    private Path resolveSubPath(
            final Path output,
            final String suffix) {
        final Path parent =
                output.getParent();
        final String name =
                output.getFileName()
                        .toString();
        final int dot = name.lastIndexOf('.');
        final String stem;
        final String ext;
        if (dot > 0) {
            stem = name.substring(0, dot);
            ext = name.substring(dot);
        } else {
            stem = name;
            ext = "";
        }
        final String subName =
                stem + suffix + ext;
        if (parent != null) {
            return parent.resolve(subName);
        }
        return Path.of(subName);
    }

    /**
     * Wraps body in a complete HTML page.
     *
     * @param title page title
     * @param body  body content
     * @return HTML string
     */
    private String generateHtmlPage(
            final String title,
            final String body) {
        return "<!DOCTYPE html>\n"
                + "<html>\n<head>\n"
                + "<meta charset=\"UTF-8\">\n"
                + "<title>" + title
                + "</title>\n"
                + "<style>" + CSS
                + "</style>\n"
                + "</head>\n<body>\n"
                + body
                + "</body>\n</html>\n";
    }

    /**
     * Wraps body in a Markdown page.
     *
     * @param title page title
     * @param body  body content
     * @return Markdown string
     */
    private String generateMdPage(
            final String title,
            final String body) {
        return "# " + title + "\n\n" + body;
    }

    /**
     * Builds dependency changes body.
     *
     * @param changes dependency changes
     * @return body string
     */
    private String depBody(
            final List<DependencyChange>
                    changes) {
        final StringBuilder sb =
                new StringBuilder();
        appendHtmlDependencyChanges(sb,
                changes);
        return sb.toString();
    }

    /**
     * Builds internal changes body.
     *
     * @param points change points
     * @return body string
     */
    private String intBody(
            final List<ChangePoint>
                    points) {
        final StringBuilder sb =
                new StringBuilder();
        appendHtmlChangePoints(sb,
                points);
        return sb.toString();
    }

    /**
     * Builds impact paths body.
     *
     * @param result impact result
     * @param evidence method body evidence
     * @return body string
     */
    private String impBody(
            final ImpactResult result,
            final List<MethodBodyEvidence>
                    evidence) {
        final StringBuilder sb =
                new StringBuilder();
        appendHtmlImpactPaths(sb, result,
                evidence);
        return sb.toString();
    }

    /**
     * Builds MD dependency body.
     *
     * @param changes dependency changes
     * @return body string
     */
    private String depBodyMd(
            final List<DependencyChange>
                    changes) {
        final StringBuilder sb =
                new StringBuilder();
        appendMdDependencyChanges(sb,
                changes);
        return sb.toString();
    }

    /**
     * Builds MD internal changes body.
     *
     * @param points change points
     * @return body string
     */
    private String intBodyMd(
            final List<ChangePoint>
                    points) {
        final StringBuilder sb =
                new StringBuilder();
        appendMdChangePoints(sb, points);
        return sb.toString();
    }

    /**
     * Builds MD impact paths body.
     *
     * @param result impact result
     * @param evidence method body evidence
     * @return body string
     */
    private String impBodyMd(
            final ImpactResult result,
            final List<MethodBodyEvidence>
                    evidence) {
        final StringBuilder sb =
                new StringBuilder();
        appendMdImpactPaths(sb, result,
                evidence);
        return sb.toString();
    }

    /**
     * Writes HTML sub-file.
     *
     * @param path  sub-file path
     * @param title page title
     * @param body  body content
     * @throws IOException on write error
     */
    private void writeHtmlSub(
            final Path path,
            final String title,
            final String body)
            throws IOException {
        Files.writeString(path,
                generateHtmlPage(title, body));
    }

    /**
     * Writes Markdown sub-file.
     *
     * @param path  sub-file path
     * @param title page title
     * @param body  body content
     * @throws IOException on write error
     */
    private void writeMdSub(
            final Path path,
            final String title,
            final String body)
            throws IOException {
        Files.writeString(path,
                generateMdPage(title, body));
    }

    /**
     * Writes HTML index file.
     *
     * @param output  output path
     * @param changes dependency changes
     * @param points  change points
     * @param result  impact result
     * @param events  diagnostic events
     * @throws IOException on write error
     */
    private void writeIndexHtml(
            final Path output,
            final List<DependencyChange>
                    changes,
            final List<ChangePoint>
                    points,
            final ImpactResult result,
            final List<DiagnosticEvent>
                    events)
            throws IOException {
        final String depName =
                resolveSubPath(output,
                        "-dependencies")
                        .getFileName()
                        .toString();
        final String intName =
                resolveSubPath(output,
                        "-internal-changes")
                        .getFileName()
                        .toString();
        final String impName =
                resolveSubPath(output,
                        "-impact-paths")
                        .getFileName()
                        .toString();
        final StringBuilder sb =
                new StringBuilder();
        sb.append("<!DOCTYPE html>\n")
                .append("<html>\n<head>\n")
                .append("<meta charset=")
                .append("\"UTF-8\">\n")
                .append("<title>Impact ")
                .append("Analysis ")
                .append("Report</title>\n")
                .append("<style>")
                .append(CSS)
                .append("</style>\n")
                .append("</head>\n<body>\n");
        appendHtmlHeader(sb);
        appendHtmlSummary(sb, changes,
                points, result,
                depName, intName, impName);
        appendHtmlDiagnostics(sb, events);
        sb.append("</body>\n</html>\n");
        Files.writeString(output,
                sb.toString());
    }

    /**
     * Writes Markdown index file.
     *
     * @param output  output path
     * @param changes dependency changes
     * @param points  change points
     * @param result  impact result
     * @param events  diagnostic events
     * @throws IOException on write error
     */
    private void writeIndexMd(
            final Path output,
            final List<DependencyChange>
                    changes,
            final List<ChangePoint>
                    points,
            final ImpactResult result,
            final List<DiagnosticEvent>
                    events)
            throws IOException {
        final String depName =
                resolveSubPath(output,
                        "-dependencies")
                        .getFileName()
                        .toString();
        final String intName =
                resolveSubPath(output,
                        "-internal-changes")
                        .getFileName()
                        .toString();
        final String impName =
                resolveSubPath(output,
                        "-impact-paths")
                        .getFileName()
                        .toString();
        final StringBuilder sb =
                new StringBuilder();
        appendMdHeader(sb);
        appendMdSummary(sb, changes,
                points, result,
                depName, intName, impName);
        appendMdDiagnostics(sb, events);
        Files.writeString(output,
                sb.toString());
    }

}
