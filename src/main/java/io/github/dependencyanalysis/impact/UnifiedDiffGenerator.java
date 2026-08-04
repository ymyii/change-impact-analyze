package io.github.dependencyanalysis.impact;

import java.util.ArrayList;
import java.util.List;

/** Deterministic complete Unified diff generator with three context lines. */
final class UnifiedDiffGenerator {

    /** Context lines around each change. */
    private static final int CONTEXT = 3;

    /** Maximum dynamic-programming matrix cells. */
    private static final long MAX_MATRIX_CELLS = 4_000_000L;

    List<UnifiedDiffHunk> diff(
            final String oldText, final String newText) {
        final List<String> oldLines = lines(oldText);
        final List<String> newLines = lines(newText);
        final List<Edit> edits = (long) oldLines.size() * newLines.size()
                <= MAX_MATRIX_CELLS
                ? lcsEdits(oldLines, newLines)
                : replacementEdits(oldLines, newLines);
        return hunks(edits);
    }

    private List<String> lines(final String value) {
        if (value.isEmpty()) {
            return List.of();
        }
        return List.of(value.replace("\r\n", "\n")
                .replace('\r', '\n').split("\n", -1));
    }

    private List<Edit> lcsEdits(
            final List<String> oldLines,
            final List<String> newLines) {
        final int[][] lengths = new int[oldLines.size() + 1]
                [newLines.size() + 1];
        for (int oldIndex = oldLines.size() - 1; oldIndex >= 0; oldIndex--) {
            for (int newIndex = newLines.size() - 1;
                    newIndex >= 0; newIndex--) {
                lengths[oldIndex][newIndex] = oldLines.get(oldIndex)
                        .equals(newLines.get(newIndex))
                        ? lengths[oldIndex + 1][newIndex + 1] + 1
                        : Math.max(lengths[oldIndex + 1][newIndex],
                        lengths[oldIndex][newIndex + 1]);
            }
        }
        final List<Edit> result = new ArrayList<>();
        int oldIndex = 0;
        int newIndex = 0;
        while (oldIndex < oldLines.size() && newIndex < newLines.size()) {
            if (oldLines.get(oldIndex).equals(newLines.get(newIndex))) {
                result.add(new Edit(' ', oldLines.get(oldIndex++)));
                newIndex++;
            } else if (lengths[oldIndex + 1][newIndex]
                    >= lengths[oldIndex][newIndex + 1]) {
                result.add(new Edit('-', oldLines.get(oldIndex++)));
            } else {
                result.add(new Edit('+', newLines.get(newIndex++)));
            }
        }
        while (oldIndex < oldLines.size()) {
            result.add(new Edit('-', oldLines.get(oldIndex++)));
        }
        while (newIndex < newLines.size()) {
            result.add(new Edit('+', newLines.get(newIndex++)));
        }
        return result;
    }

    private List<Edit> replacementEdits(
            final List<String> oldLines,
            final List<String> newLines) {
        final List<Edit> result = new ArrayList<>();
        oldLines.forEach(line -> result.add(new Edit('-', line)));
        newLines.forEach(line -> result.add(new Edit('+', line)));
        return result;
    }

    private List<UnifiedDiffHunk> hunks(final List<Edit> edits) {
        final List<Integer> changes = new ArrayList<>();
        for (int index = 0; index < edits.size(); index++) {
            if (edits.get(index).prefix() != ' ') {
                changes.add(index);
            }
        }
        if (changes.isEmpty()) {
            return List.of();
        }
        final List<UnifiedDiffHunk> result = new ArrayList<>();
        int groupStart = 0;
        while (groupStart < changes.size()) {
            int groupEnd = groupStart;
            while (groupEnd + 1 < changes.size()
                    && changes.get(groupEnd + 1) - changes.get(groupEnd)
                    <= CONTEXT * 2 + 1) {
                groupEnd++;
            }
            final int start = Math.max(0,
                    changes.get(groupStart) - CONTEXT);
            final int end = Math.min(edits.size(),
                    changes.get(groupEnd) + CONTEXT + 1);
            result.add(hunk(edits, start, end));
            groupStart = groupEnd + 1;
        }
        return List.copyOf(result);
    }

    private UnifiedDiffHunk hunk(
            final List<Edit> edits, final int start, final int end) {
        int oldBefore = 0;
        int newBefore = 0;
        for (int index = 0; index < start; index++) {
            oldBefore += edits.get(index).prefix() == '+' ? 0 : 1;
            newBefore += edits.get(index).prefix() == '-' ? 0 : 1;
        }
        int oldCount = 0;
        int newCount = 0;
        final List<String> lines = new ArrayList<>();
        for (int index = start; index < end; index++) {
            final Edit edit = edits.get(index);
            oldCount += edit.prefix() == '+' ? 0 : 1;
            newCount += edit.prefix() == '-' ? 0 : 1;
            lines.add(edit.prefix() + edit.line());
        }
        return new UnifiedDiffHunk(oldCount == 0 ? oldBefore
                : oldBefore + 1, oldCount,
                newCount == 0 ? newBefore : newBefore + 1,
                newCount, lines);
    }

    /**
     * @param prefix diff line prefix
     * @param line source line
     */
    private record Edit(char prefix, String line) {
    }
}
