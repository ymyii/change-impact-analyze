package io.github.dependencyanalysis.tree;

import java.util.Locale;
import java.util.Set;

record TreeDependencyCoordinate(
        DependencyKey key,
        String display,
        String scope) {

    /** Known Maven scope names. */
    private static final Set<String> SCOPES = Set.of(
            "compile", "runtime", "provided", "test", "system", "import");

    /** Minimum coordinate part count. */
    private static final int MIN_PARTS = 4;

    /** Maximum coordinate part count. */
    private static final int MAX_PARTS = 6;

    /** Coordinate part count with either classifier or scope. */
    private static final int OPTIONAL_PARTS = 5;

    /** Classifier index. */
    private static final int CLASSIFIER_INDEX = 3;

    /** Scope index without classifier. */
    private static final int SIMPLE_SCOPE_INDEX = 4;

    /** Scope index with classifier. */
    private static final int CLASSIFIED_SCOPE_INDEX = 5;

    /**
     * Parses a coordinate already accepted by {@link DependencyTextParser}.
     *
     * @param value coordinate display token
     * @return identity and display fields
     */
    static TreeDependencyCoordinate parse(final String value) {
        final int annotation = value.indexOf(" (");
        final int module = value.indexOf(" -- module ");
        int end = value.length();
        if (annotation >= 0) {
            end = annotation;
        }
        if (module >= 0) {
            end = Math.min(end, module);
        }
        final String token = value.substring(0, end).trim();
        final String[] parts = token.split(":", -1);
        if (parts.length < MIN_PARTS || parts.length > MAX_PARTS) {
            throw new IllegalArgumentException(
                    "Invalid dependency path coordinate: " + value);
        }
        final boolean scopedFive = parts.length == OPTIONAL_PARTS
                && SCOPES.contains(parts[SIMPLE_SCOPE_INDEX]
                .toLowerCase(Locale.ROOT));
        final boolean classified = parts.length == MAX_PARTS
                || parts.length == OPTIONAL_PARTS && !scopedFive;
        final String classifier = classified
                ? parts[CLASSIFIER_INDEX] : "";
        final String scope = parts.length == MAX_PARTS
                ? parts[CLASSIFIED_SCOPE_INDEX]
                : scopedFive ? parts[SIMPLE_SCOPE_INDEX] : "";
        return new TreeDependencyCoordinate(new DependencyKey(
                parts[0], parts[1], parts[2], classifier), token, scope);
    }
}
