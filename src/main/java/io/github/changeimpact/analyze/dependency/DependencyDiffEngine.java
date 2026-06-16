package io.github.changeimpact.analyze.dependency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;

// Wiki: wiki/features/dependency-diff-engine.md - 依赖变动对比引擎主实现
/**
 * Engine that compares baseline and
 * target resolved dependency trees and
 * produces a sorted list of dependency
 * changes.
 */
public final class DependencyDiffEngine {

    /**
     * Creates a new diff engine.
     */
    public DependencyDiffEngine() {
    }

    /**
     * Computes dependency changes between
     * baseline and target module trees.
     *
     * @param baseline baseline trees
     * @param target target trees
     * @return sorted immutable changes
     */
    public List<DependencyChange> diff(
            final List<ModuleDependencyTree>
                    baseline,
            final List<ModuleDependencyTree>
                    target) {
        Objects.requireNonNull(
                baseline, "baseline");
        Objects.requireNonNull(
                target, "target");
        if (baseline.isEmpty()
                && target.isEmpty()) {
            return Collections.emptyList();
        }
        final List<DependencyChange>
                result = new ArrayList<>();
        final Map<String, ModuleDependencyTree>
                baseMap = indexByModule(baseline);
        final Map<String, ModuleDependencyTree>
                targetMap = indexByModule(target);
        final Set<String> allKeys =
                new LinkedHashSet<>();
        allKeys.addAll(baseMap.keySet());
        allKeys.addAll(targetMap.keySet());
        for (final String modKey : allKeys) {
            final ModuleDependencyTree bTree =
                    baseMap.get(modKey);
            final ModuleDependencyTree tTree =
                    targetMap.get(modKey);
            diffModule(bTree, tTree, result);
        }
        result.sort(comparator());
        return Collections.unmodifiableList(
                result);
    }

    /**
     * Indexes module trees by module
     * diff key.
     *
     * @param trees module trees
     * @return map by module diff key
     */
    private Map<String, ModuleDependencyTree>
            indexByModule(
                    final List<ModuleDependencyTree>
                            trees) {
        final Map<String, ModuleDependencyTree>
                map = new LinkedHashMap<>();
        for (final ModuleDependencyTree t
                : trees) {
            map.put(
                    t.getModule().diffKey(),
                    t);
        }
        return map;
    }

    /**
     * Diffs a single module pair.
     *
     * @param base baseline tree or null
     * @param tgt target tree or null
     * @param out change collector
     */
    private void diffModule(
            final ModuleDependencyTree base,
            final ModuleDependencyTree tgt,
            final List<DependencyChange>
                    out) {
        if (base == null) {
            final String mod =
                    tgt.getModule().toString();
            final Map<String, DependencyNode>
                    flat = flatten(
                            tgt.getDependencies());
            for (final DependencyNode n
                    : flat.values()) {
                out.add(new DependencyChange(
                        ChangeType.ADDED,
                        null,
                        n.getArtifact(),
                        n.getScope(),
                        mod));
            }
            return;
        }
        if (tgt == null) {
            final String mod =
                    base.getModule().toString();
            final Map<String, DependencyNode>
                    flat = flatten(
                            base.getDependencies());
            for (final DependencyNode n
                    : flat.values()) {
                out.add(new DependencyChange(
                        ChangeType.REMOVED,
                        n.getArtifact(),
                        null,
                        n.getScope(),
                        mod));
            }
            return;
        }
        final String mod =
                tgt.getModule().toString();
        final Map<String, DependencyNode>
                baseFlat = flatten(
                        base.getDependencies());
        final Map<String, DependencyNode>
                tgtFlat = flatten(
                        tgt.getDependencies());
        final Set<String> allArtKeys =
                new LinkedHashSet<>();
        allArtKeys.addAll(baseFlat.keySet());
        allArtKeys.addAll(tgtFlat.keySet());
        for (final String artKey
                : allArtKeys) {
            final DependencyNode bNode =
                    baseFlat.get(artKey);
            final DependencyNode tNode =
                    tgtFlat.get(artKey);
            diffArtifact(
                    bNode, tNode, mod, out);
        }
    }

    /**
     * Diffs a single artifact across
     * baseline and target.
     *
     * @param bNode baseline node or null
     * @param tNode target node or null
     * @param mod module string
     * @param out change collector
     */
    private void diffArtifact(
            final DependencyNode bNode,
            final DependencyNode tNode,
            final String mod,
            final List<DependencyChange>
                    out) {
        if (bNode == null) {
            out.add(new DependencyChange(
                    ChangeType.ADDED,
                    null,
                    tNode.getArtifact(),
                    tNode.getScope(),
                    mod));
            return;
        }
        if (tNode == null) {
            out.add(new DependencyChange(
                    ChangeType.REMOVED,
                    bNode.getArtifact(),
                    null,
                    bNode.getScope(),
                    mod));
            return;
        }
        final String bVer =
                bNode.getArtifact()
                        .getVersion();
        final String tVer =
                tNode.getArtifact()
                        .getVersion();
        if (!bVer.equals(tVer)) {
            out.add(new DependencyChange(
                    ChangeType.VERSION_CHANGED,
                    bNode.getArtifact(),
                    tNode.getArtifact(),
                    tNode.getScope(),
                    mod));
        }
    }

    /**
     * Flattens a dependency node list
     * via DFS with first-wins dedup
     * by artifact diff key.
     *
     * @param nodes root nodes
     * @return map of diff key to node
     */
    private Map<String, DependencyNode>
            flatten(
                    final List<DependencyNode>
                            nodes) {
        final Map<String, DependencyNode>
                result = new LinkedHashMap<>();
        for (final DependencyNode node
                : nodes) {
            flattenNode(node, result);
        }
        return result;
    }

    /**
     * Recursively flattens a single
     * node into the result map.
     *
     * @param node current node
     * @param map accumulator map
     */
    private void flattenNode(
            final DependencyNode node,
            final Map<String, DependencyNode>
                    map) {
        final String key =
                node.getArtifact().diffKey();
        map.putIfAbsent(key, node);
        for (final DependencyNode child
                : node.getChildren()) {
            flattenNode(child, map);
        }
    }

    /**
     * Returns the stable sort comparator
     * for dependency changes.
     *
     * @return comparator
     */
    private Comparator<DependencyChange>
            comparator() {
        return Comparator
                .comparing(
                        DependencyChange
                                ::getModule)
                .thenComparingInt(c ->
                        c.getChangeType()
                                .ordinal())
                .thenComparing(c ->
                        artifactDiffKey(c));
    }

    /**
     * Returns the artifact diff key
     * for sorting purposes.
     *
     * @param change dependency change
     * @return artifact diff key
     */
    private String artifactDiffKey(
            final DependencyChange change) {
        if (change.getChangeType()
                == ChangeType.REMOVED) {
            return change.getOldArtifact()
                    .diffKey();
        }
        return change.getNewArtifact()
                .diffKey();
    }
}
