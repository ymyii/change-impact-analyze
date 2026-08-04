package io.github.dependencyanalysis.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions
        .assertThat;

/** Embedded Maven offline runtime integration test. */
class MavenRuntimeManagerIT {

    /** Temporary config directory. */
    @TempDir
    private Path config;

    @Test
    void embeddedMavenRunsWithoutPathSelection()
            throws Exception {
        final MavenRuntimeDescriptor runtime =
                new MavenRuntimeManager().prepare(
                        null, config, null);

        final MavenExecutionResult result =
                new MavenExecutor().execute(runtime,
                        config, List.of("--version"));

        assertThat(result.getExitCode()).isZero();
        assertThat(result.getCombinedOutput())
                .contains("Apache Maven 3.6.3");
    }
}
