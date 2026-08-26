package io.github.dependencyanalysis.tree;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/** Incremental offline HTML renderer for dependency tree diffs. */
final class TreeDiffReportRenderer {

    /** Diff report directory. */
    private static final String REPORT_DIRECTORY = "tree-diff-report";

    /** Shared browser helper. */
    private static final String COMMON_RESOURCE =
            "/io/github/dependencyanalysis/report/report-common.js";

    /** Diff browser application. */
    private static final String SCRIPT_RESOURCE =
            "/io/github/dependencyanalysis/tree/tree-diff-report.js";

    /** Diff report stylesheet. */
    private static final String STYLE_RESOURCE =
            "/io/github/dependencyanalysis/tree/tree-diff-report.css";

    /** Length used for abbreviated commit hashes. */
    private static final int SHORT_COMMIT_LENGTH = 12;

    /**
     * Initializes assets and publishes a RUNNING index shell.
     *
     * @param metadata command metadata
     * @param reactorCount expected Reactor count
     * @param output report output directory
     * @return active session
     * @throws IOException on publication failure
     */
    TreeDiffReportSession start(
            final TreeDiffReportMetadata metadata,
            final int reactorCount,
            final Path output) throws IOException {
        final Path normalized = output.toAbsolutePath().normalize();
        Files.createDirectories(normalized);
        final Path staging = normalized.resolve(".tree-diff-stage-"
                + UUID.randomUUID());
        try {
            final Path assets = staging.resolve("assets");
            Files.createDirectories(assets);
            Files.createDirectories(staging.resolve("reactors"));
            Files.writeString(assets.resolve("report-common.js"),
                    resource(COMMON_RESOURCE), StandardCharsets.UTF_8);
            Files.writeString(assets.resolve("tree-diff-report.js"),
                    resource(SCRIPT_RESOURCE), StandardCharsets.UTF_8);
            Files.writeString(assets.resolve("tree-report.css"),
                    resource(STYLE_RESOURCE), StandardCharsets.UTF_8);
            replaceDirectory(staging,
                    normalized.resolve(REPORT_DIRECTORY));
        } catch (Exception exception) {
            deleteTree(staging);
            throw exception;
        }
        publishIndex(metadata, normalized, List.of(), reactorCount,
                TreeDiffReportState.RUNNING, "");
        return new TreeDiffReportSession(this, metadata,
                normalized, reactorCount);
    }

    /**
     * Publishes one Reactor data directory and HTML page.
     *
     * @param metadata command metadata
     * @param output report output directory
     * @param reactor Reactor result
     * @return published index summary
     * @throws IOException on publication failure
     */
    TreeDiffReactorSummary publishReactor(
            final TreeDiffReportMetadata metadata,
            final Path output,
            final TreeDiffReactorResult reactor) throws IOException {
        final String file = TreeReportFileName.of(reactor.reactorKey());
        final String stem = file.substring(0, file.length() - ".html".length());
        final Path reactors = output.resolve(REPORT_DIRECTORY)
                .resolve("reactors");
        Files.createDirectories(reactors);
        final Path staging = reactors.resolve("." + stem + "-stage-"
                + UUID.randomUUID());
        final Path stagingData = staging.resolve(stem + "-data");
        try {
            Files.createDirectories(stagingData);
            final TreeDiffReportDataWriter.TreeDiffReportData data =
                    new TreeDiffReportDataWriter().write(reactor, metadata,
                            stagingData, stem + "-data");
            final String html = reactorHtml(reactor,
                    data.manifest().toJson());
            final Path stagedPage = staging.resolve(file);
            Files.writeString(stagedPage, html, StandardCharsets.UTF_8);
            replaceDirectory(stagingData,
                    reactors.resolve(stem + "-data"));
            writeAtomic(reactors.resolve(file), html);
            final int comparable = (int) reactor.modules().stream()
                    .filter(module -> module.comparisonStatus()
                            == TreeDiffComparisonStatus.COMPARABLE).count();
            final int mismatch = (int) reactor.modules().stream()
                    .filter(module -> module.comparisonStatus()
                            == TreeDiffComparisonStatus.STRUCTURE_MISMATCH)
                    .count();
            final int unavailable = (int) reactor.modules().stream()
                    .filter(module -> module.comparisonStatus()
                            == TreeDiffComparisonStatus.UNAVAILABLE).count();
            return new TreeDiffReactorSummary(reactor.reactorKey(), file,
                    reactor.comparisonStatus(), reactor.metrics(),
                    reactor.modules().size(), comparable, mismatch,
                    unavailable, data.shardCount(), data.shardBytes(),
                    reactor.issues(), data.technicalWarnings());
        } finally {
            deleteTree(staging);
        }
    }

    /**
     * Atomically refreshes the lightweight repository index.
     *
     * @param metadata command metadata
     * @param output report output directory
     * @param summaries published Reactor summaries
     * @param totalReactors expected Reactor count
     * @param state lifecycle state
     * @param failure failure message
     * @throws IOException on publication failure
     */
    void publishIndex(
            final TreeDiffReportMetadata metadata,
            final Path output,
            final List<TreeDiffReactorSummary> summaries,
            final int totalReactors,
            final TreeDiffReportState state,
            final String failure) throws IOException {
        writeAtomic(output.resolve("index.html"), indexHtml(metadata,
                summaries, totalReactors, state, failure));
    }

    private String indexHtml(
            final TreeDiffReportMetadata metadata,
            final List<TreeDiffReactorSummary> summaries,
            final int totalReactors,
            final TreeDiffReportState state,
            final String failure) {
        TreeDiffMetrics totals = TreeDiffMetrics.ZERO;
        int mismatch = 0;
        int unavailable = 0;
        for (TreeDiffReactorSummary summary : summaries) {
            totals = totals.plus(summary.metrics());
            mismatch += summary.structureMismatchModules();
            unavailable += summary.unavailableModules();
        }
        final StringBuilder html = new StringBuilder(documentStart(
                "Dependency Tree Diff"));
        html.append("<main><h1>Dependency Tree Diff</h1>")
                .append("<p class=\"status status-")
                .append(state.name().toLowerCase())
                .append("\">Report state: <strong>")
                .append(state).append("</strong></p>")
                .append("<p>Reactor progress: ")
                .append(summaries.size()).append(" / ")
                .append(totalReactors).append("</p>");
        if (!failure.isBlank()) {
            html.append("<p class=\"error\">")
                    .append(escape(failure)).append("</p>");
        }
        html.append("<section><h2>Comparison metadata</h2><table><tbody>")
                .append(row("Baseline", side(metadata.baseline())))
                .append(row("Target", side(metadata.target())))
                .append(row("Analysis path",
                        displayPath(metadata.analysisPath())))
                .append(row("Scopes", String.join(", ", metadata.scopes())))
                .append(row("Maven", metadata.runtime().getExecutable()
                        + " (" + metadata.runtime().getVersion() + ")"))
                .append(row("maven-dependency-plugin",
                        metadata.dependencyPluginVersion()))
                .append("</tbody></table></section>")
                .append("<section><h2>Dependency summary</h2>")
                .append(metrics(totals, mismatch, unavailable))
                .append("</section><section><h2>Reactors</h2>")
                .append("<table><thead><tr><th scope=\"col\">Reactor</th>")
                .append("<th scope=\"col\">Status</th>")
                .append("<th scope=\"col\">Modules</th>")
                .append("<th scope=\"col\">Issues</th>")
                .append("<th scope=\"col\">Actions</th></tr></thead><tbody>");
        for (TreeDiffReactorSummary summary : summaries) {
            html.append("<tr><td><code>")
                    .append(escape(summary.reactorKey()))
                    .append("</code></td><td>")
                    .append(badge(summary.comparisonStatus().name(),
                            summary.comparisonStatus().name()))
                    .append("</td><td>").append(summary.moduleCount())
                    .append("</td><td>").append(summary.issues().size())
                    .append("</td><td><a class=\"table-action\" href=\"")
                    .append(REPORT_DIRECTORY).append("/reactors/")
                    .append(escape(summary.file()))
                    .append("\">View Reactor</a></td></tr>");
        }
        if (summaries.isEmpty()) {
            html.append("<tr><td colspan=\"5\" class=\"muted\">")
                    .append("No Reactor page has been published yet.")
                    .append("</td></tr>");
        }
        html.append("</tbody></table></section>");
        final List<String> warnings = summaries.stream()
                .flatMap(summary -> summary.technicalWarnings().stream())
                .toList();
        if (!warnings.isEmpty()) {
            html.append("<section><h2>Technical warnings</h2><ul>");
            warnings.forEach(warning -> html.append("<li>")
                    .append(escape(warning)).append("</li>"));
            html.append("</ul></section>");
        }
        return html.append("</main></body></html>").toString();
    }

    private String reactorHtml(
            final TreeDiffReactorResult reactor,
            final String manifest) {
        final StringBuilder html = new StringBuilder(documentStart(
                "Dependency Tree Diff — " + reactor.reactorKey()));
        html.append("<main data-tree-diff-root><nav>"
                + "<a href=\"../../index.html\">")
                .append("← Back to index</a></nav>"
                        + "<h1>Dependency Tree Diff</h1>")
                .append("<p><strong>Reactor:</strong> <code>")
                .append(escape(reactor.reactorKey()))
                .append("</code></p><p><strong>Status:</strong> ")
                .append(badge(reactor.comparisonStatus().name(),
                        reactor.comparisonStatus().name()))
                .append("</p><section><h2>Module summary</h2>")
                .append("<div class=\"table-toolbar\"><label>Page size ")
                .append("<select id=\"module-page-size\"><option>10</option>")
                .append("<option>50</option><option>100</option></select>")
                .append("</label><span id=\"module-range\"></span></div>")
                .append("<div class=\"table-scroll\">"
                        + "<table id=\"module-summary\">")
                .append("<thead><tr><th scope=\"col\">Module</th>")
                .append("<th scope=\"col\">Status</th>")
                .append("<th scope=\"col\">Version changed</th>")
                .append("<th scope=\"col\">Added</th>")
                .append("<th scope=\"col\">Removed</th>")
                .append("<th scope=\"col\">Resolved version unchanged</th>")
                .append("<th scope=\"col\">Scope changed</th>")
                .append("</tr></thead>")
                .append("<tbody></tbody></table></div>")
                .append(pager("module"))
                .append("</section><section id=\"module-detail\">")
                .append("<h2>Module detail</h2><div class=\"combobox\" ")
                .append("data-combobox=\"module-selector\">")
                .append("<label for=\"module-selector\">Module</label>")
                .append("<input id=\"module-selector\" role=\"combobox\" ")
                .append("aria-autocomplete=\"list\" aria-expanded=\"false\" ")
                .append("autocomplete=\"off\"><ul id=\"module-options\" ")
                .append("role=\"listbox\"></ul></div>")
                .append("<div id=\"metric-cards\" class=\"metric-grid\"></div>")
                .append("<h3>Comparison table</h3><div class=\"filters\">")
                .append("<div class=\"combobox\" "
                        + "data-combobox=\"dependency-selector\">")
                .append("<label for=\"dependency-filter\">Dependency</label>")
                .append("<input id=\"dependency-filter\" role=\"combobox\" ")
                .append("aria-autocomplete=\"list\" aria-expanded=\"false\" ")
                .append("autocomplete=\"off\"><ul id=\"dependency-options\" ")
                .append("role=\"listbox\"></ul></div>")
                .append("<label>Change type <select id=\"change-filter\">")
                .append("<option value=\"ALL\">All</option>")
                .append("<option value=\"VERSION_CHANGED\">"
                        + "Version changed</option>")
                .append("<option value=\"ADDED\">Added</option>")
                .append("<option value=\"REMOVED\">Removed</option>")
                .append("<option value=\"RESOLVED_UNCHANGED\">")
                .append("Resolved version unchanged</option>")
                .append("<option value=\"SCOPE_CHANGED\">"
                        + "Scope changed</option>")
                .append("</select></label><label>Page size ")
                .append("<select id=\"dependency-page-size\">"
                        + "<option>10</option>")
                .append("<option>50</option><option>100</option>"
                        + "</select></label>")
                .append("</div><p id=\"dependency-range\"></p>")
                .append("<div id=\"dependency-error\" class=\"error\" "
                        + "hidden></div>")
                .append("<div class=\"table-scroll\">"
                        + "<table id=\"dependency-table\">")
                .append("<thead><tr><th scope=\"col\">Dependency</th>")
                .append("<th scope=\"col\">Version</th>")
                .append("<th scope=\"col\">Scope</th>")
                .append("<th scope=\"col\">Direct dependency</th>")
                .append("<th scope=\"col\">Change type</th>")
                .append("<th scope=\"col\">Actions</th></tr></thead>")
                .append("<tbody></tbody></table></div>")
                .append(pager("dependency"))
                .append("<section id=\"tree-comparison\" tabindex=\"0\">")
                .append("<h3>Dependency tree comparison</h3>")
                .append("<div class=\"tree-pair\"><article><h4>Baseline</h4>")
                .append("<pre id=\"baseline-tree\">"
                        + "Select a Module.</pre></article>")
                .append("<article><h4>Target</h4><pre id=\"target-tree\">")
                .append("Select a Module.</pre></article></div></section>")
                .append("<section><h3>Issues</h3>"
                        + "<ul id=\"module-issues\"></ul>")
                .append("</section></section><noscript>"
                        + "This report requires JavaScript.")
                .append("</noscript><script type=\"application/json\" ")
                .append("id=\"tree-diff-manifest\">")
                .append(manifest).append("</script>")
                .append("<script src=\"../assets/report-common.js\"></script>")
                .append("<script src=\"../assets/"
                        + "tree-diff-report.js\"></script>")
                .append("</main></body></html>");
        return html.toString();
    }

    private String documentStart(final String title) {
        return "<!doctype html><html lang=\"en\"><head>"
                + "<meta charset=\"utf-8\"><meta name=\"viewport\" "
                + "content=\"width=device-width,initial-scale=1\">"
                + "<title>" + escape(title) + "</title>"
                + "<link rel=\"stylesheet\" href=\""
                + (title.startsWith("Dependency Tree Diff — ")
                ? "../assets/tree-report.css"
                : REPORT_DIRECTORY + "/assets/tree-report.css")
                + "\"></head><body>";
    }

    private String pager(final String prefix) {
        return "<div class=\"pager\"><button id=\"" + prefix
                + "-previous\" type=\"button\">Previous</button>"
                + "<span id=\"" + prefix + "-page\"></span>"
                + "<button id=\"" + prefix
                + "-next\" type=\"button\">Next</button></div>";
    }

    private String metrics(
            final TreeDiffMetrics metrics,
            final int mismatch,
            final int unavailable) {
        return "<div class=\"metric-grid\">"
                + metric("Version changed", metrics.versionChanged())
                + metric("Added", metrics.added())
                + metric("Removed", metrics.removed())
                + metric("Resolved version unchanged",
                metrics.resolvedUnchanged())
                + metric("Scope changed", metrics.scopeChanged())
                + metric("Structure mismatch Modules", mismatch)
                + metric("Unavailable Modules", unavailable)
                + "</div>";
    }

    private String metric(final String label, final int value) {
        return "<div class=\"metric\"><span>" + escape(label)
                + "</span><strong>" + value + "</strong></div>";
    }

    private String row(final String label, final String value) {
        return "<tr><th>" + escape(label) + "</th><td><code>"
                + escape(value) + "</code></td></tr>";
    }

    private String side(final TreeDiffSideMetadata side) {
        return side.displayName() + " @ " + shortCommit(side.commit())
                + ("current-workspace".equals(side.kind())
                ? side.dirty() ? " (dirty)" : " (clean)" : "");
    }

    private String shortCommit(final String commit) {
        return commit.length() <= SHORT_COMMIT_LENGTH
                ? commit : commit.substring(0, SHORT_COMMIT_LENGTH);
    }

    private String displayPath(final Path path) {
        return path.toString().isBlank() ? "."
                : path.toString().replace('\\', '/');
    }

    private String badge(final String code, final String text) {
        return "<span class=\"badge badge-"
                + escape(code.toLowerCase().replace('_', '-')) + "\">"
                + escape(text) + "</span>";
    }

    private String resource(final String name) throws IOException {
        try (InputStream input = getClass().getResourceAsStream(name)) {
            if (input == null) {
                throw new IOException("Missing report resource: " + name);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void writeAtomic(final Path target, final String content)
            throws IOException {
        Files.createDirectories(target.getParent());
        final Path temporary = target.resolveSibling(target.getFileName()
                + ".tmp-" + UUID.randomUUID());
        Files.writeString(temporary, content, StandardCharsets.UTF_8);
        move(temporary, target);
    }

    private void replaceDirectory(final Path source, final Path target)
            throws IOException {
        final Path backup = target.resolveSibling(target.getFileName()
                + ".backup-" + UUID.randomUUID());
        boolean backedUp = false;
        try {
            if (Files.exists(target)) {
                move(target, backup);
                backedUp = true;
            }
            move(source, target);
            if (backedUp) {
                deleteTree(backup);
            }
        } catch (IOException failure) {
            if (!Files.exists(target) && backedUp && Files.exists(backup)) {
                try {
                    move(backup, target);
                } catch (IOException rollback) {
                    failure.addSuppressed(rollback);
                }
            }
            throw failure;
        }
    }

    private void move(final Path source, final Path target)
            throws IOException {
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteTree(final Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder())
                    .toList()) {
                Files.deleteIfExists(path);
            }
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
}
