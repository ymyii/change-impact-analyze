package io.github.dependencyanalysis.dependency;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Parses Maven Dependency Plugin absolute artifact list output. */
public final class ResolvedArtifactListParser {

    /** Minimum scope token position without classifier. */
    private static final int MIN_SCOPE_INDEX = 4;

    /** Maximum supported scope token position with classifier. */
    private static final int MAX_SCOPE_INDEX = 5;

    /** Classifier token position. */
    private static final int CLASSIFIER_INDEX = 3;

    /** Version token position with classifier. */
    private static final int CLASSIFIED_VERSION_INDEX = 4;

    /** Version token position without classifier. */
    private static final int VERSION_INDEX = 3;

    /** Maven Dependency Plugin Java module annotation separator. */
    private static final String MODULE_ANNOTATION = " -- module ";

    /** Private utility constructor. */
    private ResolvedArtifactListParser() {
    }

    /**
     * Parses one module-local dependency list.
     *
     * @param file dependency list file
     * @return resolved artifacts
     * @throws IOException when the file cannot be read
     */
    public static List<ResolvedArtifact> parse(final Path file)
            throws IOException {
        return parse(file, file.getParent());
    }

    /**
     * Parses one dependency list and binds its entries to an explicit module.
     *
     * @param file dependency list file
     * @param modulePath owning original module path
     * @return resolved artifacts
     * @throws IOException when the file cannot be read
     */
    public static List<ResolvedArtifact> parse(
            final Path file,
            final Path modulePath) throws IOException {
        final List<ResolvedArtifact> result = new ArrayList<>();
        for (String line : Files.readAllLines(
                file, StandardCharsets.UTF_8)) {
            final ResolvedArtifact artifact = parseLine(
                    modulePath, line);
            if (artifact != null) {
                result.add(artifact);
            }
        }
        result.sort(Comparator
                .comparing((ResolvedArtifact item) ->
                        item.getArtifact().toString())
                .thenComparing(item -> item.getScope().getValue())
                .thenComparing(item -> item.getPath().toString()));
        return List.copyOf(result);
    }

    private static ResolvedArtifact parseLine(
            final Path modulePath,
            final String rawLine) throws IOException {
        final String trimmed = rawLine.trim();
        final int moduleAnnotation = trimmed.indexOf(MODULE_ANNOTATION);
        final String line = moduleAnnotation < 0 ? trimmed
                : trimmed.substring(0, moduleAnnotation);
        if (line.isEmpty() || line.startsWith("The following")
                || line.startsWith("None")) {
            return null;
        }
        final String[] parts = line.split(":", -1);
        final int scopeIndex = findScopeIndex(parts);
        if (scopeIndex < 0 || parts.length <= scopeIndex + 1) {
            return null;
        }
        final String classifier = scopeIndex == MAX_SCOPE_INDEX
                ? parts[CLASSIFIER_INDEX] : "";
        final String version = scopeIndex == MAX_SCOPE_INDEX
                ? parts[CLASSIFIED_VERSION_INDEX]
                : parts[VERSION_INDEX];
        final DependencyScope scope = DependencyScope.fromString(
                parts[scopeIndex]);
        final Path physical = Path.of(joinPath(parts, scopeIndex + 1));
        if (!physical.isAbsolute()) {
            throw new IOException("Resolved artifact path is not absolute: "
                    + physical + " in " + fileContext(modulePath));
        }
        if (!Files.exists(physical)) {
            throw new IOException("Resolved artifact path does not exist: "
                    + physical + " in " + fileContext(modulePath));
        }
        return new ResolvedArtifact(modulePath,
                new ArtifactCoord(parts[0], parts[1], parts[2],
                        version, classifier),
                scope, physical.toRealPath());
    }

    private static int findScopeIndex(final String[] parts) {
        final int upper = Math.min(MAX_SCOPE_INDEX,
                parts.length - 2);
        for (int index = MIN_SCOPE_INDEX; index <= upper; index++) {
            if (DependencyScope.fromString(parts[index]) != null) {
                return index;
            }
        }
        return -1;
    }

    private static String joinPath(
            final String[] parts, final int start) {
        final StringBuilder result = new StringBuilder(parts[start]);
        for (int index = start + 1; index < parts.length; index++) {
            result.append(':').append(parts[index]);
        }
        return result.toString();
    }

    private static String fileContext(final Path modulePath) {
        return modulePath == null ? "unknown module" : modulePath.toString();
    }
}
