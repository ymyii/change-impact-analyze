package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.bytecode.DecompiledMethod;
import io.github.dependencyanalysis.classpath.ClassConflictRisk;
import io.github.dependencyanalysis.classpath.CodeOrigin;
import io.github.dependencyanalysis.preflight.PreflightReport;
import io.github.dependencyanalysis.runtime.MavenRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.MavenRuntimeSource;
import io.github.dependencyanalysis.runtime.MavenVersion;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

// Wiki: wiki/features/repository-dependency-tree-report.md - Browser gate
// Wiki: wiki/runbooks/build-test-package.md - Playwright fixture entrypoint
/** Publishes the deterministic Tree Report consumed by Playwright. */
class TreeReportBrowserFixtureIT {

    /** Conflicts required to exercise the default ten-row page. */
    private static final int CONFLICT_COUNT = 12;

    @Test
    void publishesDeterministicOfflineTreeBrowserFixture() throws Exception {
        final Path projectRoot = Path.of(System.getProperty(
                "cia.multiModuleProjectDirectory", "."))
                .toAbsolutePath().normalize();
        final Path fixtureRoot = projectRoot.resolve(
                "target/playwright-report-fixture/tree");
        Files.createDirectories(fixtureRoot);

        new TreeReportRenderer().render(treeResult(projectRoot), fixtureRoot);

        assertThat(fixtureRoot.resolve("index.html")).isRegularFile();
        final Path reactors = fixtureRoot.resolve(
                "dependency-report/reactors");
        try (Stream<Path> files = Files.list(reactors)) {
            assertThat(files.filter(path -> path.getFileName().toString()
                    .endsWith(".html")).toList()).hasSize(1);
        }
        try (Stream<Path> files = Files.walk(reactors)) {
            assertThat(files.filter(path -> path.getFileName().toString()
                    .endsWith(".js")).count()).isEqualTo(
                            CONFLICT_COUNT + 1L);
        }
    }

    private TreeRepositoryResult treeResult(final Path projectRoot) {
        final List<TreeClassConflict> conflicts = new ArrayList<>();
        for (int index = 0; index < CONFLICT_COUNT; index++) {
            conflicts.add(conflict(index, projectRoot));
        }
        final ModuleTreeResult application = new ModuleTreeResult(
                Path.of("application/pom.xml"),
                "io.browserfixture:application:1.0.0", List.of(),
                true, "", ModuleAnalysisRole.REQUESTED)
                .withClassAnalysis(conflicts, List.of());
        final ModuleTreeResult library = new ModuleTreeResult(
                Path.of("library/pom.xml"),
                "io.browserfixture:library:1.0.0", List.of(),
                true, "", ModuleAnalysisRole.DEPENDENCY)
                .withClassAnalysis(List.of(conflict(
                        CONFLICT_COUNT, projectRoot)), List.of());
        final ReactorDescriptor descriptor = new ReactorDescriptor(
                Path.of("pom.xml"), "io.browserfixture:root:1.0.0",
                List.of(Path.of("pom.xml"), Path.of("application/pom.xml"),
                        Path.of("library/pom.xml")),
                List.of(Path.of("application/pom.xml")), List.of());
        final ReactorTreeResult reactor = new ReactorTreeResult(
                descriptor, List.of(application, library),
                ReactorStatus.SUCCESS, "");
        final RepositorySnapshot snapshot = new RepositorySnapshot(
                projectRoot, projectRoot, "current checkout", "fixture",
                "fixture", false, () -> { });
        final MavenRuntimeDescriptor runtime = new MavenRuntimeDescriptor(
                MavenRuntimeSource.EMBEDDED, projectRoot.resolve("mvn"),
                MavenVersion.parse("3.9.11"), null, projectRoot);
        return new TreeRepositoryResult(snapshot, runtime,
                List.of("compile", "dependency:tree",
                        "collect-classpath-evidence"),
                Set.of("compile", "runtime"),
                new PreflightReport(List.of()), List.of(reactor));
    }

    private TreeClassConflict conflict(
            final int index,
            final Path projectRoot) {
        final String suffix = String.format("%02d", index);
        final String binaryName = "fixture/Conflict" + suffix;
        final TreeClassConflictCandidate winner =
                new TreeClassConflictCandidate(CodeOrigin.PROJECT,
                        "io.browserfixture:application:jar:1.0.0", "",
                        "winner-" + suffix, binaryName + ".class",
                        projectRoot.resolve("target/browser-project-classes"),
                        index == 2
                                ? DecompiledMethod.unavailable(
                                        "fixture winner unavailable")
                                : DecompiledMethod.available(winnerSource(suffix)));
        final TreeClassConflictCandidate shadowed =
                new TreeClassConflictCandidate(CodeOrigin.DEPENDENCY,
                        "org.browserfixture:library:jar:2.0.0", "runtime",
                        index % 2 == 0 ? "shadowed-" + suffix
                                : "winner-" + suffix,
                        binaryName + ".class",
                        projectRoot.resolve("target/browser-library.jar"),
                        DecompiledMethod.available(shadowedSource(suffix)));
        return new TreeClassConflict(binaryName,
                index % 2 == 0 ? ClassConflictRisk.HIGH
                        : ClassConflictRisk.LOW,
                winner, List.of(winner, shadowed),
                "PROJECT precedes DEPENDENCY in classpath order");
    }

    private String winnerSource(final String suffix) {
        return "package fixture;\n"
                + "// </script><script>window.__treeInjected=true</script>\n"
                + "public class Conflict" + suffix
                + " { String source() { return \"winner\"; } }";
    }

    private String shadowedSource(final String suffix) {
        return "package fixture;\npublic class Conflict" + suffix
                + " { String source() { return \"shadowed\"; } }";
    }
}
