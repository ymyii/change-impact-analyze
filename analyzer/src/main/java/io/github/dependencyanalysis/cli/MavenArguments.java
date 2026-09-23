package io.github.dependencyanalysis.cli;

import io.github.dependencyanalysis.diagnostic.LogVerbosity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Validates and resolves user Maven argument tokens. */
public final class MavenArguments {

    /** Length of short options containing two letters. */
    private static final int TWO_LETTER_OPTION_LENGTH = 3;

    /** Exact options controlled by the tool. */
    private static final Set<String> FORBIDDEN =
            Set.of("-f", "--file", "-pl",
                    "--projects", "-am", "-amd",
                    "--also-make",
                    "--also-make-dependents",
                    "-rf", "--resume-from",
                    "-n", "--non-recursive",
                    "-q", "--quiet");

    /** Controlled properties. */
    private static final List<String>
            FORBIDDEN_PROPERTIES = List.of(
            "-doutputfile", "-doutputtype",
            "-dappendoutput", "-dverbose",
            "-dtokens");

    private MavenArguments() {
    }

    /**
     * Validates and resolves settings paths.
     *
     * @param tokens raw argument tokens
     * @param repositoryRoot repository root
     * @return safe process tokens
     */
    public static List<String> validate(
            final List<String> tokens,
            final Path repositoryRoot) {
        final List<String> result =
                new ArrayList<>();
        boolean expectsSettingsPath = false;
        for (String token : tokens) {
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException(
                        "Maven argument must not be blank");
            }
            if (expectsSettingsPath) {
                result.add(resolvePath(token,
                        repositoryRoot));
                expectsSettingsPath = false;
                continue;
            }
            final String lower = token
                    .toLowerCase(Locale.ROOT);
            if (isForbiddenOption(lower)) {
                throw new IllegalArgumentException(
                        "Maven argument is controlled"
                                + " by the tool: "
                                + token);
            }
            for (String property
                    : FORBIDDEN_PROPERTIES) {
                if (lower.equals(property)
                        || lower.startsWith(
                        property + "=")) {
                    throw new IllegalArgumentException(
                            "Maven property is controlled"
                                    + " by the tool: "
                                    + token);
                }
            }
            if (lower.equals("-s")
                    || lower.equals("--settings")
                    || lower.equals("-gs")
                    || lower.equals(
                    "--global-settings")) {
                result.add(token);
                expectsSettingsPath = true;
                continue;
            }
            if (isInlinePath(lower,
                    "--settings=")) {
                result.add("--settings="
                        + resolvePath(token.substring(
                        token.indexOf('=') + 1),
                        repositoryRoot));
                continue;
            }
            if (isInlinePath(lower,
                    "--global-settings=")) {
                result.add("--global-settings="
                        + resolvePath(token.substring(
                        token.indexOf('=') + 1),
                        repositoryRoot));
                continue;
            }
            if (isAttachedShortPath(lower,
                    "-gs")) {
                result.add("-gs" + resolvePath(
                        attachedPath(token,
                                TWO_LETTER_OPTION_LENGTH),
                        repositoryRoot));
                continue;
            }
            if (isAttachedShortPath(lower,
                    "-s")) {
                result.add("-s" + resolvePath(
                        attachedPath(token, 2),
                        repositoryRoot));
                continue;
            }
            if (!token.startsWith("-")) {
                throw new IllegalArgumentException(
                        "Maven lifecycle phases and goals"
                                + " are not allowed: "
                                + token);
            }
            result.add(token);
        }
        if (expectsSettingsPath) {
            throw new IllegalArgumentException(
                    "Maven settings option requires a path");
        }
        return List.copyOf(result);
    }

    /**
     * Maps verbosity to Maven and deduplicates equivalent diagnostic flags.
     * @param arguments validated command arguments
     * @param verbosity Analyzer verbosity
     * @return Maven arguments with native logging options
     */
    public static List<String> withVerbosity(
            final List<String> arguments,
            final LogVerbosity verbosity) {
        final List<String> result = new ArrayList<>();
        boolean debug = false;
        boolean errors = false;
        for (String argument : arguments) {
            if (argument.equals("-X") || argument.equals("--debug")) {
                if (!debug) {
                    result.add(argument);
                }
                debug = true;
            } else if (argument.equals("-e") || argument.equals("--errors")) {
                if (!errors) {
                    result.add(argument);
                }
                errors = true;
            } else {
                result.add(argument);
            }
        }
        if (!debug && verbosity.includes(
                LogVerbosity.DEBUG)) {
            result.add("-X");
        }
        return result;
    }

    private static boolean isInlinePath(
            final String value,
            final String prefix) {
        return value.startsWith(prefix)
                && value.length() > prefix.length();
    }

    private static boolean isForbiddenOption(
            final String value) {
        if (FORBIDDEN.contains(value)) {
            return true;
        }
        for (String forbidden : FORBIDDEN) {
            if (value.startsWith(forbidden + "=")) {
                return true;
            }
        }
        return isAttachedForbiddenShort(value);
    }

    private static boolean isAttachedForbiddenShort(
            final String value) {
        if (value.startsWith("-pl")
                && value.length()
                > TWO_LETTER_OPTION_LENGTH) {
            return true;
        }
        if (value.startsWith("-rf")
                && value.length()
                > TWO_LETTER_OPTION_LENGTH) {
            return true;
        }
        return value.startsWith("-f")
                && value.length() > 2
                && !Set.of("-fae", "-ff", "-fn")
                .contains(value);
    }

    private static boolean isAttachedShortPath(
            final String value,
            final String option) {
        return value.startsWith(option)
                && value.length() > option.length()
                && !value.startsWith("--");
    }

    private static String attachedPath(
            final String token,
            final int optionLength) {
        String value = token.substring(optionLength);
        if (value.startsWith("=")) {
            value = value.substring(1);
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(
                    "Maven settings option requires a path");
        }
        return value;
    }

    private static String resolvePath(
            final String value,
            final Path repositoryRoot) {
        final Path path = Path.of(value);
        if (path.isAbsolute()) {
            return path.normalize().toString();
        }
        return repositoryRoot.resolve(path)
                .normalize().toString();
    }
}
