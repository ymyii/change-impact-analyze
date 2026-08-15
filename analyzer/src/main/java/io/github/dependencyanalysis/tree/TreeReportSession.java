package io.github.dependencyanalysis.tree;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

// Wiki: wiki/features/repository-dependency-tree-report.md - 增量 Report 生命周期
/** Incrementally publishes reactor pages and repository checkpoints. */
public final class TreeReportSession {

    /** Renderer implementation. */
    private final TreeReportRenderer renderer;

    /** Immutable run metadata. */
    private final TreeReportMetadata metadata;

    /** Report output directory. */
    private final Path output;

    /** Expected reactor count. */
    private final int totalReactors;

    /** Report cache root used for external conflict grouping, nullable. */
    private final Path groupingCache;

    /** Lightweight published reactor checkpoints. */
    private final List<ReactorReportSummary> summaries =
            new ArrayList<>();

    /** Current lifecycle state. */
    private TreeReportState state = TreeReportState.RUNNING;

    /** Terminal failure reason. */
    private String failureReason = "";

    /**
     * Creates an initialized session.
     *
     * @param reportRenderer renderer
     * @param reportMetadata metadata
     * @param outputDirectory output directory
     * @param reactorCount expected reactors
     * @param cacheRoot external grouping cache root, nullable
     */
    TreeReportSession(
            final TreeReportRenderer reportRenderer,
            final TreeReportMetadata reportMetadata,
            final Path outputDirectory,
            final int reactorCount,
            final Path cacheRoot) {
        renderer = reportRenderer;
        metadata = reportMetadata;
        output = outputDirectory;
        totalReactors = reactorCount;
        groupingCache = cacheRoot;
    }

    /**
     * Publishes one complete reactor page and then its index checkpoint.
     *
     * @param result complete reactor result
     * @throws IOException on filesystem failure
     */
    public void publish(final ReactorTreeResult result)
            throws IOException {
        requireRunning();
        if (summaries.size() >= totalReactors) {
            throw new IllegalStateException(
                    "All expected reactors are already published");
        }
        if (summaries.stream().anyMatch(item -> item
                .getId().equals(result.getReactor().getId()))) {
            throw new IllegalArgumentException(
                    "Reactor was already published: "
                            + result.getReactor().getId());
        }
        final ReactorReportSummary summary =
                renderer.publishReactor(metadata, output,
                        result, summaries, totalReactors, groupingCache);
        summaries.add(summary);
    }

    /**
     * Marks the report complete and writes its final checkpoint.
     *
     * @throws IOException on filesystem failure
     */
    public void complete() throws IOException {
        requireRunning();
        if (summaries.size() != totalReactors) {
            throw new IllegalStateException(
                    "Published reactor count "
                            + summaries.size()
                            + " does not match expected "
                            + totalReactors);
        }
        final TreeReportState completedState =
                getAnalysisIssues().isEmpty()
                        ? TreeReportState.SUCCESS
                        : TreeReportState
                        .COMPLETED_WITH_ISSUES;
        renderer.publishIndex(metadata, output,
                summaries, totalReactors,
                completedState, "");
        state = completedState;
    }

    /**
     * Marks a handled report pipeline failure while preserving pages.
     *
     * @param reason failure reason
     * @throws IOException on filesystem failure
     */
    public void fail(final String reason)
            throws IOException {
        requireRunning();
        final String normalized = reason == null
                ? "" : reason;
        renderer.publishIndex(metadata, output,
                summaries, totalReactors,
                TreeReportState.FAILED, normalized);
        failureReason = normalized;
        state = TreeReportState.FAILED;
    }

    /** @return current state */
    public TreeReportState getState() {
        return state;
    }

    /** @return number of published reactor checkpoints */
    public int getCompletedReactors() {
        return summaries.size();
    }

    /** @return immutable lightweight summaries */
    public List<ReactorReportSummary> getSummaries() {
        return List.copyOf(summaries);
    }

    /**
     * Returns lightweight operational analysis issues.
     *
     * @return non-success reactor issues
     */
    public List<TreeAnalysisIssue> getAnalysisIssues() {
        return summaries.stream()
                .filter(item -> item.getStatus()
                        != ReactorStatus.SUCCESS)
                .map(TreeAnalysisIssue::from)
                .toList();
    }

    /** @return terminal failure reason */
    public String getFailureReason() {
        return failureReason;
    }

    private void requireRunning() {
        if (state != TreeReportState.RUNNING) {
            throw new IllegalStateException(
                    "Report session is already " + state);
        }
    }
}
