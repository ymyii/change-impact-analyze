package io.github.dependencyanalysis.tree;

import java.util.ArrayList;
import java.util.List;

/** Converts dependency occurrences into a Maven-style text tree. */
final class MavenTextTreeFormatter {

    /**
     * Formats one Module dependency tree.
     *
     * @param module Module evidence
     * @return text tree
     */
    String format(final ModuleTreeResult module) {
        final StringBuilder result = new StringBuilder();
        final List<DependencyOccurrence> occurrences =
                module.getOccurrences();
        String rootCoordinate = module.getCoordinate();
        for (DependencyOccurrence occurrence : occurrences) {
            if (!occurrence.getPath().isEmpty()) {
                rootCoordinate = occurrence.getPath().get(0);
                break;
            }
        }
        List<String> previousPath = List.of();
        for (int occurrenceIndex = 0;
             occurrenceIndex < occurrences.size(); occurrenceIndex++) {
            final DependencyOccurrence occurrence =
                    occurrences.get(occurrenceIndex);
            final List<String> path = occurrencePath(
                    occurrence, rootCoordinate);
            final int common = commonPrefixLength(previousPath, path);
            final int start = common == path.size()
                    ? Math.max(0, path.size() - 1) : common;
            for (int index = start; index < path.size(); index++) {
                final boolean terminal = index == path.size() - 1;
                appendMavenLine(result, path.subList(0, index + 1),
                        terminal ? occurrence.coordinate() : path.get(index),
                        terminal ? occurrence : null, occurrences,
                        occurrenceIndex, rootCoordinate);
            }
            previousPath = path;
        }
        return result.toString();
    }

    private List<String> occurrencePath(
            final DependencyOccurrence occurrence,
            final String rootCoordinate) {
        return occurrence.getPath().isEmpty()
                ? List.of(rootCoordinate, occurrence.coordinate())
                : occurrence.getPath();
    }

    private int commonPrefixLength(
            final List<String> left,
            final List<String> right) {
        final int maximum = Math.min(left.size(), right.size());
        int common = 0;
        while (common < maximum && left.get(common)
                .equals(right.get(common))) {
            common++;
        }
        return common;
    }

    private void appendMavenLine(
            final StringBuilder output,
            final List<String> path,
            final String coordinate,
            final DependencyOccurrence occurrence,
            final List<DependencyOccurrence> occurrences,
            final int occurrenceIndex,
            final String rootCoordinate) {
        final int depth = path.size() - 1;
        for (int ancestor = 1; ancestor < depth; ancestor++) {
            output.append(hasLaterSibling(path, occurrences,
                    occurrenceIndex, ancestor, rootCoordinate, false)
                    ? "|  " : "   ");
        }
        if (depth > 0) {
            output.append(hasLaterSibling(path, occurrences,
                    occurrenceIndex, depth, rootCoordinate,
                    occurrence != null) ? "+- " : "\\- ");
        }
        output.append(coordinate);
        if (occurrence != null) {
            output.append(verboseAnnotation(occurrence));
        }
        output.append('\n');
    }

    private boolean hasLaterSibling(
            final List<String> current,
            final List<DependencyOccurrence> occurrences,
            final int currentIndex,
            final int depth,
            final String rootCoordinate,
            final boolean terminal) {
        for (int index = currentIndex + 1;
             index < occurrences.size(); index++) {
            final List<String> candidate = occurrencePath(
                    occurrences.get(index), rootCoordinate);
            if (candidate.size() <= depth
                    || !current.subList(0, depth).equals(
                    candidate.subList(0, depth))) {
                return false;
            }
            if (current.get(depth).equals(candidate.get(depth))) {
                if (terminal && candidate.size() == current.size()) {
                    return true;
                }
                continue;
            }
            return true;
        }
        return false;
    }

    private String verboseAnnotation(
            final DependencyOccurrence occurrence) {
        final List<String> values = new ArrayList<>();
        if (!occurrence.getManagedFromVersion().isBlank()) {
            values.add("version managed from "
                    + occurrence.getManagedFromVersion());
        }
        if (!occurrence.getManagedFromScope().isBlank()) {
            values.add("scope managed from "
                    + occurrence.getManagedFromScope());
        }
        if (Boolean.TRUE.equals(occurrence.getOptional())) {
            values.add("optional");
        }
        if (!occurrence.isSelected()) {
            values.add(omittedAnnotation(occurrence));
        }
        if (occurrence.isReactorModule()) {
            values.add("reactor module");
        }
        return values.isEmpty() ? ""
                : " (" + String.join("; ", values) + ")";
    }

    private String omittedAnnotation(
            final DependencyOccurrence occurrence) {
        final String reason = occurrence.getOmittedReason();
        if (reason.equals("conflict")) {
            return "omitted for conflict with "
                    + occurrence.getSelectedVersion();
        }
        if (reason.startsWith("omitted ")) {
            return reason;
        }
        return reason.isBlank() ? "omitted" : "omitted for " + reason;
    }
}
