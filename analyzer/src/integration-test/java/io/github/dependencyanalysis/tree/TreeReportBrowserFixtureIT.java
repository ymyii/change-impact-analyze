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
        publishTreeDiffFixture(projectRoot,
                projectRoot.resolve(
                        "target/playwright-report-fixture/tree-diff"));

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
                    .anyMatch(name -> name.startsWith(
                            "module-dependency-catalog-"))
                    .anyMatch(name -> name.startsWith("class-conflicts-"))
                    .anyMatch(name -> name.startsWith("class-sources-"))
                    .anyMatch(name -> name.startsWith("dependency-trees-"));
            assertThat(names).noneMatch(name ->
                    name.startsWith("internal-conflicts-"));
        }
        assertThat(projectRoot.resolve("target/playwright-report-fixture/"
                + "tree-diff/index.html")).isRegularFile();
    }

    private void publishTreeDiffFixture(
            final Path projectRoot,
            final Path fixtureRoot) throws Exception {
        final List<TreeDiffModuleResult> modules = new ArrayList<>();
        TreeDiffMetrics reactorMetrics = TreeDiffMetrics.ZERO;
        for (int moduleIndex = 0; moduleIndex < MODULE_COUNT; moduleIndex++) {
            final String name = moduleIndex == 0 ? "application"
                    : moduleIndex == 1 ? "library"
                    : String.format("module-%03d", moduleIndex);
            final String coordinate = "io.browserfixture:" + name + ":1.0.0";
            final List<TreeDependencyDiffRecord> dependencies =
                    new ArrayList<>();
            final TreeDiffMetrics.Builder metrics =
                    new TreeDiffMetrics.Builder();
            for (int dependencyIndex = 0;
                 dependencyIndex < DEPENDENCIES_PER_MODULE;
                 dependencyIndex++) {
                final String artifact = String.format("dependency-%03d",
                        (moduleIndex + dependencyIndex) % 75);
                final DependencyKey key = new DependencyKey(
                        "org.browserfixture", artifact, "jar", "");
                final TreeDependencyBaseChangeType type = switch (
                        dependencyIndex % 4) {
                    case 0 -> TreeDependencyBaseChangeType.VERSION_CHANGED;
                    case 1 -> TreeDependencyBaseChangeType.ADDED;
                    case 2 -> TreeDependencyBaseChangeType.REMOVED;
                    default -> TreeDependencyBaseChangeType
                            .RESOLVED_UNCHANGED;
                };
                final boolean scopeChanged = dependencyIndex % 8 == 0;
                final TreeDiffPathKey pathKey = new TreeDiffPathKey(
                        List.of(key));
                final TreeDiffPathOccurrence beforePath =
                        new TreeDiffPathOccurrence(pathKey,
                                coordinate + "\n└─ org.browserfixture:"
                                        + artifact + ":jar:1.0.0:compile",
                                "1.0.0", dependencyIndex == 0
                                ? "0.9.0" : "", "compile", true);
                final TreeDiffPathOccurrence afterPath =
                        new TreeDiffPathOccurrence(pathKey,
                                coordinate + "\n└─ org.browserfixture:"
                                        + artifact + ":jar:2.0.0:"
                                        + (scopeChanged
                                        ? "runtime" : "compile"),
                                type == TreeDependencyBaseChangeType
                                        .VERSION_CHANGED ? "2.0.0" : "1.0.0",
                                dependencyIndex == 0 ? "1.5.0" : "",
                                scopeChanged ? "runtime" : "compile", true);
                final TreeDiffSideDependency before = type
                        == TreeDependencyBaseChangeType.ADDED ? null
                        : new TreeDiffSideDependency(key, "1.0.0",
                        "compile", true, new java.util.TreeMap<>(
                        java.util.Map.of(pathKey, beforePath)));
                final TreeDiffSideDependency after = type
                        == TreeDependencyBaseChangeType.REMOVED ? null
                        : new TreeDiffSideDependency(key,
                        type == TreeDependencyBaseChangeType.VERSION_CHANGED
                                ? "2.0.0" : "1.0.0",
                        scopeChanged ? "runtime" : "compile", true,
                        new java.util.TreeMap<>(java.util.Map.of(
                                pathKey, afterPath)));
                final TreeChainChangeType chainType = before == null
                        ? TreeChainChangeType.ADDED
                        : after == null ? TreeChainChangeType.REMOVED
                        : TreeChainChangeType.UNCHANGED;
                final List<TreeDiffChainRow> chains = dependencyIndex == 0
                        ? denseChainRows(coordinate, key, beforePath,
                        afterPath) : List.of(new TreeDiffChainRow(
                        before == null ? null : beforePath,
                        after == null ? null : afterPath,
                        chainType));
                dependencies.add(new TreeDependencyDiffRecord(key,
                        before, after, type,
                        before != null && after != null && scopeChanged,
                        chains));
                metrics.add(type,
                        before != null && after != null && scopeChanged);
            }
            final TreeDiffMetrics moduleMetrics = metrics.build();
            reactorMetrics = reactorMetrics.plus(moduleMetrics);
            final ModuleTreeResult baselineTree = diffTreeModule(
                    moduleIndex, name, coordinate);
            final ModuleTreeResult targetTree = diffTreeModule(
                    moduleIndex + 1, name, coordinate);
            modules.add(new TreeDiffModuleResult(
                    name + "/pom.xml", coordinate, coordinate,
                    TreeDiffSideState.PRESENT,
                    TreeDiffSideState.PRESENT,
                    TreeDiffComparisonStatus.COMPARABLE,
                    moduleMetrics, dependencies, baselineTree,
                    targetTree, List.of()));
        }
        final TreeDiffReactorResult reactor = new TreeDiffReactorResult(
                "pom.xml", "io.browserfixture:root:1.0.0",
                "io.browserfixture:root:1.0.0",
                TreeDiffSideState.PRESENT, TreeDiffSideState.PRESENT,
                TreeDiffComparisonStatus.COMPARABLE, modules,
                reactorMetrics, List.of());
        final MavenRuntimeDescriptor runtime = new MavenRuntimeDescriptor(
                MavenRuntimeSource.EMBEDDED, projectRoot.resolve("mvn"),
                MavenVersion.parse("3.9.11"), null, projectRoot);
        final TreeDiffReportMetadata metadata =
                new TreeDiffReportMetadata(
                        new TreeDiffSideMetadata("git-ref", "main",
                                "0123456789abcdef", false),
                        new TreeDiffSideMetadata("current-workspace",
                                "Current workspace",
                                "fedcba9876543210", true),
                        projectRoot, Path.of(""),
                        Set.of("compile", "runtime"), runtime,
                        "3.6.1", List.of());
        final TreeDiffReportSession session =
                new TreeDiffReportRenderer().start(metadata, 1, fixtureRoot);
        session.publish(reactor);
        session.complete();
    }

    private List<TreeDiffChainRow> denseChainRows(
            final String coordinate,
            final DependencyKey dependency,
            final TreeDiffPathOccurrence baseline,
            final TreeDiffPathOccurrence target) {
        final List<TreeDiffChainRow> chains = new ArrayList<>();
        for (int index = 0; index < 12; index++) {
            final DependencyKey bridge = new DependencyKey(
                    "org.browserfixture", String.format("bridge-%03d", index),
                    "jar", "");
            final TreeDiffPathKey pathKey = new TreeDiffPathKey(
                    List.of(bridge, dependency));
            chains.add(new TreeDiffChainRow(
                    new TreeDiffPathOccurrence(pathKey,
                            coordinate + "\n+- org.browserfixture:"
                                    + bridge.getArtifactId()
                                    + ":jar:1.0.0:compile\n"
                                    + "\\- org.browserfixture:"
                                    + dependency.getArtifactId()
                                    + ":jar:1.0.0:compile",
                            baseline.resolvedVersion(),
                            baseline.managedFromVersion(), "compile", false),
                    new TreeDiffPathOccurrence(pathKey,
                            coordinate + "\n+- org.browserfixture:"
                                    + bridge.getArtifactId()
                                    + ":jar:1.1.0:runtime\n"
                                    + "\\- org.browserfixture:"
                                    + dependency.getArtifactId()
                                    + ":jar:2.0.0:runtime",
                            target.resolvedVersion(),
                            target.managedFromVersion(), "runtime", false),
                    TreeChainChangeType.UNCHANGED));
        }
        return chains;
    }

    private ModuleTreeResult diffTreeModule(
            final int moduleIndex,
            final String name,
            final String coordinate) {
        final List<DependencyOccurrence> occurrences = new ArrayList<>();
        for (int index = 0; index < 48; index++) {
            occurrences.add(occurrence(moduleIndex,
                    index % DEPENDENCIES_PER_MODULE, coordinate));
        }
        return new ModuleTreeResult(Path.of(name, "pom.xml"), coordinate,
                occurrences, true, "", ModuleAnalysisRole.REQUESTED);
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
        final boolean managed = dependencyIndex == 0;
        final String scope = dependencyIndex % 3 == 0
                ? "runtime" : dependencyIndex % 3 == 1
                ? "test" : "compile";
        return new DependencyOccurrence(key,
                new OccurrenceVersions(managed ? "0.8.0" : requestedVersion,
                        managed ? "0.8.0" : "",
                        managed ? resolvedVersion : requestedVersion,
                        resolvedVersion),
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
