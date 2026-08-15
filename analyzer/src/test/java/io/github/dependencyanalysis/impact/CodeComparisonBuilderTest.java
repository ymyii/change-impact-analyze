package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.bytecode.AccessTransition;
import io.github.dependencyanalysis.bytecode.JvmAccess;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.dependency.ResolvedArtifact;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;
import io.github.dependencyanalysis.testing.TestJarRepositories;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests user-reviewable bytecode-derived code comparisons. */
class CodeComparisonBuilderTest {

    /** Test owner. */
    private static final String OWNER = "com/example/Changed";

    /** Baseline artifact. */
    private static final ArtifactCoord OLD =
            new ArtifactCoord("g", "a", "jar", "1");

    /** Target artifact. */
    private static final ArtifactCoord TARGET =
            new ArtifactCoord("g", "a", "jar", "2");

    /** Temporary directory. */
    @TempDir
    private Path temporary;

    @Test
    void createsDecompiledUnifiedDiffForChangedMethod() throws Exception {
        final Path oldJar = jar("old.jar", bytes(1, true));
        final Path newJar = jar("new.jar", bytes(2, true));
        final ChangePoint point = new ChangePoint(TARGET,
                ChangePointKind.METHOD_BODY_CHANGED, OWNER, "value", "()I",
                "old", "new");

        final CodeComparisonEvidence evidence = builder(oldJar, newJar)
                .build(bound(point));

        assertThat(evidence.getStatus()).isEqualTo(
                CodeComparisonStatus.AVAILABLE);
        assertThat(evidence.getUnifiedDiff())
                .startsWith("--- old/decompiled.java")
                .contains("-      return 1;")
                .contains("+      return 2;");
        assertThat(evidence.getHunks()).isNotEmpty();
    }

    @Test
    void reportsIdenticalJavaWithoutAsmFallback() throws Exception {
        final Path oldJar = jar("identical-old.jar", bytes(1, true));
        final Path newJar = jar("identical-new.jar", bytes(1, true));
        final ChangePoint point = new ChangePoint(TARGET,
                ChangePointKind.METHOD_BODY_CHANGED, OWNER, "value", "()I",
                "old", "new");

        final CodeComparisonEvidence evidence = builder(oldJar, newJar)
                .build(bound(point));

        assertThat(evidence.getStatus())
                .isEqualTo(CodeComparisonStatus.JAVA_TEXT_IDENTICAL);
        assertThat(evidence.getUnifiedDiff()).isEmpty();
    }

    @Test
    void reportsUnavailableWhenJavaCannotBeDecompiled() throws Exception {
        final Path oldJar = jar("missing-old.jar", bytes(1, true));
        final Path newJar = jar("missing-new.jar", bytes(2, true));
        final ChangePoint point = new ChangePoint(TARGET,
                ChangePointKind.METHOD_BODY_CHANGED, "missing/Changed",
                "value", "()I", "old", "new");

        final CodeComparisonEvidence evidence = builder(oldJar, newJar)
                .build(bound(point));

        assertThat(evidence.getStatus())
                .isEqualTo(CodeComparisonStatus.UNAVAILABLE);
        assertThat(evidence.getUnifiedDiff()).isEmpty();
    }

    @Test
    void rendersRemovedFieldDeclarationAgainstEmptyTarget() throws Exception {
        final Path oldJar = jar("field-old.jar", bytes(1, true));
        final Path newJar = jar("field-new.jar", bytes(1, false));
        final ChangePoint point = new ChangePoint(TARGET,
                ChangePointKind.FIELD_REMOVED, OWNER, "count", "I",
                null, null);

        final CodeComparisonEvidence evidence = builder(oldJar, newJar)
                .build(bound(point));

        assertThat(evidence.getStatus()).isEqualTo(
                CodeComparisonStatus.AVAILABLE);
        assertThat(evidence.getUnifiedDiff())
                .contains("-private int count;");
    }

    @Test
    void rendersAccessOnlyModifierChanges() throws Exception {
        final AccessTransition memberTransition = new AccessTransition(
                JvmAccess.PUBLIC, JvmAccess.PROTECTED);
        assertAccessDiff(
                ChangePoint.accessNarrowed(TARGET,
                        ChangePointKind.METHOD_ACCESS_NARROWED,
                        OWNER, "value", "()I", memberTransition),
                accessBytes(Opcodes.ACC_PUBLIC, Opcodes.ACC_PUBLIC,
                        Opcodes.ACC_PUBLIC, Opcodes.ACC_PUBLIC),
                accessBytes(Opcodes.ACC_PUBLIC, Opcodes.ACC_PROTECTED,
                        Opcodes.ACC_PUBLIC, Opcodes.ACC_PUBLIC),
                "-   public static int value()",
                "+   protected static int value()");
        assertAccessDiff(
                ChangePoint.accessNarrowed(TARGET,
                        ChangePointKind.METHOD_ACCESS_NARROWED,
                        OWNER, "<init>", "()V", memberTransition),
                accessBytes(Opcodes.ACC_PUBLIC, Opcodes.ACC_PUBLIC,
                        Opcodes.ACC_PUBLIC, Opcodes.ACC_PUBLIC),
                accessBytes(Opcodes.ACC_PUBLIC, Opcodes.ACC_PUBLIC,
                        Opcodes.ACC_PROTECTED, Opcodes.ACC_PUBLIC),
                "-   public Changed()",
                "+   protected Changed()");
        assertAccessDiff(
                ChangePoint.accessNarrowed(TARGET,
                        ChangePointKind.FIELD_ACCESS_NARROWED,
                        OWNER, "count", "I", memberTransition),
                accessBytes(Opcodes.ACC_PUBLIC, Opcodes.ACC_PUBLIC,
                        Opcodes.ACC_PUBLIC, Opcodes.ACC_PUBLIC),
                accessBytes(Opcodes.ACC_PUBLIC, Opcodes.ACC_PUBLIC,
                        Opcodes.ACC_PUBLIC, Opcodes.ACC_PROTECTED),
                "-public int count;", "+protected int count;");
        assertAccessDiff(
                ChangePoint.accessNarrowed(TARGET,
                        ChangePointKind.CLASS_ACCESS_NARROWED,
                        OWNER, null, null, new AccessTransition(
                        JvmAccess.PUBLIC, JvmAccess.PACKAGE_PRIVATE)),
                accessBytes(Opcodes.ACC_PUBLIC, Opcodes.ACC_PUBLIC,
                        Opcodes.ACC_PUBLIC, Opcodes.ACC_PUBLIC),
                accessBytes(0, Opcodes.ACC_PUBLIC,
                        Opcodes.ACC_PUBLIC, Opcodes.ACC_PUBLIC),
                "-public class Changed", "+class Changed");
    }

    @Test
    void keepsThreeContextLinesInSeparatedHunks() {
        final String oldText = numbered("old", 20);
        final String newText = oldText.replace("old-2\n", "new-2\n")
                .replace("old-18\n", "new-18\n");

        final java.util.List<UnifiedDiffHunk> hunks =
                new UnifiedDiffGenerator().diff(oldText, newText);

        assertThat(hunks).hasSize(2);
        assertThat(hunks.get(0).lines())
                .contains(" old-1", " old-5")
                .doesNotContain(" old-6");
        assertThat(hunks.get(1).lines())
                .contains(" old-15", " old-20")
                .doesNotContain(" old-14");
    }

    private String numbered(final String prefix, final int count) {
        final StringBuilder result = new StringBuilder();
        for (int index = 1; index <= count; index++) {
            result.append(prefix).append('-').append(index).append('\n');
        }
        return result.toString();
    }

    private void assertAccessDiff(
            final ChangePoint point,
            final byte[] oldBytes,
            final byte[] newBytes,
            final String removed,
            final String added) throws Exception {
        final Path oldJar = jar(point.getKind() + "-old.jar", oldBytes);
        final Path newJar = jar(point.getKind() + "-new.jar", newBytes);
        final CodeComparisonEvidence evidence = builder(oldJar, newJar)
                .build(bound(point));

        assertThat(evidence.getStatus()).as(point.getKind().name())
                .isEqualTo(CodeComparisonStatus.AVAILABLE);
        assertThat(evidence.getUnifiedDiff()).as(point.getKind().name())
                .contains(removed, added);
    }

    private CodeComparisonBuilder builder(
            final Path oldJar,
            final Path newJar) throws Exception {
        final PrintStream sink = new PrintStream(new ByteArrayOutputStream());
        return new CodeComparisonBuilder(
                new DiagnosticLog(sink, LogVerbosity.INFO),
                TestJarRepositories.of(List.of(
                        new ResolvedArtifact(OLD, oldJar),
                        new ResolvedArtifact(TARGET, newJar))));
    }

    private BoundChangePoint bound(final ChangePoint point) {
        final ModuleId module = new ModuleId(
                new ArtifactCoord("g", "app", "jar", "1"), Path.of("app"));
        return new BoundChangePoint(new DependencyUpgradeKey(
                module, DependencyScope.COMPILE, OLD, TARGET), point);
    }

    private Path jar(final String name, final byte[] classBytes)
            throws Exception {
        final Path result = temporary.resolve(name);
        try (OutputStream output = Files.newOutputStream(result);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(OWNER + ".class"));
            zip.write(classBytes);
            zip.closeEntry();
        }
        return result;
    }

    private byte[] bytes(final int value, final boolean field) {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, OWNER, null,
                "java/lang/Object", null);
        if (field) {
            final FieldVisitor visitor = writer.visitField(
                    Opcodes.ACC_PRIVATE, "count", "I", null, null);
            visitor.visitEnd();
        }
        final MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "value", "()I", null, null);
        method.visitCode();
        method.visitLdcInsn(value);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private byte[] accessBytes(
            final int classAccess,
            final int methodAccess,
            final int constructorAccess,
            final int fieldAccess) {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, classAccess, OWNER, null,
                "java/lang/Object", null);
        writer.visitField(fieldAccess, "count", "I", null, null).visitEnd();
        final MethodVisitor constructor = writer.visitMethod(
                constructorAccess, "<init>", "()V", null, null);
        constructor.visitCode();
        constructor.visitVarInsn(Opcodes.ALOAD, 0);
        constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false);
        constructor.visitInsn(Opcodes.RETURN);
        constructor.visitMaxs(1, 1);
        constructor.visitEnd();
        final MethodVisitor method = writer.visitMethod(
                methodAccess | Opcodes.ACC_STATIC,
                "value", "()I", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
