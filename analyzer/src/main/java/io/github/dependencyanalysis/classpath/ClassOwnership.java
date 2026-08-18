package io.github.dependencyanalysis.classpath;

import java.util.Objects;

/** Logical ownership record for one binary class name. */
public final class ClassOwnership {

    /** Code origin. */
    private final CodeOrigin origin;

    /** Logical owner entry. */
    private final ClassSource source;

    /** Content digest. */
    private final String digest;

    /** Exact effective class entry. */
    private final String entryName;

    /**
     * Creates a class ownership record.
     *
     * @param value code origin
     * @param classSource logical owner entry
     * @param contentDigest content digest
     * @param effectiveEntry exact directory-relative or JAR entry name
     */
    ClassOwnership(
            final CodeOrigin value,
            final ClassSource classSource,
            final String contentDigest,
            final String effectiveEntry) {
        origin = Objects.requireNonNull(value, "origin");
        source = Objects.requireNonNull(classSource, "source");
        digest = Objects.requireNonNull(contentDigest, "digest");
        entryName = Objects.requireNonNull(effectiveEntry, "entryName");
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
    public String getDigest() {
        return digest;
    }

    /** @return exact effective class entry */
    public String getEntryName() {
        return entryName;
    }
}
