package io.github.dependencyanalysis.models.jdk8;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Language;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXCFABuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXInstanceKeys;
import com.ibm.wala.ipa.callgraph.propagation.rta.BasicRTABuilder;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.SummarizedMethod;
import com.ibm.wala.types.ClassLoaderReference;
import io.github.dependencyanalysis.models.jdk.JdkModelMetadata;
import io.github.dependencyanalysis.models.jdk.JdkModelSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Direct WALA fixed-point acceptance across supported algorithms. */
class Jdk8ModelFixedPointAcceptanceTest {

    /** Minimum catalog targets expected to be exercised. */
    private static final int MINIMUM_HITS = 10;

    /** Required application callbacks and downstream methods. */
    private static final Set<String> REQUIRED_APPLICATION_METHODS = Set.of(
            "fixture/JdkModelFixture$BusinessValue.downstream()V",
            "fixture/JdkModelFixture$BusinessValue.toString()"
                    + "Ljava/lang/String;",
            "fixture/JdkModelFixture$Mapper.apply(Ljava/lang/Object;)"
                    + "Ljava/lang/Object;",
            "fixture/JdkModelFixture$DateQuery.queryFrom("
                    + "Ljava/time/temporal/TemporalAccessor;)"
                    + "Ljava/lang/Object;",
            "fixture/JdkModelFixture$Task.run()V",
            "fixture/JdkModelFixture$Visitor.visitFile("
                    + "Ljava/lang/Object;"
                    + "Ljava/nio/file/attribute/BasicFileAttributes;)"
                    + "Ljava/nio/file/FileVisitResult;",
            "fixture/JdkModelFixture$NonSerializableBase.<init>()V",
            "fixture/JdkModelFixture$SerialValue.writeObject("
                    + "Ljava/io/ObjectOutputStream;)V",
            "fixture/JdkModelFixture$SerialValue.readObject("
                    + "Ljava/io/ObjectInputStream;)V",
            "fixture/JdkModelFixture$SerialValue.readObjectNoData()V",
            "fixture/JdkModelFixture$SerialValue.writeReplace()"
                    + "Ljava/lang/Object;",
            "fixture/JdkModelFixture$SerialValue.readResolve()"
                    + "Ljava/lang/Object;",
            "fixture/JdkModelFixture$ExternalValue.writeExternal("
                    + "Ljava/io/ObjectOutput;)V",
            "fixture/JdkModelFixture$ExternalValue.readExternal("
                    + "Ljava/io/ObjectInput;)V",
            "fixture/JdkModelFixture$Validation.validateObject()V",
            "fixture/JdkModelFixture$InputHooks.resolveObject("
                    + "Ljava/lang/Object;)Ljava/lang/Object;",
            "fixture/JdkModelFixture$InputHooks.resolveClass("
                    + "Ljava/io/ObjectStreamClass;)Ljava/lang/Class;",
            "fixture/JdkModelFixture$InputHooks.readClassDescriptor()"
                    + "Ljava/io/ObjectStreamClass;",
            "fixture/JdkModelFixture$OutputHooks.replaceObject("
                    + "Ljava/lang/Object;)Ljava/lang/Object;",
            "fixture/JdkModelFixture$OutputHooks.annotateClass("
                    + "Ljava/lang/Class;)V",
            "fixture/JdkModelFixture$OutputHooks.writeClassDescriptor("
                    + "Ljava/io/ObjectStreamClass;)V");

    /** Optimized ZeroX instance-key policy used by the application. */
    private static final int OPTIMIZED_POLICY =
            ZeroXInstanceKeys.ALLOCATIONS
                    | ZeroXInstanceKeys.CONSTANT_SPECIFIC
                    | ZeroXInstanceKeys.SMUSH_MANY
                    | ZeroXInstanceKeys.SMUSH_PRIMITIVE_HOLDERS
                    | ZeroXInstanceKeys.SMUSH_STRINGS
                    | ZeroXInstanceKeys.SMUSH_THROWABLES;

    /** Compiled Java 8 fixture. */
    private static Path fixtureClasses;

    @BeforeAll
    static void compileFixture() throws Exception {
        fixtureClasses = TestHierarchies.compileFixture();
    }

    @ParameterizedTest
    @EnumSource(Algorithm.class)
    void conservativeModelsPreserveApplicationReachability(
            final Algorithm algorithm) throws Exception {
        final BuildResult baseline = build(algorithm, false,
                "Lfixture/ReachabilityFixture");
        final BuildResult comparable = build(algorithm, true,
                "Lfixture/ReachabilityFixture");
        final BuildResult modeled = build(algorithm, true,
                "Lfixture/JdkModelFixture");

        assertThat(comparable.applicationMethods())
                .containsAll(baseline.applicationMethods());
        assertThat(modeled.hitTargets()).anyMatch(target ->
                target.contains("ObjectInputStream")
                        && target.contains("registerValidation"));
        assertThat(modeled.hitTargets()).anyMatch(target ->
                target.contains("ObjectInputStream")
                        && target.contains("readObject"));
        assertThat(modeled.applicationMethods())
                .containsAll(REQUIRED_APPLICATION_METHODS);
        assertThat(modeled.summarizedTargets()).isNotEmpty();
        assertThat(modeled.hitCount()).isGreaterThan(MINIMUM_HITS);
        assertThat(modeled.jdkMethods()).noneMatch(method ->
                method.contains("java/util/stream/ReferencePipeline"));
        assertThat(modeled.jdkMethods()).noneMatch(method ->
                method.contains("AbstractQueuedSynchronizer.acquire"));
        assertThat(modeled.jdkMethods()).noneMatch(method ->
                method.contains("ObjectInputStream.readObject0"));
        assertThat(modeled.jdkMethods()).noneMatch(method ->
                method.contains("sun/net/www/protocol"));
        record(algorithm, baseline, comparable, modeled);
    }

    private BuildResult build(
            final Algorithm algorithm,
            final boolean modelsEnabled,
            final String entrypoint) throws Exception {
        final IClassHierarchy hierarchy = TestHierarchies.jdk8(
                fixtureClasses);
        final AnalysisOptions options = new AnalysisOptions(
                hierarchy.getScope(), Util.makeMainEntrypoints(
                        hierarchy.getScope(), hierarchy,
                        entrypoint));
        options.setReflectionOptions(
                AnalysisOptions.ReflectionOptions.NONE);
        Util.addDefaultSelectors(options, hierarchy);
        Util.addDefaultBypassLogic(options, Util.class.getClassLoader(),
                hierarchy);
        final JdkModelSession session = modelsEnabled
                ? Jdk8Models.install(options, hierarchy) : null;
        final AnalysisCacheImpl cache = new AnalysisCacheImpl();
        final CallGraphBuilder<?> builder = algorithm.builder(
                hierarchy, options, cache);
        final long started = System.nanoTime();
        final CallGraph graph = builder.makeCallGraph(options, null);
        final long elapsed = System.nanoTime() - started;
        final Set<String> application = new LinkedHashSet<>();
        final Set<String> jdk = new LinkedHashSet<>();
        final Set<String> summarized = new LinkedHashSet<>();
        int edges = 0;
        for (CGNode node : graph) {
            final IMethod method = node.getMethod();
            final String identity = identity(method);
            if (method.getDeclaringClass().getClassLoader().getReference()
                    .equals(ClassLoaderReference.Application)) {
                application.add(identity);
            } else if (method.getDeclaringClass().getClassLoader()
                    .getReference().equals(
                            ClassLoaderReference.Primordial)) {
                jdk.add(identity);
            }
            if (method instanceof SummarizedMethod) {
                summarized.add(identity);
            }
            edges += graph.getSuccNodeCount(node);
        }
        final JdkModelMetadata metadata = session == null ? null
                : session.snapshot();
        final int hits = metadata == null ? 0
                : metadata.hitTargetCount();
        final Set<String> hitTargets = metadata == null ? Set.of()
                : Set.copyOf(metadata.hitTargets());
        return new BuildResult(application, jdk, summarized,
                elapsed, graph.getNumberOfNodes(), edges, hits,
                hitTargets);
    }

    private String identity(final IMethod method) {
        String owner = method.getDeclaringClass().getName().toString();
        if (owner.startsWith("L")) {
            owner = owner.substring(1);
        }
        return owner + "." + method.getSelector();
    }

    private void record(
            final Algorithm algorithm,
            final BuildResult baseline,
            final BuildResult comparable,
            final BuildResult modeled) throws Exception {
        final Path report = Path.of("target",
                "jdk8-model-acceptance.tsv");
        Files.createDirectories(report.getParent());
        final String line = algorithm + "\tbaseline\t"
                + baseline.metrics(false) + System.lineSeparator()
                + algorithm + "\tcomparable\t"
                + comparable.metrics(true) + System.lineSeparator()
                + algorithm + "\tcoverage\t"
                + modeled.metrics(true) + System.lineSeparator();
        Files.writeString(report, line, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    /** Supported standalone acceptance algorithms. */
    private enum Algorithm {
        /** Basic rapid type analysis. */
        RTA {
            @Override
            CallGraphBuilder<?> builder(
                    final IClassHierarchy hierarchy,
                    final AnalysisOptions options,
                    final AnalysisCacheImpl cache) {
                return new BasicRTABuilder(hierarchy, options, cache,
                        null, null);
            }
        },
        /** Class-based zero-context flow analysis. */
        ZERO_CFA {
            @Override
            CallGraphBuilder<?> builder(
                    final IClassHierarchy hierarchy,
                    final AnalysisOptions options,
                    final AnalysisCacheImpl cache) {
                return ZeroXCFABuilder.make(Language.JAVA, hierarchy,
                        options, cache, null, null,
                        ZeroXInstanceKeys.CONSTANT_SPECIFIC);
            }
        },
        /** Allocation-sensitive optimized ZeroX policy. */
        OPTIMIZED_ZERO_X {
            @Override
            CallGraphBuilder<?> builder(
                    final IClassHierarchy hierarchy,
                    final AnalysisOptions options,
                    final AnalysisCacheImpl cache) {
                return ZeroXCFABuilder.make(Language.JAVA, hierarchy,
                        options, cache, null, null, OPTIMIZED_POLICY);
            }
        };

        abstract CallGraphBuilder<?> builder(
                IClassHierarchy hierarchy,
                AnalysisOptions options,
                AnalysisCacheImpl cache);
    }

    /**
     * One graph and its acceptance metrics.
     *
     * @param applicationMethods reachable application methods
     * @param jdkMethods reachable JDK methods
     * @param summarizedTargets reachable WALA summarized methods
     * @param elapsedNanos Call Graph build elapsed time
     * @param nodes Call Graph node count
     * @param edges Call Graph edge count
     * @param hitCount exact JDK model hit count
     * @param hitTargets exact JDK model hit identities
     */
    private record BuildResult(
            Set<String> applicationMethods,
            Set<String> jdkMethods,
            Set<String> summarizedTargets,
            long elapsedNanos,
            int nodes,
            int edges,
            int hitCount,
            Set<String> hitTargets) {

        String metrics(final boolean modeled) {
            return modeled + "\t" + elapsedNanos + "\t" + nodes
                    + "\t" + edges + "\t" + jdkMethods.size()
                    + "\t" + hitCount;
        }
    }
}
