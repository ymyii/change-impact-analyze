package io.github.dependencyanalysis.impact;

/** User selection matched no analyzable changed dependency JAR. */
final class DependencySelectionException extends RuntimeException {

    /** @param message stable user-facing failure detail */
    DependencySelectionException(final String message) {
        super(message);
    }
}
