package io.github.dependencyanalysis.runtime;

/** Maven executable origin. */
public enum MavenRuntimeSource {
    /** Maven extracted from the application JAR. */
    EMBEDDED,

    /** Maven executable supplied by the user. */
    USER_CONFIGURED
}
