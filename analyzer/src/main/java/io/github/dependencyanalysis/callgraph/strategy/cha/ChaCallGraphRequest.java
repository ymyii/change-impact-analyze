package io.github.dependencyanalysis.callgraph.strategy.cha;

import io.github.dependencyanalysis.callgraph.boundary.DependencyBodyBoundary;
import io.github.dependencyanalysis.classpath.ClassOwnershipIndex;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphStrategyRequest;

import java.util.Objects;

/**
 * Immutable CHA-only ownership, dispatch, and body-boundary policy.
 *
 * @param ownership classpath winner ownership
 * @param dispatchTargets changed-path dispatch policy
 * @param ancestorRetention retained external ancestor policy
 * @param dependencyBoundary external method-body boundary
 */
public record ChaCallGraphRequest(
        ClassOwnershipIndex ownership,
        ChaDispatchTargetPolicy dispatchTargets,
        ChaAncestorRetentionPolicy ancestorRetention,
        DependencyBodyBoundary dependencyBoundary)
        implements CallGraphStrategyRequest {

    /** Validates the CHA request. */
    public ChaCallGraphRequest {
        Objects.requireNonNull(ownership, "ownership");
        Objects.requireNonNull(dispatchTargets, "dispatchTargets");
        Objects.requireNonNull(ancestorRetention, "ancestorRetention");
        Objects.requireNonNull(dependencyBoundary, "dependencyBoundary");
    }
}
