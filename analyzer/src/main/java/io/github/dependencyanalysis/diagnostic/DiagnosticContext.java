package io.github.dependencyanalysis.diagnostic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable stage, substage, optional phase, and prefix identity.
 *
 * @param stage diagnostic stage
 * @param substage diagnostic substage
 * @param phase optional algorithm phase
 * @param attributes structured attributes
 */
public record DiagnosticContext(
        String stage,
        String substage,
        String phase,
        Map<String, String> attributes) {

    /** Stable-key field separator. */
    private static final String KEY_SEPARATOR = "\u001f";

    /** Normalizes nullable fields and takes an immutable attribute copy. */
    public DiagnosticContext {
        stage = normalize(stage);
        substage = normalize(substage);
        phase = normalize(phase);
        final Map<String, String> normalized = new LinkedHashMap<>();
        if (attributes != null) {
            attributes.forEach((key, value) -> {
                final String normalizedKey = normalize(key);
                final String normalizedValue = normalize(value);
                if (!normalizedKey.isBlank() && !normalizedValue.isBlank()) {
                    normalized.put(normalizedKey, normalizedValue);
                }
            });
        }
        attributes = Collections.unmodifiableMap(
                new LinkedHashMap<>(normalized));
    }

    /**
     * Creates a context without an algorithm phase.
     *
     * @param stageName diagnostic stage
     * @param substageName diagnostic substage
     * @param identityAttributes structured identity attributes
     */
    public DiagnosticContext(
            final String stageName,
            final String substageName,
            final Map<String, String> identityAttributes) {
        this(stageName, substageName, "", identityAttributes);
    }

    /**
     * Creates a stage-only context.
     *
     * @param stageName stage name
     * @return context
     */
    public static DiagnosticContext stage(final String stageName) {
        return new DiagnosticContext(stageName, "", "", Map.of());
    }

    /**
     * Creates a stage and substage context.
     *
     * @param stageName stage name
     * @param substageName substage name
     * @return context
     */
    public static DiagnosticContext of(
            final String stageName,
            final String substageName) {
        return new DiagnosticContext(stageName, substageName, Map.of());
    }

    /** @return copy with substage */
    public DiagnosticContext withSubstage(final String value) {
        return new DiagnosticContext(stage, value, phase, attributes);
    }

    /**
     * Selects an optional algorithm phase for one diagnostic event.
     *
     * <p>Phase is observational state. It is deliberately excluded from
     * {@link #stableKey()} so phase transitions cannot split one Stage
     * timer.</p>
     *
     * @param value phase name, or blank to clear it
     * @return copy with phase
     */
    public DiagnosticContext withPhase(final String value) {
        return new DiagnosticContext(stage, substage, value, attributes);
    }

    /**
     * Adds one prefix identity attribute.
     *
     * <p>Actual results, measurements, paths, and counts belong in the log
     * message rather than the prefix identity.</p>
     *
     * @param key identity key
     * @param value identity value
     * @return copy with one identity attribute
     */
    public DiagnosticContext with(
            final String key,
            final Object value) {
        final Map<String, String> copy = new LinkedHashMap<>(attributes);
        final String normalized = value == null ? "" : value.toString();
        if (normalized.isBlank()) {
            copy.remove(key);
        } else {
            copy.put(key, normalized);
        }
        return new DiagnosticContext(stage, substage, phase, copy);
    }

    /** @return copy with side attribute */
    public DiagnosticContext withSide(final String value) {
        return with("side", value);
    }

    /** @return copy with reactor attribute */
    public DiagnosticContext withReactor(final String value) {
        return with("reactor", value);
    }

    /** @return copy with module attribute */
    public DiagnosticContext withModule(final String value) {
        return with("module", value);
    }

    /** @return copy with artifact attribute */
    public DiagnosticContext withArtifact(final String value) {
        return with("artifact", value);
    }

    /** @return copy with path attribute */
    public DiagnosticContext withPath(final String value) {
        return with("path", value);
    }

    /**
     * Returns one normalized attribute.
     *
     * @param key attribute key
     * @return value or empty string
     */
    public String attribute(final String key) {
        return attributes.getOrDefault(key, "");
    }

    /**
     * Returns a collision-resistant timing key.
     *
     * @return stable key
     */
    public String stableKey() {
        final StringBuilder value = new StringBuilder(stage)
                .append(KEY_SEPARATOR).append(substage);
        attributes.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> value.append(KEY_SEPARATOR)
                        .append(entry.getKey()).append('=')
                        .append(entry.getValue()));
        return value.toString();
    }

    private static String normalize(final String value) {
        return Objects.requireNonNullElse(value, "");
    }
}
