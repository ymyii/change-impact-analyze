package io.github.dependencyanalysis.runtime;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parsed Apache Maven semantic version. */
public final class MavenVersion
        implements Comparable<MavenVersion> {

    /** Supported Maven major. */
    private static final int MAVEN_THREE = 3;

    /** Minimum supported minor. */
    private static final int MINIMUM_MINOR = 6;

    /** Minimum supported patch. */
    private static final int MINIMUM_PATCH = 3;

    /** First unsupported Maven major. */
    private static final int MAVEN_FOUR = 4;

    /** Regex group for major component. */
    private static final int MAJOR_GROUP = 1;

    /** Regex group for minor component. */
    private static final int MINOR_GROUP = 2;

    /** Regex group for patch component. */
    private static final int PATCH_GROUP = 3;

    /** First semantic version in Maven output. */
    private static final Pattern VERSION = Pattern.compile(
            "(?m)(?:Apache Maven\\s+)?"
                    + "(\\d+)\\.(\\d+)\\.(\\d+)");

    /** Major component. */
    private final int major;

    /** Minor component. */
    private final int minor;

    /** Patch component. */
    private final int patch;

    private MavenVersion(
            final int majorValue,
            final int minorValue,
            final int patchValue) {
        major = majorValue;
        minor = minorValue;
        patch = patchValue;
    }

    /**
     * Parses Maven version output.
     *
     * @param text Maven version output
     * @return parsed version
     */
    public static MavenVersion parse(
            final String text) {
        Objects.requireNonNull(text, "text");
        final Matcher matcher = VERSION.matcher(text);
        if (!matcher.find()) {
            throw new IllegalArgumentException(
                    "Unable to parse Maven version: "
                            + text);
        }
        return new MavenVersion(
                Integer.parseInt(matcher.group(
                        MAJOR_GROUP)),
                Integer.parseInt(matcher.group(
                        MINOR_GROUP)),
                Integer.parseInt(matcher.group(
                        PATCH_GROUP)));
    }

    /**
     * Tests the supported product range.
     *
     * @return true for Maven 3.6.3 through 3.x
     */
    public boolean isSupported() {
        return compareTo(new MavenVersion(
                MAVEN_THREE, MINIMUM_MINOR,
                MINIMUM_PATCH)) >= 0
                && major < MAVEN_FOUR;
    }

    /** @return major component */
    public int getMajor() {
        return major;
    }

    /** @return minor component */
    public int getMinor() {
        return minor;
    }

    /** @return patch component */
    public int getPatch() {
        return patch;
    }

    @Override
    public int compareTo(
            final MavenVersion other) {
        int value = Integer.compare(major, other.major);
        if (value == 0) {
            value = Integer.compare(minor, other.minor);
        }
        if (value == 0) {
            value = Integer.compare(patch, other.patch);
        }
        return value;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof MavenVersion)) {
            return false;
        }
        final MavenVersion value =
                (MavenVersion) other;
        return major == value.major
                && minor == value.minor
                && patch == value.patch;
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
