package io.github.dependencyanalysis.runtime;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Built-in Dependency Evidence Plugin repository packaging contract tests. */
class ArtifactPathPluginPackagingTest {

    /** Plugin version supplied by Maven. */
    private static final String VERSION = System.getProperty(
            "cia.artifactPathPluginVersion");

    /** Repository ZIP resource. */
    private static final String RESOURCE =
            "/maven/plugin-repositories/"
                    + "dependency-analyzer-artifact-path-maven-plugin-"
                    + VERSION + "-repository.zip";

    /** Repository artifact base path. */
    private static final String ARTIFACT_BASE =
            "repository/io/github/dependencyanalysis/"
                    + "dependency-analyzer-artifact-path-maven-plugin/"
                    + VERSION + "/"
                    + "dependency-analyzer-artifact-path-maven-plugin-"
                    + VERSION;

    /** Java 8 class major version. */
    private static final int JAVA_EIGHT_MAJOR = 52;

    /** Class-file magic. */
    private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;

    @Test
    void bundlesRepositoryZipWithJavaEightSelfContainedPlugin()
            throws Exception {
        final List<String> files = new ArrayList<>();
        byte[] pluginJar = null;
        String pluginPom = null;
        try (InputStream resource = required(RESOURCE);
             ZipInputStream zip = new ZipInputStream(resource)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    files.add(entry.getName());
                    if (entry.getName().equals(ARTIFACT_BASE + ".jar")) {
                        pluginJar = zip.readAllBytes();
                    } else if (entry.getName().equals(
                            ARTIFACT_BASE + ".pom")) {
                        pluginPom = new String(
                                zip.readAllBytes(), StandardCharsets.UTF_8);
                    }
                }
                zip.closeEntry();
            }
        }

        assertThat(files).containsExactlyInAnyOrder(
                ARTIFACT_BASE + ".jar", ARTIFACT_BASE + ".pom");
        assertThat(pluginPom).contains("<version>" + VERSION + "</version>");
        assertThat(pluginJar).isNotNull();
        assertPluginJar(pluginJar);
    }

    private void assertPluginJar(final byte[] pluginJar) throws Exception {
        final List<String> entries = new ArrayList<>();
        int maximumBaseClassMajor = -1;
        try (JarInputStream jar = new JarInputStream(
                new ByteArrayInputStream(pluginJar))) {
            JarEntry entry;
            while ((entry = jar.getNextJarEntry()) != null) {
                entries.add(entry.getName());
                if (entry.getName().endsWith(".class")
                        && !entry.getName().startsWith("META-INF/versions/")) {
                    final DataInputStream data = new DataInputStream(jar);
                    assertThat(data.readInt()).isEqualTo(CLASS_FILE_MAGIC);
                    data.readUnsignedShort();
                    maximumBaseClassMajor = Math.max(
                            maximumBaseClassMajor,
                            data.readUnsignedShort());
                }
            }
        }
        assertThat(maximumBaseClassMajor)
                .isPositive().isLessThanOrEqualTo(JAVA_EIGHT_MAJOR);
        assertThat(entries)
                .contains("META-INF/maven/plugin.xml")
                .anyMatch(value -> value.startsWith(
                        "io/github/dependencyanalysis/maven/internal/jackson/"))
                .noneMatch(value -> value.startsWith("org/apache/maven/"))
                .noneMatch(value -> value.startsWith("org/eclipse/aether/"))
                .noneMatch(value -> value.startsWith(
                        "com/fasterxml/jackson/core/"));
    }

    private InputStream required(final String resource) {
        final InputStream input = getClass().getResourceAsStream(resource);
        assertThat(input).as(resource).isNotNull();
        return input;
    }
}
