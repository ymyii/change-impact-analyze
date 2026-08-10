package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.summaries.SummarizedMethod;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;
import io.github.dependencyanalysis.impact.ModuleAnalysisUnit;
import io.github.dependencyanalysis.impact.ModuleChangeSet;
import io.github.dependencyanalysis.impact.ModuleId;
import io.github.dependencyanalysis.impact.ModulePresence;
import io.github.dependencyanalysis.runtime.Jdk8RuntimeProvider;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;
import io.github.dependencyanalysis.testing.TestJarRepositories;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies callback reachability through target JDK 8 implementations. */
class JdkCallbackReachabilityTest {

    /** Keeps each real-JDK graph build bounded. */
    private static final long GRAPH_TIMEOUT_SECONDS = 120L;

    /** Application fixture owner. */
    private static final String APP = "JdkCallbacks$";

    /** Temporary source and classes. */
    @TempDir
    private Path temporary;

    @Test
    void rtaAndZeroXAlgorithmsReachBroadRealJdkCallbacks()
            throws Exception {
        final Path classes = compileFixture();
        final JavaRuntimeDescriptor runtime = new Jdk8RuntimeProvider()
                .probe(Path.of(System.getenv("TEST_JDK8_HOME")));
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                new ModuleId(new ArtifactCoord(
                        "test", "jdk-callbacks", "jar", "1"), Path.of(".")),
                ModulePresence.BOTH, classes, List.of(), List.of(), List.of(),
                new ModuleChangeSet(List.of(), List.of()));
        final EntrypointSelection roots = EntrypointSelection.parse(
                List.of("JdkCallbacks"), List.of());

        for (CallGraphAlgorithm algorithm : List.of(
                CallGraphAlgorithm.RTA,
                CallGraphAlgorithm.ZERO_CFA,
                CallGraphAlgorithm.OPTIMIZED_ZERO_ONE_CFA)) {
            try (var repository = TestJarRepositories.empty()) {
                final ModuleCallGraphSession session =
                        new ModuleCallGraphEngine(
                                diagnostics(), runtime, roots, algorithm,
                                WalaReflectionOptions.parse("NONE"),
                                repository).build(unit, GRAPH_TIMEOUT_SECONDS);

                assertRealJdkDispatch(session, algorithm,
                        APP + "StreamSink", "accept");
                assertRealJdkDispatch(session, algorithm,
                        APP + "OptionalSink", "accept");
                assertRealJdkDispatch(session, algorithm,
                        APP + "CollectionSink", "accept");
                assertRealJdkDispatch(session, algorithm,
                        APP + "MapSink", "accept");
                assertRealJdkDispatch(session, algorithm,
                        APP + "ExecutorTask", "call");
                assertRealJdkDispatch(session, algorithm,
                        APP + "FutureMapper", "apply");
                assertRealJdkDispatch(session, algorithm,
                        APP + "ThreadTask", "run");
                assertNativeAccessControllerModel(session, algorithm);
            }
        }
    }

    @Test
    void oneObjectOneCallSiteReachesJdk8PrimordialThreadCallback()
            throws Exception {
        final Path classes = compileThreadFixture();
        final JavaRuntimeDescriptor runtime =
                MinimalJdk8RuntimeFixture.create(
                        temporary.resolve("minimal-jdk8"));
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                new ModuleId(new ArtifactCoord(
                        "test", "jdk-thread", "jar", "1"), Path.of(".")),
                ModulePresence.BOTH, classes, List.of(), List.of(), List.of(),
                new ModuleChangeSet(List.of(), List.of()));
        final EntrypointSelection roots = EntrypointSelection.parse(
                List.of("PreciseJdkCallback"), List.of());

        try (var repository = TestJarRepositories.empty()) {
            final ModuleCallGraphSession session =
                    new ModuleCallGraphEngine(
                            diagnostics(), runtime, roots,
                            CallGraphAlgorithm.ONE_OBJECT_ONE_CALL_SITE,
                            WalaReflectionOptions.parse("NONE"), repository)
                            .build(unit, GRAPH_TIMEOUT_SECONDS);

            assertRealJdkDispatch(session,
                    CallGraphAlgorithm.ONE_OBJECT_ONE_CALL_SITE,
                    "PreciseJdkCallback$Task", "run");
        }
    }

    private void assertRealJdkDispatch(
            final ModuleCallGraphSession session,
            final CallGraphAlgorithm algorithm,
            final String callbackOwner,
            final String callbackName) {
        final List<CGNode> dispatchers = predecessors(
                session, callbackOwner, callbackName).stream()
                .filter(node -> session.originOf(
                        node.getMethod().getDeclaringClass()) == CodeOrigin.JDK)
                .filter(node -> !node.getMethod().isNative())
                .filter(node -> !node.getMethod().isSynthetic())
                .filter(node -> !node.getMethod().isWalaSynthetic())
                .filter(node -> node.getIR() != null)
                .toList();

        assertThat(dispatchers)
                .as("%s real JDK dispatch to %s.%s",
                        algorithm.identifier(), callbackOwner, callbackName)
                .isNotEmpty();
    }

    private void assertNativeAccessControllerModel(
            final ModuleCallGraphSession session,
            final CallGraphAlgorithm algorithm) {
        final List<CGNode> models = predecessors(
                session, APP + "PrivilegedTask", "run").stream()
                .filter(node -> owner(node)
                        .equals("java/security/AccessController"))
                .filter(node -> node.getMethod().getName().toString()
                        .equals("doPrivileged"))
                .filter(node -> node.getMethod() instanceof SummarizedMethod)
                .filter(node -> node.getIR() != null)
                .toList();

        assertThat(models)
                .as("%s WALA native model for AccessController.doPrivileged",
                        algorithm.identifier())
                .isNotEmpty();
    }

    private List<CGNode> predecessors(
            final ModuleCallGraphSession session,
            final String callbackOwner,
            final String callbackName) {
        final List<CGNode> result = new ArrayList<>();
        for (CGNode callback : session.getGraph()) {
            if (!owner(callback).equals(callbackOwner)
                    || !callback.getMethod().getName().toString()
                    .equals(callbackName)) {
                continue;
            }
            final Iterator<CGNode> iterator = session.getGraph()
                    .getPredNodes(callback);
            iterator.forEachRemaining(result::add);
        }
        return result;
    }

    private String owner(final CGNode node) {
        final String value = node.getMethod().getDeclaringClass()
                .getName().toString();
        return value.startsWith("L") ? value.substring(1) : value;
    }

    private Path compileFixture() throws Exception {
        final Path source = temporary.resolve("JdkCallbacks.java");
        final Path classes = temporary.resolve("classes");
        Files.createDirectories(classes);
        Files.writeString(source, """
                import java.security.AccessController;
                import java.security.PrivilegedAction;
                import java.util.ArrayList;
                import java.util.HashMap;
                import java.util.List;
                import java.util.Map;
                import java.util.Optional;
                import java.util.concurrent.AbstractExecutorService;
                import java.util.concurrent.CompletableFuture;
                import java.util.concurrent.TimeUnit;
                import java.util.function.BiConsumer;
                import java.util.function.Consumer;
                import java.util.function.Function;
                import java.util.stream.Stream;

                public final class JdkCallbacks {
                    public static void streamAndOptional() {
                        Stream.of("stream").forEach(new StreamSink());
                        Optional.of("optional").ifPresent(
                                new OptionalSink());
                    }

                    public static void collectionAndMap() {
                        List<String> values = new ArrayList<String>();
                        values.add("collection");
                        values.forEach(new CollectionSink());
                        Map<String, String> map =
                                new HashMap<String, String>();
                        map.put("key", "value");
                        map.forEach(new MapSink());
                    }

                    public static void executorAndFuture() throws Exception {
                        DirectExecutor executor = new DirectExecutor();
                        executor.submit(new ExecutorTask()).get();
                        CompletableFuture.completedFuture("future")
                                .thenApply(new FutureMapper());
                    }

                    public static void thread() {
                        new Thread(new ThreadTask()).run();
                    }

                    public static void privileged() {
                        AccessController.doPrivileged(new PrivilegedTask());
                    }

                    static final class StreamSink
                            implements Consumer<String> {
                        public void accept(String value) { }
                    }

                    static final class OptionalSink
                            implements Consumer<String> {
                        public void accept(String value) { }
                    }

                    static final class CollectionSink
                            implements Consumer<String> {
                        public void accept(String value) { }
                    }

                    static final class MapSink
                            implements BiConsumer<String, String> {
                        public void accept(String key, String value) { }
                    }

                    static final class ExecutorTask
                            implements java.util.concurrent.Callable<String> {
                        public String call() { return "executor"; }
                    }

                    static final class FutureMapper
                            implements Function<String, String> {
                        public String apply(String value) { return value; }
                    }

                    static final class ThreadTask implements Runnable {
                        public void run() { }
                    }

                    static final class PrivilegedTask
                            implements PrivilegedAction<String> {
                        public String run() { return "privileged"; }
                    }

                    static final class DirectExecutor
                            extends AbstractExecutorService {
                        private boolean shutdown;
                        public void shutdown() { shutdown = true; }
                        public List<Runnable> shutdownNow() {
                            shutdown = true;
                            return new ArrayList<Runnable>();
                        }
                        public boolean isShutdown() { return shutdown; }
                        public boolean isTerminated() { return shutdown; }
                        public boolean awaitTermination(
                                long timeout, TimeUnit unit) {
                            return shutdown;
                        }
                        public void execute(Runnable command) {
                            command.run();
                        }
                    }
                }
                """);
        final int exit = ToolProvider.getSystemJavaCompiler().run(
                null, null, null, "--release", "8", "-d",
                classes.toString(), source.toString());
        assertThat(exit).isZero();
        return classes;
    }

    private Path compileThreadFixture() throws Exception {
        final Path source = temporary.resolve("PreciseJdkCallback.java");
        final Path classes = temporary.resolve("precise-classes");
        Files.createDirectories(classes);
        Files.writeString(source, """
                public final class PreciseJdkCallback {
                    public static void execute() {
                        new Thread(new Task()).run();
                    }
                    static final class Task implements Runnable {
                        public void run() { }
                    }
                }
                """);
        final int exit = ToolProvider.getSystemJavaCompiler().run(
                null, null, null, "--release", "8", "-d",
                classes.toString(), source.toString());
        assertThat(exit).isZero();
        return classes;
    }

    private DiagnosticLog diagnostics() {
        return new DiagnosticLog(
                new PrintStream(new ByteArrayOutputStream()),
                LogVerbosity.INFO);
    }
}
