package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.build.BuildResult;
import io.github.dependencyanalysis.build.ModuleBuildOutput;
import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.callgraph.MethodId;
import io.github.dependencyanalysis.dependency.ArtifactCoord;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests exact ChangePoint seed indexing at large input size. */
class ChangePointRefScannerTest {

    /** Large change-point fixture size. */
    private static final int CHANGE_POINT_COUNT = 15_000;

    /** Temporary classes directory. */
    @TempDir
    private Path temporary;

    @Test
    void scansOnlyTheExactMethodKey() throws Exception {
        final Path classes = temporary.resolve("classes");
        Files.createDirectories(classes);
        writeCaller(classes);
        final ArtifactCoord artifact = new ArtifactCoord(
                "g", "a", "jar", "2.0");
        final List<ChangePoint> points = new ArrayList<>();
        ChangePoint requested = null;
        for (int index = 0; index < CHANGE_POINT_COUNT; index++) {
            final String name = "method" + index;
            final ChangePoint point = new ChangePoint(
                    artifact, ChangePointKind.METHOD_REMOVED,
                    "dep/Library", name, "()V", null, null);
            points.add(point);
            if (index == CHANGE_POINT_COUNT - 1) {
                requested = point;
            }
        }
        final BuildResult build = BuildResult.of(List.of(
                new ModuleBuildOutput(temporary, classes)));

        final Map<ChangePoint, Set<MethodId>> seeds =
                new ChangePointRefScanner().scan(points, build);

        final ChangePoint exact = requested;
        assertThat(seeds.entrySet().stream()
                .filter(entry -> !entry.getValue().isEmpty()))
                .singleElement()
                .satisfies(entry -> assertThat(entry.getKey())
                        .isEqualTo(exact));
    }

    private void writeCaller(final Path classes)
            throws Exception {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V17, Opcodes.ACC_PUBLIC,
                "app/Caller", null, "java/lang/Object", null);
        final MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "call", "()V", null, null);
        method.visitCode();
        method.visitMethodInsn(Opcodes.INVOKESTATIC,
                "dep/Library",
                "method" + (CHANGE_POINT_COUNT - 1),
                "()V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        final Path output = classes.resolve("app/Caller.class");
        Files.createDirectories(output.getParent());
        Files.write(output, writer.toByteArray());
    }
}
