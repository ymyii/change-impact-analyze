package io.github.dependencyanalysis.dependency;

/**
 * Maven dependency scope values that are
 * retained for analysis. Test scope is
 * excluded; system scope preserves its
 * explicit artifact path.
 */
public enum DependencyScope {

    /** Compile scope. */
    COMPILE("compile"),

    /** Runtime scope. */
    RUNTIME("runtime"),

    /** Provided scope. */
    PROVIDED("provided"),

    /** System scope. */
    SYSTEM("system");

    /** Scope string value. */
    private final String value;

    /**
     * Creates a scope enum constant.
     *
     * @param val scope string value
     */
    DependencyScope(final String val) {
        this.value = val;
    }

    /**
     * Returns the scope string value.
     *
     * @return scope string
     */
    public String getValue() {
        return value;
    }

    /**
     * Parses a scope string into a
     * DependencyScope. Returns null for
     * test or unknown scopes.
     *
     * @param str scope string
     * @return scope enum or null
     */
    public static DependencyScope
            fromString(final String str) {
        if (str == null) {
            return null;
        }
        final String lower =
                str.trim().toLowerCase();
        if ("compile".equals(lower)) {
            return COMPILE;
        }
        if ("runtime".equals(lower)) {
            return RUNTIME;
        }
        if ("provided".equals(lower)) {
            return PROVIDED;
        }
        if ("system".equals(lower)) {
            return SYSTEM;
        }
        return null;
    }
}
