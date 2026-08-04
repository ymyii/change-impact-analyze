package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.preflight
        .PreflightReport;
import io.github.dependencyanalysis.runtime
        .MavenRuntimeDescriptor;
import io.github.dependencyanalysis.runtime
        .MavenRuntimeSource;
import io.github.dependencyanalysis.runtime.MavenVersion;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions
        .assertThat;

/** Incremental session analysis issue aggregation tests. */
class TreeReportSessionIssueTest {

    /** Temporary report directory. */
    @TempDir
    private Path temporary;

    @Test
    void publishesWithoutReactorPreflightAndAggregatesIssues()
            throws Exception {
        final TreeReportSession session =
                new TreeReportRenderer().start(
                        metadata(), 2,
                        temporary.resolve("report"));

        session.publish(result("a/pom.xml",
                ReactorStatus.SUCCESS, ""));
        session.publish(result("b/pom.xml",
                ReactorStatus.FAILED,
                "Maven failed"));
        session.complete();

        assertThat(session.getState()).isEqualTo(
                TreeReportState.COMPLETED_WITH_ISSUES);
        assertThat(session.getAnalysisIssues())
                .singleElement()
                .satisfies(issue -> {
                    assertThat(issue.getReactorId())
                            .isEqualTo("b/pom.xml");
                    assertThat(issue.getStatus())
                            .isEqualTo(ReactorStatus.FAILED);
                    assertThat(issue.getReason())
                            .isEqualTo("Maven failed");
                });
        assertThat(java.util.Arrays.stream(
                        ReactorReportSummary.class
                                .getDeclaredFields())
                .map(java.lang.reflect.Field::getType))
                .doesNotContain(PreflightReport.class);
    }

    private ReactorTreeResult result(
            final String id,
            final ReactorStatus status,
            final String reason) {
        final Path pom = Path.of(id);
        return new ReactorTreeResult(
                new ReactorDescriptor(pom, "g:a:1",
                        List.of(pom), List.of()),
                List.of(), status, reason);
    }

    private TreeReportMetadata metadata() {
        final RepositorySnapshot snapshot =
                new RepositorySnapshot(temporary,
                        temporary, "current checkout",
                        "abc", "main", false, () -> { });
        final MavenRuntimeDescriptor runtime =
                new MavenRuntimeDescriptor(
                        MavenRuntimeSource.EMBEDDED,
                        temporary.resolve("mvn"),
                        MavenVersion.parse("3.6.3"),
                        null, temporary, "sha");
        return new TreeReportMetadata(snapshot,
                runtime, List.of(), Set.of("compile"),
                new PreflightReport(List.of()));
    }
}
