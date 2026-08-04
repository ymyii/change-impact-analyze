package io.github.dependencyanalysis.util;

/**
 * Result of a process whose output was streamed to the Console.
 *
 * @param exitCode process exit code
 * @param outputTail bounded combined output tail
 */
public record ProcessConsoleResult(
        int exitCode,
        String outputTail) {
}
