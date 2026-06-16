package io.github.changeimpact.analyze.bytecode;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions
        .assertThat;

/**
 * Tests for
 * {@link BytecodeDiffException}.
 */
class BytecodeDiffExceptionTest {

    @Test
    void messageContainsJarAndClass() {
        final Path jar =
                Path.of("/tmp/foo.jar");
        final BytecodeDiffException ex =
                new BytecodeDiffException(
                        jar,
                        "com/Foo",
                        "bad class");
        final String msg = ex.getMessage();
        assertThat(msg)
                .contains(jar.toString());
        assertThat(msg)
                .contains("com/Foo");
        assertThat(msg)
                .contains("bad class");
    }

    @Test
    void messageWithoutClassName() {
        final Path jar =
                Path.of("/tmp/foo.jar");
        final BytecodeDiffException ex =
                new BytecodeDiffException(
                        jar, null,
                        "corrupt jar");
        final String msg = ex.getMessage();
        assertThat(msg)
                .contains(jar.toString());
        assertThat(msg)
                .doesNotContain("class=");
    }

    @Test
    void gettersReturnValues() {
        final Path jar =
                Path.of("/tmp/foo.jar");
        final BytecodeDiffException ex =
                new BytecodeDiffException(
                        jar,
                        "com/Foo",
                        "bad class");
        assertThat(ex.getJarPath())
                .isEqualTo(jar);
        assertThat(ex.getClassName())
                .isEqualTo("com/Foo");
    }

    @Test
    void classNameNullWhenNotSet() {
        final Path jar =
                Path.of("/tmp/foo.jar");
        final BytecodeDiffException ex =
                new BytecodeDiffException(
                        jar, null,
                        "corrupt");
        assertThat(ex.getClassName())
                .isNull();
    }

    @Test
    void causeChainPreserved() {
        final Path jar =
                Path.of("/tmp/foo.jar");
        final RuntimeException cause =
                new RuntimeException("root");
        final BytecodeDiffException ex =
                new BytecodeDiffException(
                        jar, "com/Foo",
                        "fail", cause);
        assertThat(ex.getCause())
                .isSameAs(cause);
    }

    @Test
    void constructorWithoutCause() {
        final Path jar =
                Path.of("/tmp/foo.jar");
        final BytecodeDiffException ex =
                new BytecodeDiffException(
                        jar, "com/Foo",
                        "fail");
        assertThat(ex.getCause())
                .isNull();
    }
}
