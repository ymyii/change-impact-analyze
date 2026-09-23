package io.github.dependencyanalysis.tree;

import com.fasterxml.jackson.core.JsonGenerator;

import io.github.dependencyanalysis.classpath.ClassConflictRisk;
import io.github.dependencyanalysis.preflight
        .PreflightResult;
import io.github.dependencyanalysis.preflight
        .PreflightReport;
import io.github.dependencyanalysis.runtime.ReportCache;
import io.github.dependencyanalysis.report.ScriptSafeJson;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Renders safe, offline repository dependency reports. */
public final class TreeReportRenderer {

    /** Bytes used in stable short hashes. */
    private static final int SHORT_HASH_BYTES = 6;

    /** Local report stylesheet. */
    private static final String CSS = """
            :root{color-scheme:light;--bg:#f6f7fb;--card:#fff;
            --line:#dfe3eb;--text:#172033;--muted:#657089;
            --ok:#157347;--warn:#9a6700;--fail:#b42318}
            *{box-sizing:border-box}body{margin:0;background:var(--bg);
            color:var(--text);font:14px/1.55 system-ui,sans-serif}
            main{width:100%;margin:0 auto;padding:clamp(14px,2vw,28px)}
            h1{margin:0 0 8px}
            h2{margin-top:28px}.muted{color:var(--muted)}
            .card{background:var(--card);border:1px solid var(--line);
            border-radius:10px;padding:18px;margin:14px 0;overflow-x:auto;
            min-width:0;max-width:100%}
            table{border-collapse:collapse;width:100%;background:var(--card)}
            th,td{border:1px solid var(--line);padding:8px;vertical-align:top}
            th{background:#eef1f6;text-align:left}
            .SUCCESS,.PASS{color:var(--ok)}
            .DEGRADED,.WARN,.COMPLETED_WITH_ISSUES{color:var(--warn)}
            .FAILED,.FAIL,.SKIPPED{color:var(--fail)}
            code{overflow-wrap:anywhere}
            pre.dependency-tree{margin:10px 0 0;padding:14px;overflow:auto;
            border:1px solid var(--line);border-radius:6px;background:#f8f9fc;
            font:13px/1.55 ui-monospace,SFMono-Regular,Consolas,monospace}
            .controls{display:flex;gap:8px;align-items:center;
            flex-wrap:wrap;margin:12px 0}
            input,select,button{padding:7px;border:1px solid var(--line);
            border-radius:6px;background:var(--card);color:var(--text)}
            button{cursor:pointer}button:disabled{cursor:default;opacity:.5}
            .sortable{border:0;background:transparent;padding:0;
            font-weight:600}.pager{margin-left:auto}
            .badge{border:1px solid currentColor;
            border-radius:999px;padding:2px 7px}
            .tab-list{display:flex;gap:6px;overflow:auto;margin:14px 0}
            [role=tab]{white-space:nowrap}[role=tab][aria-selected=true]{
            border-color:#175cd3;color:#175cd3;font-weight:600}
            [role=tabpanel]{margin-top:0}[hidden],.hidden{display:none}
            .class-code-panel{padding:12px;background:#f8f9fc}
            .class-code-panel pre{max-height:70vh;overflow:auto;white-space:pre;
            border:1px solid var(--line);padding:12px;background:#fff;
            font:13px/1.5 ui-monospace,SFMono-Regular,Consolas,monospace}
            .source-switches{display:flex;gap:6px;flex-wrap:wrap}
            .source-switches button[aria-pressed=true]{background:#175cd3;
            border-color:#175cd3;color:#fff;font-weight:700}
            .source-switches button:focus-visible{outline:3px solid #84adff;
            outline-offset:2px}
            .table-scroll{overflow-x:auto;border:1px solid var(--line);
            border-radius:8px;min-width:0;max-width:100%}
            .table-scroll table{margin:0;min-width:1180px}
            .report-control{display:grid;gap:4px;min-width:150px;
            max-width:100%}
            .report-control.grow{flex:1 1 240px}.report-control input,
            .report-control select{width:100%}
            #dependency-analysis{overflow:visible}
            .dependency-filter-form{display:grid;gap:12px;margin:12px 0;
            min-width:0;max-width:100%}
            .dependency-filter-primary{display:grid;gap:12px;
            grid-template-columns:minmax(180px,280px)
            repeat(2,minmax(260px,420px));align-items:start;
            justify-content:start;min-width:0}
            .dependency-filter-primary>.report-control{min-width:0}
            .dependency-filter-secondary{display:flex;gap:8px;
            align-items:end;flex-wrap:wrap;min-width:0}
            .dependency-filter-secondary>.report-control{flex:0 1 160px}
            .dependency-filter-buttons{display:flex;gap:8px;
            align-items:center;align-self:end}
            .combobox{position:relative;min-width:0;max-width:100%}
            .combobox input{width:100%;padding-right:42px}
            .combobox-toggle{position:absolute;z-index:2;top:1px;right:1px;
            bottom:1px;width:36px;padding:0;border:0;
            border-left:1px solid var(--line);border-radius:0 5px 5px 0;
            background:transparent;display:flex;align-items:center;
            justify-content:center;color:var(--muted)}
            .combobox-toggle::before{content:"";width:8px;height:8px;
            border-right:2px solid currentColor;
            border-bottom:2px solid currentColor;
            transform:translateY(-2px) rotate(45deg);
            transition:transform .15s ease}
            .combobox-toggle[aria-expanded=true]::before{
            transform:translateY(2px) rotate(225deg)}
            .combobox-options{position:absolute;
            z-index:10;top:calc(100% + 4px);left:0;right:0;max-height:280px;
            overflow:auto;list-style:none;margin:0;padding:4px;background:#fff;
            border:1px solid var(--line);border-radius:8px;box-shadow:0 8px 24px
            #1018281f}.combobox-options li{display:flex;align-items:center;
            justify-content:space-between;gap:8px;padding:7px;border-radius:6px;
            cursor:pointer;overflow-wrap:anywhere}.combobox-options li[aria-selected=true]{
            background:#eef4ff;color:#175cd3}.combobox-option-label{min-width:0;
            overflow-wrap:anywhere}.version-counts{display:inline-flex;gap:5px;
            align-items:center;flex:none}.version-count{display:inline-flex;
            align-items:center;justify-content:center;min-width:22px;height:22px;
            padding:0 5px;border-radius:999px;background:#b42318;color:#fff;
            font-size:11px;font-weight:700;flex:none}.unique-version-count{
            display:inline-flex;align-items:center;justify-content:center;
            min-height:22px;padding:1px 7px;border:1px solid #98a2b3;
            border-radius:999px;background:#f2f4f7;color:#344054;
            font-size:11px;font-weight:600;white-space:nowrap}.candidate-status{
            margin:4px 0 0}.resolution-cell{min-width:230px}.resolution-source{
            display:inline-flex;padding:2px 7px;border-radius:999px;
            background:#eef4ff;color:#175cd3;font-weight:600}
            .resolution-detail{display:block;margin-top:5px;color:var(--muted);
            overflow-wrap:anywhere}.module-dependency-filter-primary{
            grid-template-columns:minmax(180px,280px) minmax(260px,420px)}
            .module-analysis-card{overflow:visible}
            .module-selector-control{width:min(720px,100%);min-width:0}
            .module-panel{margin-top:14px;min-width:0;max-width:100%}
            .module-panel>.card{overflow-x:auto}
            .module-panel>.card[data-internal-component]{overflow:visible}
            .error{color:var(--fail)}
            button:focus-visible,input:focus-visible,select:focus-visible,
            a:focus-visible{outline:3px solid #84adff;outline-offset:2px}
            @media(max-width:1100px){.dependency-filter-primary{
            grid-template-columns:repeat(2,minmax(0,1fr));max-width:860px}
            .dependency-search-control{grid-column:1/-1;max-width:420px}}
            @media(max-width:700px){main{padding:12px}.card{padding:12px}
            .dependency-filter-primary{grid-template-columns:minmax(0,1fr)}
            .dependency-search-control{grid-column:auto;max-width:none}
            .dependency-filter-secondary{display:grid;
            grid-template-columns:minmax(0,1fr)}
            .dependency-filter-secondary>.report-control{min-width:0}
            .pager{margin-left:0;width:100%}.combobox-options{position:fixed;
            left:12px;right:12px;top:auto;max-height:42vh}}
            a{color:#175cd3}
            """;

    /** Shared local report behavior used by Impact and Tree. */
    private static final String REPORT_COMMON_JAVASCRIPT = resource(
            "/io/github/dependencyanalysis/report/report-common.js");

    /** Tree Reactor dynamic report behavior. */
    private static final String TREE_JAVASCRIPT = resource(
            "/io/github/dependencyanalysis/tree/tree-report.js");

    /** File move operation, injectable for rollback tests. */
    private final MoveOperation moveOperation;

    /** Creates a renderer using filesystem atomic moves. */
    public TreeReportRenderer() {
        this(TreeReportRenderer::moveFile);
    }

    /**
     * Creates a renderer with a controlled move operation.
     *
     * @param operation file move operation
     */
    TreeReportRenderer(final MoveOperation operation) {
        moveOperation = operation;
    }

    /**
     * Renders and safely replaces tool-owned output.
     *
     * @param result repository result
     * @param output output directory
     * @throws IOException on write failure
     */
    public void render(
            final TreeRepositoryResult result,
            final Path output)
            throws IOException {
        final TreeReportSession session = start(
                TreeReportMetadata.from(result),
                result.getReactors().size(), output);
        for (ReactorTreeResult reactor
                : result.getReactors()) {
            session.publish(reactor);
        }
        session.complete();
    }

    /**
     * Starts a new incremental report and writes RUNNING 0/N.
     *
     * @param metadata run metadata
     * @param totalReactors expected reactor count
     * @param output output directory
     * @return initialized report session
     * @throws IOException on write failure
     */
    public TreeReportSession start(
            final TreeReportMetadata metadata,
            final int totalReactors,
            final Path output)
            throws IOException {
        if (totalReactors < 0) {
            throw new IllegalArgumentException(
                    "Reactor count must not be negative");
        }
        final Path normalized = output.toAbsolutePath()
                .normalize();
        initialize(metadata, normalized,
                totalReactors);
        return new TreeReportSession(this, metadata,
                normalized, totalReactors, null);
    }

    /**
     * Starts a production session with cache-backed conflict grouping.
     *
     * @param metadata run metadata
     * @param totalReactors expected reactor count
     * @param output output directory
     * @param cache command-owned report cache
     * @return initialized report session
     * @throws IOException on write failure
     */
    public TreeReportSession start(
            final TreeReportMetadata metadata,
            final int totalReactors,
            final Path output,
            final ReportCache cache) throws IOException {
        if (totalReactors < 0) {
            throw new IllegalArgumentException(
                    "Reactor count must not be negative");
        }
        final Path normalized = output.toAbsolutePath().normalize();
        initialize(metadata, normalized, totalReactors);
        return new TreeReportSession(this, metadata, normalized,
                totalReactors, cache.root());
    }

    private void initialize(
            final TreeReportMetadata metadata,
            final Path output,
            final int totalReactors)
            throws IOException {
        Files.createDirectories(output);
        final Path staging = Files.createTempDirectory(
                output, ".dependency-analyzer-staging-");
        try {
            final Path reportRoot = staging.resolve(
                    "dependency-report");
            final Path reactors = reportRoot.resolve(
                    "reactors");
            final Path assets = reportRoot.resolve(
                    "assets");
            Files.createDirectories(reactors);
            Files.createDirectories(assets);
            Files.writeString(assets.resolve("report.css"),
                    CSS, StandardCharsets.UTF_8);
            Files.writeString(assets.resolve("report.js"),
                    TREE_JAVASCRIPT, StandardCharsets.UTF_8);
            Files.writeString(assets.resolve("report-common.js"),
                    REPORT_COMMON_JAVASCRIPT, StandardCharsets.UTF_8);
            writeIndex(staging.resolve("index.html"), metadata, List.of(),
                    totalReactors, TreeReportState.RUNNING, "", false);
            replaceOwnedOutputs(
                    staging.resolve("index.html"),
                    reportRoot,
                    output.resolve("index.html"),
                    output.resolve("dependency-report"));
        } finally {
            deleteTree(staging);
        }
    }

    ReactorReportSummary publishReactor(
            final TreeReportMetadata metadata,
            final Path output,
            final ReactorTreeResult result,
            final List<ReactorReportSummary> summaries,
            final int totalReactors,
            final Path groupingCache)
            throws IOException {
        final String filename = filename(
                result.getReactor().getId());
        final Path page = output.resolve(
                "dependency-report/reactors")
                .resolve(filename);
        final TreeReportDataWriter.TreeReportData data =
                writeReactor(page, result, groupingCache);
        final ReactorReportSummary summary =
                ReactorReportSummary.from(filename,
                        result, data.multiVersionDependencyCount());
        final List<ReactorReportSummary> checkpoint =
                new ArrayList<>(summaries);
        checkpoint.add(summary);
        publishIndex(metadata, output, checkpoint,
                totalReactors, TreeReportState.RUNNING, "");
        return summary;
    }

    void publishIndex(
            final TreeReportMetadata metadata,
            final Path output,
            final List<ReactorReportSummary> summaries,
            final int totalReactors,
            final TreeReportState state,
            final String failureReason)
            throws IOException {
        writeIndex(output.resolve("index.html"), metadata, summaries,
                totalReactors, state, failureReason, true);
    }

    private void writeIndex(
            final Path target,
            final TreeReportMetadata metadata,
            final List<ReactorReportSummary> summaries,
            final int totalReactors,
            final TreeReportState state,
            final String failureReason,
            final boolean atomic) throws IOException {
        final PageBody content = body -> {
            body.append("<h1>Dependency Analyzer</h1>")
                .append("<p class=\"muted\">Repository ")
                .append("dependency tree report · ")
                .append(escape(OffsetDateTime.now()
                        .toString())).append("</p>")
                .append(indexMetadata(metadata))
                .append(indexSummary(state, summaries,
                        totalReactors, failureReason))
                .append(preflightTable(
                        checkpointPreflight(metadata), null))
                .append("<h2>Reactors</h2>")
                .append("<table><tr><th>path</th>")
                .append("<th>coordinate</th>")
                .append("<th>status</th><th>module</th>")
                .append("<th>dependency</th>")
                .append("<th>internal conflicts</th>")
                .append("<th>multi-version dependencies</th>")
                .append("<th>class conflicts</th>")
                .append("<th>high-risk class conflicts</th>")
                .append("<th>reason</th></tr>");
        for (ReactorReportSummary summary : summaries) {
            body.append("<tr><td><a href=\"")
                    .append("dependency-report/reactors/")
                    .append(escape(summary.getFilename()))
                    .append("\">")
                    .append(escape(summary.getId()))
                    .append("</a></td><td>")
                    .append(escape(summary.getCoordinate()))
                    .append("</td><td class=\"")
                    .append(summary.getStatus())
                    .append("\">")
                    .append(summary.getStatus())
                    .append("</td><td>")
                    .append(summary.getModuleCount())
                    .append("</td><td>")
                    .append(summary.getDependencyCount())
                    .append("</td><td>")
                    .append(summary.getInternalConflictCount())
                    .append("</td><td>")
                    .append(summary.getMultiVersionDependencyCount())
                    .append("</td><td>")
                    .append(summary.getClassConflictCount())
                    .append("</td><td>")
                    .append(summary.getHighRiskClassConflictCount())
                    .append("</td><td>")
                    .append(escape(summary.getReason()))
                    .append("</td></tr>");
        }
        body.append("</table>");
        };
        if (atomic) {
            writePageAtomically(target, "Dependency Analyzer",
                    "dependency-report/assets/", content);
        } else {
            writePage(target, "Dependency Analyzer",
                    "dependency-report/assets/", content);
        }
    }

    private String indexMetadata(
            final TreeReportMetadata metadata) {
        final HtmlSink value = HtmlSink.memory();
        value.append("<section class=\"card\"><h2>Metadata</h2>")
                .append("<table><thead><tr><th>Field</th>")
                .append("<th>Value</th></tr></thead><tbody>")
                .append(metadataRow("repository", metadata
                        .getSnapshot().getRepositoryRoot().toString()))
                .append(metadataRow("requested path", metadata
                        .getRequestedPath().toString()))
                .append(metadataRow("analysis path", displayPath(
                        metadata.getAnalysisPath())))
                .append(metadataRow("ref", metadata.getSnapshot()
                        .getRef()))
                .append(metadataRow("commit", metadata.getSnapshot()
                        .getCommit()))
                .append(metadataRow("branch", metadata.getSnapshot()
                        .getBranch()))
                .append(metadataRow("dirty", Boolean.toString(
                        metadata.getSnapshot().isDirty())))
                .append(metadataRow("Maven source", metadata
                        .getRuntime().getSource().toString()))
                .append(metadataRow("Maven executable", metadata
                        .getRuntime().getExecutable().toString()))
                .append(metadataRow("Maven version", String.valueOf(
                        metadata.getRuntime().getVersion())))
                .append(metadataRow("Java home", String.valueOf(
                        metadata.getRuntime().getJavaHome())))
                .append(metadataRow("scope", metadata.getScopes()
                        .toString()))
                .append(metadataRow("Maven arguments", metadata
                        .getMavenArguments().toString()))
                .append("</tbody></table></section>");
        return value.toString();
    }

    private String indexSummary(
            final TreeReportState state,
            final List<ReactorReportSummary> summaries,
            final long total,
            final String failureReason) {
        final long modules = summaries.stream().mapToLong(
                ReactorReportSummary::getModuleCount).sum();
        final long dependencies = summaries.stream().mapToLong(
                ReactorReportSummary::getDependencyCount).sum();
        final long internalConflicts = summaries.stream().mapToLong(
                ReactorReportSummary::getInternalConflictCount).sum();
        final long multiVersionDependencies = summaries.stream().mapToLong(
                ReactorReportSummary::getMultiVersionDependencyCount).sum();
        final long classConflicts = summaries.stream().mapToLong(
                ReactorReportSummary::getClassConflictCount).sum();
        final long highRiskClassConflicts = summaries.stream().mapToLong(
                ReactorReportSummary::getHighRiskClassConflictCount).sum();
        final long processed = summaries.size();
        return "<section class=\"card\"><h2>Summary</h2><table>"
                + "<thead><tr><th>Status</th><th>Progress</th>"
                + "<th>Reactors</th><th>Modules</th>"
                + "<th>Dependencies</th><th>Internal conflicts</th>"
                + "<th>Multi-version dependencies</th>"
                + "<th>Class conflicts</th>"
                + "<th>High-risk class conflicts</th>"
                + "<th>Failure reason</th></tr></thead><tbody><tr><td class=\""
                + state + "\">" + state + "</td><td>" + processed
                + "/" + total + "</td><td>" + processed
                + "</td><td>" + modules + "</td><td>"
                + dependencies + "</td><td>" + internalConflicts
                + "</td><td>" + multiVersionDependencies
                + "</td><td>" + classConflicts
                + "</td><td>" + highRiskClassConflicts
                + "</td><td>" + escape(failureReason)
                + "</td></tr></tbody></table></section>";
    }

    private String metadataRow(
            final String field,
            final String value) {
        return "<tr><th>" + escape(field) + "</th><td><code>"
                + escape(value) + "</code></td></tr>";
    }

    private TreeReportDataWriter.TreeReportData writeReactor(
            final Path target,
            final ReactorTreeResult reactor,
            final Path groupingCache) throws IOException {
        final List<IssueRow> issues = issueRows(reactor);
        final String pageName = target.getFileName().toString();
        final String base = pageName.endsWith(".html")
                ? pageName.substring(0,
                pageName.length() - ".html".length()) : pageName;
        final String directoryName = base + "-data";
        final Path dataDirectory = target.resolveSibling(directoryName);
        final Path staging = Files.createTempDirectory(
                target.getParent(), ".tree-report-data-");
        final TreeReportDataWriter.TreeReportData data;
        try {
            data = new TreeReportDataWriter().write(reactor,
                    staging, directoryName, groupingCache);
            if (Files.exists(dataDirectory)) {
                deleteTree(dataDirectory);
            }
            move(staging, dataDirectory);
            writePageAtomically(target, reactor.getReactor().getCoordinate(),
                    "../assets/", body -> {
            body.append("<p><a href=\"../../index.html\">")
                .append("← Repository</a></p><h1>")
                .append(escape(reactor.getReactor()
                        .getCoordinate()))
                .append("</h1><p><span class=\"badge ")
                .append(reactor.getStatus())
                .append("\">")
                .append(reactor.getStatus())
                .append("</span></p>")
                .append(reactorMetadata(reactor, data, issues.size()))
                .append(moduleMetadata(reactor, data))
                .append(issueTable(issues));
        appendDependencyAnalysis(body, data.manifest());
        appendModuleAnalysis(body, data.manifest());
        body.append("<script id=\"tree-report-manifest\" ")
                .append("type=\"application/json\">")
                .append(data.manifest().toJson())
                .append("</script>");
            });
        } catch (IOException | RuntimeException exception) {
            deleteTree(staging);
            deleteTree(dataDirectory);
            throw exception;
        }
        return data;
    }

    private String reactorMetadata(
            final ReactorTreeResult reactor,
            final TreeReportDataWriter.TreeReportData data,
            final int issueCount) {
        final long dependencyCount = reactor.getModules()
                .stream().mapToLong(module -> module
                        .getOccurrences().size()).sum();
        final HtmlSink value = HtmlSink.memory();
        final long classConflicts = classConflictCount(reactor);
        final long highRisk = highRiskClassConflictCount(reactor);
        final long internalConflictCount = data.manifest().modules().stream()
                .mapToLong(TreeReportManifest.ModuleDescriptor
                        ::internalConflictCount).sum();
        value.append("<section class=\"card\">")
                .append("<h2>Reactor metadata</h2><table>")
                .append("<thead><tr><th>Coordinate</th>")
                .append("<th>Root POM</th><th>Analysis mode</th>")
                .append("<th>Status</th><th>Modules</th>")
                .append("<th>Dependencies</th>")
                .append("<th>Internal conflicts</th>")
                .append("<th>Multi-version dependencies</th>")
                .append("<th>Class conflicts</th>")
                .append("<th>High-risk class conflicts</th>")
                .append("<th>Issues</th></tr></thead><tbody><tr><td><code>")
                .append(escape(reactor.getReactor()
                        .getCoordinate()))
                .append("</code></td><td><code>")
                .append(escape(reactor.getReactor()
                        .getRootPom().toString()))
                .append("</code></td><td>")
                .append(reactor.getReactor().getScopeMode())
                .append("</td><td class=\"")
                .append(reactor.getStatus()).append("\">")
                .append(reactor.getStatus()).append("</td><td>")
                .append(reactor.getModules().size())
                .append("</td><td>").append(dependencyCount)
                .append("</td><td>").append(internalConflictCount)
                .append("</td><td>")
                .append(data.multiVersionDependencyCount())
                .append("</td><td>").append(classConflicts)
                .append("</td><td>").append(highRisk)
                .append("</td><td>").append(issueCount)
                .append("</td></tr></tbody></table></section>");
        return value.toString();
    }

    private String moduleMetadata(
            final ReactorTreeResult reactor,
            final TreeReportDataWriter.TreeReportData data) {
        final HtmlSink value = HtmlSink.memory();
        value.append("<section class=\"card\"><h2>Module metadata</h2>")
                .append("<table><thead><tr><th>Module</th><th>POM</th>")
                .append("<th>Status</th><th>Dependencies</th>")
                .append("<th>Internal conflicts</th>")
                .append("<th>Multi-version dependencies</th>")
                .append("<th>Class conflicts</th>")
                .append("<th>High-risk class conflicts</th>")
                .append("<th>Issues</th></tr></thead>")
                .append("<tbody>");
        for (int index = 0; index < reactor.getModules().size(); index++) {
            final ModuleTreeResult module = reactor.getModules().get(index);
            final TreeReportManifest.ModuleDescriptor descriptor =
                    data.manifest().modules().get(index);
            final int issueCount = moduleIssueCount(module);
            final String status = moduleStatus(module);
            value.append("<tr><td><code>")
                    .append(escape(module.getCoordinate()))
                    .append("</code></td><td><code>")
                    .append(escape(module.getPom().toString()))
                    .append("</code></td><td class=\"")
                    .append(status).append("\">")
                    .append(status).append("</td><td>")
                    .append(module.getOccurrences().size())
                    .append("</td><td>")
                    .append(descriptor.internalConflictCount())
                    .append("</td><td>")
                    .append(descriptor.multiVersionDependencies())
                    .append("</td><td>")
                    .append(module.getClassConflicts().size())
                    .append("</td><td>")
                    .append(highRiskClassConflictCount(module))
                    .append("</td><td>").append(issueCount)
                    .append("</td></tr>");
        }
        return value.append("</tbody></table></section>")
                .toString();
    }

    private void appendDependencyAnalysis(
            final HtmlSink body,
            final TreeReportManifest manifest) {
        body.append("<section class=\"card\" id=\"dependency-analysis\">")
                .append("<h2>跨模块依赖分析</h2>")
                .append("<form class=\"dependency-filter-form\" ")
                .append("id=\"dependency-controls\">")
                .append("<div class=\"dependency-filter-primary\">")
                .append("<label class=\"report-control ")
                .append("dependency-search-control\">")
                .append("检索 <input id=\"dependency-search\" ")
                .append("type=\"search\"></label>");
        appendCombobox(body, "dependency-filter", "Dependency", false,
                "dependency-combobox-control");
        appendCombobox(body, "dependency-module-filter", "Module", false,
                "dependency-combobox-control");
        body.append("</div><div class=\"dependency-filter-secondary\">");
        body.append("<label class=\"report-control\">Scope ")
                .append("<select id=\"dependency-scope-filter\">")
                .append("<option value=\"\">全部</option>");
        for (TreeReportManifest.ScopeDescriptor scope : manifest.scopes()) {
            body.append("<option value=\"")
                    .append(escape(scope.value())).append("\">")
                    .append(escape(scope.value())).append("</option>");
        }
        body.append("</select></label><label class=\"report-control\">")
                .append("每页 <select id=\"dependency-page-size\">")
                .append("<option>10</option><option>50</option>")
                .append("<option>100</option></select></label>")
                .append("<div class=\"dependency-filter-buttons\">")
                .append("<button type=\"submit\">检索</button>")
                .append("<button type=\"button\" id=\"dependency-clear\">")
                .append("清空</button></div></div></form>")
                .append("<p id=\"dependency-result-summary\" class=\"muted\" ")
                .append("aria-live=\"polite\">Loading…</p>")
                .append("<div class=\"table-scroll\"><table ")
                .append("id=\"dependency-table\"><thead><tr>")
                .append(dynamicHeader("Dependency", "dependency"))
                .append(dynamicHeader("Scope", "scope"))
                .append(dynamicHeader("Module", "module"))
                .append(dynamicHeader("Dependency chain", "chain"))
                .append(dynamicHeader("Original version", "originalVersion"))
                .append(dynamicHeader("Resolved version", "resolvedVersion"))
                .append(dynamicHeader("Resolution source",
                        "resolutionSource"))
                .append("</tr></thead><tbody id=\"dependency-rows\">")
                .append("</tbody></table></div>")
                .append("<div class=\"controls\"><span class=\"pager\">")
                .append("<button id=\"dependency-previous\" type=\"button\">")
                .append("上一页</button> <span id=\"dependency-position\">")
                .append("</span> <button id=\"dependency-next\" ")
                .append("type=\"button\">下一页</button></span></div>")
                .append("<noscript><p class=\"WARN\">依赖浏览需要启用 ")
                .append("JavaScript，汇总信息仍可用。</p></noscript></section>");
    }

    private void appendModuleAnalysis(
            final HtmlSink body,
            final TreeReportManifest manifest) {
        body.append("<section class=\"card module-analysis-card\" ")
                .append("id=\"module-analysis\"><h2>Module 分析</h2>");
        if (manifest.modules().isEmpty()) {
            body.append("<p class=\"muted\">该 Reactor 没有 active Module。</p>")
                    .append("</section>");
            return;
        }
        appendCombobox(body, "module-selector", "Module", true,
                "module-selector-control");
        body.append("<div id=\"module-panel\" class=\"module-panel\" ")
                .append("aria-live=\"polite\">Loading…</div>")
                .append("<noscript><p class=\"WARN\">Module 分析需要启用 ")
                .append("JavaScript。</p></noscript></section>");
    }

    private void appendCombobox(
            final HtmlSink body,
            final String id,
            final String label,
            final boolean required,
            final String controlClass) {
        body.append("<div class=\"report-control ")
                .append(controlClass).append("\"><label for=\"")
                .append(id).append("-input\">")
                .append(escape(label)).append("</label>")
                .append("<div class=\"combobox\" data-combobox=\"")
                .append(id).append("\"><input id=\"")
                .append(id).append("-input\" type=\"text\" role=\"combobox\" ")
                .append("autocomplete=\"off\" aria-autocomplete=\"list\" ")
                .append("aria-expanded=\"false\" aria-controls=\"")
                .append(id).append("-options\"")
                .append(required ? " aria-required=\"true\"" : "")
                .append("><button id=\"").append(id)
                .append("-toggle\" class=\"combobox-toggle\" ")
                .append("type=\"button\" aria-label=\"展开 ")
                .append(escape(label)).append(" 候选\" ")
                .append("aria-expanded=\"false\" aria-controls=\"")
                .append(id).append("-options\" data-label=\"")
                .append(escape(label)).append("\"></button><ul id=\"")
                .append(id)
                .append("-options\" class=\"combobox-options\" ")
                .append("role=\"listbox\" hidden></ul></div>")
                .append("<p id=\"").append(id)
                .append("-status\" class=\"muted candidate-status\" ")
                .append("aria-live=\"polite\"></p></div>");
    }

    private String dynamicHeader(
            final String label,
            final String key) {
        return "<th><button class=\"sortable\" type=\"button\" "
                + "data-dependency-sort=\"" + key + "\">"
                + label + "</button></th>";
    }

    private ClassConflictShardManifest writeClassConflictData(
            final Path reactorPage,
            final ReactorTreeResult reactor) throws IOException {
        final String pageName = reactorPage.getFileName().toString();
        final String base = pageName.endsWith(".html")
                ? pageName.substring(0, pageName.length() - ".html".length())
                : pageName;
        final String directoryName = base + "-class-conflict-data";
        final Path target = reactorPage.resolveSibling(directoryName);
        final boolean empty = reactor.getModules().stream().allMatch(
                module -> module.getClassConflicts().isEmpty());
        if (empty) {
            return new ClassConflictShardManifest(target, Map.of());
        }
        final Path staging = Files.createTempDirectory(
                reactorPage.getParent(), ".class-conflict-data-");
        final Map<TreeClassConflict, ClassConflictShard> shards =
                new IdentityHashMap<>();
        try {
            int sequence = 0;
            for (ModuleTreeResult module : reactor.getModules()) {
                for (TreeClassConflict conflict : module.getClassConflicts()) {
                    final String id = "class-conflict-" + sequence;
                    final String filename = String.format(
                            Locale.ROOT, "class-conflict-%05d.js", sequence);
                    writeClassConflictShard(staging.resolve(filename),
                            id, conflict);
                    shards.put(conflict, new ClassConflictShard(
                            id, directoryName + "/" + filename));
                    sequence++;
                }
            }
            if (Files.exists(target)) {
                deleteTree(target);
            }
            move(staging, target);
            return new ClassConflictShardManifest(target, shards);
        } catch (IOException | RuntimeException exception) {
            deleteTree(staging);
            throw exception;
        }
    }

    private void writeClassConflictShard(
            final Path target,
            final String id,
            final TreeClassConflict conflict) throws IOException {
        try (OutputStream stream = Files.newOutputStream(target)) {
            stream.write("window.__ciaClassConflictPayload(".getBytes(
                    StandardCharsets.UTF_8));
            try (JsonGenerator json = ScriptSafeJson.factory()
                    .createGenerator(stream)) {
                json.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
                json.writeStartObject();
                json.writeNumberField("schemaVersion", 1);
                json.writeStringField("id", id);
                json.writeArrayFieldStart("candidates");
                for (TreeClassConflictCandidate candidate
                        : conflict.candidates()) {
                    json.writeStartObject();
                    json.writeStringField("source", sourceLabel(candidate));
                    json.writeBooleanField("winner",
                            candidate == conflict.winner());
                    json.writeBooleanField("available",
                            candidate.decompiled().isAvailable());
                    if (candidate.decompiled().isAvailable()) {
                        json.writeStringField("sourceCode",
                                candidate.decompiled().getSource());
                    }
                    json.writeEndObject();
                }
                json.writeEndArray();
                json.writeEndObject();
            }
            stream.write(");\n".getBytes(StandardCharsets.UTF_8));
        }
    }

    private void appendClassConflictTable(
            final HtmlSink body,
            final ModuleTreeResult module,
            final ClassConflictShardManifest data) {
        final List<TreeClassConflict> conflicts = module.getClassConflicts()
                .stream().sorted(Comparator
                        .comparing((TreeClassConflict value) ->
                                value.risk() == ClassConflictRisk.HIGH ? 0 : 1)
                        .thenComparing(TreeClassConflict::binaryName))
                .toList();
        body.append("<section class=\"card\" data-class-conflict-component>")
                .append("<h3>冲突类</h3>");
        if (!conflicts.isEmpty()) {
            appendClassConflictControls(body);
        }
        body.append("<table data-class-conflicts><thead><tr>")
                .append(sortableClassHeader("Class", "class"))
                .append(sortableClassHeader("Risk", "riskOrder"))
                .append(sortableClassHeader("Winner", "winner"))
                .append("<th>Shadowed sources</th><th>Selection</th>")
                .append("<th>Decompiled code</th></tr></thead><tbody>");
        for (TreeClassConflict conflict : conflicts) {
            final ClassConflictShard shard = data.shards().get(conflict);
            final String winner = sourceLabel(conflict.winner());
            final String shadowed = conflict.shadowed().stream()
                    .map(this::sourceLabel)
                    .collect(java.util.stream.Collectors.joining(" | "));
            final String search = String.join(" ", conflict.displayName(),
                    conflict.risk().name(), winner, shadowed,
                    conflict.selection()).toLowerCase(Locale.ROOT);
            body.append("<tr data-class-conflict-row data-class=\"")
                    .append(escape(conflict.displayName().toLowerCase(
                            Locale.ROOT)))
                    .append("\" data-risk-order=\"")
                    .append(conflict.risk() == ClassConflictRisk.HIGH
                            ? "0" : "1")
                    .append("\" data-risk=\"")
                    .append(conflict.risk())
                    .append("\" data-winner=\"")
                    .append(escape(winner.toLowerCase(Locale.ROOT)))
                    .append("\" data-class-conflict-text=\"")
                    .append(escape(search)).append("\" data-payload-id=\"")
                    .append(escape(shard.id())).append("\" data-shard=\"")
                    .append(escape(shard.relativeFile())).append("\">")
                    .append("<td><code>")
                    .append(escape(conflict.displayName()))
                    .append("</code></td><td><span class=\"badge ")
                    .append(conflict.risk() == ClassConflictRisk.HIGH
                            ? "FAILED" : "muted")
                    .append("\">").append(conflict.risk())
                    .append("</span></td><td>").append(escape(winner))
                    .append("</td><td>").append(escape(shadowed))
                    .append("</td><td>")
                    .append(escape(conflict.selection()))
                    .append("</td><td><button type=\"button\" ")
                    .append("data-view-class-code aria-expanded=\"false\">")
                    .append("查看反编译代码</button></td></tr>");
        }
        body.append("</tbody></table></section>");
    }

    private void appendClassConflictControls(final HtmlSink body) {
        body.append("<div class=\"controls\" data-class-controls>")
                .append("<label>检索 <input type=\"search\" ")
                .append("data-class-search></label>")
                .append("<label>Risk <select data-risk-filter>")
                .append("<option value=\"\">全部</option>")
                .append("<option>HIGH</option><option>LOW</option>")
                .append("</select></label><label>每页 ")
                .append("<select data-class-page-size>")
                .append("<option>10</option><option>50</option>")
                .append("<option>100</option></select></label>")
                .append("<span class=\"pager\">")
                .append("<button type=\"button\" data-class-previous>")
                .append("上一页</button> <span data-class-position></span> ")
                .append("<button type=\"button\" data-class-next>")
                .append("下一页</button></span></div>");
    }

    private String sortableClassHeader(
            final String label,
            final String key) {
        return "<th><button class=\"sortable\" type=\"button\" "
                + "data-class-sort=\"" + key + "\">" + label
                + "</button></th>";
    }

    private String sourceLabel(final TreeClassConflictCandidate candidate) {
        return candidate.origin() + " — " + candidate.source()
                + (candidate.scope().isBlank()
                ? "" : " [" + candidate.scope() + "]");
    }

    private long classConflictCount(final ReactorTreeResult reactor) {
        return reactor.getModules().stream().mapToLong(value ->
                value.getClassConflicts().size()).sum();
    }

    private long highRiskClassConflictCount(
            final ReactorTreeResult reactor) {
        return reactor.getModules().stream().mapToLong(
                this::highRiskClassConflictCount).sum();
    }

    private long highRiskClassConflictCount(final ModuleTreeResult module) {
        return module.getClassConflicts().stream().filter(value ->
                value.risk() == ClassConflictRisk.HIGH).count();
    }

    private void appendModuleTabs(
            final HtmlSink body,
            final List<ModuleTreeResult> modules,
            final Map<ModuleTreeResult,
                    List<ConflictRow>> conflicts,
            final ClassConflictShardManifest classData) {
        body.append("<section data-module-tabs><h2>Module 分析</h2>");
        if (modules.isEmpty()) {
            body.append("<p>该 reactor 没有 active module。</p></section>");
            return;
        }
        final Map<ModuleTreeResult, String> labels =
                moduleTabLabels(modules);
        body.append("<div class=\"tab-list\" role=\"tablist\" ")
                .append("aria-label=\"Modules\">");
        for (int index = 0; index < modules.size(); index++) {
            final ModuleTreeResult module = modules.get(index);
            final String id = moduleTabId(module);
            body.append("<button type=\"button\" role=\"tab\" id=\"")
                    .append("module-tab-").append(id)
                    .append("\" aria-controls=\"module-panel-")
                    .append(id).append("\" aria-selected=\"")
                    .append(index == 0).append("\" tabindex=\"")
                    .append(index == 0 ? "0" : "-1")
                    .append("\" title=\"")
                    .append(escape(module.getCoordinate()))
                    .append("\">")
                    .append(escape(labels.get(module)))
                    .append("</button>");
        }
        body.append("</div>");
        for (int index = 0; index < modules.size(); index++) {
            appendModulePanel(body, modules.get(index),
                    conflicts.get(modules.get(index)), index == 0, classData);
        }
        body.append("</section>");
    }

    private void appendModulePanel(
            final HtmlSink body,
            final ModuleTreeResult module,
            final List<ConflictRow> conflicts,
            final boolean selected,
            final ClassConflictShardManifest classData) {
        final String id = moduleTabId(module);
        body.append("<section role=\"tabpanel\" id=\"module-panel-")
                .append(id).append("\" aria-labelledby=\"module-tab-")
                .append(id).append("\"")
                .append(selected ? "" : " hidden")
                .append("><h2>")
                .append(escape(module.getCoordinate()))
                .append("</h2>");
        appendClassConflictTable(body, module, classData);
        appendConflictTable(body, "模块内部依赖冲突",
                conflicts, false);
        body.append("<section class=\"card\"><h3>Dependency tree</h3>");
        if (!module.getFailure().isBlank()) {
            body.append("<p class=\"FAILED\">")
                    .append("Dependency tree 未生成。</p>");
        } else if (module.getOccurrences().isEmpty()) {
            body.append("<p class=\"muted\">未发现 dependency。</p>");
        } else {
            body.append("<pre class=\"dependency-tree\">");
            appendMavenTree(body, module);
            body.append("</pre>");
        }
        body.append("</section></section>");
    }

    private Map<ModuleTreeResult, String> moduleTabLabels(
            final List<ModuleTreeResult> modules) {
        final Map<ModuleTreeResult, String> labels =
                new LinkedHashMap<>();
        for (ModuleTreeResult module : modules) {
            labels.put(module, artifactId(module));
        }
        disambiguateLabels(modules, labels, true);
        disambiguateLabels(modules, labels, false);
        return labels;
    }

    private void disambiguateLabels(
            final List<ModuleTreeResult> modules,
            final Map<ModuleTreeResult, String> labels,
            final boolean groupArtifact) {
        final Map<String, Integer> counts = new LinkedHashMap<>();
        labels.values().forEach(label -> counts.merge(
                label, 1, Integer::sum));
        for (ModuleTreeResult module : modules) {
            if (counts.get(labels.get(module)) > 1) {
                labels.put(module, groupArtifact
                        ? groupArtifact(module)
                        : coordinateOrPom(module));
            }
        }
    }

    private String artifactId(final ModuleTreeResult module) {
        final String[] parts = coordinateParts(module);
        return parts.length > 1 && !parts[1].isBlank()
                ? parts[1] : coordinateOrPom(module);
    }

    private String groupArtifact(final ModuleTreeResult module) {
        final String[] parts = coordinateParts(module);
        return parts.length > 1 && !parts[0].isBlank()
                && !parts[1].isBlank()
                ? parts[0] + ":" + parts[1]
                : coordinateOrPom(module);
    }

    private String[] coordinateParts(
            final ModuleTreeResult module) {
        return module.getCoordinate().split(":", -1);
    }

    private String coordinateOrPom(
            final ModuleTreeResult module) {
        return module.getCoordinate().isBlank()
                ? module.getPom().toString()
                : module.getCoordinate();
    }

    private String moduleTabId(final ModuleTreeResult module) {
        return stableHash(module.getCoordinate() + "\n"
                + module.getPom());
    }

    private void appendMavenTree(
            final HtmlSink result,
            final ModuleTreeResult module) {
        final List<DependencyOccurrence> occurrences =
                module.getOccurrences();
        String rootCoordinate = module.getCoordinate();
        for (DependencyOccurrence occurrence : occurrences) {
            if (!occurrence.getPath().isEmpty()) {
                rootCoordinate = occurrence.getPath().get(0);
                break;
            }
        }
        List<String> previousPath = List.of();
        for (int occurrenceIndex = 0;
             occurrenceIndex < occurrences.size(); occurrenceIndex++) {
            final DependencyOccurrence occurrence = occurrences.get(
                    occurrenceIndex);
            final List<String> path = occurrencePath(
                    occurrence, rootCoordinate);
            final int common = commonPrefixLength(
                    previousPath, path);
            final int start = common == path.size()
                    ? Math.max(0, path.size() - 1) : common;
            for (int index = start;
                 index < path.size(); index++) {
                final boolean terminal = index
                        == path.size() - 1;
                appendMavenLine(result,
                        path.subList(0, index + 1),
                        terminal ? occurrence.coordinate()
                                : path.get(index),
                        terminal ? occurrence : null,
                        occurrences, occurrenceIndex,
                        rootCoordinate);
            }
            previousPath = path;
        }
    }

    private List<String> occurrencePath(
            final DependencyOccurrence occurrence,
            final String rootCoordinate) {
        return occurrence.getPath().isEmpty()
                ? List.of(rootCoordinate, occurrence.coordinate())
                : occurrence.getPath();
    }

    private int commonPrefixLength(
            final List<String> left,
            final List<String> right) {
        final int maximum = Math.min(left.size(),
                right.size());
        int common = 0;
        while (common < maximum && left.get(common)
                .equals(right.get(common))) {
            common++;
        }
        return common;
    }

    private void appendMavenLine(
            final HtmlSink body,
            final List<String> path,
            final String coordinate,
            final DependencyOccurrence occurrence,
            final List<DependencyOccurrence> occurrences,
            final int occurrenceIndex,
            final String rootCoordinate) {
        final int depth = path.size() - 1;
        for (int ancestor = 1;
             ancestor < depth; ancestor++) {
            body.append(hasLaterSibling(path, occurrences,
                    occurrenceIndex, ancestor, rootCoordinate, false)
                    ? "|  " : "   ");
        }
        if (depth > 0) {
            body.append(hasLaterSibling(path, occurrences,
                    occurrenceIndex, depth, rootCoordinate,
                    occurrence != null)
                    ? "+- " : "\\- ");
        }
        body.append(escape(coordinate));
        if (occurrence != null) {
            body.append(escape(verboseAnnotation(occurrence)));
        }
        body.append('\n');
    }

    private boolean hasLaterSibling(
            final List<String> current,
            final List<DependencyOccurrence> occurrences,
            final int currentIndex,
            final int depth,
            final String rootCoordinate,
            final boolean terminal) {
        for (int index = currentIndex + 1;
             index < occurrences.size(); index++) {
            final List<String> candidate = occurrencePath(
                    occurrences.get(index), rootCoordinate);
            if (candidate.size() <= depth) {
                return false;
            }
            if (!current.subList(0, depth).equals(
                    candidate.subList(0, depth))) {
                return false;
            }
            if (current.get(depth).equals(candidate.get(depth))) {
                if (terminal && candidate.size() == current.size()) {
                    return true;
                }
                continue;
            }
            return true;
        }
        return false;
    }

    private String verboseAnnotation(
            final DependencyOccurrence occurrence) {
        final List<String> values = new ArrayList<>();
        if (!occurrence.getManagedFromVersion().isBlank()) {
            values.add("version managed from "
                    + occurrence.getManagedFromVersion());
        }
        if (!occurrence.getManagedFromScope().isBlank()) {
            values.add("scope managed from "
                    + occurrence.getManagedFromScope());
        }
        if (Boolean.TRUE.equals(occurrence.getOptional())) {
            values.add("optional");
        }
        if (!occurrence.isSelected()) {
            values.add(omittedAnnotation(occurrence));
        }
        if (occurrence.isReactorModule()) {
            values.add("reactor module");
        }
        return values.isEmpty() ? ""
                : " (" + String.join("; ", values) + ")";
    }

    private String omittedAnnotation(
            final DependencyOccurrence occurrence) {
        final String reason = occurrence.getOmittedReason();
        if (reason.equals("conflict")) {
            return "omitted for conflict with "
                    + occurrence.getSelectedVersion();
        }
        if (reason.startsWith("omitted ")) {
            return reason;
        }
        return reason.isBlank() ? "omitted"
                : "omitted for " + reason;
    }

    private String issueTable(final List<IssueRow> issues) {
        if (issues.isEmpty()) {
            return "";
        }
        final HtmlSink value = HtmlSink.memory();
        value.append("<section class=\"card\"><h2>问题</h2><table>")
                .append("<thead><tr><th>Severity</th><th>Code</th>")
                .append("<th>Scope</th><th>Message</th>")
                .append("<th>Remediation</th></tr></thead><tbody>");
        for (IssueRow issue : issues) {
            value.append("<tr><td class=\"")
                    .append(escape(issue.severity()))
                    .append("\">").append(escape(issue.severity()))
                    .append("</td><td><code>")
                    .append(escape(issue.code()))
                    .append("</code></td><td><code>")
                    .append(escape(issue.scope()))
                    .append("</code></td><td>")
                    .append(escape(issue.message()))
                    .append("</td><td>")
                    .append(escape(issue.remediation()))
                    .append("</td></tr>");
        }
        return value.append("</tbody></table></section>")
                .toString();
    }

    private void appendConflictTable(
            final HtmlSink value,
            final String title,
            final List<ConflictRow> conflicts,
            final boolean crossModule) {
        value.append("<section class=\"card\" ")
                .append("data-conflict-component><h3>")
                .append(escape(title)).append("</h3>");
        if (!conflicts.isEmpty()) {
            appendConflictControls(value, conflicts,
                    crossModule);
        }
        value.append("<table data-conflicts data-default-sort=\"")
                .append(crossModule ? "module" : "dependency")
                .append("\"><thead><tr>");
        if (crossModule) {
            value.append(sortableHeader("Module", "module"));
        }
        value.append(sortableHeader("Dependency", "dependency"))
                .append(sortableHeader("Scope", "scope"))
                .append(sortableHeader("Resolved version", "version"))
                .append("<th>Result</th></tr></thead><tbody>");
        for (ConflictRow conflict : conflicts) {
            value.append("<tr data-module=\"")
                    .append(escape(conflict.module().toLowerCase(
                            Locale.ROOT)))
                    .append("\" data-modules=\"")
                    .append(escape(String.join("|",
                            conflict.modules())))
                    .append("\" data-dependency=\"")
                    .append(escape(conflict.dependency()
                            .toLowerCase(Locale.ROOT)))
                    .append("\" data-scope=\"")
                    .append(escape(conflict.scope()
                            .toLowerCase(Locale.ROOT)))
                    .append("\" data-scopes=\"")
                    .append(escape(String.join("|",
                            conflict.scopes())))
                    .append("\" data-version=\"")
                    .append(escape(conflict.version()
                            .toLowerCase(Locale.ROOT)))
                    .append("\" data-conflict-text=\"")
                    .append(escape(conflict.searchText())).append("\">");
            if (crossModule) {
                value.append("<td>")
                        .append(escape(conflict.module()))
                        .append("</td>");
            }
            value.append("<td><code>")
                    .append(escape(conflict.dependency()))
                    .append("</code></td><td>")
                    .append(escape(conflict.scope()))
                    .append("</td><td>")
                    .append(escape(conflict.version()))
                    .append("</td><td>")
                    .append(conflict.evidenceHtml())
                    .append("</td></tr>");
        }
        value.append("</tbody></table></section>");
    }

    private void appendConflictControls(
            final HtmlSink body,
            final List<ConflictRow> conflicts,
            final boolean crossModule) {
        final Set<String> modules = new LinkedHashSet<>();
        final Set<String> scopes = new LinkedHashSet<>();
        conflicts.forEach(conflict -> {
            modules.addAll(conflict.modules());
            scopes.addAll(conflict.scopes());
        });
        body.append("<div class=\"controls\" data-conflict-controls>")
                .append("<label>检索 <input data-conflict-search ")
                .append("type=\"search\"></label>");
        if (crossModule) {
            body.append("<label>Module <select data-conflict-module>")
                    .append("<option value=\"\">全部</option>");
            modules.stream().sorted().forEach(module -> body
                    .append("<option value=\"")
                    .append(escape(module)).append("\">")
                    .append(escape(module)).append("</option>"));
            body.append("</select></label>");
        }
        body.append("<label>Scope <select data-conflict-scope>")
                .append("<option value=\"\">全部</option>");
        scopes.stream().sorted().forEach(scope -> body
                .append("<option value=\"")
                .append(escape(scope)).append("\">")
                .append(escape(scope)).append("</option>"));
        body.append("</select></label><label>每页 ")
                .append("<select data-page-size>")
                .append("<option>10</option><option>50</option>")
                .append("<option>100</option></select></label>")
                .append("<span class=\"pager\">")
                .append("<button data-page-previous type=\"button\">")
                .append("上一页</button> <span data-page-position>")
                .append("</span> <button data-page-next type=\"button\">")
                .append("下一页</button></span></div>");
    }

    private String sortableHeader(
            final String label,
            final String key) {
        return "<th><button class=\"sortable\" type=\"button\" "
                + "data-conflict-sort=\"" + key + "\">"
                + label + "</button></th>";
    }

    private Map<ModuleTreeResult, List<ConflictRow>>
            internalConflictRows(
            final ReactorTreeResult reactor,
            final Path groupingCache) throws IOException {
        final Map<ModuleTreeResult, List<ConflictRow>> result =
                new LinkedHashMap<>();
        for (ModuleTreeResult module : reactor.getModules()) {
            final List<ConflictRow> rows = new ArrayList<>();
            final List<VersionMediationIssue> issues = groupingCache == null
                    ? new ModuleVersionAnalyzer().analyze(module)
                    : new ModuleVersionAnalyzer().analyze(
                            module, groupingCache);
            for (VersionMediationIssue issue : issues) {
                rows.add(moduleConflict(module, issue));
            }
            rows.sort(Comparator.comparing(
                    ConflictRow::dependency));
            result.put(module, rows);
        }
        return result;
    }

    private ConflictRow moduleConflict(
            final ModuleTreeResult module,
            final VersionMediationIssue issue) {
        final HtmlSink html = HtmlSink.memory();
        final HtmlSink text = HtmlSink.memory();
        final Set<String> scopes = new LinkedHashSet<>();
        appendEvidenceTableStart(html, false);
        for (VersionPath path : issue.getPaths()) {
            scopes.add(path.getScope());
            for (VersionEvidence source : path.getEvidence()) {
                appendEvidenceRow(html, text, source,
                        module.getCoordinate(), path, false);
            }
        }
        html.append("</tbody></table>");
        final List<String> scopeValues = scopes.stream()
                .sorted().toList();
        return new ConflictRow("MODULE_MEDIATION",
                module.getCoordinate(),
                List.of(module.getCoordinate()),
                issue.getKey().toString(),
                String.join(", ", scopeValues), scopeValues,
                issue.getSelectedVersion(), html.toString(),
                conflictSearch("MODULE_MEDIATION",
                        module.getCoordinate(),
                        issue.getKey().toString(),
                        String.join(", ", scopeValues),
                        issue.getSelectedVersion(),
                        text.toString()));
    }

    private void appendEvidenceTableStart(
            final HtmlSink html,
            final boolean crossModule) {
        html.append("<table class=\"evidence-table\"><thead><tr>")
                .append("<th>Source</th>");
        if (crossModule) {
            html.append("<th>Module</th>");
        }
        html.append("<th>Dependency chain</th>")
                .append("<th>Original version</th><th>Scope</th>")
                .append("</tr></thead><tbody>");
    }

    private void appendEvidenceRow(
            final HtmlSink html,
            final HtmlSink text,
            final VersionEvidence source,
            final String module,
            final VersionPath path,
            final boolean crossModule) {
        final String chain = source.getSource()
                == VersionEvidenceSource.DEPENDENCY_MANAGEMENT
                ? "" : String.join(" → ", path.getPath());
        html.append("<tr><td><code>")
                .append(source.getSource())
                .append("</code></td>");
        if (crossModule) {
            html.append("<td><code>")
                    .append(escape(module))
                    .append("</code></td>");
        }
        if (chain.isEmpty()) {
            html.append("<td></td>");
        } else {
            html.append("<td><code>").append(escape(chain))
                    .append("</code></td>");
        }
        html.append("<td><code>")
                .append(evidence(source.getVersion()))
                .append("</code></td><td>")
                .append(evidence(path.getScope()))
                .append("</td></tr>");
        text.append(' ').append(source.getSource())
                .append(' ').append(module)
                .append(' ').append(chain)
                .append(' ').append(source.getVersion())
                .append(' ').append(path.getScope());
    }

    private String conflictSearch(final String... values) {
        return String.join(" ", values)
                .toLowerCase(Locale.ROOT);
    }

    private List<IssueRow> issueRows(
            final ReactorTreeResult reactor) {
        final List<IssueRow> issues = new ArrayList<>();
        if (!reactor.getReason().isBlank()) {
            issues.add(new IssueRow(
                    reactor.getStatus() == ReactorStatus.FAILED
                            ? "FAIL" : "WARN",
                    "REACTOR_ANALYSIS",
                    reactor.getReactor().getId(),
                    reactor.getReason(),
                    "查看 Maven diagnostics 并修复 Reactor 分析错误。"));
        }
        for (String violation
                : reactor.getReactor().getViolations()) {
            issues.add(new IssueRow("FAIL", "REACTOR_MODEL",
                    reactor.getReactor().getId(), violation,
                    "修复 Reactor POM model 或 module 边界。"));
        }
        for (ModuleTreeResult module : reactor.getModules()) {
            if (!module.getFailure().isBlank()) {
                issues.add(new IssueRow("FAIL", "MODULE_ANALYSIS",
                        module.getCoordinate(), module.getFailure(),
                        "查看 Maven diagnostics 并修复 Module 分析错误。"));
            }
            if (!module.isCompleteMediation()) {
                issues.add(new IssueRow("WARN", "INCOMPLETE_EVIDENCE",
                        module.getCoordinate(),
                        "Dependency conflict evidence is incomplete.",
                        "使用支持完整 verbose evidence 的 dependency plugin。"));
            }
            for (String reason : module.getClasspathIssues()) {
                issues.add(new IssueRow("WARN", "INCOMPLETE_CLASSPATH",
                        module.getCoordinate(), reason,
                        "检查 compile 输出、Reactor classifier 与 classpath。"));
            }
        }
        return issues;
    }

    private int moduleIssueCount(
            final ModuleTreeResult module) {
        int count = module.getFailure().isBlank() ? 0 : 1;
        if (!module.isCompleteMediation()) {
            count++;
        }
        return count + module.getClasspathIssues().size();
    }

    private String moduleStatus(
            final ModuleTreeResult module) {
        if (!module.getFailure().isBlank()) {
            return "FAILED";
        }
        return module.isCompleteMediation()
                && module.getClasspathIssues().isEmpty()
                ? "SUCCESS" : "DEGRADED";
    }

    private String preflightTable(
            final PreflightReport report,
            final String scopeId) {
        final HtmlSink value = HtmlSink.memory();
        value.append("<h2>Preflight</h2><table><tr>")
                .append("<th>checkId</th><th>scope</th>")
                .append("<th>status</th><th>decision</th>")
                .append("<th>summary/evidence/fallback</th></tr>");
        for (PreflightResult item
                : report.getResults()) {
            if (scopeId != null
                    && !item.getScopeId().equals(scopeId)) {
                continue;
            }
            value.append("<tr><td>")
                    .append(escape(item.getCheckId()))
                    .append("</td><td>")
                    .append(item.getScope()).append(" / ")
                    .append(escape(item.getScopeId()))
                    .append("</td><td class=\"")
                    .append(item.getStatus()).append("\">")
                    .append(item.getStatus())
                    .append("</td><td>")
                    .append(item.getDecision())
                    .append("</td><td>")
                    .append(escape(item.getSummary()))
                    .append(item.getEvidence().isBlank()
                            ? "" : "<br><span class=\"muted\">"
                            + escape(item.getEvidence()) + "</span>")
                    .append(item.getFallback().isBlank()
                            ? "" : "<br>" + escape(item.getFallback()))
                    .append("</td></tr>");
        }
        return value.append("</table>").toString();
    }

    private String term(
            final String name,
            final String value) {
        return "<dt>" + escape(name) + "</dt><dd><code>"
                + escape(value) + "</code></dd>";
    }

    private PreflightReport checkpointPreflight(
            final TreeReportMetadata metadata) {
        return metadata.getPreflight();
    }

    private String displayPath(final Path path) {
        return path.toString().isBlank()
                ? "." : path.toString();
    }

    private String filename(final String id) {
        return TreeReportFileName.of(id);
    }

    private String stableHash(final String value) {
        try {
            final byte[] bytes = MessageDigest
                    .getInstance("SHA-256")
                    .digest(value.getBytes(
                            StandardCharsets.UTF_8));
            final HtmlSink result = HtmlSink.memory();
            for (int index = 0;
                 index < SHORT_HASH_BYTES;
                 index++) {
                result.append(String.format(
                        Locale.ROOT, "%02x",
                        bytes[index]));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String escape(final String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private String evidence(final String value) {
        return value == null || value.isBlank()
                ? "—" : escape(value);
    }

    private void replaceOwnedOutputs(
            final Path indexSource,
            final Path reportSource,
            final Path indexTarget,
            final Path reportTarget)
            throws IOException {
        final String suffix = ".backup-"
                + UUID.randomUUID();
        final Path indexBackup = indexTarget
                .resolveSibling(".dependency-analyzer-index"
                        + suffix);
        final Path reportBackup = reportTarget
                .resolveSibling(".dependency-analyzer-report"
                        + suffix);
        final boolean hadIndex = Files.exists(indexTarget);
        final boolean hadReport = Files.exists(reportTarget);
        boolean indexInstalled = false;
        boolean reportInstalled = false;
        try {
            if (hadIndex) {
                move(indexTarget, indexBackup);
            }
            if (hadReport) {
                move(reportTarget, reportBackup);
            }
            move(reportSource, reportTarget);
            reportInstalled = true;
            move(indexSource, indexTarget);
            indexInstalled = true;
        } catch (IOException exception) {
            IOException rollbackFailure = null;
            try {
                rollbackTarget(indexTarget, indexBackup,
                        hadIndex, indexInstalled);
            } catch (IOException rollbackException) {
                rollbackFailure = rollbackException;
            }
            try {
                rollbackTarget(reportTarget, reportBackup,
                        hadReport, reportInstalled);
            } catch (IOException rollbackException) {
                if (rollbackFailure == null) {
                    rollbackFailure = rollbackException;
                } else {
                    rollbackFailure.addSuppressed(
                            rollbackException);
                }
            }
            if (rollbackFailure != null) {
                exception.addSuppressed(rollbackFailure);
            }
            throw exception;
        }
        deleteTree(indexBackup);
        deleteTree(reportBackup);
    }

    private void rollbackTarget(
            final Path target,
            final Path backup,
            final boolean existed,
            final boolean installed)
            throws IOException {
        if (Files.exists(target)
                && (installed || Files.exists(backup))) {
            deleteTree(target);
        }
        if (existed && Files.exists(backup)) {
            move(backup, target);
        }
    }

    private void move(
            final Path source,
            final Path target)
            throws IOException {
        moveOperation.move(source, target);
    }

    private void writePageAtomically(
            final Path target,
            final String title,
            final String assetPrefix,
            final PageBody content)
            throws IOException {
        final Path temporary = Files.createTempFile(
                target.getParent(), ".report-", ".tmp");
        try {
            writePage(temporary, title, assetPrefix, content);
            move(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void writePage(
            final Path target,
            final String title,
            final String assetPrefix,
            final PageBody content) throws IOException {
        try (Writer writer = Files.newBufferedWriter(
                target, StandardCharsets.UTF_8)) {
            final HtmlSink output = HtmlSink.writer(writer);
            output.append("<!DOCTYPE html><html lang=\"zh-CN\"><head>")
                    .append("<meta charset=\"UTF-8\"><meta ")
                    .append("name=\"viewport\" content=\"width=device-")
                    .append("width,initial-scale=1\"><title>")
                    .append(escape(title))
                    .append("</title><link rel=\"stylesheet\" href=\"")
                    .append(assetPrefix)
                    .append("report.css\"></head><body><main>");
            content.write(output);
            output.append("</main><script src=\"")
                    .append(assetPrefix)
                    .append("report-common.js\"></script><script src=\"")
                    .append(assetPrefix)
                    .append("report.js\"></script></body></html>");
        } catch (java.io.UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    /** Streaming page body callback. */
    @FunctionalInterface
    private interface PageBody {
        void write(HtmlSink output);
    }

    private static String resource(final String path) {
        try (InputStream input = TreeReportRenderer.class
                .getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException(
                        "Missing report resource: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new UncheckedIOException(
                    "Failed to read report resource: " + path, exception);
        }
    }

    /** Append facade that converts checked Writer failures to one runtime. */
    private static final class HtmlSink {

        /** Target appendable. */
        private final Appendable target;

        private HtmlSink(final Appendable value) {
            target = value;
        }

        static HtmlSink memory() {
            return new HtmlSink(new StringBuilder());
        }

        static HtmlSink writer(final Writer value) {
            return new HtmlSink(value);
        }

        HtmlSink append(final Object value) {
            try {
                target.append(String.valueOf(value));
                return this;
            } catch (IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        }

        HtmlSink append(final char value) {
            try {
                target.append(value);
                return this;
            } catch (IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        }

        @Override
        public String toString() {
            return target.toString();
        }
    }

    private static void moveFile(
            final Path source,
            final Path target)
            throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException
                 | java.nio.file.DirectoryNotEmptyException
                 exception) {
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteTree(final Path root)
            throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (java.util.stream.Stream<Path> stream =
                     Files.walk(root)) {
            final Path[] paths = stream.sorted(
                    Comparator.reverseOrder())
                    .toArray(Path[]::new);
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    /** Controlled filesystem move. */
    @FunctionalInterface
    interface MoveOperation {

        /**
         * Moves one path.
         *
         * @param source source path
         * @param target target path
         * @throws IOException on move failure
         */
        void move(Path source, Path target)
                throws IOException;
    }

    /**
     * One row in the unified dependency-conflict table.
     *
     * @param type conflict type
     * @param module module display text
     * @param modules filterable modules
     * @param dependency dependency key
     * @param scope scope display text
     * @param scopes filterable scopes
     * @param version resolved version display text
     * @param evidenceHtml escaped evidence HTML
     * @param searchText normalized searchable text
     */
    private record ConflictRow(
            String type,
            String module,
            List<String> modules,
            String dependency,
            String scope,
            List<String> scopes,
            String version,
            String evidenceHtml,
            String searchText) {
    }

    /**
     * Published class source shards for one Reactor page.
     *
     * @param directory published data directory
     * @param shards shard reference by conflict identity
     */
    private record ClassConflictShardManifest(
            Path directory,
            Map<TreeClassConflict, ClassConflictShard> shards) {
    }

    /**
     * One lazy class source payload reference.
     *
     * @param id payload identifier
     * @param relativeFile Reactor-page-relative shard
     */
    private record ClassConflictShard(
            String id,
            String relativeFile) {
    }

    /**
     * One row in the unified issue table.
     *
     * @param severity issue severity
     * @param code stable issue code
     * @param scope issue scope
     * @param message issue message
     * @param remediation suggested remediation
     */
    private record IssueRow(
            String severity,
            String code,
            String scope,
            String message,
            String remediation) {
    }

}
