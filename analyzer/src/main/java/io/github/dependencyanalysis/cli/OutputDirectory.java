package io.github.dependencyanalysis.cli;

import io.github.dependencyanalysis.preflight.PreflightOutcome;

import java.nio.file.Files;
import java.nio.file.Path;

/** Shared validation and normalization for report output directories. */
public final class OutputDirectory {

    private OutputDirectory() {
    }

    /**
     * Validates a report output directory without creating it.
     *
     * @param configured configured output path
     * @return preflight result containing the normalized path
     */
    public static PreflightOutcome validate(final Path configured) {
        final Path value = configured.toAbsolutePath().normalize();
        if (Files.exists(value) && !Files.isDirectory(value)) {
            return PreflightOutcome.fail(
                    "Output path is not a directory", value.toString(), "");
        }
        Path parent = Files.exists(value) ? value : value.getParent();
        while (parent != null && !Files.exists(parent)) {
            parent = parent.getParent();
        }
        if (parent == null || !Files.isDirectory(parent)
                || !Files.isWritable(parent)) {
            return PreflightOutcome.fail(
                    "Output directory cannot be created", value.toString(), "");
        }
        return PreflightOutcome.pass(
                "Output directory can be created safely", value.toString());
    }
}
