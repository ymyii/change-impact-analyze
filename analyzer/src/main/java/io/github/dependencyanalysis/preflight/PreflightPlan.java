package io.github.dependencyanalysis.preflight;

import java.util.List;
import java.util.Objects;

/** Immutable preflight check graph. */
public final class PreflightPlan {

    /** Checks in declaration order. */
    private final List<PreflightCheck> checks;

    /**
     * Creates a plan.
     *
     * @param planChecks checks
     */
    public PreflightPlan(
            final List<PreflightCheck> planChecks) {
        checks = List.copyOf(Objects.requireNonNull(
                planChecks, "planChecks"));
    }

    /** @return checks in declaration order */
    public List<PreflightCheck> getChecks() {
        return checks;
    }
}
