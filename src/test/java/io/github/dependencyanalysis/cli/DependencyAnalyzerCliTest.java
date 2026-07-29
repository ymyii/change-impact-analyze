package io.github.dependencyanalysis.cli;

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
                .contains("-j, --maven-java-home")
                .contains("-c, --config-dir")
                .contains("-a, --maven-arg");
        assertThat(impactText.toString())
                .contains("-p, --path")
                .contains("-b, --baseline")
                .contains("-t, --target")
                .contains("-o, --output")
                .contains("-f, --format")
                .contains("-k, --include-change-kinds");
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
}
