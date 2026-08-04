package io.github.dependencyanalysis.runtime;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Built-in Artifact Path Plugin packaging contract tests. */
class ArtifactPathPluginPackagingTest {

    /** Plugin resource base. */
    private static final String RESOURCE_BASE =
            "/maven/artifact-path-plugin/"
                    + "dependency-analyzer-artifact-path-maven-plugin-1.0.0";

    /** Java 8 class major version. */
    private static final int JAVA_EIGHT_MAJOR = 52;

    /** Class-file magic. */
    private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;

    @Test
    void bundlesJavaEightPluginWithoutMavenImplementations()
            throws Exception {
        final List<String> entries = new ArrayList<>();
        int maximumBaseClassMajor = -1;
        try (InputStream resource = required(RESOURCE_BASE + ".jar");
             JarInputStream jar = new JarInputStream(resource)) {
            JarEntry entry;
            while ((entry = jar.getNextJarEntry()) != null) {
                entries.add(entry.getName());
                if (entry.getName().endsWith(".class")
                        && !entry.getName().startsWith(
                        "META-INF/versions/")) {
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
                .isPositive()
                .isLessThanOrEqualTo(JAVA_EIGHT_MAJOR);
        assertThat(entries)
                .contains("META-INF/maven/plugin.xml")
                .anyMatch(value -> value.startsWith(
                        "io/github/dependencyanalysis/maven/internal/jackson/"))
                .noneMatch(value -> value.startsWith("org/apache/maven/"))
                .noneMatch(value -> value.startsWith("org/eclipse/aether/"))
                .noneMatch(value -> value.startsWith(
                        "com/fasterxml/jackson/core/"));
        required(RESOURCE_BASE + ".pom").close();
        required(RESOURCE_BASE + ".jar.sha512").close();
        required(RESOURCE_BASE + ".pom.sha512").close();
    }

    private InputStream required(final String resource) {
        final InputStream input = getClass().getResourceAsStream(resource);
        assertThat(input).as(resource).isNotNull();
        return input;
    }
}
