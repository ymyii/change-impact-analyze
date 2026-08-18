package io.github.dependencyanalysis.classpath;

/** Origin of code loaded into one module analysis scope. */
public enum CodeOrigin {

    /** Classes declared by the module currently being analyzed. */
    PROJECT,

    /** Classes declared by another module in the reactor closure. */
    REACTOR_DEPENDENCY,

    /** Classes loaded from a resolved external dependency. */
    DEPENDENCY,

    /** Classes loaded from the selected JDK 8 runtime. */
    JDK,

    /** Synthetic model or terminal evidence node. */
    SYNTHETIC
}
