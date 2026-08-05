package io.github.dependencyanalysis.jar;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import java.io.IOException;
import java.util.jar.JarFile;

/** One repository-owned open JAR handle. */
public interface JarLease extends AutoCloseable {

    /** @return logical artifact coordinate */
    ArtifactCoord coordinate();

    /** @return open JAR; valid until this lease is closed */
    JarFile jarFile();

    /** Releases the JAR handle. */
    @Override
    void close() throws IOException;
}
