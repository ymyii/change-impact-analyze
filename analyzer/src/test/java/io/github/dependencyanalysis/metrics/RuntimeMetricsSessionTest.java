package io.github.dependencyanalysis.metrics;

import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;
import io.github.dependencyanalysis.metrics.ManagedExecutorRegistry
        .ManagedExecutor;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests TRACE-only asynchronous Runtime Metrics. */
class RuntimeMetricsSessionTest {

    /** Short asynchronous sample interval. */
    private static final Duration TEST_INTERVAL = Duration.ofMillis(10L);

    /** Time allowed for repeated asynchronous samples. */
    private static final long SAMPLE_WAIT_MILLIS = 45L;

    /** Observations attempted before the failing session closes. */
    private static final int FAILING_SAMPLE_COUNT = 3;

    /** Bytes per MiB. */
    private static final long MIB = 1024L * 1024L;

    /** Initial used heap. */
    private static final long INITIAL_USED_MIB = 10L;

    /** Initial committed heap. */
    private static final long INITIAL_COMMITTED_MIB = 20L;

    /** Initial maximum heap. */
    private static final long INITIAL_MAX_MIB = 30L;

    /** Final used heap. */
    private static final long FINAL_USED_MIB = 25L;

    /** Final committed heap. */
    private static final long FINAL_COMMITTED_MIB = 40L;

    /** Final maximum heap. */
    private static final long FINAL_MAX_MIB = 50L;

    @Test
    void infoAndDebugSessionsProduceNoMetrics() {
        for (LogVerbosity verbosity : List.of(
                LogVerbosity.INFO, LogVerbosity.DEBUG)) {
            final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            final DiagnosticLog log = new DiagnosticLog(
                    new PrintStream(bytes), verbosity);

            try (RuntimeMetricsSession ignored =
                         RuntimeMetricsSession.start(log)) {
                assertThat(bytes.toString(StandardCharsets.UTF_8)).isEmpty();
            }
        }
    }

    @Test
    void samplingFailureIsTransientAndDoesNotEscape() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DiagnosticLog log = new DiagnosticLog(
                new PrintStream(bytes), LogVerbosity.TRACE);
        final MemoryMXBean failingMemory = (MemoryMXBean) Proxy
                .newProxyInstance(getClass().getClassLoader(),
                        new Class<?>[]{MemoryMXBean.class},
                        (proxy, method, arguments) -> {
                            throw new IllegalStateException("unavailable");
                        });
        final RuntimeMetricsSession session = RuntimeMetricsSession.create(
                log, new ManagedExecutorRegistry(), failingMemory,
                null, TEST_INTERVAL);

        session.sample();
        session.sample();
        session.close();

        assertThat(bytes.toString(StandardCharsets.UTF_8).lines()
                .filter(line -> line.contains("Sampling failed")))
                .hasSize(FAILING_SAMPLE_COUNT);
        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("[TRACE][runtime-metrics][sampler][-]"
                        + " Sampling failed; sample=1; elapsedMs=")
                .contains("error=IllegalStateException: unavailable");
    }

    @Test
    void traceSessionSamplesImmediatelyAndThenAsynchronously()
            throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DiagnosticLog log = new DiagnosticLog(
                new PrintStream(bytes), LogVerbosity.TRACE);
        final ScheduledExecutorService scheduler =
                Executors.newSingleThreadScheduledExecutor();
        final RuntimeMetricsSession session = RuntimeMetricsSession.create(
                log, new ManagedExecutorRegistry(),
                ManagementFactory.getMemoryMXBean(), scheduler,
                TEST_INTERVAL);

        Thread.sleep(SAMPLE_WAIT_MILLIS);
        session.close();

        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("[TRACE][runtime-metrics][heap][-]"
                        + " Runtime metrics snapshot; sample=")
                .contains("heapUsedMiB=")
                .contains("heapCommittedMiB=")
                .contains("heapMaxMiB=")
                .contains("[TRACE][runtime-metrics][summary][-]"
                        + " Runtime metrics summary; samples=")
                .contains("peakHeapUsedMiB=")
                .contains("peakHeapCommittedMiB=");
        assertThat(bytes.toString(StandardCharsets.UTF_8).lines()
                .filter(line -> line.contains("[runtime-metrics][heap]")))
                .hasSizeGreaterThanOrEqualTo(2);
    }

    @Test
    void samplesEachRegisteredPoolAndStopsAfterClose() throws Exception {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DiagnosticLog log = new DiagnosticLog(
                new PrintStream(bytes), LogVerbosity.TRACE);
        final ManagedExecutorRegistry registry =
                new ManagedExecutorRegistry();
        final RuntimeMetricsSession session = RuntimeMetricsSession.create(
                log, registry, ManagementFactory.getMemoryMXBean(),
                null, TEST_INTERVAL);
        final ManagedExecutor managed = registry.fixed("module-analysis", 1);
        final CountDownLatch running = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final Future<?> first = managed.executor().submit(() -> {
            running.countDown();
            release.await();
            return null;
        });
        final Future<?> second = managed.executor().submit(() -> null);
        assertThat(running.await(1L, TimeUnit.SECONDS)).isTrue();

        session.sample();
        final String sampled = bytes.toString(StandardCharsets.UTF_8);
        assertThat(sampled)
                .contains("[TRACE][runtime-metrics][thread-pool]"
                        + "[pool=module-analysis] Runtime metrics snapshot;")
                .contains("core=1; max=1; size=1; active=1; queued=1")
                .contains("shutdown=false; terminated=false");

        session.close();
        final int closedLength = bytes.size();
        session.sample();
        session.close();
        assertThat(bytes.size()).isEqualTo(closedLength);
        release.countDown();
        first.get(1L, TimeUnit.SECONDS);
        second.get(1L, TimeUnit.SECONDS);
        managed.close();
        assertThat(registry.snapshot()).isEmpty();
    }

    @Test
    void closeForcesFinalObservationAndReportsPeaks() {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DiagnosticLog log = new DiagnosticLog(
                new PrintStream(bytes), LogVerbosity.TRACE);
        final AtomicMemoryBean memory = new AtomicMemoryBean();
        final RuntimeMetricsSession session = RuntimeMetricsSession.create(
                log, new ManagedExecutorRegistry(), memory.bean(),
                null, TEST_INTERVAL);

        memory.set(INITIAL_USED_MIB * MIB,
                INITIAL_COMMITTED_MIB * MIB, INITIAL_MAX_MIB * MIB);
        session.sample();
        memory.set(FINAL_USED_MIB * MIB,
                FINAL_COMMITTED_MIB * MIB, FINAL_MAX_MIB * MIB);
        session.close();

        assertThat(bytes.toString(StandardCharsets.UTF_8))
                .contains("Runtime metrics summary; samples=2")
                .contains("peakHeapUsedMiB=25.0")
                .contains("peakHeapCommittedMiB=40.0")
                .contains("heapMaxMiB=50.0");
    }

    /** Mutable test MemoryMXBean. */
    private static final class AtomicMemoryBean {

        /** Current heap usage. */
        private volatile java.lang.management.MemoryUsage usage =
                new java.lang.management.MemoryUsage(0L, 0L, 0L, 0L);

        void set(final long used, final long committed, final long max) {
            usage = new java.lang.management.MemoryUsage(
                    0L, used, committed, max);
        }

        MemoryMXBean bean() {
            return (MemoryMXBean) Proxy.newProxyInstance(
                    getClass().getClassLoader(),
                    new Class<?>[]{MemoryMXBean.class},
                    (proxy, method, arguments) -> {
                        if (method.getName().equals("getHeapMemoryUsage")) {
                            return usage;
                        }
                        if (method.getName().equals("getObjectName")) {
                            return null;
                        }
                        return null;
                    });
        }
    }
}
