package io.github.dependencyanalysis.callgraph.entrypoint;

import java.util.List;

/**
 * Immutable target/classes index used to construct module entrypoints.
 *
 * @param selectedClassNames selected non-interface internal class names
 * @param entrypointCount selected non-abstract declared method count
 */
public record EntrypointClassIndex(
        List<String> selectedClassNames,
        int entrypointCount) {

    /** Creates an immutable index. */
    public EntrypointClassIndex {
        selectedClassNames = List.copyOf(selectedClassNames);
    }

    /** @return selection metrics available before Call Graph construction */
    public EntrypointSelectionMetrics metrics() {
        return new EntrypointSelectionMetrics(
                selectedClassNames.size(), entrypointCount, 0);
    }
}
