package io.github.dependencyanalysis.tree;

import java.util.List;

/** Dependency resolved differently with complete cross-module evidence. */
public final class CrossModuleVersionIssue {

    /** Key. */
    private final DependencyKey key;

    /** Module aggregates. */
    private final List<CrossModuleVersion> versions;

    /**
     * Creates an issue.
     *
     * @param dependencyKey key
     * @param resolvedVersions module aggregates
     */
    public CrossModuleVersionIssue(
            final DependencyKey dependencyKey,
            final List<CrossModuleVersion>
                    resolvedVersions) {
        key = dependencyKey;
        versions = List.copyOf(resolvedVersions);
    }

    /** @return dependency key */
    public DependencyKey getKey() {
        return key;
    }

    /** @return module aggregates */
    public List<CrossModuleVersion> getVersions() {
        return versions;
    }
}
