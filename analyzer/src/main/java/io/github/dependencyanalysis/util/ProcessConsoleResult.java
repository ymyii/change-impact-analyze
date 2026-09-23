package io.github.dependencyanalysis.util;

/**
 * Result of a process with streamed diagnostics.
 * @param exitCode process exit code
 * @param standardOutput stdout data, empty for logging commands
 */
public record ProcessConsoleResult(int exitCode, String standardOutput) {
}
