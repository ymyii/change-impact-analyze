package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.ipa.callgraph.CGNode;

import java.util.Objects;
import java.util.Optional;

/**
 * Reachable invokedynamic bootstrap or direct handle evidence.
 *
 * @param targetOwner referenced internal owner
 * @param targetName referenced method name
 * @param targetDescriptor referenced method descriptor
 * @param caller reachable caller Context
 * @param bytecodePc invokedynamic bytecode PC
 * @param referenceKind typed dynamic reference source
 * @param methodHandleKind class-file handle kind when applicable
 * @param detail stable detail
 */
public record DynamicCallEvidence(
        String targetOwner,
        String targetName,
        String targetDescriptor,
        CGNode caller,
        int bytecodePc,
        DynamicReferenceKind referenceKind,
        Optional<MethodHandleReferenceKind> methodHandleKind,
        String detail) {

    /** Validates immutable evidence. */
    public DynamicCallEvidence {
        Objects.requireNonNull(targetOwner, "targetOwner");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(targetDescriptor, "targetDescriptor");
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(referenceKind, "referenceKind");
        methodHandleKind = Objects.requireNonNull(
                methodHandleKind, "methodHandleKind");
        Objects.requireNonNull(detail, "detail");
        final boolean handle = referenceKind
                != DynamicReferenceKind.BOOTSTRAP_IMPLEMENTATION_METHOD;
        if (handle != methodHandleKind.isPresent()) {
            throw new IllegalArgumentException(
                    "Dynamic reference and MethodHandle kind must agree");
        }
    }

    /**
     * Stable key independent of graph node numbering and Context identity.
     *
     * @return caller binary identity, PC, typed source and target identity
     */
    public String stableKey() {
        return caller.getMethod().getReference() + "|pc=" + bytecodePc
                + "|source=" + referenceKind
                + "|handle=" + methodHandleKind.map(Enum::name)
                .orElse("NONE")
                + "|target=" + targetOwner + "#" + targetName
                + targetDescriptor;
    }
}
