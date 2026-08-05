package io.github.dependencyanalysis.build;

import io.github.dependencyanalysis
        .diagnostic.DiagnosticLog;
import io.github.dependencyanalysis
        .diagnostic.DiagnosticContext;
import io.github.dependencyanalysis
        .util.CommandResolver;
import io.github.dependencyanalysis
        .util.ProcessConsoleExecutor;
import io.github.dependencyanalysis
        .util.ProcessConsoleResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// Wiki: wiki/features/maven-build-runner.md - Maven 编译执行和 main classes 收集
// Wiki: wiki/rules/process-command-resolution.md - 跨平台命令规则
/**
 * Executes Maven compile on a workspace
 * and collects main classes directories.
 */
public final class BuildRunner {

    /** Diagnostic stage name. */
    private static final String STAGE =
            "build";

    /** Failure output tail lines retained in memory. */
    private static final int TAIL_LINES =
            20;

    /** Side identifier. */
    private final String side;

    /** Workspace root path. */
    private final Path workspacePath;

    /** Diagnostic collector. */
    private final DiagnosticLog diag;

    /** Optional JAVA_HOME for mvn. */
    private final File buildJavaHome;

    /** Maven executable. */
    private final Path mavenExecutable;

    /** User Maven arguments. */
    private final List<String> mavenArguments;

    /** Reactor project selection arguments. */
    private List<String> projectArguments = List.of();

    /** Stable concurrent diagnostic context. */
    private DiagnosticContext diagnosticContext;

    /**
     * Creates a new build runner.
     *
     * @param sideName side identifier
     * @param path     workspace root path
     * @param diagCol  diagnostic collector
     */
    public BuildRunner(
            final String sideName,
            final Path path,
            final DiagnosticLog
                    diagCol) {
        this(sideName, path, diagCol,
                null);
    }

    /**
     * Creates a new build runner
     * with optional JAVA_HOME override.
     *
     * @param sideName     side identifier
     * @param path         workspace root path
     * @param diagCol      diagnostic collector
     * @param javaHomeOpt  optional JAVA_HOME
     *                     for mvn subprocess
     */
    public BuildRunner(
            final String sideName,
            final Path path,
            final DiagnosticLog
                    diagCol,
            final File javaHomeOpt) {
        this(sideName, path, diagCol,
                javaHomeOpt, Path.of("mvn"),
                List.of());
    }

    /**
     * Creates a runner with selected Maven runtime.
     *
     * @param sideName side identifier
     * @param path workspace root
     * @param diagCol diagnostics
     * @param javaHomeOpt JAVA_HOME, nullable
     * @param executable Maven executable
     * @param arguments safe Maven arguments
     */
    public BuildRunner(
            final String sideName,
            final Path path,
            final DiagnosticLog diagCol,
            final File javaHomeOpt,
            final Path executable,
            final List<String> arguments) {
        this.side = sideName;
        this.workspacePath = path;
        this.diag = diagCol;
        this.buildJavaHome = javaHomeOpt;
        this.mavenExecutable = executable;
        this.mavenArguments = List.copyOf(arguments);
    }

    /**
     * Selects a Maven reactor project closure, for example
     * {@code -pl module -am}.
     *
     * @param arguments Maven project selection tokens
     * @return this runner
     */
    public BuildRunner withProjectArguments(
            final List<String> arguments) {
        projectArguments = List.copyOf(arguments);
        return this;
    }

    /**
     * Selects the diagnostic task context.
     *
     * @param context context
     * @return this runner
     */
    public BuildRunner withDiagnosticContext(
            final DiagnosticContext context) {
        diagnosticContext = context;
        return this;
    }

    /**
     * Runs {@code mvn compile} and
     * collects module outputs.
     *
     * @return build result
     * @throws BuildException     if build
     *                            fails
     * @throws IOException        if IO
     *                            error
     * @throws InterruptedException if
     *  interrupted
     */
    public BuildResult build()
            throws BuildException,
            IOException,
            InterruptedException {
        startDiagnostics();
        info(
                "Building workspace: "
                        + workspacePath
                        + " side=" + side);
        final String cmdStr =
                "mvn compile -B";
        trace(
                "Executing Maven compile; workspace="
                        + workspacePath);
        final ProcessConsoleResult execution =
                runMvnCompile();
        if (execution.exitCode() != 0) {
            fail(
                    "Build failed side="
                            + side
                            + " exitCode="
                            + execution.exitCode());
            throw new BuildException(
                    side,
                    workspacePath
                            .toString(),
                    cmdStr,
                    execution.exitCode(),
                    execution.outputTail());
        }
        info(
                "Build succeeded, "
                        + "discovering "
                        + "modules");
        final List<ModuleBuildOutput>
                mods =
                discoverModules(
                        workspacePath);
        info(
                "Found " + mods.size()
                        + " module(s)");
        endDiagnostics();
        return BuildResult.of(mods);
    }

    private void startDiagnostics() {
        if (diagnosticContext == null) {
            diag.startStage(STAGE);
        } else {
            diag.startStage(diagnosticContext);
        }
    }

    private void endDiagnostics() {
        if (diagnosticContext == null) {
            diag.endStage(STAGE);
        } else {
            diag.endStage(diagnosticContext);
        }
    }

    private void info(final String message) {
        if (diagnosticContext == null) {
            diag.info(STAGE, message);
        } else {
            diag.info(diagnosticContext, message);
        }
    }

    private void trace(final String message) {
        if (diagnosticContext == null) {
            diag.trace(STAGE, message);
        } else {
            diag.trace(diagnosticContext, message);
        }
    }

    private void fail(final String message) {
        if (diagnosticContext == null) {
            diag.failStage(STAGE, message);
        } else {
            diag.failStage(diagnosticContext, message);
        }
    }

    /**
     * Executes mvn compile and streams output according to verbosity.
     *
     * @return process result
     * @throws IOException        if
     *  process start fails
     * @throws InterruptedException if
     *  interrupted
     */
    private ProcessConsoleResult runMvnCompile()
            throws IOException,
            InterruptedException {
        final List<String> cmd =
                new ArrayList<>();
        cmd.add(mavenExecutable.toString());
        cmd.addAll(mavenArguments);
        cmd.addAll(projectArguments);
        cmd.add("compile");
        cmd.add("-B");
        final List<String> resolved =
                CommandResolver.resolve(
                        cmd);
        final ProcessBuilder pb =
                new ProcessBuilder(
                        resolved)
                        .directory(
                                workspacePath
                                        .toFile());
        if (buildJavaHome != null) {
            final Map<String, String> env =
                    pb.environment();
            env.put("JAVA_HOME",
                    buildJavaHome
                            .getAbsolutePath());
        }
        return ProcessConsoleExecutor.execute(
                pb, diag, context(), TAIL_LINES);
    }

    /**
     * Discovers all target/classes
     * directories under the given root.
     * Excludes test-classes.
     *
     * @param root workspace root
     * @return module build outputs
     * @throws IOException if walk fails
     */
    static List<ModuleBuildOutput>
            discoverModules(final Path root)
            throws IOException {
        final List<ModuleBuildOutput>
                result =
                new ArrayList<>();
        try (Stream<Path> stream =
                Files.walk(root)) {
            stream.filter(
                            Files::isDirectory)
                    .filter(p -> {
                        final String name =
                                p.getFileName()
                                        .toString();
                        return "classes"
                                .equals(name);
                    })
                    .filter(p -> {
                        final Path parent =
                                p.getParent();
                        if (parent == null) {
                            return false;
                        }
                        final String pName =
                                parent
                                        .getFileName()
                                        .toString();
                        return "target"
                                .equals(pName);
                    })
                    .forEach(p -> {
                        final Path modDir =
                                p.getParent()
                                        .getParent();
                        result.add(
                                new ModuleBuildOutput(
                                        modDir, p));
                    });
        }
        return result;
    }

    private DiagnosticContext context() {
        return diagnosticContext == null
                ? DiagnosticContext.stage(STAGE)
                : diagnosticContext;
    }
}
