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
 * @param kind evidence kind
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
        EdgeKind kind,
        DynamicReferenceKind referenceKind,
        Optional<MethodHandleReferenceKind> methodHandleKind,
        String detail) {

    /** Validates immutable evidence. */
    public DynamicCallEvidence {
        Objects.requireNonNull(targetOwner, "targetOwner");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(targetDescriptor, "targetDescriptor");
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(kind, "kind");
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
        final EdgeKind expected = switch (referenceKind) {
            case BOOTSTRAP_IMPLEMENTATION_METHOD ->
                    EdgeKind.INVOKEDYNAMIC_BOOTSTRAP;
            case BOOTSTRAP_ARGUMENT_METHOD_HANDLE ->
                    EdgeKind.INVOKEDYNAMIC_HANDLE_REFERENCE;
            case DIRECT_MODELED_HANDLE_TARGET ->
                    EdgeKind.METHOD_HANDLE_TARGET;
        };
        if (kind != expected) {
            throw new IllegalArgumentException(
                    "Dynamic reference and edge kind must agree");
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
