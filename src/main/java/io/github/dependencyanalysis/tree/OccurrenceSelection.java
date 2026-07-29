package io.github.dependencyanalysis.tree;

/** Selection fields for one dependency occurrence. */
final class OccurrenceSelection {

    /** Selected flag. */
    private final boolean selected;

    /** Omitted reason. */
    private final String reason;

    /**
     * Creates selection fields.
     *
     * @param isSelected selected flag
     * @param omittedReason omitted reason
     */
    OccurrenceSelection(
            final boolean isSelected,
            final String omittedReason) {
        selected = isSelected;
        reason = omittedReason;
    }

    /** @return selected flag */
    boolean selected() {
        return selected;
    }

    /** @return omitted reason */
    String reason() {
        return reason;
    }
}
