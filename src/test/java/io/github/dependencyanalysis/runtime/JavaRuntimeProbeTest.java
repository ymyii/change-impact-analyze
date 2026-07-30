package io.github.dependencyanalysis.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests the impact target JDK 8 preflight contract. */
class JavaRuntimeProbeTest {

    /** Java 8 major. */
    private static final int JAVA_8 = 8;

    /** Java 17 major. */
    private static final int JAVA_17 = 17;

    /** Temporary directory. */
    @TempDir
    private Path temporary;

    @Test
    void missingJavaHomeIsRejected() {
        assertThatThrownBy(() ->
                new JavaRuntimeProbe().probeJdk8(null))
                .isInstanceOf(JavaRuntimeException.class)
                .hasMessageContaining("requires --java-home");
    }

    @Test
    void analyzerJdk17IsRejectedAsTarget() {
        assertThatThrownBy(() -> new JavaRuntimeProbe()
                .probeJdk8(Path.of(
                        System.getProperty("java.home"))))
                .isInstanceOf(JavaRuntimeException.class)
                .hasMessageContaining("JDK 8 only");
    }

    @Test
    void missingJavacIsRejectedBeforeProbe() throws Exception {
        final Path home = temporary.resolve("missing-javac");
        final Path java = executable(home, "java");
        writeScript(java, "exit 0");

        assertThatThrownBy(() ->
                new JavaRuntimeProbe().probeJdk8(home))
                .isInstanceOf(JavaRuntimeException.class)
                .hasMessageContaining("javac is unavailable");
    }

    @Test
    void missingRtJarIsRejected() throws Exception {
        final Path home = temporary.resolve("missing-rt");
        final Path runtime = home.resolve("jre");
        final Path java = executable(home, "java");
        final Path javac = executable(home, "javac");
        writeScript(java, "echo '    java.version = 1.8.0_402'"
                + "\necho '    java.home = " + runtime + "'"
                + "\necho '    sun.boot.class.path = "
                + runtime.resolve("lib/missing.jar") + "'");
        writeScript(javac, "exit 0");

        assertThatThrownBy(() ->
                new JavaRuntimeProbe().probeJdk8(home))
                .isInstanceOf(JavaRuntimeException.class)
                .hasMessageContaining("rt.jar");
    }

    @Test
    void parsesLegacyAndModernJavaVersions() {
        assertThat(JavaRuntimeProbe.parseMajor("1.8.0_402"))
                .isEqualTo(JAVA_8);
        assertThat(JavaRuntimeProbe.parseMajor("17.0.19"))
                .isEqualTo(JAVA_17);
    }

    private Path executable(
            final Path home,
            final String name) throws Exception {
        final Path path = home.resolve("bin").resolve(name);
        Files.createDirectories(path.getParent());
        return path;
    }

    private void writeScript(
            final Path path,
            final String body) throws Exception {
        Files.writeString(path, "#!/bin/sh\n" + body + "\n");
        assertThat(path.toFile().setExecutable(true))
                .isTrue();
    }
}
