package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.callgraph.model.CodeOrigin;
import io.github.dependencyanalysis.callgraph.model.MethodId;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyScope;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests node-only paths and complete ChangePoint terminal evidence. */
class ImpactPathTest {

    /** Terminal usage bytecode PC. */
    private static final int TERMINAL_PC = 42;

    @Test
    void retainsTerminalUsageEvidenceWithoutMaterializedEdges() {
        final List<QueryNode> nodes = new ArrayList<>(List.of(
                new TestQueryNode(new MethodId("sample/Controller", "call",
                        "()V", "sample", "sample/classes"),
                        CodeOrigin.PROJECT)));
        final BoundChangePoint point = point();
        final ReferenceEvidence evidence = new ReferenceEvidence(
                Optional.empty(), new ReferenceTarget(
                        "sample/Api", "removed", "()V"),
                EvidenceKind.METHOD_REFERENCE,
                EvidenceMechanism.DECLARED_INVOKE,
                new EvidenceLocation(
                        "sample/Controller#call()V", TERMINAL_PC),
                "declared invoke sample/Api.removed()V");
        final ChangePointTerminal terminal = new ChangePointTerminal(
                point, evidence);
        final ImpactPath path = new ImpactPath(nodes, terminal,
                ImpactClassification.TRANSITIVE,
                ImpactPathRootKind.METHOD);

        nodes.clear();

        assertThat(path.getNodes()).hasSize(1);
        assertThat(path.getTerminal().getChangePoint()).isEqualTo(point);
        assertThat(path.getTerminal().getImpactEvidence()).isSameAs(evidence);
        assertThat(path.getTerminal().getImpactEvidence().target().stableKey())
                .isEqualTo("sample/Api#removed()V");
        assertThat(path.getTerminal().getImpactEvidence().location())
                .isEqualTo(new EvidenceLocation(
                        "sample/Controller#call()V", TERMINAL_PC));
        assertThat(path.getTerminal().getEvidenceMechanism())
                .isEqualTo(EvidenceMechanism.DECLARED_INVOKE);
        assertThat(path.getRootMethod())
                .isEqualTo(path.getNodes().get(0).methodId());
        assertThat(path.getAffectedMethods())
                .containsExactly(path.getNodes().get(0).methodId());
        assertThat(path.getRootKind()).isEqualTo(ImpactPathRootKind.METHOD);
        assertThatThrownBy(() -> path.getNodes().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(new ImpactPath(List.copyOf(path.getNodes()), terminal,
                ImpactClassification.TRANSITIVE,
                ImpactPathRootKind.METHOD)).isEqualTo(path)
                .hasSameHashCodeAs(path);
    }

    @Test
    void rejectsEmptyOrdinaryPathButAllowsDirectStructuralPath() {
        final ChangePointTerminal terminal = new ChangePointTerminal(point(),
                new ReferenceEvidence(Optional.empty(),
                        new ReferenceTarget("sample/Api", "removed", "()V"),
                        EvidenceKind.METHOD_REFERENCE,
                        EvidenceMechanism.METHOD_DECLARATION,
                        new EvidenceLocation("sample/Api#removed()V", -1),
                        "declaration"));

        assertThatThrownBy(() -> new ImpactPath(List.of(), terminal,
                ImpactClassification.DIRECT,
                ImpactPathRootKind.METHOD))
                .isInstanceOf(IllegalArgumentException.class);

        final StructuralReference reference = new StructuralReference(
                "sample/Controller", CodeOrigin.PROJECT,
                StructuralReferenceKind.SUPERCLASS, "", "sample/Api",
                "super sample/Api");
        assertThat(new StructuralReferencePath(point(), reference, List.of(),
                ImpactClassification.DIRECT).isDirectClassReference())
                .isTrue();
    }

    private BoundChangePoint point() {
        final ArtifactCoord module = new ArtifactCoord(
                "sample", "app", "jar", "1");
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "sample", "library", "jar", "1");
        final ArtifactCoord newArtifact = new ArtifactCoord(
                "sample", "library", "jar", "2");
        return new BoundChangePoint(new DependencyUpgradeKey(
                new ModuleId(module, Path.of("app")), DependencyScope.COMPILE,
                oldArtifact, newArtifact), new ChangePoint(newArtifact,
                ChangePointKind.METHOD_REMOVED, "sample/Api", "removed",
                "()V", null, null));
    }

    /**
     * Minimal query node.
     *
     * @param methodId method identity
     * @param origin code origin
     */
    private record TestQueryNode(MethodId methodId, CodeOrigin origin)
            implements QueryNode {
    }
}
