package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.diagnostic.DiagnosticContext;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.reactor.ReactorDescriptor;
import io.github.dependencyanalysis.reactor.RepositoryInventory;
import io.github.dependencyanalysis.runtime.MavenDependencyPluginRuntime;
import io.github.dependencyanalysis.runtime.MavenDependencyPluginRuntimeManager;
import io.github.dependencyanalysis.runtime.MavenExecutionResult;
import io.github.dependencyanalysis.runtime.MavenExecutor;
import io.github.dependencyanalysis.runtime.MavenRuntimeDescriptor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Collects compile and Maven dependency tree evidence only. */
final class DependencyTreeCollector {

    /** Maximum retained Maven lines. */
    private static final int TAIL_LINES = 100;

    /** Diagnostics. */
    private final DiagnosticLog diagnostics;

    /**
     * Creates a dependency-only collector.
     *
     * @param log diagnostics
     */
    DependencyTreeCollector(final DiagnosticLog log) {
        diagnostics = log;
    }

    /**
     * Collects one bounded Reactor without classpath enrichment.
     *
     * @param snapshot side snapshot
     * @param inventory side inventory
     * @param reactor Reactor descriptor
     * @param runtime Maven runtime
     * @param pluginRuntime Dependency Plugin runtime
     * @param scopes selected scopes
     * @param sideName baseline or target
     * @return dependency-only result
     */
    ReactorTreeResult collect(
            final RepositorySnapshot snapshot,
            final RepositoryInventory inventory,
            final ReactorDescriptor reactor,
            final MavenRuntimeDescriptor runtime,
            final MavenDependencyPluginRuntime pluginRuntime,
            final Set<String> scopes,
            final String sideName) {
        final String outputName = DependencyTreeCollectionSupport
                .outputName();
        final Map<Path, Path> outputs = DependencyTreeCollectionSupport
                .outputs(snapshot, reactor, outputName);
        final DiagnosticContext context = DiagnosticContext.of(
                "tree-diff", sideName + ".collect")
                .with("reactor", reactor.getId());
        MavenExecutionResult execution = null;
        Exception executionFailure = null;
        diagnostics.startStage(context,
                "goals=compile,dependency-tree");
        try {
            execution = new MavenExecutor().execute(runtime,
                    snapshot.getRoot(),
                    DependencyTreeCollectionSupport.arguments(
                            snapshot, reactor, pluginRuntime, outputName),
                    diagnostics, context, TAIL_LINES);
            if (execution.getExitCode() == 0) {
                diagnostics.endStage(context);
            } else {
                diagnostics.failStage(context,
                        "exitCode=" + execution.getExitCode());
            }
        } catch (Exception exception) {
            executionFailure = exception;
            diagnostics.failStage(context, "reason="
                    + DependencyTreeCollectionSupport.message(exception));
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }

        final List<String> reasons = new ArrayList<>();
        boolean failed = executionFailure != null || execution == null
                || execution.getExitCode() != 0;
        if (executionFailure != null) {
            reasons.add("Reactor Maven execution failed: "
                    + DependencyTreeCollectionSupport
                    .message(executionFailure));
        } else if (execution != null && execution.getExitCode() != 0) {
            reasons.add("Reactor Maven execution failed: "
                    + TreeDependencyCollector.diagnosticTail(
                    execution.getCombinedOutput()));
        }

        final boolean complete = MavenDependencyPluginRuntimeManager
                .supportsCompleteEvidence(pluginRuntime.getVersion());
        final List<ModuleTreeResult> modules = new ArrayList<>();
        final Set<DependencyKey> reactorKeys =
                DependencyTreeCollectionSupport.reactorKeys(
                        inventory, reactor);
        final ModuleAnalysisRole role =
                DependencyTreeCollectionSupport.analysisRole(reactor);
        try {
            for (Path pom : DependencyTreeCollectionSupport.analysisPoms(
                    inventory, reactor)) {
                final ModuleTreeResult module =
                        DependencyTreeCollectionSupport.parseModule(
                                pom, role, inventory, outputs.get(pom),
                                reactorKeys, scopes, complete);
                modules.add(module);
                if (!module.getFailure().isBlank()) {
                    failed = true;
                    reasons.add(pom + ": " + module.getFailure());
                }
                if (!complete) {
                    reasons.add(pom
                            + ": verbose/managed evidence unavailable");
                }
            }
        } finally {
            DependencyTreeCollectionSupport.cleanup(outputs.values());
        }
        final ReactorStatus status = failed ? ReactorStatus.FAILED
                : complete ? ReactorStatus.SUCCESS : ReactorStatus.DEGRADED;
        return new ReactorTreeResult(reactor, modules, status,
                String.join("; ", reasons));
    }
}
