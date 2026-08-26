package io.github.dependencyanalysis.tree;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/** Canonical parser for tree dependency scope expressions. */
final class TreeScopeParser {

    private TreeScopeParser() {
    }

    /**
     * Parses a comma-separated scope expression.
     *
     * @param value raw expression
     * @return normalized stable scopes
     */
    static Set<String> parse(final String value) {
        final Set<String> result = new LinkedHashSet<>();
        Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .map(item -> item.toLowerCase(Locale.ROOT))
                .forEach(result::add);
        return Collections.unmodifiableSet(result);
    }
}
