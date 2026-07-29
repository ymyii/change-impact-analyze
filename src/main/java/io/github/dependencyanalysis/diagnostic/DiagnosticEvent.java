package io.github.dependencyanalysis.diagnostic;

// Wiki: wiki/architecture/dependency-analysis-pipelines.md - diagnostics model
/**
 * Immutable diagnostic event.
 */
public final class DiagnosticEvent {

    /** Stage name. */
    private final String stage;

    /** Severity level. */
    private final DiagnosticLevel level;

    /** Message text. */
    private final String message;

    /** Side identifier. */
    private final String side;

    /** Module identifier. */
    private final String module;

    /** Artifact identifier. */
    private final String artifact;

    /** Path reference. */
    private final String path;

    /** Elapsed time in milliseconds. */
    private final long elapsedMillis;

    private DiagnosticEvent(final Builder builder) {
        this.stage = builder.stage;
        this.level = builder.level;
        this.message = builder.message;
        this.side = builder.side;
        this.module = builder.module;
        this.artifact = builder.artifact;
        this.path = builder.path;
        this.elapsedMillis = builder.elapsedMillis;
    }

    /**
     * Returns the stage name.
     *
     * @return stage
     */
    public String getStage() {
        return stage;
    }

    /**
     * Returns the severity level.
     *
     * @return level
     */
    public DiagnosticLevel getLevel() {
        return level;
    }

    /**
     * Returns the message.
     *
     * @return message
     */
    public String getMessage() {
        return message;
    }

    /**
     * Returns the side.
     *
     * @return side
     */
    public String getSide() {
        return side;
    }

    /**
     * Returns the module.
     *
     * @return module
     */
    public String getModule() {
        return module;
    }

    /**
     * Returns the artifact.
     *
     * @return artifact
     */
    public String getArtifact() {
        return artifact;
    }

    /**
     * Returns the path.
     *
     * @return path
     */
    public String getPath() {
        return path;
    }

    /**
     * Returns elapsed time in millis.
     *
     * @return elapsed millis
     */
    public long getElapsedMillis() {
        return elapsedMillis;
    }

    @Override
    public String toString() {
        return "DiagnosticEvent{"
                + "stage='" + stage + '\''
                + ", level=" + level
                + ", message='" + message + '\''
                + ", side='" + side + '\''
                + ", module='" + module + '\''
                + ", artifact='" + artifact + '\''
                + ", path='" + path + '\''
                + ", elapsedMillis=" + elapsedMillis
                + '}';
    }

    /**
     * Builder for {@link DiagnosticEvent}.
     */
    public static final class Builder {

        /** Stage name. */
        private String stage;

        /** Severity level. */
        private DiagnosticLevel level;

        /** Message text. */
        private String message;

        /** Side identifier. */
        private String side;

        /** Module identifier. */
        private String module;

        /** Artifact identifier. */
        private String artifact;

        /** Path reference. */
        private String path;

        /** Elapsed time in milliseconds. */
        private long elapsedMillis;

        /**
         * Sets the stage.
         *
         * @param value stage name
         * @return this builder
         */
        public Builder stage(final String value) {
            this.stage = value;
            return this;
        }

        /**
         * Sets the level.
         *
         * @param value severity level
         * @return this builder
         */
        public Builder level(
                final DiagnosticLevel value) {
            this.level = value;
            return this;
        }

        /**
         * Sets the message.
         *
         * @param value message text
         * @return this builder
         */
        public Builder message(final String value) {
            this.message = value;
            return this;
        }

        /**
         * Sets the side.
         *
         * @param value side
         * @return this builder
         */
        public Builder side(final String value) {
            this.side = value;
            return this;
        }

        /**
         * Sets the module.
         *
         * @param value module
         * @return this builder
         */
        public Builder module(final String value) {
            this.module = value;
            return this;
        }

        /**
         * Sets the artifact.
         *
         * @param value artifact
         * @return this builder
         */
        public Builder artifact(final String value) {
            this.artifact = value;
            return this;
        }

        /**
         * Sets the path.
         *
         * @param value path
         * @return this builder
         */
        public Builder path(final String value) {
            this.path = value;
            return this;
        }

        /**
         * Sets elapsed millis.
         *
         * @param value elapsed time
         * @return this builder
         */
        public Builder elapsedMillis(
                final long value) {
            this.elapsedMillis = value;
            return this;
        }

        /**
         * Builds the event.
         *
         * @return new event
         */
        public DiagnosticEvent build() {
            return new DiagnosticEvent(this);
        }
    }
}
