package io.github.dependencyanalysis.callgraph;

/** Semantic source of one reachable dynamic-call reference. */
public enum DynamicReferenceKind {

    /** invokedynamic bootstrap implementation method. */
    BOOTSTRAP_IMPLEMENTATION_METHOD,

    /** CONSTANT_MethodHandle bootstrap argument. */
    BOOTSTRAP_ARGUMENT_METHOD_HANDLE,

    /** Target resolved by the local MethodHandle execution model. */
    DIRECT_MODELED_HANDLE_TARGET
}
