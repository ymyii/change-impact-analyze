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

    /** At least one physical JAR pair could not be diffed. */
    INCONCLUSIVE_BYTECODE_DIFF,

    /** ServiceLoader metadata could not be resolved completely. */
    INCONCLUSIVE_SERVICE_LOADER,

    /** At least one candidate method returned SSA UNKNOWN. */
    INCONCLUSIVE_SSA_UNKNOWN,

    /** External dependencies reference excluded JDK classes. */
    INCONCLUSIVE_SCOPE_VALIDATION,

    /** Scope validation rejected a classpath boundary. */
    FAILED_SCOPE_VALIDATION,

    /** Call Graph construction exceeded its module timeout. */
    FAILED_CALL_GRAPH_TIMEOUT,

    /** Module analysis failed for another reason. */
    FAILED_ANALYSIS
}
