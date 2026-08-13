package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.preflight
        .PreflightResult;
import io.github.dependencyanalysis.preflight
        .PreflightReport;
import io.github.dependencyanalysis.runtime.ReportTaskCache;

import java.io.IOException;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Wiki: wiki/features/repository-dependency-tree-report.md - HTML renderer
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
            main{max-width:1440px;margin:auto;padding:28px}h1{margin:0 0 8px}
            h2{margin-top:28px}.muted{color:var(--muted)}
            .card{background:var(--card);border:1px solid var(--line);
            border-radius:10px;padding:18px;margin:14px 0}
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
            .evidence-table th,.evidence-table td{padding:5px;font-size:12px}
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
            a{color:#175cd3}
            """;

    /** Local report interaction script. */
    private static final String JAVASCRIPT = """
            (()=>{document.querySelectorAll('[data-conflict-component]')
            .forEach(component=>{const table=component.querySelector(
            '[data-conflicts]');if(!table)return;const body=table.tBodies[0];
            const rows=[...body.children].filter(row=>row.tagName==='TR');
            if(!rows.length)return;const search=component.querySelector(
            '[data-conflict-search]');const module=component.querySelector(
            '[data-conflict-module]');const scope=component.querySelector(
            '[data-conflict-scope]');const size=component.querySelector(
            '[data-page-size]');const previous=component.querySelector(
            '[data-page-previous]');const next=component.querySelector(
            '[data-page-next]');const position=component.querySelector(
            '[data-page-position]');let page=0;
            let sort=table.dataset.defaultSort;let direction=1;
            const value=(row,key)=>row.dataset[key]||'';
            const render=()=>{const query=search.value.trim().toLowerCase();
            const filtered=rows.filter(row=>(!query||
            value(row,'conflictText').includes(query))&&(!module||
            !module.value||
            value(row,'modules').split('|').includes(module.value))&&
            (!scope||!scope.value||
            value(row,'scopes').split('|').includes(scope.value)));
            filtered.sort((left,right)=>direction*
            value(left,sort).localeCompare(value(right,sort)));
            rows.forEach(row=>row.classList.add('hidden'));
            filtered.forEach(row=>body.appendChild(row));
            const count=Number(size.value);const pages=Math.max(1,
            Math.ceil(filtered.length/count));page=Math.min(page,pages-1);
            const start=page*count;const end=Math.min(start+count,
            filtered.length);filtered.slice(start,end).forEach(row=>
            row.classList.remove('hidden'));position.textContent=
            filtered.length?`${start+1}-${end} / ${filtered.length}`:'0 / 0';
            previous.disabled=page===0;next.disabled=page>=pages-1;};
            [search,module,scope,size].filter(Boolean).forEach(control=>
            control.addEventListener(
            control===search?'input':'change',()=>{page=0;render();}));
            previous.onclick=()=>{page--;render();};
            next.onclick=()=>{page++;render();};
            component.querySelectorAll('[data-conflict-sort]').forEach(button=>
            button.onclick=()=>{const key=button.dataset.conflictSort;
            direction=sort===key?-direction:1;sort=key;page=0;render();});
            render();});document.querySelectorAll('[data-module-tabs]')
            .forEach(tabs=>{const buttons=[...tabs.querySelectorAll(
            '[role=tab]')];const activate=(button,focus)=>{
            buttons.forEach(candidate=>{const selected=candidate===button;
            candidate.setAttribute('aria-selected',String(selected));
            candidate.tabIndex=selected?0:-1;const panel=tabs.querySelector(
            '#'+candidate.getAttribute('aria-controls'));
            panel.hidden=!selected;});
            if(focus)button.focus();};buttons.forEach((button,index)=>{
            button.onclick=()=>activate(button,false);button.onkeydown=event=>{
            let target=index;if(event.key==='ArrowRight')
            target=(index+1)%buttons.length;
            else if(event.key==='ArrowLeft')
            target=(index-1+buttons.length)%buttons.length;
            else if(event.key==='Home')target=0;
            else if(event.key==='End')target=buttons.length-1;else return;
            event.preventDefault();activate(buttons[target],true);};});});})();
            """;

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
     * @param cache task-scoped report cache
     * @return initialized report session
     * @throws IOException on write failure
     */
    public TreeReportSession start(
            final TreeReportMetadata metadata,
            final int totalReactors,
            final Path output,
            final ReportTaskCache cache) throws IOException {
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
                    JAVASCRIPT, StandardCharsets.UTF_8);
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
        writeReactor(page, result, groupingCache);
        final ReactorReportSummary summary =
                ReactorReportSummary.from(filename,
                        result);
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
                .append("<th>cross-module conflicts</th>")
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
                    .append(summary.getCrossModuleConflictCount())
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
        final long crossModuleConflicts = summaries.stream().mapToLong(
                ReactorReportSummary::getCrossModuleConflictCount).sum();
        final long processed = summaries.size();
        return "<section class=\"card\"><h2>Summary</h2><table>"
                + "<thead><tr><th>Status</th><th>Progress</th>"
                + "<th>Reactors</th><th>Modules</th>"
                + "<th>Dependencies</th><th>Internal conflicts</th>"
                + "<th>Cross-module conflicts</th>"
                + "<th>Failure reason</th></tr></thead><tbody><tr><td class=\""
                + state + "\">" + state + "</td><td>" + processed
                + "/" + total + "</td><td>" + processed
                + "</td><td>" + modules + "</td><td>"
                + dependencies + "</td><td>" + internalConflicts
                + "</td><td>" + crossModuleConflicts
                + "</td><td>" + escape(failureReason)
                + "</td></tr></tbody></table></section>";
    }

    private String metadataRow(
            final String field,
            final String value) {
        return "<tr><th>" + escape(field) + "</th><td><code>"
                + escape(value) + "</code></td></tr>";
    }

    private void writeReactor(
            final Path target,
            final ReactorTreeResult reactor,
            final Path groupingCache) throws IOException {
        final Map<ModuleTreeResult, List<ConflictRow>> internal =
                internalConflictRows(reactor, groupingCache);
        final List<ConflictRow> crossModule =
                crossModuleConflictRows(reactor, groupingCache);
        final List<IssueRow> issues = issueRows(reactor);
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
                .append(reactorMetadata(reactor,
                        internal.values().stream()
                                .mapToLong(List::size).sum(),
                        crossModule.size(), issues.size()))
                .append(moduleMetadata(reactor, internal,
                        crossModule))
                .append(issueTable(issues));
        appendConflictTable(body, "跨模块依赖冲突",
                crossModule, true);
        appendModuleTabs(body, reactor.getModules(), internal);
        });
    }

    private String reactorMetadata(
            final ReactorTreeResult reactor,
            final long internalConflictCount,
            final long crossModuleConflictCount,
            final int issueCount) {
        final long dependencyCount = reactor.getModules()
                .stream().mapToLong(module -> module
                        .getOccurrences().size()).sum();
        final HtmlSink value = HtmlSink.memory();
        value.append("<section class=\"card\">")
                .append("<h2>Reactor metadata</h2><table>")
                .append("<thead><tr><th>Coordinate</th>")
                .append("<th>Root POM</th><th>Analysis mode</th>")
                .append("<th>Status</th><th>Modules</th>")
                .append("<th>Dependencies</th>")
                .append("<th>Internal conflicts</th>")
                .append("<th>Cross-module conflicts</th>")
                .append("<th>Issues</th></tr></thead><tbody><tr><td><code>")
                .append(escape(reactor.getReactor()
                        .getCoordinate()))
                .append("</code></td><td><code>")
                .append(escape(reactor.getReactor()
                        .getRootPom().toString()))
                .append("</code></td><td>")
                .append(reactor.getReactor().isRootSelected()
                        ? "FULL_REACTOR" : "BOUNDED_MODULE")
                .append("</td><td class=\"")
                .append(reactor.getStatus()).append("\">")
                .append(reactor.getStatus()).append("</td><td>")
                .append(reactor.getModules().size())
                .append("</td><td>").append(dependencyCount)
                .append("</td><td>").append(internalConflictCount)
                .append("</td><td>").append(crossModuleConflictCount)
                .append("</td><td>").append(issueCount)
                .append("</td></tr></tbody></table></section>");
        return value.toString();
    }

    private String moduleMetadata(
            final ReactorTreeResult reactor,
            final Map<ModuleTreeResult,
                    List<ConflictRow>> internal,
            final List<ConflictRow> crossModule) {
        final HtmlSink value = HtmlSink.memory();
        value.append("<section class=\"card\"><h2>Module metadata</h2>")
                .append("<table><thead><tr><th>Module</th><th>POM</th>")
                .append("<th>Status</th><th>Dependencies</th>")
                .append("<th>Internal conflicts</th>")
                .append("<th>Cross-module conflicts</th>")
                .append("<th>Issues</th></tr></thead>")
                .append("<tbody>");
        for (ModuleTreeResult module : reactor.getModules()) {
            final long crossCount = crossModule.stream()
                    .filter(row -> row.modules()
                            .contains(module.getCoordinate()))
                    .count();
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
                    .append(internal.get(module).size())
                    .append("</td><td>").append(crossCount)
                    .append("</td><td>").append(issueCount)
                    .append("</td></tr>");
        }
        return value.append("</tbody></table></section>")
                .toString();
    }

    private void appendModuleTabs(
            final HtmlSink body,
            final List<ModuleTreeResult> modules,
            final Map<ModuleTreeResult,
                    List<ConflictRow>> conflicts) {
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
                    conflicts.get(modules.get(index)), index == 0);
        }
        body.append("</section>");
    }

    private void appendModulePanel(
            final HtmlSink body,
            final ModuleTreeResult module,
            final List<ConflictRow> conflicts,
            final boolean selected) {
        final String id = moduleTabId(module);
        body.append("<section role=\"tabpanel\" id=\"module-panel-")
                .append(id).append("\" aria-labelledby=\"module-tab-")
                .append(id).append("\"")
                .append(selected ? "" : " hidden")
                .append("><h2>")
                .append(escape(module.getCoordinate()))
                .append("</h2>");
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
                .append("<th>Evidence</th></tr></thead><tbody>");
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

    private List<ConflictRow> crossModuleConflictRows(
            final ReactorTreeResult reactor,
            final Path groupingCache) throws IOException {
        final List<ConflictRow> rows = new ArrayList<>();
        final List<CrossModuleVersionIssue> issues = groupingCache == null
                ? new CrossModuleVersionAnalyzer().analyze(reactor)
                : new CrossModuleVersionAnalyzer().analyze(
                        reactor, groupingCache);
        for (CrossModuleVersionIssue issue : issues) {
            rows.add(crossModuleConflict(issue));
        }
        rows.sort(Comparator.comparing(ConflictRow::module)
                .thenComparing(ConflictRow::dependency));
        return rows;
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

    private ConflictRow crossModuleConflict(
            final CrossModuleVersionIssue issue) {
        final Set<String> modules = new LinkedHashSet<>();
        final Set<String> scopes = new LinkedHashSet<>();
        final Set<String> versions = new LinkedHashSet<>();
        final HtmlSink html = HtmlSink.memory();
        final HtmlSink text = HtmlSink.memory();
        appendEvidenceTableStart(html, true);
        issue.getVersions().forEach(version -> {
            modules.add(version.getModule());
            scopes.add(version.getScope());
            versions.add(version.getVersion());
            for (VersionPath path : version.getPaths()) {
                for (VersionEvidence source
                        : path.getEvidence()) {
                    appendEvidenceRow(html, text, source,
                            version.getModule(), path, true);
                }
            }
        });
        html.append("</tbody></table>");
        final List<String> moduleValues = modules.stream()
                .sorted().toList();
        final List<String> scopeValues = scopes.stream()
                .sorted().toList();
        final String module = String.join(", ", moduleValues);
        final String scope = String.join(", ", scopeValues);
        final String version = versions.stream().sorted()
                .collect(java.util.stream.Collectors
                        .joining(", "));
        return new ConflictRow("CROSS_MODULE_RESOLUTION",
                module, moduleValues, issue.getKey().toString(),
                scope, scopeValues, version, html.toString(),
                conflictSearch("CROSS_MODULE_RESOLUTION",
                        module, issue.getKey().toString(), scope,
                        version, text.toString()));
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
        }
        return issues;
    }

    private int moduleIssueCount(
            final ModuleTreeResult module) {
        int count = module.getFailure().isBlank() ? 0 : 1;
        if (!module.isCompleteMediation()) {
            count++;
        }
        return count;
    }

    private String moduleStatus(
            final ModuleTreeResult module) {
        if (!module.getFailure().isBlank()) {
            return "FAILED";
        }
        return module.isCompleteMediation()
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
                    .append("<br><span class=\"muted\">")
                    .append(escape(item.getEvidence()))
                    .append("</span><br>")
                    .append(escape(item.getFallback()))
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
        String slug = id.replace("pom.xml", "")
                .replaceAll("[^A-Za-z0-9]+", "-")
                .replaceAll("^-|-$", "")
                .toLowerCase(Locale.ROOT);
        if (slug.isBlank()) {
            slug = "root";
        }
        return slug + "-" + stableHash(id) + ".html";
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
