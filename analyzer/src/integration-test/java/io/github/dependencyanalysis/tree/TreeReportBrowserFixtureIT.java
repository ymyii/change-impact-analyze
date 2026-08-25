package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.bytecode.DecompiledMethod;
import io.github.dependencyanalysis.classpath.ClassConflictRisk;
import io.github.dependencyanalysis.classpath.CodeOrigin;
import io.github.dependencyanalysis.preflight.PreflightReport;
import io.github.dependencyanalysis.reactor.ReactorDescriptor;
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

    /** Modules required to exercise a large searchable catalog. */
    private static final int MODULE_COUNT = 101;

    /** Dependency occurrences contributed by each Module. */
    private static final int DEPENDENCIES_PER_MODULE = 26;

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
            final List<String> names = files.filter(path -> path.getFileName()
                            .toString().endsWith(".js"))
                    .map(path -> path.getFileName().toString()).toList();
            assertThat(names)
                    .anyMatch(name -> name.startsWith("dependency-rows-"))
                    .anyMatch(name -> name.startsWith("dependency-ranges-"))
                    .anyMatch(name -> name.startsWith("class-conflicts-"))
                    .anyMatch(name -> name.startsWith("class-sources-"))
                    .anyMatch(name -> name.startsWith("dependency-trees-"));
        }
    }

    private TreeRepositoryResult treeResult(final Path projectRoot) {
        final List<TreeClassConflict> conflicts = new ArrayList<>();
        for (int index = 0; index < CONFLICT_COUNT; index++) {
            conflicts.add(conflict(index, projectRoot));
        }
        final List<ModuleTreeResult> modules = new ArrayList<>();
        for (int moduleIndex = 0; moduleIndex < MODULE_COUNT; moduleIndex++) {
            final String name = moduleIndex == 0 ? "application"
                    : moduleIndex == 1 ? "library"
                    : String.format("module-%03d", moduleIndex);
            final String coordinate = "io.browserfixture:" + name + ":1.0.0";
            final List<DependencyOccurrence> occurrences =
                    new ArrayList<>();
            for (int dependencyIndex = 0;
                 dependencyIndex < DEPENDENCIES_PER_MODULE;
                 dependencyIndex++) {
                occurrences.add(occurrence(moduleIndex,
                        dependencyIndex, coordinate));
            }
            ModuleTreeResult module = new ModuleTreeResult(
                    Path.of(name, "pom.xml"), coordinate, occurrences,
                    true, "", moduleIndex == 0
                    ? ModuleAnalysisRole.REQUESTED
                    : ModuleAnalysisRole.DEPENDENCY);
            if (moduleIndex == 0) {
                module = module.withClassAnalysis(conflicts, List.of());
            } else if (moduleIndex == 1) {
                module = module.withClassAnalysis(List.of(conflict(
                        CONFLICT_COUNT, projectRoot)), List.of());
            }
            modules.add(module);
        }
        final List<Path> reactorPoms = new ArrayList<>();
        reactorPoms.add(Path.of("pom.xml"));
        modules.stream().map(ModuleTreeResult::getPom)
                .forEach(reactorPoms::add);
        final ReactorDescriptor descriptor = new ReactorDescriptor(
                Path.of("pom.xml"), "io.browserfixture:root:1.0.0",
                reactorPoms,
                List.of(Path.of("application/pom.xml")), List.of());
        final ReactorTreeResult reactor = new ReactorTreeResult(
                descriptor, modules,
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

    private DependencyOccurrence occurrence(
            final int moduleIndex,
            final int dependencyIndex,
            final String moduleCoordinate) {
        final int artifactIndex = (moduleIndex + dependencyIndex) % 75;
        final String artifact = String.format(
                "dependency-%03d", artifactIndex);
        final DependencyKey key = new DependencyKey(
                "org.browserfixture", artifact, "jar", "");
        final boolean selected = dependencyIndex
                != DEPENDENCIES_PER_MODULE - 1;
        final String requestedVersion = selected ? "1.0.0" : "0.9.0";
        final String resolvedVersion = moduleIndex % 2 == 0
                ? "1.0.0" : "2.0.0";
        final String scope = dependencyIndex % 3 == 0
                ? "runtime" : dependencyIndex % 3 == 1
                ? "test" : "compile";
        return new DependencyOccurrence(key,
                new OccurrenceVersions(requestedVersion, "",
                        requestedVersion, resolvedVersion),
                new OccurrenceScopes(scope, ""), null,
                new OccurrenceSelection(selected,
                        selected ? "" : "conflict"),
                List.of(moduleCoordinate,
                        "org.browserfixture:middle:jar:1.0.0",
                        "org.browserfixture:" + artifact
                                + ":jar:" + requestedVersion), false);
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
