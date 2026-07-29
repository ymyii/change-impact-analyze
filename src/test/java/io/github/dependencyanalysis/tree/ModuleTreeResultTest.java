package io.github.dependencyanalysis.tree;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatNullPointerException;

/** Module result role contract tests. */
class ModuleTreeResultTest {

    @Test
    void legacyConstructorDefaultsToRequested() {
        final ModuleTreeResult result =
                new ModuleTreeResult(Path.of("pom.xml"),
                        "g:a:1", List.of(), true, "");

        assertThat(result.getRole())
                .isEqualTo(ModuleAnalysisRole.REQUESTED);
    }

    @Test
    void retainsExplicitInclusionRole() {
        final ModuleTreeResult result =
                new ModuleTreeResult(Path.of("pom.xml"),
                        "g:a:1", List.of(), true, "",
                        ModuleAnalysisRole.DEPENDENCY);

        assertThat(result.getRole())
                .isEqualTo(ModuleAnalysisRole.DEPENDENCY);
        assertThat(result.getRole().getInclusionReason())
                .contains("REQUESTED module");
    }

    @Test
    void rejectsMissingInclusionRole() {
        assertThatNullPointerException().isThrownBy(() ->
                new ModuleTreeResult(Path.of("pom.xml"),
                        "g:a:1", List.of(), true, "",
                        null));
    }
}
