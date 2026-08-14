package io.github.dependencyanalysis.callgraph.strategy.kobj;

import io.github.dependencyanalysis.impact.ModuleCallGraphInputAdapter;

import io.github.dependencyanalysis.callgraph.engine.CallGraphException;
import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphEngine;
import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphSession;
import io.github.dependencyanalysis.callgraph.entrypoint.EntrypointSelection;
import io.github.dependencyanalysis.callgraph.jdk.JdkModelSelection;
import io.github.dependencyanalysis.callgraph.model.CodeOrigin;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.strategy.WalaReflectionOptions;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.summaries.SummarizedMethod;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;
import io.github.dependencyanalysis.impact.ModuleAnalysisUnit;
import io.github.dependencyanalysis.impact.ModuleChangeSet;
import io.github.dependencyanalysis.impact.ModuleId;
import io.github.dependencyanalysis.impact.ModulePresence;
import io.github.dependencyanalysis.models.jdk.JdkModelException;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verifies callback reachability through target JDK 8 implementations. */
class JdkCallbackReachabilityTest {

    /** Keeps each real-JDK graph build bounded. */
    private static final long GRAPH_TIMEOUT_SECONDS = 120L;

    /** JDK 8 model catalog target count. */
    private static final int MODEL_CATALOG_TARGETS = 384;

    /** Focused model fixture owner. */
    private static final String MODEL_APP = "JdkModelCallback";

    /** Temporary source and classes. */
    @TempDir
    private Path temporary;

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
                            CallGraphAlgorithm.K_OBJ,
                            WalaReflectionOptions.parse("NONE"),
                            JdkModelSelection.NONE, repository)
                            .build(new ModuleCallGraphInputAdapter()
                                            .adapt(unit),
                                    GRAPH_TIMEOUT_SECONDS);

            assertRealJdkDispatch(session,
                    CallGraphAlgorithm.K_OBJ,
                    "PreciseJdkCallback$Task", "run");
        }
    }

    @Test
    void defaultJdk8ModelRejectsIncompleteHierarchyWithoutFallback()
            throws Exception {
        final Path classes = compileThreadFixture();
        final JavaRuntimeDescriptor runtime =
                MinimalJdk8RuntimeFixture.create(
                        temporary.resolve("incomplete-jdk8"));
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                new ModuleId(new ArtifactCoord(
                        "test", "jdk-model-strict", "jar", "1"),
                        Path.of(".")),
                ModulePresence.BOTH, classes, List.of(), List.of(), List.of(),
                new ModuleChangeSet(List.of(), List.of()));

        try (var repository = TestJarRepositories.empty()) {
            final ModuleCallGraphEngine engine = new ModuleCallGraphEngine(
                    diagnostics(), runtime,
                    EntrypointSelection.parse(
                            List.of("PreciseJdkCallback"), List.of()),
                    CallGraphAlgorithm.K_OBJ,
                    WalaReflectionOptions.parse("NONE"), repository);

            assertThatThrownBy(() -> engine.build(
                    new ModuleCallGraphInputAdapter().adapt(unit),
                    GRAPH_TIMEOUT_SECONDS))
                    .isInstanceOf(CallGraphException.class)
                    .hasRootCauseInstanceOf(JdkModelException.class)
                    .satisfies(exception -> assertThat(exception.getCause())
                            .hasMessageContaining(
                                    "model catalog is incomplete"));
        }
    }

    @Test
    void kObjInstallsAndUsesDefaultJdk8Model()
            throws Exception {
        final Path classes = compileModelFixture();
        final JavaRuntimeDescriptor runtime = new Jdk8RuntimeProvider()
                .probe(Path.of(System.getenv("TEST_JDK8_HOME")));
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                new ModuleId(new ArtifactCoord(
                        "test", "jdk-models", "jar", "1"), Path.of(".")),
                ModulePresence.BOTH, classes, List.of(), List.of(), List.of(),
                new ModuleChangeSet(List.of(), List.of()));
        final EntrypointSelection roots = EntrypointSelection.parse(
                List.of(MODEL_APP), List.of());

        for (CallGraphAlgorithm algorithm : CallGraphAlgorithm.values()) {
            if (algorithm == CallGraphAlgorithm.CHA) {
                continue;
            }
            try (var repository = TestJarRepositories.empty()) {
                final ModuleCallGraphSession session =
                        new ModuleCallGraphEngine(
                                diagnostics(), runtime, roots, algorithm,
                                WalaReflectionOptions.parse("NONE"),
                                repository).build(
                                new ModuleCallGraphInputAdapter().adapt(unit),
                                GRAPH_TIMEOUT_SECONDS);

                final var metadata = session.jdkModelMetadata()
                        .orElseThrow();
                assertThat(metadata.modelId()).isEqualTo("jdk8");
                assertThat(metadata.catalogTargetCount())
                        .isEqualTo(MODEL_CATALOG_TARGETS);
                assertThat(metadata.availableTargetCount())
                        .isEqualTo(MODEL_CATALOG_TARGETS);
                assertThat(metadata.unavailableTargetCount()).isZero();
                assertThat(metadata.hitTargetCount()).isPositive();
                assertModeledDispatch(session, algorithm,
                        MODEL_APP + "$ChangedMapper", "apply");
                assertThat(session.getGraph()).anyMatch(node ->
                        owner(node).equals(MODEL_APP)
                                && node.getMethod().getName().toString()
                                .equals("changedDependency"));
            }
        }
    }

    private void assertModeledDispatch(
            final ModuleCallGraphSession session,
            final CallGraphAlgorithm algorithm,
            final String callbackOwner,
            final String callbackName) {
        assertThat(predecessors(session, callbackOwner, callbackName))
                .as("%s modeled dispatch to %s.%s",
                        algorithm.identifier(), callbackOwner, callbackName)
                .anyMatch(node -> node.getMethod() instanceof SummarizedMethod
                        && session.originOf(node.getMethod()
                        .getDeclaringClass()) == CodeOrigin.JDK);
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

    private Path compileModelFixture() throws Exception {
        final Path source = temporary.resolve(MODEL_APP + ".java");
        final Path classes = temporary.resolve("model-classes");
        Files.createDirectories(classes);
        Files.writeString(source, """
                import java.util.function.Function;
                import java.util.stream.Stream;

                public final class JdkModelCallback {
                    public static int execute(String value) {
                        return Stream.of(value)
                                .map(new ChangedMapper())
                                .findFirst()
                                .orElse(0);
                    }
                    private static int changedDependency(String value) {
                        return value.length();
                    }
                    private static final class ChangedMapper
                            implements Function<String, Integer> {
                        public Integer apply(String value) {
                            return changedDependency(value);
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

    private DiagnosticLog diagnostics() {
        return new DiagnosticLog(
                new PrintStream(new ByteArrayOutputStream()),
                LogVerbosity.INFO);
    }
}
