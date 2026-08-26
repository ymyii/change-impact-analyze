package io.github.dependencyanalysis.tree;

import picocli.CommandLine.Option;

import java.io.File;
import java.util.Set;

/** Options shared by dependency tree operations. */
final class TreeCommonOptions {

    /** Default dependency scopes. */
    static final String DEFAULT_SCOPES =
            "compile,runtime,provided,system";

    /** Analysis path inside a Git repository. */
    @Option(names = {"-p", "--path"},
            description = "Analysis path inside a Git repository."
                    + " Defaults to current directory.")
    private File path;

    /** Output directory. */
    @Option(names = {"-o", "--output"}, required = true,
            description = "HTML report output directory.")
    private File output;

    /** Scope CSV. */
    @Option(names = {"-s", "--scopes"},
            defaultValue = DEFAULT_SCOPES,
            description = "Included dependency scopes.")
    private String scopes;

    /** Dependency plugin override. */
    @Option(names = {"-d", "--dependency-plugin-version"},
            description = "Optional maven-dependency-plugin version.")
    private String pluginVersion;

    /** @return configured or current working directory */
    File path() {
        return path == null
                ? new File(System.getProperty("user.dir")) : path;
    }

    /** @return report output directory */
    File output() {
        return output;
    }

    /** @return raw scope expression */
    String scopes() {
        return scopes;
    }

    /** @return optional Plugin version */
    String pluginVersion() {
        return pluginVersion;
    }

    /** @return normalized stable scope set */
    Set<String> parsedScopes() {
        return TreeScopeParser.parse(scopes);
    }
}
