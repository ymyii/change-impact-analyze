package io.github.dependencyanalysis.impact;

import java.util.Objects;

/**
 * Typed JVM access decision.
 *
 * @param decision access outcome
 * @param reason typed decision reason
 */
public record AccessCheckResult(
        AccessDecision decision,
        AccessDecisionReason reason) {

    /** Validates the decision. */
    public AccessCheckResult {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(reason, "reason");
    }
}
