package io.github.dependencyanalysis.bytecode;

import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.IincInsnNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.IntInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.MultiANewArrayInsnNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * ASM method tree that computes a
 * deterministic semantic bytecode hash.
 * Debug nodes and stack map frames are
 * ignored; control-flow topology and
 * bootstrap metadata are retained.
 */
final class StableHashMethodVisitor
        extends MethodNode {

    /** SHA digest name. */
    private static final String ALGORITHM =
            "SHA-256";

    /** Hex alphabet. */
    private static final char[] HEX =
            "0123456789abcdef".toCharArray();

    /** Try/catch record tag. */
    private static final int TRY_CATCH_RECORD = 100;

    /** Long constant tag. */
    private static final int VALUE_LONG = 3;

    /** Float constant tag. */
    private static final int VALUE_FLOAT = 4;

    /** Double constant tag. */
    private static final int VALUE_DOUBLE = 5;

    /** Type constant tag. */
    private static final int VALUE_TYPE = 6;

    /** Handle constant tag. */
    private static final int VALUE_HANDLE = 7;

    /** ConstantDynamic tag. */
    private static final int VALUE_DYNAMIC = 8;

    /** Byte constant tag. */
    private static final int VALUE_BYTE = 9;

    /** Short constant tag. */
    private static final int VALUE_SHORT = 10;

    /** Character constant tag. */
    private static final int VALUE_CHARACTER = 11;

    /** Boolean constant tag. */
    private static final int VALUE_BOOLEAN = 12;

    /** Object array constant tag. */
    private static final int VALUE_ARRAY = 13;

    /** Unsigned byte mask. */
    private static final int BYTE_MASK = 0xff;

    /** High nibble bit shift. */
    private static final int NIBBLE_SHIFT = 4;

    /** Low nibble mask. */
    private static final int NIBBLE_MASK = 0x0f;

    /** Digest prototype validation. */
    private final String algorithm;

    /**
     * Creates a method tree hasher.
     *
     * @throws NoSuchAlgorithmException
     *  when SHA-256 is unavailable
     */
    StableHashMethodVisitor()
            throws NoSuchAlgorithmException {
        super(Opcodes.ASM9);
        MessageDigest.getInstance(ALGORITHM);
        algorithm = ALGORITHM;
    }

    /**
     * Returns the canonical SHA-256 hash.
     *
     * @return lowercase hexadecimal hash
     */
    String getHash() {
        try {
            final MessageDigest digest =
                    MessageDigest.getInstance(
                            algorithm);
            digest.update(canonicalBytes());
            return toHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 unavailable", ex);
        }
    }

    /**
     * Builds the length-delimited model.
     *
     * @return canonical method bytes
     */
    private byte[] canonicalBytes() {
        final ByteArrayOutputStream bytes =
                new ByteArrayOutputStream();
        try (DataOutputStream out =
                     new DataOutputStream(bytes)) {
            final Map<LabelNode, Integer> labels =
                    labelIds();
            writeString(out, "METHOD-CODE-V2");
            for (AbstractInsnNode instruction
                    : instructions) {
                writeInstruction(
                        out, instruction, labels);
            }
            out.writeInt(-1);
            final java.util.List<TryCatchBlockNode> blocks =
                    tryCatchBlocks == null
                            ? Collections.emptyList()
                            : tryCatchBlocks;
            out.writeInt(blocks.size());
            for (TryCatchBlockNode block : blocks) {
                out.writeInt(TRY_CATCH_RECORD);
                writeLabel(out, labels, block.start);
                writeLabel(out, labels, block.end);
                writeLabel(out, labels, block.handler);
                writeString(out, block.type);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Unable to encode method", ex);
        }
    }

    /**
     * Assigns stable IDs to semantic labels.
     *
     * @return identity label map
     */
    private Map<LabelNode, Integer> labelIds() {
        final Set<LabelNode> referenced =
                Collections.newSetFromMap(
                        new IdentityHashMap<>());
        for (AbstractInsnNode instruction
                : instructions) {
            collectLabels(instruction, referenced);
        }
        if (tryCatchBlocks != null) {
            for (TryCatchBlockNode block
                    : tryCatchBlocks) {
                referenced.add(block.start);
                referenced.add(block.end);
                referenced.add(block.handler);
            }
        }
        final Map<LabelNode, Integer> result =
                new IdentityHashMap<>();
        int next = 0;
        for (AbstractInsnNode instruction
                : instructions) {
            if (instruction instanceof LabelNode
                    && referenced.contains(instruction)) {
                result.put((LabelNode) instruction,
                        next++);
            }
        }
        if (tryCatchBlocks != null) {
            for (TryCatchBlockNode block
                    : tryCatchBlocks) {
                next = assignMissing(
                        result, block.start, next);
                next = assignMissing(
                        result, block.end, next);
                next = assignMissing(
                        result, block.handler, next);
            }
        }
        return result;
    }

    /**
     * Collects targets referenced by one instruction.
     *
     * @param instruction instruction to inspect
     * @param labels target label set
     */
    private static void collectLabels(
            final AbstractInsnNode instruction,
            final Set<LabelNode> labels) {
        if (instruction instanceof JumpInsnNode) {
            labels.add(((JumpInsnNode) instruction).label);
        } else if (instruction
                instanceof TableSwitchInsnNode) {
            final TableSwitchInsnNode table =
                    (TableSwitchInsnNode) instruction;
            labels.add(table.dflt);
            labels.addAll(table.labels);
        } else if (instruction
                instanceof LookupSwitchInsnNode) {
            final LookupSwitchInsnNode lookup =
                    (LookupSwitchInsnNode) instruction;
            labels.add(lookup.dflt);
            labels.addAll(lookup.labels);
        }
    }

    /**
     * Adds a label missing from instruction order.
     *
     * @param labels assigned labels
     * @param label label to assign
     * @param next next available ID
     * @return next available ID
     */
    private static int assignMissing(
            final Map<LabelNode, Integer> labels,
            final LabelNode label,
            final int next) {
        if (labels.containsKey(label)) {
            return next;
        }
        labels.put(label, next);
        return next + 1;
    }

    /**
     * Writes one instruction record.
     *
     * @param out output stream
     * @param instruction instruction
     * @param labels stable label IDs
     * @throws IOException on encoding error
     */
    private static void writeInstruction(
            final DataOutputStream out,
            final AbstractInsnNode instruction,
            final Map<LabelNode, Integer> labels)
            throws IOException {
        if (instruction instanceof FrameNode
                || instruction
                instanceof LineNumberNode) {
            return;
        }
        if (instruction instanceof LabelNode) {
            final Integer id = labels.get(instruction);
            if (id != null) {
                out.writeInt(1);
                out.writeInt(id);
            }
            return;
        }
        out.writeInt(2);
        out.writeInt(instruction.getType());
        out.writeInt(instruction.getOpcode());
        if (instruction instanceof InsnNode) {
            return;
        }
        if (instruction instanceof IntInsnNode) {
            out.writeInt(
                    ((IntInsnNode) instruction).operand);
        } else if (instruction instanceof VarInsnNode) {
            out.writeInt(
                    ((VarInsnNode) instruction).var);
        } else if (instruction instanceof TypeInsnNode) {
            writeString(out,
                    ((TypeInsnNode) instruction).desc);
        } else if (instruction instanceof FieldInsnNode) {
            final FieldInsnNode field =
                    (FieldInsnNode) instruction;
            writeString(out, field.owner);
            writeString(out, field.name);
            writeString(out, field.desc);
        } else if (instruction instanceof MethodInsnNode) {
            final MethodInsnNode method =
                    (MethodInsnNode) instruction;
            writeString(out, method.owner);
            writeString(out, method.name);
            writeString(out, method.desc);
            out.writeBoolean(method.itf);
        } else if (instruction
                instanceof InvokeDynamicInsnNode) {
            final InvokeDynamicInsnNode dynamic =
                    (InvokeDynamicInsnNode) instruction;
            writeString(out, dynamic.name);
            writeString(out, dynamic.desc);
            writeValue(out, dynamic.bsm);
            out.writeInt(dynamic.bsmArgs.length);
            for (Object argument : dynamic.bsmArgs) {
                writeValue(out, argument);
            }
        } else if (instruction instanceof JumpInsnNode) {
            writeLabel(out, labels,
                    ((JumpInsnNode) instruction).label);
        } else if (instruction instanceof LdcInsnNode) {
            writeValue(out,
                    ((LdcInsnNode) instruction).cst);
        } else if (instruction instanceof IincInsnNode) {
            final IincInsnNode increment =
                    (IincInsnNode) instruction;
            out.writeInt(increment.var);
            out.writeInt(increment.incr);
        } else if (instruction
                instanceof TableSwitchInsnNode) {
            final TableSwitchInsnNode table =
                    (TableSwitchInsnNode) instruction;
            out.writeInt(table.min);
            out.writeInt(table.max);
            writeLabel(out, labels, table.dflt);
            out.writeInt(table.labels.size());
            for (LabelNode label : table.labels) {
                writeLabel(out, labels, label);
            }
        } else if (instruction
                instanceof LookupSwitchInsnNode) {
            final LookupSwitchInsnNode lookup =
                    (LookupSwitchInsnNode) instruction;
            writeLabel(out, labels, lookup.dflt);
            out.writeInt(lookup.keys.size());
            for (int index = 0;
                    index < lookup.keys.size(); index++) {
                out.writeInt(lookup.keys.get(index));
                writeLabel(out, labels,
                        lookup.labels.get(index));
            }
        } else if (instruction
                instanceof MultiANewArrayInsnNode) {
            final MultiANewArrayInsnNode array =
                    (MultiANewArrayInsnNode) instruction;
            writeString(out, array.desc);
            out.writeInt(array.dims);
        } else {
            throw new IllegalStateException(
                    "Unsupported ASM instruction: "
                            + instruction.getClass()
                            .getName());
        }
    }

    /**
     * Writes a resolved label ID.
     *
     * @param out output stream
     * @param labels stable label IDs
     * @param label label to encode
     * @throws IOException on encoding error
     */
    private static void writeLabel(
            final DataOutputStream out,
            final Map<LabelNode, Integer> labels,
            final LabelNode label)
            throws IOException {
        final Integer id = labels.get(label);
        if (id == null) {
            throw new IllegalStateException(
                    "Unregistered control-flow label");
        }
        out.writeInt(id);
    }

    /**
     * Writes one typed ASM constant.
     *
     * @param out output stream
     * @param value constant value
     * @throws IOException on encoding error
     */
    private static void writeValue(
            final DataOutputStream out,
            final Object value)
            throws IOException {
        if (value == null) {
            out.writeByte(0);
        } else if (value instanceof String) {
            out.writeByte(1);
            writeString(out, (String) value);
        } else if (value instanceof Integer) {
            out.writeByte(2);
            out.writeInt((Integer) value);
        } else if (value instanceof Long) {
            out.writeByte(VALUE_LONG);
            out.writeLong((Long) value);
        } else if (value instanceof Float) {
            out.writeByte(VALUE_FLOAT);
            out.writeInt(Float.floatToRawIntBits(
                    (Float) value));
        } else if (value instanceof Double) {
            out.writeByte(VALUE_DOUBLE);
            out.writeLong(Double.doubleToRawLongBits(
                    (Double) value));
        } else if (value instanceof Type) {
            out.writeByte(VALUE_TYPE);
            final Type type = (Type) value;
            out.writeInt(type.getSort());
            writeString(out, type.getDescriptor());
        } else if (value instanceof Handle) {
            out.writeByte(VALUE_HANDLE);
            final Handle handle = (Handle) value;
            out.writeInt(handle.getTag());
            writeString(out, handle.getOwner());
            writeString(out, handle.getName());
            writeString(out, handle.getDesc());
            out.writeBoolean(handle.isInterface());
        } else if (value instanceof ConstantDynamic) {
            out.writeByte(VALUE_DYNAMIC);
            final ConstantDynamic dynamic =
                    (ConstantDynamic) value;
            writeString(out, dynamic.getName());
            writeString(out, dynamic.getDescriptor());
            writeValue(out,
                    dynamic.getBootstrapMethod());
            final int count = dynamic
                    .getBootstrapMethodArgumentCount();
            out.writeInt(count);
            for (int index = 0;
                    index < count; index++) {
                writeValue(out, dynamic
                        .getBootstrapMethodArgument(index));
            }
        } else if (value instanceof Byte) {
            out.writeByte(VALUE_BYTE);
            out.writeByte((Byte) value);
        } else if (value instanceof Short) {
            out.writeByte(VALUE_SHORT);
            out.writeShort((Short) value);
        } else if (value instanceof Character) {
            out.writeByte(VALUE_CHARACTER);
            out.writeChar((Character) value);
        } else if (value instanceof Boolean) {
            out.writeByte(VALUE_BOOLEAN);
            out.writeBoolean((Boolean) value);
        } else if (value instanceof Object[]) {
            out.writeByte(VALUE_ARRAY);
            final Object[] values = (Object[]) value;
            out.writeInt(values.length);
            for (Object item : values) {
                writeValue(out, item);
            }
        } else {
            throw new IllegalStateException(
                    "Unsupported canonical constant: "
                            + value.getClass().getName());
        }
    }

    /**
     * Writes a nullable UTF-8 string.
     *
     * @param out output stream
     * @param value nullable value
     * @throws IOException on encoding error
     */
    private static void writeString(
            final DataOutputStream out,
            final String value)
            throws IOException {
        if (value == null) {
            out.writeInt(-1);
            return;
        }
        final byte[] bytes = value.getBytes(
                StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    /**
     * Converts bytes to lowercase hex.
     *
     * @param bytes digest bytes
     * @return hexadecimal string
     */
    private static String toHex(
            final byte[] bytes) {
        final char[] result =
                new char[bytes.length * 2];
        for (int index = 0;
                index < bytes.length; index++) {
            final int value = bytes[index] & BYTE_MASK;
            result[index * 2] =
                    HEX[value >>> NIBBLE_SHIFT];
            result[index * 2 + 1] =
                    HEX[value & NIBBLE_MASK];
        }
        return new String(result);
    }
}
