package io.github.dependencyanalysis.bytecode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions
        .assertThat;

/**
 * Tests for
 * {@link ChangePointKind}.
 */
class ChangePointKindTest {

    /** Expected constant count. */
    private static final int COUNT = 13;

    @Test
    void thirteenConstants() {
        assertThat(
                ChangePointKind.values())
                .hasSize(COUNT);
    }

    @Test
    void classAddedExists() {
        assertThat(ChangePointKind
                .CLASS_ADDED)
                .isNotNull();
    }

    @Test
    void classRemovedExists() {
        assertThat(ChangePointKind
                .CLASS_REMOVED)
                .isNotNull();
    }

    @Test
    void accessNarrowingKindsExistAndAreDefault() {
        assertThat(ChangePointKind.DEFAULT_INCLUDED_KINDS)
                .contains(ChangePointKind.CLASS_ACCESS_NARROWED,
                        ChangePointKind.METHOD_ACCESS_NARROWED,
                        ChangePointKind.FIELD_ACCESS_NARROWED,
                        ChangePointKind
                                .SERVICE_PROVIDER_REGISTRATION_REMOVED);
        assertThat(ChangePointKind.CLASS_ACCESS_NARROWED
                .isAccessNarrowing()).isTrue();
    }

    @Test
    void methodAddedExists() {
        assertThat(ChangePointKind
                .METHOD_ADDED)
                .isNotNull();
    }

    @Test
    void methodRemovedExists() {
        assertThat(ChangePointKind
                .METHOD_REMOVED)
                .isNotNull();
    }

    @Test
    void methodDescriptorChangedExists() {
        assertThat(ChangePointKind
                .METHOD_DESCRIPTOR_CHANGED)
                .isNotNull();
    }

    @Test
    void methodBodyChangedExists() {
        assertThat(ChangePointKind
                .METHOD_BODY_CHANGED)
                .isNotNull();
    }

    @Test
    void fieldAddedExists() {
        assertThat(ChangePointKind
                .FIELD_ADDED)
                .isNotNull();
    }

    @Test
    void fieldRemovedExists() {
        assertThat(ChangePointKind
                .FIELD_REMOVED)
                .isNotNull();
    }

    @Test
    void fieldDescriptorChangedExists() {
        assertThat(ChangePointKind
                .FIELD_DESCRIPTOR_CHANGED)
                .isNotNull();
    }

    @Test
    void valueOfRoundTrip() {
        for (final ChangePointKind k
                : ChangePointKind.values()) {
            assertThat(ChangePointKind
                    .valueOf(k.name()))
                    .isEqualTo(k);
        }
    }
}
