package io.github.changeimpact.analyze.bytecode;

import io.github.changeimpact.analyze.dependency.ArtifactCoord;
import io.github.changeimpact.analyze.dependency.ChangeType;
import io.github.changeimpact.analyze.dependency.DependencyChange;
import io.github.changeimpact.analyze.dependency.DependencyScope;
import io.github.changeimpact.analyze.jar.JarLocationResult;
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
                .contains(ChangePointKind
                        .METHOD_DESCRIPTOR_CHANGED);
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
                        BytecodeDiffException.class);
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

    /**
     * Runs the diff engine.
     *
     * @param oldJar old jar path
     * @param newJar new jar path
     * @return change points
     * @throws BytecodeDiffException
     *  on error
     */
    private List<ChangePoint> diff(
            final Path oldJar,
            final Path newJar)
            throws BytecodeDiffException {
        final DependencyChange change =
                new DependencyChange(
                        ChangeType
                                .VERSION_CHANGED,
                        OLD, NEW,
                        DependencyScope
                                .COMPILE,
                        "root");
        final JarLocationResult loc =
                new JarLocationResult(
                        change, oldJar,
                        newJar);
        return engine.diff(loc);
    }

    /**
     * Runs the diff with a specific
     * engine instance.
     *
     * @param eng    engine instance
     * @param oldJar old jar path
     * @param newJar new jar path
     * @return change points
     * @throws BytecodeDiffException
     *  on error
     */
    private List<ChangePoint> diffWith(
            final BytecodeDiffEngine eng,
            final Path oldJar,
            final Path newJar)
            throws BytecodeDiffException {
        final DependencyChange change =
                new DependencyChange(
                        ChangeType
                                .VERSION_CHANGED,
                        OLD, NEW,
                        DependencyScope
                                .COMPILE,
                        "root");
        final JarLocationResult loc =
                new JarLocationResult(
                        change, oldJar,
                        newJar);
        return eng.diff(loc);
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
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                internalName,
                null,
                "java/lang/Object",
                null);
        cw.visitEnd();
        return cw.toByteArray();
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
