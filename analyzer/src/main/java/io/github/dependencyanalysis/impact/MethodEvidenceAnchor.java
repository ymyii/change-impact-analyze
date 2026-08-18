package io.github.dependencyanalysis.impact;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.SSAInstruction;

import io.github.dependencyanalysis.classpath.CodeOrigin;
import io.github.dependencyanalysis.callgraph.model.MethodId;

import java.util.Objects;
import java.util.Optional;

/**
 * Exact reachable caller anchor retained for reverse graph traversal.
 *
 * @param node exact WALA node and Context
 * @param methodId stable method identity
 * @param origin code origin
 * @param instruction exact reference instruction when applicable
 */
public record MethodEvidenceAnchor(
        CGNode node,
        MethodId methodId,
        CodeOrigin origin,
        Optional<SSAInstruction> instruction) implements EvidenceAnchor {

    /** Validates immutable anchor fields. */
    public MethodEvidenceAnchor {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(methodId, "methodId");
        Objects.requireNonNull(origin, "origin");
        instruction = Objects.requireNonNull(instruction, "instruction");
    }

    /** @return stable caller identity independent of graph node numbering */
    public String stableKey() {
        return methodId.owner() + "#" + methodId.name()
                + methodId.descriptor() + "|context=" + node.getContext();
    }
}
