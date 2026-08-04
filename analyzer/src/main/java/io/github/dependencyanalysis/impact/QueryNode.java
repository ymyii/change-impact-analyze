package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.CodeOrigin;
import io.github.dependencyanalysis.callgraph.MethodId;

/** Node used by direct WALA and conservative overlay traversal. */
public interface QueryNode {

    /** @return method identity */
    MethodId methodId();

    /** @return code origin */
    CodeOrigin origin();
}
