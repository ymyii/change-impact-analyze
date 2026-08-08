package io.github.dependencyanalysis.impact;

import java.util.List;

import io.github.dependencyanalysis.bytecode.ChangePointKind;

/** Exact ChangePoint kind to seed resolver registry. */
final class ChangePointSeedResolverRegistry {

    /** Independent resolver strategies. */
    private final List<ChangePointSeedResolver> resolvers = List.of(
            new ExistingMemberSeedResolver(),
            new ClassReferenceSeedResolver(),
            new AccessNarrowingSeedResolver());

    ChangePointSeedResolution resolve(
            final ChangePointSeedRequest request) {
        final List<ChangePointSeedResolver> matches = resolvers.stream()
                .filter(resolver -> resolver.supports(
                        request.point().getKind()))
                .toList();
        if (matches.size() != 1) {
            throw new IllegalStateException(
                    "Expected one seed resolver for "
                            + request.point().getKind() + ", found "
                            + matches.size());
        }
        return matches.get(0).resolve(request);
    }

    int resolverCount(final ChangePointKind kind) {
        return (int) resolvers.stream()
                .filter(resolver -> resolver.supports(kind)).count();
    }
}
