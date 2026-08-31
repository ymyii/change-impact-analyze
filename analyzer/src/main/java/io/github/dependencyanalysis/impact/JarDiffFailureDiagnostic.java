package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;

import java.util.Objects;

/** Emits one isolated JAR diff failure with reusable report text. */
final class JarDiffFailureDiagnostic {

    /** Private constructor. */
    private JarDiffFailureDiagnostic() {
    }

    /**
     * Emits the complete failure and returns its stable report summary.
     *
     * @param diagnostics diagnostic destination
     * @param context JAR pair context
     * @param failure pair failure
     * @return exception type and complete message
     */
    static String emit(
            final DiagnosticLog diagnostics,
            final DiagnosticContext context,
            final Throwable failure) {
        Objects.requireNonNull(diagnostics, "diagnostics");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(failure, "failure");
        final String detail = Objects.requireNonNullElse(
                failure.getMessage(), "<no message>");
        final String summary = failure.getClass().getSimpleName()
                + ": " + detail;
        diagnostics.warnException(context,
                "JAR comparison failed: " + summary, failure);
        return summary;
    }
}
