package io.github.dependencyanalysis.bytecode;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

/**
 * Tests for {@link ChangePoint}.
 */
class ChangePointTest {

    /** Test artifact coord. */
    private static final ArtifactCoord ART =
            new ArtifactCoord(
                    "g", "a", "jar", "1.0");

    @Test
    void constructorRejectsNullArtifact() {
        assertThatThrownBy(
                () -> new ChangePoint(
                        null,
                        ChangePointKind
                                .CLASS_ADDED,
                        "com/Foo",
                        null, null,
                        null, null))
                .isInstanceOf(
                        NullPointerException.class)
                .hasMessageContaining(
                        "artifact");
    }

    @Test
    void constructorRejectsNullKind() {
        assertThatThrownBy(
                () -> new ChangePoint(
                        ART, null,
                        "com/Foo",
                        null, null,
                        null, null))
                .isInstanceOf(
                        NullPointerException.class)
                .hasMessageContaining("kind");
    }

    @Test
    void constructorRejectsNullOwner() {
        assertThatThrownBy(
                () -> new ChangePoint(
                        ART,
                        ChangePointKind
                                .CLASS_ADDED,
                        null,
                        null, null,
                        null, null))
                .isInstanceOf(
                        NullPointerException.class)
                .hasMessageContaining(
                        "owner");
    }

    @Test
    void gettersReturnValues() {
        final ChangePoint cp =
                new ChangePoint(
                        ART,
                        ChangePointKind
                                .METHOD_BODY_CHANGED,
                        "com/Foo",
                        "bar",
                        "(I)V",
                        "aaa",
                        "bbb");
        assertThat(cp.getArtifact())
                .isEqualTo(ART);
        assertThat(cp.getKind())
                .isEqualTo(
                        ChangePointKind
                                .METHOD_BODY_CHANGED);
        assertThat(cp.getOwner())
                .isEqualTo("com/Foo");
        assertThat(cp.getName())
                .isEqualTo("bar");
        assertThat(cp.getDescriptor())
                .isEqualTo("(I)V");
        assertThat(cp.getOldHash())
                .isEqualTo("aaa");
        assertThat(cp.getNewHash())
                .isEqualTo("bbb");
        assertThat(cp.getAccessTransition()).isEmpty();
    }

    @Test
    void classLevelHasNullNameAndDesc() {
        final ChangePoint cp =
                new ChangePoint(
                        ART,
                        ChangePointKind
                                .CLASS_ADDED,
                        "com/Foo",
                        null, null,
                        null, null);
        assertThat(cp.getName()).isNull();
        assertThat(cp.getDescriptor())
                .isNull();
        assertThat(cp.getOldHash())
                .isNull();
        assertThat(cp.getNewHash())
                .isNull();
    }

    @Test
    void equalsAndHashCode() {
        final ChangePoint cp1 =
                new ChangePoint(
                        ART,
                        ChangePointKind
                                .CLASS_ADDED,
                        "com/Foo",
                        null, null,
                        null, null);
        final ChangePoint cp2 =
                new ChangePoint(
                        ART,
                        ChangePointKind
                                .CLASS_ADDED,
                        "com/Foo",
                        null, null,
                        null, null);
        assertThat(cp1).isEqualTo(cp2);
        assertThat(cp1.hashCode())
                .isEqualTo(cp2.hashCode());
    }

    @Test
    void notEqualForDifferentKind() {
        final ChangePoint cp1 =
                new ChangePoint(
                        ART,
                        ChangePointKind
                                .CLASS_ADDED,
                        "com/Foo",
                        null, null,
                        null, null);
        final ChangePoint cp2 =
                new ChangePoint(
                        ART,
                        ChangePointKind
                                .CLASS_REMOVED,
                        "com/Foo",
                        null, null,
                        null, null);
        assertThat(cp1)
                .isNotEqualTo(cp2);
    }

    @Test
    void accessFactoryExposesTransitionAndExactIdentity() {
        final AccessTransition transition = new AccessTransition(
                JvmAccess.PUBLIC, JvmAccess.PROTECTED);
        final ChangePoint cp = ChangePoint.accessNarrowed(
                ART,
                ChangePointKind.METHOD_ACCESS_NARROWED,
                "com/Foo", "bar", "(I)V", transition);

        assertThat(cp.getKind()).isEqualTo(
                ChangePointKind.METHOD_ACCESS_NARROWED);
        assertThat(cp.getOwner()).isEqualTo("com/Foo");
        assertThat(cp.getName()).isEqualTo("bar");
        assertThat(cp.getOldDescriptor()).isEqualTo("(I)V");
        assertThat(cp.getNewDescriptor()).isEqualTo("(I)V");
        assertThat(cp.getOldHash()).isNull();
        assertThat(cp.getNewHash()).isNull();
        assertThat(cp.getAccessTransition()).contains(transition);
    }

    @Test
    void accessKindRequiresTransition() {
        assertThatThrownBy(() -> new ChangePoint(
                ART,
                ChangePointKind.FIELD_ACCESS_NARROWED,
                "com/Foo", "value", "I", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must agree");
    }

    @Test
    void accessFactoryRejectsNonAccessKindAndNullTransition() {
        final AccessTransition transition = new AccessTransition(
                JvmAccess.PUBLIC, JvmAccess.PRIVATE);
        assertThatThrownBy(() -> ChangePoint.accessNarrowed(
                ART, ChangePointKind.METHOD_BODY_CHANGED,
                "com/Foo", "bar", "()V", transition))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("access narrowing");
        assertThatThrownBy(() -> ChangePoint.accessNarrowed(
                ART, ChangePointKind.CLASS_ACCESS_NARROWED,
                "com/Foo", null, null, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("transition");
    }

    @Test
    void equalityAndHashCodeIncludeAccessTransition() {
        final ChangePoint first = ChangePoint.accessNarrowed(
                ART, ChangePointKind.METHOD_ACCESS_NARROWED,
                "com/Foo", "bar", "()V",
                new AccessTransition(
                        JvmAccess.PUBLIC, JvmAccess.PROTECTED));
        final ChangePoint copy = ChangePoint.accessNarrowed(
                ART, ChangePointKind.METHOD_ACCESS_NARROWED,
                "com/Foo", "bar", "()V",
                new AccessTransition(
                        JvmAccess.PUBLIC, JvmAccess.PROTECTED));
        final ChangePoint different = ChangePoint.accessNarrowed(
                ART, ChangePointKind.METHOD_ACCESS_NARROWED,
                "com/Foo", "bar", "()V",
                new AccessTransition(
                        JvmAccess.PUBLIC, JvmAccess.PRIVATE));

        assertThat(first).isEqualTo(copy);
        assertThat(first.hashCode()).isEqualTo(copy.hashCode());
        assertThat(first).isNotEqualTo(different);
    }

    @Test
    void toStringContainsAccessTransition() {
        final ChangePoint cp = ChangePoint.accessNarrowed(
                ART, ChangePointKind.CLASS_ACCESS_NARROWED,
                "com/Foo", null, null,
                new AccessTransition(
                        JvmAccess.PUBLIC,
                        JvmAccess.PACKAGE_PRIVATE));

        assertThat(cp.toString())
                .contains("CLASS_ACCESS_NARROWED")
                .contains("PUBLIC")
                .contains("PACKAGE_PRIVATE");
    }

    @Test
    void toStringContainsFields() {
        final ChangePoint cp =
                new ChangePoint(
                        ART,
                        ChangePointKind
                                .CLASS_ADDED,
                        "com/Foo",
                        null, null,
                        null, null);
        final String str = cp.toString();
        assertThat(str)
                .contains("CLASS_ADDED");
        assertThat(str)
                .contains("com/Foo");
    }
}
