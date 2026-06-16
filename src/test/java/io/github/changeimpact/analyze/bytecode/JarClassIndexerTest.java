package io.github.changeimpact.analyze.bytecode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

/**
 * Tests for
 * {@link JarClassIndexer}.
 */
class JarClassIndexerTest {

    /** Test temp directory. */
    @TempDir
    private Path tempDir;

    @Test
    void indexesClassFromJar()
            throws Exception {
        final byte[] cls =
                classBytes("com/Foo");
        final Path jar = createJar(
                "test.jar", cls);
        final Map<String, ClassInfo> idx =
                JarClassIndexer.index(jar);
        assertThat(idx).containsKey("com/Foo");
        final ClassInfo info =
                idx.get("com/Foo");
        assertThat(info.getInternalName())
                .isEqualTo("com/Foo");
    }

    @Test
    void excludesModuleInfo()
            throws Exception {
        final byte[] moduleInfo =
                classBytes("module-info");
        final byte[] cls =
                classBytes("com/Foo");
        final Path jar =
                tempDir.resolve("test.jar");
        try (OutputStream fos =
                     Files.newOutputStream(
                             jar);
             ZipOutputStream zos =
                     new ZipOutputStream(
                             fos)) {
            zos.putNextEntry(
                    new ZipEntry(
                            "module-info"
                                    + ".class"));
            zos.write(moduleInfo);
            zos.closeEntry();
            zos.putNextEntry(
                    new ZipEntry(
                            "com/Foo.class"));
            zos.write(cls);
            zos.closeEntry();
        }
        final Map<String, ClassInfo> idx =
                JarClassIndexer.index(jar);
        assertThat(idx)
                .doesNotContainKey(
                        "module-info");
        assertThat(idx).containsKey("com/Foo");
    }

    @Test
    void extractsMethodsAndFields()
            throws Exception {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                "com/Foo", null,
                "java/lang/Object",
                null);
        cw.visitField(
                Opcodes.ACC_PUBLIC,
                "x", "I",
                null, null)
                .visitEnd();
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
        final Path jar = createJar(
                "test.jar",
                cw.toByteArray());
        final Map<String, ClassInfo> idx =
                JarClassIndexer.index(jar);
        final ClassInfo info =
                idx.get("com/Foo");
        assertThat(info.getMethods())
                .hasSize(1);
        assertThat(info.getFields())
                .hasSize(1);
        assertThat(info.getMethods()
                .get(0).getName())
                .isEqualTo("bar");
        assertThat(info.getFields()
                .get(0).getName())
                .isEqualTo("x");
    }

    @Test
    void abstractMethodHasNullHash()
            throws Exception {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC
                        | Opcodes.ACC_ABSTRACT,
                "com/Foo", null,
                "java/lang/Object",
                null);
        final MethodVisitor mv =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC
                                | Opcodes.ACC_ABSTRACT,
                        "bar", "()V",
                        null, null);
        mv.visitEnd();
        cw.visitEnd();
        final Path jar = createJar(
                "test.jar",
                cw.toByteArray());
        final Map<String, ClassInfo> idx =
                JarClassIndexer.index(jar);
        final ClassInfo info =
                idx.get("com/Foo");
        assertThat(info.getMethods()
                .get(0).getBodyHash())
                .isNull();
    }

    @Test
    void corruptJarThrowsException()
            throws Exception {
        final Path jar =
                tempDir.resolve("bad.jar");
        Files.write(jar,
                "not a jar".getBytes());
        assertThatThrownBy(
                () -> JarClassIndexer
                        .index(jar))
                .isInstanceOf(
                        BytecodeDiffException
                                .class);
    }

    @Test
    void emptyJarReturnsEmptyMap()
            throws Exception {
        final Path jar = createJar(
                "empty.jar");
        final Map<String, ClassInfo> idx =
                JarClassIndexer.index(jar);
        assertThat(idx).isEmpty();
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
        try (OutputStream fos =
                     Files.newOutputStream(
                             jar);
             ZipOutputStream zos =
                     new ZipOutputStream(
                             fos)) {
            for (int i = 0;
                 i < classData.length;
                 i++) {
                zos.putNextEntry(
                        new ZipEntry(
                                "c" + i
                                        + ".class"));
                zos.write(classData[i]);
                zos.closeEntry();
            }
        }
        return jar;
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
}
