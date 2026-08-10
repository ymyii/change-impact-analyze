package io.github.dependencyanalysis.dependency;

import io.github.dependencyanalysis
        .diagnostic.DiagnosticLog;
import io.github.dependencyanalysis
        .diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.runtime
        .MavenDependencyPluginRuntime;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

// Wiki: wiki/features/dependency-tree-extraction.md - 依赖树与 JSON path binding
// Wiki: wiki/rules/process-command-resolution.md - 跨平台命令规则
/** Executes Maven dependency evidence collection for one reactor closure. */
public final class DependencyAnalyzer {

    /** Diagnostic stage name. */
    private static final String STAGE = "dependency";

    /** GraphML output filename prefix. */
    private static final String GRAPHML_PREFIX = "dep-tree-cia-";

    /** Artifact Path JSON filename prefix. */
    private static final String ARTIFACT_JSON_PREFIX =
            "resolved-artifacts-cia-";

    /** Pinned fallback Dependency Plugin prefix. */
    private static final String FALLBACK_PLUGIN_PREFIX =
            "org.apache.maven.plugins:maven-dependency-plugin:3.6.1:";

    /** Built-in Artifact Path Plugin goal. */
    private static final String FALLBACK_ARTIFACT_PATH_GOAL =
            MavenDependencyPluginRuntime.ARTIFACT_PATH_PLUGIN_GOAL;

    /** Failure output tail retained in memory. */
    private static final int TAIL_LINES = 20;

    /** Side identifier. */
    private final String side;

    /** Workspace root. */
    private final Path workspacePath;

    /** Reactor modules supplied by the caller for GraphML filtering. */
    private final Set<ArtifactCoord> reactorModules;

    /** Diagnostics. */
    private final DiagnosticLog diag;

    /** Optional JAVA_HOME. */
    private final File buildJavaHome;

    /** Maven executable. */
    private final Path mavenExecutable;

    /** Safe user Maven arguments. */
    private final List<String> mavenArguments;

    /** Prepared embedded Plugin runtime. */
    private MavenDependencyPluginRuntime pluginRuntime;

    /** Reactor project selector arguments. */
    private List<String> projectArguments = List.of();

    /** Stable concurrent diagnostic context. */
    private DiagnosticContext diagnosticContext =
            DiagnosticContext.stage(STAGE);

    /**
     * Creates an analyzer using Maven from PATH.
     *
     * @param sideName side identifier
     * @param path workspace root
     * @param reactor reactor coordinates
     * @param diagCol diagnostics
     */
    public DependencyAnalyzer(
            final String sideName,
            final Path path,
            final Set<ArtifactCoord> reactor,
            final DiagnosticLog diagCol) {
        this(sideName, path, reactor, diagCol, null);
    }

    /**
     * Creates an analyzer using Maven from PATH.
     *
     * @param sideName side identifier
     * @param path workspace root
     * @param reactor reactor coordinates
     * @param diagCol diagnostics
     * @param javaHomeOpt optional JAVA_HOME
     */
    public DependencyAnalyzer(
            final String sideName,
            final Path path,
            final Set<ArtifactCoord> reactor,
            final DiagnosticLog diagCol,
            final File javaHomeOpt) {
        this(sideName, path, reactor, diagCol, javaHomeOpt,
                Path.of("mvn"), List.of());
    }

    /**
     * Creates an analyzer using the selected Maven runtime.
     *
     * @param sideName side identifier
     * @param path workspace root
     * @param reactor reactor coordinates
     * @param diagCol diagnostics
     * @param javaHomeOpt optional JAVA_HOME
     * @param executable Maven executable
     * @param arguments safe Maven arguments
     */
    public DependencyAnalyzer(
            final String sideName,
            final Path path,
            final Set<ArtifactCoord> reactor,
            final DiagnosticLog diagCol,
            final File javaHomeOpt,
            final Path executable,
            final List<String> arguments) {
        side = sideName;
        workspacePath = path;
        reactorModules = reactor;
        diag = diagCol;
        buildJavaHome = javaHomeOpt;
        mavenExecutable = executable;
        mavenArguments = List.copyOf(arguments);
    }

    /**
     * Selects a Maven reactor project closure.
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
     * Selects the prepared embedded Plugin runtime.
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
     * Runs only Dependency Plugin GraphML extraction.
     *
     * @return module dependency trees
     * @throws DependencyAnalysisException Maven or parsing failure
     * @throws IOException output discovery failure
     * @throws InterruptedException interrupted process
     */
    public List<ModuleDependencyTree> analyze()
            throws DependencyAnalysisException, IOException,
            InterruptedException {
        start();
        final String graphmlName = GRAPHML_PREFIX
                + UUID.randomUUID() + ".graphml";
        final ProcessConsoleResult execution = runMaven(
                graphmlName, null, true);
        final List<Path> graphmlFiles = findNamedFiles(
                workspacePath, graphmlName);
        try {
            requireSuccessful(execution, graphmlName, null, true);
            if (graphmlFiles.isEmpty()) {
                throw new DependencyAnalysisException(
                        "No GraphML files found in workspace: "
                                + workspacePath);
            }
            final List<ModuleDependencyTree> trees =
                    parseTrees(graphmlFiles);
            finish(trees.size());
            return trees;
        } finally {
            deleteFiles(graphmlFiles);
        }
    }

    /**
     * Collects physical artifact bindings and a separate verbose occurrence
     * GraphML. The built-in path resolver consumes ordinary GraphML; Maven's
     * verbose labels are retained only for occurrence topology.
     *
     * @return dependency trees and physical paths
     * @throws DependencyAnalysisException Maven or contract failure
     * @throws IOException output discovery failure
     * @throws InterruptedException interrupted process
     */
    public DependencyAnalysisResult analyzeResolved()
            throws DependencyAnalysisException, IOException,
            InterruptedException {
        start();
        final String nonce = UUID.randomUUID().toString();
        final String bindingGraphmlName = GRAPHML_PREFIX + nonce
                + "-binding.graphml";
        final String graphmlName = GRAPHML_PREFIX + nonce + ".graphml";
        final String jsonName = ARTIFACT_JSON_PREFIX + nonce + ".json";
        final ProcessConsoleResult bindingExecution = runMaven(
                bindingGraphmlName, jsonName, false);
        requireSuccessful(bindingExecution, bindingGraphmlName, jsonName,
                false);
        final ProcessConsoleResult occurrenceExecution = runMaven(
                graphmlName, null, true);
        final List<Path> graphmlFiles = findNamedFiles(
                workspacePath, graphmlName);
        final List<Path> bindingGraphmlFiles = findNamedFiles(
                workspacePath, bindingGraphmlName);
        final List<Path> jsonFiles = findNamedFiles(
                workspacePath, jsonName);
        try {
            requireSuccessful(occurrenceExecution, graphmlName, null, true);
            if (graphmlFiles.isEmpty() || jsonFiles.isEmpty()) {
                throw new DependencyAnalysisException(
                        "Missing dependency evidence: graphml="
                                + graphmlFiles.size() + "; json="
                                + jsonFiles.size());
            }
            final List<ModuleDependencyTree> trees =
                    parseTrees(graphmlFiles);
            final List<ResolvedArtifactManifest> manifests =
                    parseManifests(jsonFiles);
            final Map<Path, List<ResolvedArtifact>> artifacts =
                    pairAndValidate(trees, manifests);
            finish(trees.size());
            return new DependencyAnalysisResult(trees, artifacts);
        } finally {
            deleteFiles(graphmlFiles);
            deleteFiles(bindingGraphmlFiles);
            deleteFiles(jsonFiles);
        }
    }

    private void start() {
        diag.startStage(diagnosticContext);
        diag.info(diagnosticContext,
                "Analyzing dependencies: workspace="
                        + workspacePath + " side=" + side);
    }

    private void finish(final int modules) {
        diag.info(diagnosticContext,
                "Parsed " + modules + " module(s)");
        diag.endStage(diagnosticContext);
    }

    private ProcessConsoleResult runMaven(
            final String graphmlName,
            final String jsonName,
            final boolean verbose) throws IOException,
            InterruptedException {
        final List<String> command = new ArrayList<>();
        command.add(mavenExecutable.toString());
        command.addAll(dependencyArguments());
        command.addAll(projectArguments);
        command.add(dependencyGoal("tree"));
        command.add("-DoutputType=graphml");
        if (verbose) {
            command.add("-Dverbose=true");
        }
        command.add("-DoutputFile=" + graphmlName);
        if (jsonName != null) {
            command.add(artifactPathGoal());
            command.add("-Dcia.dependencyGraphFileName=" + graphmlName);
            command.add("-Dcia.resolvedArtifactsFileName=" + jsonName);
        }
        command.add("-B");
        diag.trace(diagnosticContext,
                "Executing dependency evidence goals; workspace="
                        + workspacePath);
        final ProcessBuilder builder = new ProcessBuilder(
                CommandResolver.resolve(command))
                .directory(workspacePath.toFile());
        if (buildJavaHome != null) {
            builder.environment().put("JAVA_HOME",
                    buildJavaHome.getAbsolutePath());
        }
        return ProcessConsoleExecutor.execute(builder, diag,
                diagnosticContext, TAIL_LINES);
    }

    private void requireSuccessful(
            final ProcessConsoleResult execution,
            final String graphmlName,
            final String jsonName,
            final boolean verbose) throws DependencyAnalysisException {
        if (execution.exitCode() == 0) {
            return;
        }
        diag.failStage(diagnosticContext,
                "Dependency evidence generation failed side=" + side
                        + " exitCode=" + execution.exitCode());
        final StringBuilder command = new StringBuilder("mvn ")
                .append(dependencyGoal("tree"))
                .append(" -DoutputType=graphml");
        if (verbose) {
            command.append(" -Dverbose=true");
        }
        command.append(" -DoutputFile=").append(graphmlName);
        if (jsonName != null) {
            command.append(' ').append(artifactPathGoal())
                    .append(" -Dcia.dependencyGraphFileName=")
                    .append(graphmlName)
                    .append(" -Dcia.resolvedArtifactsFileName=")
                    .append(jsonName);
        }
        command.append(" -B");
        throw new DependencyAnalysisException(side,
                workspacePath.toString(), command.toString(),
                execution.exitCode(), execution.outputTail());
    }

    private List<ModuleDependencyTree> parseTrees(
            final List<Path> graphmlFiles)
            throws DependencyAnalysisException {
        final List<ModuleDependencyTree> trees = new ArrayList<>();
        for (Path graphml : graphmlFiles) {
            diag.info(diagnosticContext, "Parsing: " + graphml);
            trees.add(GraphMLParser.parse(graphml, reactorModules));
        }
        trees.sort(Comparator.comparing(value ->
                value.getModulePath().toString()));
        return List.copyOf(trees);
    }

    private List<ResolvedArtifactManifest> parseManifests(
            final List<Path> jsonFiles)
            throws DependencyAnalysisException {
        final List<ResolvedArtifactManifest> manifests =
                new ArrayList<>();
        for (Path json : jsonFiles) {
            manifests.add(ResolvedArtifactJsonParser.parse(json));
        }
        return List.copyOf(manifests);
    }

    private Map<Path, List<ResolvedArtifact>> pairAndValidate(
            final List<ModuleDependencyTree> trees,
            final List<ResolvedArtifactManifest> manifests)
            throws DependencyAnalysisException {
        final Map<Path, ModuleDependencyTree> treeByDirectory =
                new LinkedHashMap<>();
        for (ModuleDependencyTree tree : trees) {
            final Path directory = canonicalDirectory(
                    tree.getModulePath());
            if (treeByDirectory.put(directory, tree) != null) {
                throw new DependencyAnalysisException(
                        "Duplicate GraphML module directory: " + directory);
            }
        }
        final Map<Path, ResolvedArtifactManifest> manifestByDirectory =
                new LinkedHashMap<>();
        for (ResolvedArtifactManifest manifest : manifests) {
            if (manifestByDirectory.put(manifest.getDirectory(),
                    manifest) != null) {
                throw new DependencyAnalysisException(
                        "Duplicate JSON module directory: "
                                + manifest.getDirectory());
            }
        }
        if (!treeByDirectory.keySet().equals(
                manifestByDirectory.keySet())) {
            throw new DependencyAnalysisException(
                    "GraphML and JSON module sets differ: graphml="
                            + treeByDirectory.keySet() + "; json="
                            + manifestByDirectory.keySet());
        }

        final Set<ArtifactCoord> reactorCoordinates =
                new LinkedHashSet<>();
        for (ModuleDependencyTree tree : trees) {
            reactorCoordinates.add(tree.getModule());
        }
        final Map<Path, List<ResolvedArtifact>> result =
                new LinkedHashMap<>();
        for (Map.Entry<Path, ModuleDependencyTree> entry
                : treeByDirectory.entrySet()) {
            final ModuleDependencyTree tree = entry.getValue();
            final ResolvedArtifactManifest manifest =
                    manifestByDirectory.get(entry.getKey());
            validateBindings(tree, manifest, reactorCoordinates);
            result.put(entry.getKey(), manifest.getArtifacts());
        }
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(result));
    }

    private void validateBindings(
            final ModuleDependencyTree tree,
            final ResolvedArtifactManifest manifest,
            final Set<ArtifactCoord> reactorCoordinates)
            throws DependencyAnalysisException {
        final Set<String> expected = new LinkedHashSet<>();
        collectExternalBindings(tree.getDependencies(),
                reactorCoordinates, expected);
        final Set<String> actual = new LinkedHashSet<>();
        for (ResolvedArtifact artifact : manifest.getArtifacts()) {
            actual.add(artifact.bindingKey());
        }
        if (!expected.equals(actual)) {
            final Set<String> missing = new LinkedHashSet<>(expected);
            missing.removeAll(actual);
            final Set<String> unexpected = new LinkedHashSet<>(actual);
            unexpected.removeAll(expected);
            throw new DependencyAnalysisException(
                    "GraphML and JSON external dependencies differ: module="
                            + tree.getModule() + "; missing=" + missing
                            + "; unexpected=" + unexpected);
        }
    }

    private void collectExternalBindings(
            final List<DependencyNode> nodes,
            final Set<ArtifactCoord> reactorCoordinates,
            final Set<String> bindings) {
        for (DependencyNode node : nodes) {
            if (!reactorCoordinates.contains(node.getArtifact())) {
                bindings.add(node.getArtifact().toString());
            }
            collectExternalBindings(node.getChildren(),
                    reactorCoordinates, bindings);
        }
    }

    private Path canonicalDirectory(final Path directory)
            throws DependencyAnalysisException {
        try {
            return directory.toRealPath();
        } catch (IOException exception) {
            throw new DependencyAnalysisException(
                    "Module directory is unavailable: " + directory,
                    exception);
        }
    }

    private List<Path> findNamedFiles(
            final Path root,
            final String name) throws IOException {
        final List<Path> result = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> name.equals(
                            path.getFileName().toString()))
                    .forEach(result::add);
        }
        result.sort(Comparator.comparing(Path::toString));
        return result;
    }

    private void deleteFiles(final List<Path> files) {
        for (Path file : files) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException exception) {
                diag.warn(diagnosticContext,
                        "Unable to remove dependency evidence: " + file);
            }
        }
    }

    private List<String> dependencyArguments() {
        return pluginRuntime == null
                ? mavenArguments : pluginRuntime.getMavenArguments();
    }

    private String dependencyGoal(final String goalName) {
        return pluginRuntime == null
                ? FALLBACK_PLUGIN_PREFIX + goalName
                : pluginRuntime.getGoal(goalName);
    }

    private String artifactPathGoal() {
        return pluginRuntime == null
                ? FALLBACK_ARTIFACT_PATH_GOAL
                : pluginRuntime.getArtifactPathGoal();
    }
}
