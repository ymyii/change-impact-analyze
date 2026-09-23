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
                            "/d",
                            "/v:off",
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
    void windowsRunsGitNativelyAndPreservesRevisionSyntax() {
        final String original = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Windows 11");
            final List<String> result = CommandResolver.resolve(List.of(
                    "git", "rev-parse", "--verify",
                    "release^{commit}"));
            assertThat(result).containsExactly(
                    "git.exe", "rev-parse", "--verify",
                    "release^{commit}");
        } finally {
            if (original != null) {
                System.setProperty("os.name", original);
            }
        }
    }

    @Test
    void windowsEscapesCmdShellMetacharacters() {
        final String original = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Windows 11");
            final List<String> result = CommandResolver.resolve(List.of(
                    "mvn.cmd", "-Dpath=C:\\work dir",
                    "-Drevision=release^2026&candidate"));
            assertThat(result).containsExactly(
                    "cmd.exe", "/d", "/v:off", "/c", "mvn.cmd",
                    "-Dpath=C:\\work dir",
                    "-Drevision=release^^2026^&candidate");
        } finally {
            if (original != null) {
                System.setProperty("os.name", original);
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
