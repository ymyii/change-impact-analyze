package io.github.changeimpact.analyze.bytecode;

import java.nio.file.Path;

/**
 * Thrown when a jar file is corrupt
 * or a class file cannot be parsed
 * during bytecode diff.
 */
public class BytecodeDiffException
        extends Exception {

    /** Serialization version. */
    private static final long
            SERIAL_VERSION = 1L;

    /** Path to the problematic jar. */
    private final Path jarPath;

    /** Class that failed (nullable). */
    private final String className;

    /**
     * Creates a new bytecode diff
     * exception.
     *
     * @param jar  path to jar file
     * @param cls  class name or null
     * @param msg  detail message
     * @param cause root cause
     */
    public BytecodeDiffException(
            final Path jar,
            final String cls,
            final String msg,
            final Throwable cause) {
        super(buildMessage(
                jar, cls, msg), cause);
        this.jarPath = jar;
        this.className = cls;
    }

    /**
     * Creates a new bytecode diff
     * exception without a cause.
     *
     * @param jar  path to jar file
     * @param cls  class name or null
     * @param msg  detail message
     */
    public BytecodeDiffException(
            final Path jar,
            final String cls,
            final String msg) {
        super(buildMessage(
                jar, cls, msg));
        this.jarPath = jar;
        this.className = cls;
    }

    /**
     * Returns the jar file path.
     *
     * @return jar path
     */
    public Path getJarPath() {
        return jarPath;
    }

    /**
     * Returns the class name that
     * failed to parse. May be null
     * if the jar itself is unreadable.
     *
     * @return class name or null
     */
    public String getClassName() {
        return className;
    }

    /**
     * Builds the detail message.
     *
     * @param jar jar path
     * @param cls class name
     * @param msg detail message
     * @return formatted message
     */
    private static String buildMessage(
            final Path jar,
            final String cls,
            final String msg) {
        final StringBuilder sb =
                new StringBuilder();
        sb.append("Bytecode diff error")
                .append(": jar=")
                .append(jar);
        if (cls != null) {
            sb.append(", class=")
                    .append(cls);
        }
        sb.append(", message=")
                .append(msg);
        return sb.toString();
    }
}
