package io.github.dependencyanalysis.callgraph.strategy.cha;

import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphInput;
import io.github.dependencyanalysis.classpath.CodeOrigin;
import io.github.dependencyanalysis.callgraph.scope.CallGraphDependencyScope;
import io.github.dependencyanalysis.classpath.ClassOwnershipIndex;
import io.github.dependencyanalysis.callgraph.scope.DependencyBodyPolicy;
import io.github.dependencyanalysis.callgraph.scope.DependencyScopeMode;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

import io.github.dependencyanalysis.dependency.ArtifactCoord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests CHA JDK-declared and external target filtering. */
class ChaDispatchFilteringClassHierarchyTest {

    /** Expected JDK-declared targets removed by the dispatch fixture. */
    private static final int EXPECTED_JDK_PRUNED_TARGETS = 4;

    /** Fixture root. */
    @TempDir
    private Path temporary;

    @Test
    void objectSelectorsUseGenericJdkDeclaredBoundary() {
        final MethodReference objectToString = MethodReference.findOrCreate(
                TypeReference.JavaLangObject, "toString",
                "()Ljava/lang/String;");
        final MethodReference objectHashCode = MethodReference.findOrCreate(
                TypeReference.JavaLangObject, "hashCode", "()I");
        final MethodReference objectEquals = MethodReference.findOrCreate(
                TypeReference.JavaLangObject, "equals",
                "(Ljava/lang/Object;)Z");
        final IMethod baseToString = method(TypeReference.JavaLangObject,
                "toString", "()Ljava/lang/String;", false);
        final IMethod stringToString = method(TypeReference.JavaLangString,
                "toString", "()Ljava/lang/String;", false);
        final IMethod projectToString = method(application("project/Value"),
                "toString", "()Ljava/lang/String;", false);
        final IMethod abstractToString = method(
                TypeReference.findOrCreate(ClassLoaderReference.Primordial,
                        "Ljava/lang/Number"), "toString",
                "()Ljava/lang/String;", false, true);
        final IMethod baseHashCode = method(TypeReference.JavaLangObject,
                "hashCode", "()I", false);
        final IMethod stringHashCode = method(TypeReference.JavaLangString,
                "hashCode", "()I", false);
        final IMethod projectHashCode = method(application("project/Value"),
                "hashCode", "()I", false);
        final IMethod baseEquals = method(TypeReference.JavaLangObject,
                "equals", "(Ljava/lang/Object;)Z", false);
        final IMethod stringEquals = method(TypeReference.JavaLangString,
                "equals", "(Ljava/lang/Object;)Z", false);
        final IMethod projectEquals = method(application("project/Value"),
                "equals", "(Ljava/lang/Object;)Z", false);
        final Map<MethodReference, Set<IMethod>> targets =
                new LinkedHashMap<>();
        targets.put(objectToString, linkedSet(projectToString,
                abstractToString, stringToString, baseToString));
        targets.put(objectHashCode, linkedSet(projectHashCode,
                stringHashCode, baseHashCode));
        targets.put(objectEquals, linkedSet(projectEquals,
                stringEquals, baseEquals));
        final ChaDispatchTargetPolicy policy = ChaDispatchTargetPolicy.create(
                input(DependencyScopeMode.FULL, Map.of()),
                new ClassOwnershipIndex(),
                ChaAncestorRetentionPolicy.disabled(), false, true);
        final IClassHierarchy filtered =
                new ChaDispatchFilteringClassHierarchy(
                        hierarchy(targets, baseToString.getDeclaringClass(),
                                new AtomicInteger()), policy);

        assertThat(filtered.getPossibleTargets(objectToString))
                .containsExactly(baseToString, stringToString);
        assertThat(filtered.getPossibleTargets(objectHashCode))
                .containsExactly(baseHashCode, stringHashCode);
        assertThat(filtered.getPossibleTargets(objectEquals))
                .containsExactly(baseEquals, stringEquals);
        assertThat(policy.jdkDeclaredDispatchSummary().prunedTargetCount())
                .isEqualTo(EXPECTED_JDK_PRUNED_TARGETS);
        assertThat(policy.jdkDeclaredDispatchSummary().examples())
                .hasSize(EXPECTED_JDK_PRUNED_TARGETS)
                .extracting(JdkDeclaredDispatchPruningSummary.TargetExample
                        ::removedOrigin)
                .containsExactlyInAnyOrder(
                        "JDK", "SYNTHETIC", "SYNTHETIC", "SYNTHETIC");
    }

    @Test
    void nonJdkOwnersDoNotTriggerObjectSelectorFiltering() {
        final TypeReference customOwner = application("project/Value");
        final MethodReference customToString = MethodReference.findOrCreate(
                customOwner, "toString", "()Ljava/lang/String;");
        final MethodReference customHashCode = MethodReference.findOrCreate(
                customOwner, "hashCode", "()I");
        final IMethod customToStringMethod = method(customOwner, "toString",
                "()Ljava/lang/String;", false);
        final IMethod jdkToStringMethod = method(TypeReference.JavaLangString,
                "toString", "()Ljava/lang/String;", false);
        final IMethod customHashCodeMethod = method(customOwner, "hashCode",
                "()I", false);
        final IMethod jdkHashCodeMethod = method(TypeReference.JavaLangString,
                "hashCode", "()I", false);
        final Set<IMethod> toStringTargets = linkedSet(
                customToStringMethod, jdkToStringMethod);
        final Set<IMethod> hashCodeTargets = linkedSet(
                customHashCodeMethod, jdkHashCodeMethod);
        final Map<MethodReference, Set<IMethod>> targets =
                new LinkedHashMap<>();
        targets.put(customToString, toStringTargets);
        targets.put(customHashCode, hashCodeTargets);
        final IClassHierarchy filtered =
                new ChaDispatchFilteringClassHierarchy(
                        hierarchy(targets,
                                customToStringMethod.getDeclaringClass(),
                                new AtomicInteger()),
                        ChaDispatchTargetPolicy.create(
                                input(DependencyScopeMode.FULL, Map.of()),
                                new ClassOwnershipIndex()));

        assertThat(filtered.getPossibleTargets(customToString))
                .isSameAs(toStringTargets);
        assertThat(filtered.getPossibleTargets(customHashCode))
                .isSameAs(hashCodeTargets);
    }

    @Test
    void jdkDeclaredDispatchDropsNonJdkTargetsButCustomInterfaceDoesNot()
            throws Exception {
        final ClassOwnershipIndex ownership = new ClassOwnershipIndex();
        ownership.addDirectory(classes("project/Runner"), CodeOrigin.PROJECT);
        final TypeReference runnable = TypeReference.findOrCreate(
                ClassLoaderReference.Primordial, "Ljava/lang/Runnable");
        final MethodReference jdkReference = MethodReference.findOrCreate(
                runnable, "run", "()V");
        final IMethod jdkMethod = method(runnable, "run", "()V", false);
        final IMethod projectMethod = method(application("project/Runner"),
                "run", "()V", false);
        final IMethod syntheticMethod = method(
                application("project/Runner$$Lambda$1"),
                "run", "()V", true);
        final TypeReference iterator = TypeReference.findOrCreate(
                ClassLoaderReference.Primordial, "Ljava/util/Iterator");
        final TypeReference applicationIterator = application(
                "java/util/Iterator");
        final MethodReference iteratorReference =
                MethodReference.findOrCreate(
                        applicationIterator, "next",
                        "()Ljava/lang/Object;");
        final IClass resolvedIterator = type(iterator, false);
        final IMethod jdkIteratorMethod = method(
                TypeReference.findOrCreate(ClassLoaderReference.Primordial,
                        "Ljava/util/ArrayList$Itr"),
                "next", "()Ljava/lang/Object;", false);
        final IMethod projectIteratorMethod = method(
                application("project/Runner"),
                "next", "()Ljava/lang/Object;", false);
        final TypeReference thread = TypeReference.findOrCreate(
                ClassLoaderReference.Primordial, "Ljava/lang/Thread");
        final MethodReference threadReference = MethodReference.findOrCreate(
                thread, "run", "()V");
        final IMethod jdkThreadMethod = method(
                thread, "run", "()V", false);
        final TypeReference custom = application("project/CustomRunner");
        final MethodReference customReference = MethodReference.findOrCreate(
                custom, "run", "()V");
        final Map<MethodReference, Set<IMethod>> targets =
                new LinkedHashMap<>();
        targets.put(jdkReference, linkedSet(
                projectMethod, syntheticMethod, jdkMethod));
        targets.put(iteratorReference, linkedSet(
                projectIteratorMethod, jdkIteratorMethod));
        targets.put(threadReference, linkedSet(
                projectMethod, jdkThreadMethod));
        targets.put(customReference, linkedSet(projectMethod));
        final ChaDispatchTargetPolicy policy =
                ChaDispatchTargetPolicy.create(
                        input(DependencyScopeMode.FULL, Map.of()), ownership,
                        ChaAncestorRetentionPolicy.disabled(), false, true);
        final IClassHierarchy filtered =
                new ChaDispatchFilteringClassHierarchy(
                        hierarchy(targets,
                                Map.of(applicationIterator,
                                        resolvedIterator),
                                jdkMethod.getDeclaringClass(),
                                new AtomicInteger()), policy);

        assertThat(filtered.getPossibleTargets(jdkReference))
                .containsExactly(jdkMethod);
        assertThat(filtered.getPossibleTargets(iteratorReference))
                .containsExactly(jdkIteratorMethod);
        assertThat(filtered.getPossibleTargets(threadReference))
                .containsExactly(jdkThreadMethod);
        assertThat(filtered.getPossibleTargets(customReference))
                .containsExactly(projectMethod);
        assertThat(policy.jdkDeclaredDispatchSummary().prunedTargetCount())
                .isEqualTo(EXPECTED_JDK_PRUNED_TARGETS);
        assertThat(policy.jdkDeclaredDispatchSummary().examples())
                .hasSize(EXPECTED_JDK_PRUNED_TARGETS)
                .allSatisfy(example -> assertThat(example.declaredTarget())
                        .containsAnyOf("Ljava/lang/Runnable, run()V",
                                "Ljava/util/Iterator, next()",
                                "Ljava/lang/Thread, run()V"))
                .extracting(JdkDeclaredDispatchPruningSummary.TargetExample
                        ::removedOrigin)
                .containsExactlyInAnyOrder(
                        "PROJECT", "PROJECT", "PROJECT", "SYNTHETIC");
    }

    @Test
    void externalPruningUsesBodyPolicyWithoutSelectorExceptions()
            throws Exception {
        final ArtifactCoord real = artifact("real", "2");
        final ArtifactCoord noOp = artifact("no-op", "2");
        final ClassOwnershipIndex ownership = ownership(Map.of(
                real, List.of("external/Real"),
                noOp, List.of("external/NoOp")));
        final MethodReference reference = MethodReference.findOrCreate(
                application("external/Contract"), "run", "()V");
        final IMethod realMethod = method(application("external/Real"),
                "run", "()V", false);
        final IMethod noOpMethod = method(application("external/NoOp"),
                "run", "()V", false);
        final IMethod projectMethod = method(application("project/Value"),
                "run", "()V", false);
        final IMethod syntheticMethod = method(
                application("synthetic/Lambda"), "run", "()V", true);
        final Map<ArtifactCoord, DependencyBodyPolicy> policies = Map.of(
                real, DependencyBodyPolicy.REAL_IR,
                noOp, DependencyBodyPolicy.NO_OP);
        final ChaDispatchTargetPolicy policy =
                ChaDispatchTargetPolicy.create(
                        input(DependencyScopeMode.CHANGED_PATHS, policies),
                        ownership, ChaAncestorRetentionPolicy.disabled(), true);
        final HierarchyFixture fixture = hierarchy(reference,
                projectMethod.getDeclaringClass(), linkedSet(noOpMethod,
                        projectMethod, syntheticMethod, realMethod),
                new AtomicInteger());
        final IClassHierarchy filtered =
                new ChaDispatchFilteringClassHierarchy(
                        fixture.hierarchy(), policy);

        assertThat(filtered.getPossibleTargets(reference))
                .containsExactly(realMethod, projectMethod, syntheticMethod);
        assertThat(policy.prunedExternalMethodTargetCount()).isOne();
    }

    @Test
    void disabledPolicyDoesNotApplyJdkDeclaredBoundary() {
        final TypeReference runnable = TypeReference.findOrCreate(
                ClassLoaderReference.Primordial, "Ljava/lang/Runnable");
        final MethodReference reference = MethodReference.findOrCreate(
                runnable, "run", "()V");
        final IMethod projectMethod = method(application("project/Runner"),
                "run", "()V", false);
        final HierarchyFixture fixture = hierarchy(reference,
                projectMethod.getDeclaringClass(), linkedSet(projectMethod),
                new AtomicInteger());
        final IClassHierarchy filtered =
                new ChaDispatchFilteringClassHierarchy(
                        fixture.hierarchy(),
                        ChaDispatchTargetPolicy.disabled());

        assertThat(filtered.getPossibleTargets(reference))
                .containsExactly(projectMethod);
    }

    private ModuleCallGraphInput input(
            final DependencyScopeMode mode,
            final Map<ArtifactCoord, DependencyBodyPolicy> policies) {
        final List<ArtifactCoord> artifacts = policies.keySet().stream()
                .sorted(Comparator.comparing(ArtifactCoord::toString))
                .toList();
        return new ModuleCallGraphInput("module", temporary, List.of(),
                artifacts, new CallGraphDependencyScope(
                        mode, policies, List.of()), List.of());
    }

    private ClassOwnershipIndex ownership(
            final Map<ArtifactCoord, List<String>> artifacts)
            throws Exception {
        final ClassOwnershipIndex ownership = new ClassOwnershipIndex();
        ownership.addDirectory(classes("project/Value"), CodeOrigin.PROJECT);
        for (Map.Entry<ArtifactCoord, List<String>> entry
                : artifacts.entrySet()) {
            for (String owner : entry.getValue()) {
                final Path jar = jar(entry.getKey(), owner, 0);
                try (java.util.jar.JarFile opened =
                             new java.util.jar.JarFile(jar.toFile())) {
                    ownership.addJar(entry.getKey(), opened,
                            CodeOrigin.DEPENDENCY);
                }
            }
        }
        return ownership;
    }

    private Path classes(final String owner) throws Exception {
        final Path root = temporary.resolve("classes-"
                + owner.replace('/', '-'));
        final Path file = root.resolve(owner + ".class");
        Files.createDirectories(file.getParent());
        Files.write(file, classBytes(owner, 0));
        return root;
    }

    private Path jar(
            final ArtifactCoord artifact,
            final String owner,
            final int flags) throws Exception {
        final Path jar = temporary.resolve(artifact.getArtifactId()
                + "-" + artifact.getVersion() + "-"
                + owner.replace('/', '-') + ".jar");
        try (java.util.jar.JarOutputStream output =
                     new java.util.jar.JarOutputStream(
                             Files.newOutputStream(jar))) {
            output.putNextEntry(new java.util.jar.JarEntry(
                    owner + ".class"));
            output.write(classBytes(owner, flags));
            output.closeEntry();
        }
        return jar;
    }

    private byte[] classBytes(final String owner, final int flags) {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | flags, owner,
                null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static ArtifactCoord artifact(
            final String artifact, final String version) {
        return new ArtifactCoord("test", artifact, "jar", version);
    }

    private static TypeReference application(final String owner) {
        return TypeReference.findOrCreate(ClassLoaderReference.Application,
                "L" + owner);
    }

    private static IMethod method(
            final TypeReference owner,
            final String name,
            final String descriptor,
            final boolean synthetic) {
        return method(owner, name, descriptor, synthetic, false);
    }

    private static IMethod method(
            final TypeReference owner,
            final String name,
            final String descriptor,
            final boolean synthetic,
            final boolean abstractMethod) {
        final IClass type = type(owner, synthetic);
        final MethodReference reference = MethodReference.findOrCreate(
                owner, name, descriptor);
        return proxy(IMethod.class, (ignored, called, args) -> switch (
                called.getName()) {
            case "getDeclaringClass" -> type;
            case "getReference" -> reference;
            case "getName" -> reference.getName();
            case "getDescriptor" -> reference.getDescriptor();
            case "getSelector" -> reference.getSelector();
            case "isWalaSynthetic", "isSynthetic" -> synthetic;
            case "isAbstract" -> abstractMethod;
            case "toString", "getSignature" -> reference.toString();
            case "hashCode" -> System.identityHashCode(ignored);
            case "equals" -> ignored == args[0];
            default -> defaultValue(called.getReturnType());
        });
    }

    private static IClass type(
            final TypeReference reference, final boolean synthetic) {
        final IClassLoader loader = proxy(IClassLoader.class,
                (ignored, called, args) -> switch (called.getName()) {
                    case "getReference" -> reference.getClassLoader();
                    default -> defaultValue(called.getReturnType());
                });
        return proxy(IClass.class, (ignored, called, args) -> switch (
                called.getName()) {
            case "getReference" -> reference;
            case "getName" -> reference.getName();
            case "getClassLoader" -> loader;
            case "isSynthetic" -> synthetic;
            case "toString" -> reference.toString();
            case "hashCode" -> System.identityHashCode(ignored);
            case "equals" -> ignored == args[0];
            default -> defaultValue(called.getReturnType());
        });
    }

    private static HierarchyFixture hierarchy(
            final MethodReference reference,
            final IClass receiver,
            final Set<IMethod> targets,
            final AtomicInteger clears) {
        return new HierarchyFixture(hierarchy(Map.of(reference, targets),
                receiver, clears));
    }

    private static IClassHierarchy hierarchy(
            final Map<MethodReference, Set<IMethod>> targets,
            final IClass receiver,
            final AtomicInteger clears) {
        return hierarchy(targets, Map.of(), receiver, clears);
    }

    private static IClassHierarchy hierarchy(
            final Map<MethodReference, Set<IMethod>> targets,
            final Map<TypeReference, IClass> resolvedTypes,
            final IClass receiver,
            final AtomicInteger clears) {
        return proxy(IClassHierarchy.class,
                (ignored, called, args) -> switch (called.getName()) {
                    case "getPossibleTargets" -> targets.get(args[
                            args.length - 1]);
                    case "lookupClass" -> resolvedTypes.getOrDefault(
                            args[0], receiver);
                    case "clearCaches" -> {
                        clears.incrementAndGet();
                        yield null;
                    }
                    case "iterator" -> List.<IClass>of().iterator();
                    default -> defaultValue(called.getReturnType());
                });
    }

    @SafeVarargs
    private static <T> Set<T> linkedSet(final T... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    private static Object defaultValue(final Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0.0F;
        }
        if (type == double.class) {
            return 0.0D;
        }
        return null;
    }

    private static <T> T proxy(
            final Class<T> type, final InvocationHandler handler) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(),
                new Class<?>[]{type}, handler));
    }

    /**
     * Decorated hierarchy fixture.
     *
     * @param hierarchy hierarchy double
     */
    private record HierarchyFixture(IClassHierarchy hierarchy) {
    }
}
