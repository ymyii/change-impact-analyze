package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.reactor.ReactorDescriptor;
import io.github.dependencyanalysis.reactor.ReactorScopeMode;
import io.github.dependencyanalysis.reactor.RepositoryInventory;
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
        .MavenRuntimeException;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

// Wiki: wiki/features/repository-dependency-tree-report.md - collection scope
/** Sequential Maven dependency tree collector. */
public final class TreeDependencyCollector {

    /** Maximum Maven diagnostic lines. */
    private static final int TAIL_LINES = 100;

    /** Classpath Evidence JSON prefix. */
    private static final String CLASSPATH_PREFIX = "classpath-module-";

    /** Evidence owner marker. */
    private static final String OWNER_MARKER = ".cia-evidence-owner";

    /** Inventory Module coordinate segment count. */
    private static final int MODULE_COORDINATE_SEGMENTS = 3;

    /** Optional command diagnostics. */
    private final DiagnosticLog diagnostics;

    /** Creates a collector without Console Maven forwarding. */
    public TreeDependencyCollector() {
        diagnostics = null;
    }

    /**
     * Creates a collector with Console Maven forwarding.
     *
     * @param diagnosticLog command diagnostics
     */
    public TreeDependencyCollector(final DiagnosticLog diagnosticLog) {
        diagnostics = diagnosticLog;
    }

    /**
     * Collects a full reactor, one owned leaf, or one standalone project.
     *
     * @param snapshot repository snapshot
     * @param inventory repository inventory
     * @param reactor reactor descriptor
     * @param runtime Maven runtime
     * @param mavenArguments safe user arguments
     * @param scopes included scopes
     * @param pluginVersion optional override
     * @return reactor result
     */
    public ReactorTreeResult collect(
            final RepositorySnapshot snapshot,
            final RepositoryInventory inventory,
            final ReactorDescriptor reactor,
            final MavenRuntimeDescriptor runtime,
            final List<String> mavenArguments,
            final Set<String> scopes,
            final String pluginVersion) {
        try (MavenDependencyPluginRuntime pluginRuntime =
                     new MavenDependencyPluginRuntimeManager()
                             .prepare(runtime.getConfigDir(),
                                     mavenArguments,
                                     pluginVersion,
                                     runtime.getDefaultGlobalSettings())) {
            return collect(snapshot, inventory, reactor,
                    runtime, pluginRuntime, scopes, null);
        } catch (java.io.IOException exception) {
            throw new MavenRuntimeException(
                    "Unable to clean Maven Plugin runtime", exception);
        }
    }

    /**
     * Collects using a command-prepared plugin runtime.
     *
     * @param snapshot repository snapshot
     * @param inventory repository inventory
     * @param reactor reactor descriptor
     * @param runtime Maven runtime
     * @param pluginRuntime prepared dependency plugin
     * @param scopes included scopes
     * @return reactor result
     */
    public ReactorTreeResult collect(
            final RepositorySnapshot snapshot,
            final RepositoryInventory inventory,
            final ReactorDescriptor reactor,
            final MavenRuntimeDescriptor runtime,
            final MavenDependencyPluginRuntime pluginRuntime,
            final Set<String> scopes) {
        return collect(snapshot, inventory, reactor, runtime,
                pluginRuntime, scopes, null);
    }

    /**
     * Collects dependency and classpath evidence into a command cache.
     *
     * @param snapshot repository snapshot
     * @param inventory repository inventory
     * @param reactor reactor descriptor
     * @param runtime Maven runtime
     * @param pluginRuntime prepared Plugin runtime
     * @param scopes included scopes
     * @param evidenceParent command-owned cache parent, nullable
     * @return reactor result
     */
    public ReactorTreeResult collect(
            final RepositorySnapshot snapshot,
            final RepositoryInventory inventory,
            final ReactorDescriptor reactor,
            final MavenRuntimeDescriptor runtime,
            final MavenDependencyPluginRuntime pluginRuntime,
            final Set<String> scopes,
            final Path evidenceParent) {
        final Set<DependencyKey> reactorKeys =
                reactorKeys(inventory, reactor);
        final List<ModuleTreeResult> modules =
                new ArrayList<>();
        final List<String> reasons =
                new ArrayList<>();
        final String outputName =
                "dependency-analyzer-"
                        + UUID.randomUUID() + ".txt";
        final Map<Path, Path> outputs = outputs(
                snapshot, reactor, outputName);
        EvidenceRun evidenceRun = null;
        MavenExecutionResult execution = null;
        Exception executionFailure = null;
        final DiagnosticContext mavenContext = DiagnosticContext.of(
                "analysis", "maven-collection")
                .with("reactor", reactor.getId());
        try {
            if (diagnostics != null) {
                diagnostics.startStage(mavenContext,
                        "goals=compile,dependency-tree,classpath-evidence");
            }
            evidenceRun = prepareEvidenceRun(evidenceParent);
            execution = executeReactor(snapshot,
                    inventory, reactor, runtime,
                    pluginRuntime, new MavenCollectionSpec(
                            outputName, evidenceRun, scopes));
            if (diagnostics != null) {
                if (execution.getExitCode() == 0) {
                    diagnostics.endStage(mavenContext);
                } else {
                    diagnostics.failStage(mavenContext, "exitCode="
                            + execution.getExitCode());
                }
            }
        } catch (Exception exception) {
            executionFailure = exception;
            if (diagnostics != null) {
                diagnostics.failStage(mavenContext,
                        "reason=" + message(exception));
            }
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
        final boolean complete =
                MavenDependencyPluginRuntimeManager
                .supportsCompleteEvidence(
                        pluginRuntime.getVersion());
        boolean failed = executionFailure != null
                || execution == null
                || execution.getExitCode() != 0;
        boolean degraded = false;
        if (executionFailure != null) {
            reasons.add("Reactor Maven execution failed: "
                    + message(executionFailure));
        } else if (execution != null
                && execution.getExitCode() != 0) {
            reasons.add("Reactor Maven execution failed: "
                    + diagnosticTail(
                            execution.getCombinedOutput()));
        }
        try {
            final Map<Path, ModuleClasspathEvidence> evidenceByPom =
                    evidenceRun == null ? Map.of()
                            : parseEvidence(snapshot, reactor, evidenceRun);
            final ModuleCollectionResult collected = collectModules(
                    inventory, reactor, outputs, evidenceByPom,
                    reactorKeys, scopes, complete);
            modules.addAll(collected.modules());
            reasons.addAll(collected.reasons());
            failed = failed || collected.failed();
            degraded = collected.degraded();
        } catch (java.io.IOException exception) {
            failed = true;
            reasons.add("Classpath evidence parsing failed: "
                    + message(exception));
        } finally {
            cleanup(outputs.values());
            cleanupEvidence(evidenceRun);
        }
        final ReactorStatus status = failed
                ? ReactorStatus.FAILED
                : degraded ? ReactorStatus.DEGRADED
                : ReactorStatus.SUCCESS;
        return new ReactorTreeResult(
                reactor, modules, status,
                String.join("; ", reasons));
    }

    private ModuleCollectionResult collectModules(
            final RepositoryInventory inventory,
            final ReactorDescriptor reactor,
            final Map<Path, Path> outputs,
            final Map<Path, ModuleClasspathEvidence> evidenceByPom,
            final Set<DependencyKey> reactorKeys,
            final Set<String> scopes,
            final boolean complete) {
        final List<ModuleTreeResult> modules = new ArrayList<>();
        final List<String> reasons = new ArrayList<>();
        boolean failed = false;
        boolean degraded = false;
        final List<Path> initialPoms = inventory.analysisPoms(
                reactor, reactor.getScopeMode()
                        == ReactorScopeMode.FULL_REACTOR
                        ? reactor.getActivePoms()
                        : reactor.getRequestedPoms());
        final ModuleAnalysisRole initialRole = reactor.getScopeMode()
                == ReactorScopeMode.FULL_REACTOR
                ? ModuleAnalysisRole.REACTOR_ROOT_SCOPE
                : ModuleAnalysisRole.REQUESTED;
        final ModuleCollectionContext context = new ModuleCollectionContext(
                inventory, outputs, evidenceByPom, reactorKeys,
                scopes, complete);
        for (Path pom : initialPoms) {
            final ModuleTreeResult module = collectModule(
                    pom, initialRole, context);
            modules.add(module);
            failed |= !module.getFailure().isBlank();
            degraded |= !module.getClasspathIssues().isEmpty() || !complete;
            addModuleReasons(reasons, module, complete);
        }
        return new ModuleCollectionResult(modules, reasons, failed, degraded);
    }

    private ModuleTreeResult collectModule(
            final Path pom,
            final ModuleAnalysisRole role,
            final ModuleCollectionContext context) {
        try {
            final Path output = context.outputs().get(pom);
            if (!Files.isRegularFile(output) || Files.size(output) == 0L) {
                throw new IllegalStateException(
                        "Dependency tree output is unavailable");
            }
            final ParsedModuleTree parsed;
            try (java.io.BufferedReader reader = Files.newBufferedReader(
                    output, StandardCharsets.UTF_8)) {
                parsed = new DependencyTextParser().parse(
                        reader, context.reactorKeys(), context.scopes());
            }
            final ModuleClassConflictAnalyzer.Analysis classes =
                    analyzeClasses(pom, context.inventory(),
                            context.evidenceByPom().get(pom));
            return new ModuleTreeResult(pom, parsed.getRootCoordinate(),
                    parsed.getOccurrences(), context.complete(), "", role)
                    .withClassAnalysis(classes.conflicts(), classes.issues());
        } catch (Exception exception) {
            return new ModuleTreeResult(pom,
                    context.inventory().coordinateOf(pom),
                    List.of(), false, message(exception), role);
        }
    }

    private void addModuleReasons(
            final List<String> reasons,
            final ModuleTreeResult module,
            final boolean complete) {
        if (!module.getFailure().isBlank()) {
            reasons.add(module.getPom() + ": " + module.getFailure());
        }
        if (!complete) {
            reasons.add(module.getPom()
                    + ": verbose/managed evidence unavailable");
        }
        if (!module.getClasspathIssues().isEmpty()) {
            reasons.add(module.getPom() + ": " + String.join(", ",
                    module.getClasspathIssues()));
        }
    }

    private Map<Path, Path> outputs(
            final RepositorySnapshot snapshot,
            final ReactorDescriptor reactor,
            final String outputName) {
        final Map<Path, Path> result =
                new LinkedHashMap<>();
        for (Path pom : reactor.getActivePoms()) {
            final Path target = snapshot.getRoot()
                    .resolve(pom).getParent()
                    .resolve("target");
            if (!reactor.requiresProjectSelection()) {
                try {
                    Files.createDirectories(target);
                } catch (Exception exception) {
                    throw new IllegalStateException(
                            "Unable to prepare module output: "
                                    + pom, exception);
                }
            }
            result.put(pom, target.resolve(outputName));
        }
        return result;
    }

    private MavenExecutionResult executeReactor(
            final RepositorySnapshot snapshot,
            final RepositoryInventory inventory,
            final ReactorDescriptor reactor,
            final MavenRuntimeDescriptor runtime,
            final MavenDependencyPluginRuntime pluginRuntime,
            final MavenCollectionSpec spec)
            throws Exception {
        final Path pom = snapshot.getRoot()
                .resolve(reactor.getRootPom());
        final List<String> arguments =
                new ArrayList<>(pluginRuntime
                        .getMavenArguments());
        arguments.add("-B");
        arguments.add("-f");
        arguments.add(pom.toString());
        if (reactor.requiresProjectSelection()) {
            arguments.add("-pl");
            arguments.add(String.join(",",
                    projectSelectors(reactor)));
            arguments.add("-am");
        }
        arguments.add("compile");
        arguments.add(pluginRuntime.getGoal());
        arguments.add(pluginRuntime.getClasspathEvidenceGoal());
        arguments.add("-DoutputFile="
                + Path.of("target", spec.outputName()));
        arguments.add("-DoutputType=text");
        arguments.add("-DappendOutput=false");
        arguments.add("-Dverbose=true");
        arguments.add("-Dtokens=standard");
        arguments.add("-Dcia.classpathEvidenceDirectory="
                + spec.evidenceRun().directory());
        arguments.add("-Dcia.classpathEvidenceOwner="
                + spec.evidenceRun().owner());
        arguments.add("-Dcia.classpathEvidenceScopes="
                + String.join(",", spec.scopes()));
        if (diagnostics == null) {
            return new MavenExecutor().execute(
                    runtime, snapshot.getRoot(), arguments);
        }
        return new MavenExecutor().execute(
                runtime, snapshot.getRoot(), arguments, diagnostics,
                DiagnosticContext.of("analysis", "reactor")
                        .with("reactor", reactor.getId()),
                TAIL_LINES);
    }

    private List<String> projectSelectors(
            final ReactorDescriptor reactor) {
        final Path rootDirectory = reactor.getRootPom().getParent() == null
                ? Path.of("") : reactor.getRootPom().getParent();
        return reactor.getRequestedPoms().stream()
                .sorted(Comparator.comparing(Path::toString))
                .map(pom -> pom.getParent() == null
                        ? Path.of("") : pom.getParent())
                .map(rootDirectory::relativize)
                .map(Path::toString)
                .toList();
    }

    private Set<DependencyKey> reactorKeys(
            final RepositoryInventory inventory,
            final ReactorDescriptor reactor) {
        final Set<DependencyKey> result =
                new HashSet<>();
        for (Path pom : reactor.getActivePoms()) {
            final String[] coordinate = inventory
                    .coordinateOf(pom).split(":", -1);
            if (coordinate.length >= 2) {
                result.add(new DependencyKey(
                        coordinate[0], coordinate[1],
                        inventory.packagingOf(pom), ""));
                result.add(new DependencyKey(
                        coordinate[0], coordinate[1],
                        "jar", ""));
                result.add(new DependencyKey(
                        coordinate[0], coordinate[1],
                        "pom", ""));
            }
        }
        return result;
    }

    static String diagnosticTail(final String output) {
        final List<String> lines = new ArrayList<>(
                output.lines().toList());
        while (!lines.isEmpty()
                && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
        final int start = Math.max(0,
                lines.size() - TAIL_LINES);
        return String.join(System.lineSeparator(),
                lines.subList(start, lines.size()));
    }

    private String message(
            final Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private EvidenceRun prepareEvidenceRun(final Path parentValue)
            throws java.io.IOException {
        final boolean ownsParent = parentValue == null;
        final Path parent = ownsParent
                ? Files.createTempDirectory("cia-tree-classpath-")
                : parentValue.toAbsolutePath().normalize();
        Files.createDirectories(parent);
        final Path directory = parent.resolve(
                "classpath-evidence-" + UUID.randomUUID()).normalize();
        Files.createDirectory(directory);
        final String owner = UUID.randomUUID().toString();
        Files.writeString(directory.resolve(OWNER_MARKER), owner,
                StandardCharsets.UTF_8);
        return new EvidenceRun(directory, owner,
                ownsParent ? parent : null);
    }

    private Map<Path, ModuleClasspathEvidence> parseEvidence(
            final RepositorySnapshot snapshot,
            final ReactorDescriptor reactor,
            final EvidenceRun run) throws java.io.IOException {
        final Map<Path, Path> pomByDirectory = new LinkedHashMap<>();
        for (Path pom : reactor.getActivePoms()) {
            final Path absolutePom = snapshot.getRoot().resolve(pom);
            pomByDirectory.put(absolutePom.getParent().toRealPath(), pom);
        }
        final Map<Path, ModuleClasspathEvidence> result =
                new LinkedHashMap<>();
        final List<Path> files;
        try (Stream<Path> stream = Files.list(run.directory())) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .startsWith(CLASSPATH_PREFIX))
                    .filter(path -> path.getFileName().toString()
                            .endsWith(".json"))
                    .sorted(Comparator.comparing(Path::toString)).toList();
        }
        for (Path file : files) {
            final ModuleClasspathEvidence evidence =
                    ClasspathEvidenceJsonParser.parse(file);
            final Path pom = pomByDirectory.get(evidence.moduleDirectory());
            if (pom == null) {
                throw new java.io.IOException(
                        "Classpath evidence Module is outside Reactor: "
                                + evidence.moduleDirectory());
            }
            if (result.putIfAbsent(pom, evidence) != null) {
                throw new java.io.IOException(
                        "Duplicate classpath evidence for Module: " + pom);
            }
        }
        return result;
    }

    private ModuleClassConflictAnalyzer.Analysis analyzeClasses(
            final Path pom,
            final RepositoryInventory inventory,
            final ModuleClasspathEvidence evidence) {
        if (evidence == null) {
            throw new IllegalStateException(
                    "Classpath evidence is unavailable");
        }
        final String[] expected = inventory.coordinateOf(pom)
                .split(":", -1);
        if (expected.length < MODULE_COORDINATE_SEGMENTS
                || !expected[0].equals(evidence.module().getGroupId())
                || !expected[1].equals(evidence.module().getArtifactId())
                || !expected[2].equals(evidence.module().getVersion())) {
            throw new IllegalStateException(
                    "Classpath evidence Module identity does not match: "
                            + inventory.coordinateOf(pom));
        }
        return new ModuleClassConflictAnalyzer(diagnostics).analyze(evidence);
    }

    private void cleanupEvidence(final EvidenceRun run) {
        if (run == null) {
            return;
        }
        try {
            deleteTree(run.directory());
            if (run.temporaryParent() != null) {
                Files.deleteIfExists(run.temporaryParent());
            }
        } catch (java.io.IOException exception) {
            if (diagnostics != null) {
                diagnostics.warn(DiagnosticContext.of(
                        "analysis", "classpath-evidence"),
                        "Unable to remove classpath evidence cache: "
                                + run.directory());
            }
        }
    }

    private void deleteTree(final Path root) throws java.io.IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder())
                    .toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private void cleanup(
            final java.util.Collection<Path> outputs) {
        for (Path output : outputs) {
            try {
                Files.deleteIfExists(output);
            } catch (Exception ignored) {
                // Best-effort tool-owned output cleanup.
            }
        }
    }

    /**
     * One owner-marked Classpath Evidence run.
     *
     * @param directory evidence directory
     * @param owner owner token
     * @param temporaryParent owned temporary parent, nullable
     */
    private record EvidenceRun(
            Path directory,
            String owner,
            Path temporaryParent) {
    }

    /**
     * Maven command-specific output properties.
     *
     * @param outputName dependency tree filename
     * @param evidenceRun owner-marked evidence run
     * @param scopes selected Maven scopes
     */
    private record MavenCollectionSpec(
            String outputName,
            EvidenceRun evidenceRun,
            Set<String> scopes) {
    }

    /**
     * Shared inputs for Module parsing and conflict analysis.
     *
     * @param inventory repository inventory
     * @param outputs dependency tree outputs
     * @param evidenceByPom classpath evidence by POM
     * @param reactorKeys Reactor dependency identities
     * @param scopes selected Maven scopes
     * @param complete complete mediation flag
     */
    private record ModuleCollectionContext(
            RepositoryInventory inventory,
            Map<Path, Path> outputs,
            Map<Path, ModuleClasspathEvidence> evidenceByPom,
            Set<DependencyKey> reactorKeys,
            Set<String> scopes,
            boolean complete) {
    }

    /**
     * Collected Module results and aggregate status flags.
     *
     * @param modules Module results
     * @param reasons Reactor reasons
     * @param failed whether a Module failed
     * @param degraded whether evidence is incomplete
     */
    private record ModuleCollectionResult(
            List<ModuleTreeResult> modules,
            List<String> reasons,
            boolean failed,
            boolean degraded) {
    }
}
