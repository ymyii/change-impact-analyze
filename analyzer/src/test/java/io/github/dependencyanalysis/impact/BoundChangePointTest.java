package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.AccessTransition;
import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.bytecode.JvmAccess;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyScope;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests immutable ChangePoint binding to Module upgrade provenance. */
class BoundChangePointTest {

    /** Baseline artifact. */
    private static final ArtifactCoord OLD = new ArtifactCoord(
            "example", "library", "jar", "1");

    /** Target artifact. */
    private static final ArtifactCoord TARGET = new ArtifactCoord(
            "example", "library", "jar", "2");

    @Test
    void sharesAccessChangePointAcrossModulesWithoutLosingTransition() {
        final AccessTransition transition = new AccessTransition(
                JvmAccess.PUBLIC, JvmAccess.PROTECTED);
        final ChangePoint point = ChangePoint.accessNarrowed(
                TARGET, ChangePointKind.METHOD_ACCESS_NARROWED,
                "example/library/Api", "call", "()V", transition);

        final BoundChangePoint first = new BoundChangePoint(
                key("module-a"), point);
        final BoundChangePoint second = new BoundChangePoint(
                key("module-b"), point);

        assertThat(first.getChangePoint()).isSameAs(point);
        assertThat(second.getChangePoint()).isSameAs(point);
        assertThat(first.getChangePoint().getAccessTransition())
                .contains(transition);
        assertThat(second.getChangePoint().getAccessTransition())
                .contains(transition);
    }

    @Test
    void rejectsChangePointForDifferentTargetArtifact() {
        final ArtifactCoord different = new ArtifactCoord(
                "example", "other", "jar", "2");
        final ChangePoint point = new ChangePoint(
                different, ChangePointKind.METHOD_REMOVED,
                "example/other/Api", "removed", "()V", null, null);

        assertThatThrownBy(() -> new BoundChangePoint(
                key("module-a"), point))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must equal target artifact")
                .hasMessageContaining(different.toString())
                .hasMessageContaining(TARGET.toString());
    }

    private DependencyUpgradeKey key(final String moduleName) {
        final ModuleId module = new ModuleId(new ArtifactCoord(
                "example", moduleName, "jar", "1"),
                Path.of(moduleName));
        return new DependencyUpgradeKey(module, DependencyScope.COMPILE,
                OLD, TARGET);
    }
}
