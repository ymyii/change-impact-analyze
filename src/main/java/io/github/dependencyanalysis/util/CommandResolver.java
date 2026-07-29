package io.github.dependencyanalysis.util;

import java.util.ArrayList;
import java.util.List;

// Wiki: wiki/rules/process-command-resolution.md - 跨平台命令规则
/**
 * Resolves command lists for the
 * current operating system.
 * On Windows wraps with cmd.exe /c
 * to enable PATHEXT resolution.
 */
public final class CommandResolver {

    private CommandResolver() {
    }

    /**
     * Resolves a command list for the
     * current platform.
     *
     * @param cmd original command tokens
     * @return resolved command tokens
     */
    public static List<String> resolve(
            final List<String> cmd) {
        if (!isWindows()) {
            return cmd;
        }
        final List<String> wrapped =
                new ArrayList<>();
        wrapped.add("cmd.exe");
        wrapped.add("/c");
        wrapped.addAll(cmd);
        return wrapped;
    }

    /**
     * Detects whether the current OS
     * is Windows.
     *
     * @return true if Windows
     */
    static boolean isWindows() {
        final String os = System
                .getProperty("os.name",
                        "")
                .toLowerCase();
        return os.contains("win");
    }
}
