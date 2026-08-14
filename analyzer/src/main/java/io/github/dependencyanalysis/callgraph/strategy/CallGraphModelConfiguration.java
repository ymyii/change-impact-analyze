package io.github.dependencyanalysis.callgraph.strategy;

import io.github.dependencyanalysis.callgraph.jdk.JdkModelSelection;
import io.github.dependencyanalysis.callgraph.protocol.invokedynamic.InvokeDynamicBootstrapModelRegistry;
import java.util.Objects;

/**
 * Immutable command-wide Call Graph model configuration.
 *
 * @param jdkModel JDK Method Model selection
 * @param dynamicModels exact invokedynamic model registry
 */
public record CallGraphModelConfiguration(
        JdkModelSelection jdkModel,
        InvokeDynamicBootstrapModelRegistry dynamicModels) {

    /** Validates model configuration. */
    public CallGraphModelConfiguration {
        Objects.requireNonNull(jdkModel, "jdkModel");
        Objects.requireNonNull(dynamicModels, "dynamicModels");
    }

    /** @return default JDK and invokedynamic model configuration */
    public static CallGraphModelConfiguration defaults() {
        return new CallGraphModelConfiguration(
                JdkModelSelection.defaultSelection(),
                InvokeDynamicBootstrapModelRegistry.jdk8Defaults());
    }

    /**
     * Creates configuration with default invokedynamic models.
     *
     * @param selection JDK Method Model selection
     * @return complete model configuration
     */
    public static CallGraphModelConfiguration withJdkModel(
            final JdkModelSelection selection) {
        return new CallGraphModelConfiguration(selection,
                InvokeDynamicBootstrapModelRegistry.jdk8Defaults());
    }
}
