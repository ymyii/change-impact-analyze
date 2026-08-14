package io.github.dependencyanalysis.callgraph.strategy.kobj;

import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

/** Minimal Java 8 Primordial bytecode for focused fixed-point tests. */
final class MinimalJdk8RuntimeFixture {

    /** Java 8 runtime major. */
    private static final int JAVA_MAJOR = 8;

    private MinimalJdk8RuntimeFixture() {
    }

    static JavaRuntimeDescriptor create(final Path directory)
            throws IOException {
        Files.createDirectories(directory);
        final Path jar = directory.resolve("minimal-jdk8.jar");
        try (JarOutputStream output = new JarOutputStream(
                Files.newOutputStream(jar))) {
            write(output, objectClass());
            write(output, emptyClass("java/lang/Class", "java/lang/Object"));
            write(output, emptyClass("java/lang/String", "java/lang/Object"));
            write(output, emptyClass(
                    "java/lang/Throwable", "java/lang/Object"));
            write(output, emptyInterface("java/lang/Cloneable"));
            write(output, emptyInterface("java/io/Serializable"));
            write(output, runnableClass());
            write(output, threadClass());
            write(output, voidClass());
            write(output, methodHandleClass());
            write(output, methodHandlesClass());
            write(output, lookupClass());
            write(output, methodTypeClass());
            write(output, emptyClass(
                    "java/lang/invoke/CallSite", "java/lang/Object"));
        }
        return new JavaRuntimeDescriptor(directory, directory,
                "1.8.0-test", JAVA_MAJOR, List.of(jar), List.of());
    }

    private static byte[] objectClass() {
        final ClassWriter writer = header(
                "java/lang/Object", null, Opcodes.ACC_PUBLIC);
        final MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        method.visitCode();
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] emptyClass(
            final String name, final String parent) {
        final ClassWriter writer = header(name, parent, Opcodes.ACC_PUBLIC);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] emptyInterface(final String name) {
        final ClassWriter writer = header(name, "java/lang/Object",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE
                        | Opcodes.ACC_ABSTRACT);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] runnableClass() {
        final ClassWriter writer = header(
                "java/lang/Runnable", "java/lang/Object",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_INTERFACE
                        | Opcodes.ACC_ABSTRACT);
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
                "run", "()V", null, null).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] threadClass() {
        final ClassWriter writer = new ClassWriter(
                ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC,
                "java/lang/Thread", null, "java/lang/Object",
                new String[]{"java/lang/Runnable"});
        writer.visitField(Opcodes.ACC_PRIVATE, "target",
                "Ljava/lang/Runnable;", null, null).visitEnd();
        MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>",
                "(Ljava/lang/Runnable;)V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false);
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitVarInsn(Opcodes.ALOAD, 1);
        method.visitFieldInsn(Opcodes.PUTFIELD, "java/lang/Thread",
                "target", "Ljava/lang/Runnable;");
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitFieldInsn(Opcodes.GETFIELD, "java/lang/Thread",
                "target", "Ljava/lang/Runnable;");
        method.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                "java/lang/Runnable", "run", "()V", true);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(0, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] voidClass() {
        final ClassWriter writer = header(
                "java/lang/Void", "java/lang/Object",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL);
        writer.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
                | Opcodes.ACC_FINAL, "TYPE", "Ljava/lang/Class;",
                null, null).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] methodHandleClass() {
        final ClassWriter writer = header(
                "java/lang/invoke/MethodHandle", "java/lang/Object",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT);
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL
                        | Opcodes.ACC_NATIVE | Opcodes.ACC_VARARGS,
                "invokeExact", "([Ljava/lang/Object;)Ljava/lang/Object;",
                null, new String[]{"java/lang/Throwable"}).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] methodHandlesClass() {
        final ClassWriter writer = header(
                "java/lang/invoke/MethodHandles", "java/lang/Object",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL);
        final MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "lookup", "()Ljava/lang/invoke/MethodHandles$Lookup;",
                null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW,
                "java/lang/invoke/MethodHandles$Lookup");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/invoke/MethodHandles$Lookup", "<init>",
                "()V", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(2, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] lookupClass() {
        final ClassWriter writer = header(
                "java/lang/invoke/MethodHandles$Lookup", "java/lang/Object",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL);
        addDefaultConstructor(writer);
        writer.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_NATIVE,
                "findStatic", "(Ljava/lang/Class;Ljava/lang/String;"
                        + "Ljava/lang/invoke/MethodType;)"
                        + "Ljava/lang/invoke/MethodHandle;",
                null, new String[]{"java/lang/Throwable"}).visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static byte[] methodTypeClass() {
        final ClassWriter writer = header(
                "java/lang/invoke/MethodType", "java/lang/Object",
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL);
        addDefaultConstructor(writer);
        final MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC
                        | Opcodes.ACC_VARARGS,
                "methodType", "(Ljava/lang/Class;[Ljava/lang/Class;)"
                        + "Ljava/lang/invoke/MethodType;",
                null, null);
        method.visitCode();
        method.visitTypeInsn(Opcodes.NEW,
                "java/lang/invoke/MethodType");
        method.visitInsn(Opcodes.DUP);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/invoke/MethodType", "<init>", "()V", false);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(2, 2);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private static void addDefaultConstructor(final ClassWriter writer) {
        final MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        method.visitCode();
        method.visitVarInsn(Opcodes.ALOAD, 0);
        method.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false);
        method.visitInsn(Opcodes.RETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
    }

    private static ClassWriter header(
            final String name,
            final String parent,
            final int access) {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, access, name, null, parent, null);
        return writer;
    }

    private static void write(
            final JarOutputStream output,
            final byte[] bytes) throws IOException {
        final org.objectweb.asm.ClassReader reader =
                new org.objectweb.asm.ClassReader(bytes);
        output.putNextEntry(new JarEntry(reader.getClassName() + ".class"));
        output.write(bytes);
        output.closeEntry();
    }
}
