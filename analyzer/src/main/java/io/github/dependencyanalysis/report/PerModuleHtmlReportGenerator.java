package io.github.dependencyanalysis.report;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.callgraph.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.CallGraphStats;
import io.github.dependencyanalysis.callgraph.ClassOwnership;
import io.github.dependencyanalysis.callgraph.DuplicateClassResolution;
import io.github.dependencyanalysis.callgraph.DependencyBodyBoundaryMetadata;
import io.github.dependencyanalysis.callgraph.MethodId;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.diagnostic.DiagnosticEvent;
import io.github.dependencyanalysis.diagnostic.DiagnosticLogFormatter;
import io.github.dependencyanalysis.impact.AnalysisRunResult;
import io.github.dependencyanalysis.impact.BoundChangePoint;
import io.github.dependencyanalysis.impact.CodeComparisonEvidence;
import io.github.dependencyanalysis.impact.CodeComparisonStatus;
import io.github.dependencyanalysis.impact.ChangePointDisposition;
import io.github.dependencyanalysis.impact.DependencyUpgradeKey;
import io.github.dependencyanalysis.impact.ImpactClassification;
import io.github.dependencyanalysis.impact.ImpactPath;
import io.github.dependencyanalysis.impact.ImpactEvidence;
import io.github.dependencyanalysis.impact.MethodEquivalenceResult;
import io.github.dependencyanalysis.impact.MethodEquivalenceStatus;
import io.github.dependencyanalysis.impact.ModuleAnalysisResult;
import io.github.dependencyanalysis.impact.ModuleAnalysisStatus;
import io.github.dependencyanalysis.impact.QueryNode;
import io.github.dependencyanalysis.impact.StructuralReferencePath;
import io.github.dependencyanalysis.impact.WalaQueryNode;
import io.github.dependencyanalysis.impact.SnapshotQueryNode;
import io.github.dependencyanalysis.preflight.PreflightReport;
import io.github.dependencyanalysis.preflight.PreflightResult;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.MavenDependencyPluginRuntime;
import io.github.dependencyanalysis.runtime.MavenRuntimeDescriptor;

import java.io.IOException;
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

    /** Shared Console/Report Diagnostic prefix formatter. */
    private final DiagnosticLogFormatter diagnosticFormatter =
            new DiagnosticLogFormatter();

    /** SHA-256 algorithm. */
    private static final String SHA_256 = "SHA-256";

    /** Stable hash prefix length. */
    private static final int HASH_LENGTH = 12;

    /** Shared offline CSS. */
    private static final String CSS =
            ":root{color-scheme:light;--line:#d8dee4;--soft:#f6f8fa;"
            + "--ink:#1f2328;--accent:#0969da}*{box-sizing:border-box}"
            + "body{margin:0;color:var(--ink);font:15px/1.55 system-ui,"
            + "sans-serif}a{color:var(--accent)}header{border-bottom:1px solid"
            + " var(--line);padding:14px 24px;background:#fff}"
            + ".breadcrumbs,.siblings{display:flex;gap:8px;flex-wrap:wrap}"
            + ".siblings{margin-top:8px}.layout{display:grid;grid-template-"
            + "columns:240px minmax(0,1fr);gap:28px;max-width:1440px;"
            + "margin:auto;padding:22px}.toc{position:sticky;top:18px;"
            + "align-self:start;border:1px solid var(--line);border-radius:8px;"
            + "padding:14px;background:#fff}.toc ul{margin:8px 0 0;"
            + "padding-left:20px}.toc a[aria-current=\"page\"]{font-weight:700}"
            + "main{min-width:0}h1{margin-top:0}h2{margin-top:34px;"
            + "border-bottom:1px solid var(--line);padding-bottom:5px}"
            + "table{border-collapse:collapse;width:100%;margin:12px 0}"
            + "th,td{border:1px solid var(--line);padding:8px;text-align:left;"
            + "vertical-align:top}th{background:var(--soft)}code,pre{"
            + "font-family:"
            + "ui-monospace,monospace}code{overflow-wrap:anywhere}pre{overflow:"
            + "auto;background:var(--soft);border:1px solid var(--line);"
            + "padding:12px}.card{border:1px solid var(--line);"
            + "border-radius:8px;"
            + "padding:14px;margin:14px 0}.ok{color:#167d36}"
            + ".warn{color:#9a6700}"
            + ".fail{color:#cf222e}.muted{color:#59636e}details{margin:10px 0;"
            + "padding:9px;border:1px solid var(--line);border-radius:6px}"
            + ".badge{display:inline-block;margin-left:7px;padding:1px 7px;"
            + "border-radius:999px;background:#ddf4ff;color:#0550ae;"
            + "font-size:12px;font-weight:700}.badge.filtered{background:"
            + "#fff8c5;color:#7d4e00}.badge.structural{background:#dafbe1;"
            + "color:#116329}.diff{white-space:pre;tab-size:4}"
            + "summary{cursor:pointer;font-weight:600}.sequence{font-size:14px}"
            + "@media(max-width:800px){.layout{display:block;padding:14px}"
            + ".toc{position:static;margin-bottom:20px}"
            + "header{padding:12px 14px}"
            + "table{display:block;overflow-x:auto}}";

    /**
     * Writes pages to staging and atomically replaces command-owned output.
     *
     * @param run analysis result
     * @param events diagnostics
     * @param preflight preflight report
     * @param maven selected Maven runtime
     * @param plugin embedded Dependency Plugin runtime
     * @param javaRuntime target JDK
     * @param output Overall Index file
     */
    public void generate(
            final AnalysisRunResult run,
            final List<DiagnosticEvent> events,
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
                    run.isExperimentalBytecodeSemanticComparisonEnabled());
            final Map<ModuleAnalysisResult, ModulePages> pages =
                    writeModulePages(run, modules, events,
                            absolute.getFileName().toString(), moduleRuntime);
            final OverallContext context = new OverallContext(
                    maven, plugin, javaRuntime, ownedName, pages);
            writeOverallPage(staging.resolve(absolute.getFileName()),
                    run, events, preflight, context);
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
            final List<DiagnosticEvent> events,
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
                    base + ".html", base + "-impact.html",
                    base + "-changes.html");
            writeModuleIndexPage(directory.resolve(pages.index()), module,
                    events, pages, overallFile, runtime);
            writeImpactPage(directory.resolve(pages.impact()), module,
                    pages, overallFile);
            writeChangesPage(directory.resolve(pages.changes()), module,
                    pages, overallFile);
            result.put(module, pages);
        }
        return result;
    }

    private void writeOverallPage(
            final Path target,
            final AnalysisRunResult run,
            final List<DiagnosticEvent> events,
            final PreflightReport preflight,
            final OverallContext context) throws IOException {
        writeDocument(target, "Impact Analysis Report", "Overall", "", "",
                overallToc(), body -> {
        final boolean semanticComparison = run
                .isExperimentalBytecodeSemanticComparisonEnabled();
        body
                .append("<h1 id=\"top\">Impact Analysis Report</h1>")
                .append("<section id=\"read\"><h2>How to read this report")
                .append("</h2><p>Start with the Modules table. Open a module ")
                .append("to review its summary, then use Affected Call Chains ")
                .append("to see application entry methods and Dependency ")
                .append("Changes to inspect changed JARs and members.</p>")
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
        body.append("<p>Code comparisons are generated locally from dependency "
                + "bytecode and may differ from the original source code.</p>");
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
                .append(row("Modules analyzed in parallel",
                        run.getActualParallelism() + " (configured "
                                + run.getConfiguredParallelism() + ")"))
                .append(row("JAR comparisons in parallel",
                        run.getActualJarDiffWorkers() + " (configured "
                                + run.getConfiguredJarDiffWorkers() + ")"))
                .append(row("Code comparisons in parallel",
                        run.getActualDecompileWorkers() + " (configured "
                                + run.getConfiguredParallelism() + ")"))
                .append(row("Dependency changes",
                        run.getDependencyChanges().size()))
                .append(row("Conflicting duplicate classes",
                        duplicateConflictCount(run)))
                .append(row("Shadowed dependency changes",
                        shadowedChangeCount(run)))
                .append(row("Candidate / final call chains",
                        pathCount(run, true) + " / "
                                + pathCount(run, false)))
                .append("</table>");
        appendStageMetrics(body, run.getStageElapsedMillis());
        body.append("<details><summary>Technical details</summary><table>")
                .append(row("Algorithm",
                        run.getCallGraphAlgorithm().identifier()))
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
                .append(row("Bytecode semantic comparison",
                        semanticComparisonStatus(semanticComparison)))
                .append(row("WALA", walaVersion()))
                .append(row("SSA equivalence workers",
                        ssaWorkers(semanticComparison)))
                .append(row("Raw changed members", rawChangeCount(run)))
                .append(row("Entrypoint includes",
                        run.getEntrypointSelection().includes()))
                .append(row("Entrypoint excludes",
                        run.getEntrypointSelection().excludes()))
                .append(row("Maven executable",
                        context.maven().getExecutable()))
                .append(row("SSA equivalent / different / unknown",
                        ssaCounts(run.getModuleResults(),
                                semanticComparison)))
                .append("</table></details></section>")
                .append("<section id=\"modules\"><h2>Modules</h2>")
                .append("<table><tr><th>Module</th><th>Status</th>")
                .append("<th>Explanation</th><th>Dependencies changed</th>")
                .append("<th>Duplicate classes</th>")
                .append("<th>Shadowed changes</th>")
                .append("<th>Final call chains</th><th>Affected methods</th>")
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
                    .append(module.getFinalPaths().size())
                    .append("</td><td>")
                    .append(affectedMethodCount(module))
                    .append("</td></tr>");
        }
        body.append("</table></section>");
        appendPreflight(body, preflight);
        appendDiagnostics(body, events);
        });
    }

    private void writeModuleIndexPage(
            final Path target,
            final ModuleAnalysisResult module,
            final List<DiagnosticEvent> events,
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
                .append(row("Candidate / filtered / final call chains",
                        module.getCandidatePaths().size() + " / "
                                + (module.getCandidatePaths().size()
                                - module.getFinalPaths().size()) + " / "
                                + module.getFinalPaths().size()))
                .append(row("Class structure references",
                        module.getStructuralPaths().size()))
                .append(row("Dependency / member changes",
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
                        + " ms")).append("</table></section>")
                .append("<section id=\"scope\"><h2>Analysis scope</h2><p>")
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
                .append(row("Bytecode semantic comparison",
                        semanticComparisonStatus(runtime
                                .semanticComparisonEnabled())))
                .append(row("SSA equivalent / different / unknown",
                        ssaCounts(List.of(module), runtime
                                .semanticComparisonEnabled())))
                .append(row("Raw dependency / member changes",
                        module.getUnit().getDependencyChanges().size() + " / "
                                + module.getUnit().getChangePoints().size()))
                .append("</table></details></section>")
                .append("<section id=\"limits\"><h2>Coverage limitations")
                .append("</h2>");
        final List<String> limitations = new ArrayList<>(
                module.getLimitations());
        module.getUnit().getJarDiffFailures().forEach(failure -> limitations
                .add("JAR member comparison unavailable for "
                        + jarLabel(failure.dependencyUpgradeKey()) + ": "
                        + failure.reason()));
        appendList(body, limitations,
                "No module-specific limitation was recorded.");
        body.append("</section><section id=\"diagnostics\"><h2>Module ")
                .append("Diagnostics</h2><details><summary>Technical details")
                .append("</summary>");
        appendDiagnosticList(body, moduleEvents(module, events));
        body.append("</details></section>");
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
        final DependencyBodyBoundaryMetadata metadata = boundary(module);
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
            final DependencyBodyBoundaryMetadata metadata) {
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

    private void writeImpactPage(
            final Path target,
            final ModuleAnalysisResult module,
            final ModulePages pages,
            final String overallFile) throws IOException {
        writeDocument(target, "Affected Call Chains",
                "Affected Call Chains",
                breadcrumbs(overallFile, module, pages.impact(),
                        "Affected Call Chains"),
                siblingLinks(pages, "impact"), impactToc(), body -> {
        body
                .append("<h1 id=\"top\">Affected Call Chains: ")
                .append(escape(moduleLabel(module)))
                .append("</h1><section id=\"chains\"><h2>Affected call ")
                .append("chains</h2>");
        final boolean reported = appendCallPathGroups(body,
                module.getFinalPaths(), pages, "final");
        if (!reported) {
            body.append("<p>No affected call chain was found within the ")
                    .append("documented analysis scope.</p>");
        }
        body.append("</section><section id=\"filtered\"><h2>Equivalent ")
                .append("candidate chains</h2>");
        final List<ImpactPath> filtered = module.getCandidatePaths().stream()
                .filter(path -> isEquivalent(module,
                        path.getTerminal().getChangePoint()))
                .toList();
        if (filtered.isEmpty()) {
            body.append("<p>No candidate chain was filtered by method ")
                    .append("equivalence.</p>");
        } else {
            body.append("<details><summary>View candidate chains filtered ")
                    .append("as equivalent</summary>");
            appendCallPathGroups(body, filtered, pages, "filtered");
            body.append("</details>");
        }
        body.append("</section><section id=\"structures\"><h2>Structural ")
                .append("reference chains</h2>");
        if (module.getStructuralPaths().isEmpty()) {
            body.append("<p>No Structural Reference Path was found within ")
                    .append("the documented analysis scope.</p>");
        } else {
            module.getStructuralPaths().stream()
                    .sorted(structuralComparator())
                    .forEach(value -> appendStructuralPath(
                            body, value, pages));
        }
        body.append("</section>");
        });
    }

    private boolean appendCallPathGroups(
            final HtmlSink body,
            final List<ImpactPath> paths,
            final ModulePages pages,
            final String anchorPrefix) {
        final List<DependencyUpgradeKey> jars = paths.stream()
                .map(path -> path.getTerminal().getChangePoint()
                        .getDependencyUpgradeKey()).distinct()
                .sorted(Comparator.comparing(DependencyUpgradeKey::stableKey))
                .toList();
        for (DependencyUpgradeKey jar : jars) {
            body.append("<article class=\"card\" id=\"")
                    .append(attribute(anchorPrefix + "-" + jarAnchor(jar)))
                    .append("\"><h3>")
                    .append(escape(jarLabel(jar))).append("</h3>");
            paths.stream().map(path -> path.getTerminal().getChangePoint())
                    .filter(point -> point.getDependencyUpgradeKey()
                            .equals(jar)).distinct()
                    .sorted(Comparator.comparing(BoundChangePoint::stableKey))
                    .forEach(point -> appendCallPathMember(
                            body, paths, point, pages));
            body.append("</article>");
        }
        return !jars.isEmpty();
    }

    private void appendCallPathMember(
            final HtmlSink body,
            final List<ImpactPath> allPaths,
            final BoundChangePoint point,
            final ModulePages pages) {
        final List<ImpactPath> paths = allPaths.stream()
                .filter(path -> path.getTerminal().getChangePoint()
                        .equals(point))
                .sorted(Comparator.comparing(path ->
                        humanMethod(path.getAffectedMethod())))
                .toList();
        body.append("<section id=\"")
                .append(attribute("impact-" + stableHash(point.stableKey())))
                .append("\"><h4><a href=\"")
                .append(attribute(pages.changes() + "#"
                        + changeAnchor(point)))
                .append("\">")
                .append(escape(memberLabel(point.getChangePoint())))
                .append("</a></h4>");
        for (ImpactPath path : paths) {
            body.append("<div class=\"card\"><strong>")
                    .append(path.getClassification()
                            == ImpactClassification.DIRECT
                            ? "Direct dependency impact"
                            : "Transitive dependency impact")
                    .append("</strong><p>Affected application method: <code>")
                    .append(escape(humanMethod(path.getAffectedMethod())))
                    .append("</code></p><ol class=\"sequence\">");
            for (QueryNode node : path.getNodes()) {
                body.append("<li><code>")
                        .append(escape(humanMethod(node.methodId())))
                        .append("</code></li>");
            }
            body.append("<li>Changed member: <code>")
                    .append(escape(memberLabel(point.getChangePoint())))
                    .append("</code></li></ol><details><summary>Technical ")
                    .append("details</summary><ul>");
            final io.github.dependencyanalysis.impact.ReferenceEvidence
                    terminal = path.getTerminal().getImpactEvidence();
            body.append("<li>Terminal evidence: ")
                    .append(escape(path.getTerminal().getEvidenceKind()
                            .name()))
                    .append(" / ")
                    .append(escape(path.getTerminal().getEvidenceMechanism()
                            .name()))
                    .append("; ")
                    .append(escape(path.getTerminal().getEvidence()))
                    .append("</li><li>Terminal target: ")
                    .append(escape(terminal.target().stableKey()))
                    .append("</li><li>Terminal location: ")
                    .append(escape(terminal.location().source()))
                    .append("; bytecode PC=")
                    .append(terminal.location().bytecodePc() < 0
                            ? "N/A" : terminal.location().bytecodePc())
                    .append("</li><li>Terminal detail: ")
                    .append(escape(terminal.detail()))
                    .append("</li></ul></details></div>");
        }
        body.append("</section>");
    }

    private void appendStructuralPath(
            final HtmlSink body,
            final StructuralReferencePath path,
            final ModulePages pages) {
        body.append("<article class=\"card\"><strong>")
                .append(path.getClassification() == ImpactClassification.DIRECT
                        ? "Direct structural impact"
                        : "Transitive structural impact")
                .append("</strong><ol class=\"sequence\">");
        for (QueryNode node : path.getNodes()) {
            body.append("<li><code>")
                    .append(escape(humanMethod(node.methodId())))
                    .append("</code></li>");
        }
        body.append("<li>Application class/member: <code>")
                .append(escape(structuralOwner(path)))
                .append("</code></li><li>")
                .append(escape(path.getReference().getKind().getLabel()))
                .append("</li><li>Changed dependency class: <a href=\"")
                .append(attribute(pages.changes() + "#"
                        + changeAnchor(path.getChangePoint())))
                .append("\"><code>")
                .append(escape(path.getReference().getChangedClass()
                        .replace('/', '.')))
                .append("</code></a></li></ol><details><summary>Technical ")
                .append("details</summary><ul><li>Origin: ")
                .append(escape(path.getReference().getOrigin().name()))
                .append("</li><li>Raw evidence: ")
                .append(escape(path.getReference().getEvidence()))
                .append("</li>");
        for (QueryNode node : path.getNodes()) {
            body.append("<li>Context: ")
                    .append(escape(contextEvidence(node))).append("</li>");
        }
        body.append("</ul></details></article>");
    }

    private void writeChangesPage(
            final Path target,
            final ModuleAnalysisResult module,
            final ModulePages pages,
            final String overallFile) throws IOException {
        writeDocument(target, "Dependency Changes", "Dependency Changes",
                breadcrumbs(overallFile, module, pages.changes(),
                        "Dependency Changes"),
                siblingLinks(pages, "changes"), changesToc(), body -> {
        body
                .append("<h1 id=\"top\">Dependency Changes: ")
                .append(escape(moduleLabel(module)))
                .append("</h1><section id=\"dependencies\"><h2>Changed ")
                .append("dependencies</h2>");
        final java.util.Set<BoundChangePoint> shown =
                new java.util.LinkedHashSet<>(
                        module.getCodeComparisons().keySet());
        module.getDispositions().forEach((point, disposition) -> {
            if (disposition
                    == ChangePointDisposition.SHADOWED_BY_DUPLICATE) {
                shown.add(point);
            }
        });
        final List<BoundChangePoint> relevant = shown.stream()
                .sorted(Comparator.comparing(BoundChangePoint::stableKey))
                .toList();
        final Map<String, List<BoundChangePoint>> memberGroups =
                memberGroups(relevant);
        if (memberGroups.isEmpty()) {
            body.append("<p>No dependency member associated with a candidate ")
                    .append("or final impact path was found within the ")
                    .append("documented analysis scope.</p>");
        }
        for (Map.Entry<String, List<BoundChangePoint>> group
                : memberGroups.entrySet()) {
            appendDependencySection(body, module, group.getKey(),
                    group.getValue());
        }
        body.append("<details><summary>Technical details</summary><table>")
                .append(row("Raw changed members",
                        module.getUnit().getChangePoints().size()))
                .append(row("Members shown", relevant.size()))
                .append(row("Raw members not shown",
                        Math.max(0, module.getUnit().getChangePoints().size()
                                - relevant.size())))
                .append("</table></details></section>");
        });
    }

    private void appendDependencySection(
            final HtmlSink body,
            final ModuleAnalysisResult module,
            final String key,
            final List<BoundChangePoint> points) {
        final DependencyUpgradeKey upgrade = points.get(0)
                .getDependencyUpgradeKey();
        body.append("<article class=\"card\" id=\"")
                .append(attribute("dependency-" + stableHash(key)))
                .append("\"><h3>")
                .append(escape(jarLabel(upgrade))).append("</h3><p>Scope: ")
                .append("<code>").append(escape(upgrade.getScope().getValue()))
                .append("</code>.</p>");
        appendMemberCategory(body, module, "Methods", points,
                kind -> kind.name().startsWith("METHOD_"));
        appendMemberCategory(body, module, "Fields", points,
                kind -> kind.name().startsWith("FIELD_"));
        appendMemberCategory(body, module, "Classes", points,
                kind -> kind.name().startsWith("CLASS_"));
        body.append("</article>");
    }

    private void appendMemberCategory(
            final HtmlSink body,
            final ModuleAnalysisResult module,
            final String title,
            final List<BoundChangePoint> points,
            final java.util.function.Predicate<ChangePointKind> filter) {
        final List<BoundChangePoint> selected = points.stream()
                .filter(point -> filter.test(
                        point.getChangePoint().getKind()))
                .sorted(Comparator.comparing(BoundChangePoint::stableKey))
                .toList();
        if (selected.isEmpty()) {
            return;
        }
        body.append("<h4>").append(title).append("</h4>");
        for (BoundChangePoint bound : selected) {
            final ChangePoint point = bound.getChangePoint();
            body.append("<details class=\"member\" id=\"")
                    .append(attribute(changeAnchor(bound)))
                    .append("\"><summary><strong>")
                    .append(escape(changeKindText(point.getKind())))
                    .append(":</strong> <code>")
                    .append(escape(memberLabel(point)))
                    .append("</code>")
                    .append(memberBadges(module, bound))
                    .append("</summary>")
                    .append(shadowedExplanation(module, bound))
                    .append(codeComparison(module, bound))
                    .append(memberTechnicalDetails(module, bound))
                    .append("</details>");
        }
    }

    private String memberBadges(
            final ModuleAnalysisResult module,
            final BoundChangePoint point) {
        final HtmlSink result = HtmlSink.memory();
        if (module.getFinalPaths().stream().anyMatch(path -> path.getTerminal()
                .getChangePoint().equals(point))) {
            result.append("<span class=\"badge\">Affected</span>");
        }
        if (isEquivalent(module, point)) {
            result.append("<span class=\"badge filtered\">Equivalent ")
                    .append("(filtered)</span>");
        }
        if (module.getStructuralPaths().stream().anyMatch(path ->
                path.getChangePoint().equals(point))) {
            result.append("<span class=\"badge structural\">Structural ")
                    .append("impact</span>");
        }
        if (module.getDispositions().get(point)
                == ChangePointDisposition.SHADOWED_BY_DUPLICATE) {
            result.append("<span class=\"badge filtered\">Shadowed by ")
                    .append("duplicate</span>");
        }
        if (module.getDispositions().get(point)
                == ChangePointDisposition.ACCESS_REMAINS_VALID) {
            result.append("<span class=\"badge filtered\">Access remains "
                    + "valid</span>");
        }
        return result.toString();
    }

    private String shadowedExplanation(
            final ModuleAnalysisResult module,
            final BoundChangePoint point) {
        if (module.getDispositions().get(point)
                != ChangePointDisposition.SHADOWED_BY_DUPLICATE) {
            return "";
        }
        final DuplicateClassResolution resolution = duplicateResolution(
                module, point);
        if (resolution == null) {
            return "<p class=\"warn\">This changed definition was shadowed "
                    + "by classpath duplicate resolution, so no Impact Path "
                    + "was generated.</p>";
        }
        return "<p class=\"warn\">This changed definition was shadowed by "
                + "<code>" + escape(ownershipLabel(
                resolution.getWinner())) + "</code> ("
                + escape(resolution.getPrecedenceReason())
                + "), so no Impact Path was generated.</p>";
    }

    private String codeComparison(
            final ModuleAnalysisResult module,
            final BoundChangePoint bound) {
        final CodeComparisonEvidence evidence = module.getCodeComparisons()
                .get(bound);
        if (evidence == null) {
            return "";
        }
        final HtmlSink result = HtmlSink.memory()
                .append("<details><summary>View code changes</summary>")
                .append("<p class=\"muted\">Decompiled Java ")
                .append("representation</p>");
        if (evidence.getStatus() == CodeComparisonStatus.AVAILABLE) {
            result.append("<pre class=\"diff\"><code>")
                    .append(escape(evidence.getUnifiedDiff()))
                    .append("</code></pre>");
        } else if (evidence.getStatus()
                == CodeComparisonStatus.ASM_FALLBACK) {
            result.append("<p>Decompiled Java text is identical. Bytecode ")
                    .append("evidence is available in Technical details.</p>");
        } else {
            result.append("<p class=\"warn\">Code comparison unavailable: ")
                    .append(escape(evidence.getReason())).append("</p>");
        }
        if (!evidence.getAsmFallback().isBlank()) {
            result.append("<details><summary>Technical details: ASM ")
                    .append("instruction diff</summary><pre class=\"diff\">")
                    .append("<code>")
                    .append(escape(evidence.getAsmFallback()))
                    .append("</code></pre></details>");
        }
        return result.append("</details>").toString();
    }

    private String memberTechnicalDetails(
            final ModuleAnalysisResult module,
            final BoundChangePoint bound) {
        final ChangePoint point = bound.getChangePoint();
        final MethodEquivalenceResult equivalence = module
                .getEquivalenceResults().get(bound);
        final HtmlSink result = HtmlSink.memory(
                "<details><summary>Technical details</summary><table>")
                .append(row("Raw ChangePointKind", point.getKind()))
                .append(row("Raw disposition",
                        module.getDispositions().get(bound)))
                .append(row("Disposition explanation", dispositionText(
                        module.getDispositions().get(bound))))
                .append(row("Old descriptor", point.getOldDescriptor()))
                .append(row("New descriptor", point.getNewDescriptor()))
                .append(row("Old hash", point.getOldHash()))
                .append(row("New hash", point.getNewHash()))
                .append(row("Old access", point.getAccessTransition()
                        .map(value -> value.oldAccess().name())
                        .orElse("Not applicable")))
                .append(row("New access", point.getAccessTransition()
                        .map(value -> value.newAccess().name())
                        .orElse("Not applicable")))
                .append(row("SSA status", equivalence == null
                        ? "Not compared" : equivalence.getStatus()))
                .append(row("SSA reason", equivalence == null
                        ? "Not compared" : equivalence.getReason()))
                .append(row("Old artifact", bound
                        .getDependencyUpgradeKey().getOldArtifact()))
                .append(row("New artifact", bound
                        .getDependencyUpgradeKey().getNewArtifact()));
        final List<ImpactEvidence> observations = module.getObservations()
                .getOrDefault(bound, List.of());
        result.append(row("Access/reference observations",
                observations.size()));
        for (int index = 0; index < observations.size(); index++) {
            result.append(row("Observation " + (index + 1),
                    observations.get(index).render()));
        }
        final DuplicateClassResolution resolution = duplicateResolution(
                module, bound);
        if (resolution != null) {
            result.append(row("Duplicate winner",
                            ownershipLabel(resolution.getWinner())))
                    .append(row("Duplicate winner logical source",
                            resolution.getWinner().getSource()))
                    .append(row("Duplicate precedence",
                            resolution.getPrecedenceReason()));
        }
        return result.append("</table></details>").toString();
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
                + sibling(pages.impact(), "Affected Call Chains",
                "impact".equals(selected))
                + sibling(pages.changes(), "Dependency Changes",
                "changes".equals(selected)) + "</nav>";
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
                        + "method control flow and data dependencies. Only a "
                        + "proven match removes candidate chains."))
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
            case RTA -> term("RTA", "Rapid Type Analysis uses the global "
                    + "set of instantiated compatible classes for virtual "
                    + "and interface reachability. It does not track "
                    + "allocation-site or value points-to dataflow.");
            case ZERO_CFA -> term("ZeroCFA", "The conservative WALA call "
                    + "analysis used here. It merges allocations by concrete "
                    + "class while preserving constant-specific identity.");
            case OPTIMIZED_ZERO_ONE_CFA -> term("Optimized 0-1-CFA",
                    "The conservative WALA call analysis used here. It "
                    + "preserves allocation and constant-specific identity "
                    + "while merging String, Throwable, primitive-holder, "
                    + "and excessive same-type allocations to reduce "
                    + "analysis cost.");
            case K_OBJ -> term(
                    "k-Object",
                    "The context-sensitive WALA call analysis used here. "
                    + "It distinguishes a configured number of receiver "
                    + "allocation sites, keeps exact allocation-site and "
                    + "constant-specific identity without smushing, and "
                    + "can therefore cost substantially more time and "
                    + "memory than the other algorithms.");
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

    private void appendDiagnostics(
            final HtmlSink body,
            final List<DiagnosticEvent> events) {
        body.append("<section id=\"diagnostics\"><h2>Diagnostics</h2>");
        body.append("<details><summary>Technical details</summary>");
        appendDiagnosticList(body, events);
        body.append("</details></section>");
    }

    private void appendDiagnosticList(
            final HtmlSink body,
            final List<DiagnosticEvent> events) {
        if (events.isEmpty()) {
            body.append("<p>No diagnostic event was recorded.</p>");
            return;
        }
        body.append("<ul>");
        for (DiagnosticEvent event : events) {
            body.append("<li><code>")
                    .append(escape(diagnosticFormatter.formatPrefix(event)))
                    .append("</code> ").append(escape(event.getMessage()))
                    .append("</li>");
        }
        body.append("</ul>");
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

    private Map<String, List<BoundChangePoint>> memberGroups(
            final List<BoundChangePoint> points) {
        final Map<String, List<BoundChangePoint>> result =
                new LinkedHashMap<>();
        points.stream().sorted(Comparator.comparing(
                        BoundChangePoint::stableKey))
                .forEach(point -> result.computeIfAbsent(
                        upgradeGroupKey(point.getDependencyUpgradeKey()),
                        ignored -> new ArrayList<>()).add(point));
        return result;
    }

    private String upgradeGroupKey(final DependencyUpgradeKey key) {
        final ArtifactCoord old = key.getOldArtifact();
        final ArtifactCoord target = key.getNewArtifact();
        return old.getGroupId() + ":" + old.getArtifactId() + ":"
                + old.getType() + ":" + old.getClassifier() + ":"
                + old.getVersion() + ":" + target.getVersion();
    }

    private String jarLabel(final DependencyUpgradeKey key) {
        return key.getOldArtifact().getGroupId() + ":"
                + key.getOldArtifact().getArtifactId() + " "
                + key.getOldArtifact().getVersion() + " → "
                + key.getNewArtifact().getVersion();
    }

    private String memberLabel(final ChangePoint point) {
        final String owner = point.getOwner().replace('/', '.');
        return point.getName() == null ? owner
                : owner + "#" + point.getName();
    }

    private String changeKindText(final ChangePointKind kind) {
        return switch (kind) {
            case CLASS_ADDED -> "Class added";
            case CLASS_REMOVED -> "Class removed";
            case CLASS_ACCESS_NARROWED -> "Class access narrowed";
            case METHOD_ADDED -> "Method added";
            case METHOD_REMOVED -> "Method removed";
            case METHOD_DESCRIPTOR_CHANGED -> "Method signature changed";
            case METHOD_BODY_CHANGED -> "Method implementation changed";
            case METHOD_ACCESS_NARROWED -> "Method access narrowed";
            case FIELD_ADDED -> "Field added";
            case FIELD_REMOVED -> "Field removed";
            case FIELD_DESCRIPTOR_CHANGED -> "Field type changed";
            case FIELD_ACCESS_NARROWED -> "Field access narrowed";
            case SERVICE_PROVIDER_REGISTRATION_REMOVED ->
                    "Service provider registration removed";
        };
    }

    private String dispositionText(final ChangePointDisposition value) {
        if (value == null) {
            return "No final classification was recorded.";
        }
        return switch (value) {
            case IMPACT_REPORTED -> "At least one affected application call "
                    + "chain or class structure reference was found.";
            case FILTERED_EQUIVALENT -> "Candidate chains were removed because "
                    + "the old and new method models were proven equivalent.";
            case CHANGE_KIND_NOT_ANALYZED -> "This added member does not "
                    + "trigger backward impact analysis.";
            case SHADOWED_BY_DUPLICATE -> "The changed class definition is "
                    + "shadowed by the selected classpath winner.";
            case TARGET_NOT_FOUND -> "The target member could not be resolved.";
            case DECLARED_REFERENCE_NOT_FOUND -> "No reachable bytecode "
                    + "reference to the previous member was found.";
            case ACCESS_REMAINS_VALID -> "Relevant bytecode references were "
                    + "found and remain legal under the narrowed access.";
            case NO_PROJECT_PATH -> "No application method was found that can "
                    + "reach the changed member within the analysis scope.";
            case UNATTRIBUTABLE_CLASS_REFERENCE -> "A class reference was "
                    + "found but could not be assigned to a method call chain.";
            case UNREACHABLE_STRUCTURAL_REFERENCE -> "A class structure "
                    + "reference was found without reachable code evidence.";
        };
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

    private boolean isEquivalent(
            final ModuleAnalysisResult module,
            final BoundChangePoint point) {
        final MethodEquivalenceResult result = module
                .getEquivalenceResults().get(point);
        return result != null && result.getStatus()
                == MethodEquivalenceStatus.PROVEN_EQUIVALENT;
    }

    private Comparator<StructuralReferencePath> structuralComparator() {
        return Comparator.comparing((StructuralReferencePath path) ->
                        path.getChangePoint().stableKey())
                .thenComparing(path -> path.getReference().stableKey())
                .thenComparing(path -> path.getAffectedMethod() == null ? ""
                        : humanMethod(path.getAffectedMethod()));
    }

    private String structuralOwner(final StructuralReferencePath path) {
        final String member = path.getReference().getReferencingMember();
        return path.getReference().getReferencingClass().replace('/', '.')
                + (member.isBlank() ? "" : "#" + member);
    }

    private String humanMethod(final MethodId method) {
        return method.owner().replace('/', '.') + "#" + method.name();
    }

    private String contextEvidence(final QueryNode node) {
        if (node instanceof WalaQueryNode wala) {
            return wala.walaNode().getContext().toString();
        }
        if (node instanceof SnapshotQueryNode snapshot) {
            return snapshot.context();
        }
        return node.origin() + " synthetic evidence";
    }

    private long pathCount(
            final AnalysisRunResult run, final boolean candidate) {
        return run.getModuleResults().stream().mapToLong(module ->
                candidate ? module.getCandidatePaths().size()
                        : module.getFinalPaths().size()).sum();
    }

    private long rawChangeCount(final AnalysisRunResult run) {
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

    private DuplicateClassResolution duplicateResolution(
            final ModuleAnalysisResult module,
            final BoundChangePoint point) {
        return duplicateResolutions(module).stream()
                .filter(value -> value.getBinaryName().equals(
                        normalizedClassName(
                                point.getChangePoint().getOwner())))
                .findFirst().orElse(null);
    }

    private String normalizedClassName(final String value) {
        final String normalized = value.startsWith("L")
                ? value.substring(1) : value;
        return normalized.replace('.', '/');
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
        module.getFinalPaths().stream().map(ImpactPath::getAffectedMethod)
                .forEach(methods::add);
        module.getStructuralPaths().stream()
                .map(StructuralReferencePath::getAffectedMethod)
                .filter(java.util.Objects::nonNull).forEach(methods::add);
        return methods.size();
    }

    private long affectedClassCount(final ModuleAnalysisResult module) {
        final java.util.Set<String> classes = new java.util.LinkedHashSet<>();
        module.getFinalPaths().stream()
                .map(path -> path.getAffectedMethod().owner())
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
        return equivalenceCount(modules,
                MethodEquivalenceStatus.PROVEN_EQUIVALENT) + " / "
                + equivalenceCount(modules,
                MethodEquivalenceStatus.DIFFERENT) + " / "
                + equivalenceCount(modules,
                MethodEquivalenceStatus.UNKNOWN);
    }

    private String ssaCounts(
            final List<ModuleAnalysisResult> modules,
            final boolean enabled) {
        return enabled ? ssaCounts(modules) : "not run";
    }

    private String semanticComparisonStatus(final boolean enabled) {
        return enabled ? "enabled (experimental)"
                : "disabled (experimental)";
    }

    private String ssaWorkers(final boolean enabled) {
        return enabled ? "1 (experimental)" : "0 (disabled)";
    }

    private long equivalenceCount(
            final List<ModuleAnalysisResult> modules,
            final MethodEquivalenceStatus status) {
        return modules.stream().flatMap(module -> module
                        .getEquivalenceResults().values().stream())
                .filter(result -> result.getStatus() == status).count();
    }

    private List<DiagnosticEvent> moduleEvents(
            final ModuleAnalysisResult module,
            final List<DiagnosticEvent> events) {
        final String key = module.getModuleId().stableKey();
        return events.stream().filter(event -> key.equals(event.getModule()))
                .toList();
    }

    private long contextCount(final ModuleAnalysisResult module) {
        if (module.getSession() != null) {
            return module.getSession().getGraph().stream()
                    .map(com.ibm.wala.ipa.callgraph.CGNode::getContext)
                    .distinct().count();
        }
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
                + row("WALA Context count", contextCount(module));
    }

    private Object entrypointCount(final ModuleAnalysisResult module) {
        return module.getSession() != null
                ? module.getSession().getEntrypointCount()
                : module.getCallGraphSnapshot() == null ? "Not available"
                : module.getCallGraphSnapshot().entrypointCount();
    }

    private Object selectedEntrypointClassCount(
            final ModuleAnalysisResult module) {
        return module.getSession() != null
                ? module.getSession().getSelectedEntrypointClassCount()
                : module.getCallGraphSnapshot() == null ? "Not available"
                : module.getCallGraphSnapshot()
                .selectedEntrypointClassCount();
    }

    private Object parameterCandidateCount(
            final ModuleAnalysisResult module) {
        return module.getSession() != null
                ? module.getSession().getParameterCandidateCount()
                : module.getCallGraphSnapshot() == null ? "Not available"
                : module.getCallGraphSnapshot().parameterCandidateCount();
    }

    private DependencyBodyBoundaryMetadata boundary(
            final ModuleAnalysisResult module) {
        return module.getSession() != null
                ? module.getSession().getDependencyBoundary()
                : module.getCallGraphSnapshot() == null
                ? DependencyBodyBoundaryMetadata.empty()
                : module.getCallGraphSnapshot().dependencyBoundary();
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

    private String changeAnchor(final BoundChangePoint point) {
        return "change-" + stableHash(point.stableKey());
    }

    private String jarAnchor(final DependencyUpgradeKey key) {
        return "jar-" + stableHash(key.stableKey());
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
                "modules", "Modules", "preflight", "Preflight checks",
                "diagnostics", "Diagnostics");
    }

    private String moduleIndexToc() {
        return toc("summary", "Summary", "scope", "Analysis scope",
                "dependency-body-boundary", "Dependency body boundary",
                "duplicates", "Duplicate class resolution",
                "metrics", "Runtime metrics", "limits", "Limitations",
                "diagnostics", "Diagnostics");
    }

    private String impactToc() {
        return toc("chains", "Affected call chains", "filtered",
                "Equivalent candidates", "structures",
                "Structural reference chains");
    }

    private String changesToc() {
        return toc("dependencies", "Changed dependencies");
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

        @Override
        public String toString() {
            return target.toString();
        }
    }

    /**
     * Owned sibling filenames for one Module.
     *
     * @param index Module Index filename
     * @param impact Affected Call Chains filename
     * @param changes Dependency Changes filename
     */
    private record ModulePages(String index, String impact, String changes) {
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
     * @param semanticComparisonEnabled experimental comparison toggle
     */
    private record ModuleRuntime(
            String jdkVersion,
            String mavenVersion,
            String mavenSource,
            String entrypointBoundary,
            String pluginVersion,
            boolean semanticComparisonEnabled) {
    }
}
