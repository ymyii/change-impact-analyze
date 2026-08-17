package io.github.dependencyanalysis.callgraph.strategy.cha;

import io.github.dependencyanalysis.impact.ModuleCallGraphInputAdapter;

import io.github.dependencyanalysis.callgraph.model.CodeOrigin;
import io.github.dependencyanalysis.callgraph.scope.ClassOwnershipIndex;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

import io.github.dependencyanalysis.bytecode.AccessTransition;
import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.bytecode.JvmAccess;
import io.github.dependencyanalysis.bytecode.MemberDescriptors;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.impact.BoundChangePoint;
import io.github.dependencyanalysis.impact.DependencyUpgradeKey;
import io.github.dependencyanalysis.impact.ModuleAnalysisUnit;
import io.github.dependencyanalysis.impact.ModuleChangeSet;
import io.github.dependencyanalysis.impact.ModuleId;
import io.github.dependencyanalysis.impact.ModulePresence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests Diff-directed CHA filtering before target node expansion. */
class ChaDispatchFilteringClassHierarchyTest {

    /** Expected JDK-declared targets removed by the dispatch fixture. */
    private static final int EXPECTED_JDK_PRUNED_TARGETS = 4;

    /** Fixture root. */
    @TempDir
    private Path temporary;

    @Test
    void filtersExactObjectDispatchAndRetainsRequiredTargets()
            throws Exception {
        final ArtifactCoord changed = artifact("changed", "2");
        final ArtifactCoord unrelated = artifact("unrelated", "2");
        final ClassOwnershipIndex ownership = ownership(Map.of(
                changed, List.of("external/Changed", "external/Added"),
                unrelated, List.of("external/Unrelated")));
        final ModuleAnalysisUnit unit = unit(List.of(
                bodyChanged(changed, "external/Changed", "toString",
                        "()Ljava/lang/String;"),
                accessNarrowed(changed, "external/Changed", "hashCode",
                        "()I"),
                added(changed, "external/Added", "toString",
                        "()Ljava/lang/String;")));
        final IMethod objectMethod = method(TypeReference.JavaLangObject,
                "toString", "()Ljava/lang/String;", false);
        final IMethod projectMethod = method(application("project/Value"),
                "toString", "()Ljava/lang/String;", false);
        final IMethod reactorMethod = method(application("reactor/Value"),
                "toString", "()Ljava/lang/String;", false);
        final IMethod changedMethod = method(application("external/Changed"),
                "toString", "()Ljava/lang/String;", false);
        final IMethod unrelatedMethod = method(
                application("external/Unrelated"), "toString",
                "()Ljava/lang/String;", false);
        final IMethod addedMethod = method(application("external/Added"),
                "toString", "()Ljava/lang/String;", false);
        final IMethod stringMethod = method(TypeReference.JavaLangString,
                "toString", "()Ljava/lang/String;", false);
        final IMethod syntheticMethod = method(
                application("synthetic/Lambda"), "toString",
                "()Ljava/lang/String;", true);
        final MethodReference reference = MethodReference.findOrCreate(
                TypeReference.JavaLangObject, "toString",
                "()Ljava/lang/String;");
        final AtomicInteger clears = new AtomicInteger();
        final IClass receiver = objectMethod.getDeclaringClass();
        final Set<IMethod> candidates = linkedSet(stringMethod,
                unrelatedMethod, changedMethod, addedMethod, reactorMethod,
                projectMethod, objectMethod, syntheticMethod);
        final HierarchyFixture fixture = hierarchy(reference, receiver,
                candidates, clears);
        final IClassHierarchy filtered = new
                ChaDispatchFilteringClassHierarchy(fixture.hierarchy(),
                ChaDispatchTargetPolicy.create(
                        new ModuleCallGraphInputAdapter().adapt(unit),
                        ownership));

        assertThat(filtered.getPossibleTargets(reference))
                .containsExactly(objectMethod);
        assertThat(filtered.getPossibleTargets(receiver, reference))
                .containsExactly(objectMethod);
        assertThat(filtered.getPossibleTargets(reference))
                .containsExactly(objectMethod);

        filtered.clearCaches();

        assertThat(clears).hasValue(1);
        assertThat(filtered.getPossibleTargets(reference))
                .containsExactly(objectMethod);
    }

    @Test
    void filtersHashCodeButIgnoresRemovedAndDescriptorChangedTargets()
            throws Exception {
        final ArtifactCoord changed = artifact("changed", "2");
        final ClassOwnershipIndex ownership = ownership(Map.of(changed,
                List.of("external/Removed",
                        "external/DescriptorChanged",
                        "external/ClassRemoved")));
        final ModuleAnalysisUnit unit = unit(List.of(
                removed(changed, "external/Removed", "hashCode", "()I"),
                descriptorChanged(changed, "external/DescriptorChanged",
                        "hashCode", "()I", "()J"),
                classRemoved(changed, "external/ClassRemoved")));
        final IMethod objectMethod = method(TypeReference.JavaLangObject,
                "hashCode", "()I", false);
        final IMethod removedMethod = method(
                application("external/Removed"), "hashCode", "()I", false);
        final IMethod descriptorMethod = method(
                application("external/DescriptorChanged"), "hashCode",
                "()I", false);
        final IMethod classRemovedMethod = method(
                application("external/ClassRemoved"), "hashCode", "()I",
                false);
        final MethodReference reference = MethodReference.findOrCreate(
                TypeReference.JavaLangObject, "hashCode", "()I");
        final Set<IMethod> candidates = linkedSet(removedMethod,
                descriptorMethod, classRemovedMethod, objectMethod);
        final HierarchyFixture fixture = hierarchy(reference,
                objectMethod.getDeclaringClass(), candidates,
                new AtomicInteger());
        final IClassHierarchy filtered = new
                ChaDispatchFilteringClassHierarchy(fixture.hierarchy(),
                ChaDispatchTargetPolicy.create(
                        new ModuleCallGraphInputAdapter().adapt(unit),
                        ownership));

        assertThat(filtered.getPossibleTargets(reference))
                .containsExactly(objectMethod);
    }

    @Test
    void doesNotFilterOtherOwnersOrSelectors() throws Exception {
        final ArtifactCoord changed = artifact("changed", "2");
        final ClassOwnershipIndex ownership = ownership(Map.of(changed,
                List.of("external/Changed")));
        final ModuleAnalysisUnit unit = unit(List.of());
        final MethodReference stringToString = MethodReference.findOrCreate(
                TypeReference.JavaLangString, "toString",
                "()Ljava/lang/String;");
        final MethodReference objectEquals = MethodReference.findOrCreate(
                TypeReference.JavaLangObject, "equals",
                "(Ljava/lang/Object;)Z");
        final IMethod stringMethod = method(TypeReference.JavaLangString,
                "toString", "()Ljava/lang/String;", false);
        final IMethod equalsMethod = method(TypeReference.JavaLangObject,
                "equals", "(Ljava/lang/Object;)Z", false);
        final Set<IMethod> stringTargets = linkedSet(stringMethod);
        final Set<IMethod> equalsTargets = linkedSet(equalsMethod);
        final Map<MethodReference, Set<IMethod>> targets =
                new LinkedHashMap<>();
        targets.put(stringToString, stringTargets);
        targets.put(objectEquals, equalsTargets);
        final IClassHierarchy delegate = hierarchy(targets,
                stringMethod.getDeclaringClass(), new AtomicInteger());
        final IClassHierarchy filtered = new
                ChaDispatchFilteringClassHierarchy(delegate,
                ChaDispatchTargetPolicy.create(
                        new ModuleCallGraphInputAdapter().adapt(unit),
                        ownership));

        assertThat(filtered.getPossibleTargets(stringToString))
                .containsExactly(stringMethod);
        assertThat(filtered.getPossibleTargets(objectEquals))
                .containsExactly(equalsMethod);
    }

    @Test
    void jdkDeclaredDispatchDropsNonJdkTargetsButCustomInterfaceDoesNot()
            throws Exception {
        final ClassOwnershipIndex ownership = new ClassOwnershipIndex();
        ownership.addDirectory(classes("project/Runner"), CodeOrigin.PROJECT);
        final ModuleAnalysisUnit unit = unit(List.of());
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
        final MethodReference iteratorReference =
                MethodReference.findOrCreate(
                        iterator, "next", "()Ljava/lang/Object;");
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
                        new ModuleCallGraphInputAdapter().adapt(unit),
                        ownership, ChaAncestorRetentionPolicy.disabled(),
                        false, true);
        final IClassHierarchy filtered =
                new ChaDispatchFilteringClassHierarchy(
                        hierarchy(targets, jdkMethod.getDeclaringClass(),
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

    @Test
    void duplicateOwnerRequiresWinningArtifactToMatchDiff() throws Exception {
        final ArtifactCoord winner = artifact("winner", "2");
        final ArtifactCoord loser = artifact("loser", "2");
        final ClassOwnershipIndex ownership = new ClassOwnershipIndex();
        final Path winnerJar = jar(winner, "external/Duplicate", 0);
        final Path loserJar = jar(loser, "external/Duplicate",
                Opcodes.ACC_FINAL);
        try (java.util.jar.JarFile opened =
                     new java.util.jar.JarFile(winnerJar.toFile())) {
            ownership.addJar(winner, opened, CodeOrigin.DEPENDENCY);
        }
        try (java.util.jar.JarFile opened =
                     new java.util.jar.JarFile(loserJar.toFile())) {
            ownership.addJar(loser, opened, CodeOrigin.DEPENDENCY);
        }
        final ModuleAnalysisUnit unit = unit(List.of(bodyChanged(
                loser, "external/Duplicate", "toString",
                "()Ljava/lang/String;")));
        final IMethod objectMethod = method(TypeReference.JavaLangObject,
                "toString", "()Ljava/lang/String;", false);
        final IMethod duplicateMethod = method(
                application("external/Duplicate"), "toString",
                "()Ljava/lang/String;", false);
        final MethodReference reference = MethodReference.findOrCreate(
                TypeReference.JavaLangObject, "toString",
                "()Ljava/lang/String;");
        final HierarchyFixture fixture = hierarchy(reference,
                objectMethod.getDeclaringClass(),
                linkedSet(duplicateMethod, objectMethod),
                new AtomicInteger());
        final IClassHierarchy filtered = new
                ChaDispatchFilteringClassHierarchy(fixture.hierarchy(),
                ChaDispatchTargetPolicy.create(
                        new ModuleCallGraphInputAdapter().adapt(unit),
                        ownership));

        assertThat(filtered.getPossibleTargets(reference))
                .containsExactly(objectMethod);
    }

    private ClassOwnershipIndex ownership(
            final Map<ArtifactCoord, List<String>> artifacts)
            throws Exception {
        final ClassOwnershipIndex ownership = new ClassOwnershipIndex();
        ownership.addDirectory(classes("project/Value"),
                CodeOrigin.PROJECT);
        ownership.addDirectory(classes("reactor/Value"),
                CodeOrigin.REACTOR_DEPENDENCY);
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

    private ModuleAnalysisUnit unit(final List<ChangePoint> points) {
        final ModuleId module = new ModuleId(
                artifact("module", "1"), Path.of("module"));
        final List<BoundChangePoint> bound = points.stream()
                .map(point -> new BoundChangePoint(
                        new DependencyUpgradeKey(module,
                                DependencyScope.COMPILE,
                                artifact(point.getArtifact().getArtifactId(),
                                        "1"), point.getArtifact()), point))
                .toList();
        final List<ArtifactCoord> targets = points.stream()
                .map(ChangePoint::getArtifact).distinct().toList();
        return new ModuleAnalysisUnit(module, ModulePresence.BOTH,
                temporary, List.of(), targets, List.of(),
                new ModuleChangeSet(bound, List.of()));
    }

    private ChangePoint bodyChanged(
            final ArtifactCoord artifact,
            final String owner,
            final String name,
            final String descriptor) {
        return ChangePoint.withDescriptors(artifact,
                ChangePointKind.METHOD_BODY_CHANGED, owner, name,
                new MemberDescriptors(descriptor, descriptor), "old", "new");
    }

    private ChangePoint accessNarrowed(
            final ArtifactCoord artifact,
            final String owner,
            final String name,
            final String descriptor) {
        return ChangePoint.accessNarrowed(artifact,
                ChangePointKind.METHOD_ACCESS_NARROWED, owner, name,
                descriptor, new AccessTransition(JvmAccess.PUBLIC,
                JvmAccess.PROTECTED));
    }

    private ChangePoint added(
            final ArtifactCoord artifact,
            final String owner,
            final String name,
            final String descriptor) {
        return ChangePoint.withDescriptors(artifact,
                ChangePointKind.METHOD_ADDED, owner, name,
                new MemberDescriptors(null, descriptor), null, null);
    }

    private ChangePoint removed(
            final ArtifactCoord artifact,
            final String owner,
            final String name,
            final String descriptor) {
        return ChangePoint.withDescriptors(artifact,
                ChangePointKind.METHOD_REMOVED, owner, name,
                new MemberDescriptors(descriptor, null), null, null);
    }

    private ChangePoint descriptorChanged(
            final ArtifactCoord artifact,
            final String owner,
            final String name,
            final String oldDescriptor,
            final String newDescriptor) {
        return ChangePoint.withDescriptors(artifact,
                ChangePointKind.METHOD_DESCRIPTOR_CHANGED, owner, name,
                new MemberDescriptors(oldDescriptor, newDescriptor),
                null, null);
    }

    private ChangePoint classRemoved(
            final ArtifactCoord artifact, final String owner) {
        return new ChangePoint(artifact, ChangePointKind.CLASS_REMOVED,
                owner, null, null, null, null);
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
        return proxy(IClassHierarchy.class,
                (ignored, called, args) -> switch (called.getName()) {
                    case "getPossibleTargets" -> targets.get(args[
                            args.length - 1]);
                    case "lookupClass" -> receiver;
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
