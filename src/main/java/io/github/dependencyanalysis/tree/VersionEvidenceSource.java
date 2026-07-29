package io.github.dependencyanalysis.tree;

/** Source that contributed a version to conflict analysis. */
public enum VersionEvidenceSource {

    /** Version requested by a dependency path declaration. */
    DEPENDENCY_PATH,

    /** Version actually applied by dependency management. */
    DEPENDENCY_MANAGEMENT
}
