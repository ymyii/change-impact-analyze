package io.github.dependencyanalysis.tree;

/** Scope fields for one dependency occurrence. */
final class OccurrenceScopes {

    /** Effective scope. */
    private final String effective;

    /** Scope before management. */
    private final String managedFrom;

    /**
     * Creates scope fields.
     *
     * @param effectiveScope effective scope
     * @param managedScope scope before management
     */
    OccurrenceScopes(
            final String effectiveScope,
            final String managedScope) {
        effective = effectiveScope;
        managedFrom = managedScope;
    }

    /** @return effective scope */
    String effective() {
        return effective;
    }

    /** @return scope before management */
    String managedFrom() {
        return managedFrom;
    }
}
