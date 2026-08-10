package io.github.dependencyanalysis.cli;

import io.github.dependencyanalysis.callgraph.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.WalaReflectionOptions;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;
import io.github.dependencyanalysis.impact.DependencyAnalysisScopeMode;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.assertj.core.api.Assertions
        .assertThat;

/** Root CLI contract tests. */
class DependencyAnalyzerCliTest {

    @Test
    void rootHelpListsSubcommands() {
        final CommandLine command =
                DependencyAnalyzerCli.newCommandLine(
                        new DependencyAnalyzerCli());

        final int code = command.execute("--help");

        assertThat(code).isZero();
        assertThat(command.getSubcommands())
                .containsKeys("impact", "tree");
    }

    @Test
    void noSubcommandIsUsageError() {
        final int code = DependencyAnalyzerCli
                .newCommandLine(
                        new DependencyAnalyzerCli())
                .execute();

        assertThat(code).isEqualTo(1);
    }

    @Test
    void oldRootInvocationIsRejected() {
        final int code = DependencyAnalyzerCli
                .newCommandLine(
                        new DependencyAnalyzerCli())
                .execute("--baseline", "HEAD",
                        "--output", "report.html");

        assertThat(code).isEqualTo(1);
    }

    @Test
    void parseAndRequiredOptionFailuresReturnOne() {
        final CommandLine unknown = DependencyAnalyzerCli
                .newCommandLine(
                        new DependencyAnalyzerCli());
        final CommandLine missing = DependencyAnalyzerCli
                .newCommandLine(
                        new DependencyAnalyzerCli());

        assertThat(unknown.execute("--unknown"))
                .isEqualTo(1);
        assertThat(missing.execute("impact"))
                .isEqualTo(1);
    }

    @Test
    void globalOptionsWorkBeforeSubcommand() {
        final int code = DependencyAnalyzerCli
                .newCommandLine(
                        new DependencyAnalyzerCli())
                .execute("-c", "config",
                        "tree", "--help");

        assertThat(code).isZero();
    }

    @Test
    void globalOptionsWorkAfterSubcommand() {
        final int code = DependencyAnalyzerCli
                .newCommandLine(
                        new DependencyAnalyzerCli())
                .execute("impact", "-c",
                        "config", "--help");

        assertThat(code).isZero();
    }

    @Test
    void rootAndImpactHelpListShortOptions() {
        final CommandLine command =
                DependencyAnalyzerCli.newCommandLine(
                        new DependencyAnalyzerCli());
        final StringWriter rootText =
                new StringWriter();
        final StringWriter impactText =
                new StringWriter();
        final StringWriter treeText =
                new StringWriter();

        command.usage(new PrintWriter(rootText));
        command.getSubcommands().get("impact")
                .usage(new PrintWriter(impactText));
        command.getSubcommands().get("tree")
                .usage(new PrintWriter(treeText));

        assertThat(rootText.toString())
                .contains("-m, --maven")
                .contains("-j, --java-home")
                .contains("-c, --config-dir")
                .contains("-a, --maven-arg")
                .contains("-v, --verbose");
        assertThat(impactText.toString())
                .contains("-p, --path")
                .contains("-b, --baseline")
                .contains("-t, --target")
                .contains("-o, --output")
                .contains("-f, --format")
                .contains("-k, --include-change-kinds")
                .contains("--analysis-parallelism")
                .contains("--call-graph-algorithm")
                .contains("--dependency-analysis-scope")
                .contains("--call-graph-diagnostics-output")
                .contains("--wala-reflection-options")
                .contains("--entrypoint-include")
                .contains("--entrypoint-exclude")
                .contains("--analysis-target");
        assertThat(impactText.toString().replaceAll("\\s+", " "))
                .contains("Call Graph algorithm: rta, zero-cfa, "
                        + "optimized-0-1-cfa, or 1-object-1-call-site; "
                        + "default: rta.");
        assertThat(treeText.toString())
                .contains("-p, --path")
                .contains("-r, --ref")
                .contains("-o, --output")
                .contains("-s, --scopes")
                .contains("-d, --dependency-plugin-version");
    }

    @Test
    void removedProjectOptionIsRejected() {
        final int code = DependencyAnalyzerCli
                .newCommandLine(
                        new DependencyAnalyzerCli())
                .execute("impact", "--project", ".",
                        "--baseline", "HEAD",
                        "--output", "report.html");

        assertThat(code).isEqualTo(1);
    }

    @Test
    void removedRepositoryOptionIsRejected() {
        final int code = DependencyAnalyzerCli
                .newCommandLine(
                        new DependencyAnalyzerCli())
                .execute("tree", "--repository", ".",
                        "--output", "report");

        assertThat(code).isEqualTo(1);
    }

    @Test
    void removedMavenJavaHomeOptionIsRejected() {
        final int code = DependencyAnalyzerCli
                .newCommandLine(new DependencyAnalyzerCli())
                .execute("tree", "--maven-java-home", ".",
                        "--output", "report");

        assertThat(code).isEqualTo(1);
    }

    @Test
    void negativeCallGraphTimeoutIsRejected() {
        final int code = DependencyAnalyzerCli
                .newCommandLine(new DependencyAnalyzerCli())
                .execute("impact", "--baseline", "HEAD",
                        "--output", "report.html",
                        "--call-graph-timeout-seconds", "-1");

        assertThat(code).isEqualTo(1);
    }

    @Test
    void markdownAndInvalidOrRemovedParallelismAreRejected() {
        final int markdown = DependencyAnalyzerCli
                .newCommandLine(new DependencyAnalyzerCli())
                .execute("impact", "--baseline", "HEAD",
                        "--output", "report.md", "--format", "md");
        final int removed = DependencyAnalyzerCli
                .newCommandLine(new DependencyAnalyzerCli())
                .execute("impact", "--baseline", "HEAD",
                        "--output", "report.html",
                        "--module-parallelism", "0");
        final int invalid = DependencyAnalyzerCli
                .newCommandLine(new DependencyAnalyzerCli())
                .execute("impact", "--baseline", "HEAD",
                        "--output", "report.html",
                        "--analysis-parallelism", "0");

        assertThat(markdown).isEqualTo(1);
        assertThat(removed).isEqualTo(1);
        assertThat(invalid).isEqualTo(1);
    }

    @Test
    void legacyEntrypointSelectorSyntaxIsRejected() {
        final int code = DependencyAnalyzerCli
                .newCommandLine(new DependencyAnalyzerCli())
                .execute("impact", "--baseline", "HEAD",
                        "--output", "report.html",
                        "--entrypoint-include", "com.icbc:A");

        assertThat(code).isEqualTo(1);
    }

    @Test
    void callGraphAlgorithmDefaultsAndParsesCaseInsensitively() {
        final CommandLine defaultCommand = DependencyAnalyzerCli
                .newCommandLine(new DependencyAnalyzerCli());
        final CommandLine zeroCfaCommand = DependencyAnalyzerCli
                .newCommandLine(new DependencyAnalyzerCli());
        final CommandLine optimizedCommand = DependencyAnalyzerCli
                .newCommandLine(new DependencyAnalyzerCli());
        final CommandLine oneObjectOneCallSiteCommand = DependencyAnalyzerCli
                .newCommandLine(new DependencyAnalyzerCli());

        final CommandLine.ParseResult defaultResult =
                defaultCommand.parseArgs("impact", "--baseline", "HEAD",
                        "--output", "report.html");
        final CommandLine.ParseResult zeroCfaResult =
                zeroCfaCommand.parseArgs("impact", "--baseline", "HEAD",
                        "--output", "report.html",
                        "--call-graph-algorithm", "ZERO-CFA");
        final CommandLine.ParseResult optimizedResult =
                optimizedCommand.parseArgs("impact", "--baseline", "HEAD",
                        "--output", "report.html",
                        "--call-graph-algorithm", "OPTIMIZED-0-1-CFA");
        final CommandLine.ParseResult oneObjectOneCallSiteResult =
                oneObjectOneCallSiteCommand.parseArgs(
                        "impact", "--baseline", "HEAD",
                        "--output", "report.html",
                        "--call-graph-algorithm",
                        "1-OBJECT-1-CALL-SITE");

        assertThat(algorithm(defaultResult))
                .isEqualTo(CallGraphAlgorithm.RTA);
        assertThat(algorithm(zeroCfaResult))
                .isEqualTo(CallGraphAlgorithm.ZERO_CFA);
        assertThat(algorithm(optimizedResult))
                .isEqualTo(CallGraphAlgorithm.OPTIMIZED_ZERO_ONE_CFA);
        assertThat(algorithm(oneObjectOneCallSiteResult))
                .isEqualTo(CallGraphAlgorithm.ONE_OBJECT_ONE_CALL_SITE);
    }

    @Test
    void walaReflectionOptionsDefaultsAndParsesCaseInsensitively() {
        final CommandLine defaultCommand = DependencyAnalyzerCli
                .newCommandLine(new DependencyAnalyzerCli());
        final CommandLine fullCommand = DependencyAnalyzerCli
                .newCommandLine(new DependencyAnalyzerCli());
        final CommandLine aliasCommand = DependencyAnalyzerCli
                .newCommandLine(new DependencyAnalyzerCli());

        final CommandLine.ParseResult defaultResult =
                defaultCommand.parseArgs("impact", "--baseline", "HEAD",
                        "--output", "report.html");
        final CommandLine.ParseResult fullResult =
                fullCommand.parseArgs("impact", "--baseline", "HEAD",
                        "--output", "report.html",
                        "--wala-reflection-options", "full");
        final CommandLine.ParseResult aliasResult =
                aliasCommand.parseArgs("impact", "--baseline", "HEAD",
                        "--output", "report.html",
                        "--reflection-options", "none");

        assertThat(reflectionOptions(defaultResult).identifier())
                .isEqualTo("ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD");
        assertThat(reflectionOptions(fullResult).identifier())
                .isEqualTo("FULL");
        assertThat(reflectionOptions(aliasResult).identifier())
                .isEqualTo("NONE");
    }

    @Test
    void dependencyAnalysisScopeDefaultsToChangedPathsAndParsesFull() {
        final CommandLine defaultCommand = DependencyAnalyzerCli
                .newCommandLine(new DependencyAnalyzerCli());
        final CommandLine fullCommand = DependencyAnalyzerCli
                .newCommandLine(new DependencyAnalyzerCli());

        final CommandLine.ParseResult defaultResult =
                defaultCommand.parseArgs("impact", "--baseline", "HEAD",
                        "--output", "report.html");
        final CommandLine.ParseResult fullResult =
                fullCommand.parseArgs("impact", "--baseline", "HEAD",
                        "--output", "report.html",
                        "--dependency-analysis-scope", "FULL");

        assertThat(dependencyScope(defaultResult)).isEqualTo(
                DependencyAnalysisScopeMode.CHANGED_PATHS);
        assertThat(dependencyScope(fullResult)).isEqualTo(
                DependencyAnalysisScopeMode.FULL);
    }

    @Test
    void callGraphDiagnosticsOutputIsOptionalAndParsesAsFile() {
        final CommandLine command = DependencyAnalyzerCli
                .newCommandLine(new DependencyAnalyzerCli());

        final CommandLine.ParseResult result = command.parseArgs(
                "impact", "--baseline", "HEAD", "--output", "report.html",
                "--call-graph-diagnostics-output", "topology.json");

        assertThat((java.io.File) result.subcommand().commandSpec()
                .findOption("--call-graph-diagnostics-output").getValue())
                .hasName("topology.json");
    }

    @Test
    void unknownWalaReflectionOptionsIsRejectedBeforePreflight() {
        final CommandLine command = DependencyAnalyzerCli
                .newCommandLine(new DependencyAnalyzerCli());
        final StringWriter errors = new StringWriter();
        command.setErr(new PrintWriter(errors));

        final int code = command.execute("impact", "--baseline", "HEAD",
                "--output", "report.html", "--wala-reflection-options",
                "one-flow");

        assertThat(code).isEqualTo(1);
        assertThat(errors.toString())
                .contains("ONE_FLOW_TO_CASTS_APPLICATION_GET_METHOD")
                .doesNotContain("[preflight]");
    }

    @Test
    void unknownCallGraphAlgorithmIsRejectedBeforePreflight() {
        final CommandLine command = DependencyAnalyzerCli
                .newCommandLine(new DependencyAnalyzerCli());
        final StringWriter errors = new StringWriter();
        command.setErr(new PrintWriter(errors));

        final int code = command.execute("impact", "--baseline", "HEAD",
                "--output", "report.html", "--call-graph-algorithm",
                "zerocfa");

        assertThat(code).isEqualTo(1);
        assertThat(errors.toString())
                .contains("rta, zero-cfa, optimized-0-1-cfa, "
                        + "1-object-1-call-site")
                .doesNotContain("[preflight]");
    }

    @Test
    void repeatedVerboseFlagSelectsLogLevel() {
        final DependencyAnalyzerCli defaultRoot =
                new DependencyAnalyzerCli();
        final DependencyAnalyzerCli debugRoot =
                new DependencyAnalyzerCli();
        final DependencyAnalyzerCli traceRoot =
                new DependencyAnalyzerCli();

        DependencyAnalyzerCli.newCommandLine(defaultRoot)
                .parseArgs("tree", "--help");
        DependencyAnalyzerCli.newCommandLine(debugRoot)
                .parseArgs("tree", "-v", "--help");
        DependencyAnalyzerCli.newCommandLine(traceRoot)
                .parseArgs("tree", "-vv", "--help");

        assertThat(defaultRoot.getLogVerbosity())
                .isEqualTo(LogVerbosity.INFO);
        assertThat(debugRoot.getLogVerbosity())
                .isEqualTo(LogVerbosity.DEBUG);
        assertThat(traceRoot.getLogVerbosity())
                .isEqualTo(LogVerbosity.TRACE);
    }

    private CallGraphAlgorithm algorithm(
            final CommandLine.ParseResult result) {
        return result.subcommand().commandSpec()
                .findOption("--call-graph-algorithm").getValue();
    }

    private WalaReflectionOptions reflectionOptions(
            final CommandLine.ParseResult result) {
        return result.subcommand().commandSpec().findOption(
                "--wala-reflection-options").getValue();
    }

    private DependencyAnalysisScopeMode dependencyScope(
            final CommandLine.ParseResult result) {
        return result.subcommand().commandSpec().findOption(
                "--dependency-analysis-scope").getValue();
    }
}
