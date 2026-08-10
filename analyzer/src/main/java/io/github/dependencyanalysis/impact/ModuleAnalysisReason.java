package io.github.dependencyanalysis.impact;

/** Structured reason attached to a module status. */
public enum ModuleAnalysisReason {

    /** No exceptional reason. */
    NONE,

    /** Module exists only on the baseline side. */
    SKIPPED_NOT_IN_TARGET,

    /** Module exists only on the target side. */
    SKIPPED_NO_BASELINE,

    /** Module has no relevant dependency ChangePoint. */
    SKIPPED_NO_RELEVANT_CHANGE,

    /** User entrypoint selectors matched no executable PROJECT method. */
    SKIPPED_USER_ENTRYPOINT_SCOPE,

    /** At least one logical coordinate pair could not be diffed. */
    INCONCLUSIVE_BYTECODE_DIFF,

    /** ServiceLoader metadata could not be resolved completely. */
    INCONCLUSIVE_SERVICE_LOADER,

    /** Reachable invokedynamic bootstrap could not be modeled. */
    INCONCLUSIVE_INVOKEDYNAMIC_MODEL,

    /** Reachable MethodHandle target could not be resolved locally. */
    INCONCLUSIVE_METHOD_HANDLE_MODEL,

    /** At least one candidate method returned SSA UNKNOWN. */
    INCONCLUSIVE_SSA_UNKNOWN,

    /** External dependencies reference excluded JDK classes. */
    INCONCLUSIVE_SCOPE_VALIDATION,

    /** A changed instance crossed into, or was materialized by, a no-op JAR. */
    INCONCLUSIVE_DEPENDENCY_BODY_BOUNDARY,

    /** Scope validation rejected a classpath boundary. */
    FAILED_SCOPE_VALIDATION,

    /** Call Graph construction exceeded its module timeout. */
    FAILED_CALL_GRAPH_TIMEOUT,

    /** Module analysis failed for another reason. */
    FAILED_ANALYSIS
}
