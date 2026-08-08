package io.github.dependencyanalysis.impact;

/** Target-side JVM reference operation relevant to access control. */
public enum JvmReferenceKind {

    /** Class/type reference. */
    CLASS(false),

    /** Static method invocation. */
    METHOD_STATIC(false),

    /** Virtual or interface method invocation. */
    METHOD_INSTANCE(true),

    /** Constructor invocation. */
    CONSTRUCTOR(true),

    /** Static field read/write. */
    FIELD_STATIC(false),

    /** Instance field read/write. */
    FIELD_INSTANCE(true);

    /** Whether protected receiver restrictions apply. */
    private final boolean instance;

    JvmReferenceKind(final boolean value) {
        instance = value;
    }

    /** @return true for instance member references */
    public boolean isInstance() {
        return instance;
    }
}
