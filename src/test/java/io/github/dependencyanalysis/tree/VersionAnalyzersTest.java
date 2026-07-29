package io.github.dependencyanalysis.tree;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions
        .assertThat;

/** Module and cross-module version analysis tests. */
class VersionAnalyzersTest {

    @Test
    void reportsDifferentDependencyPathVersions() {
        final DependencyKey key = new DependencyKey(
                "g", "a", "jar", "");
        final ModuleTreeResult same = module("one",
                occurrence(key, "1", "1", true,
                        List.of("one", "left")),
                occurrence(key, "1", "1", false,
                        List.of("one", "right")));
        final ModuleTreeResult different = module("two",
                occurrence(key, "1", "2", true,
                        List.of("two", "left")),
                occurrence(key, "2", "2", false,
                        List.of("two", "right")));

        assertThat(new ModuleVersionAnalyzer()
                .analyze(same)).isEmpty();
        assertThat(new ModuleVersionAnalyzer()
                .analyze(different)).singleElement()
                .satisfies(issue -> {
                    assertThat(issue.getSelectedVersion())
                            .isEqualTo("2");
                    assertThat(issue.getPaths())
                            .hasSize(2);
                });
    }

    @Test
    void managedSinglePathContributesTwoVersionSources() {
        final DependencyKey key = new DependencyKey(
                "g", "managed", "jar", "");
        final ModuleTreeResult module = module("managed",
                occurrence(key, "1", "2", "1", "2",
                        true, List.of("managed", "leaf")));

        final List<VersionMediationIssue> issues =
                new ModuleVersionAnalyzer().analyze(module);
        assertThat(issues).hasSize(1);
        assertThat(issues.get(0).getSelectedVersion())
                .isEqualTo("2");
        assertThat(issues.get(0).getPaths()).hasSize(1);
        final VersionPath versionPath = issues.get(0)
                .getPaths().get(0);
        assertThat(versionPath.getResolvedVersion())
                .isEqualTo("2");
        assertThat(versionPath.getScope())
                .isEqualTo("compile");
        assertThat(versionPath.getEvidence())
                .extracting(VersionEvidence::getSource,
                        VersionEvidence::getVersion)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                VersionEvidenceSource
                                        .DEPENDENCY_PATH,
                                "1"),
                        org.assertj.core.groups.Tuple.tuple(
                                VersionEvidenceSource
                                        .DEPENDENCY_MANAGEMENT,
                                "2"));
    }

    @Test
    void versionPathRetainsOccurrenceResolutionAndScope() {
        final DependencyOccurrence occurrence =
                new DependencyOccurrence(
                        new DependencyKey(
                                "g", "a", "jar", ""),
                        new OccurrenceVersions(
                                "1", "", "1", "2"),
                        new OccurrenceScopes(
                                "runtime", "compile"),
                        null, new OccurrenceSelection(
                                false, "conflict"),
                        List.of("module", "dependency"),
                        false);

        final VersionPath path = VersionPath.from(occurrence);

        assertThat(path.getResolvedVersion()).isEqualTo("2");
        assertThat(path.getScope()).isEqualTo("runtime");
        assertThat(path.isSelected()).isFalse();
        assertThat(path.getReason()).isEqualTo("conflict");
    }

    @Test
    void duplicateSameVersionDoesNotCreateConflict() {
        final DependencyKey key = new DependencyKey(
                "g", "duplicate", "jar", "");
        final ModuleTreeResult module = module("duplicate",
                occurrence(key, "1", "1", true,
                        List.of("duplicate", "left")),
                occurrence(key, "1", "1", false,
                        "duplicate",
                        List.of("duplicate", "right")));

        assertThat(new ModuleVersionAnalyzer()
                .analyze(module)).isEmpty();
    }

    @Test
    void conflictPreservesEveryDistinctDependencyChain() {
        final DependencyKey key = new DependencyKey(
                "g", "paths", "jar", "");
        final ModuleTreeResult module = module("paths",
                occurrence(key, "2", "2", true,
                        List.of("paths", "selected")),
                occurrence(key, "1", "2", false,
                        List.of("paths", "left", "leaf")),
                occurrence(key, "1", "2", false,
                        List.of("paths", "right", "leaf")));

        assertThat(new ModuleVersionAnalyzer()
                .analyze(module)).singleElement()
                .satisfies(issue -> assertThat(
                        issue.getPaths())
                        .extracting(VersionPath::getPath)
                        .containsExactly(
                                List.of("paths", "left", "leaf"),
                                List.of("paths", "right", "leaf"),
                                List.of("paths", "selected")));
    }

    @Test
    void typeAndClassifierRemainSeparateKeys() {
        final ModuleTreeResult module = module("module",
                occurrence(new DependencyKey(
                                "g", "a", "jar", ""),
                        "1", "2", true,
                        List.of("m", "jar")),
                occurrence(new DependencyKey(
                                "g", "a", "test-jar",
                                "tests"),
                        "2", "2", true,
                        List.of("m", "tests")));

        assertThat(new ModuleVersionAnalyzer()
                .analyze(module)).isEmpty();
    }

    @Test
    void reportsSelectedCrossModuleDivergence() {
        final DependencyKey key = new DependencyKey(
                "g", "a", "jar", "");
        final ReactorDescriptor descriptor =
                new ReactorDescriptor(Path.of("pom.xml"),
                        "g:r:1", List.of(
                        Path.of("pom.xml")), List.of());
        final ReactorTreeResult reactor =
                new ReactorTreeResult(descriptor,
                        List.of(module("one",
                                        occurrence(key, "1", "1",
                                                true,
                                                List.of("one", "a"))),
                                module("two",
                                        occurrence(key, "2", "2",
                                                true,
                                                List.of("two", "a")))),
                        ReactorStatus.SUCCESS, "");

        assertThat(new CrossModuleVersionAnalyzer()
                .analyze(reactor)).singleElement()
                .satisfies(issue -> assertThat(
                        issue.getVersions()).hasSize(2));
    }

    @Test
    void crossModuleIssueRetainsAllPathAndManagementEvidence() {
        final DependencyKey key = new DependencyKey(
                "g", "managed", "jar", "");
        final ReactorDescriptor descriptor =
                new ReactorDescriptor(Path.of("pom.xml"),
                        "g:r:1", List.of(
                        Path.of("pom.xml")), List.of());
        final ModuleTreeResult one = module("one",
                occurrence(key, "1", "2", "1", "2",
                        true, List.of("one", "managed")),
                occurrenceWithScope(key, "3", "3", "2",
                        false, List.of("one", "other"),
                        "runtime"));
        final ModuleTreeResult two = module("two",
                occurrence(key, "4", "4", true,
                        List.of("two", "direct")));
        final ReactorTreeResult reactor =
                new ReactorTreeResult(descriptor,
                        List.of(one, two),
                        ReactorStatus.SUCCESS, "");

        assertThat(new CrossModuleVersionAnalyzer()
                .analyze(reactor)).singleElement()
                .satisfies(issue -> {
                    assertThat(issue.getVersions())
                            .extracting(CrossModuleVersion::getVersion)
                            .containsExactly("2", "4");
                    final CrossModuleVersion first =
                            issue.getVersions().get(0);
                    assertThat(first.getPaths())
                            .extracting(VersionPath::getPath)
                            .containsExactly(
                                    List.of("one", "managed"),
                                    List.of("one", "other"));
                    assertThat(first.getPaths().get(0)
                            .getEvidence())
                            .extracting(VersionEvidence::getSource)
                            .containsExactly(
                                    VersionEvidenceSource
                                            .DEPENDENCY_PATH,
                                    VersionEvidenceSource
                                            .DEPENDENCY_MANAGEMENT);
                    assertThat(first.getPaths())
                            .extracting(
                                    VersionPath::getResolvedVersion,
                                    VersionPath::getScope)
                            .containsExactly(
                                    org.assertj.core.groups.Tuple
                                            .tuple("2", "compile"),
                                    org.assertj.core.groups.Tuple
                                            .tuple("2", "runtime"));
                });
    }

    private ModuleTreeResult module(
            final String coordinate,
            final DependencyOccurrence... occurrences) {
        return new ModuleTreeResult(
                Path.of(coordinate, "pom.xml"),
                coordinate, List.of(occurrences),
                true, "");
    }

    private DependencyOccurrence occurrence(
            final DependencyKey key,
            final String requested,
            final String selectedVersion,
            final boolean selected,
            final List<String> path) {
        return occurrence(key, requested, selectedVersion,
                selected, "conflict", path);
    }

    private DependencyOccurrence occurrence(
            final DependencyKey key,
            final String requested,
            final String selectedVersion,
            final boolean selected,
            final String omittedReason,
            final List<String> path) {
        return new DependencyOccurrence(
                key, new OccurrenceVersions(
                requested, "", selectedVersion,
                selectedVersion),
                new OccurrenceScopes("compile", ""),
                null, new OccurrenceSelection(
                selected, selected ? "" : omittedReason),
                path, false);
    }

    private DependencyOccurrence occurrence(
            final DependencyKey key,
            final String requested,
            final String effective,
            final String managedFrom,
            final String selectedVersion,
            final boolean selected,
            final List<String> path) {
        return new DependencyOccurrence(
                key, new OccurrenceVersions(
                requested, managedFrom, effective,
                selectedVersion),
                new OccurrenceScopes("compile", ""),
                null, new OccurrenceSelection(
                selected, selected ? "" : "conflict"),
                path, false);
    }

    private DependencyOccurrence occurrenceWithScope(
            final DependencyKey key,
            final String requested,
            final String effective,
            final String selectedVersion,
            final boolean selected,
            final List<String> path,
            final String scope) {
        return new DependencyOccurrence(
                key, new OccurrenceVersions(
                requested, "", effective,
                selectedVersion),
                new OccurrenceScopes(scope, ""),
                null, new OccurrenceSelection(
                selected, selected ? "" : "conflict"),
                path, false);
    }
}
