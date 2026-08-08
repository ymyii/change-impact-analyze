package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePointKind;

import java.util.Objects;

/** Pure final-disposition policy after seed and structural path resolution. */
final class ChangePointDispositionReducer {

    ChangePointDisposition reduce(
            final ChangePointKind kind,
            final ReferenceObservation observation,
            final boolean hasSeeds,
            final boolean seedReachedProject,
            final boolean structuralPath,
            final boolean structuralUnreachable,
            final boolean structuralAccessObserved) {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(observation, "observation");
        if (seedReachedProject || structuralPath) {
            return ChangePointDisposition.IMPACT_REPORTED;
        }
        if (hasSeeds) {
            return ChangePointDisposition.NO_PROJECT_PATH;
        }
        if (structuralUnreachable) {
            return ChangePointDisposition.UNREACHABLE_STRUCTURAL_REFERENCE;
        }
        if (kind.isAccessNarrowing()
                && (observation == ReferenceObservation.ACCESSIBLE_ONLY
                || structuralAccessObserved)) {
            return ChangePointDisposition.ACCESS_REMAINS_VALID;
        }
        return kind == ChangePointKind.METHOD_BODY_CHANGED
                ? ChangePointDisposition.TARGET_NOT_FOUND
                : ChangePointDisposition.DECLARED_REFERENCE_NOT_FOUND;
    }
}
