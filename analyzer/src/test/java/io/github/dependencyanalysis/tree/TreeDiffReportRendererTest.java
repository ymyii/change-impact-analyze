package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.runtime.MavenRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.MavenRuntimeSource;
import io.github.dependencyanalysis.runtime.MavenVersion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/** Tree diff Schema v1 renderer tests. */
class TreeDiffReportRendererTest {

    /** Temporary report root. */
    @TempDir
    private Path temporary;

    @Test
    void publishesSchemaV1WithSixAndFourColumnTables() throws Exception {
        final DependencyKey key = new DependencyKey(
                "example", "client", "jar", "");
        final TreeDiffPathOccurrence baselinePath =
                new TreeDiffPathOccurrence(
                        new TreeDiffPathKey(List.of(key)),
                        "example:app:jar:1\n└─ example:client:jar:1:compile",
                        "1", "0.9", "compile", true);
        final TreeDiffPathOccurrence targetPath =
                new TreeDiffPathOccurrence(
                        new TreeDiffPathKey(List.of(key)),
                        "example:app:jar:1\n└─ example:client:jar:2:runtime",
                        "2", "1.5", "runtime", true);
        final TreeDiffSideDependency baseline =
                new TreeDiffSideDependency(key, "1", "compile", true,
                        new java.util.TreeMap<>(java.util.Map.of(
                                baselinePath.pathKey(), baselinePath)));
        final TreeDiffSideDependency target =
                new TreeDiffSideDependency(key, "2", "runtime", true,
                        new java.util.TreeMap<>(java.util.Map.of(
                                targetPath.pathKey(), targetPath)));
        final TreeDependencyDiffRecord dependency =
                new TreeDependencyDiffRecord(key, baseline, target,
                        TreeDependencyBaseChangeType.VERSION_CHANGED, true,
                        List.of(new TreeDiffChainRow(baselinePath,
                                targetPath, TreeChainChangeType.UNCHANGED)));
        final TreeDiffModuleResult module = new TreeDiffModuleResult(
                "pom.xml", "example:app:1", "example:app:1",
                TreeDiffSideState.PRESENT, TreeDiffSideState.PRESENT,
                TreeDiffComparisonStatus.COMPARABLE,
                new TreeDiffMetrics(1, 0, 0, 0, 1),
                List.of(dependency), null, null, List.of());
        final TreeDiffReactorResult reactor = new TreeDiffReactorResult(
                "pom.xml", "example:app:1", "example:app:1",
                TreeDiffSideState.PRESENT, TreeDiffSideState.PRESENT,
                TreeDiffComparisonStatus.COMPARABLE, List.of(module),
                module.metrics(), List.of());

        final TreeDiffReportSession session =
                new TreeDiffReportRenderer().start(metadata(), 1, temporary);
        session.publish(reactor);
        session.complete();

        assertThat(session.state()).isEqualTo(TreeDiffReportState.SUCCESS);
        final String index = Files.readString(temporary.resolve(
                "index.html"));
        assertThat(index)
                .contains("<html lang=\"en\">")
                .contains("Dependency Tree Diff")
                .contains("SUCCESS")
                .doesNotContainPattern("[\\p{IsHan}]");
        final Path page;
        try (Stream<Path> files = Files.list(temporary.resolve(
                "tree-diff-report/reactors"))) {
            page = files.filter(path -> path.getFileName().toString()
                            .endsWith(".html"))
                    .findFirst().orElseThrow();
        }
        final String pageHtml = Files.readString(page);
        assertThat(pageHtml)
                .contains("<html lang=\"en\">")
                .contains("tree-diff-report")
                .contains("Version</th>")
                .contains("Direct dependency</th>")
                .contains("<th scope=\"col\">Actions</th>")
                .contains("<h3>Issues</h3>")
                .contains("Dependency tree comparison")
                .doesNotContain("changeTypes")
                .doesNotContainPattern("[\\p{IsHan}]");
        final String moduleSummary = pageHtml.substring(
                pageHtml.indexOf("<table id=\"module-summary\">"),
                pageHtml.indexOf("</table>", pageHtml.indexOf(
                        "<table id=\"module-summary\">")));
        assertThat(moduleSummary)
                .contains("Version changed", "Added", "Removed",
                        "Resolved version unchanged", "Scope changed")
                .doesNotContain("Actions", "table-action");
        final String comparisonTable = pageHtml.substring(
                pageHtml.indexOf("<table id=\"dependency-table\">"),
                pageHtml.indexOf("</table>", pageHtml.indexOf(
                        "<table id=\"dependency-table\">")));
        assertThat(comparisonTable)
                .contains("<th scope=\"col\">Actions</th>");
        final Path data = page.resolveSibling(page.getFileName().toString()
                .replace(".html", "-data"));
        final String shards;
        try (Stream<Path> files = Files.list(data)) {
            shards = files.filter(Files::isRegularFile)
                    .sorted().map(path -> {
                        try {
                            return Files.readString(path);
                        } catch (java.io.IOException exception) {
                            throw new java.io.UncheckedIOException(exception);
                        }
                    }).collect(java.util.stream.Collectors.joining());
        }
        assertThat(shards)
                .contains("\"schema\":\"tree-diff-report\"")
                .contains("\"schemaVersion\":1")
                .contains("\"baseChangeType\":\"VERSION_CHANGED\"")
                .contains("\"scopeChanged\":true")
                .contains("\"baselineManagedFromVersion\":\"0.9\"")
                .contains("\"chainChangeType\":\"UNCHANGED\"")
                .doesNotContain("changeTypes", "reactorId");
    }

    private TreeDiffReportMetadata metadata() {
        final MavenRuntimeDescriptor runtime = new MavenRuntimeDescriptor(
                MavenRuntimeSource.EMBEDDED, temporary.resolve("mvn"),
                MavenVersion.parse("3.9.11"), null, temporary);
        return new TreeDiffReportMetadata(
                new TreeDiffSideMetadata("git-ref", "main",
                        "0123456789abcdef", false),
                new TreeDiffSideMetadata("current-workspace",
                        "Current workspace", "fedcba9876543210", true),
                temporary, Path.of(""), Set.of("compile", "runtime"),
                runtime, "3.6.1", List.of());
    }
}
