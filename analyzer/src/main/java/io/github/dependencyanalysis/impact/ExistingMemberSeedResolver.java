package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePointKind;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Resolves method and field seeds only from frozen build evidence. */
final class ExistingMemberSeedResolver
        implements ChangePointSeedResolver {

    @Override
    public boolean supports(final ChangePointKind kind) {
        return kind == ChangePointKind.METHOD_BODY_CHANGED
                || kind == ChangePointKind.METHOD_REMOVED
                || kind == ChangePointKind.METHOD_DESCRIPTOR_CHANGED
                || kind == ChangePointKind.FIELD_REMOVED
                || kind == ChangePointKind.FIELD_DESCRIPTOR_CHANGED
                || kind == ChangePointKind
                .SERVICE_PROVIDER_REGISTRATION_REMOVED;
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
