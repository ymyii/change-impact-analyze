package io.github.dependencyanalysis.bytecode;

import org.objectweb.asm.Opcodes;

/** Normalized JVM visibility used by binary compatibility analysis. */
public enum JvmAccess {

    /** Accessible from every runtime package. */
    PUBLIC(3),

    /** Accessible from the runtime package and qualifying subclasses. */
    PROTECTED(2),

    /** Accessible only from the same runtime package. */
    PACKAGE_PRIVATE(1),

    /** Accessible only from the declaring class under Java 8 rules. */
    PRIVATE(0);

    /** Narrowing rank. */
    private final int rank;

    JvmAccess(final int value) {
        rank = value;
    }

    /**
     * Normalizes actual class access flags.
     *
     * @param flags ASM class access flags
     * @return normalized class access
     */
    public static JvmAccess fromClassFlags(final int flags) {
        rejectMutuallyExclusive(flags);
        if ((flags & (Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED)) != 0) {
            throw new IllegalArgumentException(
                    "Class visibility must be public or package-private");
        }
        return (flags & Opcodes.ACC_PUBLIC) != 0
                ? PUBLIC : PACKAGE_PRIVATE;
    }

    /**
     * Normalizes actual member access flags.
     *
     * @param flags ASM member access flags
     * @return normalized member access
     */
    public static JvmAccess fromMemberFlags(final int flags) {
        rejectMutuallyExclusive(flags);
        if ((flags & Opcodes.ACC_PUBLIC) != 0) {
            return PUBLIC;
        }
        if ((flags & Opcodes.ACC_PROTECTED) != 0) {
            return PROTECTED;
        }
        return (flags & Opcodes.ACC_PRIVATE) != 0
                ? PRIVATE : PACKAGE_PRIVATE;
    }

    /**
     * Tests the confirmed strict-narrowing transition order.
     *
     * @param target new access
     * @return true when target is strictly narrower
     */
    public boolean narrowsTo(final JvmAccess target) {
        return target != null && rank > target.rank;
    }

    private static void rejectMutuallyExclusive(final int flags) {
        final int visibility = flags & (Opcodes.ACC_PUBLIC
                | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
        if (Integer.bitCount(visibility) > 1) {
            throw new IllegalArgumentException(
                    "JVM visibility flags are mutually exclusive");
        }
    }
}
