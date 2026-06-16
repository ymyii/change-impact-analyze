package io.github.changeimpact.analyze.report;

import io.github.changeimpact.analyze.bytecode.ChangePoint;
import io.github.changeimpact.analyze.cli.OutputFormat;
import io.github.changeimpact.analyze.dependency.ArtifactCoord;
import io.github.changeimpact.analyze.dependency.ChangeType;
import io.github.changeimpact.analyze.dependency.DependencyChange;
import io.github.changeimpact.analyze.diagnostic.DiagnosticEvent;
import io.github.changeimpact.analyze.impact.ImpactPath;
import io.github.changeimpact.analyze.impact.ImpactResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

// Wiki: wiki/features/report-generator.md - 报告生成核心类，HTML/Markdown 双格式输出
/**
 * Generates single-file HTML or
 * Markdown impact analysis reports.
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
            + ".info{color:#666;}";

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
        Objects.requireNonNull(changes,
                "changes");
        Objects.requireNonNull(points,
                "points");
        Objects.requireNonNull(result,
                "result");
        Objects.requireNonNull(events,
                "events");
        Objects.requireNonNull(format,
                "format");
        Objects.requireNonNull(output,
                "output");
        final String content =
                generateToString(
                        changes, points,
                        result, events,
                        format);
        try {
            Files.writeString(output,
                    content);
        } catch (IOException e) {
            throw new ReportException(
                    "Failed to write: "
                    + output, e);
        }
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
        Objects.requireNonNull(changes,
                "changes");
        Objects.requireNonNull(points,
                "points");
        Objects.requireNonNull(result,
                "result");
        Objects.requireNonNull(events,
                "events");
        Objects.requireNonNull(format,
                "format");
        if (format == OutputFormat.MD) {
            return generateMarkdown(
                    changes, points,
                    result, events);
        }
        return generateHtml(
                changes, points,
                result, events);
    }

    /**
     * Generates HTML report content.
     *
     * @param changes dependency changes
     * @param points  change points
     * @param result  impact result
     * @param events  diagnostic events
     * @return HTML string
     */
    private String generateHtml(
            final List<DependencyChange>
                    changes,
            final List<ChangePoint>
                    points,
            final ImpactResult result,
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
        appendHtmlImpactPaths(sb, result);
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
        sb.append("<tr class=\"")
                .append(cssClass)
                .append("\"><td>")
                .append(typeLabel)
                .append("</td><td>")
                .append(formatArtifact(art))
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
     */
    private void appendHtmlImpactPaths(
            final StringBuilder sb,
            final ImpactResult result) {
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
        for (int i = 0; i < paths.size();
                i++) {
            appendHtmlImpactPath(sb,
                    paths.get(i), i + 1);
        }
    }

    /**
     * Appends single HTML impact path.
     *
     * @param sb   string builder
     * @param path impact path
     * @param num  path number
     */
    private void appendHtmlImpactPath(
            final StringBuilder sb,
            final ImpactPath path,
            final int num) {
        sb.append("<h3>Path #")
                .append(num)
                .append("</h3>\n")
                .append("<p><b>Affected:")
                .append("</b> ")
                .append(formatMethod(
                        path.getAffectedMethod()))
                .append("</p>\n")
                .append("<p><b>Change:")
                .append("</b> ")
                .append(path.getChangePoint()
                        .getKind())
                .append(" in ")
                .append(path.getChangePoint()
                        .getOwner())
                .append("</p>\n");
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
     * @param events  diagnostic events
     * @return Markdown string
     */
    private String generateMarkdown(
            final List<DependencyChange>
                    changes,
            final List<ChangePoint>
                    points,
            final ImpactResult result,
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
        appendMdImpactPaths(sb, result);
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
                    .append("Scope |\n")
                    .append("|------|")
                    .append("----------|")
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
        sb.append("| ").append(typeLabel)
                .append(" | ")
                .append(formatArtifact(art))
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
     */
    private void appendMdImpactPaths(
            final StringBuilder sb,
            final ImpactResult result) {
        sb.append("## Impact Paths\n\n");
        final List<ImpactPath> paths =
                result.getPaths();
        if (paths.isEmpty()) {
            sb.append("No static ")
                    .append("confirmed ")
                    .append("impact.\n\n");
            return;
        }
        for (int i = 0; i < paths.size();
                i++) {
            appendMdImpactPath(sb,
                    paths.get(i), i + 1);
        }
    }

    /**
     * Appends single Markdown impact path.
     *
     * @param sb   string builder
     * @param path impact path
     * @param num  path number
     */
    private void appendMdImpactPath(
            final StringBuilder sb,
            final ImpactPath path,
            final int num) {
        sb.append("### Path #")
                .append(num).append("\n\n")
                .append("**Affected:** ")
                .append(formatMethod(
                        path.getAffectedMethod()))
                .append("\n\n")
                .append("**Change:** ")
                .append(path.getChangePoint()
                        .getKind())
                .append(" in ")
                .append(path.getChangePoint()
                        .getOwner())
                .append("\n\n");
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
     * Formats method identifier.
     *
     * @param method method id
     * @return formatted string
     */
    private String formatMethod(
            final io.github.changeimpact
                    .analyze.callgraph
                    .MethodId method) {
        return method.owner() + "."
                + method.name()
                + method.descriptor();
    }
}
