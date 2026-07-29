package io.github.dependencyanalysis.tree;

import java.util.List;

/** Parsed dependency plugin text for one module. */
final class ParsedModuleTree {

    /** Root coordinate. */
    private final String rootCoordinate;

    /** Dependency occurrences. */
    private final List<DependencyOccurrence> occurrences;

    /**
     * Creates a parsed tree.
     *
     * @param coordinate root coordinate
     * @param dependencies occurrences
     */
    ParsedModuleTree(
            final String coordinate,
            final List<DependencyOccurrence>
                    dependencies) {
        rootCoordinate = coordinate;
        occurrences = List.copyOf(dependencies);
    }

    /** @return root coordinate */
    String getRootCoordinate() {
        return rootCoordinate;
    }

    /** @return occurrences */
    List<DependencyOccurrence> getOccurrences() {
        return occurrences;
    }
}
