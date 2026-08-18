package io.github.dependencyanalysis.bytecode;

/** Result of comparing normalized Vineflower method text. */
public enum DecompileComparisonStatus {

    /** Both sides are available and exactly equal. */
    IDENTICAL,

    /** Both sides are available and differ. */
    DIFFERENT,

    /** At least one side could not be decompiled. */
    UNKNOWN
}
