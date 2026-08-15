package io.github.dependencyanalysis.impact;

import java.util.List;
import java.util.Objects;

/** User-reviewable bytecode-derived old/new code comparison. */
public final class CodeComparisonEvidence {

    /** Evidence status. */
    private final CodeComparisonStatus status;

    /** Decompiled Java Unified diff hunks. */
    private final List<UnifiedDiffHunk> hunks;

    /** Failure reason, empty when unnecessary. */
    private final String reason;

    /**
     * Creates code comparison evidence.
     *
     * @param value comparison status
     * @param diffHunks complete Unified diff hunks
     * @param detail failure reason
     */
    public CodeComparisonEvidence(
            final CodeComparisonStatus value,
            final List<UnifiedDiffHunk> diffHunks,
            final String detail) {
        status = Objects.requireNonNull(value, "status");
        hunks = List.copyOf(diffHunks);
        reason = Objects.requireNonNull(detail, "detail");
    }

    /** @return evidence status */
    public CodeComparisonStatus getStatus() {
        return status;
    }

    /** @return complete Unified diff hunks */
    public List<UnifiedDiffHunk> getHunks() {
        return hunks;
    }

    /** @return failure reason, possibly empty */
    public String getReason() {
        return reason;
    }

    /** @return complete Git-style Unified diff */
    public String getUnifiedDiff() {
        if (hunks.isEmpty()) {
            return "";
        }
        final StringBuilder result = new StringBuilder()
                .append("--- old/decompiled.java\n")
                .append("+++ new/decompiled.java\n");
        hunks.forEach(hunk -> result.append(hunk.toUnifiedText()));
        return result.toString();
    }
}
