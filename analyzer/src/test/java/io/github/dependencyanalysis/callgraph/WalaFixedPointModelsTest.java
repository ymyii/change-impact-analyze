package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.ContextItem;
import com.ibm.wala.ipa.callgraph.propagation.cfa.CallStringContextSelector;
import com.ibm.wala.ipa.callgraph.propagation.cfa.nObjContextSelector;
import com.ibm.wala.ipa.summaries.BypassSyntheticClass;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAPhiInstruction;
import com.ibm.wala.ssa.SSAReturnInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;
import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.bytecode.MemberDescriptors;
import io.github.dependencyanalysis.impact.BoundChangePoint;
import io.github.dependencyanalysis.impact.DependencyUpgradeKey;
import io.github.dependencyanalysis.impact.ModuleAnalysisUnit;
import io.github.dependencyanalysis.impact.ModuleChangeSet;
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
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** End-to-end fixed-point model tests against the configured JDK 8. */
class WalaFixedPointModelsTest {

    /** Keeps a malformed model from exhausting the test JVM. */
    private static final long GRAPH_TIMEOUT_SECONDS = 30L;

    /** Constructor receiver plus entry receiver and five arguments. */
    private static final int DECLARED_PARAMETER_CANDIDATES = 7;

    /** Concrete types that must not expand declared entrypoint parameters. */
    private static final int UNUSED_IMPLEMENTATION_COUNT = 32;

    /** Unsupported MethodHandle receiver sources in the negative fixture. */
    private static final int UNRESOLVED_HANDLE_SOURCE_COUNT = 3;

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

            assertThatThrownBy(() -> engine.build(unit,
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
                            CallGraphAlgorithm.RTA,
                            WalaReflectionOptions.parse("NONE"),
                            JdkModelSelection.NONE, repository)
                            .build(unit, manualIndex,
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
    void oneObjectOneCallSiteCarriesBothBoundedContexts()
            throws Exception {
        final Path classes = compile("ContextPrecision", """
                public class ContextPrecision {
                    public void entry() {
                        callA(new Receiver());
                        callB(new Receiver());
                    }
                    static void callA(Receiver receiver) {
                        receiver.target();
                    }
                    static void callB(Receiver receiver) {
                        receiver.target();
                    }
                    static class Receiver {
                        void target() { }
                    }
                }
                """);

        try (IJarRepository repository = TestJarRepositories.empty()) {
            final ModuleCallGraphSession session = build(
                    classes, repository,
                    CallGraphAlgorithm.ONE_OBJECT_ONE_CALL_SITE,
                    WalaReflectionOptions.parse("NONE"));
            final List<CGNode> contextualTargets = new ArrayList<>();
            for (CGNode node : session.getGraph()) {
                if (owner(node).equals("ContextPrecision$Receiver")
                        && node.getMethod().getName().toString()
                        .equals("target")
                        && node.getContext().get(
                        nObjContextSelector.ALLOCATION_STRING_KEY) != null
                        && node.getContext().get(
                        CallStringContextSelector.CALL_STRING) != null) {
                    contextualTargets.add(node);
                }
            }
            final Set<ContextItem> allocations = new HashSet<>();
            final Set<ContextItem> calls = new HashSet<>();
            contextualTargets.forEach(node -> {
                allocations.add(node.getContext().get(
                        nObjContextSelector.ALLOCATION_STRING_KEY));
                calls.add(node.getContext().get(
                        CallStringContextSelector.CALL_STRING));
            });

            assertThat(contextualTargets).hasSizeGreaterThanOrEqualTo(2);
            assertThat(allocations).hasSizeGreaterThanOrEqualTo(2);
            assertThat(calls).hasSizeGreaterThanOrEqualTo(2);
        }
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
            final DiagnosticLog collector = diagnostics();
            try (IJarRepository repository = TestJarRepositories.empty()) {
                new ModuleCallGraphEngine(collector, runtime,
                        EntrypointSelection.allProjectClasses(), algorithm,
                        WalaReflectionOptions.defaultOptions(),
                        JdkModelSelection.NONE, repository)
                        .build(unit, GRAPH_TIMEOUT_SECONDS);
            }
            assertThat(collector.getEvents())
                    .anySatisfy(event -> assertThat(event.getMessage())
                            .startsWith("algorithm="
                                    + algorithm.identifier() + ";")
                            .contains("jdkModel=none")
                            .doesNotContain("availableTarget",
                                    "unavailableTarget", "hitTarget"));
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
                final ModuleCallGraphSession session = build(
                        classes, repository, algorithm,
                        WalaReflectionOptions.parse("NONE"));
                if (algorithm == CallGraphAlgorithm.RTA) {
                    assertThat(session.hasServiceLoaderLimitations())
                            .isTrue();
                } else {
                    assertThat(session.getModelLimitations())
                            .as(algorithm.identifier()).isEmpty();
                }
                assertThat(hasEdge(session,
                        "ServiceApp", "execute",
                        "ServiceApp$Provider", "run"))
                        .as(algorithm.identifier()).isTrue();
                if (algorithm != CallGraphAlgorithm.RTA) {
                    assertThat(hasEdge(session,
                            "ServiceApp", "executeFromField",
                            "ServiceApp$Provider", "run"))
                            .as(algorithm.identifier()).isTrue();
                }
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
    void zeroCfaConstantKeysPreserveServiceLoaderProviderPaths()
            throws Exception {
        final Path classes = compile("ZeroCfaServices", """
                import java.util.ServiceLoader;
                public class ZeroCfaServices {
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
        writeServiceResource(classes, "ZeroCfaServices$First",
                "ZeroCfaServices$FirstProvider\n");
        writeServiceResource(classes, "ZeroCfaServices$Second",
                "ZeroCfaServices$SecondProvider\n");

        try (IJarRepository repository = TestJarRepositories.empty()) {
            final ModuleCallGraphSession session = build(
                    classes, repository, CallGraphAlgorithm.ZERO_CFA);
            assertThat(hasEdge(session, "ZeroCfaServices", "first",
                    "ZeroCfaServices$FirstProvider", "run")).isTrue();
            assertThat(hasEdge(session, "ZeroCfaServices", "second",
                    "ZeroCfaServices$SecondProvider", "run"))
                    .as("relevant edges: %s", serviceEdges(session))
                    .isTrue();
            assertThat(hasEdge(session, "ZeroCfaServices", "first",
                    "ZeroCfaServices$SecondProvider", "run")).isFalse();
            assertThat(hasEdge(session, "ZeroCfaServices", "second",
                    "ZeroCfaServices$FirstProvider", "run")).isFalse();
            assertThat(session.getModelLimitations()).isEmpty();
        }
    }

    @Test
    void zeroCfaClassBasedServiceLoaderReceiverUsesProviderUnion()
            throws Exception {
        final Path classes = compile("ZeroCfaAggregateServices", """
                import java.util.ServiceLoader;
                public class ZeroCfaAggregateServices {
                    public interface Action { void run(); }
                    public interface First extends Action { }
                    public interface Second extends Action { }
                    public static class FirstProvider implements First {
                        public FirstProvider() { }
                        public void run() { }
                    }
                    public static class SecondProvider implements Second {
                        public SecondProvider() { }
                        public void run() { }
                    }
                    public void consume(
                            ServiceLoader<? extends Action> services) {
                        services.iterator().next().run();
                    }
                }
                """);
        writeServiceResource(classes, "ZeroCfaAggregateServices$First",
                "ZeroCfaAggregateServices$FirstProvider\n");
        writeServiceResource(classes, "ZeroCfaAggregateServices$Second",
                "ZeroCfaAggregateServices$SecondProvider\n");

        try (IJarRepository repository = TestJarRepositories.empty()) {
            final ModuleCallGraphSession session = build(
                    classes, repository, CallGraphAlgorithm.ZERO_CFA);
            assertThat(serviceEdges(session))
                    .contains(
                            "ZeroCfaAggregateServices.consume -> "
                                    + "wala/serviceloader/Iterator$Lwala$"
                                    + "serviceloader$AllConfiguredServices."
                                    + "next",
                            "wala/serviceloader/Iterator$Lwala$serviceloader$"
                                    + "AllConfiguredServices.next -> "
                                    + "ZeroCfaAggregateServices$FirstProvider."
                                    + "<init>",
                            "wala/serviceloader/Iterator$Lwala$serviceloader$"
                                    + "AllConfiguredServices.next -> "
                                    + "ZeroCfaAggregateServices$SecondProvider."
                                    + "<init>");
            assertThat(session.getModelLimitations()).isEmpty();
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
    void rtaServiceLoaderProviderFlowsIntoInterfaceDispatch()
            throws Exception {
        final Path classes = compile("RtaServiceApp", """
                import java.util.ServiceLoader;
                public class RtaServiceApp {
                    interface Service { void run(); }
                    public static class Provider implements Service {
                        public Provider() { }
                        public void run() { helper(); }
                        static void helper() { }
                    }
                    public void execute() {
                        for (Service service
                                : ServiceLoader.load(Service.class)) {
                            service.run();
                        }
                    }
                }
                """);
        writeServiceResource(classes, "RtaServiceApp$Service",
                "RtaServiceApp$Provider\n");

        try (IJarRepository repository = TestJarRepositories.empty()) {
            final ModuleCallGraphSession session = build(
                    classes, repository, CallGraphAlgorithm.RTA);
            assertThat(session.getModelLimitations()).isEmpty();
            assertThat(hasEdge(session, "RtaServiceApp", "execute",
                    "RtaServiceApp$Provider", "run")).isTrue();
            assertRtaServiceLoaderHasNoReturnCarriers(session);
        }
    }

    @Test
    void rtaServiceLoaderRecoversContractFromProviderCheckcast()
            throws Exception {
        final Path classes = compile("RtaCheckcastServiceApp", """
                import java.util.ServiceLoader;
                public class RtaCheckcastServiceApp {
                    interface Service { void run(); }
                    public static class Provider implements Service {
                        public Provider() { }
                        public void run() { helper(); }
                        static void helper() { }
                    }
                    public void execute(Class<Service> serviceType) {
                        for (Service service
                                : ServiceLoader.load(serviceType)) {
                            service.run();
                        }
                    }
                }
                """);
        writeServiceResource(classes, "RtaCheckcastServiceApp$Service",
                "RtaCheckcastServiceApp$Provider\n");

        try (IJarRepository repository = TestJarRepositories.empty()) {
            final ModuleCallGraphSession session = build(
                    classes, repository, CallGraphAlgorithm.RTA);
            assertThat(hasEdge(session, "RtaCheckcastServiceApp", "execute",
                    "RtaCheckcastServiceApp$Provider", "run")).isTrue();
            assertThat(session.hasServiceLoaderLimitations()).isFalse();
        }
    }

    @Test
    void methodHandleTargetIsReachableAcrossAlgorithms()
            throws Exception {
        final Path classes = compile("RtaMethodHandleApp", """
                import java.lang.invoke.MethodHandle;
                import java.lang.invoke.MethodHandles;
                import java.lang.invoke.MethodType;
                public class RtaMethodHandleApp {
                    static void target() { }
                    public void execute() throws Throwable {
                        MethodHandle handle = MethodHandles.lookup()
                                .findStatic(RtaMethodHandleApp.class, "target",
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
                                .ONE_OBJECT_ONE_CALL_SITE
                        ? build(classes, repository, algorithm,
                                WalaReflectionOptions.parse("NONE"),
                                minimalRuntime)
                        : build(classes, repository, algorithm,
                                WalaReflectionOptions.parse("NONE"));
                assertThat(hasPath(session,
                        "RtaMethodHandleApp", "execute",
                        "RtaMethodHandleApp", "target"))
                        .as(algorithm.identifier()).isTrue();
                if (algorithm == CallGraphAlgorithm.RTA) {
                    assertThat(hasMethodHandleBridgePath(session,
                            "RtaMethodHandleApp", "execute",
                            "RtaMethodHandleApp", "target")).isTrue();
                    assertThat(session.getDynamicEvidence().find(
                            "RtaMethodHandleApp", "target", "()V"))
                            .anySatisfy(evidence -> {
                                assertThat(evidence.kind()).isEqualTo(
                                        EdgeKind.METHOD_HANDLE_TARGET);
                                assertThat(evidence.detail()).contains(
                                        "operation=INVOKE_EXACT");
                            });
                }
                assertThat(session.hasMethodHandleLimitations()).isFalse();
            }
        }
    }

    @Test
    void rtaReportsUnresolvedMethodHandleReceiver()
            throws Exception {
        final Path classes = compile("RtaUnknownMethodHandleApp", """
                import java.lang.invoke.MethodHandle;
                public class RtaUnknownMethodHandleApp {
                    public void execute(MethodHandle handle) throws Throwable {
                        handle.invokeExact();
                    }
                }
                """);

        try (IJarRepository repository = TestJarRepositories.empty()) {
            final ModuleCallGraphSession session = build(
                    classes, repository, CallGraphAlgorithm.RTA);
            assertThat(session.hasMethodHandleLimitations()).isTrue();
            assertThat(session.getModelLimitations()).anySatisfy(value ->
                    assertThat(value).contains(
                            "RTA_METHOD_HANDLE_LOCAL_TARGET_UNRESOLVED"));
        }
    }

    @Test
    void rtaDoesNotGuessFieldCrossMethodOrDivergentPhiHandles()
            throws Exception {
        final Path classes = compile("RtaUnresolvedHandleSources", """
                import java.lang.invoke.MethodHandle;
                import java.lang.invoke.MethodHandles;
                import java.lang.invoke.MethodType;
                public class RtaUnresolvedHandleSources {
                    static MethodHandle saved;
                    static void first() { }
                    static void second() { }
                    static MethodHandle create() throws Exception {
                        return MethodHandles.lookup().findStatic(
                                RtaUnresolvedHandleSources.class, "first",
                                MethodType.methodType(void.class));
                    }
                    public void fromField() throws Throwable {
                        saved.invokeExact();
                    }
                    public void fromOtherMethod() throws Throwable {
                        create().invokeExact();
                    }
                    public void fromDivergentPhi(boolean choose)
                            throws Throwable {
                        MethodHandle handle;
                        if (choose) {
                            handle = MethodHandles.lookup().findStatic(
                                    RtaUnresolvedHandleSources.class,
                                    "first",
                                    MethodType.methodType(void.class));
                        } else {
                            handle = MethodHandles.lookup().findStatic(
                                    RtaUnresolvedHandleSources.class,
                                    "second",
                                    MethodType.methodType(void.class));
                        }
                        handle.invokeExact();
                    }
                }
                """);

        try (IJarRepository repository = TestJarRepositories.empty()) {
            final ModuleCallGraphSession session = build(
                    classes, repository, CallGraphAlgorithm.RTA,
                    WalaReflectionOptions.parse("NONE"));
            assertThat(session.getModelLimitations().stream()
                    .filter(value -> value.contains(
                            "RTA_METHOD_HANDLE_LOCAL_TARGET_UNRESOLVED")))
                    .hasSizeGreaterThanOrEqualTo(
                            UNRESOLVED_HANDLE_SOURCE_COUNT);
            for (String method : List.of(
                    "fromField", "fromOtherMethod", "fromDivergentPhi")) {
                assertThat(hasPath(session,
                        "RtaUnresolvedHandleSources", method,
                        "RtaUnresolvedHandleSources", "first"))
                        .as(method).isFalse();
                assertThat(hasPath(session,
                        "RtaUnresolvedHandleSources", method,
                        "RtaUnresolvedHandleSources", "second"))
                        .as(method).isFalse();
            }
        }
    }

    @Test
    void rtaMethodHandleTypeKeepsApiReachabilityWithoutFakeValue()
            throws Exception {
        final Path classes = compile("RtaMethodHandleType", """
                import java.lang.invoke.MethodHandle;
                import java.lang.invoke.MethodHandles;
                import java.lang.invoke.MethodType;
                public class RtaMethodHandleType {
                    static void target() { }
                    public MethodType inspect() throws Throwable {
                        MethodHandle handle = MethodHandles.lookup()
                                .findStatic(RtaMethodHandleType.class,
                                        "target",
                                        MethodType.methodType(void.class));
                        return handle.type();
                    }
                }
                """);

        try (IJarRepository repository = TestJarRepositories.empty()) {
            final ModuleCallGraphSession session = build(
                    classes, repository, CallGraphAlgorithm.RTA,
                    WalaReflectionOptions.parse("NONE"));
            assertThat(session.getGraph()).anySatisfy(node -> {
                assertThat(owner(node)).isEqualTo(
                        "java/lang/invoke/MethodHandle");
                assertThat(node.getMethod().getName().toString())
                        .isEqualTo("type");
            });
            assertThat(session.hasMethodHandleLimitations()).isFalse();
            assertThat(session.getDynamicEvidence().find(
                    "RtaMethodHandleType", "target", "()V"))
                    .isEmpty();
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
                    public static Base task(String captured) {
                        return (Base & Narrow & Marker & Serializable)
                                () -> captured;
                    }
                }
                """);
        final Path classes = compile("AltLambdaApp", """
                public class AltLambdaApp {
                    public Object execute() {
                        return AltFactory.task("value").get();
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
                                + session.getModelLimitations()).isFalse();
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
                                        .startsWith("lambda$task$");
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
                    public Runnable task() {
                        return LambdaLibrary::implementation;
                    }
                    public void execute() {
                        task().run();
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
                                .trace(unit, session);
                assertThat(query.getPaths())
                        .as(algorithm.identifier()).anySatisfy(path -> {
                            assertThat(path.getAffectedMethod().owner())
                                    .isEqualTo("RemovedLambdaApp");
                            assertThat(path.getTerminal().getEdgeKind())
                                    .isEqualTo(EdgeKind
                                            .INVOKEDYNAMIC_HANDLE_REFERENCE);
                        });
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
                            assertThat(evidence.kind()).isEqualTo(
                                    EdgeKind.INVOKEDYNAMIC_BOOTSTRAP);
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
                                + session.getModelLimitations()).isFalse();
                assertThat(hasEdge(session,
                        "CustomDynamicApp", "execute",
                        "CustomDynamicApp", "helper"))
                        .as(algorithm.identifier()).isTrue();
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
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId(), ModulePresence.BOTH, classes, List.of(),
                List.of(), List.of(),
                new ModuleChangeSet(List.of(), List.of()));
        final JavaRuntimeDescriptor runtime = new Jdk8RuntimeProvider()
                .probe(Path.of(System.getenv("TEST_JDK8_HOME")));
        return new ModuleCallGraphEngine(diagnostics(), runtime,
                EntrypointSelection.allProjectClasses(), algorithm,
                reflectionOptions, JdkModelSelection.NONE, repository).build(
                unit, GRAPH_TIMEOUT_SECONDS);
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
                unit, GRAPH_TIMEOUT_SECONDS);
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
                unit, GRAPH_TIMEOUT_SECONDS);
    }

    private ModuleCallGraphSession build(
            final ModuleAnalysisUnit unit,
            final IJarRepository repository,
            final InvokeDynamicBootstrapModelRegistry registry)
            throws Exception {
        return build(unit, repository, registry,
                EntrypointSelection.allProjectClasses(),
                CallGraphAlgorithm.OPTIMIZED_ZERO_ONE_CFA);
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
                CallGraphAlgorithm.OPTIMIZED_ZERO_ONE_CFA);
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
                .build(unit, GRAPH_TIMEOUT_SECONDS);
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

    private boolean hasMethodHandleBridgePath(
            final ModuleCallGraphSession session,
            final String callerOwner,
            final String callerName,
            final String targetOwner,
            final String targetName) {
        for (CGNode caller : session.getGraph()) {
            if (!callerOwner.equals(owner(caller))
                    || !callerName.equals(
                    caller.getMethod().getName().toString())) {
                continue;
            }
            for (CGNode bridge : successors(session, caller)) {
                if (!"wala/methodhandle/RtaBridge".equals(owner(bridge))
                        || !bridge.getMethod().getName().toString()
                        .startsWith("wala$rta$methodHandle$")) {
                    continue;
                }
                for (CGNode target : successors(session, bridge)) {
                    if (targetOwner.equals(owner(target))
                            && targetName.equals(target.getMethod()
                            .getName().toString())) {
                        return true;
                    }
                }
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

    private List<String> serviceEdges(
            final ModuleCallGraphSession session) {
        final List<String> result = new ArrayList<>();
        for (CGNode caller : session.getGraph()) {
            final String callerOwner = owner(caller);
            if (!callerOwner.contains("ZeroCfa")
                    && !callerOwner.contains("AllConfiguredServices")) {
                continue;
            }
            for (CGNode callee : successors(session, caller)) {
                result.add(callerOwner + "."
                        + caller.getMethod().getName() + " -> "
                        + owner(callee) + "."
                        + callee.getMethod().getName());
            }
        }
        return result.stream().sorted().toList();
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

    private void assertRtaServiceLoaderHasNoReturnCarriers(
            final ModuleCallGraphSession session) {
        int modeled = 0;
        for (CGNode node : session.getGraph()) {
            final String type = owner(node);
            final String method = node.getMethod().getName().toString();
            final boolean summary = "java/util/ServiceLoader".equals(type)
                    && node.getContext().get(
                    RtaServiceLoaderModel.SERVICE_TYPE_KEY) != null
                    && ("load".equals(method)
                    || "loadInstalled".equals(method)
                    || "iterator".equals(method))
                    || type.startsWith("wala/serviceloader/Iterator$")
                    && "next".equals(method);
            if (!summary) {
                continue;
            }
            modeled++;
            final IR ir = node.getIR();
            assertThat(ir).as(type + "." + method).isNotNull();
            assertThat(ir.getInstructions())
                    .as(type + "." + method + " has no provider phi")
                    .noneMatch(SSAPhiInstruction.class::isInstance);
            for (SSAInstruction instruction : ir.getInstructions()) {
                if (instruction instanceof SSAReturnInstruction value
                        && !value.returnsVoid()
                        && !value.returnsPrimitiveType()) {
                    assertThat(ir.getSymbolTable().isNullConstant(
                            value.getResult()))
                            .as(type + "." + method + " return")
                            .isTrue();
                }
            }
        }
        assertThat(modeled).isPositive();
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
}
