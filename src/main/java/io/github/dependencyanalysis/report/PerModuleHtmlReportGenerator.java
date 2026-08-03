package io.github.dependencyanalysis.report;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.callgraph.CallGraphStats;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ChangeType;
import io.github.dependencyanalysis.dependency.DependencyChange;
import io.github.dependencyanalysis.diagnostic.DiagnosticEvent;
import io.github.dependencyanalysis.impact.AnalysisRunResult;
import io.github.dependencyanalysis.impact.BoundChangePoint;
import io.github.dependencyanalysis.impact.ChangePointDisposition;
import io.github.dependencyanalysis.impact.DependencyUpgradeKey;
import io.github.dependencyanalysis.impact.ImpactClassification;
import io.github.dependencyanalysis.impact.ImpactPath;
import io.github.dependencyanalysis.impact.JarDiffFailure;
import io.github.dependencyanalysis.impact.MethodEquivalenceResult;
import io.github.dependencyanalysis.impact.MethodEquivalenceStatus;
import io.github.dependencyanalysis.impact.ModuleAnalysisResult;
import io.github.dependencyanalysis.impact.ModuleAnalysisStatus;
import io.github.dependencyanalysis.impact.QueryEdge;
import io.github.dependencyanalysis.impact.QueryNode;
import io.github.dependencyanalysis.impact.StructuralImpact;
import io.github.dependencyanalysis.preflight.PreflightReport;
import io.github.dependencyanalysis.preflight.PreflightResult;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.MavenDependencyPluginRuntime;
import io.github.dependencyanalysis.runtime.MavenRuntimeDescriptor;

import java.io.IOException;
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
                    "embedded " + plugin.getVersion());
            final Map<ModuleAnalysisResult, ModulePages> pages =
                    writeModulePages(run, modules, events,
                            absolute.getFileName().toString(), moduleRuntime);
            final OverallContext context = new OverallContext(
                    maven, plugin, javaRuntime, ownedName, pages);
            Files.writeString(staging.resolve(absolute.getFileName()),
                    overallPage(run, events, preflight, context));
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
            Files.writeString(directory.resolve(pages.index()),
                    moduleIndexPage(module, events, pages, overallFile,
                            runtime));
            Files.writeString(directory.resolve(pages.impact()),
                    impactPage(module, pages, overallFile));
            Files.writeString(directory.resolve(pages.changes()),
                    changesPage(module, pages, overallFile));
            result.put(module, pages);
        }
        return result;
    }

    private String overallPage(
            final AnalysisRunResult run,
            final List<DiagnosticEvent> events,
            final PreflightReport preflight,
            final OverallContext context) {
        final StringBuilder body = new StringBuilder()
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
        appendTerminology(body);
        body.append("<section id=\"run\"><h2>Run summary</h2><table>")
                .append(row("Status", statusText(run.getStatus().name())))
                .append(row("Analysis mode", run.getMode()))
                .append(row("Target JDK",
                        context.javaRuntime().getVersion()))
                .append(row("Apache Maven runtime",
                        context.maven().getVersion()))
                .append(row("Maven Dependency Plugin",
                        "embedded " + context.plugin().getVersion()))
                .append(row("Modules analyzed in parallel",
                        run.getActualParallelism() + " (configured "
                                + run.getConfiguredParallelism() + ")"))
                .append(row("JAR comparisons in parallel",
                        run.getActualJarDiffWorkers() + " (configured "
                                + run.getConfiguredJarDiffWorkers() + ")"))
                .append(row("Dependency changes",
                        run.getDependencyChanges().size()))
                .append(row("Candidate / final call chains",
                        pathCount(run, true) + " / "
                                + pathCount(run, false)))
                .append("</table>");
        appendStageMetrics(body, run.getStageElapsedMillis());
        body.append("<details><summary>Technical details</summary><table>")
                .append(row("Algorithm", "vanilla-0-1-cfa"))
                .append(row("WALA", walaVersion()))
                .append(row("SSA equivalence workers", 1))
                .append(row("Raw changed members", rawChangeCount(run)))
                .append(row("SSA equivalent / different / unknown",
                        ssaCounts(run.getModuleResults())))
                .append("</table></details></section>")
                .append("<section id=\"modules\"><h2>Modules</h2>")
                .append("<table><tr><th>Module</th><th>Status</th>")
                .append("<th>Explanation</th><th>Dependencies changed</th>")
                .append("<th>Final call chains</th><th>Affected methods</th>")
                .append("</tr>");
        for (ModuleAnalysisResult module : run.getModuleResults()) {
            final ModulePages modulePages = context.pages().get(module);
            body.append("<tr><td>");
            if (modulePages == null) {
                body.append(escape(module.getModuleId().stableKey()));
            } else {
                body.append("<a href=\"")
                        .append(attribute(context.ownedName() + "/"
                                + modulePages.index()))
                        .append("\">")
                        .append(escape(module.getModuleId().stableKey()))
                        .append("</a>");
            }
            body.append("</td><td>")
                    .append(escape(statusText(module.getStatus().name())))
                    .append("</td><td>")
                    .append(escape(reasonText(module)))
                    .append("</td><td>")
                    .append(module.getUnit().getDependencyChanges().size())
                    .append("</td><td>")
                    .append(module.getFinalPaths().size())
                    .append("</td><td>")
                    .append(affectedMethodCount(module))
                    .append("</td></tr>");
        }
        body.append("</table></section>");
        appendPreflight(body, preflight);
        appendDiagnostics(body, events);
        return document("Impact Analysis Report", "Overall", "", "",
                overallToc(), body.toString());
    }

    private String moduleIndexPage(
            final ModuleAnalysisResult module,
            final List<DiagnosticEvent> events,
            final ModulePages pages,
            final String overallFile,
            final ModuleRuntime runtime) {
        final StringBuilder body = new StringBuilder()
                .append("<h1 id=\"top\">Module summary: ")
                .append(escape(module.getModuleId().stableKey()))
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
                        module.getStructuralImpacts().size()))
                .append(row("Dependency / member changes",
                        module.getUnit().getDependencyChanges().size()
                                + " / "
                                + module.getUnit().getChangePoints().size()))
                .append(row("Target JDK", runtime.jdkVersion()))
                .append(row("Apache Maven runtime", runtime.mavenVersion()))
                .append(row("Maven Dependency Plugin",
                        runtime.pluginVersion()))
                .append(row("Module analysis worker",
                        "1 (call relationship build and query are "
                                + "single-threaded)"))
                .append(row("Analysis elapsed", module.getElapsedMillis()
                        + " ms")).append("</table></section>")
                .append("<section id=\"scope\"><h2>Analysis scope</h2><p>")
                .append("Application classes: <code>")
                .append(escape(module.getUnit().getProjectClasses().toString()))
                .append("</code>. Reactor dependencies: ")
                .append(module.getUnit().getReactorDependencyClasses().size())
                .append(". External dependencies: ")
                .append(module.getUnit().getTargetArtifacts().size())
                .append(".</p><details><summary>Technical details</summary>")
                .append("<ul>");
        module.getUnit().getReactorDependencyClasses().forEach(path -> body
                .append("<li>Reactor dependency: <code>")
                .append(escape(path.toString())).append("</code></li>"));
        module.getUnit().getTargetArtifacts().forEach(artifact -> body
                .append("<li>")
                .append(escape(artifact.getArtifact().toString()))
                .append(" [").append(escape(artifact.getScope().name()))
                .append("] <code>")
                .append(escape(artifact.getPath().toString()))
                .append("</code></li>"));
        body.append("</ul></details></section>")
                .append("<section id=\"metrics\"><h2>Runtime metrics</h2>")
                .append("<table>")
                .append(row("Entry methods", module.getSession() == null
                        ? "Not available"
                        : module.getSession().getEntrypointCount()))
                .append(row("Call relationships", module.getCallGraphStats()
                        == null ? "Not available"
                        : module.getCallGraphStats().edgeCount()))
                .append("</table>");
        appendStageMetrics(body, module.getStageElapsedMillis());
        body.append("<details><summary>Technical details</summary><table>")
                .append(sessionRows(module))
                .append(row("Raw status", module.getStatus()))
                .append(row("Raw reason", module.getReason()))
                .append(row("SSA equivalent / different / unknown",
                        ssaCounts(List.of(module))))
                .append("</table></details></section>")
                .append("<section id=\"limits\"><h2>Coverage limitations")
                .append("</h2>");
        appendList(body, module.getLimitations(),
                "No module-specific limitation was recorded.");
        body.append("</section><section id=\"diagnostics\"><h2>Module ")
                .append("Diagnostics</h2>");
        appendDiagnosticList(body, moduleEvents(module, events));
        body.append("</section>");
        return document("Module summary", "Module Index",
                breadcrumbs(overallFile, module, pages.index(),
                        "Module Index"), siblingLinks(pages, "index"),
                moduleIndexToc(), body.toString());
    }

    private String impactPage(
            final ModuleAnalysisResult module,
            final ModulePages pages,
            final String overallFile) {
        final StringBuilder body = new StringBuilder()
                .append("<h1 id=\"top\">Affected Call Chains: ")
                .append(escape(module.getModuleId().stableKey()))
                .append("</h1><p>A call chain starts at an application method ")
                .append("and ends at a changed dependency member.</p>")
                .append("<section id=\"chains\"><h2>Affected call chains")
                .append("</h2>");
        final List<DependencyUpgradeKey> jars = module.getUnit()
                .getChangePoints().stream()
                .map(BoundChangePoint::getDependencyUpgradeKey).distinct()
                .sorted(Comparator.comparing(DependencyUpgradeKey::stableKey))
                .toList();
        boolean reported = false;
        for (DependencyUpgradeKey jar : jars) {
            final List<BoundChangePoint> points = module.getUnit()
                    .getChangePoints().stream()
                    .filter(point -> point.getDependencyUpgradeKey()
                            .equals(jar))
                    .sorted(Comparator.comparing(BoundChangePoint::stableKey))
                    .toList();
            final boolean hasEvidence = points.stream().anyMatch(point ->
                    hasFinalEvidence(module, point));
            if (!hasEvidence) {
                continue;
            }
            reported = true;
            body.append("<article class=\"card\" id=\"")
                    .append(attribute(jarAnchor(jar)))
                    .append("\"><h3>")
                    .append(escape(jarLabel(jar))).append("</h3>");
            for (BoundChangePoint point : points) {
                appendImpactMember(body, module, point, pages);
            }
            body.append("</article>");
        }
        if (!reported) {
            body.append("<p>No affected call chain was found within the ")
                    .append("documented analysis scope.</p>");
        }
        body.append("</section><section id=\"structures\"><h2>Class ")
                .append("structure references</h2>");
        if (module.getStructuralImpacts().isEmpty()) {
            body.append("<p>No class structure reference was reported.</p>");
        } else {
            body.append("<ul>");
            module.getStructuralImpacts().stream()
                    .sorted(Comparator.comparing(value ->
                            value.getChangePoint().stableKey() + ":"
                                    + value.getReferencingClass()))
                    .forEach(value -> body.append("<li><code>")
                            .append(escape(value.getReferencingClass()))
                            .append("</code> references <a href=\"")
                            .append(attribute(pages.changes() + "#"
                                    + changeAnchor(
                                    value.getChangePoint())))
                            .append("\">")
                            .append(escape(memberLabel(
                                    value.getChangePoint().getChangePoint())))
                            .append("</a>")
                            .append("<details><summary>Technical details")
                            .append("</summary><code>")
                            .append(escape(value.getOrigin().name()))
                            .append("</code>: ")
                            .append(escape(value.getEvidence()))
                            .append("</details></li>"));
            body.append("</ul>");
        }
        body.append("</section>");
        return document("Affected Call Chains", "Affected Call Chains",
                breadcrumbs(overallFile, module, pages.impact(),
                        "Affected Call Chains"),
                siblingLinks(pages, "impact"), impactToc(), body.toString());
    }

    private void appendImpactMember(
            final StringBuilder body,
            final ModuleAnalysisResult module,
            final BoundChangePoint point,
            final ModulePages pages) {
        final List<ImpactPath> paths = module.getFinalPaths().stream()
                .filter(path -> path.getTerminal().getChangePoint()
                        .equals(point))
                .sorted(Comparator.comparing(path ->
                        path.getAffectedMethod().toString()))
                .toList();
        final List<StructuralImpact> structures = module
                .getStructuralImpacts().stream()
                .filter(value -> value.getChangePoint().equals(point))
                .toList();
        if (paths.isEmpty() && structures.isEmpty()) {
            return;
        }
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
                    .append(escape(path.getAffectedMethod().toString()))
                    .append("</code></p><ol class=\"sequence\">");
            for (QueryNode node : path.getNodes()) {
                body.append("<li><code>")
                        .append(escape(node.methodId().toString()))
                        .append("</code></li>");
            }
            body.append("<li>Changed member: <code>")
                    .append(escape(memberLabel(point.getChangePoint())))
                    .append("</code></li></ol><details><summary>Technical ")
                    .append("details</summary><ul>");
            for (QueryEdge edge : path.getOrderedEdges()) {
                body.append("<li>").append(escape(edge.getKind().name()))
                        .append("; bytecode PC=")
                        .append(edge.getBytecodePc()).append("; ")
                        .append(escape(edge.getEvidence())).append("</li>");
            }
            body.append("<li>Terminal evidence: ")
                    .append(escape(path.getTerminal().getEdgeKind().name()))
                    .append("; ")
                    .append(escape(path.getTerminal().getEvidence()))
                    .append("</li></ul></details></div>");
        }
        body.append("</section>");
    }

    private String changesPage(
            final ModuleAnalysisResult module,
            final ModulePages pages,
            final String overallFile) {
        final StringBuilder body = new StringBuilder()
                .append("<h1 id=\"top\">Dependency Changes: ")
                .append(escape(module.getModuleId().stableKey()))
                .append("</h1><section id=\"dependencies\"><h2>Changed ")
                .append("dependencies</h2>");
        final Map<String, List<DependencyChange>> groups =
                dependencyGroups(module.getUnit().getDependencyChanges());
        final Map<String, List<BoundChangePoint>> memberGroups =
                memberGroups(module.getUnit().getChangePoints());
        final Map<String, List<JarDiffFailure>> failureGroups =
                failureGroups(module.getUnit().getJarDiffFailures());
        final List<String> keys = new ArrayList<>();
        keys.addAll(groups.keySet());
        memberGroups.keySet().stream().filter(key -> !keys.contains(key))
                .forEach(keys::add);
        failureGroups.keySet().stream().filter(key -> !keys.contains(key))
                .forEach(keys::add);
        keys.sort(String::compareTo);
        if (keys.isEmpty()) {
            body.append("<p>No dependency change was recorded for this ")
                    .append("module.</p>");
        }
        for (String key : keys) {
            final List<DependencyChange> dependencies = groups.getOrDefault(
                    key, List.of());
            final List<BoundChangePoint> points = memberGroups.getOrDefault(
                    key, List.of());
            final List<JarDiffFailure> failures = failureGroups.getOrDefault(
                    key, List.of());
            appendDependencySection(body, module, key, dependencies, points,
                    failures);
        }
        body.append("</section>");
        return document("Dependency Changes", "Dependency Changes",
                breadcrumbs(overallFile, module, pages.changes(),
                        "Dependency Changes"),
                siblingLinks(pages, "changes"), changesToc(),
                body.toString());
    }

    private void appendDependencySection(
            final StringBuilder body,
            final ModuleAnalysisResult module,
            final String key,
            final List<DependencyChange> dependencies,
            final List<BoundChangePoint> points,
            final List<JarDiffFailure> failures) {
        final DependencyUpgradeKey upgrade = points.isEmpty()
                ? failures.isEmpty() ? null
                : failures.get(0).dependencyUpgradeKey()
                : points.get(0).getDependencyUpgradeKey();
        final DependencyChange dependency = dependencies.isEmpty()
                ? null : dependencies.get(0);
        body.append("<article class=\"card\" id=\"")
                .append(attribute("dependency-" + stableHash(key)))
                .append("\"><h3>")
                .append(escape(upgrade == null
                        ? dependencyLabel(dependency)
                        : jarLabel(upgrade))).append("</h3>");
        if (dependency != null) {
            body.append("<p>").append(escape(dependencyChangeText(dependency)))
                    .append(" Scope: <code>")
                    .append(escape(dependency.getScope().getValue()))
                    .append("</code>.</p>");
            final ArtifactCoord artifact = dependency.getNewArtifact() == null
                    ? dependency.getOldArtifact()
                    : dependency.getNewArtifact();
            if (!"jar".equals(artifact.getType())) {
                body.append("<p>This dependency is not a JAR, so member-level ")
                        .append("comparison does not apply.</p>");
            } else if (dependency.getChangeType()
                    != ChangeType.VERSION_CHANGED) {
                body.append("<p>Member-level comparison is not available for ")
                        .append("added or removed dependencies.</p>");
            }
        }
        if (upgrade != null) {
            body.append("<p>Member comparison status: <strong>")
                    .append(failures.isEmpty()
                            ? "Completed" : "Incomplete")
                    .append("</strong>.</p>");
        }
        if (!failures.isEmpty()) {
            body.append("<p class=\"warn\">Member-level comparison did not ")
                    .append("complete for this JAR.</p>");
            failures.forEach(failure -> body
                    .append("<details><summary>Technical details</summary>")
                    .append("<p>").append(escape(failure.reason()))
                    .append("</p><p>Old path: <code>")
                    .append(escape(failure.dependencyUpgradeKey()
                            .getOldPath().toString()))
                    .append("</code><br>New path: <code>")
                    .append(escape(failure.dependencyUpgradeKey()
                            .getNewPath().toString()))
                    .append("</code></p></details>"));
        }
        appendMemberCategory(body, module, "Methods", points,
                kind -> kind.name().startsWith("METHOD_"));
        appendMemberCategory(body, module, "Fields", points,
                kind -> kind.name().startsWith("FIELD_"));
        appendMemberCategory(body, module, "Classes", points,
                kind -> kind.name().startsWith("CLASS_"));
        body.append("</article>");
    }

    private void appendMemberCategory(
            final StringBuilder body,
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
        body.append("<h4>").append(title).append("</h4><ul>");
        for (BoundChangePoint bound : selected) {
            final ChangePoint point = bound.getChangePoint();
            body.append("<li id=\"")
                    .append(attribute(changeAnchor(bound)))
                    .append("\"><strong>")
                    .append(escape(changeKindText(point.getKind())))
                    .append(":</strong> <code>")
                    .append(escape(memberLabel(point)))
                    .append("</code><br>")
                    .append(escape(dispositionText(
                            module.getDispositions().get(bound))))
                    .append(memberTechnicalDetails(module, bound))
                    .append("</li>");
        }
        body.append("</ul>");
    }

    private String memberTechnicalDetails(
            final ModuleAnalysisResult module,
            final BoundChangePoint bound) {
        final ChangePoint point = bound.getChangePoint();
        final MethodEquivalenceResult equivalence = module
                .getEquivalenceResults().get(bound);
        return "<details><summary>Technical details</summary><table>"
                + row("Raw ChangePointKind", point.getKind())
                + row("Raw disposition", module.getDispositions().get(bound))
                + row("Old descriptor", point.getOldDescriptor())
                + row("New descriptor", point.getNewDescriptor())
                + row("Old hash", point.getOldHash())
                + row("New hash", point.getNewHash())
                + row("SSA status", equivalence == null
                ? "Not compared" : equivalence.getStatus())
                + row("SSA reason", equivalence == null
                ? "Not compared" : equivalence.getReason())
                + row("Old physical path", bound.getDependencyUpgradeKey()
                .getOldPath())
                + row("New physical path", bound.getDependencyUpgradeKey()
                .getNewPath()) + "</table></details>";
    }

    private String document(
            final String title,
            final String currentPage,
            final String breadcrumbs,
            final String siblings,
            final String toc,
            final String body) {
        return "<!DOCTYPE html><html lang=\"en\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,"
                + "initial-scale=1\"><title>" + escape(title)
                + "</title><style>" + CSS + "</style></head><body><header>"
                + (breadcrumbs.isEmpty()
                ? "<nav class=\"breadcrumbs\" aria-label=\"Breadcrumb\">"
                + "<span>Overall</span></nav>" : breadcrumbs)
                + siblings + "</header><div class=\"layout\"><nav class=\"toc\""
                + " aria-label=\"Table of contents\"><strong>On this page"
                + "</strong><div class=\"muted\" aria-current=\"page\">"
                + escape(currentPage) + "</div>" + toc
                + "</nav><main>" + body + "</main></div></body></html>";
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
                ? "<span>" + escape(module.getModuleId().stableKey())
                + "</span>" : "<a href=\"" + attribute(moduleIndex)
                + "\">" + escape(module.getModuleId().stableKey())
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

    private void appendTerminology(final StringBuilder body) {
        body.append("<section id=\"terms\"><h2>Terminology</h2><dl>")
                .append(term("WALA", "The Java analysis library used to "
                        + "discover possible call relationships."))
                .append(term("Call Graph", "A graph whose nodes are methods "
                        + "and whose edges are possible calls."))
                .append(term("0-1-CFA", "The WALA call analysis used here. "
                        + "It distinguishes useful receiver allocation "
                        + "contexts while remaining conservative."))
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

    private String term(final String name, final String explanation) {
        return "<dt><strong>" + escape(name) + "</strong></dt><dd>"
                + escape(explanation) + "</dd>";
    }

    private void appendPreflight(
            final StringBuilder body,
            final PreflightReport preflight) {
        body.append("<section id=\"preflight\"><h2>Preflight checks</h2>")
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
        body.append("</table></section>");
    }

    private void appendDiagnostics(
            final StringBuilder body,
            final List<DiagnosticEvent> events) {
        body.append("<section id=\"diagnostics\"><h2>Diagnostics</h2>");
        appendDiagnosticList(body, events);
        body.append("</section>");
    }

    private void appendDiagnosticList(
            final StringBuilder body,
            final List<DiagnosticEvent> events) {
        if (events.isEmpty()) {
            body.append("<p>No diagnostic event was recorded.</p>");
            return;
        }
        body.append("<ul>");
        for (DiagnosticEvent event : events) {
            body.append("<li><code>")
                    .append(escape(diagnosticPrefix(event)))
                    .append("</code> ").append(escape(event.getMessage()))
                    .append("</li>");
        }
        body.append("</ul>");
    }

    private String diagnosticPrefix(final DiagnosticEvent event) {
        final StringBuilder value = new StringBuilder("[")
                .append(event.getStage()).append(']');
        appendContext(value, "", event.getTask());
        appendContext(value, "side=", event.getSide());
        appendContext(value, "module=", event.getModule());
        appendContext(value, "artifact=", event.getArtifact());
        return value.toString();
    }

    private void appendContext(
            final StringBuilder value,
            final String name,
            final String field) {
        if (field != null && !field.isBlank()) {
            value.append('[').append(name).append(field).append(']');
        }
    }

    private void appendStageMetrics(
            final StringBuilder body,
            final Map<String, Long> stages) {
        body.append("<h3>Stage elapsed time</h3><table>")
                .append("<tr><th>Stage</th><th>Milliseconds</th></tr>");
        stages.forEach((stage, millis) -> body.append("<tr><td>")
                .append(escape(stage)).append("</td><td>")
                .append(millis).append("</td></tr>"));
        body.append("</table>");
    }

    private void appendList(
            final StringBuilder body,
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

    private Map<String, List<DependencyChange>> dependencyGroups(
            final List<DependencyChange> changes) {
        final Map<String, List<DependencyChange>> result =
                new LinkedHashMap<>();
        changes.stream().sorted(Comparator.comparing(
                        DependencyChange::toString))
                .forEach(change -> result.computeIfAbsent(
                        dependencyGroupKey(change), ignored ->
                                new ArrayList<>()).add(change));
        return result;
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

    private Map<String, List<JarDiffFailure>> failureGroups(
            final List<JarDiffFailure> failures) {
        final Map<String, List<JarDiffFailure>> result =
                new LinkedHashMap<>();
        failures.stream().sorted(Comparator.comparing(
                        JarDiffFailure::stableKey))
                .forEach(failure -> result.computeIfAbsent(
                        upgradeGroupKey(failure.dependencyUpgradeKey()),
                        ignored -> new ArrayList<>()).add(failure));
        return result;
    }

    private String dependencyGroupKey(final DependencyChange change) {
        final ArtifactCoord old = change.getOldArtifact();
        final ArtifactCoord target = change.getNewArtifact();
        final ArtifactCoord representative = target == null ? old : target;
        return representative.getGroupId() + ":"
                + representative.getArtifactId() + ":"
                + representative.getType() + ":"
                + representative.getClassifier() + ":"
                + (old == null ? "" : old.getVersion()) + ":"
                + (target == null ? "" : target.getVersion());
    }

    private String upgradeGroupKey(final DependencyUpgradeKey key) {
        final ArtifactCoord old = key.getOldArtifact();
        final ArtifactCoord target = key.getNewArtifact();
        return old.getGroupId() + ":" + old.getArtifactId() + ":"
                + old.getType() + ":" + old.getClassifier() + ":"
                + old.getVersion() + ":" + target.getVersion();
    }

    private String dependencyLabel(final DependencyChange change) {
        if (change == null) {
            return "Dependency change";
        }
        final ArtifactCoord value = change.getNewArtifact() == null
                ? change.getOldArtifact() : change.getNewArtifact();
        return value.getGroupId() + ":" + value.getArtifactId();
    }

    private String dependencyChangeText(final DependencyChange change) {
        return switch (change.getChangeType()) {
            case ADDED -> "Dependency added at version "
                    + change.getNewArtifact().getVersion() + ".";
            case REMOVED -> "Dependency removed from version "
                    + change.getOldArtifact().getVersion() + ".";
            case VERSION_CHANGED -> "Dependency updated from "
                    + change.getOldArtifact().getVersion() + " to "
                    + change.getNewArtifact().getVersion() + ".";
        };
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
            case METHOD_ADDED -> "Method added";
            case METHOD_REMOVED -> "Method removed";
            case METHOD_DESCRIPTOR_CHANGED -> "Method signature changed";
            case METHOD_BODY_CHANGED -> "Method implementation changed";
            case FIELD_ADDED -> "Field added";
            case FIELD_REMOVED -> "Field removed";
            case FIELD_DESCRIPTOR_CHANGED -> "Field type changed";
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
            case TARGET_NOT_FOUND -> "The target member could not be resolved.";
            case DECLARED_REFERENCE_NOT_FOUND -> "No reachable bytecode "
                    + "reference to the previous member was found.";
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

    private boolean hasFinalEvidence(
            final ModuleAnalysisResult module,
            final BoundChangePoint point) {
        return module.getFinalPaths().stream().anyMatch(path ->
                path.getTerminal().getChangePoint().equals(point))
                || module.getStructuralImpacts().stream().anyMatch(value ->
                value.getChangePoint().equals(point));
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

    private long affectedMethodCount(final ModuleAnalysisResult module) {
        return module.getFinalPaths().stream()
                .map(ImpactPath::getAffectedMethod).distinct().count();
    }

    private long affectedClassCount(final ModuleAnalysisResult module) {
        return module.getFinalPaths().stream()
                .map(path -> path.getAffectedMethod().owner())
                .distinct().count();
    }

    private String ssaCounts(final List<ModuleAnalysisResult> modules) {
        return equivalenceCount(modules,
                MethodEquivalenceStatus.PROVEN_EQUIVALENT) + " / "
                + equivalenceCount(modules,
                MethodEquivalenceStatus.DIFFERENT) + " / "
                + equivalenceCount(modules,
                MethodEquivalenceStatus.UNKNOWN);
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
        return module.getSession() == null ? 0L
                : module.getSession().getGraph().stream()
                .map(com.ibm.wala.ipa.callgraph.CGNode::getContext)
                .distinct().count();
    }

    private String sessionRows(final ModuleAnalysisResult module) {
        if (module.getSession() == null) {
            return row("Call Graph", "Not available");
        }
        final CallGraphStats stats = module.getSession().getStats();
        return row("Parameter candidates",
                module.getSession().getParameterCandidateCount())
                + row("Call Graph nodes", stats.methodCount())
                + row("Call Graph edges", stats.edgeCount())
                + row("WALA Context count", contextCount(module));
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
                "metrics", "Runtime metrics", "limits", "Limitations",
                "diagnostics", "Diagnostics");
    }

    private String impactToc() {
        return toc("chains", "Affected call chains", "structures",
                "Class structure references");
    }

    private String changesToc() {
        return toc("dependencies", "Changed dependencies");
    }

    private String toc(final String... entries) {
        final StringBuilder value = new StringBuilder("<ul>");
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
     * @param pluginVersion Maven Dependency Plugin source/version
     */
    private record ModuleRuntime(
            String jdkVersion,
            String mavenVersion,
            String pluginVersion) {
    }
}
