package io.github.dependencyanalysis.callgraph.strategy.kobj;

import io.github.dependencyanalysis.callgraph.boundary.DependencyBodyBoundary;
import io.github.dependencyanalysis.callgraph.jdk.JdkModelSelection;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphStrategyRequest;
import io.github.dependencyanalysis.callgraph.strategy.WalaReflectionOptions;

import java.util.Objects;

/**
 * Immutable k-object-only fixed-point configuration.
 *
 * @param depth receiver allocation-string depth
 * @param reflectionOptions WALA reflection policy
 * @param jdkModel selected JDK method model
 * @param dependencyBoundary external method-body boundary
 */
public record KObjCallGraphRequest(
        int depth,
        WalaReflectionOptions reflectionOptions,
        JdkModelSelection jdkModel,
        DependencyBodyBoundary dependencyBoundary)
        implements CallGraphStrategyRequest {

    /** Validates the experimental strategy request. */
    public KObjCallGraphRequest {
        depth = CallGraphAlgorithm.requireValidKObjDepth(depth);
        Objects.requireNonNull(reflectionOptions, "reflectionOptions");
        Objects.requireNonNull(jdkModel, "jdkModel");
        Objects.requireNonNull(dependencyBoundary, "dependencyBoundary");
    }
}
