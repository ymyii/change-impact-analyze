package io.github.dependencyanalysis.callgraph.entrypoint;

import io.github.dependencyanalysis.callgraph.engine.CallGraphException;

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

    /** Retained roots in the scanner privacy fixture. */
    private static final int RETAINED_ROOT_COUNT = 4;

    /** Temporary class output. */
    @TempDir
    private Path temporary;

    @Test
    void supportsSlashGlobsRecursivePathsAndNestedClasses() {
        final EntrypointPattern exactClass = EntrypointPattern.parse(
                "com/icbc/A");
        final EntrypointPattern exact = EntrypointPattern.parse(
                "com/i?bc/Order*");
        final EntrypointPattern variablePackage = EntrypointPattern.parse(
                "com/*/A*");
        final EntrypointPattern directPackage = EntrypointPattern.parse(
                "com/icbc/*");
        final EntrypointPattern recursive = EntrypointPattern.parse(
                "com/icbc/**");
        final EntrypointPattern nested = EntrypointPattern.parse(
                "com/*/*$Handler");

        assertThat(exactClass.matchesInternalName("com/icbc/A")).isTrue();
        assertThat(exactClass.matchesInternalName("com/icbc/AB")).isFalse();
        assertThat(exact.matchesInternalName("com/icbc/OrderService"))
                .isTrue();
        assertThat(exact.matchesInternalName("Lcom/icbc/OrderService"))
                .isTrue();
        assertThat(exact.matchesInternalName("com/icbc/sub/OrderService"))
                .isFalse();
        assertThat(variablePackage.matchesInternalName("com/icbc/Api"))
                .isTrue();
        assertThat(variablePackage.matchesInternalName("com/icbc/v1/Api"))
                .isFalse();
        assertThat(directPackage.matchesInternalName("com/icbc/A"))
                .isTrue();
        assertThat(directPackage.matchesInternalName("com/icbc/sub/A"))
                .isFalse();
        assertThat(recursive.matchesInternalName(
                "com/icbc/OrderService")).isTrue();
        assertThat(recursive.matchesInternalName(
                "com/icbc/sub/Order$Handler")).isTrue();
        assertThat(nested.matchesInternalName(
                "com/icbc/Order$Handler")).isTrue();
        assertThat(nested.matchesInternalName(
                "com/icbc/sub/Order$Handler")).isFalse();
        assertThat(EntrypointPattern.parse("**")
                .matchesInternalName("DefaultClass")).isTrue();
    }

    @Test
    void combinesIncludesAndLetsExcludesWin() {
        final EntrypointSelection selection = EntrypointSelection.parse(
                List.of("com/acme/Order*", "com/acme/api/**"),
                List.of("com/acme/OrderInternal",
                        "com/acme/api/internal/**"));

        assertThat(selection.matchesInternalName("com/acme/OrderService"))
                .isTrue();
        assertThat(selection.matchesInternalName("com/acme/OrderInternal"))
                .isFalse();
        assertThat(selection.matchesInternalName(
                "com/acme/api/v1/PublicController")).isTrue();
        assertThat(selection.matchesInternalName(
                "com/acme/api/internal/HiddenController")).isFalse();
        assertThat(selection.matchesInternalName("com/acme/Other"))
                .isFalse();
    }

    @Test
    void rejectsLegacyAndInvalidRecursivePatterns() {
        assertThatThrownBy(() -> EntrypointPattern.parse("com.icbc:A"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EntrypointPattern.parse("/com/icbc/A"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EntrypointPattern.parse("com/icbc/A/"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EntrypointPattern.parse("com//icbc/A"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EntrypointPattern.parse("com\\icbc\\A"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EntrypointPattern.parse("com/**/A"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EntrypointPattern.parse("com/icbc**"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EntrypointPattern.parse("com/icbc/A**"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lightweightScannerCountsOnlySelectedDeclaredConcreteMethods()
            throws Exception {
        writeClass("com/acme/Selected", true);
        writeClass("com/acme/Other", false);
        writeInterface("com/acme/Contract");
        writeAnnotation("com/acme/Marker");
        final EntrypointSelection selection = EntrypointSelection.parse(
                List.of("com/acme/**"), List.of("com/acme/Other"));

        final EntrypointClassIndex index =
                new EntrypointClassScanner().scan(temporary, selection);
        final EntrypointSelectionMetrics metrics = index.metrics();

        assertThat(index.selectedClassNames())
                .containsExactly("com/acme/Selected");
        assertThat(metrics.selectedClassCount()).isEqualTo(1);
        assertThat(metrics.entrypointCount()).isEqualTo(2);

        final EntrypointClassIndex interfaceOnly =
                new EntrypointClassScanner().scan(
                        temporary, EntrypointSelection.parse(
                                List.of("com/acme/Contract"), List.of()));
        assertThat(interfaceOnly.selectedClassNames()).isEmpty();
        assertThat(interfaceOnly.entrypointCount()).isZero();
    }

    @Test
    void scannerRejectsDuplicateInternalNames() throws Exception {
        writeClass("com/acme/Duplicate", false);
        final byte[] duplicate = Files.readAllBytes(
                temporary.resolve("com/acme/Duplicate.class"));
        Files.write(temporary.resolve("duplicate-copy.class"), duplicate);

        assertThatThrownBy(() -> new EntrypointClassScanner().scan(
                temporary, EntrypointSelection.allProjectClasses()))
                .isInstanceOf(CallGraphException.class)
                .hasMessageContaining("Duplicate PROJECT entrypoint class");
    }

    @Test
    void scannerExcludesPrivateNestedClassesAndPrivateMethods()
            throws Exception {
        writeClass("com/acme/Methods", false);
        writeNestedClass("com/acme/Owner$PrivateNested",
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC);
        writeNestedClass("com/acme/Owner$VisibleNested",
                Opcodes.ACC_PROTECTED | Opcodes.ACC_STATIC);

        final EntrypointClassIndex index = new EntrypointClassScanner().scan(
                temporary, EntrypointSelection.allProjectClasses());

        assertThat(index.selectedClassNames()).containsExactly(
                "com/acme/Methods", "com/acme/Owner$VisibleNested");
        assertThat(index.entrypointCount()).isEqualTo(RETAINED_ROOT_COUNT);
    }

    private void writeClass(final String owner, final boolean abstractMethod)
            throws Exception {
        final ClassWriter writer = new ClassWriter(0);
        final int access = Opcodes.ACC_PUBLIC
                | (abstractMethod ? Opcodes.ACC_ABSTRACT : 0);
        writer.visit(Opcodes.V1_8, access, owner, null,
                "java/lang/Object", null);
        addMethod(writer, "one", Opcodes.ACC_PUBLIC);
        addMethod(writer, "two", Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        addMethod(writer, "privateInstance", Opcodes.ACC_PRIVATE);
        addMethod(writer, "privateStatic",
                Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC);
        addMethod(writer, "<init>", Opcodes.ACC_PRIVATE);
        if (abstractMethod) {
            writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                    "abstractMethod", "()V", null, null).visitEnd();
        }
        writer.visitEnd();
        final Path output = temporary.resolve(owner + ".class");
        Files.createDirectories(output.getParent());
        Files.write(output, writer.toByteArray());
    }

    private void writeNestedClass(
            final String owner, final int innerAccess) throws Exception {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, owner, null,
                "java/lang/Object", null);
        final int separator = owner.lastIndexOf('$');
        writer.visitInnerClass(owner, owner.substring(0, separator),
                owner.substring(separator + 1), innerAccess);
        addMethod(writer, "one", Opcodes.ACC_PUBLIC);
        addMethod(writer, "two", Opcodes.ACC_SYNTHETIC | Opcodes.ACC_BRIDGE);
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

    private void writeInterface(final String owner) throws Exception {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE
                        | Opcodes.ACC_ABSTRACT,
                owner, null, "java/lang/Object", null);
        addMethod(writer, "staticMethod",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC);
        addMethod(writer, "defaultMethod", Opcodes.ACC_PUBLIC);
        writer.visitEnd();
        final Path output = temporary.resolve(owner + ".class");
        Files.createDirectories(output.getParent());
        Files.write(output, writer.toByteArray());
    }

    private void writeAnnotation(final String owner) throws Exception {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8,
                Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE
                        | Opcodes.ACC_ABSTRACT | Opcodes.ACC_ANNOTATION,
                owner, null, "java/lang/Object",
                new String[]{"java/lang/annotation/Annotation"});
        writer.visitEnd();
        final Path output = temporary.resolve(owner + ".class");
        Files.createDirectories(output.getParent());
        Files.write(output, writer.toByteArray());
    }
}
