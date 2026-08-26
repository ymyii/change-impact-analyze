package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.reactor.PomDescriptor;
import io.github.dependencyanalysis.reactor.ReactorDescriptor;
import io.github.dependencyanalysis.reactor.RepositoryInventory;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Occurrence-aware tree diff domain tests. */
class TreeDiffEngineTest {

    /** Module POM. */
    private static final Path POM = Path.of("pom.xml");

    @Test
    void classifiesVersionAndScopeOncePerDependencyKey() {
        final DependencyKey key = new DependencyKey(
                "example", "client", "jar", "");
        final ModuleTreeResult baseline = module(List.of(
                occurrence(key, "1", "compile", "",
                        "example:app:jar:1",
                        "example:client:jar:1:compile"),
                occurrence(key, "1", "compile", "",
                        "example:app:jar:1",
                        "example:bridge:jar:1:compile",
                        "example:client:jar:1:compile")));
        final ModuleTreeResult target = module(List.of(
                occurrence(key, "2", "runtime", "",
                        "example:app:jar:1",
                        "example:client:jar:2:runtime")));

        final TreeDiffModuleResult result = diff(baseline, target)
                .modules().get(0);

        assertThat(result.metrics()).isEqualTo(
                new TreeDiffMetrics(1, 0, 0, 0, 1));
        assertThat(result.dependencies()).singleElement().satisfies(row -> {
            assertThat(row.baseChangeType()).isEqualTo(
                    TreeDependencyBaseChangeType.VERSION_CHANGED);
            assertThat(row.scopeChanged()).isTrue();
            assertThat(row.baseline().direct()).isTrue();
            assertThat(row.target().direct()).isTrue();
            assertThat(row.chains()).hasSize(2)
                    .extracting(TreeDiffChainRow::changeType)
                    .containsExactlyInAnyOrder(TreeChainChangeType.UNCHANGED,
                            TreeChainChangeType.REMOVED);
        });
    }

    @Test
    void managedFromChangeDoesNotChangeResolvedClassification() {
        final DependencyKey key = new DependencyKey(
                "example", "managed", "jar", "");
        final ModuleTreeResult baseline = module(List.of(
                occurrence(key, "2", "compile", "1",
                        "example:app:jar:1",
                        "example:managed:jar:2:compile")));
        final ModuleTreeResult target = module(List.of(
                occurrence(key, "2", "compile", "1.5",
                        "example:app:jar:1",
                        "example:managed:jar:2:compile")));

        final TreeDependencyDiffRecord result = diff(baseline, target)
                .modules().get(0).dependencies().get(0);

        assertThat(result.baseChangeType()).isEqualTo(
                TreeDependencyBaseChangeType.RESOLVED_UNCHANGED);
        assertThat(result.scopeChanged()).isFalse();
        assertThat(result.chains()).singleElement().satisfies(chain -> {
            assertThat(chain.changeType()).isEqualTo(
                    TreeChainChangeType.UNCHANGED);
            assertThat(chain.baseline().managedFromVersion())
                    .isEqualTo("1");
            assertThat(chain.target().managedFromVersion())
                    .isEqualTo("1.5");
        });
    }

    @Test
    void pathIdentityIncludesTypeAndClassifierButIgnoresVersionAndScope() {
        final DependencyKey key = new DependencyKey(
                "example", "leaf", "jar", "");
        final ModuleTreeResult baseline = module(List.of(
                occurrence(key, "1", "compile", "",
                        "example:app:jar:1",
                        "example:bridge:test-jar:tests:1:compile",
                        "example:leaf:jar:1:compile")));
        final ModuleTreeResult samePath = module(List.of(
                occurrence(key, "2", "runtime", "",
                        "example:app:jar:2",
                        "example:bridge:test-jar:tests:9:runtime",
                        "example:leaf:jar:2:runtime")));
        final ModuleTreeResult changedType = module(List.of(
                occurrence(key, "2", "runtime", "",
                        "example:app:jar:2",
                        "example:bridge:jar:tests:9:runtime",
                        "example:leaf:jar:2:runtime")));

        assertThat(diff(baseline, samePath).modules().get(0)
                .dependencies().get(0).chains())
                .singleElement().extracting(TreeDiffChainRow::changeType)
                .isEqualTo(TreeChainChangeType.UNCHANGED);
        assertThat(diff(baseline, changedType).modules().get(0)
                .dependencies().get(0).chains())
                .extracting(TreeDiffChainRow::changeType)
                .containsExactlyInAnyOrder(TreeChainChangeType.REMOVED,
                        TreeChainChangeType.ADDED);
    }

    @Test
    void unavailableModuleDoesNotBecomeDependencyRemoval() {
        final DependencyKey key = new DependencyKey(
                "example", "client", "jar", "");
        final ModuleTreeResult baseline = module(List.of(
                occurrence(key, "1", "compile", "",
                        "example:app:jar:1",
                        "example:client:jar:1:compile")));
        final ModuleTreeResult target = new ModuleTreeResult(POM,
                "example:app:1", List.of(), false,
                "Maven collection failed");

        final TreeDiffModuleResult result = diff(baseline, target)
                .modules().get(0);

        assertThat(result.comparisonStatus()).isEqualTo(
                TreeDiffComparisonStatus.UNAVAILABLE);
        assertThat(result.metrics()).isNull();
        assertThat(result.dependencies()).isEmpty();
    }

    private TreeDiffReactorResult diff(
            final ModuleTreeResult baseline,
            final ModuleTreeResult target) {
        final ReactorDescriptor descriptor = new ReactorDescriptor(
                POM, "example:app:1", List.of(POM), List.of());
        final RepositoryInventory inventory = new RepositoryInventory(
                List.of(descriptor), Map.of(POM,
                new PomDescriptor(POM, "example:app:1", "jar",
                        List.of(), List.of(), "")));
        final TreeDiffSideReactor left = new TreeDiffSideReactor(
                TreeDiffSideState.PRESENT, descriptor, inventory,
                new ReactorTreeResult(descriptor, List.of(baseline),
                        ReactorStatus.SUCCESS, ""), "");
        final TreeDiffSideReactor right = new TreeDiffSideReactor(
                TreeDiffSideState.PRESENT, descriptor, inventory,
                new ReactorTreeResult(descriptor, List.of(target),
                        ReactorStatus.SUCCESS, ""), "");
        return new TreeDiffEngine().diff("pom.xml", left, right);
    }

    private ModuleTreeResult module(
            final List<DependencyOccurrence> occurrences) {
        return new ModuleTreeResult(POM, "example:app:1",
                occurrences, true, "");
    }

    private DependencyOccurrence occurrence(
            final DependencyKey key,
            final String resolved,
            final String scope,
            final String managedFrom,
            final String... path) {
        return new DependencyOccurrence(key,
                new OccurrenceVersions(managedFrom.isBlank()
                        ? resolved : managedFrom,
                        managedFrom, resolved, resolved),
                new OccurrenceScopes(scope, ""), null,
                new OccurrenceSelection(true, ""),
                List.of(path), false);
    }
}
