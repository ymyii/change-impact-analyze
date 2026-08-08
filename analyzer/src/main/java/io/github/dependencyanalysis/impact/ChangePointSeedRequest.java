package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.callgraph.DynamicCallEvidenceIndex;
import io.github.dependencyanalysis.callgraph.ModuleCallGraphSession;

import java.util.Objects;

/**
 * Read-only input for one ChangePoint seed resolver.
 *
 * @param moduleId current Module
 * @param point ChangePoint to resolve
 * @param session target Call Graph session
 * @param dynamicEvidence fixed-point dynamic evidence
 * @param reachable precollected reachable caller/IR facts
 */
record ChangePointSeedRequest(
        ModuleId moduleId,
        ChangePoint point,
        ModuleCallGraphSession session,
        DynamicCallEvidenceIndex dynamicEvidence,
        ReachableReferenceCollector reachable) {

    ChangePointSeedRequest {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(dynamicEvidence, "dynamicEvidence");
        Objects.requireNonNull(reachable, "reachable");
    }
}
