package io.github.dependencyanalysis.tree;

/** Version fields for one dependency occurrence. */
final class OccurrenceVersions {

    /** Requested version. */
    private final String requested;

    /** Version before management. */
    private final String managedFrom;

    /** Effective version. */
    private final String effective;

    /** Selected version. */
    private final String selected;

    /**
     * Creates version fields.
     *
     * @param requestedVersion requested version
     * @param managedVersion version before management
     * @param effectiveVersion effective version
     * @param selectedVersion selected version
     */
    OccurrenceVersions(
            final String requestedVersion,
            final String managedVersion,
            final String effectiveVersion,
            final String selectedVersion) {
        requested = requestedVersion;
        managedFrom = managedVersion;
        effective = effectiveVersion;
        selected = selectedVersion;
    }

    /** @return requested version */
    String requested() {
        return requested;
    }

    /** @return version before management */
    String managedFrom() {
        return managedFrom;
    }

    /** @return effective version */
    String effective() {
        return effective;
    }

    /** @return selected version */
    String selected() {
        return selected;
    }
}
