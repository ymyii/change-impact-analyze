package io.github.changeimpact.analyze.jar;

import io.github.changeimpact.analyze.dependency.ArtifactCoord;
import io.github.changeimpact.analyze.dependency.ChangeType;
import io.github.changeimpact.analyze.dependency.DependencyChange;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Wiki: wiki/features/jar-locator.md - Jar 定位主实现，分析流水线第六阶段
/**
 * Locates old and new jar files in the
 * local Maven repository for
 * version-changed dependencies.
 */
public final class JarLocator {

    /** Local Maven repository root. */
    private final Path localRepoPath;

    /**
     * Creates a locator with the default
     * Maven local repository at
     * ~/.m2/repository.
     */
    public JarLocator() {
        this(Path.of(
                System.getProperty(
                        "user.home"),
                ".m2",
                "repository"));
    }

    /**
     * Creates a locator with a custom
     * Maven local repository path.
     *
     * @param repoPath repository root
     */
    public JarLocator(
            final Path repoPath) {
        this.localRepoPath =
                Objects.requireNonNull(
                        repoPath,
                        "localRepoPath");
    }

    /**
     * Locates jar files for all
     * version-changed dependencies.
     * Non-VERSION_CHANGED entries are
     * filtered out.
     *
     * @param changes dependency changes
     * @return located jar results
     * @throws JarLocatorException
     *  if a jar file is missing
     */
    public List<JarLocationResult> locate(
            final List<DependencyChange>
                    changes)
            throws JarLocatorException {
        Objects.requireNonNull(
                changes, "changes");
        final List<JarLocationResult>
                results = new ArrayList<>();
        for (DependencyChange ch : changes) {
            if (ch.getChangeType()
                    != ChangeType
                    .VERSION_CHANGED) {
                continue;
            }
            final Path oldPath =
                    resolveJarPath(
                            ch.getOldArtifact());
            final Path newPath =
                    resolveJarPath(
                            ch.getNewArtifact());
            if (!Files.exists(oldPath)) {
                throw new JarLocatorException(
                        ch.getOldArtifact()
                                .toString(),
                        "old",
                        oldPath,
                        localRepoPath);
            }
            if (!Files.exists(newPath)) {
                throw new JarLocatorException(
                        ch.getNewArtifact()
                                .toString(),
                        "new",
                        newPath,
                        localRepoPath);
            }
            results.add(
                    new JarLocationResult(
                            ch, oldPath,
                            newPath));
        }
        return results;
    }

    /**
     * Resolves the expected jar file
     * path for an artifact coordinate.
     *
     * @param coord artifact coordinate
     * @return expected jar file path
     */
    Path resolveJarPath(
            final ArtifactCoord coord) {
        final String groupPath =
                coord.getGroupId()
                        .replace('.', '/');
        final Path versionDir =
                localRepoPath
                        .resolve(groupPath)
                        .resolve(coord
                                .getArtifactId())
                        .resolve(coord
                                .getVersion());
        final String fileName =
                buildFileName(coord);
        return versionDir.resolve(fileName);
    }

    private static String buildFileName(
            final ArtifactCoord coord) {
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
        return namePart + "."
                + coord.getType();
    }
}
