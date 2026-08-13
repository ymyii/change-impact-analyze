package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.callgraph.CodeOrigin;
import io.github.dependencyanalysis.callgraph.MethodId;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests isolated per-seed TRACE clocks and progress metrics. */
class SeedProgressReporterTest {

    /** Ten seconds in nanoseconds. */
    private static final long TEN_SECONDS = Duration.ofSeconds(
            SeedProgressReporter.HEARTBEAT_INTERVAL.toSeconds()).toNanos();

    /** Twenty-five seconds in nanoseconds. */
    private static final long TWENTY_FIVE_SECONDS =
            TEN_SECONDS * 5L / 2L;

    /** Thirty-five seconds in nanoseconds. */
    private static final long THIRTY_FIVE_SECONDS =
            TEN_SECONDS * 7L / 2L;

    /** Seed A visited count. */
    private static final int SEED_A_VISITED = 4;

    /** Seed B visited count. */
    private static final int SEED_B_VISITED = 7;

    /** Quiet seed visited count. */
    private static final int QUIET_VISITED = 9;

    /** Terminal reference bytecode PC. */
    private static final int TERMINAL_PC = 12;

    @Test
    void resetsClockVisitedRecentNodeAndHeartbeatForEverySeed() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DiagnosticLog log = new DiagnosticLog(
                new PrintStream(bytes), LogVerbosity.TRACE);
        final AtomicLong clock = new AtomicLong();
        final ManualScheduler scheduler = new ManualScheduler();
        final DiagnosticContext context = DiagnosticContext.of(
                "module-analysis", "impact-query").withModule("sample");

        try (SeedProgressReporter reporter = SeedProgressReporter.create(
                log, context, clock::get, scheduler,
                SeedProgressReporter.HEARTBEAT_INTERVAL)) {
            final QueryNode seedA = node("SeedA");
            try (SeedProgressTracker tracker = reporter.startOrdinary(
                    point("first"), new ImpactSeed(seedA,
                            evidence("first")))) {
                tracker.reverseProgress(node("RecentA"), SEED_A_VISITED);
                clock.set(TEN_SECONDS);
                scheduler.fire();
                clock.set(2 * TEN_SECONDS);
                scheduler.fire();
                clock.set(TWENTY_FIVE_SECONDS);
                tracker.complete();
                scheduler.fire();
            }

            final QueryNode seedB = node("SeedB");
            try (SeedProgressTracker tracker = reporter.startOrdinary(
                    point("second"), new ImpactSeed(seedB,
                            evidence("second")))) {
                tracker.reverseCompleted(SEED_B_VISITED);
                clock.set(THIRTY_FIVE_SECONDS);
                scheduler.fire();
                tracker.complete();
            }
        }

        final List<String> lines = eventLines(bytes);
        assertThat(lines).filteredOn(line -> line.contains(
                "event=seed-started"))
                .hasSize(2)
                .allMatch(line -> line.contains(
                        "elapsedMs=0; visited=0"));
        assertThat(lines).anySatisfy(line -> assertThat(line)
                .contains("event=seed-progress; seedOrdinal=1")
                .contains("heartbeat=1; elapsedMs=10000")
                .contains("visited=4")
                .contains("recentMethodName=RecentA"));
        assertThat(lines).anySatisfy(line -> assertThat(line)
                .contains("event=seed-progress; seedOrdinal=1")
                .contains("heartbeat=2; elapsedMs=20000"));
        assertThat(lines).anySatisfy(line -> assertThat(line)
                .contains("event=seed-completed; seedOrdinal=1")
                .contains("elapsedMs=25000; visited=4"));
        assertThat(lines).anySatisfy(line -> assertThat(line)
                .contains("event=seed-started; seedOrdinal=2")
                .contains("elapsedMs=0; visited=0")
                .contains("methodName=SeedB"));
        assertThat(lines).anySatisfy(line -> assertThat(line)
                .contains("event=seed-progress; seedOrdinal=2")
                .contains("heartbeat=1; elapsedMs=10000")
                .contains("visited=7")
                .contains("recentMethodName=SeedB"));
        assertThat(lines).filteredOn(line -> line.contains(
                "event=seed-progress; seedOrdinal=1"))
                .hasSize(2);
        assertThat(lines).allMatch(line -> !line.contains("methodBody"));
        assertThat(log.getEvents()).isEmpty();
        assertThat(scheduler.closed).isTrue();
    }

    @Test
    void disablesSeedDiagnosticsBelowTraceWithoutScheduling() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DiagnosticLog log = new DiagnosticLog(
                new PrintStream(bytes), LogVerbosity.DEBUG);
        final ManualScheduler scheduler = new ManualScheduler();

        try (SeedProgressReporter reporter = SeedProgressReporter.create(
                log, DiagnosticContext.stage("impact-query"),
                System::nanoTime, scheduler,
                SeedProgressReporter.HEARTBEAT_INTERVAL);
             SeedProgressTracker tracker = reporter.startOrdinary(
                     point("quiet"), new ImpactSeed(
                             node("Quiet"), evidence("quiet")))) {
            tracker.reverseProgress(node("Hidden"), QUIET_VISITED);
            scheduler.fire();
            tracker.complete();
        }

        assertThat(bytes.toString(StandardCharsets.UTF_8)).isEmpty();
        assertThat(scheduler.tasks).isEmpty();
    }

    private List<String> eventLines(final ByteArrayOutputStream bytes) {
        return bytes.toString(StandardCharsets.UTF_8).lines()
                .filter(line -> line.contains("event=seed-"))
                .toList();
    }

    private QueryNode node(final String name) {
        return new TestQueryNode(new MethodId("sample/Owner", name,
                "()V", "sample", "sample/classes"), CodeOrigin.PROJECT);
    }

    private BoundChangePoint point(final String name) {
        final ArtifactCoord module = new ArtifactCoord(
                "sample", "app", "jar", "1");
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "sample", "library", "jar", "1");
        final ArtifactCoord newArtifact = new ArtifactCoord(
                "sample", "library", "jar", "2");
        return new BoundChangePoint(new DependencyUpgradeKey(
                new ModuleId(module, Path.of("app")), DependencyScope.COMPILE,
                oldArtifact, newArtifact), new ChangePoint(newArtifact,
                ChangePointKind.METHOD_REMOVED, "sample/Api", name, "()V",
                null, null));
    }

    private ReferenceEvidence evidence(final String name) {
        return new ReferenceEvidence(Optional.empty(),
                new ReferenceTarget("sample/Api", name, "()V"),
                EvidenceKind.METHOD_REFERENCE,
                EvidenceMechanism.DECLARED_INVOKE,
                new EvidenceLocation("sample/Owner#call()V", TERMINAL_PC),
                "declared invoke " + name);
    }

    /**
     * Minimal query node.
     *
     * @param methodId method identity
     * @param origin code origin
     */
    private record TestQueryNode(MethodId methodId, CodeOrigin origin)
            implements QueryNode {
    }

    /** Deterministic scheduler advanced explicitly by the test. */
    private static final class ManualScheduler
            implements SeedProgressReporter.HeartbeatScheduler {

        /** Scheduled tasks. */
        private final List<ManualTask> tasks = new ArrayList<>();

        /** Closed state. */
        private boolean closed;

        @Override
        public SeedProgressReporter.Cancellable scheduleAtFixedRate(
                final Runnable task,
                final long initialDelay,
                final long period,
                final TimeUnit unit) {
            assertThat(unit.toNanos(initialDelay)).isEqualTo(TEN_SECONDS);
            assertThat(unit.toNanos(period)).isEqualTo(TEN_SECONDS);
            final ManualTask result = new ManualTask(task);
            tasks.add(result);
            return result;
        }

        void fire() {
            tasks.stream().filter(task -> !task.cancelled)
                    .forEach(task -> task.runnable.run());
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    /** One manual scheduled task. */
    private static final class ManualTask
            implements SeedProgressReporter.Cancellable {

        /** Runnable. */
        private final Runnable runnable;

        /** Cancelled flag. */
        private boolean cancelled;

        ManualTask(final Runnable value) {
            runnable = value;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
