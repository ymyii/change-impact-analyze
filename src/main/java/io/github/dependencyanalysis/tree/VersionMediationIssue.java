package io.github.dependencyanalysis.tree;

import java.util.List;

/** Dependency with at least two requested versions in one module. */
public final class VersionMediationIssue {

    /** Dependency key. */
    private final DependencyKey key;

    /** Final selected version. */
    private final String selectedVersion;

    /** Final selected scope. */
    private final String selectedScope;

    /** All distinct paths. */
    private final List<VersionPath> paths;

    /**
     * Creates an issue.
     *
     * @param dependencyKey key
     * @param version selected version
     * @param scope selected scope
     * @param dependencyPaths paths
     */
    public VersionMediationIssue(
            final DependencyKey dependencyKey,
            final String version,
            final String scope,
            final List<VersionPath> dependencyPaths) {
        key = dependencyKey;
        selectedVersion = version;
        selectedScope = scope;
        paths = List.copyOf(dependencyPaths);
    }

    /** @return key */
    public DependencyKey getKey() {
        return key;
    }

    /** @return selected version */
    public String getSelectedVersion() {
        return selectedVersion;
    }

    /** @return selected scope */
    public String getSelectedScope() {
        return selectedScope;
    }

    /** @return all distinct paths */
    public List<VersionPath> getPaths() {
        return paths;
    }
}
