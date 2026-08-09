package io.github.dependencyanalysis.models.jdk;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the common engine is packaged as an unshaded ordinary JAR. */
class JdkPackagingIT {

    @Test
    void packagesOnlyCommonEngineClasses() throws Exception {
        final Path jar = Path.of(System.getProperty("model.jar"));
        try (JarFile archive = new JarFile(jar.toFile())) {
            final List<String> entries = archive.stream()
                    .map(value -> value.getName()).toList();

            assertThat(entries).contains(
                    "io/github/dependencyanalysis/models/jdk/JdkModels.class",
                    "io/github/dependencyanalysis/models/jdk/"
                            + "JdkModelDefinition.class");
            assertThat(entries).noneMatch(value ->
                    value.endsWith("jdk-models.tsv")
                            || value.startsWith(
                                    "io/github/dependencyanalysis/"
                                            + "models/jdk8/")
                            || value.startsWith(
                                    "io/github/dependencyanalysis/analyzer/")
                            || value.startsWith("com/ibm/wala/"));
        }
    }
}
