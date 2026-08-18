package io.github.dependencyanalysis.impact;

import com.ibm.wala.ipa.callgraph.CGNode;

import io.github.dependencyanalysis.classpath.CodeOrigin;
import io.github.dependencyanalysis.callgraph.model.MethodId;

import java.util.Objects;

/** Query node retaining the exact WALA CGNode Context identity. */
public final class WalaQueryNode implements QueryNode {

    /** Exact graph node. */
    private final CGNode walaNode;

    /** Renderable method identity. */
    private final MethodId methodId;

    /** Code origin. */
    private final CodeOrigin origin;

    /**
     * Creates a WALA query node.
     *
     * @param node exact WALA graph node
     * @param method renderable method identity
     * @param value origin
     */
    public WalaQueryNode(
            final CGNode node,
            final MethodId method,
            final CodeOrigin value) {
        walaNode = Objects.requireNonNull(node, "node");
        methodId = Objects.requireNonNull(method, "methodId");
        origin = Objects.requireNonNull(value, "origin");
    }

    /** @return exact WALA node */
    public CGNode walaNode() {
        return walaNode;
    }

    @Override
    public MethodId methodId() {
        return methodId;
    }

    @Override
    public CodeOrigin origin() {
        return origin;
    }

    @Override
    public boolean equals(final Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof WalaQueryNode
                && walaNode.equals(((WalaQueryNode) other).walaNode);
    }

    @Override
    public int hashCode() {
        return walaNode.hashCode();
    }
}
