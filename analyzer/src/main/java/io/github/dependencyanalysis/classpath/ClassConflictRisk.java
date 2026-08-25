package io.github.dependencyanalysis.classpath;

/** Risk of one duplicate binary class name under selected evidence. */
public enum ClassConflictRisk {

    /** Every compared candidate representation is identical. */
    LOW,

    /** At least two compared candidate representations are different. */
    HIGH
}
