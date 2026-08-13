package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePointKind;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves removed-class seeds only from frozen build evidence. */
final class ClassReferenceSeedResolver
        implements ChangePointSeedResolver {

    @Override
    public boolean supports(final ChangePointKind kind) {
        return kind == ChangePointKind.CLASS_REMOVED;
    }

    @Override
    public ChangePointSeedResolution resolve(
            final ChangePointSeedRequest request) {
        final Set<ImpactSeed> result = new LinkedHashSet<>();
        for (ReferenceEvidence evidence : request.evidence().evidence()) {
            evidence.anchor().filter(MethodEvidenceAnchor.class::isInstance)
                    .map(MethodEvidenceAnchor.class::cast)
                    .ifPresent(anchor -> result.add(
                    new ImpactSeed(new WalaQueryNode(
                            anchor.node(), anchor.methodId(),
                            anchor.origin()), evidence)));
        }
        final List<ImpactSeed> seeds = result.stream()
                .sorted(Comparator.comparing(seed ->
                        seed.evidence().stableKey())).toList();
        return new ChangePointSeedResolution(seeds, seeds.isEmpty()
                ? ReferenceObservation.NONE : ReferenceObservation.IMPACTING,
                List.of(), List.of());
    }
}
