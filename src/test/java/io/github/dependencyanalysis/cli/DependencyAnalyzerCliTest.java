package io.github.dependencyanalysis.cli;

import io.github.dependencyanalysis.diagnostic.LogVerbosity;

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
                .contains("--module-parallelism")
                .contains("--analysis-target");
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
    void markdownAndInvalidModuleParallelismAreRejected() {
        final int markdown = DependencyAnalyzerCli
                .newCommandLine(new DependencyAnalyzerCli())
                .execute("impact", "--baseline", "HEAD",
                        "--output", "report.md", "--format", "md");
        final int parallelism = DependencyAnalyzerCli
                .newCommandLine(new DependencyAnalyzerCli())
                .execute("impact", "--baseline", "HEAD",
                        "--output", "report.html",
                        "--module-parallelism", "0");

        assertThat(markdown).isEqualTo(1);
        assertThat(parallelism).isEqualTo(1);
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
}
