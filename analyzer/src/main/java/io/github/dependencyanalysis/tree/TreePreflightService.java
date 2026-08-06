package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.cli
        .DependencyAnalyzerCli;
import io.github.dependencyanalysis.cli.MavenArguments;
import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.preflight
        .PreflightCheck;
import io.github.dependencyanalysis.preflight
        .PreflightContext;
import io.github.dependencyanalysis.preflight
        .PreflightOutcome;
import io.github.dependencyanalysis.preflight
        .PreflightPlan;
import io.github.dependencyanalysis.preflight
        .PreflightReport;
import io.github.dependencyanalysis.preflight
        .PreflightRequirement;
import io.github.dependencyanalysis.preflight
        .PreflightRunner;
import io.github.dependencyanalysis.preflight
        .PreflightScope;
import io.github.dependencyanalysis.preflight
        .SimplePreflightCheck;
import io.github.dependencyanalysis.runtime
        .CommandRunDirectory;
import io.github.dependencyanalysis.runtime
        .MavenDependencyPluginRuntime;
import io.github.dependencyanalysis.runtime
        .MavenDependencyPluginRuntimeManager;
import io.github.dependencyanalysis.runtime
        .MavenExecutionResult;
import io.github.dependencyanalysis.runtime
        .MavenExecutor;
import io.github.dependencyanalysis.runtime
        .MavenRuntimeDescriptor;
import io.github.dependencyanalysis.runtime
        .MavenRuntimeManager;
import io.github.dependencyanalysis.runtime
        .MavenRuntimeEvidence;
import io.github.dependencyanalysis.runtime.MavenVersion;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Builds the tree command-level check graph. */
final class TreePreflightService {

    /** Maximum retained Maven output lines. */
    private static final int MAVEN_TAIL_LINES = 100;

    /** Snapshot key. */
    static final String SNAPSHOT = "tree.snapshot";

    /** Maven arguments key. */
    static final String MAVEN_ARGS = "tree.maven-args";

    /** Maven runtime key. */
    static final String MAVEN_RUNTIME =
            "tree.maven-runtime";

    /** Maven Dependency Plugin runtime key. */
    static final String DEPENDENCY_PLUGIN_RUNTIME =
            "tree.dependency-plugin-runtime";

    /** Inventory key. */
    static final String INVENTORY = "tree.inventory";

    /** Command-owned workspace and temp run. */
    static final String COMMAND_RUN = "tree.command-run";

    /** Root options. */
    private final DependencyAnalyzerCli root;

    /** Analysis path. */
    private final File path;

    /** Ref. */
    private final String ref;

    /** Output. */
    private final File output;

    /** Scopes. */
    private final Set<String> scopes;

    /** Diagnostics. */
    private final DiagnosticLog diagnostics;

    /**
     * Creates the service.
     *
     * @param rootCommand root command
     * @param analysisPath analysis path
     * @param localRef local ref
     * @param outputDir output directory
     * @param includedScopes scopes
     * @param diagnosticLog command diagnostics
     */
    TreePreflightService(
            final DependencyAnalyzerCli rootCommand,
            final File analysisPath,
            final String localRef,
            final File outputDir,
            final Set<String> includedScopes,
            final DiagnosticLog diagnosticLog) {
        root = rootCommand;
        path = analysisPath;
        ref = localRef;
        output = outputDir;
        scopes = includedScopes;
        diagnostics = diagnosticLog;
    }

    /**
     * Runs command-level checks and prepares shared context.
     *
     * @param context prepared context
     * @param pluginVersion dependency plugin override
     * @return command-level report
     */
    PreflightReport runCommandChecks(
            final PreflightContext context,
            final String pluginVersion) {
        return new PreflightRunner().run(
                new PreflightPlan(commandChecks(
                        pluginVersion)),
                context);
    }

    private List<PreflightCheck> commandChecks(
            final String pluginVersion) {
        final List<PreflightCheck> checks =
                new ArrayList<>();
        checks.add(command("tree.path",
                List.of(), context -> {
                    final Path value = path
                            .toPath().toAbsolutePath()
                            .normalize();
                    if (!Files.isDirectory(value)) {
                        return PreflightOutcome.fail(
                                "Analysis path is not a directory",
                                value.toString(), "");
                    }
                    return PreflightOutcome.pass(
                            "Analysis path is available",
                            value.toString());
                }));
        checks.add(command("tree.snapshot",
                List.of("tree.path"),
                context -> {
                    try {
                        final CommandRunDirectory run =
                                context.own(
                                        new CommandRunDirectory(
                                                root
                                                        .getConfigDir()
                                                        .toPath(),
                                                "tree"));
                        context.put(COMMAND_RUN, run);
                        final Path workspaceDirectory =
                                run.getWorkspaceDirectory();
                        final RepositorySnapshot snapshot =
                                context.own(
                                        new GitSnapshotProvider()
                                                .open(path
                                                        .toPath(),
                                                        ref,
                                                        workspaceDirectory));
                        context.put(SNAPSHOT, snapshot);
                        return PreflightOutcome.pass(
                                "Repository snapshot prepared",
                                snapshot.getCommit()
                                        + "; dirty="
                                        + snapshot.isDirty());
                    } catch (Exception exception) {
                        return PreflightOutcome.fail(
                                "Repository snapshot failed",
                                exception.getMessage(), "");
                    }
                }));
        checks.add(command("tree.output",
                List.of("tree.path"),
                context -> checkOutput()));
        checks.add(command("tree.scope-filter",
                List.of("tree.path"),
                context -> checkScopes()));
        checks.add(command("tree.maven-arguments",
                List.of("tree.snapshot"),
                context -> {
                    final RepositorySnapshot snapshot =
                            context.get(SNAPSHOT,
                                    RepositorySnapshot.class);
                    final List<String> arguments =
                            MavenArguments.validate(
                                    root.getMavenArguments(),
                                    snapshot.getRoot());
                    context.put(MAVEN_ARGS, arguments);
                    return PreflightOutcome.pass(
                            "Maven arguments are safe",
                            arguments.toString());
                }));
        checks.add(command("tree.maven-runtime",
                List.of("tree.path"),
                context -> prepareRuntime(context)));
        checks.add(command("tree.maven-version",
                List.of("tree.maven-runtime",
                        "tree.snapshot"),
                context -> probeVersion(context)));
        checks.add(command("tree.dependency-plugin-runtime",
                List.of("tree.maven-version",
                        "tree.maven-arguments"),
                context -> prepareDependencyPlugin(
                        context, pluginVersion)));
        checks.add(command("tree.reactor-inventory",
                List.of("tree.snapshot",
                        "tree.dependency-plugin-runtime",
                        "tree.scope-filter"),
                context -> prepareInventory(context)));
        return checks;
    }

    private SimplePreflightCheck command(
            final String id,
            final List<String> dependencies,
            final SimplePreflightCheck.Action action) {
        return new SimplePreflightCheck(
                id, "tree", PreflightScope.COMMAND,
                path.getAbsolutePath(),
                PreflightRequirement.REQUIRED,
                dependencies, action);
    }

    private PreflightOutcome checkOutput() {
        final Path value = output.toPath()
                .toAbsolutePath().normalize();
        if (Files.exists(value)
                && !Files.isDirectory(value)) {
            return PreflightOutcome.fail(
                    "Output exists and is not a directory",
                    value.toString(), "");
        }
        Path parent = Files.exists(value)
                ? value : value.getParent();
        while (parent != null
                && !Files.exists(parent)) {
            parent = parent.getParent();
        }
        if (parent == null
                || !Files.isDirectory(parent)
                || !Files.isWritable(parent)) {
            return PreflightOutcome.fail(
                    "Output directory cannot be created",
                    value.toString(), "");
        }
        return PreflightOutcome.pass(
                "Output directory can be replaced safely",
                value.toString());
    }

    private PreflightOutcome checkScopes() {
        final Set<String> allowed = Set.of(
                "compile", "runtime", "provided",
                "test", "system");
        if (scopes.isEmpty()
                || !allowed.containsAll(scopes)) {
            return PreflightOutcome.fail(
                    "Scope filter is invalid",
                    scopes.toString(), "");
        }
        return PreflightOutcome.pass(
                "Scope filter is valid",
                scopes.toString());
    }

    private PreflightOutcome prepareRuntime(
            final PreflightContext context) {
        final File javaHome = root
                .getJavaHome();
        if (javaHome != null
                && !javaHome.isDirectory()) {
            return PreflightOutcome.fail(
                    "Maven JAVA_HOME is not a directory",
                    javaHome.getAbsolutePath(), "");
        }
        final File executable = root.getMaven();
        final MavenRuntimeDescriptor runtime =
                new MavenRuntimeManager().prepare(
                        executable == null ? null
                                : executable.toPath(),
                        root.getConfigDir().toPath(),
                        javaHome == null ? null
                                : javaHome.toPath());
        context.put(MAVEN_RUNTIME, runtime);
        return PreflightOutcome.pass(
                "Maven runtime prepared",
                MavenRuntimeEvidence.source(runtime));
    }

    private PreflightOutcome probeVersion(
            final PreflightContext context)
            throws Exception {
        final MavenRuntimeDescriptor runtime =
                context.get(MAVEN_RUNTIME,
                        MavenRuntimeDescriptor.class);
        final MavenExecutionResult result =
                new MavenExecutor().execute(
                        runtime,
                        context.get(SNAPSHOT,
                                RepositorySnapshot.class)
                                .getRoot(),
                        List.of("--version"), diagnostics,
                        DiagnosticContext.of(
                                "preflight", "maven-version"),
                        MAVEN_TAIL_LINES);
        if (result.getExitCode() != 0) {
            return PreflightOutcome.fail(
                    "Maven executable cannot run",
                    result.getCombinedOutput(), "");
        }
        final MavenVersion version =
                MavenVersion.parse(
                        result.getCombinedOutput());
        if (!version.isSupported()) {
            return PreflightOutcome.fail(
                    "Unsupported Maven version",
                    version
                            + "; required >=3.6.3 and <4.0.0",
                    "");
        }
        context.put(MAVEN_RUNTIME,
                runtime.withVersion(version));
        return PreflightOutcome.pass(
                "Maven version is supported",
                MavenRuntimeEvidence.version(version));
    }

    private PreflightOutcome prepareDependencyPlugin(
            final PreflightContext context,
            final String pluginVersion) {
        final MavenRuntimeDescriptor runtime = context.get(
                MAVEN_RUNTIME,
                MavenRuntimeDescriptor.class);
        final MavenDependencyPluginRuntime plugin = context.own(
                new MavenDependencyPluginRuntimeManager()
                        .prepare(runtime.getConfigDir(),
                                context.get(MAVEN_ARGS,
                                        List.class),
                                pluginVersion));
        context.put(DEPENDENCY_PLUGIN_RUNTIME, plugin);
        return PreflightOutcome.pass(
                "Maven Dependency Plugin runtime prepared",
                (plugin.isEmbedded() ? "embedded:"
                        : "override:")
                        + plugin.getVersion());
    }

    private PreflightOutcome prepareInventory(
            final PreflightContext context)
            throws Exception {
        final RepositoryInventory inventory =
                new ReactorInventoryBuilder().build(
                        context.get(SNAPSHOT,
                                RepositorySnapshot.class),
                        context.get(MAVEN_ARGS,
                                List.class));
        context.put(INVENTORY, inventory);
        if (inventory.getReactors().isEmpty()) {
            return PreflightOutcome.fail(
                    "No active Maven POM matches analysis path",
                    inventory.getAnalysisPath().toString(), "");
        }
        return PreflightOutcome.pass(
                "Maven reactors discovered",
                "reactors=" + inventory
                        .getReactors().size());
    }

}
