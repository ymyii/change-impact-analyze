package io.github.dependencyanalysis.models.jdk8;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the JDK 8 model is packaged as an unshaded ordinary JAR. */
class Jdk8PackagingIT {

    @Test
    void packagesFacadeAndExactCatalogWithoutDependencies()
            throws Exception {
        final Path jar = Path.of(System.getProperty("model.jar"));
        try (JarFile archive = new JarFile(jar.toFile())) {
            final List<String> entries = archive.stream()
                    .map(value -> value.getName()).toList();

            assertThat(entries).contains(
                    "io/github/dependencyanalysis/models/jdk8/"
                            + "Jdk8Models.class",
                    "io/github/dependencyanalysis/models/jdk8/"
                            + "jdk8-models.tsv");
            assertThat(entries).noneMatch(value ->
                    value.equals("io/github/dependencyanalysis/models/jdk/"
                            + "JdkModels.class")
                            || value.startsWith(
                                    "io/github/dependencyanalysis/analyzer/")
                            || value.startsWith("com/ibm/wala/"));
        }
    }
}
