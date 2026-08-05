package io.github.dependencyanalysis.callgraph;

import java.util.Objects;

/** Logical ownership record for one binary class name. */
public final class ClassOwnership {

    /** Code origin. */
    private final CodeOrigin origin;

    /** Logical owner entry. */
    private final ClassSource source;

    /** Content digest. */
    private final String digest;

    /**
     * Creates a class ownership record.
     *
     * @param value code origin
     * @param classSource logical owner entry
     * @param contentDigest content digest
     */
    ClassOwnership(
            final CodeOrigin value,
            final ClassSource classSource,
            final String contentDigest) {
        origin = Objects.requireNonNull(value, "origin");
        source = Objects.requireNonNull(classSource, "source");
        digest = Objects.requireNonNull(contentDigest, "digest");
    }

    /** @return code origin */
    public CodeOrigin getOrigin() {
        return origin;
    }

    /** @return logical source entry */
    public ClassSource getSource() {
        return source;
    }

    /** @return content digest */
    String getDigest() {
        return digest;
    }
}
