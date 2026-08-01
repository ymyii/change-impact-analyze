package io.github.dependencyanalysis.callgraph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests deterministic duplicate class ownership. */
class ClassOwnershipIndexTest {

    /** Temporary class roots. */
    @TempDir
    private Path temporary;

    @Test
    void identicalDuplicateUsesHigherPriorityOrigin() throws Exception {
        final Path dependency = temporary.resolve("dependency");
        final Path project = temporary.resolve("project");
        final byte[] bytes = classBytes("sample/Duplicate", 0);
        write(dependency, bytes);
        write(project, bytes);
        final ClassOwnershipIndex index = new ClassOwnershipIndex();

        index.addDirectory(dependency, CodeOrigin.DEPENDENCY);
        index.addDirectory(project, CodeOrigin.PROJECT);

        assertThat(index.ownershipOf("Lsample/Duplicate").getOrigin())
                .isEqualTo(CodeOrigin.PROJECT);
    }

    @Test
    void conflictingDuplicateFailsBeforeWala() throws Exception {
        final Path first = temporary.resolve("first");
        final Path second = temporary.resolve("second");
        write(first, classBytes("sample/Duplicate", 0));
        write(second, classBytes("sample/Duplicate",
                Opcodes.ACC_FINAL));
        final ClassOwnershipIndex index = new ClassOwnershipIndex();
        index.addDirectory(first, CodeOrigin.PROJECT);

        assertThatThrownBy(() -> index.addDirectory(
                second, CodeOrigin.DEPENDENCY))
                .isInstanceOf(CallGraphException.class)
                .hasMessageContaining("Conflicting duplicate class");
    }

    @Test
    void validatesOnlyJdkNamesThatConflictWithIndexedClasses()
            throws Exception {
        final Path project = temporary.resolve("jdk-project");
        final byte[] bytes = classBytes("sample/Duplicate", 0);
        write(project, bytes);
        final Path jar = jar("jdk.jar", Map.of(
                "sample/Duplicate.class", bytes,
                "jdk/Only.class", classBytes("jdk/Only", 0)));
        final ClassOwnershipIndex index = new ClassOwnershipIndex();
        index.addDirectory(project, CodeOrigin.PROJECT);

        index.validateJarDuplicates(jar, CodeOrigin.JDK, name -> true);

        assertThat(index.ownershipOf("sample/Duplicate").getOrigin())
                .isEqualTo(CodeOrigin.JDK);
        assertThat(index.ownershipOf("jdk/Only")).isNull();
    }

    private void write(final Path root, final byte[] bytes)
            throws Exception {
        final Path file = root.resolve("sample/Duplicate.class");
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    private byte[] classBytes(final String name, final int extraAccess) {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | extraAccess,
                name, null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private Path jar(
            final String name,
            final java.util.Map<String, byte[]> entries) throws Exception {
        final Path path = temporary.resolve(name);
        try (JarOutputStream output = new JarOutputStream(
                Files.newOutputStream(path))) {
            for (java.util.Map.Entry<String, byte[]> entry
                    : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return path;
    }
}
