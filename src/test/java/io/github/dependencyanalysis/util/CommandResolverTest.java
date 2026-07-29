package io.github.dependencyanalysis.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions
        .assertThat;

/**
 * Tests for {@link CommandResolver}.
 */
class CommandResolverTest {

    @Test
    void nonWindowsReturnsOriginalCommand() {
        final String original =
                System.getProperty(
                        "os.name");
        try {
            System.setProperty("os.name",
                    "Linux");
            final List<String> cmd =
                    new ArrayList<>();
            cmd.add("mvn");
            cmd.add("compile");
            cmd.add("-B");
            final List<String> result =
                    CommandResolver
                            .resolve(cmd);
            assertThat(result)
                    .isSameAs(cmd);
            assertThat(result)
                    .containsExactly(
                            "mvn",
                            "compile",
                            "-B");
        } finally {
            if (original != null) {
                System.setProperty(
                        "os.name",
                        original);
            }
        }
    }

    @Test
    void windowsWrapsWithCmdExe() {
        final String original =
                System.getProperty(
                        "os.name");
        try {
            System.setProperty("os.name",
                    "Windows 10");
            final List<String> cmd =
                    new ArrayList<>();
            cmd.add("mvn");
            cmd.add("compile");
            cmd.add("-B");
            final List<String> result =
                    CommandResolver
                            .resolve(cmd);
            assertThat(result)
                    .isNotSameAs(cmd);
            assertThat(result)
                    .containsExactly(
                            "cmd.exe",
                            "/c",
                            "mvn",
                            "compile",
                            "-B");
        } finally {
            if (original != null) {
                System.setProperty(
                        "os.name",
                        original);
            }
        }
    }

    @Test
    void isWindowsDetectsCorrectly() {
        final String original =
                System.getProperty(
                        "os.name");
        try {
            System.setProperty("os.name",
                    "Windows 11");
            assertThat(CommandResolver
                    .isWindows()).isTrue();
            System.setProperty("os.name",
                    "Linux");
            assertThat(CommandResolver
                    .isWindows())
                    .isFalse();
            System.setProperty("os.name",
                    "Mac OS X");
            assertThat(CommandResolver
                    .isWindows())
                    .isFalse();
        } finally {
            if (original != null) {
                System.setProperty(
                        "os.name",
                        original);
            }
        }
    }
}
