package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.cli
        .MavenArguments;
import io.github.dependencyanalysis.cli
        .DependencyAnalyzerCli;
import io.github.dependencyanalysis.diagnostic
        .DiagnosticCollector;
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
        .JavaRuntimeDescriptor;
import io.github.dependencyanalysis.runtime
        .Jdk8RuntimeProvider;
import io.github.dependencyanalysis.runtime
        .MavenExecutionResult;
import io.github.dependencyanalysis.runtime
        .MavenDependencyPluginRuntime;
import io.github.dependencyanalysis.runtime
        .MavenDependencyPluginRuntimeManager;
import io.github.dependencyanalysis.runtime
        .MavenExecutor;
import io.github.dependencyanalysis.runtime
        .MavenRuntimeDescriptor;
import io.github.dependencyanalysis.runtime
        .MavenRuntimeManager;
import io.github.dependencyanalysis.runtime
        .MavenRuntimeEvidence;
import io.github.dependencyanalysis.runtime
        .MavenVersion;
import io.github.dependencyanalysis.util
        .CommandResolver;
import io.github.dependencyanalysis.workspace
        .WorkspaceManager;
import io.github.dependencyanalysis.workspace
        .WorkspaceResult;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Builds and executes the impact-specific check graph. */
final class ImpactPreflightService {

    /** Prepared analysis path key. */
    static final String ANALYSIS_PATH =
            "impact.analysis-path";

    /** Prepared Git root key. */
    static final String GIT_ROOT = "impact.git-root";

    /** Prepared Maven arguments key. */
    static final String MAVEN_ARGS = "impact.maven-args";

    /** Prepared Maven runtime key. */
    static final String MAVEN_RUNTIME =
            "impact.maven-runtime";

    /** Prepared embedded Maven Dependency Plugin runtime key. */
    static final String DEPENDENCY_PLUGIN_RUNTIME =
            "impact.dependency-plugin-runtime";

    /** Prepared target Java runtime key. */
    static final String JAVA_RUNTIME =
            "impact.java-runtime";

    /** Prepared workspace key. */
    static final String WORKSPACE = "impact.workspace";

    /** Command-owned workspace and temp run. */
    static final String COMMAND_RUN = "impact.command-run";

    /** Root CLI options. */
    private final DependencyAnalyzerCli root;

    /** Analysis path. */
    private final File path;

    /** Baseline ref. */
    private final String baseline;

    /** Target ref. */
    private final String target;

    /** Output. */
    private final File output;

    /** Diagnostics. */
    private final DiagnosticCollector diagnostics;

    /**
     * Creates the service.
     *
     * @param rootCommand root options
     * @param analysisPath analysis directory
     * @param baselineRef baseline ref
     * @param targetRef target ref, nullable
     * @param outputFile report output
     * @param collector diagnostics
     */
    ImpactPreflightService(
            final DependencyAnalyzerCli rootCommand,
            final File analysisPath,
            final String baselineRef,
            final String targetRef,
            final File outputFile,
            final DiagnosticCollector collector) {
        root = rootCommand;
        path = analysisPath;
        baseline = baselineRef;
        target = targetRef;
        output = outputFile;
        diagnostics = collector;
    }

    /**
     * Runs the impact preflight graph.
     *
     * @param context prepared context
     * @return canonical result
     */
    PreflightReport run(
            final PreflightContext context) {
        return new PreflightRunner().run(
                new PreflightPlan(checks()),
                context);
    }

    private List<PreflightCheck> checks() {
        final List<PreflightCheck> checks =
                new ArrayList<>();
        checks.add(required("impact.path",
                List.of(), context -> {
                    final Path value = path
                            .toPath().toAbsolutePath()
                            .normalize();
                    if (!Files.isDirectory(value)) {
                        return PreflightOutcome.fail(
                                "Analysis path is not a directory",
                                value.toString(), "");
                    }
                    context.put(ANALYSIS_PATH, value);
                    return PreflightOutcome.pass(
                            "Analysis path is available",
                            value.toString());
                }));
        checks.add(required("impact.git-repository",
                List.of("impact.path"),
                context -> {
                    final Path analysisPath = context.get(
                            ANALYSIS_PATH, Path.class);
                    final Process process =
                            new ProcessBuilder(
                                    CommandResolver.resolve(
                                            List.of("git",
                                                    "rev-parse",
                                                    "--show-toplevel")))
                                    .directory(analysisPath.toFile())
                                    .redirectErrorStream(true)
                                    .start();
                    final String value = new String(
                            process.getInputStream()
                                    .readAllBytes(),
                            java.nio.charset.StandardCharsets
                                    .UTF_8).trim();
                    if (process.waitFor() != 0) {
                        return PreflightOutcome.fail(
                                "Analysis path is not in a Git repository",
                                value, "");
                    }
                    final Path gitRoot = Path.of(value)
                            .toAbsolutePath().normalize();
                    context.put(GIT_ROOT, gitRoot);
                    return PreflightOutcome.pass(
                            "Git repository found",
                            gitRoot.toString());
                }));
        checks.add(required("impact.root-pom",
                List.of("impact.path"),
                context -> {
                    final Path pom = context.get(
                            ANALYSIS_PATH, Path.class)
                            .resolve("pom.xml");
                    if (!Files.isRegularFile(pom)
                            || !Files.isReadable(pom)) {
                        return PreflightOutcome.fail(
                                "Root POM is unavailable",
                                pom.toString(), "");
                    }
                    return PreflightOutcome.pass(
                            "Root POM is readable",
                            pom.toString());
                }));
        checks.add(required("impact.output",
                List.of("impact.path"),
                context -> checkOutput()));
        checks.add(required("impact.maven-arguments",
                List.of("impact.git-repository"),
                context -> {
                    final List<String> arguments =
                            MavenArguments.validate(
                                    root.getMavenArguments(),
                                    context.get(GIT_ROOT,
                                            Path.class));
                    context.put(MAVEN_ARGS, arguments);
                    return PreflightOutcome.pass(
                            "Maven arguments are safe",
                            "accepted option count=" + arguments.size());
                }));
        checks.add(required("impact.java-runtime",
                List.of("impact.path"),
                context -> prepareJavaRuntime(
                        context)));
        checks.add(required("impact.maven-runtime",
                List.of("impact.java-runtime"),
                context -> prepareRuntime(context)));
        checks.add(required("impact.maven-version",
                List.of("impact.maven-runtime"),
                context -> probeVersion(context)));
        checks.add(required("impact.dependency-plugin-runtime",
                List.of("impact.maven-version",
                        "impact.maven-arguments"),
                context -> prepareDependencyPlugin(context)));
        checks.add(required("impact.workspace",
                List.of("impact.git-repository",
                        "impact.root-pom"),
                context -> prepareWorkspace(context)));
        checks.add(required("impact.graphml-capability",
                List.of("impact.workspace",
                        "impact.dependency-plugin-runtime"),
                context -> probeGraphMl(context)));
        return checks;
    }

    private SimplePreflightCheck required(
            final String id,
            final List<String> dependencies,
            final SimplePreflightCheck.Action action) {
        return new SimplePreflightCheck(
                id, "impact",
                PreflightScope.COMMAND,
                path.getAbsolutePath(),
                PreflightRequirement.REQUIRED,
                dependencies, action);
    }

    private PreflightOutcome checkOutput() {
        final Path value = output.toPath()
                .toAbsolutePath().normalize();
        if (Files.isDirectory(value)) {
            return PreflightOutcome.fail(
                    "Output path is a directory",
                    value.toString(), "");
        }
        final Path parent = value.getParent();
        if (parent == null
                || !Files.isDirectory(parent)
                || !Files.isWritable(parent)) {
            return PreflightOutcome.fail(
                    "Output parent is not writable",
                    value.toString(), "");
        }
        return PreflightOutcome.pass(
                "Output path is writable",
                value.toString());
    }

    private PreflightOutcome prepareRuntime(
            final PreflightContext context) {
        final File executable = root.getMaven();
        final File javaHome =
                root.getJavaHome();
        if (javaHome != null
                && !javaHome.isDirectory()) {
            return PreflightOutcome.fail(
                    "Java home is not a directory",
                    javaHome.getAbsolutePath(), "");
        }
        final MavenRuntimeDescriptor runtime =
                new MavenRuntimeManager().prepare(
                        executable == null
                                ? null
                                : executable.toPath(),
                        root.getConfigDir().toPath(),
                        javaHome == null
                                ? null
                                : javaHome.toPath());
        context.put(MAVEN_RUNTIME, runtime);
        return PreflightOutcome.pass(
                "Maven runtime prepared",
                MavenRuntimeEvidence.source(runtime));
    }

    private PreflightOutcome prepareJavaRuntime(
            final PreflightContext context) {
        final File configured = root.getJavaHome();
        final JavaRuntimeDescriptor descriptor =
                new Jdk8RuntimeProvider().probe(
                        configured == null
                                ? null
                                : configured.toPath());
        context.put(JAVA_RUNTIME, descriptor);
        return PreflightOutcome.pass(
                "Target JDK 8 is available",
                descriptor.getVersion() + "; home="
                        + descriptor.getJavaHome()
                        + "; bootEntries="
                        + descriptor.getBootClassPath()
                        .size());
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
                        context.get(ANALYSIS_PATH,
                                Path.class),
                        List.of("--version"));
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
            final PreflightContext context) {
        final MavenRuntimeDescriptor runtime = context.get(
                MAVEN_RUNTIME, MavenRuntimeDescriptor.class);
        final MavenDependencyPluginRuntime plugin = context.own(
                new MavenDependencyPluginRuntimeManager().prepare(
                        runtime.getConfigDir(),
                        context.get(MAVEN_ARGS, List.class),
                        null));
        context.put(DEPENDENCY_PLUGIN_RUNTIME, plugin);
        return PreflightOutcome.pass(
                "Maven Dependency Plugin runtime prepared",
                "dependencyPlugin=embedded:" + plugin.getVersion()
                        + "; artifactPathPlugin="
                        + plugin.getArtifactPathPluginVersion()
                        + "; repositories="
                        + plugin.getRepositories().size());
    }

    private PreflightOutcome prepareWorkspace(
            final PreflightContext context) {
        try {
            final CommandRunDirectory run =
                    context.own(new CommandRunDirectory(
                            root.getConfigDir().toPath(),
                            "impact"));
            context.put(COMMAND_RUN, run);
            final WorkspaceManager manager =
                    context.own(new WorkspaceManager(
                            context.get(
                                    ANALYSIS_PATH,
                                    Path.class),
                            diagnostics,
                            run.getWorkspaceDirectory()));
            final WorkspaceResult result =
                    manager.prepare(baseline, target);
            context.put(WORKSPACE, result);
            return PreflightOutcome.pass(
                    "Baseline and target workspaces prepared",
                    result.getBaseline().getCommit()
                            + " -> "
                            + result.getTarget().getCommit());
        } catch (Exception exception) {
            return PreflightOutcome.fail(
                    "Workspace preparation failed",
                    exception.getMessage(), "");
        }
    }

    private PreflightOutcome probeGraphMl(
            final PreflightContext context)
            throws Exception {
        final Path probe = Files.createTempFile(
                context.get(COMMAND_RUN,
                        CommandRunDirectory.class)
                        .getTemporaryDirectory(),
                "graphml-probe-",
                ".graphml");
        try {
            final List<String> arguments =
                    new ArrayList<>(context.get(
                            DEPENDENCY_PLUGIN_RUNTIME,
                            MavenDependencyPluginRuntime.class)
                            .getMavenArguments());
            arguments.add("-B");
            arguments.add(context.get(
                            DEPENDENCY_PLUGIN_RUNTIME,
                            MavenDependencyPluginRuntime.class)
                    .getGoal("tree"));
            arguments.add("-DoutputType=graphml");
            arguments.add("-DoutputFile=" + probe);
            final MavenExecutionResult result =
                    new MavenExecutor().execute(
                            context.get(MAVEN_RUNTIME,
                                    MavenRuntimeDescriptor.class),
                            context.get(WORKSPACE,
                                    WorkspaceResult.class)
                                    .getBaseline().getPath(),
                            arguments);
            if (result.getExitCode() != 0
                    || Files.size(probe) == 0) {
                return PreflightOutcome.fail(
                        "Dependency plugin GraphML"
                                + " capability is unavailable",
                        tail(result.getCombinedOutput()), "");
            }
            return PreflightOutcome.pass(
                    "Dependency plugin supports GraphML",
                    "probe bytes=" + Files.size(probe));
        } finally {
            Files.deleteIfExists(probe);
        }
    }

    private String tail(final String value) {
        final int start = Math.max(0,
                value.length() - 2000);
        return value.substring(start);
    }
}
