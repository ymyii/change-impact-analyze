package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.diagnostic.DiagnosticCollector;

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

        final CodeComparisonEvidence evidence = builder().build(
                bound(oldJar, newJar, point));

        assertThat(evidence.getStatus()).isEqualTo(
                CodeComparisonStatus.AVAILABLE);
        assertThat(evidence.getUnifiedDiff())
                .startsWith("--- old/decompiled.java")
                .contains("-      return 1;")
                .contains("+      return 2;");
        assertThat(evidence.getHunks()).isNotEmpty();
    }

    @Test
    void rendersRemovedFieldDeclarationAgainstEmptyTarget() throws Exception {
        final Path oldJar = jar("field-old.jar", bytes(1, true));
        final Path newJar = jar("field-new.jar", bytes(1, false));
        final ChangePoint point = new ChangePoint(TARGET,
                ChangePointKind.FIELD_REMOVED, OWNER, "count", "I",
                null, null);

        final CodeComparisonEvidence evidence = builder().build(
                bound(oldJar, newJar, point));

        assertThat(evidence.getStatus()).isEqualTo(
                CodeComparisonStatus.AVAILABLE);
        assertThat(evidence.getUnifiedDiff())
                .contains("-private int count;");
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

    private CodeComparisonBuilder builder() {
        final PrintStream sink = new PrintStream(new ByteArrayOutputStream());
        return new CodeComparisonBuilder(new DiagnosticCollector(sink, sink));
    }

    private BoundChangePoint bound(
            final Path oldJar,
            final Path newJar,
            final ChangePoint point) {
        final ModuleId module = new ModuleId(
                new ArtifactCoord("g", "app", "jar", "1"), Path.of("app"));
        return new BoundChangePoint(new DependencyUpgradeKey(
                module, DependencyScope.COMPILE, OLD, TARGET,
                oldJar, newJar), point);
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
}
