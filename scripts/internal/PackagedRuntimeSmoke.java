package scripts.internal;

import io.github.dependencyanalysis.runtime.MavenDependencyPluginRuntime;
import io.github.dependencyanalysis.runtime.MavenDependencyPluginRuntimeManager;
import io.github.dependencyanalysis.runtime.MavenExecutionResult;
import io.github.dependencyanalysis.runtime.MavenExecutor;
import io.github.dependencyanalysis.runtime.MavenRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.MavenRuntimeManager;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Wiki: wiki/runbooks/version-and-distribution.md - Packaged empty-cache combined-goal smoke
/** Executes the packaged runtime against an isolated Maven local repository. */
public final class PackagedRuntimeSmoke {

    private PackagedRuntimeSmoke() {
    }

    /** Executes the smoke test. */
    public static void main(final String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException(
                    "Expected Artifact Path Plugin version");
        }
        final String pluginVersion = arguments[0];
        final Path root = Files.createTempDirectory(
                "dependency-analyzer-packaged-smoke-");
        try {
            execute(root, pluginVersion);
        } finally {
            deleteTree(root);
        }
    }

    private static void execute(final Path root,
                                final String pluginVersion) throws Exception {
        final Path project = Files.createDirectories(
                root.resolve("project"));
        Files.writeString(project.resolve("pom.xml"),
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">"
                        + "<modelVersion>4.0.0</modelVersion>"
                        + "<groupId>fixture</groupId>"
                        + "<artifactId>packaged-smoke</artifactId>"
                        + "<version>1</version></project>",
                StandardCharsets.UTF_8);
        final Path local = root.resolve("empty-local");
        for (String oldVersion : List.of("1.0.0", "1.0.1")) {
            final Path old = local.resolve("io/github/dependencyanalysis/"
                    + "dependency-analyzer-artifact-path-maven-plugin/"
                    + oldVersion);
            Files.createDirectories(old);
            Files.writeString(old.resolve(
                            "dependency-analyzer-artifact-path-maven-plugin-"
                                    + oldVersion + ".jar"),
                    "intentionally stale", StandardCharsets.UTF_8);
        }

        final Path config = root.resolve("config");
        final MavenRuntimeDescriptor maven =
                new MavenRuntimeManager().prepare(null, config, null);
        final MavenDependencyPluginRuntime plugin =
                new MavenDependencyPluginRuntimeManager().prepare(
                        config,
                        List.of("-Dmaven.repo.local=" + local), null);
        if (!pluginVersion.equals(plugin.getArtifactPathPluginVersion())) {
            throw new IllegalStateException(
                    "Packaged Plugin version mismatch");
        }
        final List<String> mavenArguments = new ArrayList<>(
                plugin.getMavenArguments());
        mavenArguments.add("-X");
        mavenArguments.add("-B");
        mavenArguments.add("-f");
        mavenArguments.add(project.resolve("pom.xml").toString());
        mavenArguments.add(plugin.getGoal());
        mavenArguments.add("-DoutputFile=tree.graphml");
        mavenArguments.add("-DoutputType=graphml");
        mavenArguments.add(plugin.getArtifactPathGoal());
        mavenArguments.add("-Dcia.dependencyGraphFileName=tree.graphml");
        mavenArguments.add(
                "-Dcia.resolvedArtifactsFileName=artifacts.json");
        final MavenExecutionResult result = new MavenExecutor().execute(
                maven, project, mavenArguments);
        final String output = result.getCombinedOutput();
        if (result.getExitCode() != 0
                || !output.contains(
                "(f) dependencyGraphFileName = tree.graphml")
                || !output.contains(
                "Artifact Path Plugin implementation=graphml-v2")
                || !output.contains("version=" + pluginVersion)
                || !output.contains("sha512="
                + plugin.getArtifactPathJarSha512())
                || !Files.isRegularFile(project.resolve("artifacts.json"))) {
            throw new IllegalStateException(
                    "Packaged combined-goal smoke failed: " + output);
        }
        final String manifest = Files.readString(
                project.resolve("artifacts.json"));
        if (!manifest.contains("\"schemaVersion\" : 2")
                || manifest.contains("\"module\"")
                || manifest.contains("\"scope\"")) {
            throw new IllegalStateException(
                    "Packaged Artifact Path Schema v2 mismatch: "
                            + manifest);
        }
        System.out.println("Packaged runtime smoke verified: Plugin "
                + pluginVersion + "; sha512="
                + plugin.getArtifactPathJarSha512());
    }

    private static void deleteTree(final Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
