package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.analysis.reflection.ClassFactoryContextSelector;
import com.ibm.wala.analysis.reflection.JavaTypeContext;
import com.ibm.wala.analysis.typeInference.TypeAbstraction;
import com.ibm.wala.classLoader.BinaryDirectoryTreeModule;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.CallGraphBuilderCancelException;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextItem;
import com.ibm.wala.ipa.callgraph.ContextKey;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.ConstantKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PointerAnalysis;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa.AllocationString;
import com.ibm.wala.ipa.callgraph.propagation.cfa.nObjContextSelector;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.impact.ModuleAnalysisUnit;
import io.github.dependencyanalysis.impact.ModuleChangeSet;
import io.github.dependencyanalysis.impact.ModuleId;
import io.github.dependencyanalysis.impact.ModulePresence;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.Jdk8RuntimeProvider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarFile;
import javax.tools.ToolProvider;

import static org.assertj.core.api.Assertions.assertThat;

/** Guards ClassFactory Context shape under k-object Reflection analysis. */
class KObjClassFactoryContextRegressionTest {

    /** Maximum cooperative fixed-point duration. */
    private static final long TIMEOUT_SECONDS = 300L;

    /** Temporary Java source and classes. */
    @TempDir
    private Path temporary;

    @Test
    void classFactoryContextsRemainTypedWithoutLosingKObjectContext()
            throws Exception {
        final KObjClassFactoryContextValidation result =
                KObjClassFactoryContextTestHarness.run(
                        temporary, TIMEOUT_SECONDS);

        System.out.println("KObj ClassFactory regression status="
                + result.status() + "; nodes="
                + result.graph().getNumberOfNodes()
                + "; classFactoryContexts="
                + result.classFactoryContextNodeCount()
                + "; malformed="
                + result.malformedClassFactoryContextNodeCount()
                + "; duplicateJavaTypeContexts="
                + result.duplicateJavaTypeContextCount()
                + "; reflectionConstants="
                + result.reflectionConstantContextNodeCount()
                + "; allocationContexts="
                + result.allocationContextNodeCount()
                + "; maxAllocationDepth="
                + result.maxAllocationStringDepth());
        assertThat(result.selectedClassCount()).isOne();
        assertThat(result.declaredEntrypointCount()).isOne();
        assertThat(result.reflectionApiPath()).isTrue();
        assertThat(result.classFactoryApiPath()).isTrue();
        assertThat(result.classFactoryContextNodeCount()).isPositive();
        assertThat(result.malformedClassFactoryContextNodeCount()).isZero();
        assertThat(result.duplicateJavaTypeContextCount()).isZero();
        assertThat(result.reflectionConstantContextNodeCount()).isPositive();
        assertThat(result.allocationContextNodeCount()).isPositive();
        assertThat(result.maxAllocationStringDepth()).isOne();
        assertThat(result.pointerAnalysis()).isNotNull();
    }
}

/** Test-only production-equivalent k-object Reflection harness. */
final class KObjClassFactoryContextTestHarness {

    /** Regression fixture owner. */
    private static final String FIXTURE =
            "ClassFactoryContextRegression";

    /** Fixed k-object depth. */
    private static final int K_OBJ_DEPTH = 1;

    private KObjClassFactoryContextTestHarness() {
    }

    static KObjClassFactoryContextValidation run(
            final Path temporary,
            final long timeoutSeconds) throws Exception {
        final KObjClassFactoryPreparedAnalysis prepared = prepare(temporary);
        final CallGraphTimeoutMonitor monitor =
                new CallGraphTimeoutMonitor(
                        Duration.ofSeconds(timeoutSeconds));
        final KObjClassFactoryFixedPoint fixedPoint = fixedPoint(
                prepared, monitor);
        return inspect(prepared, fixedPoint.graph(),
                fixedPoint.pointerAnalysis(), fixedPoint.status());
    }

    private static KObjClassFactoryFixedPoint fixedPoint(
            final KObjClassFactoryPreparedAnalysis prepared,
            final CallGraphTimeoutMonitor monitor) throws Exception {
        try {
            final CallGraph graph = prepared.builder().makeCallGraph(
                    prepared.options(), monitor);
            return new KObjClassFactoryFixedPoint(
                    graph, prepared.builder().getPointerAnalysis(),
                    KObjClassFactoryContextStatus.COMPLETE);
        } catch (CallGraphBuilderCancelException exception) {
            final CallGraph graph = exception.getPartialCallGraph() == null
                    ? prepared.builder().getCallGraph()
                    : exception.getPartialCallGraph();
            final PointerAnalysis<InstanceKey> pointerAnalysis =
                    exception.getPartialPointerAnalysis() == null
                    ? prepared.builder().getPointerAnalysis()
                    : exception.getPartialPointerAnalysis();
            return new KObjClassFactoryFixedPoint(
                    graph, pointerAnalysis,
                    KObjClassFactoryContextStatus.TIMED_OUT_PARTIAL);
        }
    }

    static KObjClassFactoryPreparedAnalysis prepare(
            final Path temporary) throws Exception {
        final Path classes = compile(temporary);
        final JavaRuntimeDescriptor runtime = new Jdk8RuntimeProvider()
                .probe(Path.of(System.getenv("TEST_JDK8_HOME")));
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                new ModuleId(new ArtifactCoord(
                        "test", "classfactory-context", "jar", "1"),
                        Path.of(".")),
                ModulePresence.BOTH, classes, List.of(), List.of(),
                List.of(), new ModuleChangeSet(List.of(), List.of()));
        final ClassOwnershipIndex ownership = new ClassOwnershipIndex();
        ownership.addDirectory(classes, CodeOrigin.PROJECT);
        final AnalysisScope scope = scope(runtime, classes, ownership);
        final IClassHierarchy hierarchy = ClassHierarchyFactory.make(scope);
        final EntrypointClassIndex index = new EntrypointClassScanner().scan(
                classes, EntrypointSelection.parse(
                        List.of(FIXTURE), List.of()));
        final List<Entrypoint> entrypoints = entrypoints(hierarchy, index);
        final IAnalysisCacheView cache = new AnalysisCacheImpl(
                SSAOptions.defaultOptions());
        final DependencyBodyBoundary boundary =
                new DependencyBodyBoundary(unit, ownership, hierarchy);
        final AnalysisOptions options = options(
                scope, hierarchy, entrypoints);
        JdkModelInstallation.install(
                JdkModelSelection.defaultSelection(), options, hierarchy);
        final KObjInvokeDynamicInstaller dynamic =
                new KObjInvokeDynamicInstaller();
        dynamic.install(options,
                InvokeDynamicBootstrapModelRegistry.jdk8Defaults());
        final SSAPropagationCallGraphBuilder builder =
                new KObjCallGraphBuilder(
                K_OBJ_DEPTH, hierarchy, options, cache,
                KObjCallGraphStrategy.INSTANCE_POLICY);
        new KObjMethodHandleInstaller().install(options, builder);
        final KObjServiceLoaderInstaller serviceLoader =
                new KObjServiceLoaderInstaller(
                        ServiceLoaderProtocolIndex.create(scope, hierarchy),
                        hierarchy);
        serviceLoader.install(builder);
        boundary.install(builder);
        return new KObjClassFactoryPreparedAnalysis(
                builder, options, index.selectedClassNames().size(),
                entrypoints.size());
    }

    private static AnalysisScope scope(
            final JavaRuntimeDescriptor runtime,
            final Path classes,
            final ClassOwnershipIndex ownership) throws Exception {
        final AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();
        final SpringBackendJdkExclusions exclusions =
                new SpringBackendJdkExclusions();
        scope.setExclusions(exclusions);
        addJdk(scope, ClassLoaderReference.Primordial,
                runtime.getBootClassPath(), exclusions, ownership);
        addJdk(scope, ClassLoaderReference.Extension,
                runtime.getExtensionClassPath(), exclusions, ownership);
        scope.addToScope(ClassLoaderReference.Application,
                new OwnershipFilteredModule(
                        new BinaryDirectoryTreeModule(classes.toFile()),
                        classes, ownership));
        return scope;
    }

    private static void addJdk(
            final AnalysisScope scope,
            final ClassLoaderReference loader,
            final List<Path> paths,
            final SpringBackendJdkExclusions exclusions,
            final ClassOwnershipIndex ownership) throws Exception {
        for (Path path : paths) {
            if (exclusions.excludesJar(path)) {
                continue;
            }
            ownership.validateJarDuplicates(
                    path, CodeOrigin.JDK,
                    name -> !exclusions.test(name));
            scope.addToScope(loader, new OwnershipFilteredModule(
                    new JarFileModule(new JarFile(
                            path.toFile(), false)), path, ownership));
        }
    }

    private static List<Entrypoint> entrypoints(
            final IClassHierarchy hierarchy,
            final EntrypointClassIndex index) {
        final List<IMethod> methods = new ArrayList<>();
        for (String name : index.selectedClassNames()) {
            final TypeReference reference = TypeReference.findOrCreate(
                    ClassLoaderReference.Application, "L" + name);
            final IClass type = hierarchy.lookupClass(reference);
            if (type == null) {
                throw new IllegalStateException(
                        "Missing regression entrypoint class " + name);
            }
            type.getDeclaredMethods().stream()
                    .filter(method -> !method.isAbstract())
                    .filter(method -> !method.isPrivate())
                    .forEach(methods::add);
        }
        methods.sort(Comparator.comparing(
                method -> method.getReference().toString()));
        final EntrypointSyntheticTypeRegistry syntheticTypes =
                new EntrypointSyntheticTypeRegistry(hierarchy);
        return methods.stream().map(method -> (Entrypoint)
                new DeclaredTypesEntrypoint(
                        method, hierarchy, syntheticTypes)).toList();
    }

    private static AnalysisOptions options(
            final AnalysisScope scope,
            final IClassHierarchy hierarchy,
            final List<Entrypoint> entrypoints) {
        final AnalysisOptions options = new AnalysisOptions(
                scope, entrypoints);
        options.setReflectionOptions(
                WalaReflectionOptions.defaultOptions().walaValue());
        Util.addDefaultSelectors(options, hierarchy);
        Util.addDefaultBypassLogic(
                options, Util.class.getClassLoader(), hierarchy);
        return options;
    }

    private static Path compile(final Path temporary) throws Exception {
        final Path source = temporary.resolve(FIXTURE + ".java");
        final Path classes = temporary.resolve("classes");
        Files.createDirectories(classes);
        Files.writeString(source, """
                import java.lang.reflect.Method;

                public final class ClassFactoryContextRegression {
                    private ClassFactoryContextRegression() {
                    }

                    public static void entry() throws Exception {
                        new Receiver().touch();
                        Method target = ClassFactoryContextRegression.class
                                .getDeclaredMethod("load");
                        target.invoke(null);
                    }

                    private static Class<?> load() throws Exception {
                        return Class.forName("java.lang.String");
                    }

                    private static final class Receiver {
                        void touch() {
                        }
                    }
                }
                """);
        final int exit = ToolProvider.getSystemJavaCompiler().run(
                null, null, null, "--release", "8", "-d",
                classes.toString(), source.toString());
        if (exit != 0) {
            throw new IllegalStateException(
                    "Unable to compile ClassFactory regression fixture");
        }
        return classes;
    }

    private static KObjClassFactoryContextValidation inspect(
            final KObjClassFactoryPreparedAnalysis prepared,
            final CallGraph graph,
            final PointerAnalysis<InstanceKey> pointerAnalysis,
            final KObjClassFactoryContextStatus status) {
        final Set<CGNode> reachable = reachable(graph);
        int classFactoryContexts = 0;
        int malformedClassFactoryContexts = 0;
        int duplicateJavaTypeContexts = 0;
        int reflectionConstantContexts = 0;
        int allocationContexts = 0;
        int maxAllocationDepth = 0;
        boolean reflectionPath = false;
        boolean classFactoryPath = false;
        for (CGNode node : graph) {
            final Context context = node.getContext();
            final boolean isReachable = reachable.contains(node);
            if (isReachable && isMethod(node,
                    "java/lang/reflect/Method", "invoke")) {
                reflectionPath = true;
            }
            if (isReachable && ClassFactoryContextSelector.isClassFactory(
                    node.getMethod().getReference())) {
                classFactoryPath = true;
            }
            if (ClassFactoryContextSelector.isClassFactory(
                    node.getMethod().getReference())
                    && context.isA(JavaTypeContext.class)) {
                classFactoryContexts++;
                final ContextItem receiver = context.get(
                        ContextKey.RECEIVER);
                if (!(receiver instanceof TypeAbstraction)) {
                    malformedClassFactoryContexts++;
                }
                duplicateJavaTypeContexts += Math.max(0,
                        KObjContextGraphInspector.javaTypeContextCount(
                                context) - 1);
            }
            if (isMethod(node, "java/lang/reflect/Method", "invoke")
                    && context.get(ContextKey.RECEIVER)
                    instanceof ConstantKey<?> key
                    && key.getValue() instanceof IMethod) {
                reflectionConstantContexts++;
            }
            final ContextItem allocation = context.get(
                    nObjContextSelector.ALLOCATION_STRING_KEY);
            if (isReachable && !node.getMethod().isStatic()
                    && allocation instanceof AllocationString value) {
                allocationContexts++;
                maxAllocationDepth = Math.max(maxAllocationDepth,
                        value.allocationSites().length);
            }
        }
        return new KObjClassFactoryContextValidation(
                status, graph, pointerAnalysis,
                prepared.selectedClassCount(),
                prepared.declaredEntrypointCount(), reflectionPath,
                classFactoryPath, classFactoryContexts,
                malformedClassFactoryContexts,
                duplicateJavaTypeContexts,
                reflectionConstantContexts, allocationContexts,
                maxAllocationDepth);
    }

    private static Set<CGNode> reachable(final CallGraph graph) {
        final ArrayDeque<CGNode> pending = new ArrayDeque<>();
        for (CGNode node : graph.getEntrypointNodes()) {
            if (isMethod(node, FIXTURE, "entry")) {
                pending.add(node);
            }
        }
        final Set<CGNode> result = new HashSet<>();
        while (!pending.isEmpty()) {
            final CGNode node = pending.removeFirst();
            if (!result.add(node)) {
                continue;
            }
            graph.getSuccNodes(node).forEachRemaining(pending::addLast);
        }
        return result;
    }

    private static boolean isMethod(
            final CGNode node,
            final String owner,
            final String method) {
        final String value = node.getMethod().getDeclaringClass()
                .getName().toString();
        final String normalized = value.startsWith("L")
                ? value.substring(1) : value;
        return owner.equals(normalized)
                && method.equals(node.getMethod().getName().toString());
    }
}

/**
 * Prepared production-equivalent k-object builder.
 *
 * @param builder configured k-object builder
 * @param options configured analysis options
 * @param selectedClassCount selected PROJECT class count
 * @param declaredEntrypointCount declared entrypoint count
 */
record KObjClassFactoryPreparedAnalysis(
        SSAPropagationCallGraphBuilder builder,
        AnalysisOptions options,
        int selectedClassCount,
        int declaredEntrypointCount) {
}

/**
 * Complete or partial fixed-point objects.
 *
 * @param graph complete or partial Call Graph
 * @param pointerAnalysis complete or partial Pointer Analysis
 * @param status fixed-point completion boundary
 */
record KObjClassFactoryFixedPoint(
        CallGraph graph,
        PointerAnalysis<InstanceKey> pointerAnalysis,
        KObjClassFactoryContextStatus status) {
}

/** Fixed-point completion boundary observed by the regression test. */
enum KObjClassFactoryContextStatus {
    /** Fixed point completed. */
    COMPLETE,

    /** Cooperative timeout with partial graph validation. */
    TIMED_OUT_PARTIAL
}

/**
 * Semantic Context evidence retained by the Maven regression test.
 *
 * @param status fixed-point completion boundary
 * @param graph complete or partial Call Graph
 * @param pointerAnalysis complete or partial Pointer Analysis
 * @param selectedClassCount selected PROJECT class count
 * @param declaredEntrypointCount declared entrypoint count
 * @param reflectionApiPath whether Method.invoke is scenario-reachable
 * @param classFactoryApiPath whether a ClassFactory API is reachable
 * @param classFactoryContextNodeCount typed ClassFactory Context nodes
 * @param malformedClassFactoryContextNodeCount malformed Context nodes
 * @param duplicateJavaTypeContextCount duplicate JavaTypeContext references
 * @param reflectionConstantContextNodeCount Method.invoke constant Contexts
 * @param allocationContextNodeCount reachable k-object Context nodes
 * @param maxAllocationStringDepth maximum receiver allocation depth
 */
record KObjClassFactoryContextValidation(
        KObjClassFactoryContextStatus status,
        CallGraph graph,
        PointerAnalysis<InstanceKey> pointerAnalysis,
        int selectedClassCount,
        int declaredEntrypointCount,
        boolean reflectionApiPath,
        boolean classFactoryApiPath,
        int classFactoryContextNodeCount,
        int malformedClassFactoryContextNodeCount,
        int duplicateJavaTypeContextCount,
        int reflectionConstantContextNodeCount,
        int allocationContextNodeCount,
        int maxAllocationStringDepth) {
}
