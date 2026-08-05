package io.github.dependencyanalysis.bytecode;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ChangeType;
import io.github.dependencyanalysis.dependency.DependencyChange;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;
import io.github.dependencyanalysis.diagnostic.DiagnosticLevel;
import io.github.dependencyanalysis.jar.IJarRepository;
import io.github.dependencyanalysis.testing.TestJarRepositories;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for in-memory Vineflower method evidence. */
class MethodBodyDecompilerTest {

    /** Test class owner. */
    private static final String OWNER = "com/example/Foo";

    /** Baseline artifact. */
    private static final ArtifactCoord OLD =
            new ArtifactCoord("g", "a", "jar", "1.0");

    /** Target artifact. */
    private static final ArtifactCoord NEW =
            new ArtifactCoord("g", "a", "jar", "2.0");

    /** Temporary directory. */
    @TempDir
    private Path temporary;

    /** Diagnostics. */
    private DiagnosticLog diagnostics;

    /** Decompiler under test. */
    private MethodBodyDecompiler decompiler;

    @BeforeEach
    void setUp() {
        final PrintStream sink = new PrintStream(
                new ByteArrayOutputStream());
        diagnostics = new DiagnosticLog(sink, LogVerbosity.INFO);
        decompiler = new MethodBodyDecompiler(diagnostics);
    }

    @Test
    void decompilesOldAndNewMethodWithExactDescriptor()
            throws Exception {
        final Path oldJar = createJar("old.jar", classBytes(1, 3));
        final Path newJar = createJar("new.jar", classBytes(2, 4));
        final ChangePoint point = point(
                "value", "()I");

        final MethodBodyEvidence evidence;
        try (IJarRepository repository = TestJarRepositories.pair(
                OLD, oldJar, NEW, newJar)) {
            evidence = decompiler.decompile(
                    change(), repository, point);
        }

        assertThat(evidence.getOldMethod().isAvailable())
                .isTrue();
        assertThat(evidence.getNewMethod().isAvailable())
                .isTrue();
        assertThat(evidence.getOldMethod().getSource())
                .contains("value()")
                .contains("return 1;")
                .doesNotContain("value(int");
        assertThat(evidence.getNewMethod().getSource())
                .contains("value()")
                .contains("return 2;")
                .doesNotContain("value(int");
    }

    @Test
    void decompilesConstructorByJvmIdentifier()
            throws Exception {
        final Path oldJar = createJar(
                "ctor-old.jar", classBytes(1, 3));
        final Path newJar = createJar(
                "ctor-new.jar", classBytes(1, 4));
        final ChangePoint point = point(
                "<init>", "()V");

        final MethodBodyEvidence evidence;
        try (IJarRepository repository = TestJarRepositories.pair(
                OLD, oldJar, NEW, newJar)) {
            evidence = decompiler.decompile(
                    change(), repository, point);
        }

        assertThat(evidence.getOldMethod().getSource())
                .contains("Foo()")
                .contains("consume(3)");
        assertThat(evidence.getNewMethod().getSource())
                .contains("Foo()")
                .contains("consume(4)");
    }

    @Test
    void preservesAvailableSideAndWarnsWhenOtherSideFails()
            throws Exception {
        final Path missing = createEmptyJar("missing.jar");
        final Path present = createJar(
                "present.jar", classBytes(2, 4));

        final MethodBodyEvidence evidence;
        try (IJarRepository repository = TestJarRepositories.pair(
                OLD, missing, NEW, present)) {
            evidence = decompiler.decompile(
                    change(), repository, point("value", "()I"));
        }

        assertThat(evidence.getOldMethod().isAvailable())
                .isFalse();
        assertThat(evidence.getOldMethod().getFailureReason())
                .contains("Class entry not found");
        assertThat(evidence.getNewMethod().isAvailable())
                .isTrue();
        assertThat(diagnostics.getEvents())
                .anySatisfy(event -> {
                    assertThat(event.getStage()).isEqualTo(
                            MethodBodyDecompiler.STAGE);
                    assertThat(event.getLevel()).isEqualTo(
                            DiagnosticLevel.WARN);
                    assertThat(event.getMessage())
                            .contains("old method");
                });
    }

    private ChangePoint point(
            final String name,
            final String descriptor) {
        return new ChangePoint(NEW,
                ChangePointKind.METHOD_BODY_CHANGED,
                OWNER, name, descriptor,
                "old-hash", "new-hash");
    }

    private DependencyChange change() {
        return new DependencyChange(
                ChangeType.VERSION_CHANGED,
                OLD, NEW,
                DependencyScope.COMPILE,
                "root");
    }

    private Path createJar(
            final String name,
            final byte[] bytes)
            throws IOException {
        final Path jar = temporary.resolve(name);
        try (OutputStream output =
                     Files.newOutputStream(jar);
             ZipOutputStream zip =
                     new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(
                    OWNER + ".class"));
            zip.write(bytes);
            zip.closeEntry();
        }
        return jar;
    }

    private Path createEmptyJar(final String name)
            throws IOException {
        final Path jar = temporary.resolve(name);
        try (OutputStream output =
                     Files.newOutputStream(jar);
             ZipOutputStream zip =
                     new ZipOutputStream(output)) {
            zip.finish();
        }
        return jar;
    }

    private byte[] classBytes(
            final int value,
            final int marker) {
        final ClassWriter writer =
                new ClassWriter(0);
        writer.visit(Opcodes.V17,
                Opcodes.ACC_PUBLIC,
                OWNER, null,
                "java/lang/Object", null);
        final FieldVisitor field = writer.visitField(
                Opcodes.ACC_PRIVATE
                        | Opcodes.ACC_FINAL,
                "marker", "I", null, null);
        field.visitEnd();
        addConstructor(writer, marker);
        addConsume(writer);
        addNoArgValue(writer, value);
        addOverloadedValue(writer);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private void addConstructor(
            final ClassWriter writer,
            final int marker) {
        final MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>",
                "()V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>",
                "()V", false);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitLdcInsn(marker);
        method.visitFieldInsn(Opcodes.PUTFIELD,
                OWNER, "marker", "I");
        method.visitLdcInsn(marker);
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                OWNER, "consume", "(I)V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(2, 1);
        method.visitEnd();
    }

    private void addConsume(
            final ClassWriter writer) {
        final MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                "consume", "(I)V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
    }

    private void addNoArgValue(
            final ClassWriter writer,
            final int value) {
        final MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "value", "()I", null, null);
        method.visitCode();
        method.visitLdcInsn(value);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
    }

    private void addOverloadedValue(
            final ClassWriter writer) {
        final MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "value", "(I)I", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
    }
}
