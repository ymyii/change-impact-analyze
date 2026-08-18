package io.github.dependencyanalysis.maven;

import org.apache.maven.plugin.MojoFailureException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Validates command-owned Dependency Evidence output directories. */
final class DependencyEvidenceOutputDirectory {

    /** Owner marker filename. */
    static final String OWNER_MARKER = ".cia-evidence-owner";

    private DependencyEvidenceOutputDirectory() {
    }

    static Path validate(
            final String configuredValue,
            final String owner,
            final Path sourceValue) throws MojoFailureException {
        return validate(configuredValue, owner, sourceValue,
                "cia.dependencyEvidence");
    }

    static Path validate(
            final String configuredValue,
            final String owner,
            final Path sourceValue,
            final String propertyPrefix) throws MojoFailureException {
        if (configuredValue == null || configuredValue.trim().isEmpty()) {
            throw new MojoFailureException(
                    propertyPrefix + "Directory is required");
        }
        if (owner == null || owner.isEmpty()) {
            throw new MojoFailureException(
                    propertyPrefix + "Owner is required");
        }
        final Path configured;
        try {
            configured = Paths.get(configuredValue.trim());
        } catch (RuntimeException exception) {
            throw new MojoFailureException(
                    "Invalid dependency evidence directory", exception);
        }
        if (!configured.isAbsolute()) {
            throw new MojoFailureException(
                    "Dependency evidence directory must be absolute: "
                            + configured);
        }
        final Path configuredNormalized = configured.normalize();
        final Path sourceNormalized = sourceValue.toAbsolutePath()
                .normalize();
        final Path directory;
        final Path sourceRoot;
        try {
            directory = configuredNormalized.toRealPath();
            sourceRoot = sourceNormalized.toRealPath();
        } catch (IOException exception) {
            throw new MojoFailureException(
                    "Unable to canonicalize evidence or source directory",
                    exception);
        }
        if (configuredNormalized.startsWith(sourceNormalized)
                || directory.startsWith(sourceRoot)) {
            throw new MojoFailureException(
                    "Dependency evidence directory must be outside the "
                            + "Maven source workspace: " + directory);
        }
        final Path marker = directory.resolve(OWNER_MARKER);
        if (!Files.isRegularFile(marker, LinkOption.NOFOLLOW_LINKS)) {
            throw new MojoFailureException(
                    "Dependency evidence owner marker is unavailable: "
                            + marker);
        }
        try {
            final String actual = new String(Files.readAllBytes(marker),
                    StandardCharsets.UTF_8).trim();
            if (!owner.equals(actual)) {
                throw new MojoFailureException(
                        "Dependency evidence owner token does not match");
            }
        } catch (IOException exception) {
            throw new MojoFailureException(
                    "Unable to read dependency evidence owner marker",
                    exception);
        }
        return directory;
    }
}
