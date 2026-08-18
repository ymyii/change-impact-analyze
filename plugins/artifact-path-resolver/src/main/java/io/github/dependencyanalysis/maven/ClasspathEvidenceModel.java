package io.github.dependencyanalysis.maven;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;

/** Ordered physical classpath evidence for one Maven Module. */
final class ClasspathEvidenceModel {

    /** Maven JVM major version. */
    private final int javaMajor;

    /** Owning Module coordinate. */
    private final ArtifactCoordinates module;

    /** Canonical Module directory. */
    private final Path moduleDirectory;

    /** Ordered effective classpath entries. */
    private final List<Entry> entries;

    /** Incomplete classpath reasons. */
    private final List<String> issues;

    ClasspathEvidenceModel(
            final int runtimeJavaMajor,
            final ArtifactCoordinates moduleCoordinates,
            final Path directory,
            final List<Entry> classpathEntries,
            final List<String> evidenceIssues) {
        javaMajor = runtimeJavaMajor;
        module = Objects.requireNonNull(moduleCoordinates, "module");
        moduleDirectory = Objects.requireNonNull(directory, "moduleDirectory");
        entries = Collections.unmodifiableList(
                new ArrayList<ClasspathEvidenceModel.Entry>(
                        classpathEntries));
        issues = Collections.unmodifiableList(
                new ArrayList<String>(evidenceIssues));
    }

    int getJavaMajor() {
        return javaMajor;
    }

    ArtifactCoordinates getModule() {
        return module;
    }

    Path getModuleDirectory() {
        return moduleDirectory;
    }

    List<Entry> getEntries() {
        return entries;
    }

    List<String> getIssues() {
        return issues;
    }

    /** One selected physical classpath element. */
    static final class Entry {

        /** Zero-based classpath order. */
        private final int order;

        /** Logical source kind. */
        private final String origin;

        /** Logical source coordinate. */
        private final ArtifactCoordinates coordinates;

        /** Maven dependency scope. */
        private final String scope;

        /** Canonical physical path. */
        private final Path path;

        Entry(
                final int classpathOrder,
                final String sourceOrigin,
                final ArtifactCoordinates sourceCoordinates,
                final String dependencyScope,
                final Path physicalPath) {
            order = classpathOrder;
            origin = Objects.requireNonNull(sourceOrigin, "origin");
            coordinates = Objects.requireNonNull(
                    sourceCoordinates, "coordinates");
            scope = Objects.requireNonNull(dependencyScope, "scope");
            path = Objects.requireNonNull(physicalPath, "absolutePath");
        }

        int getOrder() {
            return order;
        }

        String getOrigin() {
            return origin;
        }

        ArtifactCoordinates getCoordinates() {
            return coordinates;
        }

        String getScope() {
            return scope;
        }

        Path getPath() {
            return path;
        }
    }
}
