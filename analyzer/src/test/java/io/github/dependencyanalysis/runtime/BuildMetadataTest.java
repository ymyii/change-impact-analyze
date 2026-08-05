package io.github.dependencyanalysis.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Maven-filtered build metadata tests. */
class BuildMetadataTest {

    @Test
    void generatedMetadataMatchesMavenVersions() {
        assertThat(BuildMetadata.getAnalyzerVersion())
                .isEqualTo(System.getProperty("cia.analyzerVersion"));
        assertThat(BuildMetadata.getArtifactPathPluginVersion())
                .isEqualTo(System.getProperty(
                        "cia.artifactPathPluginVersion"));
    }

    @Test
    void generatedVersionsUseSupportedSemVer() {
        assertThat(BuildMetadata.getAnalyzerVersion())
                .matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-SNAPSHOT)?");
        assertThat(BuildMetadata.getArtifactPathPluginVersion())
                .matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-SNAPSHOT)?");
    }
}
