package io.github.dependencyanalysis.callgraph.protocol.methodhandle;

import com.ibm.wala.shrike.shrikeCT.ClassConstants;

/** JVM CONSTANT_MethodHandle reference_kind. */
public enum MethodHandleReferenceKind {

    /** REF_getField. */
    REF_GET_FIELD,
    /** REF_getStatic. */
    REF_GET_STATIC,
    /** REF_putField. */
    REF_PUT_FIELD,
    /** REF_putStatic. */
    REF_PUT_STATIC,
    /** REF_invokeVirtual. */
    REF_INVOKE_VIRTUAL,
    /** REF_invokeStatic. */
    REF_INVOKE_STATIC,
    /** REF_invokeSpecial. */
    REF_INVOKE_SPECIAL,
    /** REF_newInvokeSpecial. */
    REF_NEW_INVOKE_SPECIAL,
    /** REF_invokeInterface. */
    REF_INVOKE_INTERFACE;

    /**
     * @param kind class-file reference_kind value
     * @return typed reference kind
     */
    public static MethodHandleReferenceKind fromClassFileKind(
            final int kind) {
        return switch (kind) {
            case ClassConstants.REF_getField -> REF_GET_FIELD;
            case ClassConstants.REF_getStatic -> REF_GET_STATIC;
            case ClassConstants.REF_putField -> REF_PUT_FIELD;
            case ClassConstants.REF_putStatic -> REF_PUT_STATIC;
            case ClassConstants.REF_invokeVirtual -> REF_INVOKE_VIRTUAL;
            case ClassConstants.REF_invokeStatic -> REF_INVOKE_STATIC;
            case ClassConstants.REF_invokeSpecial -> REF_INVOKE_SPECIAL;
            case ClassConstants.REF_newInvokeSpecial ->
                    REF_NEW_INVOKE_SPECIAL;
            case ClassConstants.REF_invokeInterface -> REF_INVOKE_INTERFACE;
            default -> throw new IllegalArgumentException(
                    "Unsupported MethodHandle reference kind: " + kind);
        };
    }

    /** @return true for field read/write references */
    public boolean isField() {
        return this == REF_GET_FIELD || this == REF_GET_STATIC
                || this == REF_PUT_FIELD || this == REF_PUT_STATIC;
    }

    /** @return true for static member references */
    public boolean isStatic() {
        return this == REF_GET_STATIC || this == REF_PUT_STATIC
                || this == REF_INVOKE_STATIC;
    }
}
