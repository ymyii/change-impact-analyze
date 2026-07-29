package io.github.dependencyanalysis.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

/** Maven argument safety tests. */
class MavenArgumentsTest {

    /** Temporary repository root. */
    @TempDir
    private Path repository;

    @Test
    void resolvesRelativeSettingsPath() {
        assertThat(MavenArguments.validate(
                List.of("-s", "config/settings.xml",
                        "-Pdev", "-DskipTests"),
                repository)).containsExactly(
                "-s", repository.resolve(
                        "config/settings.xml")
                        .toString(),
                "-Pdev", "-DskipTests");
    }

    @Test
    void resolvesAttachedSettingsPaths() {
        assertThat(MavenArguments.validate(
                List.of("-sconfig/settings.xml",
                        "-gs=global/settings.xml"),
                repository)).containsExactly(
                "-s" + repository.resolve(
                        "config/settings.xml"),
                "-gs" + repository.resolve(
                        "global/settings.xml"));
    }

    @Test
    void rejectsControlledOptions() {
        assertThatThrownBy(() ->
                MavenArguments.validate(
                        List.of("-DoutputType=graphml"),
                        repository))
                .isInstanceOf(
                        IllegalArgumentException.class);
    }

    @Test
    void rejectsAttachedPomAndModuleSelection() {
        for (String argument : List.of(
                "-fother.xml", "-f=other.xml",
                "-plmodule-a", "-rfmodule-b",
                "--resume-from=module-c")) {
            assertThatThrownBy(() ->
                    MavenArguments.validate(
                            List.of(argument), repository))
                    .as(argument)
                    .isInstanceOf(
                            IllegalArgumentException.class);
        }
    }

    @Test
    void keepsNonSelectionMavenFailureOptions() {
        assertThat(MavenArguments.validate(
                List.of("-fae", "-ff", "-fn"),
                repository)).containsExactly(
                "-fae", "-ff", "-fn");
    }

    @Test
    void rejectsLifecycleGoal() {
        assertThatThrownBy(() ->
                MavenArguments.validate(
                        List.of("package"), repository))
                .isInstanceOf(
                        IllegalArgumentException.class);
    }
}
