package io.github.changeimpact.analyze.dependency;

import io.github.changeimpact.analyze
        .diagnostic.DiagnosticCollector;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

// Wiki: wiki/features/dependency-tree-extraction.md - 依赖树提取与 GraphML 解析
/**
 * Executes Maven dependency plugin and
 * parses GraphML output into module
 * dependency trees.
 */
public final class DependencyAnalyzer {

    /** Diagnostic stage name. */
    private static final String STAGE =
            "dependency";

    /** GraphML output file name. */
    private static final String GRAPHML_FILE =
            "dep-tree.graphml";

    /** Log tail lines for error. */
    private static final int TAIL_LINES =
            20;

    /** Side identifier. */
    private final String side;

    /** Workspace root path. */
    private final Path workspacePath;

    /** Reactor module coordinates. */
    private final Set<ArtifactCoord>
            reactorModules;

    /** Diagnostic collector. */
    private final DiagnosticCollector diag;

    /** Optional JAVA_HOME for mvn. */
    private final File buildJavaHome;

    /**
     * Creates a new dependency analyzer.
     *
     * @param sideName side identifier
     * @param path     workspace root path
     * @param reactor  reactor module coords
     * @param diagCol  diagnostic collector
     */
    public DependencyAnalyzer(
            final String sideName,
            final Path path,
            final Set<ArtifactCoord>
                    reactor,
            final DiagnosticCollector
                    diagCol) {
        this(sideName, path, reactor,
                diagCol, null);
    }

    /**
     * Creates a new dependency analyzer
     * with optional JAVA_HOME override.
     *
     * @param sideName     side identifier
     * @param path         workspace root path
     * @param reactor      reactor module coords
     * @param diagCol      diagnostic collector
     * @param javaHomeOpt  optional JAVA_HOME
     *                     for mvn subprocess
     */
    public DependencyAnalyzer(
            final String sideName,
            final Path path,
            final Set<ArtifactCoord>
                    reactor,
            final DiagnosticCollector
                    diagCol,
            final File javaHomeOpt) {
        this.side = sideName;
        this.workspacePath = path;
        this.reactorModules = reactor;
        this.diag = diagCol;
        this.buildJavaHome = javaHomeOpt;
    }

    /**
     * Runs Maven dependency plugin and
     * parses GraphML output.
     *
     * @return list of module dependency
     *         trees
     * @throws DependencyAnalysisException
     *  if analysis fails
     * @throws IOException        if IO
     *                            error
     * @throws InterruptedException if
     *  interrupted
     */
    public List<ModuleDependencyTree>
            analyze()
            throws DependencyAnalysisException,
            IOException,
            InterruptedException {
        diag.startStage(STAGE);
        diag.info(STAGE,
                "Analyzing dependencies: "
                        + "workspace="
                        + workspacePath
                        + " side=" + side);
        final Path logFile =
                Files.createTempFile(
                        "cia-dep-",
                        ".log");
        final String cmdStr =
                "mvn dependency:tree "
                        + "-DoutputType=graphml "
                        + "-DoutputFile="
                        + GRAPHML_FILE
                        + " -B";
        final int exitCode =
                runDependencyTree(logFile);
        if (exitCode != 0) {
            final String tail =
                    readLogTail(logFile);
            diag.failStage(STAGE,
                    "Dependency tree "
                            + "generation failed "
                            + "side=" + side
                            + " exitCode="
                            + exitCode);
            throw new DependencyAnalysisException(
                    side,
                    workspacePath.toString(),
                    cmdStr,
                    exitCode,
                    tail,
                    logFile);
        }
        diag.info(STAGE,
                "Dependency tree generated, "
                        + "parsing GraphML");
        final List<Path> graphmlFiles =
                findGraphMLFiles(
                        workspacePath);
        if (graphmlFiles.isEmpty()) {
            diag.failStage(STAGE,
                    "No GraphML files found "
                            + "in "
                            + workspacePath);
            throw new DependencyAnalysisException(
                    "No GraphML files found "
                            + "in workspace: "
                            + workspacePath);
        }
        final List<ModuleDependencyTree>
                trees = new ArrayList<>();
        for (Path gFile : graphmlFiles) {
            diag.info(STAGE,
                    "Parsing: " + gFile);
            final ModuleDependencyTree tree =
                    GraphMLParser.parse(
                            gFile,
                            reactorModules);
            trees.add(tree);
        }
        diag.info(STAGE,
                "Parsed " + trees.size()
                        + " module(s)");
        diag.endStage(STAGE);
        return trees;
    }

    /**
     * Executes mvn dependency:tree and
     * returns the process exit code.
     *
     * @param logFile path to log file
     * @return exit code
     * @throws IOException        if
     *  process start fails
     * @throws InterruptedException if
     *  interrupted
     */
    private int runDependencyTree(
            final Path logFile)
            throws IOException,
            InterruptedException {
        final List<String> cmd =
                new ArrayList<>();
        cmd.add("mvn");
        cmd.add("dependency:tree");
        cmd.add("-DoutputType=graphml");
        cmd.add("-DoutputFile="
                + GRAPHML_FILE);
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
        if (buildJavaHome != null) {
            final Map<String, String> env =
                    pb.environment();
            env.put("JAVA_HOME",
                    buildJavaHome
                            .getAbsolutePath());
        }
        final Process proc = pb.start();
        return proc.waitFor();
    }

    /**
     * Finds all dep-tree.graphml files
     * under the given root directory.
     *
     * @param root workspace root
     * @return list of GraphML file paths
     * @throws IOException if walk fails
     */
    private List<Path> findGraphMLFiles(
            final Path root)
            throws IOException {
        final List<Path> result =
                new ArrayList<>();
        try (Stream<Path> stream =
                Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(p -> GRAPHML_FILE
                            .equals(p
                                    .getFileName()
                                    .toString()))
                    .forEach(result::add);
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
