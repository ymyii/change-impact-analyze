package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

// Wiki: wiki/features/repository-dependency-tree-report.md - collection scope
/** Sequential Maven dependency tree collector. */
public final class TreeDependencyCollector {

    /** Maximum Maven diagnostic lines. */
    private static final int TAIL_LINES = 100;

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
     * Collects a full reactor or a requested dependency closure.
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
                                     pluginVersion)) {
            return collect(snapshot, inventory, reactor,
                    runtime, pluginRuntime, scopes);
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
        MavenExecutionResult execution = null;
        Exception executionFailure = null;
        try {
            execution = executeReactor(snapshot,
                    inventory, reactor, runtime,
                    pluginRuntime, outputName);
        } catch (Exception exception) {
            executionFailure = exception;
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
            final List<Path> initialPoms = inventory
                    .analysisPoms(reactor, reactor
                            .isRootSelected()
                            ? reactor.getActivePoms()
                            : reactor.getRequestedPoms());
            final ModuleAnalysisRole initialRole = reactor
                    .isRootSelected()
                    ? ModuleAnalysisRole.REACTOR_ROOT_SCOPE
                    : ModuleAnalysisRole.REQUESTED;
            for (Path pom : initialPoms) {
                final Path output = outputs.get(pom);
                try {
                    if (!Files.isRegularFile(output)
                            || Files.size(output) == 0L) {
                        throw new IllegalStateException(
                                "Dependency tree output"
                                        + " is unavailable");
                    }
                    final ParsedModuleTree parsed;
                    try (java.io.BufferedReader reader =
                                 Files.newBufferedReader(output,
                                         StandardCharsets.UTF_8)) {
                        parsed = new DependencyTextParser().parse(
                                reader, reactorKeys, scopes);
                    }
                    if (!complete) {
                        degraded = true;
                        reasons.add(pom
                                + ": verbose/managed"
                                + " evidence unavailable");
                    }
                    modules.add(new ModuleTreeResult(
                            pom, parsed.getRootCoordinate(),
                            parsed.getOccurrences(),
                            complete, "", initialRole));
                } catch (Exception exception) {
                    failed = true;
                    reasons.add(pom + ": "
                            + message(exception));
                    modules.add(new ModuleTreeResult(
                            pom, inventory.coordinateOf(pom),
                            List.of(), false,
                            message(exception), initialRole));
                }
            }
            if (!reactor.isRootSelected()) {
                for (Path pom : inventory.analysisPoms(
                        reactor, dependencyPoms(
                                inventory, reactor,
                                modules))) {
                    final Path output = outputs.get(pom);
                    try {
                        if (!Files.isRegularFile(output)
                                || Files.size(output) == 0L) {
                            throw new IllegalStateException(
                                    "Dependency tree output"
                                            + " is unavailable");
                        }
                        final ParsedModuleTree parsed;
                        try (java.io.BufferedReader reader =
                                     Files.newBufferedReader(output,
                                             StandardCharsets.UTF_8)) {
                            parsed = new DependencyTextParser().parse(
                                    reader, reactorKeys, scopes);
                        }
                        if (!complete) {
                            degraded = true;
                            reasons.add(pom
                                    + ": verbose/managed"
                                    + " evidence unavailable");
                        }
                        modules.add(new ModuleTreeResult(
                                pom, parsed.getRootCoordinate(),
                                parsed.getOccurrences(), complete,
                                "", ModuleAnalysisRole.DEPENDENCY));
                    } catch (Exception exception) {
                        failed = true;
                        reasons.add(pom + ": "
                                + message(exception));
                        modules.add(new ModuleTreeResult(
                                pom, inventory.coordinateOf(pom),
                                List.of(), false,
                                message(exception),
                                ModuleAnalysisRole.DEPENDENCY));
                    }
                }
            }
        } finally {
            cleanup(outputs.values());
        }
        final ReactorStatus status = failed
                ? ReactorStatus.FAILED
                : degraded ? ReactorStatus.DEGRADED
                : ReactorStatus.SUCCESS;
        return new ReactorTreeResult(
                reactor, modules, status,
                String.join("; ", reasons));
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
            if (reactor.isRootSelected()) {
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
            final String outputName)
            throws Exception {
        final Path pom = snapshot.getRoot()
                .resolve(reactor.getRootPom());
        final List<String> arguments =
                new ArrayList<>(pluginRuntime
                        .getMavenArguments());
        arguments.add("-B");
        arguments.add("-f");
        arguments.add(pom.toString());
        if (!reactor.isRootSelected()) {
            arguments.add("-pl");
            arguments.add(String.join(",",
                    projectSelectors(inventory, reactor)));
            arguments.add("-am");
        }
        arguments.add(pluginRuntime.getGoal());
        arguments.add("-DoutputFile="
                + Path.of("target", outputName));
        arguments.add("-DoutputType=text");
        arguments.add("-DappendOutput=false");
        arguments.add("-Dverbose=true");
        arguments.add("-Dtokens=standard");
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
            final RepositoryInventory inventory,
            final ReactorDescriptor reactor) {
        return reactor.getRequestedPoms().stream()
                .sorted(Comparator.comparing(Path::toString))
                .map(inventory::coordinateOf)
                .map(this::projectSelector)
                .toList();
    }

    private String projectSelector(
            final String coordinate) {
        final String[] parts = coordinate.split(":", -1);
        if (parts.length < 2
                || parts[0].isBlank()
                || parts[1].isBlank()) {
            throw new IllegalStateException(
                    "Unable to create Maven project selector: "
                            + coordinate);
        }
        return parts[0] + ":" + parts[1];
    }

    private List<Path> dependencyPoms(
            final RepositoryInventory inventory,
            final ReactorDescriptor reactor,
            final List<ModuleTreeResult> requestedModules) {
        final Map<String, Path> activeByCoordinate =
                new LinkedHashMap<>();
        for (Path pom : reactor.getActivePoms()) {
            activeByCoordinate.putIfAbsent(
                    inventory.coordinateOf(pom), pom);
        }
        final Set<Path> requested = new HashSet<>(
                reactor.getRequestedPoms());
        final Set<Path> dependencies =
                new LinkedHashSet<>();
        for (ModuleTreeResult module : requestedModules) {
            for (DependencyOccurrence occurrence
                    : module.getOccurrences()) {
                if (!occurrence.isSelected()
                        || !occurrence.isReactorModule()) {
                    continue;
                }
                final Path dependency = activeByCoordinate.get(
                        occurrence.getKey().getGroupId() + ":"
                                + occurrence.getKey()
                                .getArtifactId() + ":"
                                + occurrence.getEffectiveVersion());
                if (dependency != null
                        && !requested.contains(dependency)) {
                    dependencies.add(dependency);
                }
            }
        }
        return dependencies.stream()
                .sorted(Comparator.comparing(Path::toString))
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
}
