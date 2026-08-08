package io.github.dependencyanalysis.callgraph;

/** Fixed-point extension that reported model coverage evidence. */
public enum ModelKind {

    /** JVM invokedynamic bootstrap model. */
    INVOKEDYNAMIC,

    /** java.lang.invoke MethodHandle model. */
    METHOD_HANDLE,

    /** java.util.ServiceLoader model. */
    SERVICE_LOADER
}
