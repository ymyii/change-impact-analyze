package io.github.dependencyanalysis.cli;

import io.github.dependencyanalysis.impact.ImpactCommand;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;
import io.github.dependencyanalysis.runtime
        .MavenRuntimeManager;
import io.github.dependencyanalysis.tree.TreeCommand;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ScopeType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

// Wiki: wiki/project/dependency-analyzer.md - 产品 CLI 主入口
// Wiki: wiki/architecture/dependency-analysis-pipelines.md - CLI dispatch
// Wiki: wiki/implementation/cli-preflight-diagnostics.md - CLI boundary
/** Dependency Analyzer root command. */
@Command(
        name = "dependency-analyzer",
        mixinStandardHelpOptions = true,
        versionProvider = DependencyAnalyzerVersionProvider.class,
        description = "Analyze Maven dependencies.",
        subcommands = {
            ImpactCommand.class,
            TreeCommand.class
        }
)
public final class DependencyAnalyzerCli
        implements Callable<Integer> {

    /** User-selected Maven executable. */
    @Option(
            names = {"-m", "--maven"},
            scope = ScopeType.INHERIT,
            description = "Maven 3.6.3-3.x executable path."
    )
    private File maven;

    /** User project JAVA_HOME. */
    @Option(
            names = {"-j", "--java-home"},
            scope = ScopeType.INHERIT,
            description = "JDK home for Maven and compilation;"
                    + " impact requires target JDK 8."
    )
    private File javaHome;

    /** Complete application config directory. */
    @Option(
            names = {"-c", "--config-dir"},
            scope = ScopeType.INHERIT,
            description = "Dependency Analyzer config directory."
    )
    private File configDir;

    /** Safe user Maven arguments. */
    @Option(
            names = {"-a", "--maven-arg"},
            scope = ScopeType.INHERIT,
            description = "One Maven option or property token."
    )
    private List<String> mavenArguments =
            new ArrayList<>();

    /** Repeated verbosity flags. */
    @Option(
            names = {"-v", "--verbose"},
            scope = ScopeType.INHERIT,
            description = "Increase log verbosity: -v DEBUG, -vv TRACE."
    )
    private boolean[] verbose = new boolean[0];

    /** CommandLine instance for root usage output. */
    private CommandLine commandLine;

    @Override
    public Integer call() {
        if (commandLine != null) {
            commandLine.usage(System.err);
        }
        return 1;
    }

    /** @return configured executable, or null */
    public File getMaven() {
        return maven;
    }

    /** @return user project JAVA_HOME, or null */
    public File getJavaHome() {
        return javaHome;
    }

    /**
     * Returns the complete config directory.
     *
     * @return configured or default directory
     */
    public File getConfigDir() {
        if (configDir != null) {
            return configDir;
        }
        return MavenRuntimeManager.defaultConfigDir()
                .toFile();
    }

    /** @return raw Maven argument tokens */
    public List<String> getMavenArguments() {
        return List.copyOf(mavenArguments);
    }

    /** @return selected log verbosity */
    public LogVerbosity getLogVerbosity() {
        return LogVerbosity.fromVerboseCount(
                verbose.length);
    }

    /**
     * Creates configured picocli model.
     *
     * @param root root command
     * @return command line
     */
    public static CommandLine newCommandLine(
            final DependencyAnalyzerCli root) {
        final CommandLine value =
                new CommandLine(root);
        root.commandLine = value;
        value.setCaseInsensitiveEnumValuesAllowed(
                true);
        value.setParameterExceptionHandler(
                (exception, arguments) -> {
                    final CommandLine failed =
                            exception.getCommandLine();
                    failed.getErr().println(
                            exception.getMessage());
                    failed.usage(failed.getErr());
                    return 1;
                });
        return value;
    }

    /**
     * JVM entrypoint.
     *
     * @param args command arguments
     */
    public static void main(final String[] args) {
        final int exitCode = newCommandLine(
                new DependencyAnalyzerCli())
                .execute(args);
        System.exit(exitCode);
    }
}
