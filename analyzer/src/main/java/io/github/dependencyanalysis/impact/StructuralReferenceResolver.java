package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePointKind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Binds raw structural references to relevant class ChangePoints at query. */
final class StructuralReferenceResolver {

    List<StructuralReferenceMatch> resolve(
            final List<BoundChangePoint> changePoints,
            final StructuralReferenceIndex index) {
        Objects.requireNonNull(changePoints, "changePoints");
        Objects.requireNonNull(index, "index");
        final Map<String, List<BoundChangePoint>> byOwner =
                new LinkedHashMap<>();
        changePoints.stream()
                .filter(this::supports)
                .sorted(Comparator.comparing(BoundChangePoint::stableKey))
                .forEach(point -> byOwner.computeIfAbsent(
                        point.getChangePoint().getOwner(),
                        ignored -> new ArrayList<>()).add(point));
        final ArrayList<StructuralReferenceMatch> matches = new ArrayList<>();
        for (StructuralReference reference : index.references()) {
            for (BoundChangePoint point : byOwner.getOrDefault(
                    reference.getChangedClass(), List.of())) {
                matches.add(new StructuralReferenceMatch(point, reference));
            }
        }
        return matches.stream().sorted(Comparator
                .comparing((StructuralReferenceMatch value) ->
                        value.changePoint().stableKey())
                .thenComparing(value -> value.reference().stableKey()))
                .toList();
    }

    private boolean supports(final BoundChangePoint point) {
        final ChangePointKind kind = point.getChangePoint().getKind();
        return kind == ChangePointKind.CLASS_REMOVED
                || kind == ChangePointKind.CLASS_ACCESS_NARROWED;
    }
}
