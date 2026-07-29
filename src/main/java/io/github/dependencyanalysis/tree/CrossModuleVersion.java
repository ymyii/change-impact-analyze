package io.github.dependencyanalysis.tree;

import java.util.List;

/** One module/version aggregate in cross-module analysis. */
public final class CrossModuleVersion {

    /** Module coordinate. */
    private final String module;

    /** Resolved version. */
    private final String version;

    /** Scope. */
    private final String scope;

    /** Selected occurrence count. */
    private final long count;

    /** Complete selected occurrence paths and version evidence. */
    private final List<VersionPath> paths;

    /**
     * Creates an aggregate.
     *
     * @param moduleCoordinate module coordinate
     * @param resolvedVersion version
     * @param effectiveScope scope
     * @param occurrenceCount count
     */
    public CrossModuleVersion(
            final String moduleCoordinate,
            final String resolvedVersion,
            final String effectiveScope,
            final long occurrenceCount) {
        this(moduleCoordinate, resolvedVersion,
                effectiveScope, occurrenceCount,
                List.of());
    }

    /**
     * Creates an aggregate with complete evidence.
     *
     * @param moduleCoordinate module coordinate
     * @param resolvedVersion version
     * @param effectiveScope scope
     * @param occurrenceCount count
     * @param versionPaths selected occurrence evidence
     */
    public CrossModuleVersion(
            final String moduleCoordinate,
            final String resolvedVersion,
            final String effectiveScope,
            final long occurrenceCount,
            final List<VersionPath> versionPaths) {
        module = moduleCoordinate;
        version = resolvedVersion;
        scope = effectiveScope;
        count = occurrenceCount;
        paths = List.copyOf(versionPaths);
    }

    /** @return module coordinate */
    public String getModule() {
        return module;
    }

    /** @return resolved version */
    public String getVersion() {
        return version;
    }

    /** @return scope */
    public String getScope() {
        return scope;
    }

    /** @return occurrence count */
    public long getCount() {
        return count;
    }

    /** @return complete selected occurrence evidence */
    public List<VersionPath> getPaths() {
        return paths;
    }
}
