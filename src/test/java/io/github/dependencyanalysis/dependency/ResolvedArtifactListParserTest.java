package io.github.dependencyanalysis.dependency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests Maven absolute artifact list parsing. */
class ResolvedArtifactListParserTest {

    /** Temporary module directory. */
    @TempDir
    private Path temporaryDirectory;

    @Test
    void parsesClassifierAndCanonicalAbsolutePaths() throws Exception {
        final Path plain = Files.createFile(
                temporaryDirectory.resolve("plain.jar"));
        final Path classified = Files.createFile(
                temporaryDirectory.resolve("classified.jar"));
        final Path list = temporaryDirectory.resolve("artifacts.txt");
        Files.writeString(list, "The following files have been resolved:\n"
                + "g:a:jar:1.0:compile:" + plain
                + " -- module example.name (auto)\n"
                + "g:native:jar:linux-x86_64:2.0:runtime:"
                + classified + "\n");

        final List<ResolvedArtifact> result =
                ResolvedArtifactListParser.parse(list);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(value ->
                        value.getArtifact().getClassifier())
                .containsExactly("", "linux-x86_64");
        assertThat(result).extracting(ResolvedArtifact::getPath)
                .containsExactly(plain.toRealPath(),
                        classified.toRealPath());
    }
}
