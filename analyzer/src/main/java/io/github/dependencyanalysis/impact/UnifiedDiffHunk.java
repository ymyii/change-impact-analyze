package io.github.dependencyanalysis.impact;

import java.util.List;

/**
 * One Git-style Unified diff hunk.
 *
 * @param oldStart baseline start line
 * @param oldCount baseline line count
 * @param newStart target start line
 * @param newCount target line count
 * @param lines prefixed context, removal, and addition lines
 */
public record UnifiedDiffHunk(
        int oldStart,
        int oldCount,
        int newStart,
        int newCount,
        List<String> lines) {

    /** Creates an immutable hunk. */
    public UnifiedDiffHunk {
        lines = List.copyOf(lines);
    }

    /** @return Unified diff hunk text */
    public String toUnifiedText() {
        final StringBuilder result = new StringBuilder()
                .append("@@ -").append(oldStart).append(',')
                .append(oldCount).append(" +").append(newStart).append(',')
                .append(newCount).append(" @@\n");
        lines.forEach(line -> result.append(line).append('\n'));
        return result.toString();
    }
}
