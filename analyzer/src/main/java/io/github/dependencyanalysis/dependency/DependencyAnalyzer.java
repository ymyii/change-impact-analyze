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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

// Wiki: wiki/c4/components/dependency-analyzer-cli-evidence-ingestion.md - 摄取边界
/** Executes Maven dependency evidence collection for one reactor closure. */
public final class DependencyAnalyzer {

    /** Diagnostic stage name. */
    private static final String STAGE = "dependency";

    /** Dependency Evidence JSON filename suffix. */
    private static final String EVIDENCE_SUFFIX = ".json";

    /** Dependency Evidence owner marker. */
    private static final String OWNER_MARKER = ".cia-evidence-owner";

    /** Built-in Dependency Evidence Plugin goal. */
    private static final String FALLBACK_EVIDENCE_GOAL =
            MavenDependencyPluginRuntime.DEPENDENCY_EVIDENCE_PLUGIN_GOAL;

    /** Failure output tail retained in memory. */
    private static final int TAIL_LINES = 20;

    /** Side identifier. */
    private final String side;

    /** Workspace root. */
    private final Path workspacePath;

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

    /** Optional command-owned evidence cache parent. */
    private Path evidenceDirectory;

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
     * Selects the diagnostic Stage context.
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
     * Selects a command-owned dependency evidence cache parent.
     *
     * @param directory cache parent outside the source workspace
     * @return this analyzer
     */
    public DependencyAnalyzer withEvidenceDirectory(
            final Path directory) {
        evidenceDirectory = directory.toAbsolutePath().normalize();
        return this;
    }

    /**
     * Collects dependency trees projected from Schema v3 evidence.
     *
     * @return module dependency trees
     * @throws DependencyAnalysisException Maven or parsing failure
     * @throws IOException output discovery failure
     * @throws InterruptedException interrupted process
     */
    public List<ModuleDependencyTree> analyze()
            throws DependencyAnalysisException, IOException,
            InterruptedException {
        final List<ModuleDependencyTree> result = new ArrayList<>();
        for (ModuleDependencyEvidence evidence
                : analyzeResolved().getModules()) {
            result.add(new ModuleDependencyTree(
                    evidence.getModule(), evidence.getModulePath(),
                    evidence.getDependencies(),
                    evidence.getOccurrenceGraph()));
        }
        return List.copyOf(result);
    }

    /**
     * Collects selected dependency trees, winner-normalized raw occurrence
     * topology, reactor keys, and physical artifact bindings in one Maven
     * session.
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
        final EvidenceRun run = prepareEvidenceRun();
        try {
            final ProcessConsoleResult execution = runMaven(run);
            requireSuccessful(execution, run.directory);
            final List<Path> jsonFiles = evidenceFiles(run.directory);
            if (jsonFiles.isEmpty()) {
                throw new DependencyAnalysisException(
                        "No dependency evidence found in cache: "
                                + run.directory);
            }
            final List<ModuleDependencyEvidence> modules =
                    parseEvidence(jsonFiles);
            finish(modules.size());
            return new DependencyAnalysisResult(modules);
        } finally {
            deleteEvidenceRun(run);
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
            final EvidenceRun run) throws IOException,
            InterruptedException {
        final List<String> command = new ArrayList<>();
        command.add(mavenExecutable.toString());
        command.addAll(dependencyArguments());
        command.addAll(projectArguments);
        command.add(evidenceGoal());
        command.add("-Dcia.dependencyEvidenceDirectory="
                + run.directory);
        command.add("-Dcia.dependencyEvidenceOwner=" + run.owner);
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
            final Path outputDirectory)
            throws DependencyAnalysisException {
        if (execution.exitCode() == 0) {
            return;
        }
        diag.failStage(diagnosticContext,
                "reason=Dependency evidence generation failed; side=" + side
                        + " exitCode=" + execution.exitCode());
        final String command = "mvn " + evidenceGoal()
                + " -Dcia.dependencyEvidenceDirectory=" + outputDirectory
                + " -Dcia.dependencyEvidenceOwner=<redacted> -B";
        throw new DependencyAnalysisException(side,
                workspacePath.toString(), command,
                execution.exitCode(), execution.outputTail());
    }

    private List<ModuleDependencyEvidence> parseEvidence(
            final List<Path> files) throws DependencyAnalysisException {
        final List<ModuleDependencyEvidence> result = new ArrayList<>();
        final Set<String> modules = new LinkedHashSet<>();
        for (Path file : files) {
            diag.info(diagnosticContext, "Parsing evidence: " + file);
            final ModuleDependencyEvidence evidence =
                    DependencyEvidenceJsonParser.parse(file);
            if (!modules.add(evidence.getModule().diffKey())) {
                throw new DependencyAnalysisException(
                        "Duplicate dependency evidence Module: "
                                + evidence.getModule().diffKey());
            }
            result.add(evidence);
        }
        result.sort(Comparator.comparing(value ->
                value.getModulePath().toString()));
        return List.copyOf(result);
    }

    private List<Path> evidenceFiles(final Path root) throws IOException {
        final List<Path> result = new ArrayList<>();
        try (Stream<Path> stream = Files.list(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .startsWith("module-"))
                    .filter(path -> path.getFileName().toString()
                            .endsWith(EVIDENCE_SUFFIX))
                    .forEach(result::add);
        }
        result.sort(Comparator.comparing(Path::toString));
        return result;
    }

    private EvidenceRun prepareEvidenceRun() throws IOException {
        final Path parent;
        if (evidenceDirectory == null) {
            parent = Files.createTempDirectory("cia-dependency-evidence-");
        } else {
            Files.createDirectories(evidenceDirectory);
            parent = evidenceDirectory;
        }
        final Path run = parent.resolve(UUID.randomUUID().toString());
        Files.createDirectory(run);
        final String owner = UUID.randomUUID().toString();
        Files.writeString(run.resolve(OWNER_MARKER), owner,
                StandardCharsets.UTF_8);
        return new EvidenceRun(run, owner,
                evidenceDirectory == null ? parent : null);
    }

    private void deleteEvidenceRun(final EvidenceRun run) {
        try {
            deleteTree(run.directory);
            if (run.temporaryParent != null) {
                Files.deleteIfExists(run.temporaryParent);
            }
        } catch (IOException exception) {
            diag.warn(diagnosticContext,
                    "Unable to remove dependency evidence cache: "
                            + run.directory);
        }
    }

    private void deleteTree(final Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            final Path[] paths = stream.sorted(Comparator.reverseOrder())
                    .toArray(Path[]::new);
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        }
    }

    private List<String> dependencyArguments() {
        return pluginRuntime == null
                ? mavenArguments : pluginRuntime.getMavenArguments();
    }

    private String evidenceGoal() {
        return pluginRuntime == null
                ? FALLBACK_EVIDENCE_GOAL
                : pluginRuntime.getDependencyEvidenceGoal();
    }

    /** One Analyzer-owned evidence run. */
    private static final class EvidenceRun {

        /** Output directory. */
        private final Path directory;

        /** Owner token. */
        private final String owner;

        /** OS temporary parent, null for command cache. */
        private final Path temporaryParent;

        EvidenceRun(
                final Path outputDirectory,
                final String ownerToken,
                final Path osTemporaryParent) {
            directory = outputDirectory;
            owner = ownerToken;
            temporaryParent = osTemporaryParent;
        }
    }
}
