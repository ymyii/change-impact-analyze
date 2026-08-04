package io.github.dependencyanalysis.dependency;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

/**
 * Tests for
 * {@link DependencyDiffEngine}.
 */
class DependencyDiffEngineTest {

    /** The engine under test. */
    private final DependencyDiffEngine
            engine =
                    new DependencyDiffEngine();

    @Test
    void nullBaselineThrows() {
        assertThatThrownBy(
                () -> engine.diff(
                        null,
                        List.of()))
                .isInstanceOf(
                        NullPointerException
                                .class);
    }

    @Test
    void nullTargetThrows() {
        assertThatThrownBy(
                () -> engine.diff(
                        List.of(),
                        null))
                .isInstanceOf(
                        NullPointerException
                                .class);
    }

    @Test
    void emptyInputsReturnEmpty() {
        final List<DependencyChange> res =
                engine.diff(
                        List.of(),
                        List.of());
        assertThat(res).isEmpty();
    }

    @Test
    void addedDependency() {
        final ArtifactCoord mod =
                mod("g", "m");
        final ArtifactCoord dep =
                art("g", "dep", "1.0");
        final List<ModuleDependencyTree>
                base = List.of();
        final List<ModuleDependencyTree>
                tgt = List.of(tree(mod,
                        node(dep,
                                DependencyScope.COMPILE)));
        final List<DependencyChange> res =
                engine.diff(base, tgt);
        assertThat(res).hasSize(1);
        final DependencyChange ch =
                res.get(0);
        assertThat(ch.getChangeType())
                .isEqualTo(ChangeType.ADDED);
        assertThat(ch.getNewArtifact())
                .isEqualTo(dep);
        assertThat(ch.getOldArtifact())
                .isNull();
        assertThat(ch.getScope())
                .isEqualTo(DependencyScope
                        .COMPILE);
    }

    @Test
    void removedDependency() {
        final ArtifactCoord mod =
                mod("g", "m");
        final ArtifactCoord dep =
                art("g", "dep", "1.0");
        final List<ModuleDependencyTree>
                base = List.of(tree(mod,
                        node(dep,
                                DependencyScope.COMPILE)));
        final List<ModuleDependencyTree>
                tgt = List.of();
        final List<DependencyChange> res =
                engine.diff(base, tgt);
        assertThat(res).hasSize(1);
        final DependencyChange ch =
                res.get(0);
        assertThat(ch.getChangeType())
                .isEqualTo(ChangeType.REMOVED);
        assertThat(ch.getOldArtifact())
                .isEqualTo(dep);
        assertThat(ch.getNewArtifact())
                .isNull();
    }

    @Test
    void versionChanged() {
        final ArtifactCoord mod =
                mod("g", "m");
        final ArtifactCoord oldDep =
                art("g", "dep", "1.0");
        final ArtifactCoord newDep =
                art("g", "dep", "2.0");
        final List<ModuleDependencyTree>
                base = List.of(tree(mod,
                        node(oldDep,
                                DependencyScope.COMPILE)));
        final List<ModuleDependencyTree>
                tgt = List.of(tree(mod,
                        node(newDep,
                                DependencyScope.RUNTIME)));
        final List<DependencyChange> res =
                engine.diff(base, tgt);
        assertThat(res).hasSize(1);
        final DependencyChange ch =
                res.get(0);
        assertThat(ch.getChangeType())
                .isEqualTo(ChangeType
                        .VERSION_CHANGED);
        assertThat(ch.getOldArtifact())
                .isEqualTo(oldDep);
        assertThat(ch.getNewArtifact())
                .isEqualTo(newDep);
        assertThat(ch.getScope())
                .isEqualTo(DependencyScope
                        .RUNTIME);
    }

    @Test
    void noChangesReturnsEmpty() {
        final ArtifactCoord mod =
                mod("g", "m");
        final ArtifactCoord dep =
                art("g", "dep", "1.0");
        final List<ModuleDependencyTree>
                base = List.of(tree(mod,
                        node(dep,
                                DependencyScope.COMPILE)));
        final List<ModuleDependencyTree>
                tgt = List.of(tree(mod,
                        node(dep,
                                DependencyScope.COMPILE)));
        final List<DependencyChange> res =
                engine.diff(base, tgt);
        assertThat(res).isEmpty();
    }

    @Test
    void scopeOnlyChangeReturnsEmpty() {
        final ArtifactCoord mod = mod("g", "m");
        final ArtifactCoord dep = art("g", "dep", "1.0");
        final List<ModuleDependencyTree> base = List.of(tree(mod,
                node(dep, DependencyScope.COMPILE)));
        final List<ModuleDependencyTree> target = List.of(tree(mod,
                node(dep, DependencyScope.PROVIDED)));

        final List<DependencyChange> result = engine.diff(base, target);

        assertThat(result).isEmpty();
    }

    @Test
    void flattenNestedTree() {
        final ArtifactCoord mod =
                mod("g", "m");
        final ArtifactCoord parent =
                art("g", "parent", "1.0");
        final ArtifactCoord child =
                art("g", "child", "1.0");
        final DependencyNode pNode =
                nodeWithChildren(
                        parent,
                        DependencyScope.COMPILE,
                        List.of(node(child,
                                DependencyScope.RUNTIME)));
        final List<ModuleDependencyTree>
                base = List.of();
        final List<ModuleDependencyTree>
                tgt = List.of(
                        new ModuleDependencyTree(
                                mod,
                                Paths.get("/m"),
                                List.of(pNode)));
        final List<DependencyChange> res =
                engine.diff(base, tgt);
        assertThat(res).hasSize(2);
        assertThat(res)
                .extracting(
                        DependencyChange
                                ::getNewArtifact)
                .containsExactlyInAnyOrder(
                        parent, child);
    }

    @Test
    void multiModuleChanges() {
        final ArtifactCoord mod1 =
                mod("g", "m1");
        final ArtifactCoord mod2 =
                mod("g", "m2");
        final ArtifactCoord dep1 =
                art("g", "d1", "1.0");
        final ArtifactCoord dep2 =
                art("g", "d2", "1.0");
        final List<ModuleDependencyTree>
                base = List.of(
                        tree(mod1,
                                node(dep1,
                                        DependencyScope.COMPILE)));
        final List<ModuleDependencyTree>
                tgt = List.of(
                        tree(mod2,
                                node(dep2,
                                        DependencyScope.COMPILE)));
        final List<DependencyChange> res =
                engine.diff(base, tgt);
        assertThat(res).hasSize(2);
        assertThat(res)
                .extracting(
                        DependencyChange
                                ::getChangeType)
                .containsExactlyInAnyOrder(
                        ChangeType.REMOVED,
                        ChangeType.ADDED);
    }

    @Test
    void moduleAddedInTarget() {
        final ArtifactCoord mod =
                mod("g", "new-mod");
        final ArtifactCoord dep =
                art("g", "dep", "1.0");
        final List<ModuleDependencyTree>
                base = List.of();
        final List<ModuleDependencyTree>
                tgt = List.of(tree(mod,
                        node(dep,
                                DependencyScope.COMPILE)));
        final List<DependencyChange> res =
                engine.diff(base, tgt);
        assertThat(res).hasSize(1);
        assertThat(res.get(0).getModule())
                .isEqualTo(mod.toString());
        assertThat(res.get(0).getChangeType())
                .isEqualTo(ChangeType.ADDED);
    }

    @Test
    void moduleRemovedFromBaseline() {
        final ArtifactCoord mod =
                mod("g", "old-mod");
        final ArtifactCoord dep =
                art("g", "dep", "1.0");
        final List<ModuleDependencyTree>
                base = List.of(tree(mod,
                        node(dep,
                                DependencyScope.COMPILE)));
        final List<ModuleDependencyTree>
                tgt = List.of();
        final List<DependencyChange> res =
                engine.diff(base, tgt);
        assertThat(res).hasSize(1);
        assertThat(res.get(0).getModule())
                .isEqualTo(mod.toString());
        assertThat(res.get(0).getChangeType())
                .isEqualTo(ChangeType.REMOVED);
    }

    @Test
    void providedScopeMarkedAsApiRisk() {
        final ArtifactCoord mod =
                mod("g", "m");
        final ArtifactCoord dep =
                art("g", "dep", "1.0");
        final List<ModuleDependencyTree>
                base = List.of();
        final List<ModuleDependencyTree>
                tgt = List.of(tree(mod,
                        node(dep,
                                DependencyScope.PROVIDED)));
        final List<DependencyChange> res =
                engine.diff(base, tgt);
        assertThat(res).hasSize(1);
        assertThat(res.get(0)
                .isCompileTimeApiRisk())
                .isTrue();
    }

    @Test
    void stableSortByModuleTypeAndKey() {
        final ArtifactCoord modA =
                mod("a", "m");
        final ArtifactCoord modB =
                mod("b", "m");
        final ArtifactCoord depZ =
                art("z", "z", "1.0");
        final ArtifactCoord depA =
                art("a", "a", "1.0");
        final List<ModuleDependencyTree>
                base = List.of(
                        tree(modA,
                                node(depZ,
                                        DependencyScope.COMPILE)),
                        tree(modB,
                                node(depA,
                                        DependencyScope.COMPILE)));
        final List<ModuleDependencyTree>
                tgt = List.of();
        final List<DependencyChange> res =
                engine.diff(base, tgt);
        assertThat(res).hasSize(2);
        assertThat(res.get(0).getModule())
                .isEqualTo(modA.toString());
        assertThat(res.get(1).getModule())
                .isEqualTo(modB.toString());
    }

    @Test
    void resultIsUnmodifiable() {
        final List<DependencyChange> res =
                engine.diff(
                        List.of(),
                        List.of());
        assertThatThrownBy(
                () -> res.add(null))
                .isInstanceOf(
                        UnsupportedOperationException
                                .class);
    }

    @Test
    void moduleStringFormat() {
        final ArtifactCoord mod =
                new ArtifactCoord(
                        "com.example",
                        "my-module",
                        "jar",
                        "2.0");
        final ArtifactCoord dep =
                art("g", "d", "1.0");
        final List<ModuleDependencyTree>
                base = List.of();
        final List<ModuleDependencyTree>
                tgt = List.of(tree(mod,
                        node(dep,
                                DependencyScope.COMPILE)));
        final List<DependencyChange> res =
                engine.diff(base, tgt);
        assertThat(res.get(0).getModule())
                .isEqualTo(
                        "com.example:my-module"
                                + ":jar:2.0");
    }

    /**
     * Builds a module ArtifactCoord.
     *
     * @param g group id
     * @param a artifact id
     * @return module coordinate
     */
    private static ArtifactCoord mod(
            final String g,
            final String a) {
        return new ArtifactCoord(
                g, a, "jar", "1.0");
    }

    /**
     * Builds an ArtifactCoord.
     *
     * @param g group id
     * @param a artifact id
     * @param v version
     * @return artifact coordinate
     */
    private static ArtifactCoord art(
            final String g,
            final String a,
            final String v) {
        return new ArtifactCoord(
                g, a, "jar", v);
    }

    /**
     * Builds a leaf DependencyNode.
     *
     * @param art artifact
     * @param scp scope
     * @return dependency node
     */
    private static DependencyNode node(
            final ArtifactCoord art,
            final DependencyScope scp) {
        return new DependencyNode(
                art, scp, List.of());
    }

    /**
     * Builds a DependencyNode with
     * children.
     *
     * @param art artifact
     * @param scp scope
     * @param kids children
     * @return dependency node
     */
    private static DependencyNode
            nodeWithChildren(
                    final ArtifactCoord art,
                    final DependencyScope scp,
                    final List<DependencyNode>
                            kids) {
        return new DependencyNode(
                art, scp, kids);
    }

    /**
     * Builds a ModuleDependencyTree
     * with one top-level node.
     *
     * @param mod module coordinate
     * @param dep top-level dependency
     * @return module tree
     */
    private static ModuleDependencyTree
            tree(
                    final ArtifactCoord mod,
                    final DependencyNode
                            dep) {
        return new ModuleDependencyTree(
                mod,
                Paths.get("/ws"),
                new ArrayList<>(
                        List.of(dep)));
    }
}
