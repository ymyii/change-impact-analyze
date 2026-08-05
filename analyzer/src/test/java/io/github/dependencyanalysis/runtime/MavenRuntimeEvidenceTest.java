package io.github.dependencyanalysis.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests Maven evidence uses the selected runtime and actual version. */
class MavenRuntimeEvidenceTest {

    @Test
    void userRuntimeSourceDoesNotClaimEmbeddedVersionOrExposePath() {
        final MavenRuntimeDescriptor runtime = new MavenRuntimeDescriptor(
                MavenRuntimeSource.USER_CONFIGURED,
                Path.of("/opt/apache-maven-3.9.16/bin/mvn"),
                MavenVersion.parse("Apache Maven 3.9.16"),
                null, Path.of("config"));

        assertThat(MavenRuntimeEvidence.source(runtime))
                .isEqualTo("source=USER_CONFIGURED")
                .doesNotContain("3.6.3")
                .doesNotContain("/opt/");
        assertThat(MavenRuntimeEvidence.version(runtime.getVersion()))
                .isEqualTo("3.9.16")
                .doesNotContain("EOL");
    }

    @Test
    void eolWarningFollowsActualProbedVersionOnly() {
        assertThat(MavenRuntimeEvidence.version(
                MavenVersion.parse("Apache Maven 3.6.3")))
                .isEqualTo("3.6.3; Maven 3.6.3 is EOL");
    }
}
