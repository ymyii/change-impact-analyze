package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.preflight
        .PreflightConsoleRenderer;
import io.github.dependencyanalysis.preflight
        .PreflightReport;

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

// Wiki: wiki/features/cli-preflight-diagnostics.md - tree console entrypoint
/** Emits the stable three-stage tree command console contract. */
final class TreeConsoleReporter {

    /** Production heartbeat interval. */
    static final long HEARTBEAT_MILLIS = 10_000L;

    /** Console destination. */
    private final PrintStream output;

    /** Heartbeat interval. */
    private final long heartbeatMillis;

    TreeConsoleReporter(final PrintStream destination) {
        this(destination, HEARTBEAT_MILLIS);
    }

    TreeConsoleReporter(
            final PrintStream destination,
            final long intervalMillis) {
        output = destination;
        heartbeatMillis = intervalMillis;
    }

    void preflight(final PreflightReport report) {
        output.println("Stage 1/3: Preflight");
        new PreflightConsoleRenderer().render(
                report, output);
    }

    void analysisStarted(final int totalReactors) {
        output.println("Stage 2/3: Analysis");
        output.println("reactors=" + totalReactors);
    }

    void analysisSkipped(final String reason) {
        output.println("Stage 2/3: Analysis");
        output.println("SKIPPED: " + oneLine(reason));
    }

    Heartbeat reactorStarted(
            final int index,
            final int total,
            final String reactorId) {
        output.println(prefix(index, total)
                + " MAVEN_COLLECTION_START reactor="
                + reactorId);
        return new Heartbeat(output, heartbeatMillis,
                index, total, reactorId);
    }

    void reactorCompleted(
            final int index,
            final int total,
            final ReactorTreeResult result) {
        output.println(prefix(index, total)
                + " MAVEN_COLLECTION_RESULT status="
                + result.getStatus() + "; reactor="
                + result.getReactor().getId()
                + "; modules="
                + result.getModules().size());
    }

    void analysisCompleted(
            final List<TreeAnalysisIssue> issues) {
        output.println("analysisIssues=" + issues.size());
        for (int index = 0; index < issues.size(); index++) {
            final TreeAnalysisIssue issue = issues.get(index);
            output.println("issue[" + (index + 1)
                    + "]=reactor=" + issue.getReactorId()
                    + "; status=" + issue.getStatus()
                    + "; reason="
                    + oneLine(issue.getReason()));
        }
    }

    void summary(final TreeRunSummary summary) {
        output.println("Stage 3/3: Summary");
        output.println("status=" + summary.getStatus());
        output.println("report=" + summary.getReport());
    }

    private String prefix(
            final int index,
            final int total) {
        return "[" + index + "/" + total + "]";
    }

    private String oneLine(final String value) {
        if (value == null || value.isBlank()) {
            return "unspecified";
        }
        return value.replaceAll("\\s+", " ").trim();
    }

    /** Active Maven progress heartbeat. */
    static final class Heartbeat implements AutoCloseable {

        /** Scheduler. */
        private final ScheduledExecutorService scheduler;

        /** Scheduled heartbeat. */
        private final ScheduledFuture<?> future;

        Heartbeat(
                final PrintStream destination,
                final long intervalMillis,
                final int index,
                final int total,
                final String reactorId) {
            final long started = System.nanoTime();
            scheduler = Executors
                    .newSingleThreadScheduledExecutor(task -> {
                        final Thread thread = new Thread(
                                task,
                                "dependency-analyzer-heartbeat");
                        thread.setDaemon(true);
                        return thread;
                    });
            future = scheduler.scheduleAtFixedRate(() -> {
                final long elapsed = TimeUnit.NANOSECONDS
                        .toSeconds(System.nanoTime() - started);
                destination.println("[" + index + "/"
                        + total
                        + "] MAVEN_COLLECTION_HEARTBEAT reactor="
                        + reactorId + "; elapsed="
                        + elapsed + "s");
            }, intervalMillis, intervalMillis,
                    TimeUnit.MILLISECONDS);
        }

        @Override
        public void close() {
            future.cancel(false);
            scheduler.shutdownNow();
        }
    }
}
