package io.github.changeimpact.analyze.build;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions
        .assertThat;

/**
 * Tests for
 * {@link BuildRunner#discoverModules}.
 */
class BuildRunnerDiscoverTest {

    /** Temporary directory for test. */
    @TempDir
    private Path tempDir;

    @Test
    void findsTargetClasses() throws Exception {
        final Path modA = tempDir.resolve(
                "mod-a/target/classes");
        Files.createDirectories(modA);
        final Path modB = tempDir.resolve(
                "mod-b/target/classes");
        Files.createDirectories(modB);
        final List<ModuleBuildOutput> result =
                BuildRunner.discoverModules(
                        tempDir);
        assertThat(result).hasSize(2);
        assertThat(result)
                .extracting(
                        ModuleBuildOutput
                                ::getClassesDir)
                .containsExactlyInAnyOrder(
                        modA, modB);
    }

    @Test
    void excludesTestClasses()
            throws Exception {
        final Path main = tempDir.resolve(
                "mod/target/classes");
        Files.createDirectories(main);
        final Path test = tempDir.resolve(
                "mod/target/test-classes");
        Files.createDirectories(test);
        final List<ModuleBuildOutput> result =
                BuildRunner.discoverModules(
                        tempDir);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)
                .getClassesDir())
                .isEqualTo(main);
    }

    @Test
    void modulePathIsParentOfTarget()
            throws Exception {
        final Path classes = tempDir.resolve(
                "sub/mod/target/classes");
        Files.createDirectories(classes);
        final List<ModuleBuildOutput> result =
                BuildRunner.discoverModules(
                        tempDir);
        assertThat(result).hasSize(1);
        assertThat(result.get(0)
                .getModulePath())
                .isEqualTo(tempDir.resolve(
                        "sub/mod"));
    }

    @Test
    void emptyWorkspaceReturnsEmpty()
            throws Exception {
        final List<ModuleBuildOutput> result =
                BuildRunner.discoverModules(
                        tempDir);
        assertThat(result).isEmpty();
    }

    @Test
    void ignoresTargetDirItself()
            throws Exception {
        Files.createDirectories(
                tempDir.resolve("target"));
        final List<ModuleBuildOutput> result =
                BuildRunner.discoverModules(
                        tempDir);
        assertThat(result).isEmpty();
    }

    @Test
    void nestedModulesFound()
            throws Exception {
        final Path root = tempDir.resolve(
                "root/target/classes");
        Files.createDirectories(root);
        final Path child = tempDir.resolve(
                "root/child/target/classes");
        Files.createDirectories(child);
        final List<ModuleBuildOutput> result =
                BuildRunner.discoverModules(
                        tempDir);
        assertThat(result).hasSize(2);
    }
}
