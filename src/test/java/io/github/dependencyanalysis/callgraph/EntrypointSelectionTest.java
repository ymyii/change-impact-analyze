package io.github.dependencyanalysis.callgraph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests the user-declared PROJECT entrypoint boundary. */
class EntrypointSelectionTest {

    /** Temporary class output. */
    @TempDir
    private Path temporary;

    @Test
    void supportsExactAndRecursivePackagesAndNestedClasses() {
        final EntrypointPattern exact = EntrypointPattern.parse(
                "com.icbc:Order*");
        final EntrypointPattern recursive = EntrypointPattern.parse(
                "com.icbc.**:*$Handler");

        assertThat(exact.matchesInternalName("com/icbc/OrderService"))
                .isTrue();
        assertThat(exact.matchesInternalName("com/icbc/sub/OrderService"))
                .isFalse();
        assertThat(recursive.matchesInternalName(
                "com/icbc/sub/Order$Handler")).isTrue();
        assertThat(recursive.matchesInternalName(
                "com/icbc/Order$Handler")).isTrue();
    }

    @Test
    void combinesIncludesAndLetsExcludesWin() {
        final EntrypointSelection selection = EntrypointSelection.parse(
                List.of("com.acme:Order*", "com.acme.api.**:Public*"),
                List.of("com.acme:OrderInternal"));

        assertThat(selection.matchesInternalName("com/acme/OrderService"))
                .isTrue();
        assertThat(selection.matchesInternalName("com/acme/OrderInternal"))
                .isFalse();
        assertThat(selection.matchesInternalName(
                "com/acme/api/v1/PublicController")).isTrue();
        assertThat(selection.matchesInternalName("com/acme/Other"))
                .isFalse();
    }

    @Test
    void rejectsUnsupportedPackageWildcards() {
        assertThatThrownBy(() -> EntrypointPattern.parse("com.*:Controller"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EntrypointPattern.parse("com.**.api:*"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lightweightScannerCountsOnlySelectedDeclaredConcreteMethods()
            throws Exception {
        writeClass("com/acme/Selected", true);
        writeClass("com/acme/Other", false);
        final EntrypointSelection selection = EntrypointSelection.parse(
                List.of("com.acme:Selected"), List.of());

        final EntrypointSelectionMetrics metrics =
                new EntrypointClassScanner().scan(temporary, selection);

        assertThat(metrics.selectedClassCount()).isEqualTo(1);
        assertThat(metrics.entrypointCount()).isEqualTo(2);
    }

    private void writeClass(final String owner, final boolean abstractMethod)
            throws Exception {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, owner, null,
                "java/lang/Object", null);
        addMethod(writer, "one", Opcodes.ACC_PUBLIC);
        addMethod(writer, "two", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        if (abstractMethod) {
            writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                    "abstractMethod", "()V", null, null).visitEnd();
        }
        writer.visitEnd();
        final Path output = temporary.resolve(owner + ".class");
        Files.createDirectories(output.getParent());
        Files.write(output, writer.toByteArray());
    }

    private void addMethod(
            final ClassWriter writer, final String name, final int access) {
        final MethodVisitor method = writer.visitMethod(
                access, name, "()V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
    }
}
