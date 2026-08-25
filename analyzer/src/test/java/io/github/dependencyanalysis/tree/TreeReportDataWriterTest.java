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

    /** Expected distinct original/resolved version count. */
    private static final int EXPECTED_UNIQUE_VERSIONS = 4;

    /** Temporary shard directory. */
    @TempDir
    private Path temporary;

    @Test
    void writesEverySelectedAndOmittedOccurrenceWithResolutionFields()
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
                    assertThat(value.uniqueVersionCount())
                            .isEqualTo(EXPECTED_UNIQUE_VERSIONS);
                });
        assertThat(data.manifest().schemaVersion()).isEqualTo(2);
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
                .contains("\"resolutionSource\":\"Direct selection\"")
                .contains("requested 1 → selected 2")
                .contains("\"resolutionSource\":\"Conflict mediation\"")
                .contains("effective 0 → selected 2")
                .contains("g:first:jar:1 → "
                        + "g:middle:jar:1 → "
                        + "g:shared:jar:0")
                .doesNotContain("<script>");
        assertThat(shard("module-dependency-catalog"))
                .contains("\"moduleId\":0,\"dependencyId\":0,"
                        + "\"resolvedVersionCount\":1,"
                        + "\"uniqueVersionCount\":3")
                .contains("\"moduleId\":1,\"dependencyId\":0,"
                        + "\"resolvedVersionCount\":1,"
                        + "\"uniqueVersionCount\":1");
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
        assertThat(data.manifest().modules())
                .extracting(TreeReportManifest.ModuleDescriptor
                        ::dependencyCatalogStart,
                        TreeReportManifest.ModuleDescriptor
                                ::dependencyCatalogCount)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(0, 2),
                        org.assertj.core.groups.Tuple.tuple(2, 1));
    }

    @Test
    void projectsManagedAndOmittedResolutionWithoutCountingIntermediate()
            throws Exception {
        final DependencyKey managedConflict = new DependencyKey(
                "g", "managed-conflict", "jar", "");
        final DependencyKey managedSelected = new DependencyKey(
                "g", "managed-selected", "jar", "");
        final DependencyKey duplicate = new DependencyKey(
                "g", "duplicate", "jar", "");
        final DependencyKey cycle = new DependencyKey(
                "g", "cycle", "jar", "");
        final DependencyKey other = new DependencyKey(
                "g", "other", "jar", "");
        final ModuleTreeResult module = module("g:first:1",
                occurrence(managedConflict, "1", "1", "2", "3",
                        false, "conflict"),
                occurrence(managedSelected, "1", "1", "2", "2",
                        true, ""),
                occurrence(duplicate, "4", "", "4", "4",
                        false, "duplicate"),
                occurrence(cycle, "5", "", "5", "5",
                        false, "cycle"),
                occurrence(other, "6", "", "6", "6",
                        false, "omitted <script>"));

        final TreeReportDataWriter.TreeReportData data = write(
                List.of(module));

        assertThat(data.manifest().dependencies().stream()
                .filter(value -> value.value().contains("managed-conflict"))
                .findFirst()).get().satisfies(value -> {
                    assertThat(value.resolvedVersionCount()).isZero();
                    assertThat(value.uniqueVersionCount()).isEqualTo(2);
                });
        assertThat(data.manifest().modules()).singleElement()
                .satisfies(value -> assertThat(
                        value.internalConflictCount()).isEqualTo(2));
        assertThat(shard("dependency-rows"))
                .contains("Dependency management → Conflict mediation")
                .contains("requested 1 → effective 2; "
                        + "effective 2 → selected 3")
                .contains("Dependency management")
                .contains("requested 1 → effective 2")
                .contains("Duplicate mediation")
                .contains("resolved 4 reused from an earlier duplicate")
                .contains("Cycle omission")
                .contains("reported 5; cycle omitted")
                .contains("Maven omission")
                .doesNotContain("<script>");
        try (var files = Files.list(temporary)) {
            assertThat(files.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith("internal-conflicts-"))
                    .toList()).isEmpty();
        }
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

    private DependencyOccurrence occurrence(
            final DependencyKey key,
            final String requested,
            final String managedFrom,
            final String effective,
            final String selectedVersion,
            final boolean selected,
            final String reason) {
        return new DependencyOccurrence(key,
                new OccurrenceVersions(requested, managedFrom,
                        effective, selectedVersion),
                new OccurrenceScopes("compile", ""), null,
                new OccurrenceSelection(selected, reason),
                List.of("g:first:jar:1", key + ":" + effective), false);
    }
}
