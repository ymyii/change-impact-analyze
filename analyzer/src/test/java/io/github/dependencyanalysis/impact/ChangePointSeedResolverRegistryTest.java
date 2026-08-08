package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePointKind;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChangePointSeedResolverRegistryTest {

    @Test
    void everyAnalyzedKindHasExactlyOneResolver() {
        final ChangePointSeedResolverRegistry registry =
                new ChangePointSeedResolverRegistry();

        for (ChangePointKind kind : ChangePointKind.values()) {
            final int expectedCount = switch (kind) {
                case CLASS_ADDED, METHOD_ADDED, FIELD_ADDED -> 0;
                default -> 1;
            };
            assertThat(registry.resolverCount(kind))
                    .as(kind.name())
                    .isEqualTo(expectedCount);
        }
    }
}
