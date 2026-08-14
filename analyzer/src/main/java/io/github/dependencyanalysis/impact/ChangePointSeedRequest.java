package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphSession;

import java.util.Objects;

/**
 * Read-only input for one ChangePoint seed resolver.
 *
 * @param moduleId current Module
 * @param point ChangePoint to resolve
 * @param session target Call Graph session
 * @param evidence frozen graph-build evidence for this ChangePoint
 */
record ChangePointSeedRequest(
        ModuleId moduleId,
        ChangePoint point,
        ModuleCallGraphSession session,
        ChangePointEvidenceResolution evidence) {

    ChangePointSeedRequest {
        Objects.requireNonNull(moduleId, "moduleId");
        Objects.requireNonNull(point, "point");
        Objects.requireNonNull(session, "session");
        Objects.requireNonNull(evidence, "evidence");
    }
}
