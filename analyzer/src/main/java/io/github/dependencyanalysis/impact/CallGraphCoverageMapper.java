package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.boundary.DependencyBodyBoundaryMetadata;
import io.github.dependencyanalysis.callgraph.protocol.ModelKind;
import io.github.dependencyanalysis.callgraph.protocol.ModelLimitation;
import io.github.dependencyanalysis.callgraph.scope.ScopeValidationWarning;

import java.util.ArrayList;
import java.util.List;

/** Single mapping boundary from Call Graph findings to business coverage. */
final class CallGraphCoverageMapper {

    /**
     * @param limitation Call Graph protocol limitation
     * @return business limitation
     */
    CoverageLimitation model(final ModelLimitation limitation) {
        return new CallGraphCoverageLimitation(reason(limitation.model()),
                limitation.stableKey(), limitation.summary());
    }

    /**
     * @param warning Call Graph scope warning
     * @return business limitation
     */
    CoverageLimitation scope(final ScopeValidationWarning warning) {
        return new CallGraphCoverageLimitation(
                ModuleAnalysisReason.INCONCLUSIVE_SCOPE_VALIDATION,
                "SCOPE_VALIDATION|" + warning.stableKey(),
                warning.summary());
    }

    /**
     * @param metadata Call Graph boundary metadata
     * @return frozen business limitations for all boundary findings
     */
    List<CoverageLimitation> boundary(
            final DependencyBodyBoundaryMetadata metadata) {
        final DependencyBoundarySnapshot snapshot = snapshot(metadata);
        final List<CoverageLimitation> result = new ArrayList<>();
        result.addAll(snapshot.dangerousTransfers());
        result.addAll(snapshot.factories());
        result.addAll(snapshot.bodyBoundaryHits());
        return List.copyOf(result);
    }

    /**
     * @param metadata Call Graph boundary metadata
     * @return frozen Impact-layer boundary projection
     */
    DependencyBoundarySnapshot snapshot(
            final DependencyBodyBoundaryMetadata metadata) {
        final List<DependencyBoundaryEvidence> transfers = metadata
                .dangerousTransfers().stream().map(value ->
                new DependencyBoundaryEvidence(value.callerMethod(),
                        value.callerOrigin(), value.bytecodePc(),
                        value.invocationKind(), value.resolvedCallee(),
                        value.calleeArtifact(), value.argumentIndex(),
                        value.changedClass(), value.typeEvidence(),
                        value.dependencyPaths(), value.context())).toList();
        final List<DependencyFactoryEvidence> factories = metadata
                .factories().stream().map(value ->
                new DependencyFactoryEvidence(value.callerMethod(),
                        value.resolvedCallee(), value.calleeArtifact(),
                        value.bytecodePc(), value.castInstruction(),
                        value.inferredType(), value.context())).toList();
        final List<DependencyBodyBoundaryHit> hits = metadata
                .bodyBoundaryHits().stream().map(value ->
                new DependencyBodyBoundaryHit(value.calleeMethod(),
                        value.calleeArtifact(), value.context())).toList();
        return new DependencyBoundarySnapshot(transfers, factories, hits,
                metadata.noOpMethods(), metadata.realExternalMethodNodes(),
                metadata.noOpMethodNodes(), metadata.factoryMethodNodes(),
                metadata.ancestorRetainedExternalTypeCount(),
                metadata.ancestorRetainedExternalMethodNodeCount(),
                metadata.prunedExternalMethodTargetCount());
    }

    private ModuleAnalysisReason reason(final ModelKind model) {
        return switch (model) {
            case INVOKEDYNAMIC -> ModuleAnalysisReason
                    .INCONCLUSIVE_INVOKEDYNAMIC_MODEL;
            case METHOD_HANDLE -> ModuleAnalysisReason
                    .INCONCLUSIVE_METHOD_HANDLE_MODEL;
            case SERVICE_LOADER -> ModuleAnalysisReason
                    .INCONCLUSIVE_SERVICE_LOADER;
            case REFLECTION -> ModuleAnalysisReason
                    .INCONCLUSIVE_REFLECTION;
        };
    }
}
