package io.github.dependencyanalysis.callgraph;

import java.nio.file.Path;
import java.util.Objects;

/** Physical ownership record for one binary class name. */
public final class ClassOwnership {

    /** Code origin. */
    private final CodeOrigin origin;

    /** Physical owner entry. */
    private final Path source;

    /** Content digest. */
    private final String digest;

    /**
     * Creates a class ownership record.
     *
     * @param value code origin
     * @param sourcePath physical owner entry
     * @param contentDigest content digest
     */
    ClassOwnership(
            final CodeOrigin value,
            final Path sourcePath,
            final String contentDigest) {
        origin = Objects.requireNonNull(value, "origin");
        source = Objects.requireNonNull(sourcePath, "source");
        digest = Objects.requireNonNull(contentDigest, "digest");
    }

    /** @return code origin */
    public CodeOrigin getOrigin() {
        return origin;
    }

    /** @return physical source entry */
    public Path getSource() {
        return source;
    }

    /** @return content digest */
    String getDigest() {
        return digest;
    }
}
