package io.github.changeimpact.analyze.workspace;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link WorkspaceSideInfo}.
 */
class WorkspaceSideInfoTest {

    @Test
    void builderSetsAllFields() {
        final Path p = Paths.get("/tmp/ws");
        final WorkspaceSideInfo info =
                new WorkspaceSideInfo.Builder()
                        .side(
                                WorkspaceSide
                                        .BASELINE)
                        .path(p)
                        .commit("abc123")
                        .whetherTemporary(true)
                        .build();
        assertThat(info.getSide())
                .isEqualTo(
                        WorkspaceSide.BASELINE);
        assertThat(info.getPath())
                .isEqualTo(p);
        assertThat(info.getCommit())
                .isEqualTo("abc123");
        assertThat(info.isWhetherTemporary())
                .isTrue();
    }

    @Test
    void whetherTemporaryDefaultsFalse() {
        final WorkspaceSideInfo info =
                new WorkspaceSideInfo.Builder()
                        .side(
                                WorkspaceSide
                                        .TARGET)
                        .path(Paths.get("/x"))
                        .commit("def")
                        .build();
        assertThat(info.isWhetherTemporary())
                .isFalse();
    }

    @Test
    void toStringContainsFields() {
        final WorkspaceSideInfo info =
                new WorkspaceSideInfo.Builder()
                        .side(
                                WorkspaceSide
                                        .CURRENT)
                        .path(Paths.get("/p"))
                        .commit("aaa")
                        .whetherTemporary(false)
                        .build();
        final String s = info.toString();
        assertThat(s).contains("CURRENT");
        assertThat(s).contains("/p");
        assertThat(s).contains("aaa");
    }
}
