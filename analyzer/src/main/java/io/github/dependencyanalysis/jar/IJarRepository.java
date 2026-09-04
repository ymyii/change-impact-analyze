package io.github.dependencyanalysis.jar;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.io.IOException;
import java.util.Set;

// Wiki: wiki/c4/components/dependency-analyzer-cli-artifact-repository.md - 租约
/** Immutable command-scoped Maven coordinate to JAR repository. */
public interface IJarRepository extends AutoCloseable {

    /** @return immutable coordinates available in this repository */
    Set<ArtifactCoord> coordinates();

    /**
     * Opens one independently closeable JAR lease.
     *
     * @param coordinate Maven artifact coordinate
     * @return open lease owned by the caller
     * @throws IOException missing, closed, or unreadable artifact
     */
    JarLease open(ArtifactCoord coordinate) throws IOException;

    /** Closes all leases that callers did not release. */
    @Override
    void close() throws IOException;
}
