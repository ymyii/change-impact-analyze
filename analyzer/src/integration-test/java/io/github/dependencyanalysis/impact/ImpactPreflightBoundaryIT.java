package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.cli
        .DependencyAnalyzerCli;
import io.github.dependencyanalysis.util.CommandResolver;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.junit.jupiter.api.Assumptions
        .assumeFalse;

/** Maven version and output-path preflight integration tests. */
class ImpactPreflightBoundaryIT {

    /** Temporary test root. */
    @TempDir
    private Path temporary;

    @ParameterizedTest
    @ValueSource(strings = {"3.6.2", "4.0.0"})
    void unsupportedUserMavenBlocksBeforeReport(
            final String version)
            throws Exception {
        assumeUnix();
        final Path repository = createRepository(
                "repo-" + version);
        final Path maven = fakeMaven(version);
        final Path output = temporary.resolve(
                "impact-" + version + ".html");

        final int code = command().execute(
                "--maven", maven.toString(),
                "--config-dir", temporary.resolve(
                        "config-" + version).toString(),
                "impact", "--path",
                repository.toString(),
                "--baseline", "HEAD",
                "--output", output.toString());

        assertThat(code).isEqualTo(1);
        assertThat(output).doesNotExist();
    }

    @Test
    void outputDirectoryIsValidationFailure()
            throws Exception {
        assumeUnix();
        final Path repository = createRepository(
                "repo-output");
        final Path maven = fakeMaven("3.9.9");
        final Path output = temporary.resolve(
                "report-directory");
        Files.createDirectories(output);

        final int code = command().execute(
                "--maven", maven.toString(),
                "--config-dir", temporary.resolve(
                        "config-output").toString(),
                "impact", "--path",
                repository.toString(),
                "--baseline", "HEAD",
                "--output", output.toString());

        assertThat(code).isEqualTo(1);
        assertThat(output).isDirectory();
    }

    private picocli.CommandLine command() {
        return DependencyAnalyzerCli.newCommandLine(
                new DependencyAnalyzerCli());
    }

    private Path fakeMaven(final String version)
            throws Exception {
        final Path executable = temporary.resolve(
                "mvn-" + version);
        Files.writeString(executable, """
                #!/bin/sh
                if [ "$1" = "--version" ]; then
                  echo "Apache Maven %s"
                  exit 0
                fi
                exit 0
                """.formatted(version),
                StandardCharsets.UTF_8);
        assertThat(executable.toFile()
                .setExecutable(true)).isTrue();
        return executable;
    }

    private Path createRepository(final String name)
            throws Exception {
        final Path repository = temporary.resolve(name);
        Files.createDirectories(repository);
        run(repository, "git", "init");
        run(repository, "git", "config", "user.email",
                "test@example.com");
        run(repository, "git", "config", "user.name",
                "Test");
        Files.writeString(repository.resolve("pom.xml"), """
                <project xmlns="http://maven.apache.org/POM/4.0.0">
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>test</groupId>
                  <artifactId>sample</artifactId>
                  <version>1</version>
                </project>
                """, StandardCharsets.UTF_8);
        run(repository, "git", "add", "pom.xml");
        run(repository, "git", "commit", "-m", "initial");
        return repository;
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
                StandardCharsets.UTF_8);
        assertThat(process.waitFor())
                .as(output).isZero();
    }

    private void assumeUnix() {
        assumeFalse(System.getProperty("os.name", "")
                        .toLowerCase(java.util.Locale.ROOT)
                        .contains("win"),
                "POSIX fake Maven test");
    }
}
