package io.github.dependencyanalysis.runtime;

import java.nio.file.Path;

/** Target runtime provider for the impact JDK 8 contract. */
public final class Jdk8RuntimeProvider
        implements TargetJavaRuntimeProvider {

    @Override
    public JavaRuntimeDescriptor probe(final Path javaHome) {
        return new JavaRuntimeProbe().probeJdk8(javaHome);
    }
}
