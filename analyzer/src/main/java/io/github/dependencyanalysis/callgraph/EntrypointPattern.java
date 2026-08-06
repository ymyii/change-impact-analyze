package io.github.dependencyanalysis.callgraph;

import java.util.Objects;
import java.util.regex.Pattern;

/** Slash-separated glob used to select PROJECT entrypoint classes. */
public final class EntrypointPattern {

    /** Recursive wildcard segment. */
    private static final String RECURSIVE = "**";

    /** Original CLI value. */
    private final String expression;

    /** Compiled internal class-name matcher. */
    private final Pattern pathPattern;

    private EntrypointPattern(
            final String value,
            final Pattern matcher) {
        expression = value;
        pathPattern = matcher;
    }

    /**
     * Parses a slash-separated internal class-name glob.
     *
     * @param value CLI expression
     * @return validated pattern
     */
    public static EntrypointPattern parse(final String value) {
        final String expression = Objects.requireNonNull(value, "value");
        validate(expression);
        return new EntrypointPattern(expression,
                Pattern.compile(pathRegex(expression)));
    }

    private static void validate(final String expression) {
        if (expression.isBlank()
                || expression.startsWith("/")
                || expression.endsWith("/")
                || expression.contains("//")
                || expression.contains(".")
                || expression.contains("\\")
                || expression.contains(":")) {
            throw invalid(expression);
        }
        final String[] segments = expression.split("/", -1);
        final boolean recursive = RECURSIVE.equals(
                segments[segments.length - 1]);
        for (int index = 0; index < segments.length; index++) {
            final String segment = segments[index];
            if (segment.isBlank()) {
                throw invalid(expression);
            }
            if (segment.contains(RECURSIVE)) {
                if (!RECURSIVE.equals(segment)
                        || index != segments.length - 1) {
                    throw invalid(expression);
                }
                continue;
            }
            final boolean classSegment = !recursive
                    && index == segments.length - 1;
            final String allowed = classSegment
                    ? "[A-Za-z0-9_$*?]+"
                    : "[A-Za-z0-9_*?]+";
            if (!segment.matches(allowed)) {
                throw invalid(expression);
            }
        }
    }

    private static IllegalArgumentException invalid(
            final String expression) {
        return new IllegalArgumentException(
                "Invalid entrypoint class-path selector: " + expression);
    }

    private static String pathRegex(final String expression) {
        final String[] segments = expression.split("/");
        final boolean recursive = RECURSIVE.equals(
                segments[segments.length - 1]);
        if (segments.length == 1 && recursive) {
            return "^[^/]+(?:/[^/]+)*$";
        }
        final StringBuilder result = new StringBuilder("^");
        final int ordinaryCount = recursive
                ? segments.length - 1 : segments.length;
        for (int index = 0; index < ordinaryCount; index++) {
            if (index > 0) {
                result.append('/');
            }
            appendSegment(result, segments[index]);
        }
        if (recursive) {
            result.append("(?:/[^/]+)+");
        }
        return result.append('$').toString();
    }

    private static void appendSegment(
            final StringBuilder result,
            final String segment) {
        int literalStart = 0;
        for (int index = 0; index < segment.length(); index++) {
            final char value = segment.charAt(index);
            if (value != '*' && value != '?') {
                continue;
            }
            result.append(Pattern.quote(
                    segment.substring(literalStart, index)));
            result.append(value == '*' ? "[^/]*" : "[^/]");
            literalStart = index + 1;
        }
        result.append(Pattern.quote(segment.substring(literalStart)));
    }

    /**
     * Tests an internal JVM class name.
     *
     * @param internalName internal JVM class name
     * @return true when the class name matches
     */
    public boolean matchesInternalName(final String internalName) {
        final String value = Objects.requireNonNull(
                internalName, "internalName");
        if (pathPattern.matcher(value).matches()) {
            return true;
        }
        return value.startsWith("L")
                && pathPattern.matcher(value.substring(1)).matches();
    }

    /** @return original validated expression */
    public String expression() {
        return expression;
    }

    @Override
    public String toString() {
        return expression;
    }
}
