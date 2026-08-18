package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.classpath.CodeOrigin;
import io.github.dependencyanalysis.callgraph.model.MethodId;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyScope;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests unified Evidence resolution and Reverse BFS binding invariants. */
class ChangePointEvidenceIndexTest {

    @Test
    void retainsOnlyBindingsBackedByCanonicalResolution() {
        final BoundChangePoint point = point();
        final ReferenceEvidence evidence = evidence("matched");
        final QueryNode node = new TestQueryNode(new MethodId(
                "app/Entry", "call", "()V", "example:app:jar:1", "app"),
                CodeOrigin.PROJECT);
        final ChangePointTerminal terminal =
                new ChangePointTerminal(point, evidence);

        final ChangePointEvidenceIndex index =
                new ChangePointEvidenceIndex(List.of(
                        new ChangePointEvidenceResolution(point,
                                EvidenceResolutionStatus.MATCHED,
                                List.of(evidence), List.of())),
                        Map.of(node, List.of(terminal)), List.of(), 0, 0);

        assertThat(index.bindingsFor(node)).containsExactly(terminal);
        assertThat(index.reverseBfsBindings()).containsOnlyKeys(node);
        assertThatThrownBy(() -> index.reverseBfsBindings()
                .put(node, List.of())).isInstanceOf(
                        UnsupportedOperationException.class);
    }

    @Test
    void rejectsBindingWhoseEvidenceIsMissingFromResolution() {
        final BoundChangePoint point = point();
        final ReferenceEvidence evidence = evidence("canonical");
        final QueryNode node = new TestQueryNode(new MethodId(
                "app/Entry", "call", "()V", "example:app:jar:1", "app"),
                CodeOrigin.PROJECT);

        assertThatThrownBy(() -> new ChangePointEvidenceIndex(List.of(
                new ChangePointEvidenceResolution(point,
                        EvidenceResolutionStatus.MATCHED,
                        List.of(evidence), List.of())),
                Map.of(node, List.of(new ChangePointTerminal(
                        point, evidence("orphan")))), List.of(), 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absent from Evidence resolution");
    }

    private BoundChangePoint point() {
        final ModuleId module = new ModuleId(new ArtifactCoord(
                "example", "app", "jar", "1"), Path.of("app"));
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "example", "dependency", "jar", "1");
        final ArtifactCoord newArtifact = new ArtifactCoord(
                "example", "dependency", "jar", "2");
        return new BoundChangePoint(new DependencyUpgradeKey(
                module, DependencyScope.COMPILE,
                oldArtifact, newArtifact), new ChangePoint(
                newArtifact, ChangePointKind.CLASS_REMOVED,
                "dep/Removed", null, null, null, null));
    }

    private ReferenceEvidence evidence(final String detail) {
        return new ReferenceEvidence(Optional.empty(),
                new ReferenceTarget("dep/Removed", "", ""),
                EvidenceKind.TYPE_REFERENCE,
                EvidenceMechanism.BYTECODE_TYPE_REFERENCE,
                new EvidenceLocation("app/Entry#call()V", 0), detail);
    }

    /**
     * Stable non-WALA QueryNode used for index validation.
     *
     * @param methodId stable method identity
     * @param origin code origin
     */
    private record TestQueryNode(
            MethodId methodId,
            CodeOrigin origin) implements QueryNode {
    }
}
