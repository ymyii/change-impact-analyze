package io.github.dependencyanalysis.callgraph;

/**
 * Immutable behavior declaration for one Call Graph strategy.
 *
 * @param pointsToAnalysis whether the strategy builds points-to facts
 * @param jdkModelSupported whether JDK Method Models are supported
 * @param jdkBodiesTraversed whether JDK method bodies are traversed
 * @param callerLocalConstants whether bounded local constants are recovered
 * @param reflection reflection support level
 * @param serviceLoader ServiceLoader support level
 * @param methodHandle MethodHandle support level
 * @param invokeDynamic invokedynamic support level
 */
public record CallGraphStrategyCapabilities(
        boolean pointsToAnalysis,
        boolean jdkModelSupported,
        boolean jdkBodiesTraversed,
        boolean callerLocalConstants,
        SupportLevel reflection,
        SupportLevel serviceLoader,
        SupportLevel methodHandle,
        SupportLevel invokeDynamic) {

    /** Declared strategy support level. */
    public enum SupportLevel {
        /** Capability is not modeled. */
        NONE,
        /** Capability is modeled only within the documented local contract. */
        LOCAL,
        /** Capability is delegated to the strategy's propagation model. */
        PROPAGATION
    }

    /** @return capabilities shared by propagation strategies */
    static CallGraphStrategyCapabilities propagation() {
        return new CallGraphStrategyCapabilities(true, true, true, true,
                SupportLevel.PROPAGATION, SupportLevel.PROPAGATION,
                SupportLevel.PROPAGATION, SupportLevel.PROPAGATION);
    }

    /** @return capabilities of the bounded CHA strategy */
    static CallGraphStrategyCapabilities cha() {
        return new CallGraphStrategyCapabilities(false, false, false, true,
                SupportLevel.LOCAL, SupportLevel.LOCAL,
                SupportLevel.NONE, SupportLevel.LOCAL);
    }
}
