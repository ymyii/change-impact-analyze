package io.github.dependencyanalysis.impact;

/** Effective interpretation of one external dependency method body. */
public enum DependencyMethodBodyPolicy {

    /** Execute the method's bytecode-derived IR. */
    REAL_IR,

    /** Execute a typed no-op summary. */
    NO_OP,

    /** Execute a call-site-specific allocation and return summary. */
    FLOW_TO_CAST_FACTORY
}
