package io.github.dependencyanalysis.classpath;

/** Risk of one duplicate binary class name. */
public enum ClassConflictRisk {

    /** Every candidate has identical class bytes. */
    LOW,

    /** At least two candidates have different class bytes. */
    HIGH
}
