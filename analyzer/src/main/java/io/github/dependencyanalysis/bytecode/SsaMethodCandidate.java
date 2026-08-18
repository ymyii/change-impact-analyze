package io.github.dependencyanalysis.bytecode;

/**
 * One method body change eligible for semantic comparison.
 *
 * @param changePoint method body ChangePoint
 * @param oldMajorVersion baseline class file major version
 * @param newMajorVersion target class file major version
 */
record SsaMethodCandidate(
        ChangePoint changePoint,
        int oldMajorVersion,
        int newMajorVersion) {
}
