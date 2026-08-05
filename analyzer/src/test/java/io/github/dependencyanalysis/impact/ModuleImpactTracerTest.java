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
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests duplicate-specific ChangePoint classification. */
class ModuleImpactTracerTest {

    /** Temporary classpath sources. */
    @TempDir
    private Path temporary;

    @Test
    void classifiesOnlyLoserChangePointAsShadowed() throws Exception {
        final Path winner = temporary.resolve("project");
        write(winner, new byte[]{0});
        final Path loser = jar(new byte[]{1});
        final ArtifactCoord loserArtifact = new ArtifactCoord(
                "example", "dependency", "jar", "2");
        final ClassOwnershipIndex ownership = new ClassOwnershipIndex();
        ownership.addDirectory(winner, CodeOrigin.PROJECT);
        try (JarFile handle = new JarFile(loser.toFile())) {
            ownership.addJar(loserArtifact, handle, CodeOrigin.DEPENDENCY);
        }

        assertThat(ModuleImpactTracer.duplicateDisposition(
                bound(loserArtifact), ownership))
                .isEqualTo(ChangePointDisposition.SHADOWED_BY_DUPLICATE);
        assertThat(ModuleImpactTracer.duplicateDisposition(
                bound(new ArtifactCoord(
                        "example", "other", "jar", "2")), ownership))
                .isNull();
    }

    private BoundChangePoint bound(final ArtifactCoord newArtifact) {
        final ModuleId moduleId = new ModuleId(new ArtifactCoord(
                "example", "app", "jar", "1"), Path.of("app"));
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "example", "dependency", "jar", "1");
        return new BoundChangePoint(new DependencyUpgradeKey(
                moduleId, DependencyScope.COMPILE,
                oldArtifact, newArtifact),
                new ChangePoint(newArtifact,
                        ChangePointKind.METHOD_REMOVED,
                        "sample/Duplicate", "removed", "()V",
                        null, null));
    }

    private Path jar(final byte[] content) throws Exception {
        final Path result = temporary.resolve("dependency.jar");
        try (JarOutputStream output = new JarOutputStream(
                Files.newOutputStream(result))) {
            output.putNextEntry(new JarEntry("sample/Duplicate.class"));
            output.write(content);
            output.closeEntry();
        }
        return result;
    }

    private void write(final Path root, final byte[] content)
            throws Exception {
        final Path file = root.resolve("sample/Duplicate.class");
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }
}
