package io.github.dependencyanalysis.callgraph.protocol.invokedynamic;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

// Wiki: wiki/features/call-graph-engine.md - invokedynamic model registry
/** Immutable exact-key invokedynamic bootstrap model registry. */
public final class InvokeDynamicBootstrapModelRegistry {

    /** Registered models. */
    private final Map<InvokeDynamicBootstrapKey,
            InvokeDynamicBootstrapModel> models;

    private InvokeDynamicBootstrapModelRegistry(
            final Map<InvokeDynamicBootstrapKey,
                    InvokeDynamicBootstrapModel> values) {
        models = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** @return registry containing the supported JDK 8 models */
    public static InvokeDynamicBootstrapModelRegistry jdk8Defaults() {
        return builder().register(
                AltMetafactoryBootstrapModel.KEY,
                new AltMetafactoryBootstrapModel()).build();
    }

    /** @return empty registry builder */
    public static Builder builder() {
        return new Builder();
    }

    /** @return builder initialized with every registered model */
    public Builder toBuilder() {
        return new Builder(models);
    }

    /**
     * Finds an exact bootstrap model.
     *
     * @param key bootstrap identity
     * @return registered model
     */
    public Optional<InvokeDynamicBootstrapModel> find(
            final InvokeDynamicBootstrapKey key) {
        return Optional.ofNullable(models.get(key));
    }

    /** Mutable construction boundary for an immutable registry. */
    public static final class Builder {

        /** Models under construction. */
        private final Map<InvokeDynamicBootstrapKey,
                InvokeDynamicBootstrapModel> values =
                new LinkedHashMap<>();

        private Builder() {
        }

        private Builder(final Map<InvokeDynamicBootstrapKey,
                InvokeDynamicBootstrapModel> initialValues) {
            values.putAll(initialValues);
        }

        /**
         * Registers one exact bootstrap protocol.
         *
         * @param key exact bootstrap key
         * @param model protocol model
         * @return this builder
         */
        public Builder register(
                final InvokeDynamicBootstrapKey key,
                final InvokeDynamicBootstrapModel model) {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(model, "model");
            if (values.putIfAbsent(key, model) != null) {
                throw new IllegalArgumentException(
                        "Duplicate invokedynamic bootstrap model: " + key);
            }
            return this;
        }

        /** @return immutable registry snapshot */
        public InvokeDynamicBootstrapModelRegistry build() {
            return new InvokeDynamicBootstrapModelRegistry(values);
        }
    }
}
