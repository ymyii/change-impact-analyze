package io.github.dependencyanalysis.bytecode;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Method visitor that computes a
 * stable SHA-256 hash of method
 * bytecode instructions. Ignores
 * debug information such as line
 * numbers and local variable tables.
 * Package-private.
 */
final class StableHashMethodVisitor
        extends MethodVisitor {

    /** Hex chars for encoding. */
    private static final String HEX =
            "0123456789abcdef";

    /** Digest algorithm name. */
    private static final String ALGO =
            "SHA-256";

    /** Byte shift for bit 24. */
    private static final int SHIFT_24 =
            24;

    /** Byte shift for bit 16. */
    private static final int SHIFT_16 =
            16;

    /** Byte shift for bit 8. */
    private static final int SHIFT_8 =
            8;

    /** Hex digit shift. */
    private static final int HEX_SHIFT =
            4;

    /** Hex mask for low nibble. */
    private static final int HEX_MASK =
            0x0F;

    /** Message digest instance. */
    private final MessageDigest digest;

    /**
     * Creates a new stable hash
     * method visitor.
     *
     * @throws NoSuchAlgorithmException
     *  if SHA-256 is unavailable
     */
    StableHashMethodVisitor()
            throws NoSuchAlgorithmException {
        super(Opcodes.ASM9);
        this.digest =
                MessageDigest.getInstance(
                        ALGO);
    }

    /**
     * Returns the computed hex hash.
     *
     * @return hex-encoded hash string
     */
    String getHash() {
        final byte[] bytes =
                digest.digest();
        return toHex(bytes);
    }

    @Override
    public void visitInsn(
            final int opcode) {
        feedInt(opcode);
    }

    @Override
    public void visitIntInsn(
            final int opcode,
            final int operand) {
        feedInt(opcode);
        feedInt(operand);
    }

    @Override
    public void visitVarInsn(
            final int opcode,
            final int varIndex) {
        feedInt(opcode);
        feedInt(varIndex);
    }

    @Override
    public void visitTypeInsn(
            final int opcode,
            final String type) {
        feedInt(opcode);
        feedStr(type);
    }

    @Override
    public void visitFieldInsn(
            final int opcode,
            final String owner,
            final String name,
            final String desc) {
        feedInt(opcode);
        feedStr(owner);
        feedStr(name);
        feedStr(desc);
    }

    @Override
    public void visitMethodInsn(
            final int opcode,
            final String owner,
            final String name,
            final String desc,
            final boolean itf) {
        feedInt(opcode);
        feedStr(owner);
        feedStr(name);
        feedStr(desc);
        feedInt(itf ? 1 : 0);
    }

    @Override
    public void visitJumpInsn(
            final int opcode,
            final Label label) {
        feedInt(opcode);
    }

    @Override
    public void visitLdcInsn(
            final Object value) {
        feedStr(String.valueOf(value));
    }

    @Override
    public void visitIincInsn(
            final int varIndex,
            final int increment) {
        feedInt(Opcodes.IINC);
        feedInt(varIndex);
        feedInt(increment);
    }

    @Override
    public void visitTableSwitchInsn(
            final int min,
            final int max,
            final Label dflt,
            final Label... labels) {
        feedInt(Opcodes.TABLESWITCH);
        feedInt(min);
        feedInt(max);
    }

    @Override
    public void visitLookupSwitchInsn(
            final Label dflt,
            final int[] keys,
            final Label[] labels) {
        feedInt(Opcodes.LOOKUPSWITCH);
        if (keys != null) {
            for (final int k : keys) {
                feedInt(k);
            }
        }
    }

    @Override
    public void visitMultiANewArrayInsn(
            final String desc,
            final int dims) {
        feedStr(desc);
        feedInt(dims);
    }

    @Override
    public void visitInvokeDynamicInsn(
            final String name,
            final String desc,
            final Handle bsm,
            final Object... bsmArgs) {
        feedStr(name);
        feedStr(desc);
        feedStr(bsm.toString());
    }

    @Override
    public void visitTryCatchBlock(
            final Label start,
            final Label end,
            final Label handler,
            final String type) {
        feedStr(type);
    }

    @Override
    public void visitLabel(
            final Label label) {
        // labels are structural but
        // not debug info; skip to
        // keep hash stable
    }

    @Override
    public void visitFrame(
            final int type,
            final int nLocal,
            final Object[] local,
            final int nStack,
            final Object[] stack) {
        // stack map frames are
        // compiler metadata; skip
    }

    @Override
    public void visitEnd() {
        // no-op; hash finalized on
        // getHash() call
    }

    /**
     * Feeds an integer into digest.
     *
     * @param val integer value
     */
    private void feedInt(final int val) {
        digest.update(
                (byte) (val >> SHIFT_24));
        digest.update(
                (byte) (val >> SHIFT_16));
        digest.update(
                (byte) (val >> SHIFT_8));
        digest.update((byte) val);
    }

    /**
     * Feeds a string into digest.
     *
     * @param str string value
     */
    private void feedStr(final String str) {
        if (str != null) {
            digest.update(
                    str.getBytes(
                            java.nio.charset
                                    .StandardCharsets
                                    .UTF_8));
        }
    }

    /**
     * Converts bytes to hex string.
     *
     * @param bytes byte array
     * @return hex string
     */
    private static String toHex(
            final byte[] bytes) {
        final StringBuilder sb =
                new StringBuilder(
                        bytes.length * 2);
        for (final byte b : bytes) {
            sb.append(HEX.charAt(
                    (b >> HEX_SHIFT)
                            & HEX_MASK));
            sb.append(HEX.charAt(
                    b & HEX_MASK));
        }
        return sb.toString();
    }
}
