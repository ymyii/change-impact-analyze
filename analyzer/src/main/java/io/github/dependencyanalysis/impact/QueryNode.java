package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.classpath.CodeOrigin;
import io.github.dependencyanalysis.callgraph.model.MethodId;

/** Node used by read-only WALA and terminal-evidence traversal. */
public interface QueryNode {

    /** @return method identity */
    MethodId methodId();

    /** @return code origin */
    CodeOrigin origin();
}
