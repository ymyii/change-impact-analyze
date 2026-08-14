package io.github.dependencyanalysis.callgraph.jdk;

import com.ibm.wala.util.config.StringFilter;

import java.util.List;
import java.util.regex.Pattern;

/** Minimal JDK class exclusions for the Spring backend target. */
public final class SpringBackendJdkExclusions implements StringFilter {

    /** Serialization version. */
    private static final long serialVersionUID = 1L;

    /** Excluded internal-name patterns. */
    private static final List<Pattern> PATTERNS = List.of(
            Pattern.compile("^javax/swing/.*"),
            Pattern.compile("^sun/swing/.*"),
            Pattern.compile("^com/sun/java/swing/.*"),
            Pattern.compile("^java/applet/.*"),
            Pattern.compile("^sun/applet/.*"));

    /** Excluded whole JDK JAR basenames. */
    private static final List<String> JAR_NAMES = List.of(
            "jfxrt.jar", "deploy.jar", "javaws.jar", "plugin.jar");

    @Override
    public boolean test(final String value) {
        final String normalized = value.startsWith("L")
                ? value.substring(1) : value;
        return PATTERNS.stream().anyMatch(
                pattern -> pattern.matcher(normalized).matches());
    }

    @Override
    public Object toJson() {
        return PATTERNS.stream().map(Pattern::pattern).toList();
    }

    /**
     * Checks whether an entire JDK JAR is excluded.
     *
     * @param path JDK classpath entry
     * @return true when the basename is excluded
     */
    public boolean excludesJar(final java.nio.file.Path path) {
        return JAR_NAMES.contains(path.getFileName().toString());
    }
}
