package io.github.changeimpact.analyze.workspace;

import org.junit.jupiter.api.Test;

import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WorkspaceResult}.
 */
class WorkspaceResultTest {

    @Test
    void builderSetsBaselineAndTarget() {
        final WorkspaceSideInfo base =
                new WorkspaceSideInfo.Builder()
                        .side(
                                WorkspaceSide
                                        .BASELINE)
                        .path(Paths.get("/b"))
                        .commit("b1")
                        .whetherTemporary(true)
                        .build();
        final WorkspaceSideInfo tgt =
                new WorkspaceSideInfo.Builder()
                        .side(
                                WorkspaceSide
                                        .TARGET)
                        .path(Paths.get("/t"))
                        .commit("t1")
                        .whetherTemporary(false)
                        .build();
        final WorkspaceResult result =
                new WorkspaceResult.Builder()
                        .baseline(base)
                        .target(tgt)
                        .build();
        assertThat(result.getBaseline())
                .isSameAs(base);
        assertThat(result.getTarget())
                .isSameAs(tgt);
    }

    @Test
    void toStringContainsSides() {
        final WorkspaceSideInfo base =
                new WorkspaceSideInfo.Builder()
                        .side(
                                WorkspaceSide
                                        .BASELINE)
                        .path(Paths.get("/b"))
                        .commit("b1")
                        .build();
        final WorkspaceSideInfo tgt =
                new WorkspaceSideInfo.Builder()
                        .side(
                                WorkspaceSide
                                        .TARGET)
                        .path(Paths.get("/t"))
                        .commit("t1")
                        .build();
        final WorkspaceResult result =
                new WorkspaceResult.Builder()
                        .baseline(base)
                        .target(tgt)
                        .build();
        assertThat(result.toString())
                .contains("baseline")
                .contains("target");
    }
}
