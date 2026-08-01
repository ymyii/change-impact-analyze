package io.github.dependencyanalysis.bytecode;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.assertj.core.api.Assertions
        .assertThat;

/**
 * Tests for
 * {@link StableHashMethodVisitor}.
 */
class StableHashMethodVisitorTest {

    /** Expected SHA-256 hex length. */
    private static final int SHA256_HEX_LEN =
            64;

    @Test
    void producesConsistentHash()
            throws Exception {
        final byte[] cls =
                classWithReturn();
        final String hash1 =
                hashMethod(cls);
        final String hash2 =
                hashMethod(cls);
        assertThat(hash1)
                .isEqualTo(hash2);
    }

    @Test
    void differentBodiesDifferentHash()
            throws Exception {
        final byte[] cls1 =
                classWithInsn(
                        Opcodes.ICONST_0);
        final byte[] cls2 =
                classWithInsn(
                        Opcodes.ICONST_1);
        final String hash1 =
                hashMethod(cls1);
        final String hash2 =
                hashMethod(cls2);
        assertThat(hash1)
                .isNotEqualTo(hash2);
    }

    @Test
    void lineNumberDoesNotAffectHash()
            throws Exception {
        final byte[] cls1 =
                classWithLine(10);
        final byte[] cls2 =
                classWithLine(99);
        final String hash1 =
                hashMethod(cls1);
        final String hash2 =
                hashMethod(cls2);
        assertThat(hash1)
                .isEqualTo(hash2);
    }

    @Test
    void jumpTargetTopologyAffectsHash() throws Exception {
        assertThat(hashMethod(classWithJumpTarget(false)))
                .isNotEqualTo(hashMethod(classWithJumpTarget(true)));
    }

    @Test
    void bootstrapArgumentsAffectHash() throws Exception {
        assertThat(hashMethod(classWithInvokeDynamic("old")))
                .isNotEqualTo(hashMethod(classWithInvokeDynamic("new")));
    }

    @Test
    void tryCatchTypeAffectsHash() throws Exception {
        assertThat(hashMethod(classWithTryCatch("java/lang/Exception")))
                .isNotEqualTo(hashMethod(classWithTryCatch(
                        "java/lang/RuntimeException")));
    }

    @Test
    void switchTargetTopologyAffectsHash() throws Exception {
        assertThat(hashMethod(classWithSwitch(false)))
                .isNotEqualTo(hashMethod(classWithSwitch(true)));
    }

    @Test
    void hashIsHexString()
            throws Exception {
        final byte[] cls =
                classWithReturn();
        final String hash =
                hashMethod(cls);
        assertThat(hash)
                .matches("[0-9a-f]+");
    }

    @Test
    void sha256LengthIs64Chars()
            throws Exception {
        final byte[] cls =
                classWithReturn();
        final String hash =
                hashMethod(cls);
        assertThat(hash)
                .hasSize(SHA256_HEX_LEN);
    }

    /**
     * Hashes the method in a class.
     *
     * @param classBytes class bytes
     * @return hex hash
     * @throws Exception on error
     */
    private String hashMethod(
            final byte[] classBytes)
            throws Exception {
        final StableHashMethodVisitor mv =
                new StableHashMethodVisitor();
        final ClassReader reader =
                new ClassReader(
                        classBytes);
        final ClassVisitor cv =
                new ClassVisitor(
                        Opcodes.ASM9) {
                    @Override
                    public MethodVisitor
                            visitMethod(
                            final int acc,
                            final String n,
                            final String desc,
                            final String sig,
                            final String[] exc) {
                        return mv;
                    }
                };
        reader.accept(
                cv, ClassReader.SKIP_DEBUG);
        mv.visitEnd();
        return mv.getHash();
    }

    /**
     * Creates class with RETURN method.
     *
     * @return class bytes
     */
    private byte[] classWithReturn() {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                "com/Foo", null,
                "java/lang/Object",
                null);
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

    /**
     * Creates class with specific insn.
     *
     * @param opcode instruction opcode
     * @return class bytes
     */
    private byte[] classWithInsn(
            final int opcode) {
        final ClassWriter cw =
                new ClassWriter(0);
        cw.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                "com/Foo", null,
                "java/lang/Object",
                null);
        final MethodVisitor mv =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        "bar", "()I",
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
     * Creates class with line number.
     *
     * @param line line number
     * @return class bytes
     */
    private byte[] classWithLine(
            final int line) {
        final ClassWriter cw =
                new ClassWriter(
                        ClassWriter
                                .COMPUTE_FRAMES);
        cw.visit(
                Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                "com/Foo", null,
                "java/lang/Object",
                null);
        final MethodVisitor mv =
                cw.visitMethod(
                        Opcodes.ACC_PUBLIC,
                        "bar", "()V",
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

    private byte[] classWithJumpTarget(final boolean firstTarget) {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC,
                "com/Foo", null, "java/lang/Object", null);
        final MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "bar", "()I", null, null);
        final Label first = new Label();
        final Label second = new Label();
        method.visitCode();
        method.visitInsn(Opcodes.ICONST_0);
        method.visitJumpInsn(Opcodes.IFEQ,
                firstTarget ? first : second);
        method.visitLabel(first);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IRETURN);
        method.visitLabel(second);
        method.visitInsn(Opcodes.ICONST_2);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private byte[] classWithInvokeDynamic(final String argument) {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC,
                "com/Foo", null, "java/lang/Object", null);
        final MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "bar", "()Ljava/lang/String;", null, null);
        final Handle bootstrap = new Handle(Opcodes.H_INVOKESTATIC,
                "example/Bootstrap", "bootstrap",
                "(Ljava/lang/invoke/MethodHandles$Lookup;"
                        + "Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                        + "Ljava/lang/String;)Ljava/lang/invoke/CallSite;",
                false);
        method.visitCode();
        method.visitInvokeDynamicInsn("value", "()Ljava/lang/String;",
                bootstrap, argument);
        method.visitInsn(Opcodes.ARETURN);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private byte[] classWithTryCatch(final String catchType) {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC,
                "com/Foo", null, "java/lang/Object", null);
        final MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "bar", "()V", null, null);
        final Label start = new Label();
        final Label end = new Label();
        final Label handler = new Label();
        method.visitCode();
        method.visitTryCatchBlock(start, end, handler, catchType);
        method.visitLabel(start);
        method.visitInsn(Opcodes.RETURN);
        method.visitLabel(end);
        method.visitLabel(handler);
        method.visitInsn(Opcodes.ATHROW);
        method.visitMaxs(1, 0);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }

    private byte[] classWithSwitch(final boolean reversed) {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC,
                "com/Foo", null, "java/lang/Object", null);
        final MethodVisitor method = writer.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "bar", "(I)I", null, null);
        final Label first = new Label();
        final Label second = new Label();
        final Label otherwise = new Label();
        method.visitCode();
        method.visitVarInsn(Opcodes.ILOAD, 0);
        method.visitLookupSwitchInsn(otherwise,
                new int[]{1, 2}, reversed
                        ? new Label[]{second, first}
                        : new Label[]{first, second});
        method.visitLabel(first);
        method.visitInsn(Opcodes.ICONST_1);
        method.visitInsn(Opcodes.IRETURN);
        method.visitLabel(second);
        method.visitInsn(Opcodes.ICONST_2);
        method.visitInsn(Opcodes.IRETURN);
        method.visitLabel(otherwise);
        method.visitInsn(Opcodes.ICONST_0);
        method.visitInsn(Opcodes.IRETURN);
        method.visitMaxs(1, 1);
        method.visitEnd();
        writer.visitEnd();
        return writer.toByteArray();
    }
}
