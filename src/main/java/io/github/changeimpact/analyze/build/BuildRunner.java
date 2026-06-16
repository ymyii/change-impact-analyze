package io.github.changeimpact.analyze.build;

import io.github.changeimpact.analyze
        .diagnostic.DiagnosticCollector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Executes Maven compile on a workspace
 * and collects main classes directories.
 */
public final class BuildRunner {

    /** Diagnostic stage name. */
    private static final String STAGE =
            "build";

    /** Log tail lines for error. */
    private static final int TAIL_LINES =
            20;

    /** Side identifier. */
    private final String side;

    /** Workspace root path. */
    private final Path workspacePath;

    /** Diagnostic collector. */
    private final DiagnosticCollector diag;

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
            final DiagnosticCollector
                    diagCol) {
        this.side = sideName;
        this.workspacePath = path;
        this.diag = diagCol;
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
        diag.startStage(STAGE);
        diag.info(STAGE,
                "Building workspace: "
                        + workspacePath
                        + " side=" + side);
        final Path logFile =
                Files.createTempFile(
                        "cia-build-",
                        ".log");
        final String cmdStr =
                "mvn compile -B";
        final int exitCode =
                runMvnCompile(logFile);
        if (exitCode != 0) {
            final String tail =
                    readLogTail(logFile);
            diag.failStage(STAGE,
                    "Build failed side="
                            + side
                            + " exitCode="
                            + exitCode);
            throw new BuildException(
                    side,
                    workspacePath
                            .toString(),
                    cmdStr,
                    exitCode,
                    tail,
                    logFile);
        }
        diag.info(STAGE,
                "Build succeeded, "
                        + "discovering "
                        + "modules");
        final List<ModuleBuildOutput>
                mods =
                discoverModules(
                        workspacePath);
        diag.info(STAGE,
                "Found " + mods.size()
                        + " module(s)");
        diag.endStage(STAGE);
        return BuildResult.of(mods);
    }

    /**
     * Executes mvn compile and returns
     * the process exit code.
     *
     * @param logFile path to log file
     * @return exit code
     * @throws IOException        if
     *  process start fails
     * @throws InterruptedException if
     *  interrupted
     */
    private int runMvnCompile(
            final Path logFile)
            throws IOException,
            InterruptedException {
        final List<String> cmd =
                new ArrayList<>();
        cmd.add("mvn");
        cmd.add("compile");
        cmd.add("-B");
        final ProcessBuilder pb =
                new ProcessBuilder(cmd)
                        .directory(
                                workspacePath
                                        .toFile())
                        .redirectOutput(
                                logFile
                                        .toFile())
                        .redirectErrorStream(
                                true);
        final Process proc = pb.start();
        return proc.waitFor();
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

    /**
     * Reads the last N lines of a log file.
     *
     * @param log log file path
     * @return tail text
     */
    private String readLogTail(
            final Path log) {
        try {
            final List<String> lines =
                    Files.readAllLines(log);
            final int size = lines.size();
            final int from =
                    Math.max(0,
                            size - TAIL_LINES);
            final StringBuilder sb =
                    new StringBuilder();
            for (int i = from; i < size;
                    i++) {
                if (sb.length() > 0) {
                    sb.append("\n");
                }
                sb.append(lines.get(i));
            }
            return sb.toString();
        } catch (IOException ex) {
            return "(unable to read log)";
        }
    }
}
