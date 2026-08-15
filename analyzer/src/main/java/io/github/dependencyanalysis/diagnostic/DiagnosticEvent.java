package io.github.dependencyanalysis.diagnostic;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

// Wiki: wiki/architecture/dependency-analysis-pipelines.md - diagnostics model
/** Immutable diagnostic event. */
public final class DiagnosticEvent {

    /** Event wall-clock timestamp. */
    private final OffsetDateTime timestamp;

    /** Structured context. */
    private final DiagnosticContext context;

    /** Severity level. */
    private final DiagnosticLevel level;

    /** Single-line message text. */
    private final String message;

    /** Stage elapsed milliseconds, kept outside prefix identity. */
    private final long elapsedMillis;

    DiagnosticEvent(
            final OffsetDateTime eventTimestamp,
            final DiagnosticContext eventContext,
            final DiagnosticLevel eventLevel,
            final String eventMessage,
            final long eventElapsedMillis) {
        timestamp = Objects.requireNonNull(eventTimestamp, "timestamp");
        context = Objects.requireNonNull(eventContext, "context");
        level = Objects.requireNonNull(eventLevel, "level");
        message = Objects.requireNonNullElse(eventMessage, "");
        elapsedMillis = Math.max(0L, eventElapsedMillis);
    }

    /** @return event timestamp */
    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    /** @return complete context */
    public DiagnosticContext getContext() {
        return context;
    }

    /** @return stage name */
    public String getStage() {
        return context.stage();
    }

    /** @return substage name */
    public String getSubstage() {
        return context.substage();
    }

    /** @return optional algorithm phase */
    public String getPhase() {
        return context.phase();
    }

    /** @return ordered context attributes */
    public Map<String, String> getAttributes() {
        return context.attributes();
    }

    /** @return severity level */
    public DiagnosticLevel getLevel() {
        return level;
    }

    /** @return message */
    public String getMessage() {
        return message;
    }

    /** @return side or empty string */
    public String getSide() {
        return context.attribute("side");
    }

    /** @return module or empty string */
    public String getModule() {
        return context.attribute("module");
    }

    /** @return artifact or empty string */
    public String getArtifact() {
        return context.attribute("artifact");
    }

    /** @return path or empty string */
    public String getPath() {
        return context.attribute("path");
    }

    /** @return elapsed milliseconds or zero */
    public long getElapsedMillis() {
        return elapsedMillis;
    }

    @Override
    public String toString() {
        return "DiagnosticEvent{"
                + "timestamp=" + timestamp
                + ", context=" + context
                + ", level=" + level
                + ", message='" + message + '\''
                + ", elapsedMillis=" + elapsedMillis
                + '}';
    }

    /** Builder retained for fixture and adapter construction. */
    public static final class Builder {

        /** Timestamp. */
        private OffsetDateTime timestamp = OffsetDateTime.now();

        /** Stage. */
        private String stage = "";

        /** Substage. */
        private String substage = "";

        /** Optional algorithm phase. */
        private String phase = "";

        /** Attributes. */
        private final Map<String, String> attributes = new LinkedHashMap<>();

        /** Level. */
        private DiagnosticLevel level = DiagnosticLevel.INFO;

        /** Message. */
        private String message = "";

        /** Stage elapsed milliseconds. */
        private long elapsedMillis;

        /**
         * Sets the timestamp.
         *
         * @param value timestamp
         * @return this builder
         */
        public Builder timestamp(final OffsetDateTime value) {
            timestamp = value;
            return this;
        }

        /**
         * Sets the stage.
         *
         * @param value stage
         * @return this builder
         */
        public Builder stage(final String value) {
            stage = value;
            return this;
        }

        /**
         * Sets the substage.
         *
         * @param value substage
         * @return this builder
         */
        public Builder substage(final String value) {
            substage = value;
            return this;
        }

        /**
         * Sets the optional algorithm phase.
         *
         * @param value phase
         * @return this builder
         */
        public Builder phase(final String value) {
            phase = value;
            return this;
        }

        /**
         * Adds one attribute.
         *
         * @param key attribute key
         * @param value attribute value
         * @return this builder
         */
        public Builder attribute(final String key, final Object value) {
            if (value != null) {
                attributes.put(key, value.toString());
            }
            return this;
        }

        /**
         * Sets the level.
         *
         * @param value level
         * @return this builder
         */
        public Builder level(final DiagnosticLevel value) {
            level = value;
            return this;
        }

        /**
         * Sets the message.
         *
         * @param value message
         * @return this builder
         */
        public Builder message(final String value) {
            message = value;
            return this;
        }

        /**
         * Adds the side attribute.
         *
         * @param value side
         * @return this builder
         */
        public Builder side(final String value) {
            return attribute("side", value);
        }

        /**
         * Adds the module attribute.
         *
         * @param value module
         * @return this builder
         */
        public Builder module(final String value) {
            return attribute("module", value);
        }

        /**
         * Adds the artifact attribute.
         *
         * @param value artifact
         * @return this builder
         */
        public Builder artifact(final String value) {
            return attribute("artifact", value);
        }

        /**
         * Adds the path attribute.
         *
         * @param value path
         * @return this builder
         */
        public Builder path(final String value) {
            return attribute("path", value);
        }

        /**
         * Sets elapsed time outside prefix identity.
         *
         * @param value elapsed milliseconds
         * @return this builder
         */
        public Builder elapsedMillis(final long value) {
            elapsedMillis = value;
            return this;
        }

        /** @return immutable event */
        public DiagnosticEvent build() {
            return new DiagnosticEvent(timestamp,
                    new DiagnosticContext(
                            stage, substage, phase, attributes),
                    level, message, elapsedMillis);
        }
    }
}
