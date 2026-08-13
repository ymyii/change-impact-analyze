package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.MethodId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable single impact path
 * from an affected root method
 * to a change point.
 */
public final class ImpactPath {

    /** Exact ordered query nodes. */
    private final List<QueryNode> nodes;

    /** Synthetic ChangePoint terminal. */
    private final ChangePointTerminal terminal;

    /** Directness classification. */
    private final ImpactClassification classification;

    /**
     * Creates a Context-preserving path materialized directly from WALA.
     *
     * @param orderedNodes path nodes from PROJECT method to seed
     * @param changeTerminal synthetic ChangePoint terminal
     * @param impactClassification direct or transitive classification
     */
    public ImpactPath(
            final List<QueryNode> orderedNodes,
            final ChangePointTerminal changeTerminal,
            final ImpactClassification impactClassification) {
        if (orderedNodes.isEmpty()) {
            throw new IllegalArgumentException("orderedNodes is empty");
        }
        nodes = Collections.unmodifiableList(
                new ArrayList<>(orderedNodes));
        terminal = Objects.requireNonNull(
                changeTerminal, "changeTerminal");
        classification = Objects.requireNonNull(
                impactClassification, "impactClassification");
    }

    /**
     * Returns the affected root
     * method.
     *
     * @return affected method id
     */
    public MethodId
            getAffectedMethod() {
        return nodes.get(0).methodId();
    }

    /** @return exact ordered query nodes */
    public List<QueryNode> getNodes() {
        return nodes;
    }

    /** @return synthetic terminal */
    public ChangePointTerminal getTerminal() {
        return terminal;
    }

    /** @return directness classification */
    public ImpactClassification getClassification() {
        return classification;
    }

    @Override
    public boolean equals(
            final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ImpactPath)) {
            return false;
        }
        final ImpactPath that =
                (ImpactPath) o;
        return nodes.equals(that.nodes)
                && terminal.equals(that.terminal)
                && classification == that.classification;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes, terminal, classification);
    }

    @Override
    public String toString() {
        final StringBuilder sb =
                new StringBuilder();
        sb.append("ImpactPath{")
                .append("affected=")
                .append(getAffectedMethod())
                .append(", cp=")
                .append(terminal.getChangePoint())
                .append(", hops=")
                .append(nodes.size() - 1)
                .append('}');
        return sb.toString();
    }
}
