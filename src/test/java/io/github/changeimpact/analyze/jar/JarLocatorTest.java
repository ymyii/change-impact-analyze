package io.github.changeimpact.analyze.jar;

import io.github.changeimpact.analyze.dependency.ArtifactCoord;
import io.github.changeimpact.analyze.dependency.ChangeType;
import io.github.changeimpact.analyze.dependency.DependencyChange;
import io.github.changeimpact.analyze.dependency.DependencyScope;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

/**
 * Tests for {@link JarLocator}.
 */
class JarLocatorTest {

    /** Temporary directory for test. */
    @TempDir
    private Path tempDir;

    @Test
    void locateVersionChangedBothExist()
            throws Exception {
        final ArtifactCoord oldArt =
                new ArtifactCoord(
                        "com.example",
                        "lib", "jar",
                        "1.0");
        final ArtifactCoord newArt =
                new ArtifactCoord(
                        "com.example",
                        "lib", "jar",
                        "2.0");
        createJarFile(tempDir, oldArt);
        createJarFile(tempDir, newArt);
        final DependencyChange change =
                new DependencyChange(
                        ChangeType
                                .VERSION_CHANGED,
                        oldArt, newArt,
                        DependencyScope
                                .COMPILE,
                        "mod");
        final JarLocator locator =
                new JarLocator(tempDir);
        final List<JarLocationResult> res =
                locator.locate(
                        List.of(change));
        assertThat(res).hasSize(1);
        assertThat(res.get(0).getOldJar())
                .exists();
        assertThat(res.get(0).getNewJar())
                .exists();
    }

    @Test
    void locateFiltersNonVersionChanged()
            throws Exception {
        final ArtifactCoord art =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0");
        final DependencyChange added =
                new DependencyChange(
                        ChangeType.ADDED,
                        null, art,
                        DependencyScope
                                .COMPILE,
                        "mod");
        final JarLocator locator =
                new JarLocator(tempDir);
        final List<JarLocationResult> res =
                locator.locate(
                        List.of(added));
        assertThat(res).isEmpty();
    }

    @Test
    void locateEmptyListReturnsEmpty()
            throws Exception {
        final JarLocator locator =
                new JarLocator(tempDir);
        final List<JarLocationResult> res =
                locator.locate(
                        Collections
                                .emptyList());
        assertThat(res).isEmpty();
    }

    @Test
    void locateThrowsWhenOldJarMissing()
            throws Exception {
        final ArtifactCoord oldArt =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0");
        final ArtifactCoord newArt =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "2.0");
        createJarFile(tempDir, newArt);
        final DependencyChange change =
                new DependencyChange(
                        ChangeType
                                .VERSION_CHANGED,
                        oldArt, newArt,
                        DependencyScope
                                .COMPILE,
                        "mod");
        final JarLocator locator =
                new JarLocator(tempDir);
        assertThatThrownBy(() ->
                locator.locate(
                        List.of(change)))
                .isInstanceOf(
                        JarLocatorException
                                .class)
                .satisfies(ex -> {
                    final JarLocatorException
                            jle =
                            (JarLocatorException)
                                    ex;
                    assertThat(jle.getSide())
                            .isEqualTo("old");
                    assertThat(jle
                            .getArtifactCoord())
                            .isEqualTo(
                                    oldArt
                                            .toString());
                    assertThat(jle
                            .getRepositoryRoot())
                            .isEqualTo(
                                    tempDir);
                });
    }

    @Test
    void locateThrowsWhenNewJarMissing()
            throws Exception {
        final ArtifactCoord oldArt =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0");
        final ArtifactCoord newArt =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "2.0");
        createJarFile(tempDir, oldArt);
        final DependencyChange change =
                new DependencyChange(
                        ChangeType
                                .VERSION_CHANGED,
                        oldArt, newArt,
                        DependencyScope
                                .COMPILE,
                        "mod");
        final JarLocator locator =
                new JarLocator(tempDir);
        assertThatThrownBy(() ->
                locator.locate(
                        List.of(change)))
                .isInstanceOf(
                        JarLocatorException
                                .class)
                .satisfies(ex -> {
                    final JarLocatorException
                            jle =
                            (JarLocatorException)
                                    ex;
                    assertThat(jle.getSide())
                            .isEqualTo("new");
                });
    }

    @Test
    void resolveJarPathNoClassifier() {
        final ArtifactCoord coord =
                new ArtifactCoord(
                        "com.example",
                        "lib", "jar",
                        "1.0");
        final JarLocator locator =
                new JarLocator(tempDir);
        final Path path =
                locator.resolveJarPath(coord);
        assertThat(path).isEqualTo(
                tempDir.resolve("com/example")
                        .resolve("lib")
                        .resolve("1.0")
                        .resolve(
                                "lib-1.0.jar"));
    }

    @Test
    void resolveJarPathWithClassifier() {
        final ArtifactCoord coord =
                new ArtifactCoord(
                        "com.example",
                        "lib", "jar",
                        "1.0", "sources");
        final JarLocator locator =
                new JarLocator(tempDir);
        final Path path =
                locator.resolveJarPath(coord);
        assertThat(path).isEqualTo(
                tempDir.resolve("com/example")
                        .resolve("lib")
                        .resolve("1.0")
                        .resolve("lib-1.0"
                                + "-sources"
                                + ".jar"));
    }

    @Test
    void resolveJarPathNestedGroupId() {
        final ArtifactCoord coord =
                new ArtifactCoord(
                        "org.deep.nested",
                        "lib", "jar",
                        "3.0");
        final JarLocator locator =
                new JarLocator(tempDir);
        final Path path =
                locator.resolveJarPath(coord);
        assertThat(path).isEqualTo(
                tempDir.resolve(
                        "org/deep/nested")
                        .resolve("lib")
                        .resolve("3.0")
                        .resolve(
                                "lib-3.0.jar"));
    }

    @Test
    void constructorThrowsOnNullPath() {
        assertThatThrownBy(() ->
                new JarLocator(null))
                .isInstanceOf(
                        NullPointerException
                                .class);
    }

    @Test
    void locateMultipleVersionChanges()
            throws Exception {
        final ArtifactCoord old1 =
                new ArtifactCoord(
                        "g", "a1", "jar",
                        "1.0");
        final ArtifactCoord new1 =
                new ArtifactCoord(
                        "g", "a1", "jar",
                        "2.0");
        final ArtifactCoord old2 =
                new ArtifactCoord(
                        "g", "a2", "jar",
                        "1.0");
        final ArtifactCoord new2 =
                new ArtifactCoord(
                        "g", "a2", "jar",
                        "2.0");
        createJarFile(tempDir, old1);
        createJarFile(tempDir, new1);
        createJarFile(tempDir, old2);
        createJarFile(tempDir, new2);
        final DependencyChange ch1 =
                new DependencyChange(
                        ChangeType
                                .VERSION_CHANGED,
                        old1, new1,
                        DependencyScope
                                .COMPILE,
                        "mod");
        final DependencyChange ch2 =
                new DependencyChange(
                        ChangeType
                                .VERSION_CHANGED,
                        old2, new2,
                        DependencyScope
                                .COMPILE,
                        "mod");
        final JarLocator locator =
                new JarLocator(tempDir);
        final List<JarLocationResult> res =
                locator.locate(
                        List.of(ch1, ch2));
        assertThat(res).hasSize(2);
    }

    private void createJarFile(
            final Path repo,
            final ArtifactCoord coord)
            throws IOException {
        final String groupPath =
                coord.getGroupId()
                        .replace('.', '/');
        final Path versionDir =
                repo.resolve(groupPath)
                        .resolve(coord
                                .getArtifactId())
                        .resolve(coord
                                .getVersion());
        Files.createDirectories(versionDir);
        final String base =
                coord.getArtifactId()
                        + "-"
                        + coord.getVersion();
        final String clfr =
                coord.getClassifier();
        final String namePart;
        if (clfr == null
                || clfr.isEmpty()) {
            namePart = base;
        } else {
            namePart = base + "-" + clfr;
        }
        final String fileName = namePart
                + "." + coord.getType();
        Files.createFile(
                versionDir.resolve(fileName));
    }
}
