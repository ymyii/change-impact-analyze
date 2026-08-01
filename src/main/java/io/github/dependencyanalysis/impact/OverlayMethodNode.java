package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.CodeOrigin;
import io.github.dependencyanalysis.callgraph.MethodId;

import java.util.Objects;

/** Method node introduced only by a conservative overlay. */
public final class OverlayMethodNode implements QueryNode {

    /** Method identity. */
    private final MethodId methodId;

    /** Original provider origin. */
    private final CodeOrigin origin;

    /**
     * Creates an overlay node.
     *
     * @param method method identity
     * @param value origin
     */
    public OverlayMethodNode(
            final MethodId method, final CodeOrigin value) {
        methodId = Objects.requireNonNull(method, "methodId");
        origin = Objects.requireNonNull(value, "origin");
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
        if (!(other instanceof OverlayMethodNode)) {
            return false;
        }
        final OverlayMethodNode that = (OverlayMethodNode) other;
        return methodId.equals(that.methodId) && origin == that.origin;
    }

    @Override
    public int hashCode() {
        return Objects.hash(methodId, origin);
    }
}
