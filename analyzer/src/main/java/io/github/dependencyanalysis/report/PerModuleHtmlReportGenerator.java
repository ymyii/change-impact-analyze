package io.github.dependencyanalysis.report;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.DecompileComparisonStatus;
import io.github.dependencyanalysis.bytecode.DecompileComparisonSummary;
import io.github.dependencyanalysis.bytecode.SsaComparisonEvidence;
import io.github.dependencyanalysis.bytecode.SsaComparisonStatus;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.engine.CallGraphStats;
import io.github.dependencyanalysis.callgraph.scope.ClassOwnership;
import io.github.dependencyanalysis.callgraph.scope.DuplicateClassResolution;
import io.github.dependencyanalysis.callgraph.model.MethodId;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.impact.AnalysisRunResult;
import io.github.dependencyanalysis.impact.BoundChangePoint;
import io.github.dependencyanalysis.impact.ChangePointDisposition;
import io.github.dependencyanalysis.impact.DependencyBoundarySnapshot;
import io.github.dependencyanalysis.impact.DependencyUpgradeKey;
import io.github.dependencyanalysis.impact.ImpactPath;
import io.github.dependencyanalysis.impact.ModuleAnalysisResult;
import io.github.dependencyanalysis.impact.ModuleAnalysisStatus;
import io.github.dependencyanalysis.impact.QueryNode;
import io.github.dependencyanalysis.impact.pruning.ImpactPathPruningSummary;
import io.github.dependencyanalysis.impact.StructuralReferencePath;
import io.github.dependencyanalysis.preflight.PreflightReport;
import io.github.dependencyanalysis.preflight.PreflightResult;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.MavenDependencyPluginRuntime;
import io.github.dependencyanalysis.runtime.MavenRuntimeDescriptor;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Atomically publishes the English multi-page Impact HTML report. */
public final class PerModuleHtmlReportGenerator {

    /** SHA-256 algorithm. */
    private static final String SHA_256 = "SHA-256";

    /** Stable hash prefix length. */
    private static final int HASH_LENGTH = 12;


    /** Four MiB target limit for one browser data shard. */
    private static final int MAX_SHARD_BYTES = 4 * 1024 * 1024;

    /** Script-safe JSON writer for browser-resident report data. */
    private static final JsonFactory JSON = ScriptSafeJson.factory();

    /** Command diagnostic destination. */
    private final DiagnosticLog diagnostics;

    /** Browser data shard target limit. */
    private final int maxShardBytes;

    /** Inlined offline Affected Paths interaction script. */
    private static final String AFFECTED_PATHS_SCRIPT = resource(
            "/io/github/dependencyanalysis/report/affected-paths.js");

    /** Inlined offline Changed Members interaction script. */
    private static final String CHANGED_MEMBERS_SCRIPT = resource(
            "/io/github/dependencyanalysis/report/changed-members.js");

    /** Shared offline CSS. */
    private static final String CSS =
            ":root{color-scheme:light;--page:#f6f8fb;--surface:#fff;"
            + "--surface-alt:#f7f9fc;--line:#d7dee8;--line-strong:#b8c2cf;"
            + "--ink:#172033;--muted:#5b6678;--accent:#175cd3;"
            + "--focus:#84adff;--success-bg:#dcfae6;--success:#05603a;"
            + "--warn-bg:#fef0c7;--warn:#7a2e0e;--danger-bg:#fee4e2;"
            + "--danger:#b42318;--info-bg:#e0f2fe;--info:#075985;"
            + "--radius:10px;--control-height:36px}*{box-sizing:border-box}"
            + "body{margin:0;color:var(--ink);background:var(--page);"
            + "font:14px/1.5 system-ui,-apple-system,sans-serif}"
            + "a{color:var(--accent)}header{border-bottom:1px solid"
            + " var(--line);padding:14px 24px;background:var(--surface)}"
            + ".breadcrumbs,.siblings{display:flex;gap:8px;flex-wrap:wrap}"
            + ".siblings{margin-top:8px}.layout{display:grid;grid-template-"
            + "columns:240px minmax(0,1fr);gap:clamp(18px,2vw,32px);"
            + "width:100%;margin:0 auto;padding:clamp(14px,2vw,28px)}"
            + ".toc{position:sticky;top:18px;"
            + "align-self:start;border:1px solid var(--line);"
            + "border-radius:var(--radius);padding:14px;"
            + "background:var(--surface)}.toc ul{margin:8px 0 0;"
            + "padding-left:20px}.toc a[aria-current=\"page\"]{font-weight:700}"
            + "main{min-width:0}h1{margin-top:0}h2{margin-top:34px;"
            + "padding-bottom:5px}section{min-width:0;"
            + "background:var(--surface);"
            + "border:1px solid var(--line);border-radius:var(--radius);"
            + "padding:18px;margin:18px 0;box-shadow:0 1px 2px #1018280d}"
            + "section h2{margin-top:0}table{border-collapse:separate;"
            + "border-spacing:0;width:100%;margin:12px 0}th,td{"
            + "border-bottom:1px solid var(--line);padding:7px 9px;"
            + "text-align:left;vertical-align:top}thead th{position:sticky;"
            + "top:0;z-index:1;background:var(--surface-alt);"
            + "border-right:1px solid var(--line);font-weight:650;"
            + "white-space:nowrap}tbody tr:nth-child(even){"
            + "background:#f9fafc}tbody tr:hover{background:#eef4ff}"
            + "code,pre,.method-cell,.member-cell,.dependency-cell,"
            + ".path-sequence{font-family:ui-monospace,SFMono-Regular,"
            + "Consolas,monospace}code{overflow-wrap:anywhere}pre{"
            + "overflow:auto;"
            + "background:#0b1220;color:#e5e7eb;border:1px solid #273244;"
            + "border-radius:8px;padding:12px}.card{border:1px solid"
            + " var(--line);"
            + "border-radius:var(--radius);padding:14px;margin:14px 0}"
            + ".ok{color:var(--success)}.warn{color:var(--warn)}"
            + ".fail{color:var(--danger)}.muted{color:var(--muted)}"
            + "details{margin:10px 0;padding:9px;border:1px solid var(--line);"
            + "border-radius:8px}.badge{display:inline-flex;align-items:center;"
            + "padding:2px 8px;border-radius:999px;background:var(--info-bg);"
            + "color:var(--info);font-size:11px;font-weight:750;"
            + "white-space:nowrap}.badge.impact{background:var(--info-bg);"
            + "color:var(--info)}.badge.structural{background:"
            + "var(--success-bg);"
            + "color:var(--success)}.badge.kind{background:#ede9fe;"
            + "color:#5b21b6}.diff{white-space:pre;tab-size:4;margin:0}"
            + ".report-controls{display:flex;gap:12px;align-items:end;"
            + "flex-wrap:wrap;margin:12px 0}.report-control{display:grid;"
            + "gap:4px;color:var(--muted);font-size:12px}"
            + ".search-fields{display:contents}"
            + ".report-control input,.report-control select,button,"
            + ".pagination input{height:var(--control-height);border:1px solid"
            + " var(--line-strong);border-radius:8px;background:var(--surface);"
            + "color:var(--ink);font:inherit;padding:6px 10px}"
            + "button{cursor:pointer}button:disabled{cursor:not-allowed;"
            + "opacity:.5}button:hover:not(:disabled){background:#eef4ff}"
            + "button:focus-visible,input:focus-visible,select:focus-visible,"
            + "a:focus-visible,summary:focus-visible{outline:3px solid"
            + " var(--focus);outline-offset:2px}.table-scroll{overflow-x:auto;"
            + "border:1px solid var(--line);border-radius:var(--radius);"
            + "background:var(--surface)}.table-scroll table{margin:0}"
            + ".path-table{min-width:1320px}.changed-member-table{"
            + "min-width:1100px}.path-sequence{min-width:340px;"
            + "inline-size:clamp(340px,42vw,640px);max-inline-size:640px;"
            + "white-space:normal;overflow-wrap:anywhere}.numeric{"
            + "text-align:right!important;"
            + "font-variant-numeric:tabular-nums}.pagination{display:flex;"
            + "gap:8px;align-items:center;flex-wrap:wrap;margin:12px 0}"
            + ".pagination input{width:76px}.path-diff-row td{padding:0}"
            + ".path-diff{background:#0b1220;padding:12px!important}"
            + ".diff-code{display:block}"
            + ".diff-line{display:block;min-height:1.55em;padding:0 6px}"
            + ".diff-add,.diff-file-new{color:#86efac;background:#052e1b}"
            + ".diff-delete,.diff-file-old{color:#fca5a5;background:#3f1115}"
            + ".diff-hunk{color:#93c5fd;background:#172554}"
            + ".diff-file-new,.diff-file-old,.diff-hunk{font-weight:700}"
            + ".hidden{display:none!important}summary{cursor:pointer;"
            + "font-weight:600}.sequence{font-size:14px}caption{padding:8px;"
            + "text-align:left}"
            + "@media(max-width:800px){.layout{display:block;padding:14px}"
            + ".toc{position:static;margin-bottom:20px}"
            + "header{padding:12px 14px}section{padding:13px}"
            + ".table-scroll{max-width:100%}}";

    /** @param log command diagnostic destination */
    public PerModuleHtmlReportGenerator(final DiagnosticLog log) {
        this(log, MAX_SHARD_BYTES);
    }

    /**
     * Package-private constructor with a test shard limit.
     *
     * @param log command diagnostic destination
     * @param shardBytes maximum target bytes for one normal shard
     */
    PerModuleHtmlReportGenerator(
            final DiagnosticLog log,
            final int shardBytes) {
        diagnostics = java.util.Objects.requireNonNull(log, "log");
        if (shardBytes <= 0) {
            throw new IllegalArgumentException("shardBytes must be positive");
        }
        maxShardBytes = shardBytes;
    }

    /**
     * Writes pages to staging and atomically replaces command-owned output.
     *
     * @param run analysis result
     * @param preflight preflight report
     * @param maven selected Maven runtime
     * @param plugin embedded Dependency Plugin runtime
     * @param javaRuntime target JDK
     * @param output Overall Index file
     */
    public void generate(
            final AnalysisRunResult run,
            final PreflightReport preflight,
            final MavenRuntimeDescriptor maven,
            final MavenDependencyPluginRuntime plugin,
            final JavaRuntimeDescriptor javaRuntime,
            final Path output) {
        final Path absolute = output.toAbsolutePath().normalize();
        final Path parent = absolute.getParent() == null
                ? Path.of(".").toAbsolutePath().normalize()
                : absolute.getParent();
        final String ownedName = moduleDirectoryName(absolute);
        Path staging = null;
        try {
            Files.createDirectories(parent);
            staging = Files.createTempDirectory(parent, ".cia-report-");
            final Path modules = staging.resolve(ownedName);
            Files.createDirectories(modules);
            final ModuleRuntime moduleRuntime = new ModuleRuntime(
                    javaRuntime.getVersion(), maven.getVersion().toString(),
                    maven.getSource().toString(),
                    entrypointSelectionLabel(run),
                    "embedded " + plugin.getVersion(),
                    run.getCallGraphAlgorithm());
            final Map<ModuleAnalysisResult, ModulePages> pages =
                    writeModulePages(run, modules,
                            absolute.getFileName().toString(), moduleRuntime);
            final OverallContext context = new OverallContext(
                    maven, plugin, javaRuntime, ownedName, pages);
            writeOverallPage(staging.resolve(absolute.getFileName()),
                    run, preflight, context);
            publish(staging, absolute, ownedName);
        } catch (IOException exception) {
            throw new ReportException(
                    "Failed to publish HTML report: " + absolute,
                    exception);
        } finally {
            deleteTree(staging);
        }
    }

    private Map<ModuleAnalysisResult, ModulePages> writeModulePages(
            final AnalysisRunResult run,
            final Path directory,
            final String overallFile,
            final ModuleRuntime runtime) throws IOException {
        final Map<ModuleAnalysisResult, ModulePages> result =
                new LinkedHashMap<>();
        for (ModuleAnalysisResult module : run.getModuleResults()) {
            if (module.getStatus() == ModuleAnalysisStatus.SKIPPED) {
                continue;
            }
            final String base = moduleBase(module);
            final ModulePages pages = new ModulePages(
                    base + ".html", base + "-impact.html");
            final String dataDirectoryName = base + "-impact-data";
            final AffectedPathReportManifest manifest =
                    new AffectedPathReportDataWriter(
                            diagnostics, maxShardBytes).write(
                            module, directory.resolve(dataDirectoryName),
                            dataDirectoryName);
            writeModuleIndexPage(directory.resolve(pages.index()), module,
                    pages, overallFile, runtime);
            writeImpactPage(directory.resolve(pages.impact()), module,
                    pages, overallFile, manifest);
            result.put(module, pages);
        }
        return result;
    }

    private void writeOverallPage(
            final Path target,
            final AnalysisRunResult run,
            final PreflightReport preflight,
            final OverallContext context) throws IOException {
        writeDocument(target, "Impact Analysis Report", "Overall", "", "",
                overallToc(), body -> {
        final boolean semanticComparison = true;
        body
                .append("<h1 id=\"top\">Impact Analysis Report</h1>")
                .append("<section id=\"read\"><h2>How to read this report")
                .append("</h2><p>Start with the Modules table. Open a module ")
                .append("to review its summary, then use Affected Paths to ")
                .append("search application methods, inspect paths, and ")
                .append("review changed dependency members.</p>")
                .append("</section>")
                .append("<section id=\"limits\"><h2>Analysis scope and ")
                .append("limitations</h2><ul><li>The analysis follows ")
                .append("possible ")
                .append("Java calls from target application methods. Some ")
                .append("reported chains may not execute at runtime.</li>")
                .append("<li>Reflection and MethodHandle support uses the ")
                .append("relationships found by WALA; custom class loaders ")
                .append("may remain unresolved.</li><li>ServiceLoader ")
                .append("providers are ")
                .append("connected conservatively when a reachable load is ")
                .append("found.</li><li>Spring DI, AOP, annotation, XML and ")
                .append("configuration behavior is not completely modeled.")
                .append("</li><li>Swing, Applet and deployment-related JDK 8 ")
                .append("content is excluded.</li><li>Only target call ")
                .append("relationships are ")
                .append("built. Baseline artifacts supply dependency, ")
                .append("bytecode ")
                .append("and method-comparison evidence.</li></ul></section>");
        body.append("<p><strong>CHA JDK dispatch boundary:</strong> CHA does ")
                .append("not expand a virtual or interface call declared by ")
                .append("the JDK to non-JDK implementations. Callback, SPI, ")
                .append("lambda, collection implementation, and application ")
                .append("Thread or Runnable chains may therefore be omitted. ")
                .append("This predefined scope does not change Module ")
                .append("status.</p>");
        appendSemanticComparisonDisclosure(body, semanticComparison);
        appendTerminology(body, run.getCallGraphAlgorithm());
        body.append("<section id=\"run\"><h2>Run summary</h2><table>")
                .append(row("Status", statusText(run.getStatus().name())))
                .append(row("Analysis mode", run.getMode()))
                .append(row("Requested dependency scope",
                        run.getDependencyAnalysisScope().identifier()))
                .append(row("Changed-paths / full / fallback Modules",
                        dependencyScopeCounts(run)))
                .append(row("Real-IR / no-op external artifacts",
                        dependencyArtifactCounts(run)))
                .append(row("No-op / factory method nodes",
                        dependencyMethodCounts(run)))
                .append(row("Dangerous dependency transfers",
                        dangerousTransferCount(run)))
                .append(row("INCONCLUSIVE Module ratio",
                        inconclusiveRatio(run)))
                .append(row("Application entrypoint boundary",
                        entrypointSelectionLabel(run)))
                .append(row("Target JDK",
                        context.javaRuntime().getVersion()))
                .append(row("Apache Maven runtime",
                        context.maven().getVersion() + " ("
                                + context.maven().getSource() + ")"))
                .append(row("Maven Dependency Plugin",
                        "embedded " + context.plugin().getVersion()))
                .append(concurrencyRows(run))
                .append(row("Complete dependency changes",
                        run.getDependencyChanges().size()))
                .append(row("Conflicting duplicate classes",
                        duplicateConflictCount(run)))
                .append(row("Shadowed dependency changes",
                        shadowedChangeCount(run)))
                .append(row("Impact / structural records",
                        pathCount(run) + " / "
                                + structuralPathCount(run)))
                .append("</table>");
        appendStageMetrics(body, run.getStageElapsedMillis());
        body.append("<details><summary>Technical details</summary><table>")
                .append(row("Algorithm",
                        run.getCallGraphAlgorithm().identifier()
                                + (run.getCallGraphAlgorithm()
                                == CallGraphAlgorithm.K_OBJ
                                ? " (experimental)" : "")))
                .append(run.getCallGraphAlgorithm()
                        == CallGraphAlgorithm.K_OBJ
                        ? row("k-object depth", run.getKObjDepth()) : "")
                .append(row("WALA ReflectionOptions",
                        run.getCallGraphAlgorithm() == CallGraphAlgorithm.CHA
                                ? "not applied by cha (configured: "
                                + run.getReflectionOptions().identifier()
                                + ")"
                                : run.getReflectionOptions().identifier()))
                .append(row("JDK method model",
                        run.getJdkModel().identifier()))
                .append(row("Method body equivalence order",
                        "Decompiled Java first; normalized SSA on miss"))
                .append(pruningRows(run.getModuleResults()))
                .append(chaPruningBoundaryRow(run.getCallGraphAlgorithm()))
                .append(changeSelectionRows(run))
                .append(row("Entrypoint includes",
                        run.getEntrypointSelection().includes()))
                .append(row("Entrypoint excludes",
                        run.getEntrypointSelection().excludes()))
                .append(row("Maven executable",
                        context.maven().getExecutable()))
                .append(row("SSA matched / different / unknown / skipped",
                        ssaCounts(run.getModuleResults())))
                .append(row("Java identical / different / unknown",
                        decompileCounts(run.getModuleResults())))
                .append("</table></details>");
        appendSemanticComparisonEvidence(body, run.getModuleResults());
        body.append("</section><section id=\"modules\"><h2>Modules</h2>")
                .append("<table><tr><th>Module</th><th>Status</th>")
                .append("<th>Explanation</th><th>Complete dependency ")
                .append("changes</th>")
                .append("<th>Duplicate classes</th>")
                .append("<th>Shadowed changes</th>")
                .append("<th>Impact paths</th><th>Affected methods</th>")
                .append("</tr>");
        for (ModuleAnalysisResult module : run.getModuleResults()) {
            final ModulePages modulePages = context.pages().get(module);
            body.append("<tr><td>");
            if (modulePages == null) {
                body.append(escape(moduleLabel(module)));
            } else {
                body.append("<a href=\"")
                        .append(attribute(context.ownedName() + "/"
                                + modulePages.index()))
                        .append("\">")
                        .append(escape(moduleLabel(module)))
                        .append("</a>");
            }
            body.append("</td><td>")
                    .append(escape(statusText(module.getStatus().name())))
                    .append("</td><td>")
                    .append(escape(reasonText(module)))
                    .append("</td><td>")
                    .append(module.getUnit().getDependencyChanges().size())
                    .append("</td><td>")
                    .append(duplicateResolutions(module).size())
                    .append("</td><td>")
                    .append(shadowedChangeCount(module))
                    .append("</td><td>")
                    .append(module.getImpactPaths().size())
                    .append("</td><td>")
                    .append(affectedMethodCount(module))
                    .append("</td></tr>");
        }
        body.append("</table></section>");
        appendPreflight(body, preflight);
        });
    }

    private void appendSemanticComparisonDisclosure(
            final HtmlSink body,
            final boolean enabled) {
        body.append(SsaEvidenceHtmlRenderer.warning(enabled));
        body.append("<p class=\"warn\">Exact normalized Vineflower text ")
                .append("equality also suppresses method body changes.</p>");
        body.append("<p>Code comparisons are generated locally from dependency "
                + "bytecode and may differ from the original source code.</p>");
    }

    private void appendSemanticComparisonEvidence(
            final HtmlSink body,
            final List<ModuleAnalysisResult> modules) {
        body.append(SsaEvidenceHtmlRenderer.render(
                uniqueSsaComparisons(modules)));
        body.append(DecompileEvidenceHtmlRenderer.render(
                uniqueDecompileComparisons(modules)));
    }

    private String concurrencyRows(final AnalysisRunResult run) {
        return row("JAR comparisons in parallel",
                run.getActualJarDiffWorkers() + " (configured "
                        + run.getConfiguredJarDiffWorkers() + ")")
                + row("Impact queries in parallel",
                run.getActualImpactQueryWorkers() + " (configured "
                        + run.getConfiguredAnalysisParallelism() + ")")
                + row("Code comparisons in parallel",
                run.getActualDecompileWorkers() + " (configured "
                        + run.getConfiguredAnalysisParallelism() + ")");
    }

    private String changeSelectionRows(final AnalysisRunResult run) {
        return row("WALA", walaVersion())
                + row("Effective changed members", effectiveChangeCount(run))
                + row("Dependency includes",
                run.getDependencySelection().includes())
                + row("Dependency excludes",
                run.getDependencySelection().excludes());
    }

    private void writeModuleIndexPage(
            final Path target,
            final ModuleAnalysisResult module,
            final ModulePages pages,
            final String overallFile,
            final ModuleRuntime runtime) throws IOException {
        writeDocument(target, "Module summary", "Module Index",
                breadcrumbs(overallFile, module, pages.index(),
                        "Module Index"), siblingLinks(pages, "index"),
                moduleIndexToc(), body -> {
        body
                .append("<h1 id=\"top\">Module summary: ")
                .append(escape(moduleLabel(module)))
                .append("</h1><section id=\"summary\"><h2>Summary</h2>")
                .append("<p class=\"")
                .append(statusClass(module.getStatus().name()))
                .append("\"><strong>")
                .append(escape(statusText(module.getStatus().name())))
                .append(":</strong> ").append(escape(reasonText(module)))
                .append("</p><table>")
                .append(row("Affected application methods",
                        affectedMethodCount(module)))
                .append(row("Affected application classes",
                        affectedClassCount(module)))
                .append(row("Impact / structural",
                        module.getImpactPaths().size() + " / "
                                + module.getStructuralPaths().size()))
                .append(row("Complete dependency changes / selected "
                                + "effective members",
                        module.getUnit().getDependencyChanges().size()
                                + " / "
                                + module.getUnit().getChangePoints().size()))
                .append(row("Conflicting duplicate classes",
                        duplicateResolutions(module).size()))
                .append(row("Shadowed dependency changes",
                        shadowedChangeCount(module)))
                .append(row("Target JDK", runtime.jdkVersion()))
                .append(row("Apache Maven runtime", runtime.mavenVersion()))
                .append(row("Maven runtime source", runtime.mavenSource()))
                .append(row("Maven Dependency Plugin",
                        runtime.pluginVersion()))
                .append(row("Module analysis worker",
                        "1 (call relationship build and query are "
                                + "single-threaded)"))
                .append(row("Analysis elapsed", module.getElapsedMillis()
                        + " ms")).append("</table></section>");
        appendChangedMemberMetrics(body, module);
        body.append("<section id=\"scope\"><h2>Analysis scope</h2><p>")
                .append("Application module: <code>")
                .append(escape(module.getModuleId().getCoordinate().toString()))
                .append("</code>. Reactor dependencies: ")
                .append(module.getUnit().getReactorDependencyClasses().size())
                .append(". External dependencies: ")
                .append(module.getUnit().getTargetArtifacts().size())
                .append(". Entrypoint boundary: ")
                .append(escape(runtime.entrypointBoundary()))
                .append(".</p><details><summary>Technical details</summary>")
                .append("<ul><li>Application classes path: <code>")
                .append(escape(module.getUnit().getProjectClasses().toString()))
                .append("</code></li>");
        module.getUnit().getReactorDependencyClasses().forEach(path -> body
                .append("<li>Reactor dependency: <code>")
                .append(escape(path.toString())).append("</code></li>"));
        module.getUnit().getTargetArtifacts().forEach(artifact -> body
                .append("<li>External dependency: <code>")
                .append(escape(artifact.toString()))
                .append("</code></li>"));
        body.append("</ul></details></section>");
        appendDependencyBodyBoundary(body, module);
        appendDuplicateResolutions(body, module);
        body.append("<section id=\"metrics\"><h2>Runtime metrics</h2>")
                .append("<table>")
                .append(row("Entry methods", entrypointCount(module)))
                .append(row("Selected application classes",
                        selectedEntrypointClassCount(module)))
                .append(row("Call relationships", module.getCallGraphStats()
                        == null ? "Not available"
                        : module.getCallGraphStats().edgeCount()))
                .append("</table>");
        appendStageMetrics(body, module.getStageElapsedMillis());
        body.append("<details><summary>Technical details</summary><table>")
                .append(sessionRows(module))
                .append(row("Raw status", module.getStatus()))
                .append(row("Raw reason", module.getReason()))
                .append(row("Method body equivalence order",
                        "Decompiled Java first; normalized SSA on miss"))
                .append(pruningRows(List.of(module)))
                .append(chaPruningBoundaryRow(
                        runtime.callGraphAlgorithm()))
                .append(row("SSA matched / different / unknown / skipped",
                        ssaCounts(List.of(module))))
                .append(row("Java identical / different / unknown",
                        decompileCounts(List.of(module))))
                .append(row("Complete dependency changes / selected "
                                + "effective members",
                        module.getUnit().getDependencyChanges().size() + " / "
                                + module.getUnit().getChangePoints().size()))
                .append("</table></details>");
        body.append(SsaEvidenceHtmlRenderer.render(
                uniqueSsaComparisons(List.of(module))));
        body.append(DecompileEvidenceHtmlRenderer.render(
                uniqueDecompileComparisons(List.of(module))));
        body.append("</section><section id=\"limits\"><h2>Coverage limitations")
                .append("</h2>");
        final List<String> limitations = new ArrayList<>(
                module.getLimitations());
        if (runtime.callGraphAlgorithm() == CallGraphAlgorithm.CHA) {
            limitations.add("CHA Impact Path pruning uses caller-local "
                    + "receiver facts only; bridge parameters, factory "
                    + "returns, and other cross-method receiver flows may "
                    + "retain conservative false-positive paths. This "
                    + "predefined scope does not change the Module status.");
            limitations.add("CHA does not expand JDK-declared virtual or "
                    + "interface dispatch to non-JDK implementations; "
                    + "callback, SPI, lambda, collection implementation, "
                    + "and application Thread or Runnable chains may be "
                    + "omitted. This predefined scope does not change the "
                    + "Module status.");
        }
        module.getUnit().getJarDiffFailures().forEach(failure -> limitations
                .add("JAR member comparison unavailable for "
                        + jarLabel(failure.dependencyUpgradeKey()) + ": "
                        + failure.reason()));
        appendList(body, limitations,
                "No module-specific limitation was recorded.");
        body.append("</section>");
        });
    }

    private void appendDependencyBodyBoundary(
            final HtmlSink body,
            final ModuleAnalysisResult module) {
        final var selection = module.getUnit().getChangedPathSelection();
        final List<ArtifactCoord> realArtifacts = module.getUnit()
                .getTargetArtifacts().stream()
                .filter(value -> selection.policyFor(value)
                        == io.github.dependencyanalysis.impact
                        .DependencyMethodBodyPolicy.REAL_IR)
                .toList();
        final List<ArtifactCoord> noOpArtifacts = module.getUnit()
                .getTargetArtifacts().stream()
                .filter(value -> selection.policyFor(value)
                        == io.github.dependencyanalysis.impact
                        .DependencyMethodBodyPolicy.NO_OP)
                .toList();
        body.append("<section id=\"dependency-body-boundary\"><h2>")
                .append("Dependency method-body boundary</h2><p>")
                .append("All external classes, methods and resources remain ")
                .append("in AnalysisScope and CHA. The selected scope only ")
                .append("controls external method-body interpretation.</p>")
                .append("<table>")
                .append(row("Requested mode",
                        selection.requestedMode().identifier()))
                .append(row("Actual mode",
                        selection.actualMode().identifier()))
                .append(row("Fallback reason",
                        selection.fallbackReason().orElse("None")))
                .append(row("Real-IR external artifacts",
                        realArtifacts.size()))
                .append(row("No-op external artifacts",
                        noOpArtifacts.size()));
        final DependencyBoundarySnapshot metadata = boundary(module);
        body.append(row("Real / no-op / factory method nodes",
                        metadata.realExternalMethodNodes() + " / "
                                + metadata.noOpMethodNodes() + " / "
                                + metadata.factoryMethodNodes()))
                .append(row("Ancestor-retained external types / methods",
                        metadata.ancestorRetainedExternalTypeCount() + " / "
                                + metadata
                                .ancestorRetainedExternalMethodNodeCount()))
                .append(row("Pruned external method targets",
                        metadata.prunedExternalMethodTargetCount()))
                .append(row("JDK-declared dispatch targets pruned",
                        jdkDeclaredDispatchPrunedTargetCount(module)))
                .append("</table><h3>Changed dependency paths</h3>");
        if (selection.paths().isEmpty()) {
            body.append("<p>No reverse dependency path evidence was ")
                    .append("required for the actual mode.</p>");
        } else {
            body.append("<table><tr><th>Changed artifact</th>")
                    .append("<th>Module direct dependency to seed</th></tr>");
            selection.paths().forEach(path -> body.append("<tr><td><code>")
                    .append(escape(path.seed().toString()))
                    .append("</code></td><td><code>")
                    .append(escape(path.stablePath()))
                    .append("</code></td></tr>"));
            body.append("</table>");
        }
        appendArtifactPolicyList(body, "Real-IR artifacts", realArtifacts);
        appendArtifactPolicyList(body, "No-op artifacts", noOpArtifacts);
        body.append("<p><strong>Type-level exception:</strong> a no-op ")
                .append("artifact remains artifact-level no-op, but an ")
                .append("external type in the complete ancestor chain of ")
                .append("a PROJECT, reactor dependency, or selected ")
                .append("external class uses real IR for its reachable ")
                .append("concrete methods. JDK types are excluded.</p>");
        appendBoundaryEvidence(body, metadata);
        body.append("<p><strong>Interpretation:</strong> SUCCESS means no ")
                .append("Impact Path was found within the selected paths and ")
                .append("modeled boundaries. It does not prove that a no-op ")
                .append("dependency body contains no impact.</p></section>");
    }

    private void appendArtifactPolicyList(
            final HtmlSink body,
            final String title,
            final List<ArtifactCoord> artifacts) {
        body.append("<details><summary>").append(escape(title))
                .append(" (").append(artifacts.size())
                .append(")</summary><ul>");
        artifacts.forEach(value -> body.append("<li><code>")
                .append(escape(value.toString())).append("</code></li>"));
        if (artifacts.isEmpty()) {
            body.append("<li>None</li>");
        }
        body.append("</ul></details>");
    }

    private void appendBoundaryEvidence(
            final HtmlSink body,
            final DependencyBoundarySnapshot metadata) {
        body.append("<h3>Reached dependency body boundaries</h3>");
        if (metadata.bodyBoundaryHits().isEmpty()) {
            body.append("<p>No reachable no-op dependency body boundary ")
                    .append("was found.</p>");
        } else {
            body.append("<table><tr><th>Callee</th><th>Artifact</th>")
                    .append("<th>Context</th></tr>");
            metadata.bodyBoundaryHits().forEach(value -> body
                    .append("<tr><td><code>")
                    .append(escape(value.calleeMethod()))
                    .append("</code></td><td><code>")
                    .append(escape(value.calleeArtifact().toString()))
                    .append("</code></td><td><code>")
                    .append(escape(value.context()))
                    .append("</code></td></tr>"));
            body.append("</table>");
        }
        body.append("<h3>Dangerous transfers</h3>");
        if (metadata.dangerousTransfers().isEmpty()) {
            body.append("<p>No typed changed-instance transfer to a no-op ")
                    .append("dependency was found.</p>");
        } else {
            body.append("<table><tr><th>Caller</th><th>PC / argument</th>")
                    .append("<th>Callee artifact and method</th>")
                    .append("<th>Changed class / proof</th>")
                    .append("<th>Dependency paths</th></tr>");
            metadata.dangerousTransfers().forEach(value -> body
                    .append("<tr><td><code>")
                    .append(escape(value.callerMethod()))
                    .append("</code></td><td>")
                    .append(value.bytecodePc()).append(" / ")
                    .append(value.argumentIndex() < 0 ? "receiver"
                            : value.argumentIndex())
                    .append("</td><td><code>")
                    .append(escape(value.calleeArtifact() + " :: "
                            + value.resolvedCallee()))
                    .append("</code></td><td><code>")
                    .append(escape(value.changedClass() + " / "
                            + value.typeEvidence()))
                    .append("</code></td><td>")
                    .append(escape(String.join("; ",
                            value.dependencyPaths())))
                    .append("</td></tr>"));
            body.append("</table>");
        }
        body.append("<h3>Flow-to-cast factory evidence</h3>");
        if (metadata.factories().isEmpty()) {
            body.append("<p>No flow-to-cast factory approximation was ")
                    .append("generated.</p>");
        } else {
            body.append("<table><tr><th>Caller / PC</th><th>Callee</th>")
                    .append("<th>Cast / inferred concrete type</th>")
                    .append("<th>Context</th></tr>");
            metadata.factories().forEach(value -> body.append("<tr><td>")
                    .append(escape(value.callerMethod())).append(" / ")
                    .append(value.bytecodePc()).append("</td><td><code>")
                    .append(escape(value.resolvedCallee()))
                    .append("</code></td><td>")
                    .append(value.castInstruction()).append(" / <code>")
                    .append(escape(value.inferredType()))
                    .append("</code></td><td><code>")
                    .append(escape(value.context()))
                    .append("</code></td></tr>"));
            body.append("</table>");
        }
    }

    private void appendDuplicateResolutions(
            final HtmlSink body,
            final ModuleAnalysisResult module) {
        final List<DuplicateClassResolution> resolutions =
                duplicateResolutions(module);
        body.append("<section id=\"duplicates\"><h2>Duplicate class ")
                .append("resolution</h2>");
        if (resolutions.isEmpty()) {
            body.append("<p>No conflicting duplicate class was found.</p>")
                    .append("</section>");
            return;
        }
        body.append("<p class=\"warn\">Conflicting definitions were ")
                .append("resolved by classpath precedence. The selected ")
                .append("winner was used for Call Graph construction; this ")
                .append("warning does not change Module status.</p>")
                .append("<table><tr><th>Binary name</th><th>Winner</th>")
                .append("<th>Shadowed sources</th><th>Selection</th></tr>");
        for (DuplicateClassResolution resolution : resolutions) {
            body.append("<tr><td><code>")
                    .append(escape(resolution.getBinaryName()
                            .replace('/', '.')))
                    .append("</code></td><td>")
                    .append(escape(ownershipLabel(
                            resolution.getWinner())))
                    .append("</td><td>")
                    .append(escape(resolution.getLosers().stream()
                            .map(this::ownershipLabel)
                            .reduce((left, right) -> left + "; " + right)
                            .orElse("")))
                    .append("</td><td>")
                    .append(escape(resolution.getPrecedenceReason()))
                    .append("</td></tr>");
        }
        body.append("</table><details><summary>Technical details</summary>")
                .append("<table><tr><th>Binary name</th><th>Role</th>")
                .append("<th>Origin</th><th>Logical source</th></tr>");
        for (DuplicateClassResolution resolution : resolutions) {
            for (ClassOwnership candidate : resolution.getCandidates()) {
                body.append("<tr><td><code>")
                        .append(escape(resolution.getBinaryName()))
                        .append("</code></td><td>")
                        .append(candidate == resolution.getWinner()
                                ? "Winner" : "Shadowed")
                        .append("</td><td>")
                        .append(escape(candidate.getOrigin().name()))
                        .append("</td><td><code>")
                        .append(escape(candidate.getSource().toString()))
                        .append("</code></td></tr>");
            }
        }
        body.append("</table></details></section>");
    }

    // Wiki: wiki/features/report-generator.md - Changed member metrics
    private void appendChangedMemberMetrics(
            final HtmlSink body,
            final ModuleAnalysisResult module) {
        body.append("<section id=\"changed-members\"><h2>Changed members")
                .append("</h2><p>Counts include impact call paths and ")
                .append("structural reference paths.</p>")
                .append("<div class=\"report-controls\">")
                .append("<label class=\"report-control\">Dependency or ")
                .append("member search<input id=\"member-search\" type=")
                .append("\"search\" aria-controls=\"changed-member-table\">")
                .append("</label><label class=\"report-control\">")
                .append("Include dependencies<input id=")
                .append("\"member-dependency-include\" type=\"text\" ")
                .append("placeholder=\"com.acme.*:*\" aria-controls=")
                .append("\"changed-member-table\"></label><label class=")
                .append("\"report-control\">Exclude dependencies<input id=")
                .append("\"member-dependency-exclude\" type=\"text\" ")
                .append("placeholder=\"com.acme.internal:*\" aria-controls=")
                .append("\"changed-member-table\"></label><label class=")
                .append("\"report-control\">")
                .append("ChangePointKind<select id=\"member-kind\" ")
                .append("aria-controls=\"changed-member-table\">")
                .append("<option value=\"all\">All</option>");
        module.getUnit().getChangePoints().stream()
                .map(value -> value.getChangePoint().getKind().name())
                .distinct().sorted().forEach(kind -> body
                        .append("<option value=\"").append(attribute(kind))
                        .append("\">").append(escape(kind))
                        .append("</option>"));
        body.append("</select></label><label class=\"report-control\">")
                .append("Rows per page<select id=\"member-page-size\" ")
                .append("aria-controls=\"changed-member-table\">")
                .append("<option value=\"20\" selected>20</option>")
                .append("<option value=\"50\">50</option>")
                .append("<option value=\"100\">100</option></select>")
                .append("</label></div><p id=\"member-result-summary\" ")
                .append("class=\"muted\" aria-live=\"polite\"></p>")
                .append("<div class=\"table-scroll data-table-wrap\">")
                .append("<table id=\"changed-member-table\" class=")
                .append("\"data-table changed-member-table\"><caption ")
                .append("class=\"muted\">All changed members</caption>")
                .append("<thead><tr><th>Changed dependency</th>")
                .append("<th>ChangePointKind</th><th>Changed member/class")
                .append("</th><th class=\"numeric\">Impact</th>")
                .append("<th class=\"numeric\">Structural</th>")
                .append("<th class=\"numeric\">Impact total</th>")
                .append("</tr></thead><tbody id=\"member-rows\"></tbody>")
                .append("</table></div><p id=\"member-empty\" class=")
                .append("\"muted hidden\" aria-live=\"polite\"></p>")
                .append("<nav class=\"pagination\" aria-label=")
                .append("\"Changed member pages\"><button id=")
                .append("\"member-first\" type=\"button\">First</button>")
                .append("<button id=\"member-previous\" type=\"button\">")
                .append("Previous</button><label>Page <input id=")
                .append("\"member-page\" type=\"number\" min=\"1\" ")
                .append("value=\"1\" aria-label=\"Current changed member ")
                .append("page\"></label><span id=\"member-page-count\">")
                .append("of 1</span><button id=\"member-next\" type=")
                .append("\"button\">Next</button><button id=\"member-last\"")
                .append(" type=\"button\">Last</button></nav><noscript>")
                .append("<p class=\"warn\">Changed member browsing ")
                .append("requires JavaScript. Module totals remain in the ")
                .append("Summary section.</p></noscript><script id=")
                .append("\"changed-member-data\" type=")
                .append("\"application/json\">");
        writeChangedMemberMetricData(body, module);
        body.append("</script><script>").append(CHANGED_MEMBERS_SCRIPT)
                .append("</script></section>");
    }

    private void writeChangedMemberMetricData(
            final HtmlSink body,
            final ModuleAnalysisResult module) {
        final List<BoundChangePoint> members = module.getUnit()
                .getChangePoints().stream().distinct()
                .sorted(Comparator.comparing(BoundChangePoint::stableKey))
                .toList();
        final List<DependencyUpgradeKey> dependencies = members.stream()
                .map(BoundChangePoint::getDependencyUpgradeKey).distinct()
                .sorted(Comparator.comparing(DependencyUpgradeKey::stableKey))
                .toList();
        final Map<DependencyUpgradeKey, Integer> dependencyIds =
                new LinkedHashMap<>();
        for (int index = 0; index < dependencies.size(); index++) {
            dependencyIds.put(dependencies.get(index), index);
        }
        try (JsonGenerator json = JSON.createGenerator(body.writer())) {
            json.disable(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
            json.writeStartObject();
            json.writeArrayFieldStart("dependencyUpgrades");
            for (int index = 0; index < dependencies.size(); index++) {
                writeDependencyData(json, dependencies.get(index), index);
            }
            json.writeEndArray();
            json.writeArrayFieldStart("changedMembers");
            for (int index = 0; index < members.size(); index++) {
                writeChangedMemberData(json, members.get(index), index,
                        dependencyIds.get(members.get(index)
                                .getDependencyUpgradeKey()), null);
            }
            json.writeEndArray();
            json.writeArrayFieldStart("memberMetrics");
            for (int index = 0; index < members.size(); index++) {
                final BoundChangePoint member = members.get(index);
                final long impact = pathCount(module.getImpactPaths(),
                        member);
                final long structural = module.getStructuralPaths().stream()
                        .filter(path -> path.getChangePoint().equals(member))
                        .count();
                json.writeStartObject();
                json.writeNumberField("memberId", index);
                json.writeNumberField("impact", impact);
                json.writeNumberField("structural", structural);
                json.writeEndObject();
            }
            json.writeEndArray();
            json.writeEndObject();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private long pathCount(
            final List<ImpactPath> paths,
            final BoundChangePoint member) {
        return paths.stream().filter(path -> path.getTerminal()
                .getChangePoint().equals(member)).count();
    }

    // Wiki: wiki/features/report-generator.md - Affected Paths projection
    private void writeImpactPage(
            final Path target,
            final ModuleAnalysisResult module,
            final ModulePages pages,
            final String overallFile,
            final AffectedPathReportManifest manifest) throws IOException {
        writeDocument(target, "Affected Paths", "Affected Paths",
                breadcrumbs(overallFile, module, pages.impact(),
                        "Affected Paths"),
                siblingLinks(pages, "impact"), impactToc(), body -> {
        body.append("<h1 id=\"top\">Affected Paths: ")
                .append(escape(moduleLabel(module)))
                .append("</h1><section id=\"paths\"><h2>Affected paths</h2>")
                .append("<p>Each row links one root impact path to one ")
                .append("changed member. Only the current page is rendered.")
                .append("</p><div class=\"report-controls\">")
                .append("<label class=\"report-control\">View type<select ")
                .append("id=\"path-type\" aria-controls=\"path-table\">")
                .append("<option value=\"impact\" selected>Impact</option>")
                .append("<option value=\"structural\">Structural")
                .append("</option><option value=\"all\">All</option>")
                .append("</select></label><form id=\"path-search-form\" ")
                .append("class=\"search-fields\"><label class=")
                .append("\"report-control\">")
                .append("Affected method search<input id=\"path-search\" ")
                .append("type=\"search\" placeholder=\"package.Class#method\"")
                .append(" aria-controls=\"path-table\"></label><label class=")
                .append("\"report-control\">Include dependencies<input id=")
                .append("\"path-dependency-include\" type=\"text\" ")
                .append("placeholder=\"com.acme.*:*\" aria-controls=")
                .append("\"path-table\"></label><label class=")
                .append("\"report-control\">Exclude dependencies<input id=")
                .append("\"path-dependency-exclude\" type=\"text\" ")
                .append("placeholder=\"com.acme.internal:*\" aria-controls=")
                .append("\"path-table\"></label><button id=")
                .append("\"path-search-submit\" type=\"submit\">Search")
                .append("</button></form>")
                .append("<label class=\"report-control\">Rows per page")
                .append("<select id=\"path-page-size\" ")
                .append("aria-controls=\"path-table\">")
                .append("<option value=\"20\" selected>20</option>")
                .append("<option value=\"50\">50</option>")
                .append("<option value=\"100\">100</option>")
                .append("</select></label></div>")
                .append("<p id=\"path-result-summary\" class=\"muted\" ")
                .append("aria-live=\"polite\"></p><div class=\"table-scroll\">")
                .append("<table id=\"path-table\" class=\"path-table\">")
                .append("<caption class=\"muted\">Affected path records")
                .append("</caption><thead><tr><th>Type</th><th>Impact</th>")
                .append("<th>Affected application methods</th>")
                .append("<th>Changed dependency</th><th>ChangePointKind</th>")
                .append("<th>Changed member/class</th><th>Impact path</th>")
                .append("<th>Code diff</th></tr>")
                .append("</thead><tbody id=\"path-rows\"></tbody></table>")
                .append("</div><p id=\"path-empty\" class=\"muted hidden\"")
                .append(" aria-live=\"polite\"></p>")
                .append("<nav class=\"pagination\" aria-label=\"Path pages\">")
                .append("<button id=\"path-first\" type=\"button\">First")
                .append("</button><button id=\"path-previous\" ")
                .append("type=\"button\">")
                .append("Previous</button><label>Page <input id=\"path-page\"")
                .append(" type=\"number\" min=\"1\" value=\"1\" ")
                .append("aria-label=\"Current page\"></label>")
                .append("<span id=\"path-page-count\">of 1</span>")
                .append("<button id=\"path-next\" type=\"button\">Next")
                .append("</button><button id=\"path-last\" type=\"button\">")
                .append("Last</button></nav><noscript><p class=\"warn\">")
                .append("Affected path browsing requires JavaScript. ")
                .append("Summary counts remain available on Module Index.")
                .append("</p></noscript></section>")
                .append("<script id=\"affected-path-manifest\" ")
                .append("type=\"application/json\">");
        body.append(manifest.toJson());
        body.append("</script><script>")
                .append(AFFECTED_PATHS_SCRIPT).append("</script>");
        });
    }

    private void writeDependencyData(
            final JsonGenerator json,
            final DependencyUpgradeKey dependency,
            final int dependencyId) throws IOException {
        json.writeStartObject();
        json.writeNumberField("id", dependencyId);
        json.writeStringField("oldArtifact",
                dependency.getOldArtifact().toString());
        json.writeStringField("newArtifact",
                dependency.getNewArtifact().toString());
        json.writeStringField("scope", dependency.getScope().getValue());
        json.writeStringField("source",
                dependency.getNewArtifact().getGroupId() + ":"
                        + dependency.getNewArtifact().getArtifactId());
        json.writeEndObject();
    }

    private void writeChangedMemberData(
            final JsonGenerator json,
            final BoundChangePoint bound,
            final int memberId,
            final int dependencyId,
            final Integer codeDiffId) throws IOException {
        final ChangePoint point = bound.getChangePoint();
        json.writeStartObject();
        json.writeNumberField("id", memberId);
        json.writeNumberField("dependencyUpgradeId", dependencyId);
        json.writeStringField("source",
                bound.getDependencyUpgradeKey().getNewArtifact().getGroupId()
                        + ":" + bound.getDependencyUpgradeKey()
                        .getNewArtifact().getArtifactId());
        json.writeStringField("changePointKind", point.getKind().name());
        json.writeStringField("owner", point.getOwner().replace('/', '.'));
        writeNullableString(json, "name", point.getName());
        if (codeDiffId == null) {
            json.writeNullField("codeDiffId");
        } else {
            json.writeNumberField("codeDiffId", codeDiffId);
        }
        json.writeEndObject();
    }

    private void writeNullableString(
            final JsonGenerator json,
            final String field,
            final String value) throws IOException {
        if (value == null) {
            json.writeNullField(field);
        } else {
            json.writeStringField(field, value);
        }
    }

    private void writeDocument(
            final Path target,
            final String title,
            final String currentPage,
            final String breadcrumbs,
            final String siblings,
            final String toc,
            final PageBody body) throws IOException {
        try (Writer writer = Files.newBufferedWriter(
                target, java.nio.charset.StandardCharsets.UTF_8)) {
            final HtmlSink output = HtmlSink.writer(writer);
            output.append("<!DOCTYPE html><html lang=\"en\"><head>")
                    .append("<meta charset=\"UTF-8\"><meta name=\"viewport\"")
                    .append(" content=\"width=device-width,initial-scale=1\">")
                    .append("<title>").append(escape(title))
                    .append("</title><style>").append(CSS)
                    .append("</style></head><body><header>")
                    .append(breadcrumbs.isEmpty()
                            ? "<nav class=\"breadcrumbs\" "
                            + "aria-label=\"Breadcrumb\">"
                            + "<span>Overall</span></nav>" : breadcrumbs)
                    .append(siblings)
                    .append("</header><div class=\"layout\"><nav class=\"toc\"")
                    .append(" aria-label=\"Table of contents\"><strong>")
                    .append("On this page</strong><div class=\"muted\"")
                    .append(" aria-current=\"page\">")
                    .append(escape(currentPage)).append("</div>")
                    .append(toc).append("</nav><main>");
            body.write(output);
            output.append("</main></div></body></html>");
        } catch (java.io.UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    private String breadcrumbs(
            final String overallFile,
            final ModuleAnalysisResult module,
            final String currentFile,
            final String currentPage) {
        final String moduleIndex = currentFile.endsWith(".html")
                ? moduleBase(module) + ".html" : currentFile;
        return "<nav class=\"breadcrumbs\" aria-label=\"Breadcrumb\">"
                + "<a href=\"../" + attribute(overallFile)
                + "\">Overall</a><span>›</span>"
                + ("Module Index".equals(currentPage)
                ? "<span>" + escape(moduleLabel(module))
                + "</span>" : "<a href=\"" + attribute(moduleIndex)
                + "\">" + escape(moduleLabel(module))
                + "</a><span>›</span><span>" + escape(currentPage)
                + "</span>") + "</nav>";
    }

    private String siblingLinks(
            final ModulePages pages, final String selected) {
        return "<nav class=\"siblings\" aria-label=\"Module pages\">"
                + sibling(pages.index(), "Module Index",
                "index".equals(selected))
                + sibling(pages.impact(), "Affected Paths",
                "impact".equals(selected)) + "</nav>";
    }

    private String sibling(
            final String href, final String label,
            final boolean selected) {
        return selected ? "<strong aria-current=\"page\">"
                + label + "</strong>" : "<a href=\""
                + attribute(href) + "\">" + label + "</a>";
    }

    private void appendTerminology(
            final HtmlSink body,
            final CallGraphAlgorithm algorithm) {
        body.append("<section id=\"terms\"><h2>Terminology</h2><dl>")
                .append(term("WALA", "The Java analysis library used to "
                        + "discover possible call relationships."))
                .append(term("Call Graph", "A graph whose nodes are methods "
                        + "and whose edges are possible calls."))
                .append(algorithmTerm(algorithm))
                .append(term("Conservative / false positive", "Conservative "
                        + "analysis keeps possible calls to avoid silently "
                        + "missing them; a false positive is a reported call "
                        + "that cannot occur at runtime."))
                .append(term("Context", "A WALA distinction between different "
                        + "analysis instances of the same Java method."))
                .append(term("SSA equivalence", "A comparison of normalized "
                        + "method control flow and data dependencies during "
                        + "ChangePoint collection. A model match suppresses "
                        + "the method-body ChangePoint but is not proof of "
                        + "complete runtime behavior equivalence."))
                .append(term("Reflection", "Calls whose target is selected at "
                        + "runtime through class, method or constructor data."))
                .append(term("ServiceLoader", "The JDK provider discovery "
                        + "mechanism backed by META-INF/services resources."))
                .append(term("Direct / transitive impact", "Direct impact ends "
                        + "at a changed dependency from application code; "
                        + "transitive impact passes through another library."))
                .append("</dl></section>");
    }

    private String algorithmTerm(final CallGraphAlgorithm algorithm) {
        return switch (algorithm) {
            case CHA -> term("CHA", "Class Hierarchy Analysis resolves "
                    + "possible calls from declared types and the target "
                    + "class hierarchy. It does not build points-to facts "
                    + "or traverse JDK method bodies.");
            case K_OBJ -> term(
                    "k-Object (experimental)",
                    "The experimental context-sensitive WALA call analysis. "
                    + "It distinguishes a configured number of receiver "
                    + "allocation sites, keeps exact allocation-site and "
                    + "constant-specific identity without smushing, and "
                    + "can therefore cost substantially more time and "
                    + "memory than CHA.");
        };
    }

    private String term(final String name, final String explanation) {
        return "<dt><strong>" + escape(name) + "</strong></dt><dd>"
                + escape(explanation) + "</dd>";
    }

    private void appendPreflight(
            final HtmlSink body,
            final PreflightReport preflight) {
        body.append("<section id=\"preflight\"><h2>Preflight checks</h2>")
                .append("<details><summary>Technical details</summary>")
                .append("<table><tr><th>Check</th><th>Status</th>")
                .append("<th>Evidence</th></tr>");
        for (PreflightResult result : preflight.getResults()) {
            body.append("<tr><td>").append(escape(result.getCheckId()))
                    .append("</td><td>")
                    .append(escape(result.getStatus().name()))
                    .append("</td><td>")
                    .append(escape(result.getEvidence()))
                    .append("</td></tr>");
        }
        body.append("</table></details></section>");
    }

    private void appendStageMetrics(
            final HtmlSink body,
            final Map<String, Long> stages) {
        body.append("<h3>Stage elapsed time</h3><table>")
                .append("<tr><th>Stage</th><th>Milliseconds</th></tr>");
        stages.forEach((stage, millis) -> body.append("<tr><td>")
                .append(escape(stage)).append("</td><td>")
                .append(millis).append("</td></tr>"));
        body.append("</table>");
    }

    private void appendList(
            final HtmlSink body,
            final List<String> values,
            final String empty) {
        if (values.isEmpty()) {
            body.append("<p>").append(escape(empty)).append("</p>");
            return;
        }
        body.append("<ul>");
        values.forEach(value -> body.append("<li>")
                .append(escape(value)).append("</li>"));
        body.append("</ul>");
    }

    private String jarLabel(final DependencyUpgradeKey key) {
        return key.getOldArtifact().getGroupId() + ":"
                + key.getOldArtifact().getArtifactId() + " "
                + key.getOldArtifact().getVersion() + " → "
                + key.getNewArtifact().getVersion();
    }

    private String statusText(final String status) {
        return switch (status) {
            case "SUCCESS" -> "Completed";
            case "INCONCLUSIVE" -> "Completed with coverage limitations";
            case "PARTIAL_SUCCESS" -> "Partially completed";
            case "FAILED" -> "Failed";
            case "SKIPPED" -> "Skipped";
            default -> status;
        };
    }

    private String reasonText(final ModuleAnalysisResult module) {
        if (module.getStatus() == ModuleAnalysisStatus.SKIPPED) {
            return switch (module.getReason()) {
                case SKIPPED_NOT_IN_TARGET -> "The module exists only in the "
                        + "baseline revision.";
                case SKIPPED_NO_BASELINE -> "The module exists only in the "
                        + "target revision.";
                case SKIPPED_NO_RELEVANT_CHANGE -> "No relevant changed "
                        + "dependency member was found.";
                case SKIPPED_USER_ENTRYPOINT_SCOPE -> "No application class "
                        + "matched the configured entrypoint boundary.";
                default -> module.getDetail();
            };
        }
        return module.getDetail().isBlank()
                ? statusText(module.getStatus().name())
                : module.getDetail();
    }

    private String statusClass(final String status) {
        return "SUCCESS".equals(status) ? "ok"
                : "FAILED".equals(status) ? "fail" : "warn";
    }

    private String structuralOwner(final StructuralReferencePath path) {
        final String member = path.getReference().getReferencingMember();
        return path.getReference().getReferencingClass().replace('/', '.')
                + (member.isBlank() ? "" : "#" + member);
    }

    private String humanMethod(final MethodId method) {
        return method.owner().replace('/', '.') + "#" + method.name();
    }

    private long pathCount(final AnalysisRunResult run) {
        return run.getModuleResults().stream().mapToLong(module ->
                module.getImpactPaths().size()).sum();
    }

    private long structuralPathCount(final AnalysisRunResult run) {
        return run.getModuleResults().stream().mapToLong(module ->
                module.getStructuralPaths().size()).sum();
    }

    private long effectiveChangeCount(final AnalysisRunResult run) {
        return run.getModuleResults().stream().mapToLong(module ->
                module.getUnit().getChangePoints().size()).sum();
    }

    private long duplicateConflictCount(final AnalysisRunResult run) {
        return run.getModuleResults().stream()
                .mapToLong(module -> duplicateResolutions(module).size())
                .sum();
    }

    private long shadowedChangeCount(final AnalysisRunResult run) {
        return run.getModuleResults().stream()
                .mapToLong(this::shadowedChangeCount).sum();
    }

    private long shadowedChangeCount(final ModuleAnalysisResult module) {
        return module.getDispositions().values().stream()
                .filter(value -> value
                        == ChangePointDisposition.SHADOWED_BY_DUPLICATE)
                .count();
    }

    private List<DuplicateClassResolution> duplicateResolutions(
            final ModuleAnalysisResult module) {
        return module.getDuplicateClassResolutions();
    }

    private String ownershipLabel(final ClassOwnership ownership) {
        final String source = ownership.getSource().artifact()
                .map(Object::toString)
                .orElseGet(() -> {
                    final Path path = ownership.getSource().path()
                            .orElseThrow();
                    final Path fileName = path.getFileName();
                    return fileName == null ? path.toString()
                            : fileName.toString();
                });
        return ownership.getOrigin().name() + " — " + source;
    }

    private long affectedMethodCount(final ModuleAnalysisResult module) {
        final java.util.Set<MethodId> methods = new java.util.LinkedHashSet<>();
        module.getImpactPaths().stream()
                .flatMap(path -> path.getAffectedMethods().stream())
                .forEach(methods::add);
        module.getStructuralPaths().stream()
                .flatMap(path -> path.getNodes().stream())
                .filter(node -> node.origin()
                        == io.github.dependencyanalysis.callgraph.model
                        .CodeOrigin.PROJECT)
                .map(QueryNode::methodId).forEach(methods::add);
        module.getStructuralPaths().stream()
                .map(StructuralReferencePath::getAffectedMethod)
                .filter(java.util.Objects::nonNull).forEach(methods::add);
        return methods.size();
    }

    private long affectedClassCount(final ModuleAnalysisResult module) {
        final java.util.Set<String> classes = new java.util.LinkedHashSet<>();
        module.getImpactPaths().stream()
                .flatMap(path -> path.getAffectedMethods().stream())
                .map(MethodId::owner)
                .forEach(classes::add);
        module.getStructuralPaths().stream().forEach(path -> {
            if (path.getAffectedMethod() == null) {
                classes.add(path.getReference().getReferencingClass());
            } else {
                classes.add(path.getAffectedMethod().owner());
            }
        });
        return classes.size();
    }

    private String ssaCounts(final List<ModuleAnalysisResult> modules) {
        final List<SsaComparisonEvidence> comparisons =
                uniqueSsaComparisons(modules);
        final long eligible = uniqueDecompileComparisons(modules).size();
        return comparisonCount(comparisons,
                SsaComparisonStatus.MATCHED) + " / "
                + comparisonCount(comparisons,
                SsaComparisonStatus.DIFFERENT) + " / "
                + comparisonCount(comparisons,
                SsaComparisonStatus.UNKNOWN) + " / "
                + (eligible - comparisons.size());
    }

    private String decompileCounts(
            final List<ModuleAnalysisResult> modules) {
        final List<DecompileComparisonSummary> comparisons =
                uniqueDecompileComparisons(modules);
        return decompileComparisonCount(comparisons,
                DecompileComparisonStatus.IDENTICAL) + " / "
                + decompileComparisonCount(comparisons,
                DecompileComparisonStatus.DIFFERENT) + " / "
                + decompileComparisonCount(comparisons,
                DecompileComparisonStatus.UNKNOWN);
    }

    private String pruningRows(
            final List<ModuleAnalysisResult> modules) {
        final StringBuilder rows = new StringBuilder();
        for (String identifier
                : ImpactPathPruningSummary.FIXED_EXTENSION_IDS) {
            final List<ImpactPathPruningSummary.ExtensionSummary> summaries =
                    modules.stream().flatMap(module -> module
                            .getImpactPathPruning().extensions().stream())
                            .filter(value -> value.identifier()
                                    .equals(identifier)).toList();
            final String status = summaries.stream()
                    .map(value -> value.status().label())
                    .distinct().sorted()
                    .collect(java.util.stream.Collectors.joining(", "));
            final long checked = summaries.stream().mapToLong(value ->
                    value.metrics().uniqueEvaluations()).sum();
            final long pruned = summaries.stream().mapToLong(value ->
                    value.metrics().pruned()).sum();
            final long unknown = summaries.stream().mapToLong(value ->
                    value.metrics().unknown()).sum();
            rows.append(row(identifier,
                    (status.isEmpty() ? "not executed" : status)
                            + " (experimental); edges checked / pruned / "
                            + "unknown: "
                            + checked + " / " + pruned + " / " + unknown));
        }
        return rows.toString();
    }

    private String chaPruningBoundaryRow(
            final CallGraphAlgorithm algorithm) {
        return algorithm == CallGraphAlgorithm.CHA
                ? row("CHA Impact Path pruning boundary",
                "caller-local receiver facts only; cross-method receiver "
                        + "flows are retained conservatively") : "";
    }

    private List<SsaComparisonEvidence> uniqueSsaComparisons(
            final List<ModuleAnalysisResult> modules) {
        final Map<String, SsaComparisonEvidence> unique =
                new java.util.TreeMap<>();
        modules.stream().flatMap(module -> module.getUnit()
                        .getSsaComparisons().stream())
                .forEach(value -> unique.putIfAbsent(
                        value.stableKey(), value));
        return List.copyOf(unique.values());
    }

    private List<DecompileComparisonSummary> uniqueDecompileComparisons(
            final List<ModuleAnalysisResult> modules) {
        final Map<String, DecompileComparisonSummary> unique =
                new java.util.TreeMap<>();
        modules.stream().flatMap(module -> module.getUnit()
                        .getDecompileComparisons().stream())
                .forEach(value -> unique.putIfAbsent(
                        value.stableKey(), value));
        return List.copyOf(unique.values());
    }

    private long comparisonCount(
            final List<SsaComparisonEvidence> comparisons,
            final SsaComparisonStatus status) {
        return comparisons.stream()
                .filter(value -> value.getStatus() == status).count();
    }

    private long decompileComparisonCount(
            final List<DecompileComparisonSummary> comparisons,
            final DecompileComparisonStatus status) {
        return comparisons.stream()
                .filter(value -> value.status() == status).count();
    }

    private long contextCount(final ModuleAnalysisResult module) {
        return module.getCallGraphSnapshot() == null ? 0L
                : module.getCallGraphSnapshot().contextCount();
    }

    private String dependencyScopeCounts(final AnalysisRunResult run) {
        final long changedPaths = run.getModuleResults().stream()
                .filter(value -> value.getUnit().getChangedPathSelection()
                        .actualMode() == io.github.dependencyanalysis.impact
                        .DependencyAnalysisScopeMode.CHANGED_PATHS)
                .count();
        final long full = run.getModuleResults().size() - changedPaths;
        final long fallback = run.getModuleResults().stream()
                .filter(value -> value.getUnit().getChangedPathSelection()
                        .fallbackReason().isPresent()).count();
        return changedPaths + " / " + full + " / " + fallback;
    }

    private String dependencyArtifactCounts(final AnalysisRunResult run) {
        long real = 0L;
        long noOp = 0L;
        for (ModuleAnalysisResult module : run.getModuleResults()) {
            for (ArtifactCoord artifact : module.getUnit()
                    .getTargetArtifacts()) {
                if (module.getUnit().getChangedPathSelection()
                        .policyFor(artifact)
                        == io.github.dependencyanalysis.impact
                        .DependencyMethodBodyPolicy.REAL_IR) {
                    real++;
                } else {
                    noOp++;
                }
            }
        }
        return real + " / " + noOp;
    }

    private String dependencyMethodCounts(final AnalysisRunResult run) {
        long noOp = 0L;
        long factory = 0L;
        for (ModuleAnalysisResult module : run.getModuleResults()) {
            noOp += boundary(module).noOpMethodNodes();
            factory += boundary(module).factoryMethodNodes();
        }
        return noOp + " / " + factory;
    }

    private long dangerousTransferCount(final AnalysisRunResult run) {
        return run.getModuleResults().stream()
                .mapToLong(value -> boundary(value)
                        .dangerousTransfers().size())
                .sum();
    }

    private String inconclusiveRatio(final AnalysisRunResult run) {
        final int total = run.getModuleResults().size();
        final long inconclusive = run.getModuleResults().stream()
                .filter(value -> value.getStatus()
                        == ModuleAnalysisStatus.INCONCLUSIVE).count();
        final double ratio = total == 0 ? 0.0D
                : (double) inconclusive / total;
        return inconclusive + " / " + total + " ("
                + String.format(java.util.Locale.ROOT, "%.2f", ratio)
                + ")";
    }

    private String sessionRows(final ModuleAnalysisResult module) {
        final CallGraphStats stats = module.getCallGraphStats();
        if (stats == null) {
            return row("Call Graph", "Not available");
        }
        return row("Parameter candidates",
                parameterCandidateCount(module))
                + row("Call Graph nodes", stats.methodCount())
                + row("Call Graph edges", stats.edgeCount())
                + row("JDK-declared dispatch targets pruned",
                jdkDeclaredDispatchPrunedTargetCount(module))
                + row("WALA Context count", contextCount(module));
    }

    private Object entrypointCount(final ModuleAnalysisResult module) {
        return module.getCallGraphSnapshot() == null ? "Not available"
                : module.getCallGraphSnapshot().entrypointCount();
    }

    private Object selectedEntrypointClassCount(
            final ModuleAnalysisResult module) {
        return module.getCallGraphSnapshot() == null ? "Not available"
                : module.getCallGraphSnapshot()
                .selectedEntrypointClassCount();
    }

    private Object parameterCandidateCount(
            final ModuleAnalysisResult module) {
        return module.getCallGraphSnapshot() == null ? "Not available"
                : module.getCallGraphSnapshot().parameterCandidateCount();
    }

    private DependencyBoundarySnapshot boundary(
            final ModuleAnalysisResult module) {
        return module.getCallGraphSnapshot() == null
                ? DependencyBoundarySnapshot.empty()
                : module.getCallGraphSnapshot().dependencyBoundary();
    }

    private int jdkDeclaredDispatchPrunedTargetCount(
            final ModuleAnalysisResult module) {
        return module.getCallGraphSnapshot() == null ? 0
                : module.getCallGraphSnapshot()
                .jdkDeclaredDispatchPrunedTargetCount();
    }

    private String row(final String name, final Object value) {
        return "<tr><th>" + escape(name) + "</th><td>"
                + escape(String.valueOf(value)) + "</td></tr>";
    }

    private String moduleBase(final ModuleAnalysisResult module) {
        final String sanitized = (module.getModuleId().getCoordinate()
                .getGroupId() + "-" + module.getModuleId().getCoordinate()
                .getArtifactId()).replaceAll("[^A-Za-z0-9._-]", "-");
        return sanitized + "-" + stableHash(
                module.getModuleId().stableKey());
    }

    private String moduleLabel(final ModuleAnalysisResult module) {
        return module.getModuleId().getCoordinate().toString();
    }

    private String entrypointSelectionLabel(final AnalysisRunResult run) {
        if (!run.getEntrypointSelection().isFiltered()) {
            return "All application classes";
        }
        return "include=" + run.getEntrypointSelection().includes()
                + "; exclude=" + run.getEntrypointSelection().excludes();
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

    private String overallToc() {
        return toc("read", "How to read", "limits", "Scope and limitations",
                "terms", "Terminology", "run", "Run summary",
                "modules", "Modules", "preflight", "Preflight checks");
    }

    private String moduleIndexToc() {
        return toc("summary", "Summary", "changed-members",
                "Changed members", "scope", "Analysis scope",
                "dependency-body-boundary", "Dependency body boundary",
                "duplicates", "Duplicate class resolution",
                "metrics", "Runtime metrics", "limits", "Limitations");
    }

    private String impactToc() {
        return toc("paths", "Affected paths");
    }

    private String toc(final String... entries) {
        final HtmlSink value = HtmlSink.memory("<ul>");
        for (int index = 0; index < entries.length; index += 2) {
            value.append("<li><a href=\"#")
                    .append(attribute(entries[index]))
                    .append("\" aria-label=\"Go to ")
                    .append(attribute(entries[index + 1]))
                    .append("\">").append(escape(entries[index + 1]))
                    .append("</a></li>");
        }
        return value.append("</ul>").toString();
    }

    private String escape(final String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private String attribute(final String value) {
        return escape(value);
    }

    private static String resource(final String path) {
        try (InputStream input = PerModuleHtmlReportGenerator.class
                .getResourceAsStream(path)) {
            if (input == null) {
                throw new IllegalStateException(
                        "Missing report resource: " + path);
            }
            return new String(input.readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to read report resource: " + path, exception);
        }
    }

    private void publish(
            final Path staging,
            final Path output,
            final String ownedName) throws IOException {
        final Path parent = output.getParent();
        final Path owned = parent.resolve(ownedName);
        final Path stagedModules = staging.resolve(ownedName);
        final Path stagedIndex = staging.resolve(output.getFileName());
        final Path backup = parent.resolve("." + ownedName
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
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
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
                    // Best-effort command-owned cleanup.
                }
            });
        } catch (IOException ignored) {
            // Best-effort command-owned cleanup.
        }
    }

    /** Streaming HTML body callback. */
    @FunctionalInterface
    private interface PageBody {
        void write(HtmlSink output);
    }

    /** Append facade shared by streaming and small in-memory fragments. */
    private static final class HtmlSink {

        /** Target appendable. */
        private final Appendable target;

        private HtmlSink(final Appendable value) {
            target = value;
        }

        static HtmlSink memory() {
            return memory("");
        }

        static HtmlSink memory(final String initial) {
            return new HtmlSink(new StringBuilder(initial));
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

        Writer writer() {
            if (target instanceof Writer writer) {
                return writer;
            }
            throw new IllegalStateException("HTML sink is not writer-backed");
        }

        @Override
        public String toString() {
            return target.toString();
        }
    }

    /**
     * Owned sibling filenames for one Module.
     *
     * @param index Module Index filename
     * @param impact Affected Paths filename
     */
    private record ModulePages(String index, String impact) {
    }

    /**
     * Immutable Overall renderer input.
     *
     * @param maven Maven runtime
     * @param plugin Dependency Plugin runtime
     * @param javaRuntime target JDK
     * @param ownedName owned Module directory
     * @param pages Module page names
     */
    private record OverallContext(
            MavenRuntimeDescriptor maven,
            MavenDependencyPluginRuntime plugin,
            JavaRuntimeDescriptor javaRuntime,
            String ownedName,
            Map<ModuleAnalysisResult, ModulePages> pages) {
    }

    /**
     * Runtime metadata repeated on Module Index pages.
     *
     * @param jdkVersion target JDK version
     * @param mavenVersion Apache Maven version
     * @param mavenSource selected Maven runtime source
     * @param entrypointBoundary selected PROJECT entrypoint boundary
     * @param pluginVersion Maven Dependency Plugin source/version
     * @param callGraphAlgorithm command-wide Call Graph algorithm
     */
    private record ModuleRuntime(
            String jdkVersion,
            String mavenVersion,
            String mavenSource,
            String entrypointBoundary,
            String pluginVersion,
            CallGraphAlgorithm callGraphAlgorithm) {
    }
}
