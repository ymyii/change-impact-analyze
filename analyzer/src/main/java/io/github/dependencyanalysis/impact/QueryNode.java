package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.CodeOrigin;
import io.github.dependencyanalysis.callgraph.MethodId;

/** Node used by read-only WALA and terminal-evidence traversal. */
public interface QueryNode {

    /** @return method identity */
    MethodId methodId();

    /** @return code origin */
    CodeOrigin origin();
}
