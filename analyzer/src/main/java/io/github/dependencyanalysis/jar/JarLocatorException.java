package io.github.dependencyanalysis.jar;

import java.nio.file.Path;

/**
 * Thrown when a jar file cannot be
 * located in the local Maven repository.
 */
public class JarLocatorException
        extends Exception {

    /** Serialization version. */
    private static final long
            SERIAL_VERSION = 1L;

    /** Artifact coordinate string. */
    private final String artifactCoord;

    /** Side that is missing. */
    private final String side;

    /** Expected jar file path. */
    private final Path expectedPath;

    /** Maven repository root. */
    private final Path repositoryRoot;

    /**
     * Creates a new jar locator
     * exception.
     *
     * @param coord artifact coord string
     * @param sd    side ("old" or "new")
     * @param exp   expected jar path
     * @param repo  repository root path
     */
    public JarLocatorException(
            final String coord,
            final String sd,
            final Path exp,
            final Path repo) {
        super(buildMessage(
                coord, sd, exp, repo));
        this.artifactCoord = coord;
        this.side = sd;
        this.expectedPath = exp;
        this.repositoryRoot = repo;
    }

    /**
     * Returns the artifact coordinate
     * string.
     *
     * @return artifact coord string
     */
    public String getArtifactCoord() {
        return artifactCoord;
    }

    /**
     * Returns the side that is missing.
     *
     * @return "old" or "new"
     */
    public String getSide() {
        return side;
    }

    /**
     * Returns the expected jar path.
     *
     * @return expected path
     */
    public Path getExpectedPath() {
        return expectedPath;
    }

    /**
     * Returns the repository root.
     *
     * @return repository root path
     */
    public Path getRepositoryRoot() {
        return repositoryRoot;
    }

    private static String buildMessage(
            final String coord,
            final String sd,
            final Path exp,
            final Path repo) {
        final StringBuilder sb =
                new StringBuilder();
        sb.append("Jar not found")
                .append(": artifact=")
                .append(coord)
                .append(", side=")
                .append(sd)
                .append(", expectedPath=")
                .append(exp)
                .append(", repositoryRoot=")
                .append(repo);
        return sb.toString();
    }
}
