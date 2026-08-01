package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.types.ClassLoaderReference;

import io.github.dependencyanalysis.diagnostic.DiagnosticCollector;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.impact.ModuleAnalysisUnit;
import io.github.dependencyanalysis.impact.ModuleChangeSet;
import io.github.dependencyanalysis.impact.ModuleId;
import io.github.dependencyanalysis.impact.ModulePresence;
import io.github.dependencyanalysis.runtime.Jdk8RuntimeProvider;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Tests target JDK archive scope placement. */
class JdkAnalysisStageTest {

    /** Target Java major. */
    private static final int TARGET_MAJOR = 8;

    /** Minimum expected classes in a complete JDK 8 scope. */
    private static final int MINIMUM_JDK_CLASSES = 10_000;

    /** Temporary JDK archive directory. */
    @TempDir
    private Path temporary;

    @Test
    void addsBootAndExtensionJarsToSeparateLoaders()
            throws Exception {
        final Path rt = emptyJar("rt.jar");
        final Path extension = emptyJar("extension.jar");
        final JavaRuntimeDescriptor runtime =
                new JavaRuntimeDescriptor(
                        temporary, temporary,
                        "1.8.0_402", TARGET_MAJOR,
                        List.of(rt), List.of(extension));

        final AnalysisScope scope =
                new JdkAnalysisStage(diagnostics())
                        .prepare(runtime);

        assertThat(scope.getModules(
                ClassLoaderReference.Primordial))
                .hasSize(1);
        assertThat(scope.getModules(
                ClassLoaderReference.Extension))
                .hasSize(1);
    }

    @Test
    void java17AnalyzerLoadsRealJdk8AndCancelsVanillaZeroOneCfa()
            throws Exception {
        final String configured = System.getenv("TEST_JDK8_HOME");
        assumeTrue(configured != null && !configured.isBlank(),
                "TEST_JDK8_HOME unavailable");
        final Path source = temporary.resolve("src/App.java");
        final Path classes = temporary.resolve("classes");
        Files.createDirectories(source.getParent());
        Files.createDirectories(classes);
        Files.writeString(source, """
                public class App {
                    interface Task { void run(); }
                    static class Worker implements Task {
                        public void run() { helper(); }
                        static void helper() { }
                    }
                    public void execute() {
                        Task task = new Worker();
                        task.run();
                        Runnable lambda = Worker::helper;
                        lambda.run();
                    }
                }
                """);
        final int compileCode = ToolProvider
                .getSystemJavaCompiler().run(null, null, null,
                        "--release", "8", "-d",
                        classes.toString(), source.toString());
        assertThat(compileCode).isZero();
        final JavaRuntimeDescriptor runtime =
                new Jdk8RuntimeProvider().probe(
                        Path.of(configured));
        final ByteArrayOutputStream stderr =
                new ByteArrayOutputStream();
        final PrintStream original = System.err;
        final AnalysisScope scope =
                new JdkAnalysisStage(diagnostics())
                        .prepare(runtime);
        assertThat(ClassHierarchyFactory.make(scope)
                .getNumberOfClasses())
                .isGreaterThan(MINIMUM_JDK_CLASSES);
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                new ModuleId(new ArtifactCoord(
                        "test", "app", "jar", "1"), Path.of(".")),
                ModulePresence.BOTH, classes, List.of(), List.of(),
                List.of(), new ModuleChangeSet(List.of(), List.of()));
        try {
            System.setErr(new PrintStream(stderr));
            assertThatThrownBy(() ->
                    new ModuleCallGraphEngine(
                            diagnostics(), runtime).build(unit, 1L))
                    .isInstanceOf(CallGraphException.class)
                    .hasMessageContaining("timed out");
        } finally {
            System.setErr(original);
        }
        assertThat(stderr.toString()).doesNotContain("got NEW");
    }

    private Path emptyJar(final String name)
            throws Exception {
        final Path path = temporary.resolve(name);
        try (JarOutputStream ignored = new JarOutputStream(
                Files.newOutputStream(path))) {
            ignored.putNextEntry(new JarEntry("marker"));
            ignored.closeEntry();
        }
        return path;
    }

    private DiagnosticCollector diagnostics() {
        return new DiagnosticCollector(
                new PrintStream(new ByteArrayOutputStream()),
                new PrintStream(new ByteArrayOutputStream()));
    }
}
