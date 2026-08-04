package io.github.dependencyanalysis.jar;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ChangeType;
import io.github.dependencyanalysis.dependency.DependencyChange;
import io.github.dependencyanalysis.dependency.DependencyScope;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

/**
 * Tests for {@link JarLocationResult}.
 */
class JarLocationResultTest {

    @Test
    void constructorAndGetters() {
        final ArtifactCoord old =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0");
        final ArtifactCoord nbr =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "2.0");
        final DependencyChange chg =
                new DependencyChange(
                        ChangeType
                                .VERSION_CHANGED,
                        old, nbr,
                        DependencyScope
                                .COMPILE,
                        "mod");
        final Path oldPath =
                Path.of("/tmp/old.jar");
        final Path newPath =
                Path.of("/tmp/new.jar");
        final JarLocationResult res =
                new JarLocationResult(
                        chg, oldPath,
                        newPath);
        assertThat(res.getChange())
                .isSameAs(chg);
        assertThat(res.getOldJar())
                .isEqualTo(oldPath);
        assertThat(res.getNewJar())
                .isEqualTo(newPath);
    }

    @Test
    void constructorThrowsOnNullChange() {
        assertThatThrownBy(() ->
                new JarLocationResult(
                        null,
                        Path.of("/o.jar"),
                        Path.of("/n.jar")))
                .isInstanceOf(
                        NullPointerException
                                .class);
    }

    @Test
    void constructorThrowsOnNullOldJar() {
        final ArtifactCoord old =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0");
        final ArtifactCoord nbr =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "2.0");
        final DependencyChange chg =
                new DependencyChange(
                        ChangeType
                                .VERSION_CHANGED,
                        old, nbr,
                        DependencyScope
                                .COMPILE,
                        "mod");
        assertThatThrownBy(() ->
                new JarLocationResult(
                        chg, null,
                        Path.of("/n.jar")))
                .isInstanceOf(
                        NullPointerException
                                .class);
    }

    @Test
    void constructorThrowsOnNullNewJar() {
        final ArtifactCoord old =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0");
        final ArtifactCoord nbr =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "2.0");
        final DependencyChange chg =
                new DependencyChange(
                        ChangeType
                                .VERSION_CHANGED,
                        old, nbr,
                        DependencyScope
                                .COMPILE,
                        "mod");
        assertThatThrownBy(() ->
                new JarLocationResult(
                        chg,
                        Path.of("/o.jar"),
                        null))
                .isInstanceOf(
                        NullPointerException
                                .class);
    }

    @Test
    void equalsAndHashCode() {
        final ArtifactCoord old =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0");
        final ArtifactCoord nbr =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "2.0");
        final DependencyChange chg =
                new DependencyChange(
                        ChangeType
                                .VERSION_CHANGED,
                        old, nbr,
                        DependencyScope
                                .COMPILE,
                        "mod");
        final Path op = Path.of("/o.jar");
        final Path np = Path.of("/n.jar");
        final JarLocationResult a =
                new JarLocationResult(
                        chg, op, np);
        final JarLocationResult b =
                new JarLocationResult(
                        chg, op, np);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode())
                .isEqualTo(b.hashCode());
    }

    @Test
    void toStringContainsFields() {
        final ArtifactCoord old =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0");
        final ArtifactCoord nbr =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "2.0");
        final DependencyChange chg =
                new DependencyChange(
                        ChangeType
                                .VERSION_CHANGED,
                        old, nbr,
                        DependencyScope
                                .COMPILE,
                        "mod");
        final Path op = Path.of("/o.jar");
        final Path np = Path.of("/n.jar");
        final JarLocationResult res =
                new JarLocationResult(
                        chg, op, np);
        final String str = res.toString();
        assertThat(str)
                .contains("change=");
        assertThat(str)
                .contains("oldJar=");
        assertThat(str)
                .contains("newJar=");
    }
}
