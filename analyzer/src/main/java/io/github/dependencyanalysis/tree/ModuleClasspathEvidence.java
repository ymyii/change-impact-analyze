package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Strict Classpath Evidence Schema v1 value for one Module.
 *
 * @param javaMajor Maven JVM major version
 * @param module owning Module coordinate
 * @param moduleDirectory canonical Module directory
 * @param entries effective classpath order
 * @param issues incomplete classpath reasons
 */
public record ModuleClasspathEvidence(
        int javaMajor,
        ArtifactCoord module,
        Path moduleDirectory,
        List<ClasspathEvidenceEntry> entries,
        List<String> issues) {

    /** Validates immutable Module evidence. */
    public ModuleClasspathEvidence {
        if (javaMajor < 1) {
            throw new IllegalArgumentException("Invalid Java major version");
        }
        Objects.requireNonNull(module, "module");
        moduleDirectory = Objects.requireNonNull(
                moduleDirectory, "moduleDirectory")
                .toAbsolutePath().normalize();
        entries = List.copyOf(entries);
        issues = List.copyOf(issues);
    }
}
