package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.preflight
        .PreflightReport;
import io.github.dependencyanalysis.runtime
        .MavenRuntimeDescriptor;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Set;

/** Complete repository dependency tree domain result. */
public final class TreeRepositoryResult {

    /** Snapshot metadata. */
    private final RepositorySnapshot snapshot;

    /** Runtime metadata. */
    private final MavenRuntimeDescriptor runtime;

    /** Maven arguments. */
    private final List<String> mavenArguments;

    /** Scope filter. */
    private final Set<String> scopes;

    /** Canonical preflight result. */
    private final PreflightReport preflight;

    /** Reactors. */
    private final List<ReactorTreeResult> reactors;

    /**
     * Creates a repository result.
     *
     * @param repositorySnapshot snapshot
     * @param selectedRuntime Maven runtime
     * @param arguments Maven arguments
     * @param includedScopes scopes
     * @param preflightReport preflight
     * @param reactorResults reactors
     */
    public TreeRepositoryResult(
            final RepositorySnapshot repositorySnapshot,
            final MavenRuntimeDescriptor selectedRuntime,
            final List<String> arguments,
            final Set<String> includedScopes,
            final PreflightReport preflightReport,
            final List<ReactorTreeResult>
                    reactorResults) {
        snapshot = repositorySnapshot;
        runtime = selectedRuntime;
        mavenArguments = List.copyOf(arguments);
        final List<String> orderedScopes =
                includedScopes.stream().sorted().toList();
        scopes = Collections.unmodifiableSet(
                new LinkedHashSet<>(orderedScopes));
        preflight = preflightReport;
        reactors = List.copyOf(reactorResults);
    }

    /** @return snapshot */
    public RepositorySnapshot getSnapshot() {
        return snapshot;
    }

    /** @return runtime */
    public MavenRuntimeDescriptor getRuntime() {
        return runtime;
    }

    /** @return Maven arguments */
    public List<String> getMavenArguments() {
        return mavenArguments;
    }

    /** @return scopes */
    public Set<String> getScopes() {
        return scopes;
    }

    /** @return preflight */
    public PreflightReport getPreflight() {
        return preflight;
    }

    /** @return reactors */
    public List<ReactorTreeResult> getReactors() {
        return reactors;
    }
}
