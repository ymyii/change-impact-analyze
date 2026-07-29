package io.github.dependencyanalysis.tree;

import java.util.List;

/** Collected dependency results for one reactor. */
public final class ReactorTreeResult {

    /** Reactor inventory. */
    private final ReactorDescriptor reactor;

    /** Module results. */
    private final List<ModuleTreeResult> modules;

    /** Reactor status. */
    private final ReactorStatus status;

    /** Status reason. */
    private final String reason;

    /**
     * Creates a reactor result.
     *
     * @param descriptor reactor descriptor
     * @param moduleResults modules
     * @param resultStatus status
     * @param statusReason reason
     */
    public ReactorTreeResult(
            final ReactorDescriptor descriptor,
            final List<ModuleTreeResult>
                    moduleResults,
            final ReactorStatus resultStatus,
            final String statusReason) {
        reactor = descriptor;
        modules = List.copyOf(moduleResults);
        status = resultStatus;
        reason = statusReason;
    }

    /** @return reactor descriptor */
    public ReactorDescriptor getReactor() {
        return reactor;
    }

    /** @return module results */
    public List<ModuleTreeResult> getModules() {
        return modules;
    }

    /** @return reactor status */
    public ReactorStatus getStatus() {
        return status;
    }

    /** @return status reason */
    public String getReason() {
        return reason;
    }
}
