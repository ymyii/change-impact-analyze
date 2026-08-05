package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.ipa.callgraph.CGNode;

import java.util.Objects;

/**
 * Reachable invokedynamic bootstrap or direct handle evidence.
 *
 * @param targetOwner referenced internal owner
 * @param targetName referenced method name
 * @param targetDescriptor referenced method descriptor
 * @param caller reachable caller Context
 * @param bytecodePc invokedynamic bytecode PC
 * @param kind evidence kind
 * @param detail stable detail
 */
public record DynamicCallEvidence(
        String targetOwner,
        String targetName,
        String targetDescriptor,
        CGNode caller,
        int bytecodePc,
        EdgeKind kind,
        String detail) {

    /** Validates immutable evidence. */
    public DynamicCallEvidence {
        Objects.requireNonNull(targetOwner, "targetOwner");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(targetDescriptor, "targetDescriptor");
        Objects.requireNonNull(caller, "caller");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(detail, "detail");
    }
}
