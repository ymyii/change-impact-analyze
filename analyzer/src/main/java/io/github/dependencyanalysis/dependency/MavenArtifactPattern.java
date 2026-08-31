package io.github.dependencyanalysis.dependency;

import java.util.Objects;
import java.util.regex.Pattern;

// - Algorithm and Refinement Contract
/** Immutable Glob pattern for one Maven groupId:artifactId source. */
public final class MavenArtifactPattern {

    /** Canonical user expression. */
    private final String expression;

    /** Group identifier matcher. */
    private final Pattern groupPattern;

    /** Artifact identifier matcher. */
    private final Pattern artifactPattern;

    private MavenArtifactPattern(
            final String value,
            final Pattern group,
            final Pattern artifact) {
        expression = value;
        groupPattern = group;
        artifactPattern = artifact;
    }

    /**
     * Parses a two-segment Maven artifact Glob.
     *
     * @param value groupPattern:artifactPattern expression
     * @return validated immutable pattern
     */
    public static MavenArtifactPattern parse(final String value) {
        Objects.requireNonNull(value, "value");
        final int separator = value.indexOf(':');
        if (value.isEmpty() || separator <= 0
                || separator != value.lastIndexOf(':')
                || separator == value.length() - 1
                || value.chars().anyMatch(Character::isWhitespace)) {
            throw new IllegalArgumentException(
                    "Dependency pattern must be groupPattern:artifactPattern "
                            + "without whitespace: " + value);
        }
        final String group = value.substring(0, separator);
        final String artifact = value.substring(separator + 1);
        return new MavenArtifactPattern(value,
                Pattern.compile(globRegex(group)),
                Pattern.compile(globRegex(artifact)));
    }

    private static String globRegex(final String value) {
        final StringBuilder result = new StringBuilder("^");
        for (int index = 0; index < value.length(); index++) {
            final char current = value.charAt(index);
            if (current == '*') {
                result.append(".*");
            } else if (current == '?') {
                result.append('.');
            } else {
                if ("\\.[]{}()+-^$|".indexOf(current) >= 0) {
                    result.append('\\');
                }
                result.append(current);
            }
        }
        return result.append('$').toString();
    }

    /**
     * @param artifact Maven artifact coordinate
     * @return whether groupId and artifactId match
     */
    public boolean matches(final ArtifactCoord artifact) {
        Objects.requireNonNull(artifact, "artifact");
        return groupPattern.matcher(artifact.getGroupId()).matches()
                && artifactPattern.matcher(
                artifact.getArtifactId()).matches();
    }

    /** @return canonical groupPattern:artifactPattern expression */
    public String expression() {
        return expression;
    }

    @Override
    public boolean equals(final Object value) {
        return value instanceof MavenArtifactPattern other
                && expression.equals(other.expression);
    }

    @Override
    public int hashCode() {
        return expression.hashCode();
    }

    @Override
    public String toString() {
        return expression;
    }
}
