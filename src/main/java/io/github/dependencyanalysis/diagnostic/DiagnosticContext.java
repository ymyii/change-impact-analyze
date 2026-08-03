package io.github.dependencyanalysis.diagnostic;

import java.util.Objects;

/**
 * Immutable diagnostic task context used by concurrent analysis stages.
 *
 * @param stage stage name
 * @param task task name
 * @param side repository side
 * @param module module identifier
 * @param artifact artifact identifier
 * @param path relevant path
 */
public record DiagnosticContext(
        String stage,
        String task,
        String side,
        String module,
        String artifact,
        String path) {

    /** Stable-key field separator. */
    private static final String KEY_SEPARATOR = "\u001f";

    /**
     * Normalizes nullable optional fields.
     */
    public DiagnosticContext {
        if (stage == null || stage.isBlank()) {
            throw new IllegalArgumentException(
                    "Diagnostic stage must not be blank");
        }
        task = normalize(task);
        side = normalize(side);
        module = normalize(module);
        artifact = normalize(artifact);
        path = normalize(path);
    }

    /**
     * Creates a stage-only context.
     *
     * @param stage stage name
     * @return context
     */
    public static DiagnosticContext stage(
            final String stage) {
        return new DiagnosticContext(stage,
                "", "", "", "", "");
    }

    /**
     * Creates a stage and task context.
     *
     * @param stage stage name
     * @param task task name
     * @return context
     */
    public static DiagnosticContext task(
            final String stage, final String task) {
        return stage(stage).withTask(task);
    }

    /** @return copy with task */
    public DiagnosticContext withTask(
            final String value) {
        return copy(value, side, module,
                artifact, path);
    }

    /** @return copy with side */
    public DiagnosticContext withSide(
            final String value) {
        return copy(task, value, module,
                artifact, path);
    }

    /** @return copy with module */
    public DiagnosticContext withModule(
            final String value) {
        return copy(task, side, value,
                artifact, path);
    }

    /** @return copy with artifact */
    public DiagnosticContext withArtifact(
            final String value) {
        return copy(task, side, module,
                value, path);
    }

    /** @return copy with path */
    public DiagnosticContext withPath(
            final String value) {
        return copy(task, side, module,
                artifact, value);
    }

    /**
     * Returns a collision-resistant timing key.
     *
     * @return stable key
     */
    public String stableKey() {
        return String.join(KEY_SEPARATOR,
                stage, task, side, module,
                artifact, path);
    }

    private DiagnosticContext copy(
            final String newTask,
            final String newSide,
            final String newModule,
            final String newArtifact,
            final String newPath) {
        return new DiagnosticContext(stage,
                newTask, newSide, newModule,
                newArtifact, newPath);
    }

    private static String normalize(
            final String value) {
        return Objects.requireNonNullElse(value,
                "");
    }
}
