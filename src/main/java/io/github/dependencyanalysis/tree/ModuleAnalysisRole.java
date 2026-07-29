package io.github.dependencyanalysis.tree;

/** Explains why one module is included in bounded tree analysis. */
public enum ModuleAnalysisRole {

    /** Module directory was selected by the user path. */
    REQUESTED("--path selected module"),

    /** Reactor module is required by a requested module. */
    DEPENDENCY("reactor dependency of a REQUESTED module"),

    /** Module is included by explicit reactor-root scope. */
    REACTOR_ROOT_SCOPE(
            "included because the reactor root was REQUESTED");

    /** User-facing inclusion reason. */
    private final String inclusionReason;

    ModuleAnalysisRole(final String reason) {
        inclusionReason = reason;
    }

    /** @return user-facing inclusion reason */
    public String getInclusionReason() {
        return inclusionReason;
    }
}
