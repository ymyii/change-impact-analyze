package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.callgraph.ClassOwnershipIndex;
import io.github.dependencyanalysis.callgraph.CodeOrigin;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyScope;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests duplicate-specific ChangePoint classification. */
class ModuleImpactTracerTest {

    /** Temporary classpath sources. */
    @TempDir
    private Path temporary;

    @Test
    void classifiesOnlyLoserChangePointAsShadowed() throws Exception {
        final Path winner = temporary.resolve("project");
        final Path loser = temporary.resolve("dependency");
        write(winner, new byte[]{0});
        write(loser, new byte[]{1});
        final ClassOwnershipIndex ownership = new ClassOwnershipIndex();
        ownership.addDirectory(winner, CodeOrigin.PROJECT);
        ownership.addDirectory(loser, CodeOrigin.DEPENDENCY);

        assertThat(ModuleImpactTracer.duplicateDisposition(
                bound(loser), ownership))
                .isEqualTo(ChangePointDisposition.SHADOWED_BY_DUPLICATE);
        assertThat(ModuleImpactTracer.duplicateDisposition(
                bound(winner), ownership)).isNull();
    }

    private BoundChangePoint bound(final Path newPath) {
        final ModuleId moduleId = new ModuleId(new ArtifactCoord(
                "example", "app", "jar", "1"), Path.of("app"));
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "example", "dependency", "jar", "1");
        final ArtifactCoord newArtifact = new ArtifactCoord(
                "example", "dependency", "jar", "2");
        return new BoundChangePoint(new DependencyUpgradeKey(
                moduleId, DependencyScope.COMPILE,
                oldArtifact, newArtifact,
                temporary.resolve("old.jar"), newPath),
                new ChangePoint(newArtifact,
                        ChangePointKind.METHOD_REMOVED,
                        "sample/Duplicate", "removed", "()V",
                        null, null));
    }

    private void write(final Path root, final byte[] content)
            throws Exception {
        final Path file = root.resolve("sample/Duplicate.class");
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }
}
