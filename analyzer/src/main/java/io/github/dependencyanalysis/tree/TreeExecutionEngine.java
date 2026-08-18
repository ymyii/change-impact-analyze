package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.cli.DependencyAnalyzerCli;
import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;
import io.github.dependencyanalysis.preflight.PreflightContext;
import io.github.dependencyanalysis.preflight.PreflightReport;
import io.github.dependencyanalysis.reactor.ReactorDescriptor;
import io.github.dependencyanalysis.reactor.RepositoryInventory;
import io.github.dependencyanalysis.runtime.CommandRunDirectory;
import io.github.dependencyanalysis.runtime.MavenDependencyPluginRuntime;
import io.github.dependencyanalysis.runtime.MavenRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.ReportCache;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

// Wiki: wiki/architecture/dependency-analysis-pipelines.md
// - Tree execution boundary
/** Executes Tree preflight, reactor analysis, and Report publication. */
final class TreeExecutionEngine {

    /** Root command configuration. */
    private final DependencyAnalyzerCli root;

    /** Analysis path. */
    private final File path;

    /** Optional Git ref. */
    private final String ref;

    /** Report output directory. */
    private final File output;

    /** Included scope expression. */
    private final String scopes;

    /** Optional Maven Dependency Plugin version. */
    private final String pluginVersion;

    /** Diagnostics. */
    private final DiagnosticLog diagnostics;

    /**
     * Creates a command execution engine.
     *
     * @param command root command
     * @param analysisPath optional analysis path
     * @param reference optional Git ref
     * @param reportOutput output directory
     * @param includedScopes comma-separated scopes
     * @param dependencyPluginVersion optional plugin version
     * @param collector diagnostics
     */
    TreeExecutionEngine(
            final DependencyAnalyzerCli command,
            final File analysisPath,
            final String reference,
            final File reportOutput,
            final String includedScopes,
            final String dependencyPluginVersion,
            final DiagnosticLog collector) {
        root = Objects.requireNonNull(command, "command");
        path = analysisPath == null
                ? new File(System.getProperty("user.dir")) : analysisPath;
        ref = reference;
        output = Objects.requireNonNull(reportOutput, "reportOutput");
        scopes = Objects.requireNonNull(includedScopes, "includedScopes");
        pluginVersion = dependencyPluginVersion;
        diagnostics = Objects.requireNonNull(collector, "collector");
    }

    /** @return stable CLI exit code */
    int execute() {
        final Set<String> includedScopes = parseScopes(scopes);
        final TreeDiagnosticEmitter console =
                new TreeDiagnosticEmitter(diagnostics);
        console.debug("command=tree; verbosity=" + root.getLogVerbosity());
        console.trace("path=" + path.toPath().toAbsolutePath().normalize()
                + "; ref=" + (ref == null ? "CURRENT" : ref)
                + "; output=" + output.toPath().toAbsolutePath().normalize()
                + "; scopes=" + includedScopes);
        TreeReportSession reportSession = null;
        try (PreflightContext context = new PreflightContext()) {
            final TreePreflightService preflightService =
                    new TreePreflightService(root, path, ref, output,
                            includedScopes, diagnostics);
            final PreflightReport preflight = preflightService
                    .runCommandChecks(context, pluginVersion);
            console.preflight(preflight);
            if (preflight.blocksCommand()) {
                console.analysisSkipped("command preflight failed");
                console.summary(new TreeRunSummary(
                        TreeReportState.FAILED, "NOT_GENERATED"));
                return 1;
            }
            final RepositoryInventory inventory = context.get(
                    TreePreflightService.INVENTORY,
                    RepositoryInventory.class);
            final RepositorySnapshot snapshot = context.get(
                    TreePreflightService.SNAPSHOT,
                    RepositorySnapshot.class);
            final TreeReportMetadata metadata = new TreeReportMetadata(
                    snapshot,
                    context.get(TreePreflightService.MAVEN_RUNTIME,
                            MavenRuntimeDescriptor.class),
                    context.get(TreePreflightService.MAVEN_ARGS, List.class),
                    includedScopes, preflight, path.toPath(),
                    inventory.getAnalysisPath());
            final int totalReactors = inventory.getReactors().size();
            console.analysisStarted(totalReactors);
            try (ReportCache reportCache = new ReportCache(
                    context.get(TreePreflightService.COMMAND_RUN,
                            CommandRunDirectory.class), "tree")) {
                reportSession = new TreeReportRenderer().start(
                        metadata, totalReactors, output.toPath(), reportCache);
                final TreeReportCacheSpiller spiller =
                        new TreeReportCacheSpiller();
                int index = 0;
                for (ReactorDescriptor descriptor : inventory.getReactors()) {
                    index++;
                    console.reactorStarted(index, totalReactors,
                            descriptor.getId());
                    final ReactorTreeResult result = analyzeReactor(
                            context, descriptor, includedScopes,
                            reportCache.root());
                    final String cachePrefix = spiller.spill(
                            result, reportCache);
                    final DiagnosticContext publishContext =
                            DiagnosticContext.of("report", "publish")
                                    .with("reactor", descriptor.getId());
                    final long conflictCount = result.getModules().stream()
                            .mapToLong(value -> value
                                    .getClassConflicts().size()).sum();
                    diagnostics.startStage(publishContext,
                            "classConflicts=" + conflictCount);
                    try {
                        reportSession.publish(result);
                        diagnostics.debug(publishContext,
                                "class conflict shards; count="
                                        + conflictCount);
                        tracePublishedShards(publishContext, result,
                                conflictCount);
                        diagnostics.endStage(publishContext,
                                "classConflictShards=" + conflictCount);
                    } catch (Exception exception) {
                        diagnostics.failStage(publishContext,
                                "reason=" + failureMessage(exception));
                        throw exception;
                    }
                    reportCache.discard(cachePrefix);
                    console.reactorCompleted(index, totalReactors, result);
                }
                reportCache.complete();
                reportSession.complete();
            }
            final List<TreeAnalysisIssue> issues =
                    reportSession.getAnalysisIssues();
            final TreeReportState status = reportSession.getState();
            console.analysisCompleted(issues);
            console.summary(new TreeRunSummary(status, reportPath()));
            return status == TreeReportState.SUCCESS ? 0 : 2;
        } catch (Exception exception) {
            markReportFailed(reportSession, exception);
            final List<TreeAnalysisIssue> issues = reportSession == null
                    ? List.of() : reportSession.getAnalysisIssues();
            console.analysisCompleted(issues);
            console.summary(new TreeRunSummary(
                    TreeReportState.FAILED, reportPath()));
            diagnostics.error(DiagnosticContext.of("tree", "failure"),
                    "Tree command failed: " + failureMessage(exception));
            diagnostics.transientException(
                    DiagnosticContext.stage("tree"), exception);
            return 2;
        }
    }

    private ReactorTreeResult analyzeReactor(
            final PreflightContext context,
            final ReactorDescriptor descriptor,
            final Set<String> includedScopes,
            final Path evidenceParent) {
        final RepositorySnapshot snapshot = context.get(
                TreePreflightService.SNAPSHOT,
                RepositorySnapshot.class);
        final Path pom = snapshot.getRoot().resolve(descriptor.getRootPom());
        if (!Files.isReadable(pom)) {
            return failed(descriptor,
                    "Reactor root POM is unreadable: " + pom);
        }
        if (!descriptor.getViolations().isEmpty()) {
            return failed(descriptor,
                    "Reactor model violates repository boundary: "
                            + String.join("; ", descriptor.getViolations()));
        }
        try {
            return new TreeDependencyCollector(diagnostics).collect(
                    snapshot,
                    context.get(TreePreflightService.INVENTORY,
                            RepositoryInventory.class),
                    descriptor,
                    context.get(TreePreflightService.MAVEN_RUNTIME,
                            MavenRuntimeDescriptor.class),
                    context.get(TreePreflightService
                                    .DEPENDENCY_PLUGIN_RUNTIME,
                            MavenDependencyPluginRuntime.class),
                    includedScopes, evidenceParent);
        } catch (Exception exception) {
            return failed(descriptor,
                    "Reactor analysis failed: "
                            + failureMessage(exception));
        }
    }

    private ReactorTreeResult failed(
            final ReactorDescriptor descriptor,
            final String reason) {
        return new ReactorTreeResult(descriptor,
                List.of(), ReactorStatus.FAILED, reason);
    }

    private String failureMessage(final Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private void tracePublishedShards(
            final DiagnosticContext context,
            final ReactorTreeResult result,
            final long total) {
        if (!diagnostics.getVerbosity().includes(LogVerbosity.TRACE)) {
            return;
        }
        int sequence = 0;
        for (ModuleTreeResult module : result.getModules()) {
            for (TreeClassConflict conflict : module.getClassConflicts()) {
                diagnostics.trace(context, "shard published; progress="
                        + (sequence + 1) + "/" + total + "; module="
                        + module.getCoordinate() + "; class="
                        + conflict.binaryName() + "; file="
                        + String.format(Locale.ROOT,
                        "class-conflict-%05d.js", sequence));
                sequence++;
            }
        }
    }

    private String reportPath() {
        return output.toPath().toAbsolutePath().normalize().toString();
    }

    private void markReportFailed(
            final TreeReportSession session,
            final Exception failure) {
        if (session == null || session.getState() != TreeReportState.RUNNING) {
            return;
        }
        try {
            session.fail(failure.getMessage());
        } catch (Exception reportFailure) {
            failure.addSuppressed(reportFailure);
        }
    }

    private Set<String> parseScopes(final String value) {
        final Set<String> result = new LinkedHashSet<>();
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .map(item -> item.toLowerCase(Locale.ROOT))
                .forEach(result::add);
        return result;
    }
}
