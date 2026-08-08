package io.github.dependencyanalysis.bytecode;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests for {@link JvmAccess} and {@link AccessTransition}. */
class JvmAccessTest {

    @Test
    void normalizesClassFlags() {
        assertThat(JvmAccess.fromClassFlags(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL))
                .isEqualTo(JvmAccess.PUBLIC);
        assertThat(JvmAccess.fromClassFlags(Opcodes.ACC_FINAL))
                .isEqualTo(JvmAccess.PACKAGE_PRIVATE);
    }

    @Test
    void classFlagsRejectMemberOnlyVisibility() {
        assertThatThrownBy(() -> JvmAccess.fromClassFlags(
                Opcodes.ACC_PROTECTED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JvmAccess.fromClassFlags(
                Opcodes.ACC_PRIVATE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizesMemberFlags() {
        assertThat(JvmAccess.fromMemberFlags(Opcodes.ACC_PUBLIC))
                .isEqualTo(JvmAccess.PUBLIC);
        assertThat(JvmAccess.fromMemberFlags(Opcodes.ACC_PROTECTED))
                .isEqualTo(JvmAccess.PROTECTED);
        assertThat(JvmAccess.fromMemberFlags(Opcodes.ACC_STATIC))
                .isEqualTo(JvmAccess.PACKAGE_PRIVATE);
        assertThat(JvmAccess.fromMemberFlags(Opcodes.ACC_PRIVATE))
                .isEqualTo(JvmAccess.PRIVATE);
    }

    @Test
    void rejectsMutuallyExclusiveVisibilityFlags() {
        assertThatThrownBy(() -> JvmAccess.fromMemberFlags(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
        assertThatThrownBy(() -> JvmAccess.fromMemberFlags(
                Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
        assertThatThrownBy(() -> JvmAccess.fromClassFlags(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
    }

    @Test
    void recognizesOnlyStrictNarrowingTransitions() {
        for (JvmAccess oldAccess : JvmAccess.values()) {
            for (JvmAccess newAccess : JvmAccess.values()) {
                assertThat(oldAccess.narrowsTo(newAccess))
                        .as("%s -> %s", oldAccess, newAccess)
                        .isEqualTo(isConfirmedNarrowing(
                                oldAccess, newAccess));
            }
            assertThat(oldAccess.narrowsTo(null)).isFalse();
        }
    }

    @Test
    void transitionExposesStableValidatedValues() {
        final AccessTransition transition = new AccessTransition(
                JvmAccess.PUBLIC, JvmAccess.PRIVATE);

        assertThat(transition.oldAccess()).isEqualTo(JvmAccess.PUBLIC);
        assertThat(transition.newAccess()).isEqualTo(JvmAccess.PRIVATE);
        assertThat(transition.stableKey()).isEqualTo("PUBLIC->PRIVATE");
        assertThat(transition).isEqualTo(new AccessTransition(
                JvmAccess.PUBLIC, JvmAccess.PRIVATE));
    }

    @Test
    void transitionRejectsNullAndNonNarrowingValues() {
        assertThatThrownBy(() -> new AccessTransition(
                null, JvmAccess.PRIVATE))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("oldAccess");
        assertThatThrownBy(() -> new AccessTransition(
                JvmAccess.PUBLIC, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("newAccess");
        assertThatThrownBy(() -> new AccessTransition(
                JvmAccess.PROTECTED, JvmAccess.PUBLIC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly narrowing");
        assertThatThrownBy(() -> new AccessTransition(
                JvmAccess.PRIVATE, JvmAccess.PRIVATE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly narrowing");
    }

    private boolean isConfirmedNarrowing(
            final JvmAccess oldAccess,
            final JvmAccess newAccess) {
        return oldAccess == JvmAccess.PUBLIC
                && newAccess != JvmAccess.PUBLIC
                || oldAccess == JvmAccess.PROTECTED
                && (newAccess == JvmAccess.PACKAGE_PRIVATE
                || newAccess == JvmAccess.PRIVATE)
                || oldAccess == JvmAccess.PACKAGE_PRIVATE
                && newAccess == JvmAccess.PRIVATE;
    }
}
