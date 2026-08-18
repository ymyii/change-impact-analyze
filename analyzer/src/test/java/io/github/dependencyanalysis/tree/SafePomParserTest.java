package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.reactor.PomDescriptor;
import io.github.dependencyanalysis.reactor.SafePomParser;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions
        .assertThat;

/** Maven profile activation tests. */
class SafePomParserTest {

    /** Temporary repository. */
    @TempDir
    private Path repository;

    @Test
    void supportsJdkOsFileAndLongPropertyOptions()
            throws Exception {
        final String osName = System.getProperty(
                "os.name");
        final String osArch = System.getProperty(
                "os.arch");
        Files.writeString(repository.resolve(
                ".activation"), "active");
        Files.writeString(repository.resolve("pom.xml"), """
                <project>
                  <modelVersion>4.0.0</modelVersion>
                  <groupId>test</groupId><artifactId>root</artifactId>
                  <version>1</version><profiles>
                    <profile><id>jdk</id><activation><jdk>[1,)</jdk>
                    </activation><modules><module>jdk-module</module>
                    </modules></profile>
                    <profile><id>os</id><activation><os>
                      <name>%s</name><arch>%s</arch></os></activation>
                      <modules><module>os-module</module></modules>
                    </profile>
                    <profile><id>file</id><activation><file>
                      <exists>.activation</exists></file></activation>
                      <modules><module>file-module</module></modules>
                    </profile>
                    <profile><id>property</id><activation><property>
                      <name>mode</name><value>!dev</value>
                    </property></activation><modules>
                      <module>property-module</module></modules>
                    </profile>
                  </profiles>
                </project>
                """.formatted(osName, osArch));

        final PomDescriptor descriptor =
                new SafePomParser(List.of(
                        "--define=mode=prod"))
                        .parse(repository,
                                Path.of("pom.xml"));

        assertThat(descriptor.getActiveModules())
                .containsExactly("file-module",
                        "jdk-module", "os-module",
                        "property-module");
    }

    @Test
    void inheritsCiFriendlyVersionFromLocalParent() throws Exception {
        Files.createDirectories(repository.resolve("module"));
        Files.writeString(repository.resolve("pom.xml"), """
                <project><modelVersion>4.0.0</modelVersion>
                  <groupId>test</groupId><artifactId>root</artifactId>
                  <version>${revision}</version>
                  <properties><revision>3.1.0-SNAPSHOT</revision></properties>
                </project>
                """);
        Files.writeString(repository.resolve("module/pom.xml"), """
                <project><modelVersion>4.0.0</modelVersion>
                  <parent><groupId>test</groupId><artifactId>root</artifactId>
                    <version>${revision}</version></parent>
                  <artifactId>module</artifactId>
                </project>
                """);

        final PomDescriptor descriptor = new SafePomParser(List.of())
                .parse(repository, Path.of("module/pom.xml"));

        assertThat(descriptor.getCoordinate())
                .isEqualTo("test:module:3.1.0-SNAPSHOT");
    }

    @Test
    void pomPropertyDoesNotActivatePropertyProfile() throws Exception {
        Files.writeString(repository.resolve("pom.xml"), """
                <project><modelVersion>4.0.0</modelVersion>
                  <groupId>test</groupId><artifactId>root</artifactId>
                  <version>1</version>
                  <properties><pom-only>true</pom-only></properties>
                  <profiles><profile><id>pom-property</id><activation>
                    <property><name>pom-only</name></property>
                  </activation><modules><module>unexpected</module></modules>
                  </profile></profiles>
                </project>
                """);

        final PomDescriptor descriptor = new SafePomParser(List.of())
                .parse(repository, Path.of("pom.xml"));

        assertThat(descriptor.getActiveModules()).isEmpty();
    }
}
