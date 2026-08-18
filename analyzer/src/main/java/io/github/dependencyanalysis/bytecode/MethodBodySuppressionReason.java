package io.github.dependencyanalysis.bytecode;

/** Ordered-stage reasons that can suppress one method body ChangePoint. */
public enum MethodBodySuppressionReason {

    /** Normalized SSA comparison matched. */
    SSA_MATCHED,

    /** Decompiled Java text matched exactly. */
    JAVA_TEXT_IDENTICAL
}
