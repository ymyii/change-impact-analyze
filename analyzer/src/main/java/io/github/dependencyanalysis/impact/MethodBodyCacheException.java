package io.github.dependencyanalysis.impact;

/** Fatal command-owned method body cache integrity failure. */
final class MethodBodyCacheException extends RuntimeException {

    MethodBodyCacheException(final String message) {
        super(message);
    }

    MethodBodyCacheException(
            final String message,
            final Throwable cause) {
        super(message, cause);
    }
}
