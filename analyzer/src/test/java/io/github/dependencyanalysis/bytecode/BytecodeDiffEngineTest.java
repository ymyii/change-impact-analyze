package io.github.dependencyanalysis.bytecode;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ChangeType;
import io.github.dependencyanalysis.dependency.DependencyChange;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.jar.IJarRepository;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.JavaRuntimeProbe;
import io.github.dependencyanalysis.testing.TestJarRepositories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;

/**
 * Tests for
 * {@link BytecodeDiffEngine}.
 */
class BytecodeDiffEngineTest {

    /** Test artifact old. */
    private static final ArtifactCoord OLD =
            new ArtifactCoord(
                    "g", "a", "jar", "1.0");

    /** Test artifact new. */
    private static final ArtifactCoord NEW =
            new ArtifactCoord(
                    "g", "a", "jar", "2.0");

    /** Test temp directory. */
    @TempDir
    private Path tempDir;

    /** Engine under test. */
    private final BytecodeDiffEngine engine =
            new BytecodeDiffEngine(
                    EnumSet.allOf(
                            ChangePointKind.class));

    @Test
    void detectsClassAdded()
            throws Exception {
        final Path oldJar = createJar(
                "old.jar",
                classBytes("com/Foo"));
        final Path newJar = createJar(
                "new.jar",
                classBytes("com/Foo"),
                classBytes("com/Bar"));
        final List<ChangePoint> pts =
                diff(oldJar, newJar);
        assertThat(pts)
                .extracting(
                        ChangePoint::getKind)
                .contains(ChangePointKind
                        .CLASS_ADDED);
        assertThat(pts)
                .extracting(
                        ChangePoint::getOwner)
                .contains("com/Bar");
    }

    @Test
    void detectsClassRemoved()
            throws Exception {
        final Path oldJar = createJar(
                "old.jar",
                classBytes("com/Foo"),
                classBytes("com/Bar"));
        final Path newJar = createJar(
                "new.jar",
                classBytes("com/Foo"));
        final List<ChangePoint> pts =
                diff(oldJar, newJar);
        assertThat(pts)
                .extracting(
                        ChangePoint::getKind)
                .contains(ChangePointKind
                        .CLASS_REMOVED);
        assertThat(pts)
                .extracting(
                        ChangePoint::getOwner)
                .contains("com/Bar");
    }

    @Test
    void detectsMethodAdded()
            throws Exception {
        final Path oldJar = createJar(
                "old.jar",
                classWithMethod(
                        "com/Foo",
                        "bar",
                        "()V"));
        final Path newJar = createJar(
                "new.jar",
                classWithMethods(
                        "com/Foo",
                        new String[]{"bar", "()V"},
                        new String[]{"baz",
                                "(I)V"}));
        final List<ChangePoint> pts =
                diff(oldJar, newJar);
        assertThat(pts)
                .extracting(
                        ChangePoint::getKind)
                .contains(ChangePointKind
                        .METHOD_ADDED);
        assertThat(pts)
                .filteredOn(p -> p.getKind()
                        == ChangePointKind
                                .METHOD_ADDED)
                .extracting(
                        ChangePoint::getName)
                .contains("baz");
    }

    @Test
    void detectsMethodRemoved()
            throws Exception {
        final Path oldJar = createJar(
                "old.jar",
                classWithMethods(
                        "com/Foo",
                        new String[]{"bar", "()V"},
                        new String[]{"baz",
                                "(I)V"}));
        final Path newJar = createJar(
                "new.jar",
                classWithMethod(
                        "com/Foo",
                        "bar",
                        "()V"));
        final List<ChangePoint> pts =
                diff(oldJar, newJar);
        assertThat(pts)
                .extracting(
                        ChangePoint::getKind)
                .contains(ChangePointKind
                        .METHOD_REMOVED);
    }

    @Test
    void detectsMethodBodyChanged()
            throws Exception {
        final Path oldJar = createJar(
                "old.jar",
                classWithMethodBody(
                        "com/Foo",
                        "bar",
                        "()V",
                        Opcodes.ICONST_0));
        final Path newJar = createJar(
                "new.jar",
                classWithMethodBody(
                        "com/Foo",
                        "bar",
                        "()V",
                        Opcodes.ICONST_1));
        final List<ChangePoint> pts =
                diff(oldJar, newJar);
        assertThat(pts)
                .extracting(
                        ChangePoint::getKind)
                .contains(ChangePointKind
                        .METHOD_BODY_CHANGED);
        final ChangePoint bodyChange =
                pts.stream()
                        .filter(p -> p.getKind()
                                == ChangePointKind
                                        .METHOD_BODY_CHANGED)
                        .findFirst()
                        .orElseThrow();
        assertThat(bodyChange.getOldHash())
                .isNotNull();
        assertThat(bodyChange.getNewHash())
                .isNotNull();
        assertThat(bodyChange.getOldHash())
                .isNotEqualTo(
                        bodyChange.getNewHash());
    }

    @Test
    void detectsMethodDescriptorChanged()
            throws Exception {
        final Path oldJar = createJar(
                "old.jar",
                classWithMethod(
                        "com/Foo",
                        "bar",
                        "()V"));
        final Path newJar = createJar(
                "new.jar",
                classWithMethod(
                        "com/Foo",
                        "bar",
                        "(I)V"));
        final List<ChangePoint> pts =
                diff(oldJar, newJar);
        assertThat(pts)
                .extracting(
                        ChangePoint::getKind)
                .containsExactly(ChangePointKind
                        .METHOD_DESCRIPTOR_CHANGED);
        final ChangePoint change = pts.stream()
                .filter(point -> point.getKind()
                        == ChangePointKind.METHOD_DESCRIPTOR_CHANGED)
                .findFirst().orElseThrow();
        assertThat(change.getOldDescriptor()).isEqualTo("()V");
        assertThat(change.getNewDescriptor()).isEqualTo("(I)V");
    }

    @Test
    void defaultEngineDetectsClassNarrowingButNotExpansion()
            throws Exception {
        final BytecodeDiffEngine defaultEngine =
                new BytecodeDiffEngine();
        final List<ChangePoint> narrowed = diffWith(
                defaultEngine,
                createJar("old-narrow.jar", classBytes(
                        "com/Foo", Opcodes.ACC_PUBLIC)),
                createJar("new-narrow.jar", classBytes(
                        "com/Foo", 0)));

        assertThat(narrowed)
                .extracting(ChangePoint::getKind)
                .containsExactly(
                        ChangePointKind.CLASS_ACCESS_NARROWED);
        assertThat(narrowed.get(0).getAccessTransition())
                .contains(new AccessTransition(
                        JvmAccess.PUBLIC,
                        JvmAccess.PACKAGE_PRIVATE));

        final List<ChangePoint> expanded = diffWith(
                defaultEngine,
                createJar("old-expand.jar", classBytes(
                        "com/Foo", 0)),
                createJar("new-expand.jar", classBytes(
                        "com/Foo", Opcodes.ACC_PUBLIC)));
        assertThat(expanded).isEmpty();
    }

    @Test
    void detectsEveryMethodStrictNarrowingCombination()
            throws Exception {
        for (JvmAccess[] transition : strictMemberNarrowings()) {
            final List<ChangePoint> points = diff(
                    createJar("old-method.jar", classWithMethodAccess(
                            "com/Foo", "run", "()V",
                            accessFlag(transition[0]))),
                    createJar("new-method.jar", classWithMethodAccess(
                            "com/Foo", "run", "()V",
                            accessFlag(transition[1]))));

            assertThat(points)
                    .as("%s -> %s", transition[0], transition[1])
                    .extracting(ChangePoint::getKind)
                    .containsExactly(
                            ChangePointKind.METHOD_ACCESS_NARROWED);
            assertThat(points.get(0).getAccessTransition())
                    .contains(new AccessTransition(
                            transition[0], transition[1]));
        }
    }

    @Test
    void detectsConstructorNarrowingByBytecodeIdentity()
            throws Exception {
        final List<ChangePoint> points = diff(
                createJar("old.jar", classWithMethodAccess(
                        "com/Foo", "<init>", "()V",
                        Opcodes.ACC_PUBLIC)),
                createJar("new.jar", classWithMethodAccess(
                        "com/Foo", "<init>", "()V",
                        Opcodes.ACC_PRIVATE)));

        assertThat(points)
                .extracting(
                        ChangePoint::getKind,
                        ChangePoint::getName,
                        ChangePoint::getDescriptor)
                .containsExactly(tuple(
                        ChangePointKind.METHOD_ACCESS_NARROWED,
                        "<init>", "()V"));
    }

    @Test
    void methodExpansionAndUnchangedAccessProduceNoChange()
            throws Exception {
        final List<ChangePoint> expanded = diff(
                createJar("old-expand.jar", classWithMethodAccess(
                        "com/Foo", "run", "()V",
                        Opcodes.ACC_PRIVATE)),
                createJar("new-expand.jar", classWithMethodAccess(
                        "com/Foo", "run", "()V",
                        Opcodes.ACC_PUBLIC)));
        final List<ChangePoint> unchanged = diff(
                createJar("old-same.jar", classWithMethodAccess(
                        "com/Foo", "run", "()V",
                        Opcodes.ACC_PROTECTED)),
                createJar("new-same.jar", classWithMethodAccess(
                        "com/Foo", "run", "()V",
                        Opcodes.ACC_PROTECTED)));

        assertThat(expanded).isEmpty();
        assertThat(unchanged).isEmpty();
    }

    @Test
    void methodBodyAndAccessChangesRemainSeparate()
            throws Exception {
        final List<ChangePoint> points = diff(
                createJar("old.jar", classWithMethodAccessAndBody(
                        "com/Foo", "value", "()I",
                        Opcodes.ACC_PUBLIC, Opcodes.ICONST_0)),
                createJar("new.jar", classWithMethodAccessAndBody(
                        "com/Foo", "value", "()I",
                        Opcodes.ACC_PRIVATE, Opcodes.ICONST_1)));

        assertThat(points)
                .extracting(ChangePoint::getKind)
                .containsExactly(
                        ChangePointKind.METHOD_BODY_CHANGED,
                        ChangePointKind.METHOD_ACCESS_NARROWED);
    }

    @Test
    void descriptorChangeDoesNotGuessMethodAccessNarrowing()
            throws Exception {
        final List<ChangePoint> points = diff(
                createJar("old.jar", classWithMethodAccess(
                        "com/Foo", "run", "()V",
                        Opcodes.ACC_PUBLIC)),
                createJar("new.jar", classWithMethodAccess(
                        "com/Foo", "run", "(I)V",
                        Opcodes.ACC_PRIVATE)));

        assertThat(points)
                .extracting(ChangePoint::getKind)
                .containsExactly(
                        ChangePointKind.METHOD_DESCRIPTOR_CHANGED);
    }

    @Test
    void defaultEngineKeepsClassAndMemberNarrowingSeparate()
            throws Exception {
        final List<ChangePoint> points = diffWith(
                new BytecodeDiffEngine(),
                createJar("old.jar", classWithAccesses(
                        "com/Foo", Opcodes.ACC_PUBLIC,
                        Opcodes.ACC_PUBLIC, Opcodes.ACC_PUBLIC)),
                createJar("new.jar", classWithAccesses(
                        "com/Foo", 0,
                        Opcodes.ACC_PRIVATE, Opcodes.ACC_PRIVATE)));

        assertThat(points)
                .extracting(ChangePoint::getKind)
                .containsExactly(
                        ChangePointKind.CLASS_ACCESS_NARROWED,
                        ChangePointKind.METHOD_ACCESS_NARROWED,
                        ChangePointKind.FIELD_ACCESS_NARROWED);
    }

    @Test
    void classInitializerDoesNotProduceAccessChange()
            throws Exception {
        final List<ChangePoint> points = diff(
                createJar("old.jar", classWithMethodAccess(
                        "com/Foo", "<clinit>", "()V",
                        Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC)),
                createJar("new.jar", classWithMethodAccess(
                        "com/Foo", "<clinit>", "()V",
                        Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC)));

        assertThat(points)
                .extracting(ChangePoint::getKind)
                .doesNotContain(
                        ChangePointKind.METHOD_ACCESS_NARROWED);
    }

    @Test
    void syntheticBridgeMethodUsesRealBytecodeIdentity()
            throws Exception {
        final int oldAccess = Opcodes.ACC_PUBLIC
                | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE;
        final int newAccess = Opcodes.ACC_PRIVATE
                | Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE;
        final List<ChangePoint> points = diff(
                createJar("old.jar", classWithMethodAccess(
                        "com/Foo", "bridge", "()V", oldAccess)),
                createJar("new.jar", classWithMethodAccess(
                        "com/Foo", "bridge", "()V", newAccess)));

        assertThat(points)
                .extracting(ChangePoint::getKind)
                .containsExactly(
                        ChangePointKind.METHOD_ACCESS_NARROWED);
    }

    @Test
    void overloadedMethodNoFalseDescriptorChange()
            throws Exception {
        final Path oldJar = createJar(
                "old.jar",
                classWithMethods(
                        "com/Foo",
                        new String[]{"bar", "()V"},
                        new String[]{"bar", "(I)V"}));
        final Path newJar = createJar(
                "new.jar",
                classWithMethods(
                        "com/Foo",
                        new String[]{"bar", "()V"},
                        new String[]{"bar", "(I)V"},
                        new String[]{"bar",
                                "(J)V"}));
        final List<ChangePoint> pts =
                diff(oldJar, newJar);
        assertThat(pts)
                .extracting(
                        ChangePoint::getKind)
                .doesNotContain(ChangePointKind
                        .METHOD_DESCRIPTOR_CHANGED);
    }

    @Test
    void detectsFieldAdded()
            throws Exception {
        final Path oldJar = createJar(
                "old.jar",
                classWithField(
                        "com/Foo",
                        "x",
                        "I"));
        final Path newJar = createJar(
                "new.jar",
                classWithFields(
                        "com/Foo",
                        new String[]{"x", "I"},
                        new String[]{"y", "I"}));
        final List<ChangePoint> pts =
                diff(oldJar, newJar);
        assertThat(pts)
                .extracting(
                        ChangePoint::getKind)
                .contains(ChangePointKind
                        .FIELD_ADDED);
    }

    @Test
    void detectsFieldRemoved()
            throws Exception {
        final Path oldJar = createJar(
                "old.jar",
                classWithFields(
                        "com/Foo",
                        new String[]{"x", "I"},
                        new String[]{"y", "I"}));
        final Path newJar = createJar(
                "new.jar",
                classWithField(
                        "com/Foo",
                        "x",
                        "I"));
        final List<ChangePoint> pts =
                diff(oldJar, newJar);
        assertThat(pts)
                .extracting(
                        ChangePoint::getKind)
                .contains(ChangePointKind
                        .FIELD_REMOVED);
    }

    @Test
    void detectsFieldDescriptorChanged()
            throws Exception {
        final Path oldJar = createJar(
                "old.jar",
                classWithField(
                        "com/Foo",
                        "x",
                        "I"));
        final Path newJar = createJar(
                "new.jar",
                classWithField(
                        "com/Foo",
                        "x",
                        "J"));
        final List<ChangePoint> pts =
                diff(oldJar, newJar);
        assertThat(pts)
                .extracting(
                        ChangePoint::getKind)
                .contains(ChangePointKind
                        .FIELD_DESCRIPTOR_CHANGED);
        final ChangePoint change = pts.stream()
                .filter(point -> point.getKind()
                        == ChangePointKind.FIELD_DESCRIPTOR_CHANGED)
                .findFirst().orElseThrow();
        assertThat(change.getOldDescriptor()).isEqualTo("I");
        assertThat(change.getNewDescriptor()).isEqualTo("J");
    }

    @Test
    void detectsEveryFieldStrictNarrowingCombination()
            throws Exception {
        for (JvmAccess[] transition : strictMemberNarrowings()) {
            final List<ChangePoint> points = diff(
                    createJar("old-field.jar", classWithFieldAccess(
                            "com/Foo", "value", "I",
                            accessFlag(transition[0]))),
                    createJar("new-field.jar", classWithFieldAccess(
                            "com/Foo", "value", "I",
                            accessFlag(transition[1]))));

            assertThat(points)
                    .as("%s -> %s", transition[0], transition[1])
                    .extracting(ChangePoint::getKind)
                    .containsExactly(
                            ChangePointKind.FIELD_ACCESS_NARROWED);
            assertThat(points.get(0).getAccessTransition())
                    .contains(new AccessTransition(
                            transition[0], transition[1]));
        }
    }

    @Test
    void fieldExpansionProducesNoAccessChange()
            throws Exception {
        final List<ChangePoint> points = diff(
                createJar("old.jar", classWithFieldAccess(
                        "com/Foo", "value", "I",
                        Opcodes.ACC_PRIVATE)),
                createJar("new.jar", classWithFieldAccess(
                        "com/Foo", "value", "I",
                        Opcodes.ACC_PROTECTED)));

        assertThat(points).isEmpty();
    }

    @Test
    void descriptorChangeDoesNotGuessFieldAccessNarrowing()
            throws Exception {
        final List<ChangePoint> points = diff(
                createJar("old.jar", classWithFieldAccess(
                        "com/Foo", "value", "I",
                        Opcodes.ACC_PUBLIC)),
                createJar("new.jar", classWithFieldAccess(
                        "com/Foo", "value", "J",
                        Opcodes.ACC_PRIVATE)));

        assertThat(points)
                .extracting(ChangePoint::getKind)
                .containsExactly(
                        ChangePointKind.FIELD_DESCRIPTOR_CHANGED);
    }

    @Test
    void debugInfoDoesNotProduceChange()
            throws Exception {
        final byte[] cls1 =
                classWithMethodAndLine(
                        "com/Foo",
                        "bar",
                        "()V",
                        10);
        final byte[] cls2 =
                classWithMethodAndLine(
                        "com/Foo",
                        "bar",
                        "()V",
                        20);
        final Path oldJar = createJar(
                "old.jar", cls1);
        final Path newJar = createJar(
                "new.jar", cls2);
        final List<ChangePoint> pts =
                diff(oldJar, newJar);
        assertThat(pts)
                .extracting(
                        ChangePoint::getKind)
                .doesNotContain(
                        ChangePointKind
                                .METHOD_BODY_CHANGED);
    }

    @Test
    void corruptJarThrowsException() {
        final Path badJar =
                tempDir.resolve("bad.jar");
        try {
            java.nio.file.Files.write(
                    badJar,
                    "not a jar".getBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        final Path okJar = tempDir.resolve(
                "ok.jar");
        try {
            createJarTo(okJar,
                    classBytes("com/Foo"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        assertThatThrownBy(
                () -> diff(badJar, okJar))
                .isInstanceOf(
                        IOException.class);
    }

    @Test
    void moduleInfoIsExcluded()
            throws Exception {
        final byte[] moduleInfo =
                classBytes("module-info");
        final Path oldJar = createJar(
                "old.jar", moduleInfo,
                classBytes("com/Foo"));
        final Path newJar = createJar(
                "new.jar", moduleInfo,
                classBytes("com/Foo"));
        final List<ChangePoint> pts =
                diff(oldJar, newJar);
        assertThat(pts).isEmpty();
    }

    @Test
    void emptyJarsProduceNoChanges()
            throws Exception {
        final Path oldJar = createJar(
                "old.jar");
        final Path newJar = createJar(
                "new.jar");
        final List<ChangePoint> pts =
                diff(oldJar, newJar);
        assertThat(pts).isEmpty();
    }

    @Test
    void resultIsUnmodifiable()
            throws Exception {
        final Path oldJar = createJar(
                "old.jar",
                classBytes("com/Foo"));
        final Path newJar = createJar(
                "new.jar",
                classBytes("com/Foo"));
        final List<ChangePoint> pts =
                diff(oldJar, newJar);
        assertThatThrownBy(
                () -> pts.add(null))
                .isInstanceOf(
                        UnsupportedOperationException
                                .class);
    }

    @Test
    void defaultConstructorExcludesAddedKinds()
            throws Exception {
        final BytecodeDiffEngine defaultEng =
                new BytecodeDiffEngine();
        final Path oldJar = createJar(
                "old.jar",
                classBytes("com/Foo"));
        final Path newJar = createJar(
                "new.jar",
                classBytes("com/Foo"),
                classBytes("com/Bar"));
        final List<ChangePoint> pts =
                diffWith(defaultEng, oldJar, newJar);
        assertThat(pts)
                .extracting(
                        ChangePoint::getKind)
                .doesNotContain(
                        ChangePointKind
                                .CLASS_ADDED);
    }

    @Test
    void excludeAddedKindsProducesNoAdded()
            throws Exception {
        final Path oldJar = createJar(
                "old.jar",
                classWithMethodAndField(
                        "com/Foo"));
        final Path newJar = createJar(
                "new.jar",
                classWithMethodAndField(
                        "com/Foo"),
                classWithMethodAndField(
                        "com/Bar"));
        final BytecodeDiffEngine allEng =
                new BytecodeDiffEngine(
                        EnumSet.allOf(
                                ChangePointKind
                                        .class));
        final List<ChangePoint> allPts =
                diffWith(allEng, oldJar, newJar);
        assertThat(allPts)
                .extracting(
                        ChangePoint::getKind)
                .contains(
                        ChangePointKind
                                .CLASS_ADDED);
        final Set<ChangePointKind> kinds =
                EnumSet.complementOf(
                        EnumSet.of(
                                ChangePointKind
                                        .CLASS_ADDED,
                                ChangePointKind
                                        .METHOD_ADDED,
                                ChangePointKind
                                        .FIELD_ADDED));
        final BytecodeDiffEngine eng =
                new BytecodeDiffEngine(kinds);
        final List<ChangePoint> pts =
                diffWith(eng, oldJar, newJar);
        assertThat(pts)
                .extracting(
                        ChangePoint::getKind)
                .doesNotContain(
                        ChangePointKind
                                .CLASS_ADDED)
                .doesNotContain(
                        ChangePointKind
                                .METHOD_ADDED)
                .doesNotContain(
                        ChangePointKind
                                .FIELD_ADDED);
    }

    @Test
    void onlySpecificKindProducesOnlyThatKind()
            throws Exception {
        final Set<ChangePointKind> kinds =
                EnumSet.of(
                        ChangePointKind
                                .METHOD_BODY_CHANGED);
        final BytecodeDiffEngine eng =
                new BytecodeDiffEngine(kinds);
        final Path oldJar = createJar(
                "old.jar",
                classWithMethodBody(
                        "com/Foo",
                        "bar",
                        "()V",
                        Opcodes.ICONST_0));
        final Path newJar = createJar(
                "new.jar",
                classWithMethodBody(
                        "com/Foo",
                        "bar",
                        "()V",
                        Opcodes.ICONST_1));
        final List<ChangePoint> pts =
                diffWith(eng, oldJar, newJar);
        assertThat(pts)
                .extracting(
                        ChangePoint::getKind)
                .containsExactly(
                        ChangePointKind
                                .METHOD_BODY_CHANGED);
    }

    @Test
    void emptySetProducesNoChangePoints()
            throws Exception {
        final BytecodeDiffEngine eng =
                new BytecodeDiffEngine(
                        Collections.emptySet());
        final Path oldJar = createJar(
                "old.jar",
                classWithMethodBody(
                        "com/Foo",
                        "bar",
                        "()V",
                        Opcodes.ICONST_0));
        final Path newJar = createJar(
                "new.jar",
                classWithMethodBody(
                        "com/Foo",
                        "bar",
                        "()V",
                        Opcodes.ICONST_1));
        final List<ChangePoint> pts =
                diffWith(eng, oldJar, newJar);
        assertThat(pts).isEmpty();
    }

    @Test
    void defaultConstructorBackwardCompatible()
            throws Exception {
        final BytecodeDiffEngine defaultEng =
                new BytecodeDiffEngine();
        final Path oldJar = createJar(
                "old.jar",
                classBytes("com/Foo"),
                classBytes("com/Bar"));
        final Path newJar = createJar(
                "new.jar",
                classBytes("com/Foo"));
        final List<ChangePoint> pts =
                diffWith(defaultEng, oldJar, newJar);
        assertThat(pts)
                .extracting(
                        ChangePoint::getKind)
                .contains(ChangePointKind
                        .CLASS_REMOVED);
    }

    @Test
    void suppressesCrossCompilerLayoutChangeWhenNormalizedSsaMatches()
            throws Exception {
        final Path oldJar = createSsaJar("ssa-old.jar",
                classWithStaticIntMethod(Opcodes.V1_5, 1, false));
        final Path newJar = createSsaJar("ssa-new.jar",
                classWithStaticIntMethod(Opcodes.V1_6, 1, true));

        final BytecodeDiffResult result = diffResult(
                ssaEngine(javaRuntime()), oldJar, newJar);

        assertThat(result.rawChangePointCount()).isEqualTo(1);
        assertThat(result.ssaComparisons()).singleElement()
                .satisfies(evidence -> {
                    assertThat(evidence.getStatus())
                            .as(evidence.getReason())
                            .isEqualTo(SsaComparisonStatus.MATCHED);
                    assertThat(evidence.getOldMajorVersion())
                            .isEqualTo(Opcodes.V1_5);
                    assertThat(evidence.getNewMajorVersion())
                            .isEqualTo(Opcodes.V1_6);
                });
        assertThat(result.changePoints()).isEmpty();
    }

    @Test
    void retainsCrossCompilerMethodWhenNormalizedSsaDiffers()
            throws Exception {
        final Path oldJar = createSsaJar("ssa-different-old.jar",
                classWithStaticIntMethod(Opcodes.V1_5, 1, false));
        final Path newJar = createSsaJar("ssa-different-new.jar",
                classWithStaticIntMethod(Opcodes.V1_6, 2, false));

        final BytecodeDiffResult result = diffResult(
                ssaEngine(javaRuntime()), oldJar, newJar);

        assertThat(result.changePoints())
                .extracting(ChangePoint::getKind)
                .containsExactly(ChangePointKind.METHOD_BODY_CHANGED);
        assertThat(result.ssaComparisons())
                .extracting(SsaComparisonEvidence::getStatus)
                .containsExactly(SsaComparisonStatus.DIFFERENT);
    }

    @Test
    void doesNotRunSsaWhenClassMajorVersionIsUnchanged()
            throws Exception {
        final Path oldJar = createSsaJar("ssa-gate-old.jar",
                classWithStaticIntMethod(Opcodes.V1_6, 1, false));
        final Path newJar = createSsaJar("ssa-gate-new.jar",
                classWithStaticIntMethod(Opcodes.V1_6, 1, true));

        final BytecodeDiffResult result = diffResult(
                ssaEngine(javaRuntime()), oldJar, newJar);

        assertThat(result.changePoints())
                .extracting(ChangePoint::getKind)
                .containsExactly(ChangePointKind.METHOD_BODY_CHANGED);
        assertThat(result.ssaComparisons()).isEmpty();
    }

    @Test
    void retainsEligibleChangeWhenSsaSessionIsUnavailable()
            throws Exception {
        final Path oldJar = createSsaJar("ssa-unknown-old.jar",
                classWithStaticIntMethod(Opcodes.V1_5, 1, false));
        final Path newJar = createSsaJar("ssa-unknown-new.jar",
                classWithStaticIntMethod(Opcodes.V1_6, 1, true));
        final JavaRuntimeDescriptor unavailable = new JavaRuntimeDescriptor(
                tempDir, tempDir, "1.8-test", 8, List.of(), List.of());

        final BytecodeDiffResult result = diffResult(
                ssaEngine(unavailable), oldJar, newJar);

        assertThat(result.changePoints())
                .extracting(ChangePoint::getKind)
                .containsExactly(ChangePointKind.METHOD_BODY_CHANGED);
        assertThat(result.ssaComparisons())
                .extracting(SsaComparisonEvidence::getStatus)
                .containsExactly(SsaComparisonStatus.UNKNOWN);
    }

    private BytecodeDiffEngine ssaEngine(
            final JavaRuntimeDescriptor runtime) {
        return new BytecodeDiffEngine(
                Set.of(ChangePointKind.METHOD_BODY_CHANGED), runtime);
    }

    private JavaRuntimeDescriptor javaRuntime() {
        return new JavaRuntimeProbe().probeJdk8(
                Path.of(System.getenv("TEST_JDK8_HOME")));
    }

    private BytecodeDiffResult diffResult(
            final BytecodeDiffEngine selectedEngine,
            final Path oldJar,
            final Path newJar) throws Exception {
        final DependencyChange change = new DependencyChange(
                ChangeType.VERSION_CHANGED, OLD, NEW,
                DependencyScope.COMPILE, "root");
        try (IJarRepository repository = TestJarRepositories.pair(
                OLD, oldJar, NEW, newJar)) {
            return selectedEngine.diff(change, repository);
        }
    }

    /**
     * Runs the diff engine.
     *
     * @param oldJar old jar path
     * @param newJar new jar path
     * @return change points
     * @throws Exception on repository or diff error
     */
    private List<ChangePoint> diff(
            final Path oldJar,
            final Path newJar)
            throws Exception {
        final DependencyChange change =
                new DependencyChange(
                        ChangeType
                                .VERSION_CHANGED,
                        OLD, NEW,
                        DependencyScope
                                .COMPILE,
                        "root");
        try (IJarRepository repository = TestJarRepositories.pair(
                OLD, oldJar, NEW, newJar)) {
            return engine.diff(change, repository).changePoints();
        }
    }

    /**
     * Runs the diff with a specific
     * engine instance.
     *
     * @param eng    engine instance
     * @param oldJar old jar path
     * @param newJar new jar path
     * @return change points
     * @throws Exception on repository or diff error
     */
    private List<ChangePoint> diffWith(
            final BytecodeDiffEngine eng,
            final Path oldJar,
            final Path newJar)
            throws Exception {
        final DependencyChange change =
                new DependencyChange(
                        ChangeType
                                .VERSION_CHANGED,
                        OLD, NEW,
                        DependencyScope
                                .COMPILE,
                        "root");
        try (IJarRepository repository = TestJarRepositories.pair(
                OLD, oldJar, NEW, newJar)) {
            return eng.diff(change, repository).changePoints();
        }
    }

    /**
     * Creates a jar with class entries.
     *
     * @param name      jar file name
     * @param classData class byte arrays
     * @return jar path
     * @throws IOException on write error
     */
    private Path createJar(
            final String name,
            final byte[]... classData)
            throws IOException {
        final Path jar =
                tempDir.resolve(name);
        createJarTo(jar, classData);
        return jar;
    }

    private Path createSsaJar(
            final String name,
            final byte[] classData) throws IOException {
        final Path jar = tempDir.resolve(name);
        try (OutputStream output = java.nio.file.Files.newOutputStream(jar);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(
                    "example/CompilerLayout.class"));
            zip.write(classData);
            zip.closeEntry();
        }
        return jar;
    }

    /**
     * Writes class data to a jar file.
     *
     * @param jar       jar path
     * @param classData class byte arrays
     * @throws IOException on write error
     */
    private void createJarTo(
            final Path jar,
            final byte[]... classData)
            throws IOException {
        try (OutputStream fos =
                     java.nio.file.Files
                             .newOutputStream(
                                     jar);
             ZipOutputStream zos =
                     new ZipOutputStream(
                             fos)) {
            for (int i = 0;
                 i < classData.length;
                 i++) {
                final String entryName =
                        "class" + i + ".class";
                zos.putNextEntry(
                        new ZipEntry(
                                entryName));
                zos.write(classData[i]);
                zos.closeEntry();
            }
        }
    }

    /**
     * Generates minimal class bytes.
     *
     * @param internalName internal name
     * @return class bytes
     */
    private byte[] classBytes(
            final String internalName) {
        return classBytes(
                internalName,
                Opcodes.ACC_PUBLIC);
    }

    /**
     * Generates minimal class bytes with explicit access.
     *
     * @param internalName internal name
     * @param access class access flags
     * @return class bytes
     */
    private byte[] classBytes(
            final String internalName,
            final int access) {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(
                Opcodes.V1_8,
                access,
                internalName,
                null,
                "java/lang/Object",
                null);
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class with one explicitly accessible method.
     *
     * @param cls class internal name
     * @param name method name
     * @param desc method descriptor
     * @param access method access flags
     * @return class bytes
     */
    private byte[] classWithMethodAccess(
            final String cls,
            final String name,
            final String desc,
            final int access) {
        final ClassWriter cw = new ClassWriter(0);
        cw.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                cls, null,
                "java/lang/Object", null);
        final MethodVisitor mv = cw.visitMethod(
                access, name, desc, null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class with explicit method access and integer body.
     *
     * @param cls class internal name
     * @param name method name
     * @param desc method descriptor
     * @param access method access flags
     * @param opcode integer-producing body opcode
     * @return class bytes
     */
    private byte[] classWithMethodAccessAndBody(
            final String cls,
            final String name,
            final String desc,
            final int access,
            final int opcode) {
        final ClassWriter cw = new ClassWriter(0);
        cw.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                cls, null,
                "java/lang/Object", null);
        final MethodVisitor mv = cw.visitMethod(
                access, name, desc, null, null);
        mv.visitCode();
        mv.visitInsn(opcode);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates a class with one explicitly accessible field.
     *
     * @param cls class internal name
     * @param name field name
     * @param desc field descriptor
     * @param access field access flags
     * @return class bytes
     */
    private byte[] classWithFieldAccess(
            final String cls,
            final String name,
            final String desc,
            final int access) {
        final ClassWriter cw = new ClassWriter(0);
        cw.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                cls, null,
                "java/lang/Object", null);
        cw.visitField(access, name, desc, null, null).visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates one class with explicit class and member access.
     *
     * @param cls class internal name
     * @param classAccess class access flags
     * @param methodAccess method access flags
     * @param fieldAccess field access flags
     * @return class bytes
     */
    private byte[] classWithAccesses(
            final String cls,
            final int classAccess,
            final int methodAccess,
            final int fieldAccess) {
        final ClassWriter cw = new ClassWriter(0);
        cw.visit(
                Opcodes.V1_8,
                classAccess,
                cls, null,
                "java/lang/Object", null);
        final MethodVisitor mv = cw.visitMethod(
                methodAccess, "run", "()V", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();
        cw.visitField(
                fieldAccess, "value", "I", null, null).visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** @return all confirmed member access narrowing combinations */
    private JvmAccess[][] strictMemberNarrowings() {
        return new JvmAccess[][]{
                {JvmAccess.PUBLIC, JvmAccess.PROTECTED},
                {JvmAccess.PUBLIC, JvmAccess.PACKAGE_PRIVATE},
                {JvmAccess.PUBLIC, JvmAccess.PRIVATE},
                {JvmAccess.PROTECTED, JvmAccess.PACKAGE_PRIVATE},
                {JvmAccess.PROTECTED, JvmAccess.PRIVATE},
                {JvmAccess.PACKAGE_PRIVATE, JvmAccess.PRIVATE}
        };
    }

    /**
     * Converts normalized member access to ASM flags.
     *
     * @param access normalized access
     * @return ASM access flags
     */
    private int accessFlag(final JvmAccess access) {
        return switch (access) {
            case PUBLIC -> Opcodes.ACC_PUBLIC;
            case PROTECTED -> Opcodes.ACC_PROTECTED;
            case PACKAGE_PRIVATE -> 0;
            case PRIVATE -> Opcodes.ACC_PRIVATE;
        };
    }

    /**
     * Generates class with one method.
     *
     * @param cls  internal class name
     * @param name method name
     * @param desc method descriptor
     * @return class bytes
     */
    private byte[] classWithMethod(
            final String cls,
            final String name,
            final String desc) {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                cls, null,
                "java/lang/Object",
                null);
        final MethodVisitor mv =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        name, desc,
                        null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates class with multiple
     * methods.
     *
     * @param cls     internal class name
     * @param methods pairs of name,desc
     * @return class bytes
     */
    private byte[] classWithMethods(
            final String cls,
            final String[]... methods) {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                cls, null,
                "java/lang/Object",
                null);
        for (final String[] m : methods) {
            final MethodVisitor mv =
                    cw.visitMethod(
                            Opcodes.ACC_PUBLIC,
                            m[0], m[1],
                            null, null);
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 1);
            mv.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates class with method body
     * containing a specific opcode.
     *
     * @param cls    internal class name
     * @param name   method name
     * @param desc   method descriptor
     * @param opcode instruction opcode
     * @return class bytes
     */
    private byte[] classWithMethodBody(
            final String cls,
            final String name,
            final String desc,
            final int opcode) {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                cls, null,
                "java/lang/Object",
                null);
        final MethodVisitor mv =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        name, desc,
                        null, null);
        mv.visitCode();
        mv.visitInsn(opcode);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private byte[] classWithStaticIntMethod(
            final int classVersion,
            final int constant,
            final boolean localRoundTrip) {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(classVersion, Opcodes.ACC_PUBLIC,
                "example/CompilerLayout", null, "java/lang/Object", null);
        final MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "value", "()I", null, null);
        method.visitCode();
        method.visitInsn(constant == 1 ? Opcodes.ICONST_1
                : Opcodes.ICONST_2);
        if (localRoundTrip) {
            method.visitVarInsn(Opcodes.ISTORE, 0);
            method.visitVarInsn(Opcodes.ILOAD, 0);
        }
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, localRoundTrip ? 1 : 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    /**
     * Generates class with method and
     * a line number for debug info.
     *
     * @param cls    internal class name
     * @param name   method name
     * @param desc   method descriptor
     * @param line   line number
     * @return class bytes
     */
    private byte[] classWithMethodAndLine(
            final String cls,
            final String name,
            final String desc,
            final int line) {
        final ClassWriter cw =
                new ClassWriter(
                        ClassWriter
                                .COMPUTE_FRAMES);
        cw.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                cls, null,
                "java/lang/Object",
                null);
        final MethodVisitor mv =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        name, desc,
                        null, null);
        mv.visitCode();
        final org.objectweb.asm.Label lbl =
                new org.objectweb.asm.Label();
        mv.visitLabel(lbl);
        mv.visitLineNumber(line, lbl);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates class with one field.
     *
     * @param cls  internal class name
     * @param name field name
     * @param desc field descriptor
     * @return class bytes
     */
    private byte[] classWithField(
            final String cls,
            final String name,
            final String desc) {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                cls, null,
                "java/lang/Object",
                null);
        final FieldVisitor fv =
                cw.visitField(
                        Opcodes.ACC_PUBLIC,
                        name, desc,
                        null, null);
        fv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates class with multiple
     * fields.
     *
     * @param cls    internal class name
     * @param fields pairs of name,desc
     * @return class bytes
     */
    private byte[] classWithFields(
            final String cls,
            final String[]... fields) {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                cls, null,
                "java/lang/Object",
                null);
        for (final String[] f : fields) {
            final FieldVisitor fv =
                    cw.visitField(
                            Opcodes.ACC_PUBLIC,
                            f[0], f[1],
                            null, null);
            fv.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generates class with one method
     * and one field.
     *
     * @param cls internal class name
     * @return class bytes
     */
    private byte[] classWithMethodAndField(
            final String cls) {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                cls, null,
                "java/lang/Object",
                null);
        final FieldVisitor fv =
                cw.visitField(
                        Opcodes.ACC_PUBLIC,
                        "x", "I",
                        null, null);
        fv.visitEnd();
        final MethodVisitor mv =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        "bar", "()V",
                        null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }
}
