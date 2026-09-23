package io.github.dependencyanalysis.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// Wiki: wiki/rules/process-command-resolution.md - 跨平台命令规则
/**
 * Resolves command lists for the
 * current operating system.
 * On Windows uses native executables directly
 * and wraps command scripts with cmd.exe.
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
        if (cmd.isEmpty()) {
            return cmd;
        }
        final String executable = cmd.get(0);
        if (isNativeExecutable(executable)) {
            final List<String> nativeCommand = new ArrayList<>(cmd);
            if (executable.equalsIgnoreCase("git")) {
                nativeCommand.set(0, "git.exe");
            }
            return nativeCommand;
        }
        final List<String> wrapped =
                new ArrayList<>();
        wrapped.add("cmd.exe");
        wrapped.add("/d");
        wrapped.add("/v:off");
        wrapped.add("/c");
        for (String token : cmd) {
            wrapped.add(escapeCmdToken(token));
        }
        return wrapped;
    }

    private static boolean isNativeExecutable(
            final String executable) {
        final String normalized = executable
                .replace('\\', '/')
                .toLowerCase(Locale.ROOT);
        final int slash = normalized.lastIndexOf('/');
        final String name = slash >= 0
                ? normalized.substring(slash + 1) : normalized;
        return name.equals("git")
                || name.equals("git.exe")
                || name.equals("java")
                || name.equals("java.exe")
                || name.endsWith(".exe");
    }

    private static String escapeCmdToken(
            final String token) {
        final StringBuilder escaped = new StringBuilder();
        for (int i = 0; i < token.length(); i++) {
            final char value = token.charAt(i);
            if (value == '^' || value == '&' || value == '|'
                    || value == '<' || value == '>'
                    || value == '(' || value == ')'
                    || value == '%') {
                escaped.append('^');
            }
            escaped.append(value);
        }
        return escaped.toString();
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
