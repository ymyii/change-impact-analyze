package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.callgraph.MethodId;
import io.github.dependencyanalysis.dependency.ArtifactCoord;

import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests for method evidence selection in the impact pipeline. */
class ImpactPipelineTest {

    @Test
    void selectsUniqueBodyChangesPresentInImpactPaths() {
        final ArtifactCoord artifact = new ArtifactCoord(
                "g", "a", "jar", "2.0");
        final ChangePoint body = new ChangePoint(
                artifact,
                ChangePointKind.METHOD_BODY_CHANGED,
                "com/Foo", "changed", "()V",
                "old", "new");
        final ChangePoint removed = new ChangePoint(
                artifact,
                ChangePointKind.METHOD_REMOVED,
                "com/Foo", "removed", "()V",
                null, null);
        final ImpactResult impact = new ImpactResult(
                List.of(path("one", body),
                        path("two", body),
                        path("three", removed)),
                new EnumMap<>(NotReportedReason.class));

        assertThat(ImpactPipeline
                .impactedBodyChanges(impact))
                .containsExactly(body);
    }

    private ImpactPath path(
            final String name,
            final ChangePoint point) {
        final MethodId method = new MethodId(
                "com/App", name, "()V",
                "module", "com/App.class");
        return new ImpactPath(method, point,
                Collections.emptyList(),
                Collections.emptySet(),
                Collections.emptyList());
    }
}
