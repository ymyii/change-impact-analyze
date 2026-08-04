package io.github.dependencyanalysis.callgraph;

import java.util.Objects;
import java.util.regex.Pattern;

/** Package/class glob used to select PROJECT entrypoint classes. */
public final class EntrypointPattern {

    /** Original CLI value. */
    private final String expression;

    /** Compiled package matcher. */
    private final Pattern packagePattern;

    /** Compiled simple binary class matcher. */
    private final Pattern classPattern;

    private EntrypointPattern(
            final String value,
            final Pattern packages,
            final Pattern classes) {
        expression = value;
        packagePattern = packages;
        classPattern = classes;
    }

    /**
     * Parses {@code package-pattern:class-pattern}.
     *
     * @param value CLI expression
     * @return validated pattern
     */
    public static EntrypointPattern parse(final String value) {
        final String normalized = Objects.requireNonNull(
                value, "value").trim();
        final int separator = normalized.indexOf(':');
        if (separator <= 0
                || separator != normalized.lastIndexOf(':')
                || separator == normalized.length() - 1) {
            throw new IllegalArgumentException(
                    "Entrypoint selector must use "
                            + "<package-pattern>:<class-pattern>: "
                            + normalized);
        }
        final String packageValue = normalized.substring(0, separator);
        final String classValue = normalized.substring(separator + 1);
        validatePackage(packageValue, normalized);
        validateClass(classValue, normalized);
        return new EntrypointPattern(normalized,
                Pattern.compile(packageRegex(packageValue)),
                Pattern.compile(classRegex(classValue)));
    }

    private static void validatePackage(
            final String value, final String expression) {
        if (value.isBlank() || value.startsWith(".")
                || value.endsWith(".") || value.contains("..")
                || value.contains("/") || value.contains("$")) {
            throw invalid(expression);
        }
        for (String segment : value.split("\\.", -1)) {
            if (segment.isBlank()) {
                throw invalid(expression);
            }
            if (segment.contains("**") && !"**".equals(segment)) {
                throw invalid(expression);
            }
            if (!segment.matches("[A-Za-z_][A-Za-z0-9_]*|\\*\\*")) {
                throw invalid(expression);
            }
        }
    }

    private static void validateClass(
            final String value, final String expression) {
        if (value.isBlank() || value.contains(".") || value.contains("/")
                || value.contains("**")
                || !value.matches("[A-Za-z0-9_$*]+")) {
            throw invalid(expression);
        }
    }

    private static IllegalArgumentException invalid(
            final String expression) {
        return new IllegalArgumentException(
                "Invalid entrypoint selector: " + expression);
    }

    private static String packageRegex(final String value) {
        final String[] segments = value.split("\\.");
        final StringBuilder result = new StringBuilder("^");
        for (int index = 0; index < segments.length; index++) {
            final String segment = segments[index];
            if ("**".equals(segment)) {
                if (index != segments.length - 1) {
                    throw invalid(value);
                }
                if (index == 0) {
                    result.append("(?:[^.]+(?:\\.|$))*");
                } else {
                    result.append("(?:\\.[^.]+)*");
                }
                continue;
            }
            if (index > 0) {
                result.append("\\.");
            }
            result.append(Pattern.quote(segment));
        }
        return result.append('$').toString();
    }

    private static String classRegex(final String value) {
        final StringBuilder result = new StringBuilder("^");
        int start = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == '*') {
                result.append(Pattern.quote(value.substring(start, index)))
                        .append(".*");
                start = index + 1;
            }
        }
        result.append(Pattern.quote(value.substring(start))).append('$');
        return result.toString();
    }

    /**
     * Tests an internal JVM class name.
     *
     * @param internalName internal JVM class name
     * @return true when the class name matches
     */
    public boolean matchesInternalName(final String internalName) {
        final String normalized = internalName.startsWith("L")
                ? internalName.substring(1) : internalName;
        final int separator = normalized.lastIndexOf('/');
        final String packageName = separator < 0 ? ""
                : normalized.substring(0, separator).replace('/', '.');
        final String className = separator < 0 ? normalized
                : normalized.substring(separator + 1);
        return packagePattern.matcher(packageName).matches()
                && classPattern.matcher(className).matches();
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
