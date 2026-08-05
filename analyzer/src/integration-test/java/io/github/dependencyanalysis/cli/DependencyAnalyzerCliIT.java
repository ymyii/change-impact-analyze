package io.github.dependencyanalysis.cli;

import io.github.dependencyanalysis.util
        .CommandResolver;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.junit.jupiter.api.Assumptions
        .assumeTrue;

/** Root CLI impact and tree integration smoke tests. */
class DependencyAnalyzerCliIT {

    /** System Maven executable. */
    private static Path maven;

    /** Temporary test root. */
    @TempDir
    private Path temporary;

    @BeforeAll
    static void findMaven() throws Exception {
        final Process process = new ProcessBuilder(
                CommandResolver.resolve(List.of(
                        "which", "mvn")))
                .redirectErrorStream(true).start();
        final String output = new String(
                process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets
                        .UTF_8).trim();
        assumeTrue(process.waitFor() == 0
                && !output.isBlank(),
                "Maven executable unavailable");
        maven = Path.of(output);
    }

    @Test
    void treeGeneratesOfflineStaticReport()
            throws Exception {
        final Path repository = createRepository(
                temporary.resolve("tree"));
        final Path output = temporary.resolve("out");

        final int code = command().execute(
                "--maven", maven.toString(),
                "tree", "--path",
                repository.toString(),
                "--output", output.toString(),
                "--dependency-plugin-version", "3.6.1");

        assertThat(code).isZero();
        assertThat(output.resolve("index.html"))
                .content().contains("Dependency Analyzer")
                .doesNotContain("https://");
        assertThat(output.resolve(
                "dependency-report/assets/report.css"))
                .isRegularFile();
    }

    @Test
    void treeShortOptionsGenerateEquivalentReport()
            throws Exception {
        final Path repository = createRepository(
                temporary.resolve("tree-short"));
        final Path output = temporary.resolve(
                "out-short");

        final int code = command().execute(
                "tree", "-m", maven.toString(),
                "-p", repository.toString(),
                "-o", output.toString(),
                "-s", "compile,runtime",
                "-d", "3.6.1");

        assertThat(code).isZero();
        assertThat(output.resolve("index.html"))
                .content().contains("SUCCESS")
                .contains("<td>1/1</td>")
                .contains("<th>analysis path</th>"
                        + "<td><code>.</code></td>");
    }

    @Test
    void impactLongAndShortOptionsProduceReports()
            throws Exception {
        final String jdk8Home = System.getenv(
                "TEST_JDK8_HOME");
        assertThat(jdk8Home)
                .as("TEST_JDK8_HOME")
                .isNotBlank();
        final Path repository = createRepository(
                temporary.resolve("impact"));
        final Path longOutput = temporary.resolve(
                "impact-long.html");
        final Path shortOutput = temporary.resolve(
                "impact-short.html");

        final int longCode = command().execute(
                "--maven", maven.toString(),
                "--java-home", jdk8Home,
                "--config-dir", temporary.resolve(
                        "long-config").toString(),
                "--maven-arg=-DskipTests", "impact",
                "--path", repository.toString(),
                "--baseline", "HEAD",
                "--target", "HEAD",
                "--output", longOutput.toString(),
                "--format", "html",
                "--include-change-kinds",
                "METHOD_BODY_CHANGED");
        final int shortCode = command().execute(
                "impact", "-m", maven.toString(),
                "-j", jdk8Home,
                "-c", temporary.resolve(
                        "short-config").toString(),
                "-a=-DskipTests",
                "-p", repository.toString(),
                "-b", "HEAD", "-t", "HEAD",
                "-o", shortOutput.toString(),
                "-f", "html", "-k",
                "METHOD_BODY_CHANGED");

        assertThat(longCode).isZero();
        assertThat(shortCode).isZero();
        assertThat(longOutput).content()
                .contains("Impact Analysis Report")
                .contains("Preflight")
                .contains("impact.path")
                .contains("impact.maven-version");
        assertThat(shortOutput).content()
                .contains("Impact Analysis Report")
                .contains("Preflight")
                .contains("impact.path")
                .contains("impact.maven-version");
    }

    @Test
    void commandPreflightFailureCreatesNoReport()
            throws Exception {
        final Path repository = temporary.resolve(
                "not-git");
        Files.createDirectories(repository);
        final Path output = temporary.resolve(
                "blocked.html");

        final int code = command().execute(
                "impact", "--maven", maven.toString(),
                "--path", repository.toString(),
                "--baseline", "HEAD",
                "--output", output.toString());

        assertThat(code).isEqualTo(1);
        assertThat(output).doesNotExist();
    }

    @Test
    void impactRejectsAnalyzerJdk17AsTarget()
            throws Exception {
        final Path repository = createRepository(
                temporary.resolve("jdk17-target"));
        final Path output = temporary.resolve("jdk17.html");

        final int code = command().execute(
                "impact", "--maven", maven.toString(),
                "--java-home", System.getProperty("java.home"),
                "--config-dir", temporary.resolve("jdk17-config")
                        .toString(),
                "--path", repository.toString(),
                "--baseline", "HEAD",
                "--output", output.toString());

        assertThat(code).isEqualTo(1);
        assertThat(output).doesNotExist();
    }

    private picocli.CommandLine command() {
        return DependencyAnalyzerCli.newCommandLine(
                new DependencyAnalyzerCli());
    }

    private Path createRepository(final Path root)
            throws Exception {
        Files.createDirectories(root);
        run(root, "git", "init");
        run(root, "git", "config", "user.email",
                "test@example.com");
        run(root, "git", "config", "user.name",
                "Test");
        Files.writeString(root.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>test</groupId>
                  <artifactId>sample</artifactId>
                  <version>1.0</version>
                  <properties>
                    <maven.compiler.source>1.8</maven.compiler.source>
                    <maven.compiler.target>1.8</maven.compiler.target>
                  </properties>
                </project>
                """);
        run(root, "git", "add", "pom.xml");
        run(root, "git", "commit", "-m", "initial");
        return root;
    }

    private void run(
            final Path directory,
            final String... arguments)
            throws Exception {
        final List<String> command =
                new ArrayList<>(List.of(arguments));
        final Process process = new ProcessBuilder(
                CommandResolver.resolve(command))
                .directory(directory.toFile())
                .redirectErrorStream(true).start();
        final String output = new String(
                process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets
                        .UTF_8);
        assertThat(process.waitFor())
                .as(output).isZero();
    }
}
