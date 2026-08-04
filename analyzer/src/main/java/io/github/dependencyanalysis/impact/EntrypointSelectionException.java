package io.github.dependencyanalysis.impact;

/** Command-level failure when no relevant module matches user entrypoints. */
final class EntrypointSelectionException extends RuntimeException {

    EntrypointSelectionException(final String message) {
        super(message);
    }
}
