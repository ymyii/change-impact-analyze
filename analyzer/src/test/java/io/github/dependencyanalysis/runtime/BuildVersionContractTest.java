package io.github.dependencyanalysis.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Build metadata and tracked version contract tests. */
class BuildVersionContractTest {

    @Test
    void generatedMetadataMatchesMavenVersions() {
        assertThat(BuildMetadata.getAnalyzerVersion())
                .isEqualTo(System.getProperty("cia.analyzerVersion"));
        assertThat(BuildMetadata.getArtifactPathPluginVersion())
                .isEqualTo(System.getProperty(
                        "cia.artifactPathPluginVersion"));
    }

    @Test
    void sourceFileVersionToolVerifiesContractAndSemVerRules()
            throws Exception {
        final Path root = Path.of(System.getProperty(
                "cia.multiModuleProjectDirectory"))
                .toAbsolutePath().normalize();
        final Path java = Path.of(System.getProperty("java.home"),
                "bin", "java");
        final Path tool = root.resolve(
                "scripts/internal/VersionTool.java");

        final Result verify = execute(root, java, tool, "verify");
        final Result selfTest = execute(root, java, tool, "self-test");

        assertThat(verify.exitCode)
                .describedAs(verify.output).isZero();
        assertThat(verify.output)
                .contains("Version contract verified");
        assertThat(selfTest.exitCode)
                .describedAs(selfTest.output).isZero();
        assertThat(selfTest.output)
                .contains("Version tool self-test passed");
    }

    @Test
    void versionBumpRejectsPluginDriftAndSealsNewVersions(
            @TempDir final Path temporaryRoot) throws Exception {
        final Path root = Path.of(System.getProperty(
                "cia.multiModuleProjectDirectory"))
                .toAbsolutePath().normalize();
        copyVersionInputs(root, temporaryRoot);
        final Path java = Path.of(System.getProperty("java.home"),
                "bin", "java");
        final Path tool = root.resolve(
                "scripts/internal/VersionTool.java");
        final Path rootPom = temporaryRoot.resolve("pom.xml");
        final Path contract = temporaryRoot.resolve(
                "build-support/version-contract.properties");
        final Path pluginSource = temporaryRoot.resolve(
                "plugins/artifact-path-resolver/src/main/java/"
                        + "io/github/dependencyanalysis/maven/"
                        + "GraphmlDependencyReader.java");
        final String analyzerVersion = System.getProperty(
                "cia.analyzerVersion");
        final String pluginVersion = System.getProperty(
                "cia.artifactPathPluginVersion");
        final String nextAnalyzerVersion = patch(analyzerVersion);
        final String originalPom = Files.readString(rootPom);
        final String originalContract = Files.readString(contract);
        final String originalPluginSource = Files.readString(pluginSource);

        Files.writeString(pluginSource, originalPluginSource + "\n",
                StandardCharsets.UTF_8);
        final Result rejected = execute(temporaryRoot, java, tool,
                "bump", "--analyzer", "patch");

        assertThat(rejected.exitCode).isNotZero();
        assertThat(rejected.output)
                .contains("--plugin bump is required");
        assertThat(Files.readString(rootPom)).isEqualTo(originalPom);
        assertThat(Files.readString(contract)).isEqualTo(originalContract);

        Files.writeString(pluginSource, originalPluginSource,
                StandardCharsets.UTF_8);
        final Result analyzerBump = execute(temporaryRoot, java, tool,
                "bump", "--analyzer", "patch");

        assertThat(analyzerBump.exitCode)
                .describedAs(analyzerBump.output).isZero();
        assertThat(Files.readString(rootPom))
                .contains("<revision>" + nextAnalyzerVersion
                        + "</revision>")
                .contains("<artifact-path-plugin.version>"
                        + pluginVersion
                        + "</artifact-path-plugin.version>");
        assertThat(Files.readString(contract))
                .contains("current.analyzer.version="
                        + nextAnalyzerVersion)
                .contains("analyzer." + analyzerVersion
                        + ".inputs.sha512=")
                .contains("analyzer." + nextAnalyzerVersion
                        + ".inputs.sha512=");
        assertThat(Files.readString(temporaryRoot.resolve(
                "docs/user-manual.md")))
                .contains("Analyzer release: `"
                        + nextAnalyzerVersion + "`");
        final Result verify = execute(temporaryRoot, java, tool,
                "verify");
        assertThat(verify.exitCode)
                .describedAs(verify.output).isZero();
    }

    private String patch(final String version) {
        final String[] components = version.split("\\.");
        return components[0] + "." + components[1] + "."
                + (Integer.parseInt(components[2]) + 1);
    }

    private Result execute(final Path root,
                           final Path java,
                           final Path tool,
                           final String... command) throws Exception {
        final List<String> arguments = new java.util.ArrayList<>(List.of(
                java.toString(), tool.toString(),
                "--root", root.toString()));
        arguments.addAll(List.of(command));
        final Process process = new ProcessBuilder(arguments)
                .directory(root.toFile())
                .redirectErrorStream(true)
                .start();
        final String output = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        return new Result(process.waitFor(), output);
    }

    private void copyVersionInputs(final Path root,
                                   final Path destination)
            throws IOException {
        for (String input : List.of(
                "pom.xml",
                "analyzer/pom.xml",
                "analyzer/src/main",
                "plugins/pom.xml",
                "plugins/artifact-path-resolver/pom.xml",
                "plugins/artifact-path-resolver/src/main",
                "build-support/version-contract.properties",
                "build-support/artifact-path-plugin-consumer.pom.template",
                "docs/user-manual.md",
                "wiki/project/dependency-analyzer.md",
                "wiki/features/maven-runtime.md",
                "wiki/rules/release-versioning.md",
                "wiki/runbooks/build-test-package.md",
                "wiki/runbooks/version-and-distribution.md")) {
            copy(root.resolve(input), destination.resolve(input));
        }
    }

    private void copy(final Path source, final Path destination)
            throws IOException {
        if (Files.isRegularFile(source)) {
            Files.createDirectories(destination.getParent());
            Files.copy(source, destination,
                    StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        try (var paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                final Path target = destination.resolve(
                        source.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target,
                            StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    private record Result(int exitCode, String output) {
    }
}
