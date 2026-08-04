package io.github.dependencyanalysis.dependency;

import io.github.dependencyanalysis
        .diagnostic.DiagnosticCollector;
import io.github.dependencyanalysis
        .diagnostic.DiagnosticContext;
import io.github.dependencyanalysis
        .util.CommandResolver;
import io.github.dependencyanalysis
        .util.ProcessConsoleExecutor;
import io.github.dependencyanalysis
        .util.ProcessConsoleResult;
import io.github.dependencyanalysis.runtime
        .MavenDependencyPluginRuntime;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

// Wiki: wiki/features/dependency-tree-extraction.md - 依赖树提取与 GraphML 解析
// Wiki: wiki/rules/process-command-resolution.md - 跨平台命令规则
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
    private static final String GRAPHML_PREFIX =
            "dep-tree-cia-";

    /** Per-module resolved artifact list file name. */
    private static final String ARTIFACT_LIST_PREFIX =
            "resolved-artifacts-cia-";

    /** Pinned fallback Plugin prefix outside the Impact pipeline. */
    private static final String FALLBACK_PLUGIN_PREFIX =
            "org.apache.maven.plugins:maven-dependency-plugin:"
                    + "3.6.1:";

    /** Failure output tail lines retained in memory. */
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

    /** Maven executable. */
    private final Path mavenExecutable;

    /** User Maven arguments. */
    private final List<String> mavenArguments;

    /** Prepared embedded Plugin runtime, nullable for legacy constructors. */
    private MavenDependencyPluginRuntime pluginRuntime;

    /** Reactor project selection arguments. */
    private List<String> projectArguments = List.of();

    /** Command-owned temporary directory, nullable. */
    private Path temporaryDirectory;

    /** Stable concurrent diagnostic context. */
    private DiagnosticContext diagnosticContext =
            DiagnosticContext.stage(STAGE);

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
        this(sideName, path, reactor,
                diagCol, javaHomeOpt,
                Path.of("mvn"), List.of());
    }

    /**
     * Creates an analyzer with selected Maven runtime.
     *
     * @param sideName side identifier
     * @param path workspace root
     * @param reactor reactor coordinates
     * @param diagCol diagnostics
     * @param javaHomeOpt JAVA_HOME, nullable
     * @param executable Maven executable
     * @param arguments safe Maven arguments
     */
    public DependencyAnalyzer(
            final String sideName,
            final Path path,
            final Set<ArtifactCoord> reactor,
            final DiagnosticCollector diagCol,
            final File javaHomeOpt,
            final Path executable,
            final List<String> arguments) {
        this.side = sideName;
        this.workspacePath = path;
        this.reactorModules = reactor;
        this.diag = diagCol;
        this.buildJavaHome = javaHomeOpt;
        this.mavenExecutable = executable;
        this.mavenArguments = List.copyOf(arguments);
        this.pluginRuntime = null;
        this.temporaryDirectory = null;
    }

    /**
     * Selects command-owned temporary storage.
     *
     * @param directory command temporary directory
     * @return this analyzer
     */
    public DependencyAnalyzer withTemporaryDirectory(
            final Path directory) {
        temporaryDirectory = directory;
        return this;
    }

    /**
     * Selects a Maven reactor project closure, for example
     * {@code -pl module -am}.
     *
     * @param arguments Maven project selection tokens
     * @return this analyzer
     */
    public DependencyAnalyzer withProjectArguments(
            final List<String> arguments) {
        projectArguments = List.copyOf(arguments);
        return this;
    }

    /**
     * Selects the diagnostic task context.
     *
     * @param context context
     * @return this analyzer
     */
    public DependencyAnalyzer withDiagnosticContext(
            final DiagnosticContext context) {
        diagnosticContext = context;
        return this;
    }

    /**
     * Selects the prepared Maven Dependency Plugin runtime.
     *
     * @param runtime prepared runtime
     * @return this analyzer
     */
    public DependencyAnalyzer withPluginRuntime(
            final MavenDependencyPluginRuntime runtime) {
        pluginRuntime = runtime;
        return this;
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
        diag.startStage(diagnosticContext);
        diag.info(diagnosticContext,
                "Analyzing dependencies: "
                        + "workspace="
                        + workspacePath
                        + " side=" + side);
        final String outputName = GRAPHML_PREFIX
                + UUID.randomUUID() + ".graphml";
        final String cmdStr =
                "mvn " + dependencyGoal("tree") + " "
                        + "-DoutputType=graphml "
                        + "-DoutputFile="
                        + outputName
                        + " -B";
        diag.trace(diagnosticContext,
                "Executing " + dependencyGoal("tree")
                        + "; workspace=" + workspacePath);
        final ProcessConsoleResult execution =
                runDependencyTree(outputName);
        if (execution.exitCode() != 0) {
            diag.failStage(diagnosticContext,
                    "Dependency tree "
                            + "generation failed "
                            + "side=" + side
                            + " exitCode="
                            + execution.exitCode());
            throw new DependencyAnalysisException(
                    side,
                    workspacePath.toString(),
                    cmdStr,
                    execution.exitCode(),
                    execution.outputTail());
        }
        diag.info(diagnosticContext,
                "Dependency tree generated, "
                        + "parsing GraphML");
        final List<Path> graphmlFiles =
                findNamedFiles(workspacePath, outputName);
        if (graphmlFiles.isEmpty()) {
            diag.failStage(diagnosticContext,
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
        try {
            for (Path gFile : graphmlFiles) {
                diag.info(diagnosticContext,
                        "Parsing: " + gFile);
                final ModuleDependencyTree tree =
                        GraphMLParser.parse(
                                gFile,
                                reactorModules);
                trees.add(tree);
            }
        } finally {
            for (Path file : graphmlFiles) {
                Files.deleteIfExists(file);
            }
        }
        diag.info(diagnosticContext,
                "Parsed " + trees.size()
                        + " module(s)");
        diag.endStage(diagnosticContext);
        return trees;
    }

    /**
     * Runs dependency tree extraction and binds every retained
     * dependency to Maven's resolved absolute physical path.
     *
     * @return trees and physical artifact bindings
     * @throws DependencyAnalysisException when Maven or binding fails
     * @throws IOException when output cannot be read
     * @throws InterruptedException when interrupted
     */
    public DependencyAnalysisResult analyzeResolved()
            throws DependencyAnalysisException,
            IOException,
            InterruptedException {
        final List<ModuleDependencyTree> trees = analyze();
        final Set<String> reactorKeys = trees.stream()
                .map(tree -> tree.getModule().diffKey())
                .collect(java.util.stream.Collectors.toSet());
        final Map<String, List<ResolvedArtifact>> cache =
                new LinkedHashMap<>();
        final List<ResolvedArtifact> artifacts = new ArrayList<>();
        for (ModuleDependencyTree tree : trees.stream()
                .sorted(Comparator.comparing(value ->
                        value.getModulePath().toString())).toList()) {
            final List<DependencyNode> dependencies =
                    externalDependencies(tree, reactorKeys);
            if (dependencies.isEmpty()) {
                continue;
            }
            final String fingerprint = dependencyFingerprint(dependencies);
            List<ResolvedArtifact> resolved = cache.get(fingerprint);
            if (resolved == null) {
                resolved = resolveExternalArtifacts(tree, dependencies);
                cache.put(fingerprint, resolved);
            } else {
                resolved = rebindModule(resolved, tree.getModulePath());
            }
            artifacts.addAll(resolved);
        }
        return new DependencyAnalysisResult(trees, artifacts);
    }

    private List<ResolvedArtifact> resolveExternalArtifacts(
            final ModuleDependencyTree tree,
            final List<DependencyNode> dependencies)
            throws DependencyAnalysisException,
            IOException, InterruptedException {
        final Path modelDirectory = temporaryDirectory == null
                ? Files.createTempDirectory("cia-resolved-model-")
                : Files.createTempDirectory(temporaryDirectory,
                "resolved-model-" + side + "-");
        final Path modelPom = modelDirectory.resolve("pom.xml");
        final Path output = modelDirectory.resolve(
                ARTIFACT_LIST_PREFIX + UUID.randomUUID() + ".txt");
        try {
            Files.writeString(modelPom, resolutionModel(dependencies));
            diag.trace(diagnosticContext,
                    "Executing " + dependencyGoal("list")
                            + "; model=" + modelPom
                            + "; output=" + output);
            final ProcessConsoleResult execution =
                    runDependencyList(modelPom, output);
            if (execution.exitCode() != 0) {
                throw new DependencyAnalysisException(
                        side, tree.getModulePath().toString(),
                        "mvn " + dependencyGoal("list")
                                + " -DoutputAbsoluteArtifactFilename=true"
                                + " -DexcludeTransitive=true",
                        execution.exitCode(),
                        execution.outputTail());
            }
            if (!Files.isRegularFile(output)) {
                throw new DependencyAnalysisException(
                        "No resolved artifact list for module: "
                                + tree.getModulePath());
            }
            final List<ResolvedArtifact> resolved =
                    ResolvedArtifactListParser.parse(
                            output, tree.getModulePath());
            validateBindings(tree, dependencies, resolved);
            return resolved;
        } finally {
            deleteTree(modelDirectory);
        }
    }

    private ProcessConsoleResult runDependencyList(
            final Path modelPom,
            final Path output)
            throws IOException, InterruptedException {
        final List<String> cmd = new ArrayList<>();
        cmd.add(mavenExecutable.toString());
        cmd.addAll(dependencyArguments());
        cmd.add("-f");
        cmd.add(modelPom.toString());
        cmd.add(dependencyGoal("list"));
        cmd.add("-DoutputFile=" + output);
        cmd.add("-DoutputAbsoluteArtifactFilename=true");
        cmd.add("-DappendOutput=false");
        cmd.add("-DexcludeReactor=true");
        cmd.add("-DexcludeTransitive=true");
        cmd.add("-B");
        final ProcessBuilder builder = new ProcessBuilder(
                CommandResolver.resolve(cmd))
                .directory(workspacePath.toFile());
        if (buildJavaHome != null) {
            builder.environment().put("JAVA_HOME",
                    buildJavaHome.getAbsolutePath());
        }
        return ProcessConsoleExecutor.execute(
                builder, diag, diagnosticContext,
                TAIL_LINES);
    }

    private List<DependencyNode> externalDependencies(
            final ModuleDependencyTree tree,
            final Set<String> reactorKeys) {
        final Map<String, DependencyNode> result = new LinkedHashMap<>();
        collectDependencies(tree.getDependencies(), reactorKeys, result);
        return result.values().stream()
                .sorted(Comparator
                        .comparing((DependencyNode value) ->
                                value.getArtifact().toString())
                        .thenComparing(value ->
                                value.getScope().getValue()))
                .toList();
    }

    private void collectDependencies(
            final List<DependencyNode> nodes,
            final Set<String> reactorKeys,
            final Map<String, DependencyNode> result) {
        for (DependencyNode node : nodes) {
            if (!reactorKeys.contains(node.getArtifact().diffKey())) {
                final String key = node.getArtifact().toString()
                        + ":" + node.getScope().getValue();
                result.putIfAbsent(key, node);
            }
            collectDependencies(node.getChildren(), reactorKeys, result);
        }
    }

    private String dependencyFingerprint(
            final List<DependencyNode> dependencies) {
        return dependencies.stream().map(value ->
                value.getArtifact() + ":" + value.getScope().getValue())
                .sorted().collect(java.util.stream.Collectors.joining("|"));
    }

    private List<ResolvedArtifact> rebindModule(
            final List<ResolvedArtifact> artifacts,
            final Path modulePath) {
        return artifacts.stream().map(value -> new ResolvedArtifact(
                        modulePath, value.getArtifact(), value.getScope(),
                        value.getPath()))
                .toList();
    }

    private String resolutionModel(
            final List<DependencyNode> dependencies) {
        final StringBuilder value = new StringBuilder(
                "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">"
                        + "<modelVersion>4.0.0</modelVersion>"
                        + "<groupId>io.github.dependencyanalysis</groupId>"
                        + "<artifactId>resolved-artifacts</artifactId>"
                        + "<version>1</version><dependencies>");
        for (DependencyNode dependency : dependencies) {
            final ArtifactCoord artifact = dependency.getArtifact();
            value.append("<dependency><groupId>")
                    .append(xml(artifact.getGroupId()))
                    .append("</groupId><artifactId>")
                    .append(xml(artifact.getArtifactId()))
                    .append("</artifactId><version>")
                    .append(xml(artifact.getVersion()))
                    .append("</version><type>")
                    .append(xml(artifact.getType())).append("</type>");
            if (!artifact.getClassifier().isEmpty()) {
                value.append("<classifier>")
                        .append(xml(artifact.getClassifier()))
                        .append("</classifier>");
            }
            value.append("<scope>")
                    .append(dependency.getScope().getValue())
                    .append("</scope></dependency>");
        }
        return value.append("</dependencies></project>").toString();
    }

    private void validateBindings(
            final ModuleDependencyTree tree,
            final List<DependencyNode> expected,
            final List<ResolvedArtifact> resolved)
            throws DependencyAnalysisException {
        for (DependencyNode dependency : expected) {
            final long matches = resolved.stream()
                    .filter(value -> value.getArtifact().equals(
                            dependency.getArtifact()))
                    .filter(value -> value.getScope()
                            == dependency.getScope())
                    .count();
            if (matches != 1L) {
                throw new DependencyAnalysisException(
                        "Artifact path binding must be unique: module="
                                + tree.getModule() + "; artifact="
                                + dependency.getArtifact() + "; matches="
                                + matches);
            }
        }
    }

    private String xml(final String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private void deleteTree(final Path root) {
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder())
                    .toList()) {
                Files.deleteIfExists(path);
            }
        } catch (IOException exception) {
            diag.warn(diagnosticContext,
                    "Unable to remove resolution model: " + root);
        }
    }

    private List<Path> findNamedFiles(
            final Path root, final String name) throws IOException {
        final List<Path> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> name.equals(
                            path.getFileName().toString()))
                    .forEach(result::add);
        }
        result.sort(java.util.Comparator.comparing(Path::toString));
        return result;
    }

    /**
     * Executes the Maven Dependency Plugin tree goal and streams output
     * according to verbosity.
     *
     * @param outputName unique per-module GraphML filename
     * @return process result
     * @throws IOException        if
     *  process start fails
     * @throws InterruptedException if
     *  interrupted
     */
    private ProcessConsoleResult runDependencyTree(
            final String outputName)
            throws IOException,
            InterruptedException {
        final List<String> cmd =
                new ArrayList<>();
        cmd.add(mavenExecutable.toString());
        cmd.addAll(dependencyArguments());
        cmd.addAll(projectArguments);
        cmd.add(dependencyGoal("tree"));
        cmd.add("-DoutputType=graphml");
        cmd.add("-DoutputFile="
                + outputName);
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
                pb, diag, diagnosticContext,
                TAIL_LINES);
    }

    private List<String> dependencyArguments() {
        return pluginRuntime == null
                ? mavenArguments
                : pluginRuntime.getMavenArguments();
    }

    private String dependencyGoal(final String goalName) {
        if (pluginRuntime != null) {
            return pluginRuntime.getGoal(goalName);
        }
        return FALLBACK_PLUGIN_PREFIX + goalName;
    }

}
