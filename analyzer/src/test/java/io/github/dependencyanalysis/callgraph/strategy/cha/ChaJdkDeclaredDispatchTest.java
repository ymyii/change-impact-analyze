package io.github.dependencyanalysis.callgraph.strategy.cha;

import io.github.dependencyanalysis.impact.ModuleCallGraphInputAdapter;

import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphEngine;
import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphSession;
import io.github.dependencyanalysis.callgraph.entrypoint.EntrypointSelection;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.strategy.WalaReflectionOptions;

import com.ibm.wala.ipa.callgraph.CGNode;

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
import java.util.Iterator;
import java.util.List;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies resolved JDK declaration boundaries in a real JDK 8 graph. */
class ChaJdkDeclaredDispatchTest {

    /** Keeps the real-JDK graph build bounded. */
    private static final long GRAPH_TIMEOUT_SECONDS = 120L;

    /** Application fixture owner. */
    private static final String APPLICATION = "ChaJdkDispatchApp";

    /** Temporary source and classes. */
    @TempDir
    private Path temporary;

    @Test
    void resolvesApplicationReferencesBeforeJdkDispatchFiltering()
            throws Exception {
        final Path classes = compileFixture();
        final JavaRuntimeDescriptor runtime = new Jdk8RuntimeProvider()
                .probe(Path.of(System.getenv("TEST_JDK8_HOME")));
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                new ModuleId(new ArtifactCoord(
                        "test", "cha-jdk-dispatch", "jar", "1"),
                        Path.of(".")),
                ModulePresence.BOTH, classes, List.of(), List.of(), List.of(),
                new ModuleChangeSet(List.of(), List.of()));
        final EntrypointSelection roots = EntrypointSelection.parse(
                List.of(APPLICATION), List.of());

        try (var repository = TestJarRepositories.empty()) {
            final ModuleCallGraphSession session =
                    new ModuleCallGraphEngine(
                            diagnostics(), runtime, roots,
                            CallGraphAlgorithm.CHA,
                            WalaReflectionOptions.parse("NONE"), repository)
                            .build(new ModuleCallGraphInputAdapter()
                                            .adapt(unit),
                                    GRAPH_TIMEOUT_SECONDS);

            assertThat(hasEdge(session, "interfaceCall",
                    APPLICATION + "$IteratorImpl", "next")).isFalse();
            assertThat(hasEdge(session, "virtualCall",
                    APPLICATION + "$ThreadImpl", "run")).isFalse();
            assertThat(hasEdge(session, "customCall",
                    APPLICATION + "$CustomImpl", "run")).isTrue();
            assertThat(session.getJdkDispatchPruning().prunedTargetCount())
                    .isPositive();
        }
    }

    private boolean hasEdge(
            final ModuleCallGraphSession session,
            final String callerName,
            final String targetOwner,
            final String targetName) {
        for (CGNode caller : session.getGraph()) {
            if (!APPLICATION.equals(owner(caller))
                    || !callerName.equals(
                    caller.getMethod().getName().toString())) {
                continue;
            }
            final Iterator<CGNode> successors = session.getGraph()
                    .getSuccNodes(caller);
            while (successors.hasNext()) {
                final CGNode target = successors.next();
                if (targetOwner.equals(owner(target))
                        && targetName.equals(
                        target.getMethod().getName().toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private String owner(final CGNode node) {
        final String value = node.getMethod().getDeclaringClass()
                .getName().toString();
        return value.startsWith("L") ? value.substring(1) : value;
    }

    private Path compileFixture() throws Exception {
        final Path source = temporary.resolve(APPLICATION + ".java");
        final Path classes = temporary.resolve("classes");
        Files.createDirectories(classes);
        Files.writeString(source, """
                import java.util.Iterator;

                public final class ChaJdkDispatchApp {
                    public static Object interfaceCall(
                            Iterator<Object> receiver) {
                        return receiver.next();
                    }

                    public static void virtualCall(Thread receiver) {
                        receiver.run();
                    }

                    public static void customCall(Custom receiver) {
                        receiver.run();
                    }

                    interface Custom {
                        void run();
                    }

                    static final class IteratorImpl
                            implements Iterator<Object> {
                        public boolean hasNext() {
                            return false;
                        }

                        public Object next() {
                            return null;
                        }
                    }

                    static final class ThreadImpl extends Thread {
                        @Override
                        public void run() { }
                    }

                    static final class CustomImpl implements Custom {
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
