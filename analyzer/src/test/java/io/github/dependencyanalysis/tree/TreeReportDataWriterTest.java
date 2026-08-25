package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.reactor.ReactorDescriptor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tree Reactor browser data projection tests. */
class TreeReportDataWriterTest {

    /** Expected full occurrence row count. */
    private static final int EXPECTED_ROWS = 3;

    /** Temporary shard directory. */
    @TempDir
    private Path temporary;

    @Test
    void writesEverySelectedAndOmittedOccurrenceWithSixFields()
            throws Exception {
        final DependencyKey shared = new DependencyKey(
                "g<script>", "shared", "jar", "");
        final ModuleTreeResult first = module("g:first:1",
                occurrence(shared, "1", "2", true, "compile",
                        List.of("g:first:jar:1", "g:shared:jar:2")),
                occurrence(shared, "0", "2", false, "runtime",
                        List.of("g:first:jar:1", "g:middle:jar:1",
                                "g:shared:jar:0")));
        final ModuleTreeResult second = module("g:second:1",
                occurrence(shared, "3", "3", true, "test",
                        List.of("g:second:jar:1", "g:shared:jar:3")));

        final TreeReportDataWriter.TreeReportData data = write(
                List.of(first, second));

        assertThat(data.manifest().dependencyRows())
                .isEqualTo(EXPECTED_ROWS);
        assertThat(data.manifest().dependencies()).singleElement()
                .satisfies(value -> {
                    assertThat(value.value())
                            .isEqualTo("g<script>:shared:jar:");
                    assertThat(value.resolvedVersionCount()).isEqualTo(2);
                });
        assertThat(data.multiVersionDependencyCount()).isEqualTo(1);
        assertThat(data.moduleMultiVersionCounts())
                .containsEntry(0, 1).containsEntry(1, 1);
        final String rows = shard("dependency-rows");
        assertThat(rows)
                .contains("\"scope\":\"compile\"")
                .contains("\"scope\":\"runtime\"")
                .contains("\"scope\":\"test\"")
                .contains("\"originalVersion\":\"0\"")
                .contains("\"resolvedVersion\":\"2\"")
                .contains("g:first:jar:1 → "
                        + "g:middle:jar:1 → "
                        + "g:shared:jar:0")
                .doesNotContain("<script>");
    }

    @Test
    void assignsDeterministicCatalogIdsAndExactRanges() throws Exception {
        final DependencyKey alpha = new DependencyKey(
                "g", "alpha", "jar", "");
        final DependencyKey beta = new DependencyKey(
                "g", "beta", "jar", "");
        final ModuleTreeResult first = module("g:first:1",
                occurrence(beta, "1", "1", true, "runtime",
                        List.of("first", "beta")),
                occurrence(alpha, "1", "1", true, "compile",
                        List.of("first", "alpha")));
        final ModuleTreeResult second = module("g:second:1",
                occurrence(beta, "1", "1", true, "compile",
                        List.of("second", "beta")));

        final TreeReportDataWriter.TreeReportData data = write(
                List.of(first, second));

        assertThat(data.manifest().dependencies())
                .extracting(TreeReportManifest.DependencyDescriptor::value)
                .containsExactly("g:alpha:jar:", "g:beta:jar:");
        assertThat(data.manifest().modules())
                .extracting(TreeReportManifest.ModuleDescriptor::id,
                        TreeReportManifest.ModuleDescriptor::dependencyStart,
                        TreeReportManifest.ModuleDescriptor::dependencyCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(0, 0, 2),
                        org.assertj.core.groups.Tuple.tuple(1, 2, 1));
        assertThat(shard("dependency-ranges"))
                .contains("\"id\":0,\"rowStart\":1,\"rowCount\":1")
                .contains("\"id\":1,\"rowStart\":0,\"rowCount\":1")
                .contains("\"id\":2,\"rowStart\":2,\"rowCount\":1")
                .contains("\"id\":3,\"rowStart\":1,\"rowCount\":2");
    }

    private TreeReportDataWriter.TreeReportData write(
            final List<ModuleTreeResult> modules) throws Exception {
        final ReactorDescriptor descriptor = new ReactorDescriptor(
                Path.of("pom.xml"), "g:root:1", List.of(Path.of("pom.xml")),
                List.of());
        final ReactorTreeResult reactor = new ReactorTreeResult(
                descriptor, modules, ReactorStatus.SUCCESS, "");
        return new TreeReportDataWriter().write(reactor, temporary,
                "reactor-data", null);
    }

    private String shard(final String prefix) throws Exception {
        final StringBuilder result = new StringBuilder();
        try (var files = Files.list(temporary)) {
            for (Path path : files.filter(value -> value.getFileName()
                            .toString().startsWith(prefix + "-"))
                    .sorted().toList()) {
                result.append(Files.readString(path));
            }
        }
        return result.toString();
    }

    private ModuleTreeResult module(
            final String coordinate,
            final DependencyOccurrence... occurrences) {
        return new ModuleTreeResult(Path.of(coordinate, "pom.xml"),
                coordinate, List.of(occurrences), true, "");
    }

    private DependencyOccurrence occurrence(
            final DependencyKey key,
            final String requested,
            final String selectedVersion,
            final boolean selected,
            final String scope,
            final List<String> path) {
        return new DependencyOccurrence(key,
                new OccurrenceVersions(requested, "", requested,
                        selectedVersion),
                new OccurrenceScopes(scope, ""), null,
                new OccurrenceSelection(selected,
                        selected ? "" : "conflict"), path, false);
    }
}
