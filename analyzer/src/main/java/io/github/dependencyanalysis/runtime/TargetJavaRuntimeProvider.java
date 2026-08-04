package io.github.dependencyanalysis.runtime;

import java.nio.file.Path;

/** Provider boundary for target Java runtime discovery. */
public interface TargetJavaRuntimeProvider {

    /**
     * Probes and validates a target JDK home.
     *
     * @param javaHome configured JDK home
     * @return immutable target runtime descriptor
     */
    JavaRuntimeDescriptor probe(Path javaHome);
}
