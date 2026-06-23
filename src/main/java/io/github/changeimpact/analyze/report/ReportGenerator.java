package io.github.changeimpact.analyze.report;

import io.github.changeimpact.analyze.bytecode.ChangePoint;
import io.github.changeimpact.analyze.callgraph.CallEdge;
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
                        impBodyMd(result));
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
                        impBody(result));
            }
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
                .append("\n\n");
        appendMdCallChain(sb, path);
        sb.append("**Change:** ")
                .append(path.getChangePoint()
                        .getKind())
                .append(" in ")
                .append(formatChangePointLocation(
                        path.getChangePoint()))
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
     * @return body string
     */
    private String impBody(
            final ImpactResult result) {
        final StringBuilder sb =
                new StringBuilder();
        appendHtmlImpactPaths(sb, result);
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
     * @return body string
     */
    private String impBodyMd(
            final ImpactResult result) {
        final StringBuilder sb =
                new StringBuilder();
        appendMdImpactPaths(sb, result);
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
