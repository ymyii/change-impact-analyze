package io.github.dependencyanalysis.tree;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses Maven dependency plugin standard text tokens. */
public final class DependencyTextParser {

    /** Maven console prefix length. */
    private static final int INFO_PREFIX_LENGTH = 7;

    /** Standard tree token group width. */
    private static final int TOKEN_WIDTH = 3;

    /** Minimum coordinate field count. */
    private static final int MIN_FIELDS = 4;

    /** Maximum coordinate field count. */
    private static final int MAX_FIELDS = 6;

    /** Coordinate with scope field count. */
    private static final int SCOPED_FIELDS = 5;

    /** Classifier index. */
    private static final int CLASSIFIER_INDEX = 3;

    /** Version after classifier index. */
    private static final int CLASSIFIED_VERSION_INDEX = 4;

    /** Scope after classifier index. */
    private static final int CLASSIFIED_SCOPE_INDEX = 5;

    /** Regex group containing coordinate text. */
    private static final int COORDINATE_GROUP = 3;

    /** Tree token pattern. */
    private static final Pattern TREE_LINE =
            Pattern.compile("^((?:(?:\\|  |   ))*)"
                    + "(?:(\\+- |\\\\- ))?(.*)$");

    /** Managed version annotation. */
    private static final Pattern MANAGED_VERSION =
            Pattern.compile("version managed from ([^;)]+)",
                    Pattern.CASE_INSENSITIVE);

    /** Managed scope annotation. */
    private static final Pattern MANAGED_SCOPE =
            Pattern.compile("scope managed from ([^;)]+)",
                    Pattern.CASE_INSENSITIVE);

    /** Conflict selected version. */
    private static final Pattern CONFLICT =
            Pattern.compile("omitted for conflict with ([^ )]+)",
                    Pattern.CASE_INSENSITIVE);

    /**
     * Parses one output file.
     *
     * @param text plugin text output
     * @param reactorKeys reactor conflict keys
     * @param scopes included scopes
     * @return parsed tree
     */
    public ParsedModuleTree parse(
            final String text,
            final Set<DependencyKey> reactorKeys,
            final Set<String> scopes) {
        try {
            return parse(new StringReader(text), reactorKeys, scopes);
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unexpected in-memory dependency text failure",
                    exception);
        }
    }

    /**
     * Parses one dependency plugin stream without materializing all lines.
     *
     * @param input UTF-8 dependency plugin reader
     * @param reactorKeys reactor conflict keys
     * @param scopes included scopes
     * @return parsed tree
     * @throws IOException on input failure
     */
    public ParsedModuleTree parse(
            final Reader input,
            final Set<DependencyKey> reactorKeys,
            final Set<String> scopes) throws IOException {
        final Map<Integer, String> stack =
                new HashMap<>();
        final List<DependencyOccurrence> result =
                new ArrayList<>();
        String root = "unknown";
        boolean foundRoot = false;
        final BufferedReader lines = input instanceof BufferedReader
                ? (BufferedReader) input : new BufferedReader(input);
        String rawLine;
        while ((rawLine = lines.readLine()) != null) {
            String line = rawLine;
            if (line.startsWith("[INFO] ")) {
                line = line.substring(
                        INFO_PREFIX_LENGTH);
            }
            final Matcher lineMatcher =
                    TREE_LINE.matcher(line);
            if (!lineMatcher.matches()) {
                continue;
            }
            final String coordinateText =
                    lineMatcher.group(
                            COORDINATE_GROUP).trim();
            final ParsedCoordinate coordinate =
                    coordinate(coordinateText);
            if (coordinate == null) {
                continue;
            }
            final int depth = lineMatcher.group(1)
                    .length() / TOKEN_WIDTH
                    + (lineMatcher.group(2) == null
                    ? 0 : 1);
            if (!foundRoot && depth == 0) {
                root = coordinate.display;
                stack.put(0, root);
                foundRoot = true;
                continue;
            }
            if (!foundRoot || depth == 0) {
                continue;
            }
            stack.keySet().removeIf(
                    key -> key >= depth);
            stack.put(depth, coordinate.display);
            if (!scopes.contains(coordinate.scope
                    .toLowerCase(Locale.ROOT))) {
                continue;
            }
            final List<String> path =
                    new ArrayList<>();
            for (int index = 0;
                 index <= depth; index++) {
                if (stack.containsKey(index)) {
                    path.add(stack.get(index));
                }
            }
            final String annotation =
                    coordinate.annotation;
            final String managedVersion = find(
                    MANAGED_VERSION, annotation);
            final String managedScope = find(
                    MANAGED_SCOPE, annotation);
            final String conflictVersion = find(
                    CONFLICT, annotation);
            final boolean selected = !annotation
                    .toLowerCase(Locale.ROOT)
                    .contains("omitted for");
            final String reason = selected ? ""
                    : omittedReason(annotation);
            final Boolean optional = annotation
                    .toLowerCase(Locale.ROOT)
                    .contains("optional")
                    ? Boolean.TRUE : null;
            final OccurrenceVersions versions =
                    new OccurrenceVersions(
                            managedVersion.isBlank()
                                    ? coordinate.version
                                    : managedVersion,
                            managedVersion,
                            coordinate.version,
                            conflictVersion.isBlank()
                                    ? coordinate.version
                                    : conflictVersion);
            result.add(new DependencyOccurrence(
                    coordinate.key, versions,
                    new OccurrenceScopes(
                            coordinate.scope,
                            managedScope), optional,
                    new OccurrenceSelection(
                            selected, reason), path,
                    coordinate.reactorMarker
                            || reactorKeys.contains(
                            coordinate.key)));
        }
        return new ParsedModuleTree(root, result);
    }

    private ParsedCoordinate coordinate(
            final String value) {
        final int annotationStart =
                value.indexOf(" (");
        final int moduleStart =
                value.indexOf(" -- module ");
        int end = value.length();
        if (annotationStart >= 0) {
            end = annotationStart;
        }
        if (moduleStart >= 0) {
            end = Math.min(end, moduleStart);
        }
        final String token = value.substring(0, end)
                .trim();
        final String annotation = value
                .substring(end).trim();
        final String[] parts = token.split(":", -1);
        if (parts.length < MIN_FIELDS
                || parts.length > MAX_FIELDS) {
            return null;
        }
        final boolean scopedFive = parts.length
                == SCOPED_FIELDS
                && isScope(parts[
                CLASSIFIED_VERSION_INDEX]);
        final boolean classified = parts.length
                == MAX_FIELDS
                || (parts.length == SCOPED_FIELDS
                && !scopedFive);
        final String classifier = classified
                ? parts[CLASSIFIER_INDEX] : "";
        final String version = classified
                ? parts[CLASSIFIED_VERSION_INDEX]
                : parts[CLASSIFIER_INDEX];
        final String scope = parts.length
                == MAX_FIELDS
                ? parts[CLASSIFIED_SCOPE_INDEX]
                : scopedFive
                ? parts[CLASSIFIED_VERSION_INDEX] : "";
        return new ParsedCoordinate(
                new DependencyKey(parts[0], parts[1],
                        parts[2], classifier),
                version, scope, annotation, token,
                moduleStart >= 0);
    }

    private boolean isScope(final String value) {
        return Set.of("compile", "runtime", "provided",
                        "test", "system", "import")
                .contains(value.toLowerCase(Locale.ROOT));
    }

    private String find(
            final Pattern pattern,
            final String value) {
        final Matcher matcher = pattern.matcher(value);
        return matcher.find()
                ? matcher.group(1).trim() : "";
    }

    private String omittedReason(
            final String annotation) {
        final String lower = annotation
                .toLowerCase(Locale.ROOT);
        if (lower.contains("conflict")) {
            return "conflict";
        }
        if (lower.contains("duplicate")) {
            return "duplicate";
        }
        if (lower.contains("cycle")) {
            return "cycle";
        }
        return annotation;
    }

    /** Parsed coordinate fields. */
    private static final class ParsedCoordinate {

        /** Key. */
        private final DependencyKey key;

        /** Version. */
        private final String version;

        /** Scope. */
        private final String scope;

        /** Annotation. */
        private final String annotation;

        /** Display coordinate. */
        private final String display;

        /** Maven reactor module marker. */
        private final boolean reactorMarker;

        private ParsedCoordinate(
                final DependencyKey dependencyKey,
                final String dependencyVersion,
                final String dependencyScope,
                final String dependencyAnnotation,
                final String displayCoordinate,
                final boolean isReactorModule) {
            key = dependencyKey;
            version = dependencyVersion;
            scope = dependencyScope;
            annotation = dependencyAnnotation;
            display = displayCoordinate;
            reactorMarker = isReactorModule;
        }
    }
}
