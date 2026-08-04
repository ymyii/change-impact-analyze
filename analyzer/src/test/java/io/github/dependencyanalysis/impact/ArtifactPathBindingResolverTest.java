package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyAnalysisResult;
import io.github.dependencyanalysis.dependency.DependencyNode;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.dependency.ModuleDependencyTree;
import io.github.dependencyanalysis.dependency.ResolvedArtifact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Coordinate-only module-local artifact binding tests. */
class ArtifactPathBindingResolverTest {

    /** Temporary module and artifact directory. */
    @TempDir
    private Path temporaryDirectory;

    @Test
    void resolvesBaselineAndTargetWithoutComparingDependencyScope()
            throws Exception {
        final ArtifactCoord module = new ArtifactCoord(
                "g", "module", "jar", "1");
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "g", "library", "jar", "1");
        final ArtifactCoord newArtifact = new ArtifactCoord(
                "g", "library", "jar", "2");
        final Path baselineDirectory = Files.createDirectory(
                temporaryDirectory.resolve("baseline")).toRealPath();
        final Path targetDirectory = Files.createDirectory(
                temporaryDirectory.resolve("target")).toRealPath();
        final Path oldPath = Files.createFile(
                baselineDirectory.resolve("library-1.jar")).toRealPath();
        final Path newPath = Files.createFile(
                targetDirectory.resolve("library-2.jar")).toRealPath();
        final ModuleDependencyTree baselineTree = new ModuleDependencyTree(
                module, baselineDirectory,
                List.of(new DependencyNode(oldArtifact,
                        DependencyScope.COMPILE, List.of())));
        final ModuleDependencyTree targetTree = new ModuleDependencyTree(
                module, targetDirectory,
                List.of(new DependencyNode(newArtifact,
                        DependencyScope.PROVIDED, List.of())));
        final DependencyAnalysisResult baseline =
                new DependencyAnalysisResult(List.of(baselineTree), Map.of(
                        baselineDirectory,
                        List.of(new ResolvedArtifact(
                                oldArtifact, oldPath))));
        final DependencyAnalysisResult target =
                new DependencyAnalysisResult(List.of(targetTree), Map.of(
                        targetDirectory,
                        List.of(new ResolvedArtifact(
                                newArtifact, newPath))));

        final ResolvedArtifact oldResolved =
                ArtifactPathBindingResolver.require(
                        "baseline", baseline, baselineTree,
                        oldArtifact, module.toString());
        final ResolvedArtifact newResolved =
                ArtifactPathBindingResolver.require(
                        "target", target, targetTree,
                        newArtifact, module.toString());

        assertThat(oldResolved.getPath()).isEqualTo(oldPath);
        assertThat(newResolved.getPath()).isEqualTo(newPath);
    }

    @Test
    void reportsSideAndDirectoryWhenBindingIsMissing() throws Exception {
        final ArtifactCoord module = new ArtifactCoord(
                "g", "module", "jar", "1");
        final ArtifactCoord artifact = new ArtifactCoord(
                "g", "missing", "jar", "1");
        final ModuleDependencyTree tree = new ModuleDependencyTree(
                module, temporaryDirectory, List.of());
        final DependencyAnalysisResult analysis =
                new DependencyAnalysisResult(List.of(tree), Map.of(
                        temporaryDirectory.toRealPath(), List.of()));

        assertThatThrownBy(() -> ArtifactPathBindingResolver.require(
                "target", analysis, tree, artifact, module.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("side=target")
                .hasMessageContaining("manifestDirectory=")
                .hasMessageContaining("matches=0");
    }
}
