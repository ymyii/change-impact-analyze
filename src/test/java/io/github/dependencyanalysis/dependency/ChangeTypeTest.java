package io.github.dependencyanalysis.dependency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions
        .assertThat;

/**
 * Tests for {@link ChangeType}.
 */
class ChangeTypeTest {

    /** Expected constant count. */
    private static final int COUNT = 3;

    @Test
    void threeConstants() {
        assertThat(ChangeType.values())
                .hasSize(COUNT);
    }

    @Test
    void addedExists() {
        assertThat(ChangeType.ADDED)
                .isNotNull();
    }

    @Test
    void removedExists() {
        assertThat(ChangeType.REMOVED)
                .isNotNull();
    }

    @Test
    void versionChangedExists() {
        assertThat(ChangeType
                .VERSION_CHANGED)
                .isNotNull();
    }

    @Test
    void valueOfRoundTrip() {
        for (final ChangeType ct
                : ChangeType.values()) {
            assertThat(ChangeType
                    .valueOf(ct.name()))
                    .isEqualTo(ct);
        }
    }

    @Test
    void ordinalOrder() {
        assertThat(ChangeType.ADDED
                .ordinal())
                .isLessThan(ChangeType
                        .REMOVED.ordinal());
        assertThat(ChangeType.REMOVED
                .ordinal())
                .isLessThan(ChangeType
                        .VERSION_CHANGED
                        .ordinal());
    }
}
