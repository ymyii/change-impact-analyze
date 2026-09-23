package io.github.dependencyanalysis.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the shared report output directory contract. */
class OutputDirectoryTest {

    /** Temporary test directory. */
    @TempDir
    private Path temporary;

    @Test
    void acceptsExistingDirectoryAndNormalizesPath() {
        final Path configured = temporary.resolve("reports/../reports");

        final var result = OutputDirectory.validate(configured);

        assertThat(result.isSuccessful()).isTrue();
        assertThat(result.getEvidence())
                .isEqualTo(temporary.resolve("reports").toString());
    }

    @Test
    void acceptsDirectoryWhoseMissingParentsCanBeCreated() {
        final Path configured = temporary.resolve("nested/reports");

        final var result = OutputDirectory.validate(configured);

        assertThat(result.isSuccessful()).isTrue();
    }

    @Test
    void rejectsExistingFile() throws Exception {
        final Path configured = temporary.resolve("report.html");
        Files.writeString(configured, "file");

        final var result = OutputDirectory.validate(configured);

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getSummary()).isEqualTo(
                "Output path is not a directory");
    }

    @Test
    void rejectsPathWhoseExistingParentIsNotDirectory() throws Exception {
        final Path parent = temporary.resolve("parent");
        Files.writeString(parent, "file");

        final var result = OutputDirectory.validate(parent.resolve("reports"));

        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getSummary()).isEqualTo(
                "Output directory cannot be created");
    }
}
