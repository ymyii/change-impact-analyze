package io.github.dependencyanalysis.impact;


import io.github.dependencyanalysis.impact.refinement.ResultRefinementAlgorithm;
import io.github.dependencyanalysis.impact.refinement.ResultRefinementSelection;
import io.github.dependencyanalysis.impact.refinement.cha.ChaLocalReceiverRefinementSummary;

import io.github.dependencyanalysis.bytecode.AccessTransition;
import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.bytecode.JvmAccess;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.scope.ClassOwnershipIndex;
import io.github.dependencyanalysis.callgraph.model.CodeOrigin;
import io.github.dependencyanalysis.callgraph.entrypoint.EntrypointSelection;
import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphEngine;
import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphSession;
import io.github.dependencyanalysis.callgraph.strategy.WalaReflectionOptions;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.runtime.Jdk8RuntimeProvider;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;
import io.github.dependencyanalysis.testing.TestJarRepositories;
import io.github.dependencyanalysis.jar.IJarRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests duplicate-specific ChangePoint classification. */
class ModuleImpactTracerTest {

    /** Temporary classpath sources. */
    @TempDir
    private Path temporary;

    /** Maximum retained Receiver refinement examples. */
    private static final int RECEIVER_EXAMPLE_LIMIT = 10;

    @Test
    void classifiesOnlyLoserChangePointAsShadowed() throws Exception {
        final Path winner = temporary.resolve("project");
        write(winner, new byte[]{0});
        final Path loser = jar(new byte[]{1});
        final ArtifactCoord loserArtifact = new ArtifactCoord(
                "example", "dependency", "jar", "2");
        final ClassOwnershipIndex ownership = new ClassOwnershipIndex();
        ownership.addDirectory(winner, CodeOrigin.PROJECT);
        try (JarFile handle = new JarFile(loser.toFile())) {
            ownership.addJar(loserArtifact, handle, CodeOrigin.DEPENDENCY);
        }

        assertThat(ModuleImpactTracer.duplicateDisposition(
                bound(loserArtifact), ownership))
                .isEqualTo(ChangePointDisposition.SHADOWED_BY_DUPLICATE);
        assertThat(ModuleImpactTracer.duplicateDisposition(
                bound(new ArtifactCoord(
                        "example", "other", "jar", "2")), ownership))
                .isNull();
    }

    @Test
    void accessNarrowingReportsIllegalPathAndRetainsLegalObservation()
            throws Exception {
        final Path dependencies = compile("access-dependencies", Map.of(
                "same/LegalApi.java", """
                        package same;
                        public class LegalApi {
                            public static void touch() { }
                        }
                        """,
                "dep/IllegalApi.java", """
                        package dep;
                        public class IllegalApi {
                            public static void touch() { }
                        }
                        """), List.of());
        final Path project = compile("access-project", Map.of(
                "same/LegalConsumer.java", """
                        package same;
                        public class LegalConsumer {
                            public void call() { LegalApi.touch(); }
                        }
                        """,
                "app/IllegalConsumer.java", """
                        package app;
                        public class IllegalConsumer {
                            public void call() { dep.IllegalApi.touch(); }
                        }
                        """), List.of(dependencies));
        narrowClass(dependencies.resolve("same/LegalApi.class"));
        narrowClass(dependencies.resolve("dep/IllegalApi.class"));
        final ModuleId moduleId = new ModuleId(new ArtifactCoord(
                "example", "app", "jar", "1"), Path.of("app"));
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "example", "dependency", "jar", "1");
        final ArtifactCoord newArtifact = new ArtifactCoord(
                "example", "dependency", "jar", "2");
        final BoundChangePoint legal = accessPoint(moduleId,
                oldArtifact, newArtifact, "same/LegalApi");
        final BoundChangePoint illegal = accessPoint(moduleId,
                oldArtifact, newArtifact, "dep/IllegalApi");
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId, ModulePresence.BOTH, project,
                List.of(dependencies), List.of(), List.of(),
                new ModuleChangeSet(List.of(legal, illegal), List.of()));
        final DiagnosticLog diagnostics = new DiagnosticLog(
                new PrintStream(new ByteArrayOutputStream()),
                LogVerbosity.INFO);
        final String configured = System.getenv("TEST_JDK8_HOME");
        assertThat(configured).as("TEST_JDK8_HOME").isNotBlank();
        final JavaRuntimeDescriptor runtime =
                new Jdk8RuntimeProvider().probe(Path.of(configured));
        for (CallGraphAlgorithm algorithm : CallGraphAlgorithm.values()) {
            try (IJarRepository repository = TestJarRepositories.empty()) {
                final ModuleCallGraphSession session =
                        new ModuleCallGraphEngine(
                                diagnostics, runtime,
                                EntrypointSelection.allProjectClasses(),
                                algorithm,
                                WalaReflectionOptions.parse("NONE"),
                                repository).build(
                                new ModuleCallGraphInputAdapter().adapt(unit),
                                0L);
                final ModuleImpactQueryResult result =
                        new ModuleImpactTracer(diagnostics)
                                .trace(unit, session, evidence(unit, session));
                final AtomicInteger peakWorkers = new AtomicInteger();
                final ExecutorService queryExecutor =
                        Executors.newFixedThreadPool(2);
                final ModuleImpactQueryResult parallelResult;
                try {
                    parallelResult = new ModuleImpactTracer(
                            diagnostics, queryExecutor, 2,
                            workers -> peakWorkers.accumulateAndGet(
                                    workers, Math::max)).trace(
                                    unit, session,
                                    evidence(unit, session));
                } finally {
                    queryExecutor.shutdownNow();
                }

                assertThat(result.getDispositions())
                        .as(algorithm.identifier())
                        .containsEntry(legal,
                                ChangePointDisposition.ACCESS_REMAINS_VALID)
                        .containsEntry(illegal,
                                ChangePointDisposition.IMPACT_REPORTED);
                assertThat(result.getPaths())
                        .as(algorithm.identifier())
                        .noneMatch(path -> path.getTerminal()
                                .getChangePoint().equals(legal))
                        .anyMatch(path -> path.getTerminal()
                                .getChangePoint().equals(illegal)
                                && path.getTerminal().getEvidenceMechanism()
                                == EvidenceMechanism.BYTECODE_TYPE_REFERENCE);
                assertAccessObservation(result, legal,
                        AccessDecision.ACCESSIBLE,
                        AccessDecisionReason.SAME_RUNTIME_PACKAGE);
                assertAccessObservation(result, illegal,
                        AccessDecision.INACCESSIBLE,
                        AccessDecisionReason.DIFFERENT_RUNTIME_PACKAGE);
                assertThat(parallelResult.getPaths())
                        .as(algorithm.identifier()).isEqualTo(
                                result.getPaths());
                assertThat(parallelResult.getDispositions())
                        .as(algorithm.identifier()).isEqualTo(
                                result.getDispositions());
                assertThat(parallelResult.getObservations())
                        .as(algorithm.identifier()).isEqualTo(
                                result.getObservations());
                assertThat(parallelResult.getLimitations())
                        .as(algorithm.identifier()).isEqualTo(
                                result.getLimitations());
                assertThat(peakWorkers.get()).isBetween(1, 2);
            }
        }
    }

    @Test
    void memberAccessNarrowingPathsWorkAcrossAlgorithms()
            throws Exception {
        final Path dependencies = compile("member-dependencies", Map.of(
                "dep/MemberApi.java", """
                        package dep;
                        public class MemberApi {
                            public int value;
                            public MemberApi() { }
                            public static void staticTouch() { }
                            public void instanceTouch() { }
                        }
                        """), List.of());
        final Path project = compile("member-project", Map.of(
                "app/MemberConsumer.java", """
                        package app;
                        public class MemberConsumer {
                            public int call() {
                                dep.MemberApi api = new dep.MemberApi();
                                dep.MemberApi.staticTouch();
                                api.instanceTouch();
                                return api.value;
                            }
                        }
                        """), List.of(dependencies));
        narrowMembers(dependencies.resolve("dep/MemberApi.class"));
        final ModuleId moduleId = new ModuleId(new ArtifactCoord(
                "example", "app", "jar", "1"), Path.of("app"));
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "example", "dependency", "jar", "1");
        final ArtifactCoord newArtifact = new ArtifactCoord(
                "example", "dependency", "jar", "2");
        final List<BoundChangePoint> points = List.of(
                memberAccessPoint(moduleId, oldArtifact, newArtifact,
                        ChangePointKind.METHOD_ACCESS_NARROWED,
                        "<init>", "()V"),
                memberAccessPoint(moduleId, oldArtifact, newArtifact,
                        ChangePointKind.METHOD_ACCESS_NARROWED,
                        "staticTouch", "()V"),
                memberAccessPoint(moduleId, oldArtifact, newArtifact,
                        ChangePointKind.METHOD_ACCESS_NARROWED,
                        "instanceTouch", "()V"),
                memberAccessPoint(moduleId, oldArtifact, newArtifact,
                        ChangePointKind.FIELD_ACCESS_NARROWED,
                        "value", "I"));
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId, ModulePresence.BOTH, project,
                List.of(dependencies), List.of(), List.of(),
                new ModuleChangeSet(points, List.of()));
        final ByteArrayOutputStream queryLog = new ByteArrayOutputStream();
        final DiagnosticLog diagnostics = new DiagnosticLog(
                new PrintStream(queryLog), LogVerbosity.TRACE);
        final JavaRuntimeDescriptor runtime =
                new Jdk8RuntimeProvider().probe(Path.of(
                        System.getenv("TEST_JDK8_HOME")));

        for (CallGraphAlgorithm algorithm : CallGraphAlgorithm.values()) {
            try (IJarRepository repository = TestJarRepositories.empty()) {
                final ModuleCallGraphSession session =
                        new ModuleCallGraphEngine(
                                diagnostics, runtime,
                                EntrypointSelection.allProjectClasses(),
                                algorithm,
                                WalaReflectionOptions.parse("NONE"),
                                repository).build(
                                new ModuleCallGraphInputAdapter().adapt(unit),
                                0L);
                final ModuleImpactQueryResult result =
                        new ModuleImpactTracer(diagnostics)
                                .trace(unit, session, evidence(unit, session));

                for (BoundChangePoint point : points) {
                    assertThat(result.getDispositions())
                            .as(algorithm.identifier())
                            .containsEntry(point,
                                    ChangePointDisposition.IMPACT_REPORTED);
                }
                assertThat(result.getPaths().stream()
                        .map(path -> path.getTerminal()
                                .getChangePoint()).toList())
                        .as(algorithm.identifier())
                        .containsAll(points);
                for (BoundChangePoint point : points) {
                    assertAccessObservation(result, point,
                            AccessDecision.INACCESSIBLE,
                            AccessDecisionReason.PRIVATE_MEMBER);
                }
            }
        }
        final List<String> queryStarts = queryLog.toString(
                StandardCharsets.UTF_8).lines()
                .filter(line -> line.contains("[impact-query]"))
                .filter(line -> line.contains("Task started; seeds="))
                .toList();
        assertThat(queryStarts)
                .hasSize(CallGraphAlgorithm.values().length)
                .allMatch(line -> line.contains("workers=1"));
        assertThat(queryStarts).anyMatch(line -> line.contains(
                "seeds=4; queryNodes=1; workers=1"));
        assertThat(queryLog.toString(StandardCharsets.UTF_8).lines()
                .filter(line -> line.contains(
                        "event=query-node-completed"))
                .filter(line -> line.contains("evidenceSeeds=4"))
                .toList()).isNotEmpty();
    }

    @Test
    void classAccessNarrowingUsesDynamicHandleOwnerAcrossAlgorithms()
            throws Exception {
        final Path dependencies = compile("dynamic-access-dependency", Map.of(
                "dyn/HandleOwner.java", """
                        package dyn;
                        public class HandleOwner {
                            public static void target() { }
                        }
                        """), List.of());
        final Path project = compile("dynamic-access-project", Map.of(
                "app/DynamicConsumer.java", """
                        package app;
                        public class DynamicConsumer {
                            public Runnable task() {
                                return dyn.HandleOwner::target;
                            }
                            public void execute() { task().run(); }
                        }
                        """), List.of(dependencies));
        narrowClass(dependencies.resolve("dyn/HandleOwner.class"));
        final ModuleId moduleId = new ModuleId(new ArtifactCoord(
                "example", "app", "jar", "1"), Path.of("app"));
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "example", "dependency", "jar", "1");
        final ArtifactCoord newArtifact = new ArtifactCoord(
                "example", "dependency", "jar", "2");
        final BoundChangePoint point = accessPoint(moduleId,
                oldArtifact, newArtifact, "dyn/HandleOwner");
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId, ModulePresence.BOTH, project,
                List.of(dependencies), List.of(), List.of(),
                new ModuleChangeSet(List.of(point), List.of()));
        final DiagnosticLog diagnostics = new DiagnosticLog(
                new PrintStream(new ByteArrayOutputStream()),
                LogVerbosity.INFO);
        final JavaRuntimeDescriptor runtime =
                new Jdk8RuntimeProvider().probe(Path.of(
                        System.getenv("TEST_JDK8_HOME")));

        for (CallGraphAlgorithm algorithm : CallGraphAlgorithm.values()) {
            try (IJarRepository repository = TestJarRepositories.empty()) {
                final ModuleCallGraphSession session =
                        new ModuleCallGraphEngine(
                                diagnostics, runtime,
                                EntrypointSelection.allProjectClasses(),
                                algorithm,
                                WalaReflectionOptions.parse("NONE"),
                                repository).build(
                                new ModuleCallGraphInputAdapter().adapt(unit),
                                0L);
                final ModuleImpactQueryResult result =
                        new ModuleImpactTracer(diagnostics)
                                .trace(unit, session, evidence(unit, session));

                assertThat(result.getDispositions())
                        .as(algorithm.identifier())
                        .containsEntry(point,
                                ChangePointDisposition.IMPACT_REPORTED);
                assertThat(result.getPaths())
                        .as(algorithm.identifier()).anySatisfy(path -> {
                            assertThat(path.getTerminal().getChangePoint())
                                    .isEqualTo(point);
                            assertThat(path.getTerminal()
                                    .getEvidenceMechanism()).isEqualTo(
                                    EvidenceMechanism.INVOKEDYNAMIC_HANDLE);
                            assertThat(path.getTerminal().getImpactEvidence())
                                    .isInstanceOf(ReferenceEvidence.class);
                        });
            }
        }
    }

    @Test
    void chaLocalReceiverInferencePrunesOnlyInfeasibleCallerEdge()
            throws Exception {
        final Path dependencies = compile("receiver-dependency", Map.of(
                "dep/ChangedApi.java", """
                        package dep;
                        public class ChangedApi {
                            public static void changed() { }
                        }
                        """), List.of());
        final Path project = compile("receiver-project", Map.of(
                "app/DispatchTarget.java", """
                        package app;
                        public interface DispatchTarget { void call(); }
                        """,
                "app/ChangedReceiver.java", """
                        package app;
                        public class ChangedReceiver
                                implements DispatchTarget {
                            public void call() { dep.ChangedApi.changed(); }
                        }
                        """,
                "app/UnrelatedReceiver.java", """
                        package app;
                        public class UnrelatedReceiver
                                implements DispatchTarget {
                            public void call() { }
                        }
                        """,
                "app/ReceiverUseCase.java", """
                        package app;
                        public class ReceiverUseCase {
                            public void reachable() {
                                DispatchTarget value = new ChangedReceiver();
                                value.call();
                            }
                            public void falsePositive() {
                                DispatchTarget value =
                                        new UnrelatedReceiver();
                                value.call();
                            }
                            public void incompatibleCast() {
                                Object value = new UnrelatedReceiver();
                                ((DispatchTarget) value).call();
                            }
                            public void nullOnly() {
                                DispatchTarget value = null;
                                value.call();
                            }
                            public void nullable(boolean changed) {
                                DispatchTarget value = changed
                                        ? new ChangedReceiver() : null;
                                if (value != null) { value.call(); }
                            }
                            public void unknown(DispatchTarget value) {
                                value.call();
                            }
                            public void mixed(boolean useNew,
                                    DispatchTarget input) {
                                DispatchTarget value = useNew
                                        ? new UnrelatedReceiver() : input;
                                value.call();
                            }
                        }
                        """), List.of(dependencies));
        final ModuleId moduleId = new ModuleId(new ArtifactCoord(
                "example", "app", "jar", "1"), Path.of("app"));
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "example", "dependency", "jar", "1");
        final ArtifactCoord newArtifact = new ArtifactCoord(
                "example", "dependency", "jar", "2");
        final BoundChangePoint point = bodyPoint(moduleId,
                oldArtifact, newArtifact, "dep/ChangedApi", "changed");
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId, ModulePresence.BOTH, project,
                List.of(dependencies), List.of(), List.of(),
                new ModuleChangeSet(List.of(point), List.of()));
        final DiagnosticLog diagnostics = new DiagnosticLog(
                new PrintStream(new ByteArrayOutputStream()),
                LogVerbosity.TRACE);
        final JavaRuntimeDescriptor runtime =
                new Jdk8RuntimeProvider().probe(Path.of(
                        System.getenv("TEST_JDK8_HOME")));

        try (IJarRepository repository = TestJarRepositories.empty()) {
            final ModuleCallGraphSession session =
                    new ModuleCallGraphEngine(
                            diagnostics, runtime,
                            EntrypointSelection.allProjectClasses(),
                            CallGraphAlgorithm.CHA,
                            WalaReflectionOptions.parse("NONE"),
                            repository).build(
                            new ModuleCallGraphInputAdapter().adapt(unit),
                            0L);
            final long originalEdges = session.getStats().edgeCount();
            final ModuleImpactQueryResult original = new ModuleImpactTracer(
                    diagnostics).trace(
                            unit, session, evidence(unit, session));
            final ModuleImpactQueryResult refined = new ModuleImpactTracer(
                    diagnostics, ResultRefinementSelection.of(
                    ResultRefinementAlgorithm
                            .CHA_LOCAL_RECEIVER_INFERENCE))
                    .trace(unit, session, evidence(unit, session));

            assertThat(affectedNames(original)).contains("reachable",
                    "falsePositive", "incompatibleCast", "nullOnly",
                    "nullable", "unknown", "mixed", "call");
            assertThat(affectedNames(refined)).contains("reachable",
                    "nullable", "unknown", "mixed", "call")
                    .doesNotContain("falsePositive", "incompatibleCast",
                            "nullOnly");
            assertThat(refined.getReceiverRefinement().status()).isEqualTo(
                    ChaLocalReceiverRefinementSummary.Status.APPLIED);
            assertThat(refined.getReceiverRefinement().metrics()
                    .prunedEdges()).isPositive();
            assertThat(refined.getReceiverRefinement().metrics()
                    .exactResolutions()).isPositive();
            assertThat(refined.getReceiverRefinement().metrics()
                    .noNormalTargetResolutions()).isPositive();
            assertThat(refined.getReceiverRefinement().metrics()
                    .unknownResolutions()).isPositive();
            assertThat(refined.getReceiverRefinement().examples())
                    .hasSizeLessThanOrEqualTo(RECEIVER_EXAMPLE_LIMIT)
                    .anyMatch(example -> example.decision().equals(
                            "proven-infeasible"));
            assertThat(session.getStats().edgeCount()).isEqualTo(
                    originalEdges);
        }
    }

    private List<String> affectedNames(
            final ModuleImpactQueryResult result) {
        return result.getPaths().stream()
                .map(path -> path.getAffectedMethod().name()).toList();
    }

    private BoundChangePoint bound(final ArtifactCoord newArtifact) {
        final ModuleId moduleId = new ModuleId(new ArtifactCoord(
                "example", "app", "jar", "1"), Path.of("app"));
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "example", "dependency", "jar", "1");
        return new BoundChangePoint(new DependencyUpgradeKey(
                moduleId, DependencyScope.COMPILE,
                oldArtifact, newArtifact),
                new ChangePoint(newArtifact,
                        ChangePointKind.METHOD_REMOVED,
                        "sample/Duplicate", "removed", "()V",
                        null, null));
    }

    private BoundChangePoint accessPoint(
            final ModuleId moduleId,
            final ArtifactCoord oldArtifact,
            final ArtifactCoord newArtifact,
            final String owner) {
        return new BoundChangePoint(new DependencyUpgradeKey(
                moduleId, DependencyScope.COMPILE,
                oldArtifact, newArtifact),
                ChangePoint.accessNarrowed(newArtifact,
                        ChangePointKind.CLASS_ACCESS_NARROWED,
                        owner, null, null,
                        new AccessTransition(JvmAccess.PUBLIC,
                                JvmAccess.PACKAGE_PRIVATE)));
    }

    private BoundChangePoint memberAccessPoint(
            final ModuleId moduleId,
            final ArtifactCoord oldArtifact,
            final ArtifactCoord newArtifact,
            final ChangePointKind kind,
            final String name,
            final String descriptor) {
        return new BoundChangePoint(new DependencyUpgradeKey(
                moduleId, DependencyScope.COMPILE,
                oldArtifact, newArtifact),
                ChangePoint.accessNarrowed(newArtifact, kind,
                        "dep/MemberApi", name, descriptor,
                        new AccessTransition(JvmAccess.PUBLIC,
                                JvmAccess.PRIVATE)));
    }

    private BoundChangePoint bodyPoint(
            final ModuleId moduleId,
            final ArtifactCoord oldArtifact,
            final ArtifactCoord newArtifact,
            final String owner,
            final String name) {
        return new BoundChangePoint(new DependencyUpgradeKey(
                moduleId, DependencyScope.COMPILE,
                oldArtifact, newArtifact),
                new ChangePoint(newArtifact,
                        ChangePointKind.METHOD_BODY_CHANGED,
                        owner, name, "()V", "old", "new"));
    }

    private void assertAccessObservation(
            final ModuleImpactQueryResult result,
            final BoundChangePoint point,
            final AccessDecision decision,
            final AccessDecisionReason reason) {
        assertThat(result.getObservations().get(point))
                .anySatisfy(observation -> assertThat(observation)
                        .isInstanceOfSatisfying(
                                AccessReferenceEvidence.class, evidence -> {
                                    assertThat(evidence.decision())
                                            .isEqualTo(decision);
                                    assertThat(evidence.reason())
                                            .isEqualTo(reason);
                                    assertThat(evidence.transition()
                                            .stableKey())
                                            .isEqualTo(point.getChangePoint()
                                                    .getAccessTransition()
                                                    .orElseThrow()
                                                    .stableKey());
                                }));
    }

    private Path compile(
            final String fixture,
            final Map<String, String> sources,
            final List<Path> classpath) throws Exception {
        final Path sourceRoot = temporary.resolve(fixture + "-src");
        final Path classes = temporary.resolve(fixture + "-classes");
        Files.createDirectories(classes);
        final List<Path> sourceFiles = new ArrayList<>();
        for (Map.Entry<String, String> source : sources.entrySet()) {
            final Path file = sourceRoot.resolve(source.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, source.getValue());
            sourceFiles.add(file);
        }
        sourceFiles.sort(Comparator.comparing(Path::toString));
        final List<String> arguments = new ArrayList<>(List.of(
                "--release", "8", "-d", classes.toString()));
        if (!classpath.isEmpty()) {
            arguments.add("-classpath");
            arguments.add(String.join(
                    System.getProperty("path.separator"),
                    classpath.stream().map(Path::toString).toList()));
        }
        sourceFiles.forEach(path -> arguments.add(path.toString()));
        assertThat(ToolProvider.getSystemJavaCompiler().run(
                null, null, null, arguments.toArray(String[]::new)))
                .isZero();
        return classes;
    }

    private void narrowClass(final Path classFile) throws Exception {
        final ClassReader reader = new ClassReader(Files.readAllBytes(
                classFile));
        final ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visit(
                    final int version,
                    final int access,
                    final String name,
                    final String signature,
                    final String superName,
                    final String[] interfaces) {
                super.visit(version, access & ~Opcodes.ACC_PUBLIC,
                        name, signature, superName, interfaces);
            }
        }, 0);
        Files.write(classFile, writer.toByteArray());
    }

    private void narrowMembers(final Path classFile) throws Exception {
        final ClassReader reader = new ClassReader(Files.readAllBytes(
                classFile));
        final ClassWriter writer = new ClassWriter(0);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public FieldVisitor visitField(
                    final int access,
                    final String name,
                    final String descriptor,
                    final String signature,
                    final Object value) {
                final int narrowed = "value".equals(name)
                        ? privateAccess(access) : access;
                return super.visitField(narrowed, name, descriptor,
                        signature, value);
            }

            @Override
            public MethodVisitor visitMethod(
                    final int access,
                    final String name,
                    final String descriptor,
                    final String signature,
                    final String[] exceptions) {
                final int narrowed = "<init>".equals(name)
                        || "staticTouch".equals(name)
                        || "instanceTouch".equals(name)
                        ? privateAccess(access) : access;
                return super.visitMethod(narrowed, name, descriptor,
                        signature, exceptions);
            }
        }, 0);
        Files.write(classFile, writer.toByteArray());
    }

    private int privateAccess(final int access) {
        return access & ~Opcodes.ACC_PUBLIC & ~Opcodes.ACC_PROTECTED
                | Opcodes.ACC_PRIVATE;
    }

    private Path jar(final byte[] content) throws Exception {
        final Path result = temporary.resolve("dependency.jar");
        try (JarOutputStream output = new JarOutputStream(
                Files.newOutputStream(result))) {
            output.putNextEntry(new JarEntry("sample/Duplicate.class"));
            output.write(content);
            output.closeEntry();
        }
        return result;
    }

    private void write(final Path root, final byte[] content)
            throws Exception {
        final Path file = root.resolve("sample/Duplicate.class");
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }

    private ChangePointEvidenceIndex evidence(
            final ModuleAnalysisUnit unit,
            final ModuleCallGraphSession session) {
        return new ChangePointEvidenceCollector().collect(
                unit, session.getGraph(), session.getOwnership(),
                session.getDynamicEvidence(), StructuralReferenceIndex.empty(),
                session.getStrategyCapabilities(), session::isBodyAvailable);
    }
}
