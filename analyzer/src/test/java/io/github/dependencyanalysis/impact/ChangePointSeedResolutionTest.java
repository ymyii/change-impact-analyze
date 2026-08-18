package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.AccessTransition;
import io.github.dependencyanalysis.bytecode.JvmAccess;
import io.github.dependencyanalysis.classpath.CodeOrigin;
import io.github.dependencyanalysis.callgraph.model.MethodId;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChangePointSeedResolutionTest {

    /** Exact test seed. */
    private final ImpactSeed seed = new ImpactSeed(
            new TestQueryNode(new MethodId(
                    "example/Caller", "run", "()V", "app", "app"),
                    CodeOrigin.PROJECT),
            new ReferenceEvidence(Optional.empty(),
                    new ReferenceTarget("example/Api", "call", "()V"),
                    EvidenceKind.METHOD_REFERENCE,
                    EvidenceMechanism.DECLARED_INVOKE,
                    new EvidenceLocation("example/Caller#run()V", 0),
                    "reference"));

    /** Accessible typed observation. */
    private final AccessReferenceEvidence accessible =
            new AccessReferenceEvidence(
                    new AccessTransition(
                            JvmAccess.PUBLIC, JvmAccess.PROTECTED),
                    AccessDecision.ACCESSIBLE,
                    AccessDecisionReason.SAME_RUNTIME_PACKAGE,
                    "example/Caller#run()V", "example/Api",
                    "example/Api", "NOT_APPLICABLE",
                    "example/Api#call()V");

    @Test
    void noneRejectsSeedsAndEvidence() {
        assertThatThrownBy(() -> new ChangePointSeedResolution(
                List.of(seed), ReferenceObservation.NONE,
                List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChangePointSeedResolution(
                List.of(), ReferenceObservation.NONE,
                List.of(new TextImpactEvidence("reference")), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void accessibleOnlyRejectsSeedsEmptyEvidenceAndNonAccessEvidence() {
        assertThatThrownBy(() -> new ChangePointSeedResolution(
                List.of(seed), ReferenceObservation.ACCESSIBLE_ONLY,
                List.of(accessible), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChangePointSeedResolution(
                List.of(), ReferenceObservation.ACCESSIBLE_ONLY,
                List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ChangePointSeedResolution(
                List.of(), ReferenceObservation.ACCESSIBLE_ONLY,
                List.of(new TextImpactEvidence("reference")), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void impactingRequiresSeed() {
        assertThatThrownBy(() -> new ChangePointSeedResolution(
                List.of(), ReferenceObservation.IMPACTING,
                List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * @param methodId stable method identity
     * @param origin code origin
     */
    private record TestQueryNode(MethodId methodId, CodeOrigin origin)
            implements QueryNode {
    }
}
