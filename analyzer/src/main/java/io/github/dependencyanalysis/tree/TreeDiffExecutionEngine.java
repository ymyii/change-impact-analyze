package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.cli.DependencyAnalyzerCli;
import io.github.dependencyanalysis.cli.MavenArguments;
import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.reactor.MavenActivationContext;
import io.github.dependencyanalysis.reactor.ReactorDescriptor;
import io.github.dependencyanalysis.reactor.ReactorInventoryBuilder;
import io.github.dependencyanalysis.reactor.RepositoryInventory;
import io.github.dependencyanalysis.runtime.CommandRunDirectory;
import io.github.dependencyanalysis.runtime.MavenDependencyPluginRuntime;
import io.github.dependencyanalysis.runtime.MavenDependencyPluginRuntimeManager;
import io.github.dependencyanalysis.runtime.MavenExecutionResult;
import io.github.dependencyanalysis.runtime.MavenExecutor;
import io.github.dependencyanalysis.runtime.MavenRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.MavenRuntimeManager;
import io.github.dependencyanalysis.runtime.MavenVersion;
import io.github.dependencyanalysis.runtime.ReportCache;
import io.github.dependencyanalysis.workspace.WorkspaceManager;
import io.github.dependencyanalysis.workspace.WorkspaceResult;
import io.github.dependencyanalysis.workspace.WorkspaceSideInfo;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

// Wiki: wiki/architecture/dependency-analysis-pipelines.md - Tree Diff boundary
// Wiki: wiki/features/repository-dependency-tree-diff.md - Tree Diff主流程
/** Executes two-sided dependency tree collection, diff, and publication. */
final class TreeDiffExecutionEngine {

    /** Maven output lines retained by version probing. */
    private static final int MAVEN_TAIL_LINES = 100;

    /** Allowed dependency scopes. */
    private static final Set<String> ALLOWED_SCOPES = Set.of(
            "compile", "runtime", "provided", "test", "system");

    /** Root options. */
    private final DependencyAnalyzerCli root;

    /** Shared tree options. */
    private final TreeCommonOptions common;

    /** Baseline commit-ish. */
    private final String baselineRef;

    /** Optional target commit-ish. */
    private final String targetRef;

    /** Diagnostics. */
    private final DiagnosticLog diagnostics;

    TreeDiffExecutionEngine(
            final DependencyAnalyzerCli command,
            final TreeCommonOptions options,
            final String baseline,
            final String target,
            final DiagnosticLog log) {
        root = Objects.requireNonNull(command, "command");
        common = Objects.requireNonNull(options, "options");
        baselineRef = baseline;
        targetRef = target;
        diagnostics = Objects.requireNonNull(log, "log");
    }

    /** @return stable CLI exit code */
    int execute() {
        TreeDiffReportSession session = null;
        try {
            validateArguments();
            final Path requestedPath = common.path().toPath()
                    .toRealPath().normalize();
            final RepositorySnapshot current = openCurrent(requestedPath);
            final CommandRunDirectory run = prepareRunDirectory();
            final WorkspaceManager workspaces = prepareWorkspaceManager(
                    requestedPath, run);
            try (current; run; workspaces) {
                final WorkspaceResult workspace = prepareWorkspaces(
                        workspaces);
                validateMappedPath(workspace.getBaseline());
                validateMappedPath(workspace.getTarget());
                final PreparedRuntime prepared = prepareRuntime(current);
                try (MavenDependencyPluginRuntime plugin =
                             preparePlugin(prepared);
                     ReportCache reportCache = new ReportCache(run, "tree")) {
                    final SideInventory baseline = inventory(
                            current, workspace.getBaseline(), baselineRef,
                            false, prepared);
                    final SideInventory target = inventory(
                            current, workspace.getTarget(), targetRef,
                            targetRef == null, prepared);
                    final TreeDiffReportMetadata metadata = metadata(
                            requestedPath, current.getAnalysisPath(),
                            workspace, prepared, plugin);
                    final Set<String> reactorKeys = reactorKeys(
                            baseline, target);
                    session = new TreeDiffReportRenderer().start(
                            metadata, reactorKeys.size(),
                            common.output().toPath());
                    final TreeDiffEngine engine = new TreeDiffEngine();
                    final TreeDiffReportCacheSpiller spiller =
                            new TreeDiffReportCacheSpiller();
                    int completed = 0;
                    for (String reactorKey : reactorKeys) {
                        completed++;
                        final DiagnosticContext context =
                                DiagnosticContext.of(
                                        "tree-diff", "compare")
                                        .with("reactor", reactorKey);
                        diagnostics.startStage(context, "progress="
                                + completed + "/" + reactorKeys.size());
                        final boolean comparableStructure =
                                baseline.descriptors()
                                        .containsKey(reactorKey)
                                && target.descriptors()
                                        .containsKey(reactorKey);
                        final TreeDiffSideReactor left = sideReactor(
                                "baseline", reactorKey, baseline,
                                prepared.runtime(), plugin,
                                comparableStructure);
                        final TreeDiffSideReactor right = sideReactor(
                                "target", reactorKey, target,
                                prepared.runtime(), plugin,
                                comparableStructure);
                        final String leftPrefix = spiller.spill(
                                "baseline", reactorKey,
                                left.collection(), reportCache);
                        final String rightPrefix = spiller.spill(
                                "target", reactorKey,
                                right.collection(), reportCache);
                        final TreeDiffReactorResult result = engine.diff(
                                reactorKey, left, right);
                        session.publish(result);
                        reportCache.discard(leftPrefix);
                        reportCache.discard(rightPrefix);
                        diagnostics.endStage(context, "modules="
                                + result.modules().size()
                                + "; comparable="
                                + result.comparableModules());
                    }
                    reportCache.complete();
                    session.complete();
                    final TreeDiffReportState state = session.state();
                    diagnostics.info(DiagnosticContext.of(
                                    "tree-diff", "summary"),
                            "Tree diff completed; state=" + state
                                    + "; report=" + common.output()
                                    .toPath().toAbsolutePath().normalize());
                    return state == TreeDiffReportState.SUCCESS ? 0 : 2;
                }
            }
        } catch (TreeDiffPreflightException exception) {
            diagnostics.error(DiagnosticContext.of(
                            "tree-diff", "preflight"),
                    exception.getMessage());
            return 1;
        } catch (Exception exception) {
            if (session != null
                    && session.state() == TreeDiffReportState.RUNNING) {
                try {
                    session.fail(message(exception));
                } catch (Exception reportFailure) {
                    exception.addSuppressed(reportFailure);
                }
            }
            diagnostics.error(DiagnosticContext.of(
                            "tree-diff", "failure"),
                    "Tree diff failed: " + message(exception));
            diagnostics.transientException(
                    DiagnosticContext.stage("tree-diff"), exception);
            return 2;
        }
    }

    private void validateArguments() throws TreeDiffPreflightException {
        if (baselineRef == null || baselineRef.isBlank()) {
            throw new TreeDiffPreflightException(
                    "Baseline commit-ish must not be blank");
        }
        if (targetRef != null && targetRef.isBlank()) {
            throw new TreeDiffPreflightException(
                    "Target commit-ish must not be blank when provided");
        }
        final Path path = common.path().toPath()
                .toAbsolutePath().normalize();
        if (!Files.isDirectory(path)) {
            throw new TreeDiffPreflightException(
                    "Analysis path is not a directory: " + path);
        }
        final Path pom = path.resolve("pom.xml");
        if (!Files.isRegularFile(pom) || !Files.isReadable(pom)) {
            throw new TreeDiffPreflightException(
                    "Analysis path must contain a readable pom.xml: "
                            + pom);
        }
        final Set<String> scopes = common.parsedScopes();
        if (scopes.isEmpty() || !ALLOWED_SCOPES.containsAll(scopes)) {
            throw new TreeDiffPreflightException(
                    "Scope filter is invalid: " + scopes);
        }
        final Path output = common.output().toPath()
                .toAbsolutePath().normalize();
        if (Files.exists(output) && !Files.isDirectory(output)) {
            throw new TreeDiffPreflightException(
                    "Output exists and is not a directory: " + output);
        }
        Path parent = Files.exists(output) ? output : output.getParent();
        while (parent != null && !Files.exists(parent)) {
            parent = parent.getParent();
        }
        if (parent == null || !Files.isDirectory(parent)
                || !Files.isWritable(parent)) {
            throw new TreeDiffPreflightException(
                    "Output directory cannot be created: " + output);
        }
    }

    private RepositorySnapshot openCurrent(final Path requestedPath)
            throws TreeDiffPreflightException {
        try {
            return new GitSnapshotProvider().open(requestedPath, null);
        } catch (Exception exception) {
            throw new TreeDiffPreflightException(
                    "Git repository preflight failed: "
                            + message(exception), exception);
        }
    }

    private CommandRunDirectory prepareRunDirectory()
            throws TreeDiffPreflightException {
        try {
            return new CommandRunDirectory(
                    root.getConfigDir().toPath(), "tree");
        } catch (Exception exception) {
            throw new TreeDiffPreflightException(
                    "Tree run directory preflight failed: "
                            + message(exception), exception);
        }
    }

    private WorkspaceManager prepareWorkspaceManager(
            final Path requestedPath,
            final CommandRunDirectory run)
            throws TreeDiffPreflightException {
        try {
            return new WorkspaceManager(requestedPath, diagnostics,
                    run.getWorkspaceDirectory());
        } catch (Exception exception) {
            try {
                run.close();
            } catch (Exception cleanup) {
                exception.addSuppressed(cleanup);
            }
            throw new TreeDiffPreflightException(
                    "Git workspace preflight failed: "
                            + message(exception), exception);
        }
    }

    private WorkspaceResult prepareWorkspaces(
            final WorkspaceManager workspaces)
            throws TreeDiffPreflightException {
        try {
            return workspaces.prepare(baselineRef, targetRef);
        } catch (Exception exception) {
            throw new TreeDiffPreflightException(
                    "Git commit-ish/worktree preflight failed: "
                            + message(exception), exception);
        }
    }

    private void validateMappedPath(final WorkspaceSideInfo side)
            throws TreeDiffPreflightException {
        final Path path = side.getPath().toAbsolutePath().normalize();
        final Path pom = path.resolve("pom.xml");
        if (!Files.isDirectory(path) || !Files.isRegularFile(pom)
                || !Files.isReadable(pom)) {
            throw new TreeDiffPreflightException(
                    "Analysis path is unavailable in "
                            + side.getSide() + " workspace: " + path);
        }
    }

    private PreparedRuntime prepareRuntime(
            final RepositorySnapshot current)
            throws TreeDiffPreflightException {
        try {
            final File javaHome = root.getJavaHome();
            if (javaHome != null && !javaHome.isDirectory()) {
                throw new TreeDiffPreflightException(
                        "Maven JAVA_HOME is not a directory: " + javaHome);
            }
            final File executable = root.getMaven();
            MavenRuntimeDescriptor runtime = new MavenRuntimeManager()
                    .prepare(executable == null ? null : executable.toPath(),
                            root.getConfigDir().toPath(),
                            javaHome == null ? null : javaHome.toPath());
            final List<String> arguments = MavenArguments.validate(
                    root.getMavenArguments(), current.getRoot());
            final MavenExecutionResult probe = new MavenExecutor().execute(
                    runtime, current.getRoot(), List.of("--version"),
                    diagnostics, DiagnosticContext.of(
                            "tree-diff", "maven-version"),
                    MAVEN_TAIL_LINES);
            if (probe.getExitCode() != 0) {
                throw new TreeDiffPreflightException(
                        "Maven executable cannot run: "
                                + probe.getCombinedOutput());
            }
            final MavenVersion version = MavenVersion.parse(
                    probe.getCombinedOutput());
            if (!version.isSupported()) {
                throw new TreeDiffPreflightException(
                        "Unsupported Maven version: " + version);
            }
            runtime = runtime.withProbe(version,
                    probe.getCombinedOutput());
            final MavenActivationContext activation =
                    MavenActivationContext.resolveFromMavenOutput(
                            arguments, probe.getCombinedOutput(),
                            runtime.getExecutable(),
                            runtime.getDefaultGlobalSettings());
            return new PreparedRuntime(runtime, arguments,
                    probe.getCombinedOutput(), activation);
        } catch (TreeDiffPreflightException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new TreeDiffPreflightException(
                    "Maven preflight failed: " + message(exception),
                    exception);
        }
    }

    private MavenDependencyPluginRuntime preparePlugin(
            final PreparedRuntime prepared)
            throws TreeDiffPreflightException {
        try {
            return new MavenDependencyPluginRuntimeManager().prepare(
                    prepared.runtime().getConfigDir(),
                    prepared.mavenArguments(), common.pluginVersion(),
                    prepared.runtime().getDefaultGlobalSettings());
        } catch (Exception exception) {
            throw new TreeDiffPreflightException(
                    "Maven Dependency Plugin preflight failed: "
                            + message(exception), exception);
        }
    }

    private SideInventory inventory(
            final RepositorySnapshot current,
            final WorkspaceSideInfo side,
            final String ref,
            final boolean currentWorkspace,
            final PreparedRuntime prepared) {
        final Path rootPath = sideRoot(side.getPath(),
                current.getAnalysisPath());
        final RepositorySnapshot snapshot = new RepositorySnapshot(
                rootPath, current.getRoot(),
                currentWorkspace ? "current workspace" : ref,
                side.getCommit(), currentWorkspace
                ? current.getBranch() : "detached",
                currentWorkspace && side.isDirty(), () -> { })
                .withAnalysisPath(current.getAnalysisPath());
        try {
            final RepositoryInventory inventory =
                    new ReactorInventoryBuilder().build(
                            snapshot.getRoot(), snapshot.getAnalysisPath(),
                            prepared.activation());
            final Map<String, ReactorDescriptor> descriptors =
                    new LinkedHashMap<>();
            inventory.getReactors().stream()
                    .sorted(java.util.Comparator.comparing(
                            ReactorDescriptor::getId))
                    .forEach(descriptor -> descriptors.put(
                            descriptor.getId(), descriptor));
            return new SideInventory(true, snapshot, inventory,
                    Map.copyOf(descriptors), "");
        } catch (Exception exception) {
            diagnostics.warn(DiagnosticContext.of(
                            "tree-diff", "inventory"),
                    "Inventory unavailable for " + side.getSide()
                            + ": " + message(exception));
            return new SideInventory(false, snapshot, null, Map.of(),
                    message(exception));
        }
    }

    private TreeDiffSideReactor sideReactor(
            final String sideName,
            final String reactorKey,
            final SideInventory side,
            final MavenRuntimeDescriptor runtime,
            final MavenDependencyPluginRuntime plugin,
            final boolean collect) {
        if (!side.available()) {
            return TreeDiffSideReactor.unavailable(side.issue());
        }
        final ReactorDescriptor descriptor =
                side.descriptors().get(reactorKey);
        if (descriptor == null) {
            return TreeDiffSideReactor.absent(side.inventory());
        }
        if (!collect) {
            return new TreeDiffSideReactor(TreeDiffSideState.PRESENT,
                    descriptor, side.inventory(), null, "");
        }
        try {
            final ReactorTreeResult result = new DependencyTreeCollector(
                    diagnostics).collect(side.snapshot(), side.inventory(),
                    descriptor, runtime, plugin, common.parsedScopes(),
                    sideName);
            return new TreeDiffSideReactor(TreeDiffSideState.PRESENT,
                    descriptor, side.inventory(), result, "");
        } catch (Exception exception) {
            return new TreeDiffSideReactor(TreeDiffSideState.PRESENT,
                    descriptor, side.inventory(), null,
                    "Dependency collection failed: "
                            + message(exception));
        }
    }

    private Set<String> reactorKeys(
            final SideInventory baseline,
            final SideInventory target) {
        final Set<String> result = new TreeSet<>();
        result.addAll(baseline.descriptors().keySet());
        result.addAll(target.descriptors().keySet());
        return result;
    }

    private TreeDiffReportMetadata metadata(
            final Path requestedPath,
            final Path analysisPath,
            final WorkspaceResult workspace,
            final PreparedRuntime prepared,
            final MavenDependencyPluginRuntime plugin) {
        return new TreeDiffReportMetadata(
                new TreeDiffSideMetadata("git-ref", baselineRef,
                        workspace.getBaseline().getCommit(), false),
                new TreeDiffSideMetadata(targetRef == null
                        ? "current-workspace" : "git-ref",
                        targetRef == null ? "Current workspace" : targetRef,
                        workspace.getTarget().getCommit(),
                        targetRef == null
                                && workspace.getTarget().isDirty()),
                requestedPath, analysisPath, common.parsedScopes(),
                prepared.runtime(), plugin.getVersion(),
                prepared.mavenArguments());
    }

    private Path sideRoot(
            final Path analysisRoot,
            final Path relativeAnalysisPath) {
        Path result = analysisRoot.toAbsolutePath().normalize();
        if (relativeAnalysisPath.toString().isEmpty()) {
            return result;
        }
        for (Path ignored : relativeAnalysisPath) {
            result = result.getParent();
        }
        return result;
    }

    private String message(final Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private record PreparedRuntime(
            MavenRuntimeDescriptor runtime,
            List<String> mavenArguments,
            String mavenOutput,
            MavenActivationContext activation) {
    }

    private record SideInventory(
            boolean available,
            RepositorySnapshot snapshot,
            RepositoryInventory inventory,
            Map<String, ReactorDescriptor> descriptors,
            String issue) {
    }

    /** Invalid CLI, Git, path, or runtime preflight. */
    private static final class TreeDiffPreflightException
            extends Exception {

        TreeDiffPreflightException(final String message) {
            super(message);
        }

        TreeDiffPreflightException(
                final String message,
                final Throwable cause) {
            super(message, cause);
        }
    }
}
