package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.cli
        .DependencyAnalyzerCli;
import io.github.dependencyanalysis.diagnostic
        .LogVerbosity;
import io.github.dependencyanalysis.preflight
        .PreflightContext;
import io.github.dependencyanalysis.preflight
        .PreflightReport;
import io.github.dependencyanalysis.runtime
        .MavenDependencyPluginRuntime;
import io.github.dependencyanalysis.runtime
        .MavenRuntimeDescriptor;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Callable;

// Wiki: wiki/features/repository-dependency-tree-report.md - tree command 入口
/** Repository dependency tree report command. */
@Command(
        name = "tree",
        mixinStandardHelpOptions = true,
        description = "Generate repository dependency tree HTML."
)
public final class TreeCommand
        implements Callable<Integer> {

    /** Root options. */
    @ParentCommand
    private DependencyAnalyzerCli root;

    /** Analysis path inside a Git repository. */
    @Option(names = {"-p", "--path"},
            description = "Analysis path inside a Git repository."
                    + " Defaults to current directory.")
    private File path;

    /** Local ref. */
    @Option(names = {"-r", "--ref"},
            description = "Local Git ref."
                    + " Defaults to current checkout.")
    private String ref;

    /** Output directory. */
    @Option(names = {"-o", "--output"}, required = true,
            description = "HTML report output directory.")
    private File output;

    /** Scope CSV. */
    @Option(names = {"-s", "--scopes"},
            defaultValue = "compile,runtime,provided,test,system",
            description = "Included dependency scopes.")
    private String scopes;

    /** Dependency plugin override. */
    @Option(names = {"-d", "--dependency-plugin-version"},
            description = "Optional maven-dependency-plugin version.")
    private String pluginVersion;

    @Override
    public Integer call() {
        if (path == null) {
            path = new File(System.getProperty(
                    "user.dir"));
        }
        final Set<String> includedScopes =
                parseScopes(scopes);
        final TreeConsoleReporter console =
                new TreeConsoleReporter(System.err,
                        root.getLogVerbosity());
        console.debug("command=tree; verbosity="
                + root.getLogVerbosity());
        console.trace("path=" + path.toPath().toAbsolutePath().normalize()
                + "; ref=" + (ref == null ? "CURRENT" : ref)
                + "; output=" + output.toPath()
                        .toAbsolutePath().normalize()
                + "; scopes=" + includedScopes);
        TreeReportSession reportSession = null;
        int totalReactors = 0;
        try (PreflightContext context =
                     new PreflightContext()) {
            final TreePreflightService preflightService =
                    new TreePreflightService(
                            root, path, ref, output,
                            includedScopes);
            final PreflightReport preflight = preflightService
                    .runCommandChecks(context,
                            pluginVersion);
            console.preflight(preflight);
            if (preflight.blocksCommand()) {
                console.analysisSkipped(
                        "command preflight failed");
                console.summary(new TreeRunSummary(
                        TreeReportState.FAILED,
                        "NOT_GENERATED"));
                return 1;
            }
            final RepositoryInventory inventory =
                    context.get(
                            TreePreflightService.INVENTORY,
                            RepositoryInventory.class);
            final RepositorySnapshot snapshot = context.get(
                    TreePreflightService.SNAPSHOT,
                    RepositorySnapshot.class);
            final TreeReportMetadata metadata =
                    new TreeReportMetadata(
                            snapshot,
                            context.get(
                                    TreePreflightService
                                            .MAVEN_RUNTIME,
                                    MavenRuntimeDescriptor.class),
                            context.get(
                                    TreePreflightService.MAVEN_ARGS,
                                    List.class),
                            includedScopes, preflight,
                            path.toPath(),
                            inventory.getAnalysisPath());
            totalReactors = inventory.getReactors().size();
            console.analysisStarted(totalReactors);
            reportSession = new TreeReportRenderer().start(
                    metadata, totalReactors,
                    output.toPath());
            int index = 0;
            for (ReactorDescriptor descriptor
                    : inventory.getReactors()) {
                index++;
                final ReactorTreeResult result;
                try (TreeConsoleReporter.Heartbeat ignored =
                             console.reactorStarted(index,
                                     totalReactors,
                                     descriptor.getId())) {
                    result = analyzeReactor(context,
                            descriptor, includedScopes);
                }
                reportSession.publish(result);
                console.reactorCompleted(index,
                        totalReactors, result);
            }
            reportSession.complete();
            final List<TreeAnalysisIssue> issues =
                    reportSession.getAnalysisIssues();
            final TreeReportState status =
                    reportSession.getState();
            console.analysisCompleted(issues);
            console.summary(new TreeRunSummary(
                    status, reportPath()));
            return status == TreeReportState.SUCCESS
                    ? 0 : 2;
        } catch (Exception exception) {
            markReportFailed(reportSession, exception);
            final List<TreeAnalysisIssue> issues =
                    reportSession == null
                            ? List.of()
                            : reportSession.getAnalysisIssues();
            console.analysisCompleted(issues);
            console.summary(new TreeRunSummary(
                    TreeReportState.FAILED,
                    reportPath()));
            if (root.getLogVerbosity().includes(
                    LogVerbosity.DEBUG)) {
                exception.printStackTrace(System.err);
            }
            return 2;
        }
    }

    private ReactorTreeResult analyzeReactor(
            final PreflightContext context,
            final ReactorDescriptor descriptor,
            final Set<String> includedScopes) {
        final RepositorySnapshot snapshot = context.get(
                TreePreflightService.SNAPSHOT,
                RepositorySnapshot.class);
        final Path pom = snapshot.getRoot()
                .resolve(descriptor.getRootPom());
        if (!Files.isReadable(pom)) {
            return failed(descriptor,
                    "Reactor root POM is unreadable: "
                            + pom);
        }
        if (!descriptor.getViolations().isEmpty()) {
            return failed(descriptor,
                    "Reactor model violates repository boundary: "
                            + String.join("; ", descriptor
                            .getViolations()));
        }
        try {
            return new TreeDependencyCollector().collect(
                    snapshot,
                    context.get(TreePreflightService.INVENTORY,
                            RepositoryInventory.class),
                    descriptor,
                    context.get(
                            TreePreflightService.MAVEN_RUNTIME,
                            MavenRuntimeDescriptor.class),
                    context.get(TreePreflightService
                                    .DEPENDENCY_PLUGIN_RUNTIME,
                            MavenDependencyPluginRuntime.class),
                    includedScopes);
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

    private String failureMessage(
            final Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private String reportPath() {
        return output.toPath().toAbsolutePath()
                .normalize().toString();
    }

    private void markReportFailed(
            final TreeReportSession session,
            final Exception failure) {
        if (session == null
                || session.getState()
                != TreeReportState.RUNNING) {
            return;
        }
        try {
            session.fail(failure.getMessage());
        } catch (Exception reportFailure) {
            failure.addSuppressed(reportFailure);
        }
    }

    private Set<String> parseScopes(
            final String value) {
        final Set<String> result =
                new LinkedHashSet<>();
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .map(item -> item.toLowerCase(
                        Locale.ROOT))
                .forEach(result::add);
        return result;
    }
}
