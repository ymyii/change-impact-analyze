package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.AccessTransition;
import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.bytecode.JvmAccess;
import io.github.dependencyanalysis.callgraph.model.CodeOrigin;
import io.github.dependencyanalysis.callgraph.model.MethodId;
import io.github.dependencyanalysis.impact.refinement.ResultRefinementAlgorithm;
import io.github.dependencyanalysis.impact.refinement.ResultRefinementSelection;

import io.github.dependencyanalysis.callgraph.protocol.ModelKind;
import io.github.dependencyanalysis.callgraph.protocol.ModelLimitation;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.entrypoint.EntrypointSelection;
import io.github.dependencyanalysis.callgraph.jdk.JdkModelSelection;
import io.github.dependencyanalysis.callgraph.strategy.WalaReflectionOptions;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyScope;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests Module analysis coverage outcome selection. */
class PerModuleImpactPipelineTest {

    /** Odd multi-core test input. */
    private static final int THREE_PROCESSORS = 3;

    /** Eight-core test input. */
    private static final int EIGHT_PROCESSORS = 8;

    /** Half of eight processors. */
    private static final int FOUR_WORKERS = 4;

    @Test
    void analysisParallelismDefaultsToHalfAvailableProcessors() {
        assertThat(ImpactCommand.defaultAnalysisParallelism(1)).isEqualTo(1);
        assertThat(ImpactCommand.defaultAnalysisParallelism(2)).isEqualTo(1);
        assertThat(ImpactCommand.defaultAnalysisParallelism(
                THREE_PROCESSORS)).isEqualTo(1);
        assertThat(ImpactCommand.defaultAnalysisParallelism(
                EIGHT_PROCESSORS)).isEqualTo(FOUR_WORKERS);
    }

    @Test
    void resultRefinementDefaultsToSsaEquivalence() {
        final AnalysisRunConfiguration configuration =
                new AnalysisRunConfiguration(
                        EntrypointSelection.allProjectClasses(),
                        CallGraphAlgorithm.CHA);

        assertThat(configuration.resultRefinements().algorithms())
                .containsExactly(ResultRefinementAlgorithm.SSA_EQUIVALENCE);
    }

    @Test
    void resultRefinementsCanBeEnabledExplicitly() {
        final AnalysisRunConfiguration configuration =
                new AnalysisRunConfiguration(
                        EntrypointSelection.allProjectClasses(),
                        CallGraphAlgorithm.CHA,
                        CallGraphAlgorithm.defaultKObjDepth(),
                        WalaReflectionOptions.defaultOptions(),
                        DependencyAnalysisScopeMode.defaultMode(),
                        JdkModelSelection.NONE,
                        ResultRefinementSelection.of(
                                ResultRefinementAlgorithm.SSA_EQUIVALENCE));

        assertThat(configuration.resultRefinements().algorithms())
                .containsExactly(ResultRefinementAlgorithm.SSA_EQUIVALENCE);
    }

    @Test
    void codeComparisonSelectionRequiresAnImpactPath() {
        final ModuleId moduleId = new ModuleId(new ArtifactCoord(
                "example", "app", "jar", "1"), Path.of("app"));
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "example", "library", "jar", "1");
        final ArtifactCoord newArtifact = new ArtifactCoord(
                "example", "library", "jar", "2");
        final DependencyUpgradeKey upgrade = new DependencyUpgradeKey(
                moduleId, DependencyScope.COMPILE,
                oldArtifact, newArtifact);
        final BoundChangePoint access = new BoundChangePoint(upgrade,
                ChangePoint.accessNarrowed(newArtifact,
                        ChangePointKind.METHOD_ACCESS_NARROWED,
                        "example/library/Api", "call", "()V",
                        new AccessTransition(JvmAccess.PUBLIC,
                                JvmAccess.PROTECTED)));
        final ModuleAnalysisResult module = new ModuleAnalysisResult.Builder(
                unit(moduleId, List.of(access)))
                .dispositions(Map.of(access,
                        ChangePointDisposition.ACCESS_REMAINS_VALID))
                .build();

        assertThat(PerModuleImpactPipeline.codeComparisonPoints(module))
                .isEmpty();
    }

    @Test
    void codeComparisonSelectionUsesImpactAndStructuralPaths() {
        final ModuleId moduleId = new ModuleId(new ArtifactCoord(
                "example", "app", "jar", "1"), Path.of("app"));
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "example", "library", "jar", "1");
        final ArtifactCoord newArtifact = new ArtifactCoord(
                "example", "library", "jar", "2");
        final DependencyUpgradeKey upgrade = new DependencyUpgradeKey(
                moduleId, DependencyScope.COMPILE,
                oldArtifact, newArtifact);
        final BoundChangePoint point = new BoundChangePoint(upgrade,
                new ChangePoint(newArtifact,
                        ChangePointKind.METHOD_BODY_CHANGED,
                        "example/library/Api", "call", "()V",
                        "old", "new"));
        final QueryNode root = new TestQueryNode(new MethodId(
                "example/app/Controller", "handle", "()V", "app",
                "app/classes"), CodeOrigin.PROJECT);
        final ReferenceEvidence evidence = new ReferenceEvidence(
                Optional.empty(), new ReferenceTarget(
                        "example/library/Api", "call", "()V"),
                EvidenceKind.METHOD_REFERENCE,
                EvidenceMechanism.DECLARED_INVOKE,
                new EvidenceLocation("fixture", 0), "fixture");
        final ImpactPath candidate = new ImpactPath(List.of(root),
                new ChangePointTerminal(point, evidence),
                ImpactClassification.DIRECT, ImpactPathRootKind.METHOD);
        final StructuralReferencePath structural =
                new StructuralReferencePath(point,
                        new StructuralReference("example/app/Controller",
                                CodeOrigin.PROJECT,
                                StructuralReferenceKind.ANNOTATION, "",
                                "example/library/Api", "fixture"),
                        List.of(root), ImpactClassification.DIRECT);
        final ModuleAnalysisResult impactOnly =
                new ModuleAnalysisResult.Builder(
                        unit(moduleId, List.of(point)))
                        .impactPaths(List.of(candidate)).build();
        final ModuleAnalysisResult module = new ModuleAnalysisResult.Builder(
                unit(moduleId, List.of(point)))
                .impactPaths(List.of(candidate, candidate))
                .structuralPaths(List.of(structural))
                .build();

        assertThat(PerModuleImpactPipeline.codeComparisonPoints(impactOnly))
                .containsExactly(point);
        assertThat(PerModuleImpactPipeline.codeComparisonPoints(module))
                .containsExactly(point);
    }

    @Test
    void scopeValidationWarningIsInconclusive() {
        assertThat(ModuleCoverageReducer.reduce(false, List.of(
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_SCOPE_VALIDATION))))
                .isEqualTo(ModuleAnalysisReason
                        .INCONCLUSIVE_SCOPE_VALIDATION);
    }

    @Test
    void existingCoverageReasonsKeepPriority() {
        final List<CoverageLimitation> all = List.of(
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_SCOPE_VALIDATION),
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_SERVICE_LOADER),
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_METHOD_HANDLE_MODEL),
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_INVOKEDYNAMIC_MODEL));
        assertThat(ModuleCoverageReducer.reduce(true, all))
                .isEqualTo(ModuleAnalysisReason
                        .INCONCLUSIVE_BYTECODE_DIFF);
        assertThat(ModuleCoverageReducer.reduce(false, all))
                .isEqualTo(ModuleAnalysisReason
                        .INCONCLUSIVE_INVOKEDYNAMIC_MODEL);
        assertThat(ModuleCoverageReducer.reduce(false, List.of(
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_METHOD_HANDLE_MODEL),
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_SERVICE_LOADER),
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_SCOPE_VALIDATION))))
                .isEqualTo(ModuleAnalysisReason
                        .INCONCLUSIVE_METHOD_HANDLE_MODEL);
        assertThat(ModuleCoverageReducer.reduce(false, List.of(
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_SERVICE_LOADER),
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_SCOPE_VALIDATION))))
                .isEqualTo(ModuleAnalysisReason
                        .INCONCLUSIVE_SERVICE_LOADER);
        assertThat(ModuleCoverageReducer.reduce(false, List.of()))
                .isEqualTo(ModuleAnalysisReason.NONE);
    }

    @Test
    void dependencyBodyBoundaryMakesModuleInconclusive() {
        assertThat(ModuleCoverageReducer.reduce(false, List.of(
                limitation(ModuleAnalysisReason
                        .INCONCLUSIVE_DEPENDENCY_BODY_BOUNDARY))))
                .isEqualTo(ModuleAnalysisReason
                        .INCONCLUSIVE_DEPENDENCY_BODY_BOUNDARY);
    }

    @Test
    void invalidClassForNameLiteralMakesModuleInconclusive() {
        final ModelLimitation limitation = new ModelLimitation(
                ModelKind.REFLECTION,
                "CLASS_FOR_NAME_LITERAL_INVALID",
                "LExample.invalid()V|pc=1",
                "literal=<empty>");

        assertThat(ModuleCoverageReducer.reduce(false,
                List.of(new CallGraphCoverageMapper().model(limitation))))
                .isEqualTo(
                ModuleAnalysisReason.INCONCLUSIVE_REFLECTION);
    }

    private CoverageLimitation limitation(
            final ModuleAnalysisReason reason) {
        return new CoverageLimitation() {
            @Override
            public ModuleAnalysisReason reason() {
                return reason;
            }

            @Override
            public String stableKey() {
                return reason.name();
            }

            @Override
            public String summary() {
                return reason.name();
            }
        };
    }

    private ModuleAnalysisUnit unit(
            final ModuleId module,
            final List<BoundChangePoint> points) {
        return new ModuleAnalysisUnit(module, ModulePresence.BOTH,
                Path.of("classes"), List.of(), List.of(), List.of(),
                new ModuleChangeSet(points, List.of()));
    }

    /**
     * Test-only immutable query node.
     *
     * @param methodId method identity
     * @param origin code origin
     */
    private record TestQueryNode(MethodId methodId, CodeOrigin origin)
            implements QueryNode {
    }
}
