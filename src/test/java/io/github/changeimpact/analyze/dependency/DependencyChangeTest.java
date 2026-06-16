package io.github.changeimpact.analyze.dependency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

/**
 * Tests for {@link DependencyChange}.
 */
class DependencyChangeTest {

    /** Module string constant. */
    private static final String MOD =
            "g:m:jar:1.0";

    @Test
    void addedConstruction() {
        final ArtifactCoord art =
                art("g", "a", "1.0");
        final DependencyChange ch =
                new DependencyChange(
                        ChangeType.ADDED,
                        null,
                        art,
                        DependencyScope.COMPILE,
                        MOD);
        assertThat(ch.getChangeType())
                .isEqualTo(ChangeType.ADDED);
        assertThat(ch.getNewArtifact())
                .isEqualTo(art);
        assertThat(ch.getOldArtifact())
                .isNull();
        assertThat(ch.getScope())
                .isEqualTo(DependencyScope
                        .COMPILE);
        assertThat(ch.getModule())
                .isEqualTo(MOD);
    }

    @Test
    void removedConstruction() {
        final ArtifactCoord art =
                art("g", "a", "1.0");
        final DependencyChange ch =
                new DependencyChange(
                        ChangeType.REMOVED,
                        art,
                        null,
                        DependencyScope.RUNTIME,
                        MOD);
        assertThat(ch.getChangeType())
                .isEqualTo(ChangeType.REMOVED);
        assertThat(ch.getOldArtifact())
                .isEqualTo(art);
        assertThat(ch.getNewArtifact())
                .isNull();
    }

    @Test
    void versionChangedConstruction() {
        final ArtifactCoord old =
                art("g", "a", "1.0");
        final ArtifactCoord nw =
                art("g", "a", "2.0");
        final DependencyChange ch =
                new DependencyChange(
                        ChangeType
                                .VERSION_CHANGED,
                        old,
                        nw,
                        DependencyScope.COMPILE,
                        MOD);
        assertThat(ch.getChangeType())
                .isEqualTo(ChangeType
                        .VERSION_CHANGED);
        assertThat(ch.getOldArtifact())
                .isEqualTo(old);
        assertThat(ch.getNewArtifact())
                .isEqualTo(nw);
    }

    @Test
    void addedNullNewThrows() {
        assertThatThrownBy(
                () -> new DependencyChange(
                        ChangeType.ADDED,
                        null,
                        null,
                        DependencyScope.COMPILE,
                        MOD))
                .isInstanceOf(
                        IllegalArgumentException
                                .class);
    }

    @Test
    void addedNonNullOldThrows() {
        assertThatThrownBy(
                () -> new DependencyChange(
                        ChangeType.ADDED,
                        art("g", "a", "1.0"),
                        art("g", "a", "2.0"),
                        DependencyScope.COMPILE,
                        MOD))
                .isInstanceOf(
                        IllegalArgumentException
                                .class);
    }

    @Test
    void removedNullOldThrows() {
        assertThatThrownBy(
                () -> new DependencyChange(
                        ChangeType.REMOVED,
                        null,
                        null,
                        DependencyScope.COMPILE,
                        MOD))
                .isInstanceOf(
                        IllegalArgumentException
                                .class);
    }

    @Test
    void removedNonNullNewThrows() {
        assertThatThrownBy(
                () -> new DependencyChange(
                        ChangeType.REMOVED,
                        art("g", "a", "1.0"),
                        art("g", "a", "2.0"),
                        DependencyScope.COMPILE,
                        MOD))
                .isInstanceOf(
                        IllegalArgumentException
                                .class);
    }

    @Test
    void versionChangedNullOldThrows() {
        assertThatThrownBy(
                () -> new DependencyChange(
                        ChangeType
                                .VERSION_CHANGED,
                        null,
                        art("g", "a", "1.0"),
                        DependencyScope.COMPILE,
                        MOD))
                .isInstanceOf(
                        IllegalArgumentException
                                .class);
    }

    @Test
    void versionChangedNullNewThrows() {
        assertThatThrownBy(
                () -> new DependencyChange(
                        ChangeType
                                .VERSION_CHANGED,
                        art("g", "a", "1.0"),
                        null,
                        DependencyScope.COMPILE,
                        MOD))
                .isInstanceOf(
                        IllegalArgumentException
                                .class);
    }

    @Test
    void nullChangeTypeThrows() {
        assertThatThrownBy(
                () -> new DependencyChange(
                        null,
                        null,
                        art("g", "a", "1.0"),
                        DependencyScope.COMPILE,
                        MOD))
                .isInstanceOf(
                        NullPointerException
                                .class);
    }

    @Test
    void nullScopeThrows() {
        assertThatThrownBy(
                () -> new DependencyChange(
                        ChangeType.ADDED,
                        null,
                        art("g", "a", "1.0"),
                        null,
                        MOD))
                .isInstanceOf(
                        NullPointerException
                                .class);
    }

    @Test
    void nullModuleThrows() {
        assertThatThrownBy(
                () -> new DependencyChange(
                        ChangeType.ADDED,
                        null,
                        art("g", "a", "1.0"),
                        DependencyScope.COMPILE,
                        null))
                .isInstanceOf(
                        NullPointerException
                                .class);
    }

    @Test
    void providedIsApiRisk() {
        final DependencyChange ch =
                new DependencyChange(
                        ChangeType.ADDED,
                        null,
                        art("g", "a", "1.0"),
                        DependencyScope.PROVIDED,
                        MOD);
        assertThat(ch.isCompileTimeApiRisk())
                .isTrue();
    }

    @Test
    void compileIsNotApiRisk() {
        final DependencyChange ch =
                new DependencyChange(
                        ChangeType.ADDED,
                        null,
                        art("g", "a", "1.0"),
                        DependencyScope.COMPILE,
                        MOD);
        assertThat(ch.isCompileTimeApiRisk())
                .isFalse();
    }

    @Test
    void runtimeIsNotApiRisk() {
        final DependencyChange ch =
                new DependencyChange(
                        ChangeType.ADDED,
                        null,
                        art("g", "a", "1.0"),
                        DependencyScope.RUNTIME,
                        MOD);
        assertThat(ch.isCompileTimeApiRisk())
                .isFalse();
    }

    @Test
    void equalsAndHashCode() {
        final ArtifactCoord art =
                art("g", "a", "1.0");
        final DependencyChange a =
                new DependencyChange(
                        ChangeType.ADDED,
                        null,
                        art,
                        DependencyScope.COMPILE,
                        MOD);
        final DependencyChange b =
                new DependencyChange(
                        ChangeType.ADDED,
                        null,
                        art,
                        DependencyScope.COMPILE,
                        MOD);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode())
                .isEqualTo(b.hashCode());
    }

    @Test
    void notEqualDifferentType() {
        final ArtifactCoord art =
                art("g", "a", "1.0");
        final DependencyChange a =
                new DependencyChange(
                        ChangeType.ADDED,
                        null,
                        art,
                        DependencyScope.COMPILE,
                        MOD);
        final DependencyChange b =
                new DependencyChange(
                        ChangeType.REMOVED,
                        art,
                        null,
                        DependencyScope.COMPILE,
                        MOD);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void toStringContainsFields() {
        final ArtifactCoord art =
                art("g", "a", "1.0");
        final DependencyChange ch =
                new DependencyChange(
                        ChangeType.ADDED,
                        null,
                        art,
                        DependencyScope.COMPILE,
                        MOD);
        final String str = ch.toString();
        assertThat(str).contains("ADDED");
        assertThat(str).contains("g:a:jar:1.0");
        assertThat(str).contains(MOD);
    }

    /**
     * Helper to build an ArtifactCoord.
     *
     * @param g group id
     * @param a artifact id
     * @param v version
     * @return artifact coordinate
     */
    private static ArtifactCoord art(
            final String g,
            final String a,
            final String v) {
        return new ArtifactCoord(
                g, a, "jar", v);
    }
}
