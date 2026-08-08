package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.ModuleCallGraphSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves Structural Reference access before path materialization. */
final class StructuralReferencePreparation {

    Result prepare(
            final List<StructuralReferenceMatch> matches,
            final ModuleCallGraphSession session) {
        final List<StructuralReferenceMatch> impacting = new ArrayList<>();
        final Map<BoundChangePoint, List<ImpactEvidence>> observations =
                new LinkedHashMap<>();
        final Set<QueryLimitation> limitations = new LinkedHashSet<>();
        final StructuralAccessReferenceResolver access =
                new StructuralAccessReferenceResolver();
        for (StructuralReferenceMatch match : matches) {
            final StructuralAccessReferenceResolver.Resolution resolution =
                    access.resolve(match, session);
            limitations.addAll(resolution.limitations());
            final AccessReferenceEvidence evidence =
                    resolution.evidence().orElse(null);
            if (evidence != null) {
                observations.computeIfAbsent(match.changePoint(),
                        ignored -> new ArrayList<>()).add(evidence);
                if (evidence.decision() == AccessDecision.ACCESSIBLE) {
                    continue;
                }
            }
            impacting.add(match);
        }
        final Map<BoundChangePoint, List<ImpactEvidence>> stable =
                new LinkedHashMap<>();
        observations.forEach((point, values) -> stable.put(point,
                values.stream().distinct().sorted(Comparator.comparing(
                        ImpactEvidence::stableKey)).toList()));
        return new Result(impacting, stable,
                limitations.stream().sorted().toList());
    }

    /**
     * @param references references requiring path materialization
     * @param observations typed access observations
     * @param limitations target CHA resolution limitations
     */
    record Result(
            List<StructuralReferenceMatch> references,
            Map<BoundChangePoint, List<ImpactEvidence>> observations,
            List<QueryLimitation> limitations) {

        Result {
            references = List.copyOf(references);
            observations = Map.copyOf(observations);
            limitations = List.copyOf(limitations);
        }
    }
}
