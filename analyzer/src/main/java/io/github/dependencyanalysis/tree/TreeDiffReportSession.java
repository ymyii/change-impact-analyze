package io.github.dependencyanalysis.tree;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Incrementally publishes Reactor diff pages and index checkpoints. */
final class TreeDiffReportSession {

    /** Renderer. */
    private final TreeDiffReportRenderer renderer;

    /** Metadata. */
    private final TreeDiffReportMetadata metadata;

    /** Output directory. */
    private final Path output;

    /** Expected Reactor count. */
    private final int totalReactors;

    /** Published checkpoints. */
    private final List<TreeDiffReactorSummary> summaries =
            new ArrayList<>();

    /** Current state. */
    private TreeDiffReportState state = TreeDiffReportState.RUNNING;

    TreeDiffReportSession(
            final TreeDiffReportRenderer reportRenderer,
            final TreeDiffReportMetadata reportMetadata,
            final Path reportOutput,
            final int reactorCount) {
        renderer = reportRenderer;
        metadata = reportMetadata;
        output = reportOutput;
        totalReactors = reactorCount;
    }

    /**
     * Publishes one complete Reactor and refreshes the index.
     *
     * @param reactor Reactor diff result
     * @throws IOException on publication failure
     */
    void publish(final TreeDiffReactorResult reactor) throws IOException {
        requireRunning();
        if (summaries.size() >= totalReactors) {
            throw new IllegalStateException(
                    "All expected Reactors are already published");
        }
        final TreeDiffReactorSummary summary = renderer.publishReactor(
                metadata, output, reactor);
        summaries.add(summary);
        renderer.publishIndex(metadata, output, summaries, totalReactors,
                TreeDiffReportState.RUNNING, "");
    }

    /** Completes the report using the fixed status contract. */
    void complete() throws IOException {
        requireRunning();
        if (summaries.size() != totalReactors) {
            throw new IllegalStateException(
                    "Published Reactor count does not match expected count");
        }
        final int comparable = summaries.stream()
                .mapToInt(TreeDiffReactorSummary::comparableModules).sum();
        final boolean issues = summaries.stream().anyMatch(summary ->
                summary.comparisonStatus()
                        != TreeDiffComparisonStatus.COMPARABLE
                || summary.structureMismatchModules() > 0
                || summary.unavailableModules() > 0
                || !summary.issues().isEmpty());
        state = comparable == 0 ? TreeDiffReportState.FAILED
                : issues ? TreeDiffReportState.COMPLETED_WITH_ISSUES
                : TreeDiffReportState.SUCCESS;
        renderer.publishIndex(metadata, output, summaries, totalReactors,
                state, comparable == 0
                        ? "No comparable Module was available." : "");
    }

    /**
     * Marks a handled publication failure while preserving pages.
     *
     * @param reason failure reason
     * @throws IOException on publication failure
     */
    void fail(final String reason) throws IOException {
        requireRunning();
        state = TreeDiffReportState.FAILED;
        renderer.publishIndex(metadata, output, summaries, totalReactors,
                state, reason == null ? "" : reason);
    }

    /** @return lifecycle state */
    TreeDiffReportState state() {
        return state;
    }

    /** @return immutable published checkpoints */
    List<TreeDiffReactorSummary> summaries() {
        return List.copyOf(summaries);
    }

    private void requireRunning() {
        if (state != TreeDiffReportState.RUNNING) {
            throw new IllegalStateException(
                    "Tree diff report is already " + state);
        }
    }
}
