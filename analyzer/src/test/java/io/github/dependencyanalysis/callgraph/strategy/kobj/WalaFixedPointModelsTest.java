package io.github.dependencyanalysis.callgraph.strategy.kobj;

import io.github.dependencyanalysis.impact.ChangePointEvidenceCollector;
import io.github.dependencyanalysis.impact.ChangePointEvidenceIndex;
import io.github.dependencyanalysis.impact.CoverageLimitation;
import io.github.dependencyanalysis.impact.StructuralReferenceIndex;

import io.github.dependencyanalysis.impact.ModuleCallGraphInputAdapter;

import io.github.dependencyanalysis.callgraph.engine.CallGraphException;
import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphEngine;
import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphSession;
import io.github.dependencyanalysis.callgraph.entrypoint.EntrypointClassIndex;
import io.github.dependencyanalysis.callgraph.entrypoint.EntrypointSelection;
import io.github.dependencyanalysis.callgraph.jdk.JdkModelSelection;
import io.github.dependencyanalysis.callgraph.protocol.invokedynamic.DynamicReferenceKind;
import io.github.dependencyanalysis.callgraph.protocol.invokedynamic.InvokeDynamicBootstrapKey;
import io.github.dependencyanalysis.callgraph.protocol.invokedynamic.InvokeDynamicBootstrapModel;
import io.github.dependencyanalysis.callgraph.protocol.invokedynamic.InvokeDynamicBootstrapModelRegistry;
import io.github.dependencyanalysis.callgraph.protocol.invokedynamic.InvokeDynamicModelResult;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphConfiguration;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphModelConfiguration;
import io.github.dependencyanalysis.callgraph.strategy.WalaReflectionOptions;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.ContextItem;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallStringContextSelector;
import com.ibm.wala.ipa.callgraph.propagation.cfa.AllocationString;
import com.ibm.wala.ipa.callgraph.propagation.cfa.nObjContextSelector;
import com.ibm.wala.ipa.summaries.BypassSyntheticClass;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyEvidenceFixtures;
import io.github.dependencyanalysis.dependency.DependencyNode;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.dependency.ModuleDependencyEvidence;
import io.github.dependencyanalysis.dependency.ModuleDependencyOccurrenceGraph;
import io.github.dependencyanalysis.dependency.ResolvedArtifact;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;
import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.bytecode.MemberDescriptors;
import io.github.dependencyanalysis.impact.BoundChangePoint;
import io.github.dependencyanalysis.impact.DependencyAnalysisScopeMode;
import io.github.dependencyanalysis.impact.DependencyUpgradeKey;
import io.github.dependencyanalysis.impact.EvidenceMechanism;
import io.github.dependencyanalysis.impact.ModuleAnalysisUnit;
import io.github.dependencyanalysis.impact.ModuleAnalysisReason;
import io.github.dependencyanalysis.impact.ModuleChangeSet;
import io.github.dependencyanalysis.impact.ModuleDependencyInputs;
import io.github.dependencyanalysis.impact.ModuleId;
import io.github.dependencyanalysis.impact.ModuleImpactQueryResult;
import io.github.dependencyanalysis.impact.ModuleImpactTracer;
import io.github.dependencyanalysis.impact.ModulePresence;
import io.github.dependencyanalysis.jar.IJarRepository;
import io.github.dependencyanalysis.runtime.Jdk8RuntimeProvider;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;
import io.github.dependencyanalysis.testing.TestJarRepositories;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** End-to-end fixed-point model tests against the configured JDK 8. */
class WalaFixedPointModelsTest {

    /** Keeps a malformed model from exhausting the test JVM. */
    private static final long GRAPH_TIMEOUT_SECONDS = 60L;

    /** Constructor receiver plus entry receiver and five arguments. */
    private static final int DECLARED_PARAMETER_CANDIDATES = 7;

    /** Concrete types that must not expand declared entrypoint parameters. */
    private static final int UNUSED_IMPLEMENTATION_COUNT = 32;

    /** Invalid Class.forName literals in the CHA fixture. */
    private static final int INVALID_CLASS_NAME_LITERAL_COUNT = 3;

    /** Non-private roots in the defensive private-declaration fixture. */
    private static final int PRIVATE_FILTER_ROOT_COUNT = 3;

    /** Custom Java 8 bootstrap descriptor. */
    private static final String BOOTSTRAP_DESCRIPTOR =
            "(Ljava/lang/invoke/MethodHandles$Lookup;"
                    + "Ljava/lang/String;Ljava/lang/invoke/MethodType;)"
                    + "Ljava/lang/invoke/CallSite;";

    /** Temporary source and class roots. */
    @TempDir
    private Path temporary;

    @Test
    void entrypointParametersUseOneDeclaredTypePlaceholder()
            throws Exception {
        final StringBuilder implementations = new StringBuilder();
        for (int index = 0; index < UNUSED_IMPLEMENTATION_COUNT; index++) {
            implementations.append("static class ContractImpl")
                    .append(index)
                    .append(" implements Contract { public void run() { } }")
                    .append(System.lineSeparator());
            implementations.append("static class BaseImpl")
                    .append(index)
                    .append(" extends Base { void run() { } }")
                    .append(System.lineSeparator());
        }
        final Path classes = compile("EntrypointParameters", """
                public class EntrypointParameters {
                    interface Contract { void run(); }
                    abstract static class Base {
                        abstract void run();
                    }
                    %s
                    static class Concrete { }
                    public void entry(
                            Contract contract,
                            Base base,
                            Concrete concrete,
                            int primitive,
                            Concrete[] array) {
                        contract.run();
                        base.run();
                    }
                }
                """.formatted(implementations));
        final EntrypointSelection selection = EntrypointSelection.parse(
                List.of("EntrypointParameters"), List.of());

        try (IJarRepository repository = TestJarRepositories.empty()) {
            final ModuleCallGraphSession session = build(
                    classes, repository, List.of(),
                    InvokeDynamicBootstrapModelRegistry.jdk8Defaults(),
                    selection);

            assertThat(session.getSelectedEntrypointClassCount()).isEqualTo(1);
            assertThat(session.getEntrypointCount()).isEqualTo(2);
            assertThat(session.getParameterCandidateCount())
                    .isEqualTo(DECLARED_PARAMETER_CANDIDATES);
            assertThat(session.getHierarchy()).anySatisfy(type -> {
                assertThat(type).isInstanceOf(BypassSyntheticClass.class);
                final BypassSyntheticClass synthetic =
                        (BypassSyntheticClass) type;
                assertThat(synthetic.getRealType().getName().toString())
                        .isEqualTo("LEntrypointParameters$Contract");
            });
            assertThat(session.getHierarchy()).anySatisfy(type -> {
                assertThat(type).isInstanceOf(BypassSyntheticClass.class);
                final BypassSyntheticClass synthetic =
                        (BypassSyntheticClass) type;
                assertThat(synthetic.getRealType().getName().toString())
                        .isEqualTo("LEntrypointParameters$Base");
            });
            assertThat(session.getGraph()).noneMatch(node ->
                    owner(node).startsWith(
                            "EntrypointParameters$ContractImpl")
                            || owner(node).startsWith(
                            "EntrypointParameters$BaseImpl"));
        }
    }

    @Test
    void indexedEntrypointClassMustResolveFromCurrentHierarchy()
            throws Exception {
        final Path classes = compile("IndexedEntrypoint", """
                public class IndexedEntrypoint {
                    public void run() { }
                }
                """);
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId(), ModulePresence.BOTH, classes, List.of(),
                List.of(), List.of(),
                new ModuleChangeSet(List.of(), List.of()));
        final JavaRuntimeDescriptor runtime = new Jdk8RuntimeProvider()
                .probe(Path.of(System.getenv("TEST_JDK8_HOME")));

        try (IJarRepository repository = TestJarRepositories.empty()) {
            final ModuleCallGraphEngine engine = new ModuleCallGraphEngine(
                    diagnostics(), runtime,
                    EntrypointSelection.allProjectClasses(),
                    CallGraphAlgorithm.defaultAlgorithm(),
                    WalaReflectionOptions.defaultOptions(),
                    JdkModelSelection.NONE, repository);

            assertThatThrownBy(() -> engine.build(
                    new ModuleCallGraphInputAdapter().adapt(unit),
                    new EntrypointClassIndex(
                            List.of("MissingEntrypoint"), 1),
                    GRAPH_TIMEOUT_SECONDS))
                    .isInstanceOf(CallGraphException.class)
                    .hasMessageContaining(
                            "Unable to resolve indexed PROJECT entrypoint");
        }
    }

    @Test
    void privateDeclarationsNeverBecomeRootsButRemainReachable()
            throws Exception {
        final Path classes = compile("PrivateRoots", """
                public class PrivateRoots {
                    private PrivateRoots() { }
                    public static void entry() {
                        privateStatic();
                        new PrivateRoots().privateInstance();
                    }
                    private static void privateStatic() { }
                    private void privateInstance() { }
                    static class VisibleNested {
                        void retained() { }
                    }
                    private static class HiddenNested {
                        void hidden() { }
                    }
                }
                """);
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId(), ModulePresence.BOTH, classes, List.of(),
                List.of(), List.of(),
                new ModuleChangeSet(List.of(), List.of()));
        final EntrypointClassIndex manualIndex = new EntrypointClassIndex(
                List.of("PrivateRoots", "PrivateRoots$HiddenNested",
                        "PrivateRoots$VisibleNested"), 100);
        final JavaRuntimeDescriptor runtime = new Jdk8RuntimeProvider()
                .probe(Path.of(System.getenv("TEST_JDK8_HOME")));

        try (IJarRepository repository = TestJarRepositories.empty()) {
            final ModuleCallGraphSession session =
                    new ModuleCallGraphEngine(
                            diagnostics(), runtime,
                            EntrypointSelection.allProjectClasses(),
                            CallGraphAlgorithm.K_OBJ,
                            WalaReflectionOptions.parse("NONE"),
                            JdkModelSelection.NONE, repository)
                            .build(new ModuleCallGraphInputAdapter()
                                            .adapt(unit), manualIndex,
                                    GRAPH_TIMEOUT_SECONDS);

            final List<String> roots = session.getGraph()
                    .getEntrypointNodes().stream()
                    .map(node -> node.getMethod().getReference().toString()
                            + " methodPrivate="
                            + node.getMethod().isPrivate()
                            + " classPrivate="
                            + node.getMethod().getDeclaringClass().isPrivate())
                    .sorted().toList();
            assertThat(session.getEntrypointCount()).as(roots.toString())
                    .isEqualTo(PRIVATE_FILTER_ROOT_COUNT);
            assertThat(session.getGraph().getEntrypointNodes())
                    .allSatisfy(node -> {
                        assertThat(node.getMethod().isPrivate()).isFalse();
                        assertThat(node.getMethod().getDeclaringClass()
                                .isPrivate()).isFalse();
                        assertThat(owner(node)).doesNotContain("HiddenNested");
                    });
            assertThat(hasEdge(session, "PrivateRoots", "entry",
                    "PrivateRoots", "privateStatic")).isTrue();
            assertThat(hasEdge(session, "PrivateRoots", "entry",
                    "PrivateRoots", "privateInstance")).isTrue();
            assertThat(session.getGraph()).anyMatch(node ->
                    owner(node).equals("PrivateRoots")
                            && node.getMethod().getName().toString()
                            .equals("privateInstance")
                            && node.getMethod().isPrivate());
        }
    }

    @Test
    void kObjUsesConfiguredAllocationDepthWithoutCallStrings()
            throws Exception {
        final Path classes = compile("KObjPrecision", """
                public class KObjPrecision {
                    public void entry() {
                        new Factory().create().target();
                    }
                    static class Factory {
                        Receiver create() {
                            return new Receiver();
                        }
                    }
                    static class Receiver {
                        void target() { }
                    }
                }
                """);

        try (IJarRepository repository = TestJarRepositories.empty()) {
            assertKObjDepth(classes, repository, 1);
            assertKObjDepth(classes, repository, 2);
        }
    }

    @Test
    void kObjConvergesForDirectAndMutualStaticRecursion()
            throws Exception {
        final Path classes = compile("RecursiveContexts", """
                public class RecursiveContexts {
                    public int entry(int value) {
                        return direct(value) + left(value);
                    }
                    private static int direct(int value) {
                        return value <= 0 ? 0 : direct(value - 1);
                    }
                    private static int left(int value) {
                        return value <= 0 ? 0 : right(value - 1);
                    }
                    private static int right(int value) {
                        return value <= 0 ? 0 : left(value - 1);
                    }
                }
                """);

        try (IJarRepository repository = TestJarRepositories.empty()) {
            for (int depth : List.of(1, 2)) {
                final ModuleCallGraphSession session = build(
                        classes, repository, CallGraphAlgorithm.K_OBJ,
                        depth, WalaReflectionOptions.parse("NONE"), 5L);
                assertThat(hasSelfEdge(session, "RecursiveContexts",
                        "direct")).isTrue();
                assertThat(hasEdge(session, "RecursiveContexts", "left",
                        "RecursiveContexts", "right")).isTrue();
                assertThat(hasEdge(session, "RecursiveContexts", "right",
                        "RecursiveContexts", "left")).isTrue();
                assertThat(session.getGraph()).allSatisfy(node ->
                        assertThat(node.getContext().get(
                                CallStringContextSelector.CALL_STRING))
                                .isNull());
            }
        }
    }

    private void assertKObjDepth(
            final Path classes,
            final IJarRepository repository,
            final int depth) throws Exception {
        final ModuleCallGraphSession session = build(
                classes, repository, CallGraphAlgorithm.K_OBJ, depth,
                WalaReflectionOptions.parse("NONE"),
                GRAPH_TIMEOUT_SECONDS);
        final List<AllocationString> allocations = new ArrayList<>();
        for (CGNode node : session.getGraph()) {
            if (owner(node).equals("KObjPrecision$Receiver")
                    && node.getMethod().getName().toString()
                    .equals("target")) {
                final ContextItem allocation = node.getContext().get(
                        nObjContextSelector.ALLOCATION_STRING_KEY);
                if (allocation instanceof AllocationString value) {
                    allocations.add(value);
                }
                assertThat(node.getContext().get(
                        CallStringContextSelector.CALL_STRING)).isNull();
            }
        }
        assertThat(allocations).isNotEmpty();
        assertThat(allocations.stream().mapToInt(
                value -> value.allocationSites().length).max())
                .hasValue(depth);
    }

    @Test
    void reportsSelectedCallGraphAlgorithm() throws Exception {
        final Path classes = compile("AlgorithmDiagnostic", """
                public class AlgorithmDiagnostic {
                    public void run() { }
                }
                """);
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId(), ModulePresence.BOTH, classes, List.of(),
                List.of(), List.of(),
                new ModuleChangeSet(List.of(), List.of()));
        final JavaRuntimeDescriptor runtime = new Jdk8RuntimeProvider()
                .probe(Path.of(System.getenv("TEST_JDK8_HOME")));
        for (CallGraphAlgorithm algorithm : CallGraphAlgorithm.values()) {
            final ByteArrayOutputStream console = new ByteArrayOutputStream();
            final DiagnosticLog collector = new DiagnosticLog(
                    new PrintStream(console), LogVerbosity.INFO);
            try (IJarRepository repository = TestJarRepositories.empty()) {
                new ModuleCallGraphEngine(collector, runtime,
                        EntrypointSelection.allProjectClasses(), algorithm,
                        WalaReflectionOptions.defaultOptions(),
                        JdkModelSelection.NONE, repository)
                        .build(new ModuleCallGraphInputAdapter().adapt(unit),
                                GRAPH_TIMEOUT_SECONDS);
            }
            assertThat(console.toString())
                    .contains("algorithm="
                                    + algorithm.identifier()
                                    + (algorithm == CallGraphAlgorithm.K_OBJ
                                    ? " (experimental);" : ";"))
                            .contains("jdkModel=none")
                            .doesNotContain("availableTarget",
                                    "unavailableTarget", "hitTarget");
        }
    }

    @Test
    void abstractEntrypointClassUsesOneSharedFakeReceiver()
            throws Exception {
        final Path classes = compile("AbstractEntrypoint", """
                public abstract class AbstractEntrypoint {
                    public AbstractEntrypoint() { }
                    public void concrete() { }
                    public abstract void abstractMethod();
                }
                """);
        final EntrypointSelection selection = EntrypointSelection.parse(
                List.of("AbstractEntrypoint"), List.of());

        try (IJarRepository repository = TestJarRepositories.empty()) {
            final ModuleCallGraphSession session = build(
                    classes, repository, List.of(),
                    InvokeDynamicBootstrapModelRegistry.jdk8Defaults(),
                    selection);

            assertThat(session.getEntrypointCount()).isEqualTo(2);
            assertThat(session.getParameterCandidateCount()).isEqualTo(2);
            assertThat(session.getHierarchy()).filteredOn(type ->
                            type instanceof BypassSyntheticClass)
                    .map(type -> (BypassSyntheticClass) type)
                    .filteredOn(type -> type.getRealType().getName()
                            .toString().equals("LAbstractEntrypoint"))
                    .hasSize(1);
        }
    }

    @Test
    void serviceLoaderProviderFlowsIntoInterfaceDispatch() throws Exception {
        final Path classes = compile("ServiceApp", """
                import java.util.ServiceLoader;
                public class ServiceApp {
                    public interface Service { void run(); }
                    public static class Provider implements Service {
                        public Provider() { }
                        public void run() { helper(); }
                        static void helper() { }
                    }
                    private Iterable<Service> services =
                            ServiceLoader.load(Service.class);
                    public void execute() {
                        for (Service service
                                : ServiceLoader.load(Service.class)) {
                            service.run();
                        }
                    }
                    public void executeLocal() {
                        Class<Service> type = Service.class;
                        for (Service service : ServiceLoader.load(type)) {
                            service.run();
                        }
                    }
                    public void executeSamePhi(boolean flag) {
                        Class<Service> type;
                        if (flag) {
                            type = Service.class;
                        } else {
                            type = Service.class;
                        }
                        ServiceLoader.load(type);
                    }
                    public void executeFromField() {
                        for (Service service : services) {
                            service.run();
                        }
                    }
                }
                """);
        final Path resource = classes.resolve(
                "META-INF/services/ServiceApp$Service");
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, "ServiceApp$Provider\n");

        try (IJarRepository repository = TestJarRepositories.empty()) {
            for (CallGraphAlgorithm algorithm
                    : CallGraphAlgorithm.values()) {
                final int depth = algorithm == CallGraphAlgorithm.K_OBJ
                        ? 2 : CallGraphAlgorithm.defaultKObjDepth();
                final ModuleCallGraphSession session = build(
                        classes, repository, algorithm, depth,
                        WalaReflectionOptions.parse("NONE"),
                        GRAPH_TIMEOUT_SECONDS);
                assertThat(session.getModelLimitations())
                        .as(algorithm.identifier()).isEmpty();
                if (algorithm == CallGraphAlgorithm.CHA) {
                    assertThat(hasEdge(session, "ServiceApp", "execute",
                            "ServiceApp$Provider", "<init>"))
                            .as(algorithm.identifier()).isTrue();
                    assertThat(hasEdge(session, "ServiceApp", "executeLocal",
                            "ServiceApp$Provider", "<init>"))
                            .as(algorithm.identifier()).isTrue();
                    assertThat(hasEdge(session, "ServiceApp",
                            "executeSamePhi", "ServiceApp$Provider",
                            "<init>"))
                            .as(algorithm.identifier()).isTrue();
                } else {
                    assertThat(hasEdge(session,
                            "ServiceApp", "execute",
                            "ServiceApp$Provider", "run"))
                            .as(algorithm.identifier()).isTrue();
                }
                assertThat(hasEdge(session,
                        "ServiceApp", "executeFromField",
                        "ServiceApp$Provider", "run"))
                        .as(algorithm.identifier()).isTrue();
                if (algorithm != CallGraphAlgorithm.CHA) {
                    assertThat(session.getGraph()).anySatisfy(node -> {
                        assertThat(owner(node)).startsWith(
                                "wala/serviceloader/Iterator$");
                        assertThat(successors(session, node))
                                .anySatisfy(target -> {
                                    assertThat(owner(target))
                                            .isEqualTo("ServiceApp$Provider");
                                    assertThat(target.getMethod().isInit())
                                            .isTrue();
                                });
                    });
                }
                if (algorithm == CallGraphAlgorithm.K_OBJ) {
                    assertThat(session.getGraph())
                            .filteredOn(node -> "ServiceApp$Provider"
                                    .equals(owner(node))
                                    && node.getMethod().isInit())
                            .anySatisfy(node -> {
                                final ContextItem value = node.getContext()
                                        .get(nObjContextSelector
                                                .ALLOCATION_STRING_KEY);
                                assertThat(value)
                                        .isInstanceOf(AllocationString.class);
                                assertThat(((AllocationString) value)
                                        .allocationSites()).hasSize(2);
                                assertThat(node.getContext().get(
                                        CallStringContextSelector.CALL_STRING))
                                        .isNull();
                            });
                }
            }
        }
    }

    @Test
    void chaClassForNameUsesCallerLocalConstants() throws Exception {
        final Path classes = compile("ChaReflectionApp", """
                public class ChaReflectionApp {
                    public void direct() throws Exception {
                        String name = "removed.Type";
                        Class.forName(name);
                    }
                    public void samePhi(boolean flag) throws Exception {
                        String name;
                        if (flag) {
                            name = "removed.Type";
                        } else {
                            name = "removed.Type";
                        }
                        Class.forName(name);
                    }
                    public void parameter(String name) throws Exception {
                        Class.forName(name);
                    }
                    public void emptyLiteral() throws Exception {
                        Class.forName("");
                    }
                    public void blankLiteral() throws Exception {
                        Class.forName("   ");
                    }
                    public void invalidLiteral() throws Exception {
                        Class.forName("removed/Type");
                    }
                }
                """);
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "test", "library", "jar", "1");
        final ArtifactCoord newArtifact = new ArtifactCoord(
                "test", "library", "jar", "2");
        final ModuleId module = moduleId();
        final BoundChangePoint point = new BoundChangePoint(
                new DependencyUpgradeKey(module, DependencyScope.COMPILE,
                        oldArtifact, newArtifact),
                new ChangePoint(newArtifact, ChangePointKind.CLASS_REMOVED,
                        "removed/Type", null, null, null, null));
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                module, ModulePresence.BOTH, classes, List.of(),
                List.of(), List.of(),
                new ModuleChangeSet(List.of(point), List.of()));

        try (IJarRepository repository = TestJarRepositories.empty()) {
            final ModuleCallGraphSession session = build(
                    unit, repository,
                    InvokeDynamicBootstrapModelRegistry.jdk8Defaults(),
                    EntrypointSelection.allProjectClasses(),
                    CallGraphAlgorithm.CHA,
                    WalaReflectionOptions.parse("NONE"));

            assertThat(evidence(unit, session).resolution(point)
                    .evidence())
                    .extracting(evidence -> evidence.mechanism())
                    .containsOnly(EvidenceMechanism
                            .CLASS_FOR_NAME_LOCAL_CONSTANT);
            assertThat(evidence(unit, session).resolution(point)
                    .evidence())
                    .extracting(evidence -> evidence.location().source())
                    .anyMatch(location -> location.contains("direct"))
                    .anyMatch(location -> location.contains("samePhi"));
            assertThat(evidence(unit, session).limitations())
                    .anySatisfy(limitation -> assertThat(
                            limitation.summary()).contains(
                            "CLASS_FOR_NAME_LOCAL_CONSTANT_UNRESOLVED"));
            final List<CoverageLimitation> invalidLiterals = evidence(
                    unit, session).limitations().stream()
                    .filter(limitation -> limitation.summary().contains(
                            "CLASS_FOR_NAME_LITERAL_INVALID"))
                    .toList();
            assertThat(invalidLiterals)
                    .hasSize(INVALID_CLASS_NAME_LITERAL_COUNT)
                    .allSatisfy(limitation -> {
                        assertThat(limitation.reason()).isEqualTo(
                                ModuleAnalysisReason
                                        .INCONCLUSIVE_REFLECTION);
                        assertThat(limitation.summary())
                                .contains("ChaReflectionApp")
                                .contains("|pc=");
                        assertThat(limitation.stableKey())
                                .doesNotContain("Node:");
                    });
            assertThat(invalidLiterals)
                    .extracting(CoverageLimitation::summary)
                    .anySatisfy(value -> assertThat(value)
                            .contains("literal=<empty>"))
                    .anySatisfy(value -> assertThat(value)
                            .contains("literal=<blank>"))
                    .anySatisfy(value -> assertThat(value)
                            .contains("literal=removed/Type"));
            assertThat(invalidLiterals)
                    .extracting(CoverageLimitation::stableKey)
                    .doesNotHaveDuplicates();
        }
    }

    @Test
    void serviceLoaderSupportsLoadAndLoadInstalled() throws Exception {
        final Path classes = compile("ServiceOverloads", """
                import java.util.ServiceLoader;
                public class ServiceOverloads {
                    public interface Service { void run(); }
                    public static class Provider implements Service {
                        public Provider() { }
                        public void run() { }
                    }
                    public void regular() {
                        ServiceLoader.load(Service.class)
                                .iterator().next().run();
                    }
                    public void installed() {
                        ServiceLoader.loadInstalled(Service.class)
                                .iterator().next().run();
                    }
                }
                """);
        writeServiceResource(classes, "ServiceOverloads$Service",
                "ServiceOverloads$Provider\n");

        try (IJarRepository repository = TestJarRepositories.empty()) {
            final ModuleCallGraphSession session = build(classes, repository);
            assertThat(session.getModelLimitations()).isEmpty();
            for (String method : List.of(
                    "regular", "installed")) {
                assertThat(hasEdge(session,
                        "ServiceOverloads", method,
                        "ServiceOverloads$Provider", "run"))
                        .as(method).isTrue();
            }
        }
    }

    @Test
    void serviceLoaderDoesNotMixServiceTypes() throws Exception {
        final Path classes = compile("MultipleServices", """
                import java.util.ServiceLoader;
                public class MultipleServices {
                    public interface First { void run(); }
                    public interface Second { void run(); }
                    public static class FirstProvider implements First {
                        public FirstProvider() { }
                        public void run() { }
                    }
                    public static class SecondProvider implements Second {
                        public SecondProvider() { }
                        public void run() { }
                    }
                    public void first() {
                        ServiceLoader.load(First.class)
                                .iterator().next().run();
                    }
                    public void second() {
                        ServiceLoader.load(Second.class)
                                .iterator().next().run();
                    }
                }
                """);
        writeServiceResource(classes, "MultipleServices$First",
                "MultipleServices$FirstProvider\n");
        writeServiceResource(classes, "MultipleServices$Second",
                "MultipleServices$SecondProvider\n");

        try (IJarRepository repository = TestJarRepositories.empty()) {
            final ModuleCallGraphSession session = build(classes, repository);
            assertThat(hasEdge(session, "MultipleServices", "first",
                    "MultipleServices$FirstProvider", "run")).isTrue();
            assertThat(hasEdge(session, "MultipleServices", "first",
                    "MultipleServices$SecondProvider", "run")).isFalse();
            assertThat(hasEdge(session, "MultipleServices", "second",
                    "MultipleServices$SecondProvider", "run")).isTrue();
            assertThat(hasEdge(session, "MultipleServices", "second",
                    "MultipleServices$FirstProvider", "run")).isFalse();
        }
    }

    @Test
    void nonconstantServiceTypeProducesStableLimitation() throws Exception {
        final Path classes = compile("DynamicServiceApp", """
                import java.util.ServiceLoader;
                public class DynamicServiceApp {
                    public interface Service { void run(); }
                    public void execute(Class<Service> type) {
                        ServiceLoader.load(type).iterator();
                    }
                }
                """);

        try (IJarRepository repository = TestJarRepositories.empty()) {
            final ModuleCallGraphSession session = build(classes, repository);
            assertThat(session.hasServiceLoaderLimitations()).isTrue();
            assertThat(session.getModelLimitations()).anySatisfy(value ->
                    assertThat(value).contains(
                            "non-constant Class argument"));
        }
    }

    @Test
    void methodHandleTargetIsReachableAcrossAlgorithms()
            throws Exception {
        final Path classes = compile("KObjMethodHandleApp", """
                import java.lang.invoke.MethodHandle;
                import java.lang.invoke.MethodHandles;
                import java.lang.invoke.MethodType;
                public class KObjMethodHandleApp {
                    static void target() { }
                    public void execute() throws Throwable {
                        MethodHandle handle = MethodHandles.lookup()
                                .findStatic(KObjMethodHandleApp.class, "target",
                                        MethodType.methodType(void.class));
                        handle.invokeExact();
                    }
                }
                """);
        final JavaRuntimeDescriptor minimalRuntime =
                MinimalJdk8RuntimeFixture.create(
                        temporary.resolve("method-handle-jdk8"));

        try (IJarRepository repository = TestJarRepositories.empty()) {
            for (CallGraphAlgorithm algorithm
                    : CallGraphAlgorithm.values()) {
                final ModuleCallGraphSession session =
                        algorithm == CallGraphAlgorithm
                                .K_OBJ
                        ? build(classes, repository, algorithm,
                                WalaReflectionOptions.parse("NONE"),
                                minimalRuntime)
                        : build(classes, repository, algorithm,
                                WalaReflectionOptions.parse("NONE"));
                assertThat(hasPath(session,
                        "KObjMethodHandleApp", "execute",
                        "KObjMethodHandleApp", "target"))
                        .as(algorithm.identifier())
                        .isEqualTo(algorithm != CallGraphAlgorithm.CHA);
                assertThat(session.hasMethodHandleLimitations())
                        .isEqualTo(algorithm == CallGraphAlgorithm.CHA);
            }
        }
    }

    @Test
    void capturingMarkerBridgeAltMetafactoryCallsImplementation()
            throws Exception {
        final Path reactor = compile("AltFactory", """
                import java.io.Serializable;
                public class AltFactory {
                    public interface Base { Object get(); }
                    public interface Narrow { String get(); }
                    public interface Marker { }
                    public static Base create(String captured) {
                        return (Base & Narrow & Marker & Serializable)
                                () -> captured;
                    }
                }
                """);
        final Path classes = compile("AltLambdaApp", """
                public class AltLambdaApp {
                    public Object execute() {
                        return AltFactory.create("value").get();
                    }
                }
                """, List.of(reactor));

        try (IJarRepository repository = TestJarRepositories.empty()) {
            for (CallGraphAlgorithm algorithm
                    : CallGraphAlgorithm.values()) {
                final ModuleCallGraphSession session = build(
                        classes, repository, List.of(reactor), algorithm,
                        WalaReflectionOptions.parse("NONE"));
                assertThat(session.hasDynamicModelLimitations())
                        .as(algorithm.identifier() + " "
                                + session.getModelLimitations())
                        .isEqualTo(algorithm == CallGraphAlgorithm.CHA);
                if (algorithm == CallGraphAlgorithm.CHA) {
                    continue;
                }
                assertThat(session.getHierarchy()).anySatisfy(type -> {
                    assertThat(owner(type.getName().toString()))
                            .startsWith("wala/lambda/Alt$");
                    assertThat(type.getDirectInterfaces().stream()
                            .map(iface -> owner(
                                    iface.getName().toString())))
                            .contains("java/io/Serializable",
                                    "AltFactory$Marker",
                                    "AltFactory$Narrow");
                    assertThat(type.getDeclaredMethods().stream()
                            .map(method -> method.getDescriptor().toString()))
                            .contains("()Ljava/lang/Object;",
                                    "()Ljava/lang/String;");
                });
                assertThat(session.getGraph()).anySatisfy(node -> {
                    assertThat(owner(node)).startsWith(
                            "wala/lambda/Alt$");
                    assertThat(successors(session, node))
                            .anySatisfy(target -> {
                                assertThat(owner(target))
                                        .isEqualTo("AltFactory");
                                assertThat(target.getMethod().getName()
                                        .toString())
                                        .startsWith("lambda$create$");
                            });
                });
            }
        }
    }

    @Test
    void removedMetafactoryImplementationRetainsTerminalEvidence()
            throws Exception {
        final Path reactor = compile("LambdaLibrary", """
                public class LambdaLibrary {
                    public static void implementation() { }
                }
                """);
        final Path classes = compile("RemovedLambdaApp", """
                public class RemovedLambdaApp {
                    public Runnable callback() {
                        return LambdaLibrary::implementation;
                    }
                    public void execute() {
                        callback().run();
                    }
                }
                """, List.of(reactor));
        compile("LambdaLibrary", """
                public class LambdaLibrary {
                    public static void implementation(int value) { }
                }
                """);

        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "test", "library", "jar", "1");
        final ArtifactCoord newArtifact = new ArtifactCoord(
                "test", "library", "jar", "2");
        final ModuleId module = moduleId();
        final BoundChangePoint point = new BoundChangePoint(
                new DependencyUpgradeKey(module, DependencyScope.COMPILE,
                        oldArtifact, newArtifact),
                ChangePoint.withDescriptors(newArtifact,
                        ChangePointKind.METHOD_DESCRIPTOR_CHANGED,
                        "LambdaLibrary", "implementation",
                        new MemberDescriptors("()V", "(I)V"),
                        null, null));
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                module, ModulePresence.BOTH, classes, List.of(reactor),
                List.of(), List.of(),
                new ModuleChangeSet(List.of(point), List.of()));

        try (IJarRepository repository = TestJarRepositories.empty()) {
            for (CallGraphAlgorithm algorithm
                    : CallGraphAlgorithm.values()) {
                final ModuleCallGraphSession session = build(
                        unit, repository,
                        InvokeDynamicBootstrapModelRegistry.jdk8Defaults(),
                        EntrypointSelection.allProjectClasses(), algorithm,
                        WalaReflectionOptions.parse("NONE"));
                assertThat(session.getDynamicEvidence().find(
                        "LambdaLibrary", "implementation", "()V"))
                        .as(algorithm.identifier()).isNotEmpty();
                final ModuleImpactQueryResult query =
                        new ModuleImpactTracer(diagnostics())
                                .trace(unit, session, evidence(unit, session));
                assertThat(query.getPaths())
                        .as(algorithm.identifier()).anySatisfy(path -> {
                            assertThat(path.getRootMethod().owner())
                                    .isEqualTo("RemovedLambdaApp");
                            assertThat(path.getTerminal()
                                    .getEvidenceMechanism()).isEqualTo(
                                    io.github.dependencyanalysis.impact
                                            .EvidenceMechanism
                                            .INVOKEDYNAMIC_HANDLE);
                        });
            }
        }
    }

    @Test
    void chaUsesResolvedObjectDeclarationForGenericJdkFiltering()
            throws Exception {
        final Path externalClasses = compile("ExternalOverrides", """
                public class ExternalOverrides {
                    public static class Changed {
                        public String toString() { return "changed"; }
                        public int hashCode() { return 17; }
                    }
                    public static class Unrelated {
                        public String toString() { return "unrelated"; }
                        public int hashCode() { return 23; }
                    }
                }
                """);
        final Path reactor = compile("ReactorOverride", """
                public class ReactorOverride {
                    public String toString() { return "reactor"; }
                    public int hashCode() { return 31; }
                }
                """);
        final Path classes = compile("ObjectDispatchApp", """
                public class ObjectDispatchApp {
                    public static class ProjectOverride {
                        public String toString() { return "project"; }
                        public int hashCode() { return 37; }
                    }
                    public void execute(Object value) {
                        value.toString();
                        value.hashCode();
                    }
                    public int executeUnrelated() {
                        Object value = new ExternalOverrides.Unrelated();
                        return value.toString().length() + value.hashCode();
                    }
                }
                """, List.of(externalClasses, reactor));
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "test", "external-overrides", "jar", "1");
        final ArtifactCoord newArtifact = new ArtifactCoord(
                "test", "external-overrides", "jar", "2");
        final Path externalJar = jar(externalClasses,
                "external-overrides-2.jar");
        final ModuleId module = moduleId();
        final DependencyUpgradeKey key = new DependencyUpgradeKey(
                module, DependencyScope.COMPILE,
                oldArtifact, newArtifact);
        final List<BoundChangePoint> changes = List.of(
                new BoundChangePoint(key, ChangePoint.withDescriptors(
                        newArtifact, ChangePointKind.METHOD_BODY_CHANGED,
                        "ExternalOverrides$Changed", "toString",
                        new MemberDescriptors("()Ljava/lang/String;",
                                "()Ljava/lang/String;"), "old", "new")),
                new BoundChangePoint(key, ChangePoint.withDescriptors(
                        newArtifact, ChangePointKind.METHOD_BODY_CHANGED,
                        "ExternalOverrides$Changed", "hashCode",
                        new MemberDescriptors("()I", "()I"),
                        "old", "new")));
        final ModuleDependencyOccurrenceGraph dependencyGraph =
                new ModuleDependencyOccurrenceGraph("root", List.of(
                        new ModuleDependencyOccurrenceGraph.Occurrence(
                                "root", module.getCoordinate(), null,
                                true, false),
                        new ModuleDependencyOccurrenceGraph.Occurrence(
                                "external", newArtifact,
                                DependencyScope.COMPILE, false, false)),
                        List.of(new ModuleDependencyOccurrenceGraph.Edge(
                                "root", "external")));
        final ModuleDependencyEvidence evidence =
                DependencyEvidenceFixtures.evidence(
                        classes.getParent(), module.getCoordinate(),
                        List.of(new DependencyNode(newArtifact,
                                DependencyScope.COMPILE, List.of())),
                        dependencyGraph, List.of(),
                        List.of(new ResolvedArtifact(
                                newArtifact, externalJar)));

        try (IJarRepository repository = TestJarRepositories.of(List.of(
                new ResolvedArtifact(newArtifact, externalJar)))) {
            for (DependencyAnalysisScopeMode scope
                    : DependencyAnalysisScopeMode.values()) {
                final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                        module, ModulePresence.BOTH, classes,
                        List.of(reactor), ModuleDependencyInputs.fromEvidence(
                        evidence, null, Set.of(newArtifact), scope),
                        new ModuleChangeSet(changes, List.of()));
                final ModuleCallGraphSession session = build(
                        unit, repository,
                        InvokeDynamicBootstrapModelRegistry.jdk8Defaults(),
                        EntrypointSelection.parse(
                                List.of("ObjectDispatchApp"), List.of()),
                        CallGraphAlgorithm.CHA,
                        WalaReflectionOptions.parse("NONE"));

                assertThat(hasEdge(session, "ObjectDispatchApp", "execute",
                        "java/lang/Object", "toString"))
                        .as(scope.identifier()).isTrue();
                assertThat(hasEdge(session, "ObjectDispatchApp", "execute",
                        "java/lang/Object", "hashCode")).isTrue();
                assertThat(hasEdge(session, "ObjectDispatchApp", "execute",
                        "ObjectDispatchApp$ProjectOverride", "toString"))
                        .isFalse();
                assertThat(hasEdge(session, "ObjectDispatchApp", "execute",
                        "ReactorOverride", "hashCode")).isFalse();
                assertThat(hasEdge(session, "ObjectDispatchApp", "execute",
                        "ExternalOverrides$Changed", "toString")).isFalse();
                assertThat(hasEdge(session, "ObjectDispatchApp", "execute",
                        "ExternalOverrides$Changed", "hashCode")).isFalse();
                assertThat(hasEdge(session, "ObjectDispatchApp", "execute",
                        "ExternalOverrides$Unrelated", "toString")).isFalse();
                assertThat(hasEdge(session, "ObjectDispatchApp", "execute",
                        "ExternalOverrides$Unrelated", "hashCode")).isFalse();
                assertThat(hasEdge(session, "ObjectDispatchApp", "execute",
                        "java/lang/String", "toString")).isTrue();
                assertThat(hasEdge(session, "ObjectDispatchApp", "execute",
                        "java/lang/String", "hashCode")).isTrue();
                assertThat(hasMethod(session,
                        "ExternalOverrides$Unrelated", "toString")).isFalse();
                assertThat(hasMethod(session,
                        "ExternalOverrides$Unrelated", "hashCode")).isFalse();
                assertThat(new ModuleImpactTracer(diagnostics()).trace(
                        unit, session, evidence(unit, session)).getPaths())
                        .isEmpty();
            }

        }
    }

    @Test
    void reachableUnknownBootstrapIsInconclusive() throws Exception {
        final Path classes = dynamicClass(
                "UnknownDynamicApp", false);
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId(), ModulePresence.BOTH, classes, List.of(),
                List.of(), List.of(),
                new ModuleChangeSet(List.of(), List.of()));
        final EntrypointSelection roots = EntrypointSelection.parse(
                List.of("UnknownDynamicApp"), List.of());
        try (IJarRepository repository = TestJarRepositories.empty()) {
            for (CallGraphAlgorithm algorithm
                    : CallGraphAlgorithm.values()) {
                final ModuleCallGraphSession session = build(
                        unit, repository,
                        InvokeDynamicBootstrapModelRegistry.jdk8Defaults(),
                        roots, algorithm,
                        WalaReflectionOptions.parse("NONE"));
                assertThat(session.hasDynamicModelLimitations())
                        .as(algorithm.identifier()).isTrue();
                assertThat(session.getDynamicEvidence().all())
                        .as(algorithm.identifier()).anySatisfy(evidence -> {
                            assertThat(evidence.targetOwner())
                                    .isEqualTo("custom/Bootstrap");
                            assertThat(evidence.referenceKind()).isEqualTo(
                                    DynamicReferenceKind
                                            .BOOTSTRAP_IMPLEMENTATION_METHOD);
                        });
            }
        }
    }

    @Test
    void unreachableUnknownBootstrapDoesNotPolluteModule()
            throws Exception {
        final Path reactor = dynamicClass(
                "UnreachableDynamic", false);
        final Path classes = compile("PlainApp", """
                public class PlainApp {
                    public void execute() { }
                }
                """);
        try (IJarRepository repository = TestJarRepositories.empty()) {
            for (CallGraphAlgorithm algorithm
                    : CallGraphAlgorithm.values()) {
                final ModuleCallGraphSession session = build(
                        classes, repository, List.of(reactor), algorithm,
                        WalaReflectionOptions.parse("NONE"));
                assertThat(session.hasDynamicModelLimitations())
                        .as(algorithm.identifier()).isFalse();
                assertThat(session.getDynamicEvidence().all())
                        .as(algorithm.identifier()).noneMatch(evidence ->
                        owner(evidence.caller()).equals("UnreachableDynamic")
                                || evidence.targetOwner().equals(
                                "custom/Bootstrap"));
            }
        }
    }

    @Test
    void customBootstrapModelParticipatesInFixedPoint()
            throws Exception {
        final Path classes = dynamicClass("CustomDynamicApp", true);
        final InvokeDynamicBootstrapKey key =
                new InvokeDynamicBootstrapKey(
                        "custom/Bootstrap", "bootstrap",
                        BOOTSTRAP_DESCRIPTOR);
        final InvokeDynamicBootstrapModelRegistry registry =
                InvokeDynamicBootstrapModelRegistry.jdk8Defaults()
                        .toBuilder()
                        .register(key, (caller, site, instruction,
                                hierarchy, syntheticClasses) -> {
                            final IMethod helper = hierarchy.resolveMethod(
                                    MethodReference.findOrCreate(
                                            ClassLoaderReference.Application,
                                            "LCustomDynamicApp", "helper",
                                            "()V"));
                            return helper == null
                                    ? InvokeDynamicModelResult.unsupported(
                                    "helper unresolved")
                                    : InvokeDynamicModelResult.modeled(helper);
                        }).build();
        try (IJarRepository repository = TestJarRepositories.empty()) {
            final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                    moduleId(), ModulePresence.BOTH, classes, List.of(),
                    List.of(), List.of(),
                    new ModuleChangeSet(List.of(), List.of()));
            final EntrypointSelection roots = EntrypointSelection.parse(
                    List.of("CustomDynamicApp"), List.of());
            for (CallGraphAlgorithm algorithm
                    : CallGraphAlgorithm.values()) {
                final ModuleCallGraphSession session = build(
                        unit, repository, registry,
                        roots, algorithm,
                        WalaReflectionOptions.parse("NONE"));
                assertThat(session.hasDynamicModelLimitations())
                        .as(algorithm.identifier() + " "
                                + session.getModelLimitations())
                        .isEqualTo(algorithm == CallGraphAlgorithm.CHA);
                assertThat(hasEdge(session,
                        "CustomDynamicApp", "execute",
                        "CustomDynamicApp", "helper"))
                        .as(algorithm.identifier())
                        .isEqualTo(algorithm != CallGraphAlgorithm.CHA);
            }
        }
    }

    @Test
    void registryRejectsDuplicateBootstrapKeys() {
        final InvokeDynamicBootstrapKey key =
                new InvokeDynamicBootstrapKey(
                        "custom/Bootstrap", "bootstrap",
                        BOOTSTRAP_DESCRIPTOR);
        final InvokeDynamicBootstrapModel model =
                (caller, site, instruction, hierarchy,
                        syntheticClasses) ->
                        InvokeDynamicModelResult.unsupported("test");
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() ->
                InvokeDynamicBootstrapModelRegistry.builder()
                        .register(key, model)
                        .register(key, model)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate invokedynamic");
    }

    private ModuleCallGraphSession build(
            final Path classes,
            final IJarRepository repository) throws Exception {
        return build(classes, repository, List.of());
    }

    private ModuleCallGraphSession build(
            final Path classes,
            final IJarRepository repository,
            final CallGraphAlgorithm algorithm) throws Exception {
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId(), ModulePresence.BOTH, classes, List.of(),
                List.of(), List.of(),
                new ModuleChangeSet(List.of(), List.of()));
        return build(unit, repository,
                InvokeDynamicBootstrapModelRegistry.jdk8Defaults(),
                EntrypointSelection.allProjectClasses(), algorithm);
    }

    private ModuleCallGraphSession build(
            final Path classes,
            final IJarRepository repository,
            final CallGraphAlgorithm algorithm,
            final WalaReflectionOptions reflectionOptions) throws Exception {
        return build(classes, repository, algorithm,
                CallGraphAlgorithm.defaultKObjDepth(), reflectionOptions,
                GRAPH_TIMEOUT_SECONDS);
    }

    private ModuleCallGraphSession build(
            final Path classes,
            final IJarRepository repository,
            final CallGraphAlgorithm algorithm,
            final int kObjDepth,
            final WalaReflectionOptions reflectionOptions,
            final long timeoutSeconds) throws Exception {
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId(), ModulePresence.BOTH, classes, List.of(),
                List.of(), List.of(),
                new ModuleChangeSet(List.of(), List.of()));
        final JavaRuntimeDescriptor runtime = new Jdk8RuntimeProvider()
                .probe(Path.of(System.getenv("TEST_JDK8_HOME")));
        return new ModuleCallGraphEngine(diagnostics(), runtime,
                EntrypointSelection.allProjectClasses(),
                new CallGraphConfiguration(algorithm, kObjDepth,
                        reflectionOptions),
                JdkModelSelection.NONE, repository).build(
                new ModuleCallGraphInputAdapter().adapt(unit),
                timeoutSeconds);
    }

    private ModuleCallGraphSession build(
            final Path classes,
            final IJarRepository repository,
            final CallGraphAlgorithm algorithm,
            final WalaReflectionOptions reflectionOptions,
            final JavaRuntimeDescriptor runtime) throws Exception {
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId(), ModulePresence.BOTH, classes, List.of(),
                List.of(), List.of(),
                new ModuleChangeSet(List.of(), List.of()));
        return new ModuleCallGraphEngine(diagnostics(), runtime,
                EntrypointSelection.allProjectClasses(), algorithm,
                reflectionOptions, JdkModelSelection.NONE, repository).build(
                new ModuleCallGraphInputAdapter().adapt(unit),
                GRAPH_TIMEOUT_SECONDS);
    }

    private ModuleCallGraphSession build(
            final Path classes,
            final IJarRepository repository,
            final List<Path> reactorClasses) throws Exception {
        return build(classes, repository, reactorClasses,
                InvokeDynamicBootstrapModelRegistry.jdk8Defaults());
    }

    private ModuleCallGraphSession build(
            final Path classes,
            final IJarRepository repository,
            final List<Path> reactorClasses,
            final InvokeDynamicBootstrapModelRegistry registry)
            throws Exception {
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId(),
                ModulePresence.BOTH, classes, reactorClasses, List.of(),
                List.of(), new ModuleChangeSet(List.of(), List.of()));
        return build(unit, repository, registry);
    }

    private ModuleCallGraphSession build(
            final Path classes,
            final IJarRepository repository,
            final List<Path> reactorClasses,
            final CallGraphAlgorithm algorithm,
            final WalaReflectionOptions reflectionOptions) throws Exception {
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId(), ModulePresence.BOTH, classes, reactorClasses,
                List.of(), List.of(),
                new ModuleChangeSet(List.of(), List.of()));
        final JavaRuntimeDescriptor runtime = new Jdk8RuntimeProvider()
                .probe(Path.of(System.getenv("TEST_JDK8_HOME")));
        return new ModuleCallGraphEngine(diagnostics(), runtime,
                EntrypointSelection.allProjectClasses(), algorithm,
                reflectionOptions, JdkModelSelection.NONE, repository).build(
                new ModuleCallGraphInputAdapter().adapt(unit),
                GRAPH_TIMEOUT_SECONDS);
    }

    private ModuleCallGraphSession build(
            final ModuleAnalysisUnit unit,
            final IJarRepository repository,
            final InvokeDynamicBootstrapModelRegistry registry)
            throws Exception {
        return build(unit, repository, registry,
                EntrypointSelection.allProjectClasses(),
                CallGraphAlgorithm.K_OBJ);
    }

    private ModuleCallGraphSession build(
            final Path classes,
            final IJarRepository repository,
            final List<Path> reactorClasses,
            final InvokeDynamicBootstrapModelRegistry registry,
            final EntrypointSelection selection) throws Exception {
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId(), ModulePresence.BOTH, classes, reactorClasses,
                List.of(), List.of(),
                new ModuleChangeSet(List.of(), List.of()));
        return build(unit, repository, registry, selection);
    }

    private ModuleCallGraphSession build(
            final ModuleAnalysisUnit unit,
            final IJarRepository repository,
            final InvokeDynamicBootstrapModelRegistry registry,
            final EntrypointSelection selection)
            throws Exception {
        return build(unit, repository, registry, selection,
                CallGraphAlgorithm.K_OBJ);
    }

    private ModuleCallGraphSession build(
            final ModuleAnalysisUnit unit,
            final IJarRepository repository,
            final InvokeDynamicBootstrapModelRegistry registry,
            final EntrypointSelection selection,
            final CallGraphAlgorithm algorithm)
            throws Exception {
        return build(unit, repository, registry, selection, algorithm,
                WalaReflectionOptions.defaultOptions());
    }

    private ModuleCallGraphSession build(
            final ModuleAnalysisUnit unit,
            final IJarRepository repository,
            final InvokeDynamicBootstrapModelRegistry registry,
            final EntrypointSelection selection,
            final CallGraphAlgorithm algorithm,
            final WalaReflectionOptions reflectionOptions)
            throws Exception {
        final JavaRuntimeDescriptor runtime = new Jdk8RuntimeProvider()
                .probe(Path.of(System.getenv("TEST_JDK8_HOME")));
        return new ModuleCallGraphEngine(diagnostics(), runtime,
                selection, algorithm, reflectionOptions, repository,
                new CallGraphModelConfiguration(
                        JdkModelSelection.NONE, registry))
                .build(new ModuleCallGraphInputAdapter().adapt(unit),
                        GRAPH_TIMEOUT_SECONDS);
    }

    private ModuleId moduleId() {
        return new ModuleId(new ArtifactCoord(
                "test", "app", "jar", "1"), Path.of("."));
    }

    private void writeServiceResource(
            final Path classes,
            final String service,
            final String providers) throws Exception {
        final Path resource = classes.resolve(
                "META-INF/services/" + service);
        Files.createDirectories(resource.getParent());
        Files.writeString(resource, providers);
    }

    private Path dynamicClass(
            final String name,
            final boolean helper) throws Exception {
        final Path classes = temporary.resolve(name + "-classes");
        Files.createDirectories(classes);
        final ClassWriter writer = new ClassWriter(
                ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC,
                name, null, "java/lang/Object", null);
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "execute", "()V", null, null);
        method.visitCode();
        method.visitInvokeDynamicInsn("run", "()V",
                new Handle(Opcodes.H_INVOKESTATIC,
                        "custom/Bootstrap", "bootstrap",
                        BOOTSTRAP_DESCRIPTOR, false));
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        if (helper) {
            method = writer.visitMethod(
                    Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                    "helper", "()V", null, null);
            method.visitCode();
            method.visitInsn(Opcodes.RETURN);
            method.visitMaxs(0, 0);
            method.visitEnd();
        }
        writer.visitEnd();
        Files.write(classes.resolve(name + ".class"),
                writer.toByteArray());
        writeCustomBootstrap(classes);
        return classes;
    }

    private void writeCustomBootstrap(final Path classes) throws Exception {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC,
                "custom/Bootstrap", null, "java/lang/Object", null);
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
                        | Opcodes.ACC_NATIVE,
                "bootstrap", BOOTSTRAP_DESCRIPTOR, null, null).visitEnd();
        writer.visitEnd();
        final Path output = classes.resolve("custom/Bootstrap.class");
        Files.createDirectories(output.getParent());
        Files.write(output, writer.toByteArray());
    }

    private Path compile(final String name, final String source)
            throws Exception {
        return compile(name, source, List.of());
    }

    private Path compile(
            final String name,
            final String source,
            final List<Path> classpath) throws Exception {
        final Path sourceFile = temporary.resolve(name + ".java");
        final Path classes = temporary.resolve(name + "-classes");
        Files.createDirectories(classes);
        Files.writeString(sourceFile, source);
        final List<String> arguments = new ArrayList<>(List.of(
                "--release", "8", "-d", classes.toString()));
        if (!classpath.isEmpty()) {
            arguments.add("-classpath");
            arguments.add(String.join(
                    System.getProperty("path.separator"),
                    classpath.stream().map(Path::toString).toList()));
        }
        arguments.add(sourceFile.toString());
        final int exit = ToolProvider.getSystemJavaCompiler().run(
                null, null, null, arguments.toArray(String[]::new));
        assertThat(exit).isZero();
        return classes;
    }

    private Path jar(final Path classes, final String name) throws Exception {
        final Path output = temporary.resolve(name);
        try (JarOutputStream jar = new JarOutputStream(
                Files.newOutputStream(output));
             java.util.stream.Stream<Path> stream = Files.walk(classes)) {
            for (Path file : stream.filter(Files::isRegularFile)
                    .sorted().toList()) {
                final String entry = classes.relativize(file).toString()
                        .replace('\\', '/');
                jar.putNextEntry(new JarEntry(entry));
                jar.write(Files.readAllBytes(file));
                jar.closeEntry();
            }
        }
        return output;
    }

    private boolean hasEdge(
            final ModuleCallGraphSession session,
            final String callerOwner,
            final String callerName,
            final String calleeOwner,
            final String calleeName) {
        for (CGNode caller : session.getGraph()) {
            if (!callerOwner.equals(owner(caller))
                    || !callerName.equals(
                    caller.getMethod().getName().toString())) {
                continue;
            }
            for (CGNode callee : successors(session, caller)) {
                if (calleeOwner.equals(owner(callee))
                        && calleeName.equals(
                        callee.getMethod().getName().toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasMethod(
            final ModuleCallGraphSession session,
            final String methodOwner,
            final String methodName) {
        for (CGNode node : session.getGraph()) {
            if (methodOwner.equals(owner(node))
                    && methodName.equals(
                    node.getMethod().getName().toString())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSelfEdge(
            final ModuleCallGraphSession session,
            final String nodeOwner,
            final String nodeName) {
        for (CGNode node : session.getGraph()) {
            if (nodeOwner.equals(owner(node))
                    && nodeName.equals(
                    node.getMethod().getName().toString())
                    && successors(session, node).contains(node)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPath(
            final ModuleCallGraphSession session,
            final String callerOwner,
            final String callerName,
            final String targetOwner,
            final String targetName) {
        final Queue<CGNode> pending = new ArrayDeque<>();
        final Set<CGNode> visited = new HashSet<>();
        for (CGNode node : session.getGraph()) {
            if (callerOwner.equals(owner(node))
                    && callerName.equals(
                    node.getMethod().getName().toString())) {
                pending.add(node);
                visited.add(node);
            }
        }
        while (!pending.isEmpty()) {
            final CGNode node = pending.remove();
            if (targetOwner.equals(owner(node))
                    && targetName.equals(
                    node.getMethod().getName().toString())) {
                return true;
            }
            for (CGNode successor : successors(session, node)) {
                if (visited.add(successor)) {
                    pending.add(successor);
                }
            }
        }
        return false;
    }

    private List<CGNode> successors(
            final ModuleCallGraphSession session,
            final CGNode node) {
        final List<CGNode> result = new ArrayList<>();
        final Iterator<CGNode> iterator = session.getGraph()
                .getSuccNodes(node);
        iterator.forEachRemaining(result::add);
        return result;
    }

    private String owner(final CGNode node) {
        final String value = node.getMethod().getDeclaringClass()
                .getName().toString();
        return value.startsWith("L") ? value.substring(1) : value;
    }

    private String owner(final String value) {
        return value.startsWith("L") ? value.substring(1) : value;
    }

    private DiagnosticLog diagnostics() {
        return new DiagnosticLog(
                new PrintStream(new ByteArrayOutputStream()),
                LogVerbosity.INFO);
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
