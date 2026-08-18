package io.github.dependencyanalysis.tree;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Classpath Evidence Schema v1 parser tests. */
class ClasspathEvidenceJsonParserTest {

    /** Fixture Java major. */
    private static final int JAVA_MAJOR = 17;

    /** Temporary evidence roots. */
    @TempDir
    private Path temporary;

    @Test
    void parsesCanonicalOrderedClasspathEvidence() throws Exception {
        final Path module = Files.createDirectory(temporary.resolve("module"))
                .toRealPath();
        final Path classes = Files.createDirectory(module.resolve("classes"))
                .toRealPath();
        final Path file = evidence(module, classes, 0, "PROJECT", "");

        final ModuleClasspathEvidence parsed =
                ClasspathEvidenceJsonParser.parse(file);

        assertThat(parsed.javaMajor()).isEqualTo(JAVA_MAJOR);
        assertThat(parsed.moduleDirectory()).isEqualTo(module);
        assertThat(parsed.entries()).singleElement().satisfies(entry -> {
            assertThat(entry.order()).isZero();
            assertThat(entry.origin().name()).isEqualTo("PROJECT");
            assertThat(entry.physicalPath()).isEqualTo(classes);
        });
    }

    @Test
    void rejectsNonContiguousOrderAndInvalidSourceScope() throws Exception {
        final Path module = Files.createDirectory(
                temporary.resolve("invalid-module")).toRealPath();
        final Path classes = Files.createDirectory(
                module.resolve("classes")).toRealPath();
        final Path file = evidence(module, classes, 1, "PROJECT", "compile");

        assertThatThrownBy(() -> ClasspathEvidenceJsonParser.parse(file))
                .isInstanceOf(java.io.IOException.class)
                .hasMessageContaining("classpath order must be contiguous");
    }

    private Path evidence(
            final Path module,
            final Path entry,
            final int order,
            final String origin,
            final String scope) throws Exception {
        final Path file = temporary.resolve("evidence-" + order + ".json");
        Files.writeString(file, """
                {
                  "schemaVersion": 1,
                  "javaMajor": 17,
                  "module": {
                    "groupId": "demo", "artifactId": "app",
                    "type": "jar", "extension": "jar", "classifier": "",
                    "version": "1", "baseVersion": "1"
                  },
                  "moduleDirectory": "%s",
                  "entries": [{
                    "order": %d, "origin": "%s",
                    "coordinates": {
                      "groupId": "demo", "artifactId": "app",
                      "type": "jar", "extension": "jar", "classifier": "",
                      "version": "1", "baseVersion": "1"
                    },
                    "scope": "%s", "absolutePath": "%s"
                  }],
                  "issues": []
                }
                """.formatted(module, order, origin, scope, entry));
        return file;
    }
}
