package io.github.dependencyanalysis.impact;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable complete BoundChangePoint-to-evidence index. */
public final class ChangePointEvidenceIndex {

    /** Resolution by exact bound change. */
    private final Map<BoundChangePoint, ChangePointEvidenceResolution>
            resolutions;

    /** Exact reachable graph node to unified Evidence terminals. */
    private final Map<QueryNode, List<ChangePointTerminal>>
            reverseBfsBindings;

    /** Module-wide limitations not attributable to one change. */
    private final List<CoverageLimitation> limitations;

    /** Successful caller-local constant resolutions. */
    private final int localConstantSuccessCount;

    /** Unresolved caller-local constant resolutions. */
    private final int localConstantUnresolvedCount;

    /**
     * Creates a stable complete index.
     *
     * @param values complete per-change resolutions
     * @param moduleLimitations module-wide limitations
     */
    public ChangePointEvidenceIndex(
            final List<ChangePointEvidenceResolution> values,
            final List<? extends CoverageLimitation> moduleLimitations) {
        this(values, Map.of(), moduleLimitations, 0, 0);
    }

    /**
     * Creates a stable complete index with local-resolution metrics.
     *
     * @param values complete per-change resolutions
     * @param moduleLimitations module-wide limitations
     * @param localSuccess exact resolved protocol argument count
     * @param localUnresolved exact unresolved protocol argument count
     */
    public ChangePointEvidenceIndex(
            final List<ChangePointEvidenceResolution> values,
            final List<? extends CoverageLimitation> moduleLimitations,
            final int localSuccess,
            final int localUnresolved) {
        this(values, Map.of(), moduleLimitations,
                localSuccess, localUnresolved);
    }

    /**
     * Creates a stable complete index with unified Reverse BFS bindings.
     *
     * @param values complete per-change resolutions
     * @param bindings exact reachable QueryNode bindings
     * @param moduleLimitations module-wide limitations
     * @param localSuccess exact resolved protocol argument count
     * @param localUnresolved exact unresolved protocol argument count
     */
    public ChangePointEvidenceIndex(
            final List<ChangePointEvidenceResolution> values,
            final Map<? extends QueryNode,
                    ? extends List<ChangePointTerminal>> bindings,
            final List<? extends CoverageLimitation> moduleLimitations,
            final int localSuccess,
            final int localUnresolved) {
        final Map<BoundChangePoint, ChangePointEvidenceResolution> indexed =
                new LinkedHashMap<>();
        Objects.requireNonNull(values, "values").stream()
                .sorted(java.util.Comparator.comparing(value ->
                        value.changePoint().stableKey()))
                .forEach(value -> {
                    if (indexed.put(value.changePoint(), value) != null) {
                        throw new IllegalArgumentException(
                                "Duplicate ChangePoint evidence resolution: "
                                        + value.changePoint().stableKey());
                    }
                });
        resolutions = Collections.unmodifiableMap(indexed);
        reverseBfsBindings = freezeBindings(bindings, indexed);
        limitations = new java.util.ArrayList<CoverageLimitation>(
                Objects.requireNonNull(moduleLimitations,
                        "moduleLimitations")).stream()
                .distinct().sorted(java.util.Comparator.comparing(
                        CoverageLimitation::stableKey)).toList();
        if (localSuccess < 0 || localUnresolved < 0) {
            throw new IllegalArgumentException(
                    "Local constant resolution counts must be non-negative");
        }
        localConstantSuccessCount = localSuccess;
        localConstantUnresolvedCount = localUnresolved;
    }

    private Map<QueryNode, List<ChangePointTerminal>> freezeBindings(
            final Map<? extends QueryNode,
                    ? extends List<ChangePointTerminal>> values,
            final Map<BoundChangePoint, ChangePointEvidenceResolution>
                    indexed) {
        final Map<QueryNode, List<ChangePointTerminal>> result =
                new LinkedHashMap<>();
        Objects.requireNonNull(values, "bindings").entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(this::queryNodeKey)))
                .forEach(entry -> {
                    final QueryNode node = Objects.requireNonNull(
                            entry.getKey(), "binding QueryNode");
                    final List<ChangePointTerminal> terminals =
                            new ArrayList<>(Objects.requireNonNull(
                                    entry.getValue(), "binding terminals"))
                            .stream().distinct().sorted(Comparator
                                    .comparing((ChangePointTerminal value) ->
                                            value.getChangePoint().stableKey())
                                    .thenComparing(value -> value
                                            .getImpactEvidence().stableKey()))
                            .toList();
                    for (ChangePointTerminal terminal : terminals) {
                        validateBinding(node, terminal, indexed);
                    }
                    if (!terminals.isEmpty()) {
                        result.put(node, terminals);
                    }
                });
        return Collections.unmodifiableMap(result);
    }

    private void validateBinding(
            final QueryNode node,
            final ChangePointTerminal terminal,
            final Map<BoundChangePoint, ChangePointEvidenceResolution>
                    indexed) {
        Objects.requireNonNull(node, "binding QueryNode");
        Objects.requireNonNull(terminal, "binding terminal");
        final ChangePointEvidenceResolution resolution = indexed.get(
                terminal.getChangePoint());
        if (resolution == null || !resolution.evidence().contains(
                terminal.getImpactEvidence())) {
            throw new IllegalArgumentException(
                    "Reverse BFS binding is absent from Evidence resolution: "
                            + terminal.getChangePoint().stableKey() + "|"
                            + terminal.getImpactEvidence().stableKey());
        }
    }

    private String queryNodeKey(final QueryNode node) {
        final String prefix = node.methodId().owner() + "#"
                + node.methodId().name() + node.methodId().descriptor()
                + "|" + node.origin();
        return node instanceof WalaQueryNode wala
                ? prefix + "|" + wala.walaNode().getGraphNodeId()
                : prefix + "|" + node;
    }

    /**
     * @param point exact bound change
     * @return exact resolution, failing when the index is incomplete
     */
    public ChangePointEvidenceResolution resolution(
            final BoundChangePoint point) {
        final ChangePointEvidenceResolution value = resolutions.get(
                Objects.requireNonNull(point, "point"));
        if (value == null) {
            throw new IllegalStateException(
                    "Missing ChangePoint evidence resolution: "
                            + point.stableKey());
        }
        return value;
    }

    /** @return all stable resolutions */
    public List<ChangePointEvidenceResolution> resolutions() {
        return List.copyOf(resolutions.values());
    }

    /**
     * @return exact reachable QueryNode to unified terminal bindings
     */
    public Map<QueryNode, List<ChangePointTerminal>> reverseBfsBindings() {
        return reverseBfsBindings;
    }

    /**
     * @param node exact query node
     * @return stable terminals bound to the node
     */
    public List<ChangePointTerminal> bindingsFor(final QueryNode node) {
        return reverseBfsBindings.getOrDefault(
                Objects.requireNonNull(node, "node"), List.of());
    }

    /** @return module-wide typed limitations */
    public List<CoverageLimitation> limitations() {
        return limitations;
    }

    /** @return exact resolved caller-local protocol argument count */
    public int localConstantSuccessCount() {
        return localConstantSuccessCount;
    }

    /** @return exact unresolved caller-local protocol argument count */
    public int localConstantUnresolvedCount() {
        return localConstantUnresolvedCount;
    }
}
