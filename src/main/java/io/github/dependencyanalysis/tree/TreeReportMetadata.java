package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.preflight
        .PreflightReport;
import io.github.dependencyanalysis.runtime
        .MavenRuntimeDescriptor;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Immutable repository metadata retained by an incremental report. */
public final class TreeReportMetadata {

    /** Snapshot metadata. */
    private final RepositorySnapshot snapshot;

    /** Runtime metadata. */
    private final MavenRuntimeDescriptor runtime;

    /** Maven arguments. */
    private final List<String> mavenArguments;

    /** Scope filter. */
    private final Set<String> scopes;

    /** Command-level preflight report. */
    private final PreflightReport preflight;

    /** Path supplied by the user. */
    private final Path requestedPath;

    /** Analysis path relative to Git root. */
    private final Path analysisPath;

    /**
     * Creates metadata for repository-root analysis.
     *
     * @param repositorySnapshot snapshot
     * @param selectedRuntime Maven runtime
     * @param arguments Maven arguments
     * @param includedScopes scopes
     * @param preflightReport command preflight
     */
    public TreeReportMetadata(
            final RepositorySnapshot repositorySnapshot,
            final MavenRuntimeDescriptor selectedRuntime,
            final List<String> arguments,
            final Set<String> includedScopes,
            final PreflightReport preflightReport) {
        this(repositorySnapshot, selectedRuntime,
                arguments, includedScopes,
                preflightReport, repositorySnapshot
                        .getRepositoryRoot(), Path.of(""));
    }

    /**
     * Creates metadata for a bounded analysis path.
     *
     * @param repositorySnapshot snapshot
     * @param selectedRuntime Maven runtime
     * @param arguments Maven arguments
     * @param includedScopes scopes
     * @param preflightReport command preflight
     * @param inputPath user input path
     * @param relativeAnalysisPath analysis path relative to Git root
     */
    public TreeReportMetadata(
            final RepositorySnapshot repositorySnapshot,
            final MavenRuntimeDescriptor selectedRuntime,
            final List<String> arguments,
            final Set<String> includedScopes,
            final PreflightReport preflightReport,
            final Path inputPath,
            final Path relativeAnalysisPath) {
        snapshot = repositorySnapshot;
        runtime = selectedRuntime;
        mavenArguments = List.copyOf(arguments);
        final List<String> orderedScopes =
                includedScopes.stream().sorted().toList();
        scopes = Collections.unmodifiableSet(
                new LinkedHashSet<>(orderedScopes));
        preflight = preflightReport;
        requestedPath = inputPath.toAbsolutePath()
                .normalize();
        analysisPath = relativeAnalysisPath.normalize();
    }

    /**
     * Adapts the legacy aggregate result.
     *
     * @param result aggregate result
     * @return metadata
     */
    public static TreeReportMetadata from(
            final TreeRepositoryResult result) {
        return new TreeReportMetadata(
                result.getSnapshot(), result.getRuntime(),
                result.getMavenArguments(),
                result.getScopes(), result.getPreflight());
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

    /** @return command preflight */
    public PreflightReport getPreflight() {
        return preflight;
    }

    /** @return path supplied by the user */
    public Path getRequestedPath() {
        return requestedPath;
    }

    /** @return resolved Git root */
    public Path getRepositoryRoot() {
        return snapshot.getRepositoryRoot();
    }

    /** @return analysis path relative to Git root */
    public Path getAnalysisPath() {
        return analysisPath;
    }
}
