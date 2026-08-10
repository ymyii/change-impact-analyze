package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.ipa.callgraph.CGNode;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.dependency.ModuleDependencyOccurrenceGraph;
import io.github.dependencyanalysis.dependency.ResolvedArtifact;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;
import io.github.dependencyanalysis.impact.BoundChangePoint;
import io.github.dependencyanalysis.impact.DependencyAnalysisScopeMode;
import io.github.dependencyanalysis.impact.DependencyUpgradeKey;
import io.github.dependencyanalysis.impact.ModuleAnalysisUnit;
import io.github.dependencyanalysis.impact.ModuleChangeSet;
import io.github.dependencyanalysis.impact.ModuleChangedPathSelection;
import io.github.dependencyanalysis.impact.ModuleDependencyInputs;
import io.github.dependencyanalysis.impact.ModuleId;
import io.github.dependencyanalysis.impact.ModulePresence;
import io.github.dependencyanalysis.jar.IJarRepository;
import io.github.dependencyanalysis.runtime.Jdk8RuntimeProvider;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;
import io.github.dependencyanalysis.testing.TestJarRepositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** End-to-end tests for the external dependency method-body boundary. */
class DependencyBodyBoundaryTest {

    /** Fixed-point timeout per algorithm. */
    private static final long TIMEOUT_SECONDS = 30L;

    /** Direct, array and varargs dangerous transfers. */
    private static final int DANGEROUS_TRANSFER_COUNT = 3;

    /** Test filesystem root. */
    @TempDir
    private Path temporary;

    /** Selected changed artifact. */
    private final ArtifactCoord api = artifact("scenario-api");

    /** Unselected dangerous sink. */
    private final ArtifactCoord sink = artifact("external-sink");

    /** Unselected Object-returning factory. */
    private final ArtifactCoord factory = artifact("external-factory");

    /** Unselected ordinary no-op dependency. */
    private final ArtifactCoord plain = artifact("external-plain");

    /** Compiled fixture. */
    private Fixture fixture;

    @BeforeEach
    void compileFixture() throws Exception {
        final Path apiClasses = compile("api", Map.of(
                "api/ChangedValue.java", """
                        package api;
                        public class ChangedValue {
                            public void changed() { }
                        }
                        """,
                "api/IncludedType.java", """
                        package api;
                        public class IncludedType {
                            public void afterFactory() {
                                new ChangedValue().changed();
                            }
                        }
                        """), List.of());
        final Path sinkClasses = compile("sink", Map.of(
                "vendor/Sink.java", """
                        package vendor;
                        public class Sink {
                            public static void accept(
                                    api.ChangedValue value) {
                                value.changed();
                            }
                            public static void acceptArray(
                                    api.ChangedValue[] values) {
                                values[0].changed();
                            }
                            public static void acceptVarargs(
                                    api.ChangedValue... values) {
                                values[0].changed();
                            }
                        }
                        """), List.of(apiClasses));
        final Path factoryClasses = compile("factory", Map.of(
                "vendor/Factory.java", """
                        package vendor;
                        public class Factory {
                            public static Object create() {
                                return new api.IncludedType();
                            }
                        }
                        """), List.of(apiClasses));
        final Path plainClasses = compile("plain", Map.of(
                "vendor/Plain.java", """
                        package vendor;
                        public class Plain {
                            static { helper(); }
                            public Plain() { helper(); }
                            public static void call() { helper(); }
                            public static int primitive() {
                                helper(); return 42;
                            }
                            public static Object reference() {
                                helper(); return new Object();
                            }
                            private static void helper() { }
                        }
                        """), List.of());
        final Path project = compile("project", Map.of(
                "app/App.java", """
                        package app;
                        public class App {
                            public static void entry() {
                                api.ChangedValue changed =
                                        new api.ChangedValue();
                                vendor.Sink.accept(changed);
                                vendor.Sink.acceptArray(
                                        new api.ChangedValue[] {changed});
                                vendor.Sink.acceptVarargs(changed);
                                api.IncludedType created =
                                        (api.IncludedType)
                                        vendor.Factory.create();
                                created.afterFactory();
                                vendor.Plain.call();
                                new vendor.Plain();
                                int ignored = vendor.Plain.primitive();
                                Object reference = vendor.Plain.reference();
                                if (ignored == 42 && reference != null) {
                                    vendor.Plain.call();
                                }
                            }
                        }
                        """), List.of(apiClasses, sinkClasses,
                        factoryClasses, plainClasses));
        final Map<ArtifactCoord, Path> jars = new LinkedHashMap<>();
        jars.put(api, jar("api", apiClasses));
        jars.put(sink, jar("sink", sinkClasses));
        jars.put(factory, jar("factory", factoryClasses));
        jars.put(plain, jar("plain", plainClasses));
        fixture = new Fixture(project, jars);
    }

    @Test
    void changedPathsSharesBoundaryAcrossAllAlgorithms() throws Exception {
        try (IJarRepository repository = repository()) {
            for (CallGraphAlgorithm algorithm : CallGraphAlgorithm.values()) {
                final ModuleCallGraphSession session = build(repository,
                        DependencyAnalysisScopeMode.CHANGED_PATHS, algorithm);
                final DependencyBodyBoundaryMetadata boundary =
                        session.getDependencyBoundary();

                assertThat(boundary.dangerousTransfers())
                        .as(algorithm.identifier())
                        .hasSize(DANGEROUS_TRANSFER_COUNT)
                        .allMatch(value -> value.calleeArtifact()
                                .equals(sink));
                assertThat(boundary.factories())
                        .as(algorithm.identifier()).hasSize(1);
                assertThat(boundary.factories().get(0).calleeArtifact())
                        .isEqualTo(factory);
                assertThat(boundary.noOpMethodNodes()).isPositive();
                assertThat(boundary.factoryMethodNodes()).isPositive();
                assertThat(hasEdge(session, "api/IncludedType",
                        "afterFactory", "api/ChangedValue", "changed"))
                        .as(algorithm.identifier()).isTrue();
                assertThat(hasEdge(session, "vendor/Sink", "accept",
                        "api/ChangedValue", "changed"))
                        .as(algorithm.identifier()).isFalse();
                assertThat(hasNode(session, "vendor/Plain", "call"))
                        .isTrue();
                assertThat(hasNode(session, "vendor/Plain", "<init>"))
                        .isTrue();
                assertThat(hasNode(session, "vendor/Plain", "<clinit>"))
                        .isTrue();
                assertThat(hasNode(session, "vendor/Plain", "primitive"))
                        .isTrue();
                assertThat(hasNode(session, "vendor/Plain", "reference"))
                        .isTrue();
                assertThat(hasNode(session, "vendor/Plain", "helper"))
                        .isFalse();
            }
        }
    }

    @Test
    void fullUsesRealBodiesWithoutBoundaryEvidence() throws Exception {
        try (IJarRepository repository = repository()) {
            final ModuleCallGraphSession session = build(repository,
                    DependencyAnalysisScopeMode.FULL,
                    CallGraphAlgorithm.ZERO_CFA);
            final DependencyBodyBoundaryMetadata boundary =
                    session.getDependencyBoundary();

            assertThat(boundary.dangerousTransfers()).isEmpty();
            assertThat(boundary.factories()).isEmpty();
            assertThat(boundary.noOpMethodNodes()).isZero();
            assertThat(boundary.factoryMethodNodes()).isZero();
            assertThat(hasEdge(session, "vendor/Sink", "accept",
                    "api/ChangedValue", "changed")).isTrue();
            assertThat(hasNode(session, "vendor/Plain", "helper")).isTrue();
        }
    }

    private ModuleCallGraphSession build(
            final IJarRepository repository,
            final DependencyAnalysisScopeMode mode,
            final CallGraphAlgorithm algorithm) {
        final ModuleId module = new ModuleId(artifact("application"),
                Path.of("application"));
        final BoundChangePoint point = new BoundChangePoint(
                new DependencyUpgradeKey(module, DependencyScope.COMPILE,
                        new ArtifactCoord("test", "scenario-api", "jar",
                                "1"), api),
                new ChangePoint(api, ChangePointKind.METHOD_BODY_CHANGED,
                        "api/ChangedValue", "changed", "()V",
                        "old", "new"));
        final ModuleDependencyOccurrenceGraph graph = graph(module);
        final ModuleChangedPathSelection selection =
                ModuleChangedPathSelection.plan(graph, Set.of(api), mode);
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(module,
                ModulePresence.BOTH, fixture.projectClasses(), List.of(),
                new ModuleDependencyInputs(List.of(api, sink, factory, plain),
                        List.of(), selection),
                new ModuleChangeSet(List.of(point), List.of()));
        final JavaRuntimeDescriptor runtime = new Jdk8RuntimeProvider()
                .probe(Path.of(System.getenv("TEST_JDK8_HOME")));
        return new ModuleCallGraphEngine(diagnostics(), runtime,
                EntrypointSelection.parse(List.of("app/App"), List.of()),
                algorithm, WalaReflectionOptions.parse("NONE"), repository)
                .build(unit, TIMEOUT_SECONDS);
    }

    private ModuleDependencyOccurrenceGraph graph(final ModuleId module) {
        final List<ModuleDependencyOccurrenceGraph.Occurrence> nodes =
                new ArrayList<>();
        nodes.add(new ModuleDependencyOccurrenceGraph.Occurrence(
                "root", module.getCoordinate(), null, true, false));
        final List<ModuleDependencyOccurrenceGraph.Edge> edges =
                new ArrayList<>();
        int index = 0;
        for (ArtifactCoord artifact : List.of(api, sink, factory, plain)) {
            final String id = "dependency-" + index++;
            nodes.add(new ModuleDependencyOccurrenceGraph.Occurrence(
                    id, artifact, DependencyScope.COMPILE, false, false));
            edges.add(new ModuleDependencyOccurrenceGraph.Edge("root", id));
        }
        return new ModuleDependencyOccurrenceGraph("root", nodes, edges);
    }

    private IJarRepository repository() throws Exception {
        return TestJarRepositories.of(fixture.jars().entrySet().stream()
                .map(value -> new ResolvedArtifact(
                        value.getKey(), value.getValue())).toList());
    }

    private Path compile(
            final String name,
            final Map<String, String> sources,
            final List<Path> classpath) throws Exception {
        final Path sourceRoot = temporary.resolve(name + "-sources");
        final Path classes = temporary.resolve(name + "-classes");
        Files.createDirectories(sourceRoot);
        Files.createDirectories(classes);
        final List<String> arguments = new ArrayList<>(List.of(
                "--release", "8", "-d", classes.toString()));
        if (!classpath.isEmpty()) {
            arguments.add("-classpath");
            arguments.add(String.join(System.getProperty("path.separator"),
                    classpath.stream().map(Path::toString).toList()));
        }
        for (Map.Entry<String, String> source : sources.entrySet()) {
            final Path path = sourceRoot.resolve(source.getKey());
            Files.createDirectories(path.getParent());
            Files.writeString(path, source.getValue());
            arguments.add(path.toString());
        }
        assertThat(ToolProvider.getSystemJavaCompiler().run(
                null, null, null, arguments.toArray(String[]::new))).isZero();
        return classes;
    }

    private Path jar(final String name, final Path classes) throws Exception {
        final Path jar = temporary.resolve(name + ".jar");
        try (JarOutputStream output = new JarOutputStream(
                Files.newOutputStream(jar));
             java.util.stream.Stream<Path> files = Files.walk(classes)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                output.putNextEntry(new JarEntry(classes.relativize(file)
                        .toString().replace('\\', '/')));
                Files.copy(file, output);
                output.closeEntry();
            }
        }
        return jar;
    }

    private boolean hasNode(
            final ModuleCallGraphSession session,
            final String owner,
            final String method) {
        return session.getGraph().stream().anyMatch(node -> owner(node)
                .equals(owner) && node.getMethod().getName().toString()
                .equals(method));
    }

    private boolean hasEdge(
            final ModuleCallGraphSession session,
            final String callerOwner,
            final String callerMethod,
            final String calleeOwner,
            final String calleeMethod) {
        for (CGNode caller : session.getGraph()) {
            if (!owner(caller).equals(callerOwner)
                    || !caller.getMethod().getName().toString()
                    .equals(callerMethod)) {
                continue;
            }
            final java.util.Iterator<CGNode> successors =
                    session.getGraph().getSuccNodes(caller);
            while (successors.hasNext()) {
                final CGNode callee = successors.next();
                if (owner(callee).equals(calleeOwner)
                        && callee.getMethod().getName().toString()
                        .equals(calleeMethod)) {
                    return true;
                }
            }
        }
        return false;
    }

    private String owner(final CGNode node) {
        final String name = node.getMethod().getDeclaringClass().getName()
                .toString();
        return name.startsWith("L") ? name.substring(1) : name;
    }

    private DiagnosticLog diagnostics() {
        return new DiagnosticLog(new PrintStream(
                new ByteArrayOutputStream()), LogVerbosity.INFO);
    }

    private ArtifactCoord artifact(final String name) {
        return new ArtifactCoord("test", name, "jar", "2");
    }

    /**
     * @param projectClasses PROJECT class directory
     * @param jars external artifact JARs
     */
    private record Fixture(
            Path projectClasses,
            Map<ArtifactCoord, Path> jars) {
    }
}
