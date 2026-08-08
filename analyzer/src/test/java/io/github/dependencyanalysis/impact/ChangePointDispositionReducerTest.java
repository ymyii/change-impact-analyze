package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePointKind;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChangePointDispositionReducerTest {

    /** Reducer under test. */
    private final ChangePointDispositionReducer reducer =
            new ChangePointDispositionReducer();

    @Test
    void pathOutranksAllNonImpactOutcomes() {
        assertThat(reduce(ChangePointKind.CLASS_ACCESS_NARROWED,
                ReferenceObservation.ACCESSIBLE_ONLY,
                false, true, false, true, true))
                .isEqualTo(ChangePointDisposition.IMPACT_REPORTED);
        assertThat(reduce(ChangePointKind.CLASS_REMOVED,
                ReferenceObservation.NONE,
                false, false, true, true, false))
                .isEqualTo(ChangePointDisposition.IMPACT_REPORTED);
    }

    @Test
    void inaccessibleSeedWithoutProjectPathIsNoProjectPath() {
        assertThat(reduce(ChangePointKind.METHOD_ACCESS_NARROWED,
                ReferenceObservation.IMPACTING,
                true, false, false, false, false))
                .isEqualTo(ChangePointDisposition.NO_PROJECT_PATH);
    }

    @Test
    void structuralAndAccessFallbacksRemainDistinct() {
        assertThat(reduce(ChangePointKind.CLASS_REMOVED,
                ReferenceObservation.NONE,
                false, false, false, true, false))
                .isEqualTo(ChangePointDisposition
                        .UNREACHABLE_STRUCTURAL_REFERENCE);
        assertThat(reduce(ChangePointKind.FIELD_ACCESS_NARROWED,
                ReferenceObservation.ACCESSIBLE_ONLY,
                false, false, false, false, false))
                .isEqualTo(ChangePointDisposition.ACCESS_REMAINS_VALID);
        assertThat(reduce(ChangePointKind.CLASS_ACCESS_NARROWED,
                ReferenceObservation.NONE,
                false, false, false, false, true))
                .isEqualTo(ChangePointDisposition.ACCESS_REMAINS_VALID);
    }

    @Test
    void missingBodyAndDeclaredReferencesHaveDifferentOutcomes() {
        assertThat(reduce(ChangePointKind.METHOD_BODY_CHANGED,
                ReferenceObservation.NONE,
                false, false, false, false, false))
                .isEqualTo(ChangePointDisposition.TARGET_NOT_FOUND);
        assertThat(reduce(ChangePointKind.METHOD_REMOVED,
                ReferenceObservation.NONE,
                false, false, false, false, false))
                .isEqualTo(ChangePointDisposition
                        .DECLARED_REFERENCE_NOT_FOUND);
    }

    private ChangePointDisposition reduce(
            final ChangePointKind kind,
            final ReferenceObservation observation,
            final boolean hasSeeds,
            final boolean seedReachedProject,
            final boolean structuralPath,
            final boolean structuralUnreachable,
            final boolean structuralAccessObserved) {
        return reducer.reduce(kind, observation, hasSeeds,
                seedReachedProject, structuralPath, structuralUnreachable,
                structuralAccessObserved);
    }
}
