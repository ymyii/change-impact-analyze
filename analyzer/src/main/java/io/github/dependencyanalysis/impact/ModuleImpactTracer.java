package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.impact.refinement.ResultRefinementSelection;
import io.github.dependencyanalysis.impact.refinement.cha.ChaLocalReceiverEdgeRefiner;
import io.github.dependencyanalysis.impact.refinement.cha.ChaLocalReceiverRefinementSummary;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.callgraph.scope.ClassOwnership;
import io.github.dependencyanalysis.callgraph.scope.ClassOwnershipIndex;
import io.github.dependencyanalysis.callgraph.scope.ClassSource;
import io.github.dependencyanalysis.callgraph.model.CodeOrigin;
import io.github.dependencyanalysis.callgraph.scope.DuplicateClassResolution;
import io.github.dependencyanalysis.callgraph.model.MethodId;
import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphSession;
import io.github.dependencyanalysis.callgraph.topology.StronglyConnectedComponents;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.DiagnosticContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;

// Wiki: wiki/features/impact-tracing.md - Node-only reverse query entrypoint
/** QueryNode-grouped Impact Path query over a frozen per-module session. */
public final class ModuleImpactTracer {

    /** Diagnostics. */
    private final DiagnosticLog diagnostics;

    /** Shared command Impact Query executor; null for direct inline calls. */
    private final ExecutorService executor;

    /** Maximum QueryNode workers. */
    private final int parallelism;

    /** Command-level actual worker observer. */
    private final IntConsumer workersObserver;

    /** Command-wide result-refinement selection. */
    private final ResultRefinementSelection resultRefinements;

    /** Active QueryNode queries for this serial Module. */
    private final AtomicInteger activeWorkers = new AtomicInteger();

    /** Active-worker termination monitor after Module-local cancellation. */
    private final Object workerLifecycle = new Object();

    /**
     * Creates a direct module tracer.
     *
     * @param collector diagnostics
     */
    public ModuleImpactTracer(final DiagnosticLog collector) {
        this(collector, null, 1, ignored -> { },
                ResultRefinementSelection.defaultSelection());
    }

    /**
     * Creates a direct module tracer with explicit result refinements.
     *
     * @param collector diagnostics
     * @param refinements command-wide result-refinement selection
     */
    public ModuleImpactTracer(
            final DiagnosticLog collector,
            final ResultRefinementSelection refinements) {
        this(collector, null, 1, ignored -> { }, refinements);
    }

    /**
     * Creates a tracer using a shared command executor.
     *
     * @param collector diagnostics
     * @param queryExecutor shared Impact Query executor
     * @param workerLimit configured QueryNode worker limit
     * @param observer actual worker observer
     */
    ModuleImpactTracer(
            final DiagnosticLog collector,
            final ExecutorService queryExecutor,
            final int workerLimit,
            final IntConsumer observer) {
        this(collector, queryExecutor, workerLimit, observer,
                ResultRefinementSelection.defaultSelection());
    }

    /**
     * Creates a tracer using a shared command executor and refinements.
     *
     * @param collector diagnostics
     * @param queryExecutor shared Impact Query executor
     * @param workerLimit configured QueryNode worker limit
     * @param observer actual worker observer
     * @param refinements command-wide result-refinement selection
     */
    ModuleImpactTracer(
            final DiagnosticLog collector,
            final ExecutorService queryExecutor,
            final int workerLimit,
            final IntConsumer observer,
            final ResultRefinementSelection refinements) {
        diagnostics = Objects.requireNonNull(collector, "collector");
        executor = queryExecutor;
        if (workerLimit < 1) {
            throw new IllegalArgumentException(
                    "Impact Query parallelism must be >= 1");
        }
        parallelism = workerLimit;
        workersObserver = Objects.requireNonNull(observer, "observer");
        resultRefinements = Objects.requireNonNull(
                refinements, "refinements");
    }

    /**
     * Resolves seeds and materializes representative shortest paths.
     *
     * @param unit module analysis unit
     * @param session live WALA graph session
     * @param changePointEvidence frozen post-graph evidence
     * @return module query result
     */
    public ModuleImpactQueryResult trace(
            final ModuleAnalysisUnit unit,
            final ModuleCallGraphSession session,
            final ChangePointEvidenceIndex changePointEvidence) {
        final DiagnosticContext context = DiagnosticContext.of(
                "module-analysis", "impact-query").withModule(
                unit.getModuleId().stableKey());
        diagnostics.startStage(context, "changes="
                + unit.getChangePoints().size() + "; evidenceBindings="
                + changePointEvidence.reverseBfsBindings().size());
        try {
            final QueryPlan plan = plan(unit, session, changePointEvidence);
            final ChaLocalReceiverEdgeRefiner edgeRefiner =
                    new ChaLocalReceiverEdgeRefiner(
                            session, resultRefinements);
            final int workers = plan.works().isEmpty() ? 0
                    : executor == null ? 1
                    : Math.min(parallelism, plan.works().size());
            diagnostics.debug(context, "Query planning completed; seeds="
                    + plan.seedCount() + "; queryNodes="
                    + plan.works().size() + "; workers=" + workers);
            try (SeedProgressReporter seedProgress =
                         SeedProgressReporter.open(diagnostics, context)) {
            final QueryExecution execution = execute(
                    unit.getModuleId(), session, plan.works(), workers,
                    seedProgress, edgeRefiner);
            final ChaLocalReceiverRefinementSummary refinement =
                    edgeRefiner.summary();
            final ModuleImpactQueryResult result = finish(
                    plan, execution, context, refinement);
            traceRefinementExamples(context, refinement);
            diagnostics.endStage(context, "seeds="
                    + plan.seedCount() + "; queryNodes="
                    + plan.works().size() + "; workers=" + workers
                    + "; chaLocalReceiver="
                    + refinement.status().label() + "; checkedEdges="
                    + refinement.metrics().uniqueEvaluatedEdges()
                    + "; prunedEdges="
                    + refinement.metrics().prunedEdges()
                    + "; unknownEdges="
                    + refinement.metrics().retainedUnknownEdges());
            return result;
            }
        } catch (RuntimeException exception) {
            diagnostics.failStage(context, "reason="
                    + Objects.requireNonNullElse(
                    exception.getMessage(), exception.getClass().getName()));
            throw exception;
        }
    }

    private QueryPlan plan(
            final ModuleAnalysisUnit unit,
            final ModuleCallGraphSession session,
            final ChangePointEvidenceIndex changePointEvidence) {
        final Map<BoundChangePoint, ChangePointDisposition> fixedDispositions =
                new LinkedHashMap<>();
        final Map<BoundChangePoint, List<ImpactEvidence>> observations =
                new LinkedHashMap<>();
        final Set<QueryLimitation> limitations = new LinkedHashSet<>();
        final Map<BoundChangePoint, PointState> pointStates =
                new LinkedHashMap<>();
        final Map<QueryNode, QueryWorkBuilder> grouped = new HashMap<>();
        final List<StructuralReferencePath> directStructural =
                new ArrayList<>();
        final Set<StructuralReferenceMatch> plannedStructural =
                new LinkedHashSet<>();
        final Map<String, StructuralReferenceMatch> structuralByKey =
                new LinkedHashMap<>();
        int seedCount = 0;
        final ChangePointSeedResolverRegistry seedResolvers =
                new ChangePointSeedResolverRegistry();
        final List<StructuralReferenceMatch> structuralReferences =
                structuralReferences(changePointEvidence);
        final StructuralReferencePreparation.Result preparedStructures =
                new StructuralReferencePreparation().prepare(
                        structuralReferences, session);
        preparedStructures.observations().forEach((point, values) ->
                mergeObservations(observations, point, values));
        limitations.addAll(preparedStructures.limitations());
        for (StructuralReferenceMatch match
                : preparedStructures.references()) {
            if (match.reference().getOrigin() == CodeOrigin.PROJECT) {
                directStructural.add(new StructuralReferencePath(
                        match.changePoint(), match.reference(), List.of(),
                        ImpactClassification.DIRECT));
                continue;
            }
            plannedStructural.add(match);
            structuralByKey.put(referenceKey(match), match);
        }
        for (Map.Entry<QueryNode, List<ChangePointTerminal>> entry
                : changePointEvidence.reverseBfsBindings().entrySet()) {
            for (ChangePointTerminal terminal : entry.getValue()) {
                final EvidenceAnchor rawAnchor = terminal.getImpactEvidence()
                        .anchor().orElse(null);
                if (!(rawAnchor instanceof StructuralEvidenceAnchor anchor)) {
                    continue;
                }
                final StructuralReferenceMatch candidate =
                        new StructuralReferenceMatch(
                                terminal.getChangePoint(), anchor.reference());
                final StructuralReferenceMatch match = structuralByKey.get(
                        referenceKey(candidate));
                if (match == null) {
                    continue;
                }
                final QueryNode node = entry.getKey();
                grouped.computeIfAbsent(node, QueryWorkBuilder::new)
                        .structural().add(
                        new StructuralSeedBinding(match, node));
                seedCount++;
            }
        }
        for (BoundChangePoint point : unit.getChangePoints().stream()
                .sorted(Comparator.comparing(BoundChangePoint::stableKey))
                .toList()) {
            final ChangePoint change = point.getChangePoint();
            if (isAdded(change.getKind())) {
                fixedDispositions.put(point,
                        ChangePointDisposition.CHANGE_KIND_NOT_ANALYZED);
                continue;
            }
            final ChangePointDisposition duplicateDisposition =
                    duplicateDisposition(point, session.getOwnership());
            if (duplicateDisposition != null) {
                fixedDispositions.put(point, duplicateDisposition);
                continue;
            }
            final ChangePointSeedResolution resolution =
                    seedResolvers.resolve(new ChangePointSeedRequest(
                            unit.getModuleId(), change, session,
                            changePointEvidence.resolution(point)));
            final List<ImpactSeed> seeds = resolution.seeds();
            limitations.addAll(resolution.limitations());
            mergeObservations(observations, point, resolution.evidence());
            pointStates.put(point, new PointState(change.getKind(),
                    resolution.observation(), !seeds.isEmpty()));
            for (ImpactSeed seed : seeds) {
                final ChangePointTerminal terminal = new ChangePointTerminal(
                        point, seed.evidence());
                if (!changePointEvidence.bindingsFor(seed.node())
                        .contains(terminal)) {
                    throw new IllegalStateException(
                            "Impact seed is absent from unified Evidence "
                                    + "binding: " + point.stableKey() + "|"
                                    + seed.evidence().stableKey());
                }
                grouped.computeIfAbsent(seed.node(), QueryWorkBuilder::new)
                        .ordinary().add(new OrdinarySeedBinding(point, seed));
                seedCount++;
            }
        }
        final List<QueryWorkBuilder> orderedBuilders = grouped.values().stream()
                .sorted(Comparator.comparing(
                        QueryWorkBuilder::node, queryNodeComparator()))
                .toList();
        final List<QueryWork> works = new ArrayList<>();
        long ordinal = 0L;
        for (QueryWorkBuilder builder : orderedBuilders) {
            final List<OrdinarySeedBinding> ordinary =
                    builder.ordinary().stream().sorted(Comparator
                    .comparing((OrdinarySeedBinding value) ->
                            value.point().stableKey())
                    .thenComparing(value -> value.seed()
                            .evidence().stableKey())).toList();
            final List<StructuralSeedBinding> structural =
                    builder.structural().stream().sorted(Comparator
                    .comparing((StructuralSeedBinding value) ->
                            value.match().changePoint().stableKey())
                    .thenComparing(value -> value.match()
                            .reference().stableKey())).toList();
            works.add(new QueryWork(++ordinal, builder.node(), ordinary,
                    structural));
        }
        return new QueryPlan(works, seedCount, fixedDispositions,
                pointStates, stableObservations(observations), limitations,
                directStructural, plannedStructural,
                preparedStructures.observations().keySet());
    }

    private QueryExecution execute(
            final ModuleId moduleId,
            final ModuleCallGraphSession session,
            final List<QueryWork> works,
            final int workers,
            final SeedProgressReporter progress,
            final ChaLocalReceiverEdgeRefiner edgeRefiner) {
        final QueryExecution result = new QueryExecution();
        if (works.isEmpty()) {
            return result;
        }
        if (executor == null) {
            for (QueryWork work : works) {
                result.merge(runWork(moduleId, session, work, progress,
                        edgeRefiner));
            }
            return result;
        }
        final CompletionService<QueryNodeResult> completion =
                new ExecutorCompletionService<>(executor);
        final List<Future<QueryNodeResult>> active = new ArrayList<>();
        int next = 0;
        while (next < works.size() && active.size() < workers) {
            active.add(submit(completion, moduleId, session,
                    works.get(next++), progress, edgeRefiner));
        }
        try {
            while (!active.isEmpty()) {
                final Future<QueryNodeResult> completed = completion.take();
                active.remove(completed);
                result.merge(completed.get());
                if (next < works.size()) {
                    active.add(submit(completion, moduleId, session,
                            works.get(next++), progress, edgeRefiner));
                }
            }
            return result;
        } catch (InterruptedException exception) {
            active.forEach(value -> value.cancel(true));
            Thread.currentThread().interrupt();
            throw new ImpactException("Impact Query interrupted", exception);
        } catch (ExecutionException exception) {
            cancelAndAwait(active);
            throw new ImpactException("QueryNode query failed",
                    exception.getCause());
        }
    }

    private void cancelAndAwait(
            final List<Future<QueryNodeResult>> active) {
        active.forEach(value -> value.cancel(true));
        synchronized (workerLifecycle) {
            while (activeWorkers.get() > 0) {
                try {
                    workerLifecycle.wait();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new ImpactException(
                            "Interrupted while cancelling QueryNode queries",
                            exception);
                }
            }
        }
    }

    private Future<QueryNodeResult> submit(
            final CompletionService<QueryNodeResult> completion,
            final ModuleId moduleId,
            final ModuleCallGraphSession session,
            final QueryWork work,
            final SeedProgressReporter progress,
            final ChaLocalReceiverEdgeRefiner edgeRefiner) {
        return completion.submit(() -> runWork(
                moduleId, session, work, progress, edgeRefiner));
    }

    private QueryNodeResult runWork(
            final ModuleId moduleId,
            final ModuleCallGraphSession session,
            final QueryWork work,
            final SeedProgressReporter progress,
            final ChaLocalReceiverEdgeRefiner edgeRefiner) {
        final int active;
        synchronized (workerLifecycle) {
            if (Thread.currentThread().isInterrupted()) {
                throw new ImpactException("QueryNode query cancelled");
            }
            active = activeWorkers.incrementAndGet();
        }
        workersObserver.accept(active);
        try (SeedProgressTracker tracker = progress.startQueryNode(
                     work.ordinal(), work.node(), work.evidenceSeeds())) {
            ReverseTrace reverse = reverse(
                    moduleId, work.node(), session, tracker, edgeRefiner);
            tracker.reverseCompleted(reverse.visited().size());
            final List<ImpactRoot> roots = roots(reverse, session);
            final List<ImpactPath> paths = new ArrayList<>();
            for (OrdinarySeedBinding binding : work.ordinary()) {
                for (ImpactPath path : materialize(binding.point(),
                        binding.seed(), reverse, roots, tracker)) {
                    tracker.representativeSelection(path.getNodes().get(0));
                    paths.add(path);
                }
            }
            final List<StructuralReferencePath> structural =
                    new ArrayList<>();
            final Set<StructuralReferenceMatch> recovered =
                    new LinkedHashSet<>();
            for (StructuralSeedBinding binding : work.structural()) {
                final List<StructuralReferencePath> materialized =
                        materializeStructural(
                                binding.match().changePoint(),
                                binding.match().reference(), reverse, roots,
                                tracker);
                if (!materialized.isEmpty()) {
                    recovered.add(binding.match());
                }
                for (StructuralReferencePath path : materialized) {
                    tracker.representativeSelection(
                            path.getNodes().get(0));
                    structural.add(path);
                }
            }
            final QueryNodeResult result = new QueryNodeResult(
                    paths, structural, recovered);
            reverse = null;
            tracker.complete();
            return result;
        } finally {
            synchronized (workerLifecycle) {
                activeWorkers.decrementAndGet();
                workerLifecycle.notifyAll();
            }
        }
    }

    private ModuleImpactQueryResult finish(
            final QueryPlan plan,
            final QueryExecution execution,
            final DiagnosticContext context,
            final ChaLocalReceiverRefinementSummary refinement) {
        final Map<BoundChangePoint, Map<String, ImpactPath>> ordinary =
                new LinkedHashMap<>();
        for (ImpactPath path : execution.paths()) {
            final BoundChangePoint point =
                    path.getTerminal().getChangePoint();
            final String affected = methodIdentity(path.getRootMethod());
            final Map<String, ImpactPath> selected = ordinary
                    .computeIfAbsent(point, ignored -> new LinkedHashMap<>());
            final ImpactPath previous = selected.get(affected);
            if (previous == null || representativePathComparator()
                    .compare(path, previous) < 0) {
                selected.put(affected, path);
            }
        }
        final List<ImpactPath> paths = ordinary.values().stream()
                .flatMap(value -> value.values().stream())
                .sorted(pathComparator()).toList();
        final Map<String, StructuralReferencePath> structural =
                new LinkedHashMap<>();
        for (StructuralReferencePath path : plan.directStructural()) {
            structural.putIfAbsent(referenceKey(path), path);
        }
        for (StructuralReferencePath path : execution.structuralPaths()) {
            final String key = referenceKey(path) + "|"
                    + methodIdentity(path.getAffectedMethod());
            final StructuralReferencePath previous = structural.get(key);
            if (previous == null || structuralPathComparator()
                    .compare(path, previous) < 0) {
                structural.put(key, path);
            }
        }
        final List<StructuralReferencePath> structuralPaths =
                structural.values().stream()
                        .sorted(structuralReportComparator()).toList();
        final Set<BoundChangePoint> unreachable = new LinkedHashSet<>();
        plan.plannedStructural().stream()
                .filter(match -> !execution.recovered().contains(match))
                .forEach(match -> unreachable.add(match.changePoint()));
        final Map<BoundChangePoint, ChangePointDisposition> dispositions =
                new LinkedHashMap<>(plan.fixedDispositions());
        plan.pointStates().forEach((point, state) -> dispositions.put(point,
                new ChangePointDispositionReducer().reduce(
                        state.kind(), state.observation(), state.hasSeeds(),
                        ordinary.containsKey(point),
                        structuralPaths.stream().anyMatch(path ->
                                path.getChangePoint().equals(point)),
                        unreachable.contains(point),
                        plan.structuralObservedPoints().contains(point))));
        final Map<BoundChangePoint, ChangePointDisposition> stableDispositions =
                new LinkedHashMap<>();
        dispositions.entrySet().stream().sorted(Comparator.comparing(entry ->
                entry.getKey().stableKey())).forEach(entry ->
                stableDispositions.put(entry.getKey(), entry.getValue()));
        final long uniqueImpactPaths = paths.stream()
                .map(this::pathIdentity).distinct().count();
        final long rootMethods = paths.stream().map(ImpactPath::getRootMethod)
                .map(this::methodIdentity).distinct().count();
        final long affectedMethods = paths.stream()
                .flatMap(path -> path.getAffectedMethods().stream())
                .map(this::methodIdentity).distinct().count();
        final long changedMembers = paths.stream().map(path -> path
                        .getTerminal().getChangePoint().stableKey())
                .distinct().count();
        diagnostics.info(context, "impactPaths=" + paths.size()
                + "; uniqueImpactPaths=" + uniqueImpactPaths
                + "; rootMethods=" + rootMethods
                + "; affectedMethods=" + affectedMethods
                + "; changedMembers=" + changedMembers
                + "; structuralPaths=" + structuralPaths.size()
                + "; reverseBfs=" + plan.works().size());
        return new ModuleImpactQueryResult(paths, structuralPaths,
                stableDispositions, plan.observations(),
                plan.limitations().stream().sorted().toList(), refinement);
    }

    private void traceRefinementExamples(
            final DiagnosticContext context,
            final ChaLocalReceiverRefinementSummary refinement) {
        for (ChaLocalReceiverRefinementSummary.EdgeExample example
                : refinement.examples()) {
            diagnostics.trace(context, "cha-local-receiver-edge; caller="
                    + example.caller() + "; callee=" + example.callee()
                    + "; pc=" + example.programCounter() + "; invoke="
                    + example.invocationKind() + "; decision="
                    + example.decision() + "; reason=" + example.reason()
                    + "; receiver=" + example.receiverSummary());
        }
    }

    private void mergeObservations(
            final Map<BoundChangePoint, List<ImpactEvidence>> observations,
            final BoundChangePoint point,
            final List<? extends ImpactEvidence> additions) {
        if (additions.isEmpty()) {
            return;
        }
        final List<ImpactEvidence> merged = new ArrayList<>(
                observations.getOrDefault(point, List.of()));
        merged.addAll(additions);
        observations.put(point, merged.stream().distinct()
                .sorted(Comparator.comparing(ImpactEvidence::stableKey))
                .toList());
    }

    private Map<BoundChangePoint, List<ImpactEvidence>> stableObservations(
            final Map<BoundChangePoint, List<ImpactEvidence>> observations) {
        final Map<BoundChangePoint, List<ImpactEvidence>> stable =
                new LinkedHashMap<>();
        observations.entrySet().stream().sorted(Comparator.comparing(entry ->
                entry.getKey().stableKey())).forEach(entry ->
                stable.put(entry.getKey(), List.copyOf(entry.getValue())));
        return stable;
    }

    private List<StructuralReferenceMatch> structuralReferences(
            final ChangePointEvidenceIndex changePointEvidence) {
        final Set<StructuralReferenceMatch> result = new LinkedHashSet<>();
        for (ChangePointEvidenceResolution resolution
                : changePointEvidence.resolutions()) {
            for (ReferenceEvidence evidence : resolution.evidence()) {
                evidence.anchor()
                        .filter(StructuralEvidenceAnchor.class::isInstance)
                        .map(StructuralEvidenceAnchor.class::cast)
                        .ifPresent(anchor -> result.add(
                                new StructuralReferenceMatch(
                                        resolution.changePoint(),
                                        anchor.reference())));
            }
        }
        return result.stream().sorted(Comparator
                .comparing((StructuralReferenceMatch value) ->
                        value.changePoint().stableKey())
                .thenComparing(value -> value.reference().stableKey()))
                .toList();
    }

    static ChangePointDisposition duplicateDisposition(
            final BoundChangePoint point,
            final ClassOwnershipIndex ownership) {
        final DuplicateClassResolution resolution = ownership
                .duplicateResolutionOf(
                        point.getChangePoint().getOwner());
        if (resolution == null) {
            return null;
        }
        final ClassSource changedSource = ClassSource.artifact(
                point.getDependencyUpgradeKey().getNewArtifact());
        return resolution.getLosers().stream()
                .anyMatch(value -> value.getSource().equals(changedSource))
                ? ChangePointDisposition.SHADOWED_BY_DUPLICATE : null;
    }

    private List<StructuralReferencePath> materializeStructural(
            final BoundChangePoint point,
            final StructuralReference reference,
            final ReverseTrace reverse,
            final List<ImpactRoot> roots,
            final SeedProgressTracker tracker) {
        final Map<String, StructuralReferencePath> result =
                new LinkedHashMap<>();
        for (ImpactRoot root : roots) {
            final List<QueryNode> nodes = pathNodes(
                    root.node(), reverse, tracker);
            if (nodes.isEmpty()) {
                continue;
            }
            result.putIfAbsent(methodIdentity(root.node().methodId()),
                    new StructuralReferencePath(point, reference,
                            nodes, ImpactClassification.TRANSITIVE));
        }
        return List.copyOf(result.values());
    }

    private List<QueryNode> pathNodes(
            final QueryNode root,
            final ReverseTrace reverse,
            final SeedProgressTracker tracker) {
        final List<QueryNode> nodes = new ArrayList<>();
        QueryNode current = root;
        tracker.pathMaterialization(current);
        nodes.add(current);
        while (!current.equals(reverse.seed())) {
            current = reverse.next().get(current);
            if (current == null) {
                return List.of();
            }
            tracker.pathMaterialization(current);
            nodes.add(current);
        }
        return nodes;
    }

    private String referenceKey(final StructuralReferenceMatch match) {
        return match.changePoint().stableKey() + "|"
                + match.reference().stableKey();
    }

    private String referenceKey(final StructuralReferencePath path) {
        return path.getChangePoint().stableKey() + "|"
                + path.getReference().stableKey();
    }

    private ReverseTrace reverse(
            final ModuleId moduleId,
            final QueryNode seed,
            final ModuleCallGraphSession session,
            final SeedProgressTracker tracker,
            final ChaLocalReceiverEdgeRefiner edgeRefiner) {
        final Map<QueryNode, QueryNode> next = new HashMap<>();
        final Map<QueryNode, Set<QueryNode>> incoming = new HashMap<>();
        final Map<QueryNode, Set<QueryNode>> successors = new HashMap<>();
        final Queue<QueryNode> queue = new ArrayDeque<>();
        final Set<QueryNode> visited = new HashSet<>();
        queue.add(seed);
        visited.add(seed);
        tracker.reverseProgress(seed, visited.size());
        while (!queue.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                throw new ImpactException("QueryNode query cancelled");
            }
            final QueryNode current = queue.remove();
            tracker.reverseProgress(current, visited.size());
            final List<QueryNode> callers = predecessors(
                    moduleId, current, session, edgeRefiner);
            callers.sort(queryNodeComparator());
            for (QueryNode caller : callers) {
                incoming.computeIfAbsent(current,
                        ignored -> new LinkedHashSet<>()).add(caller);
                successors.computeIfAbsent(caller,
                        ignored -> new LinkedHashSet<>()).add(current);
                if (visited.add(caller)) {
                    next.put(caller, current);
                    queue.add(caller);
                    tracker.visited(visited.size());
                }
            }
        }
        return new ReverseTrace(seed, next, visited,
                immutableAdjacency(incoming),
                immutableAdjacency(successors));
    }

    private Map<QueryNode, Set<QueryNode>> immutableAdjacency(
            final Map<QueryNode, Set<QueryNode>> source) {
        final Map<QueryNode, Set<QueryNode>> result = new HashMap<>();
        source.forEach((node, adjacent) ->
                result.put(node, Set.copyOf(adjacent)));
        return Map.copyOf(result);
    }

    private List<ImpactRoot> roots(
            final ReverseTrace reverse,
            final ModuleCallGraphSession session) {
        final Comparator<QueryNode> order = queryNodeComparator();
        final List<ImpactRoot> result = new ArrayList<>();
        final Set<String> calledProjectMethods = reverse.visited().stream()
                .filter(node -> node.origin() == CodeOrigin.PROJECT)
                .filter(node -> !reverse.predecessors()
                        .getOrDefault(node, Set.of()).isEmpty())
                .map(node -> methodIdentity(node.methodId()))
                .collect(Collectors.toSet());
        for (List<QueryNode> component
                : StronglyConnectedComponents.decompose(reverse.visited(),
                node -> reverse.successors().getOrDefault(node, Set.of()),
                node -> reverse.predecessors().getOrDefault(node, Set.of()),
                order)) {
            final Set<QueryNode> members = Set.copyOf(component);
            final boolean hasIncoming = component.stream()
                    .flatMap(node -> reverse.predecessors()
                            .getOrDefault(node, Set.of()).stream())
                    .anyMatch(node -> !members.contains(node));
            if (hasIncoming) {
                continue;
            }
            final List<QueryNode> project = component.stream()
                    .filter(node -> node.origin() == CodeOrigin.PROJECT)
                    .toList();
            if (project.isEmpty()) {
                continue;
            }
            final List<QueryNode> entrypoints = project.stream()
                    .filter(node -> isEntrypoint(node, session)).toList();
            final List<QueryNode> choices = entrypoints.isEmpty()
                    ? project : entrypoints;
            final QueryNode representative = choices.stream()
                    .min(order).orElseThrow();
            final boolean cycle = component.size() > 1
                    || component.stream().anyMatch(node -> reverse
                            .successors().getOrDefault(node, Set.of())
                            .contains(node));
            if (!cycle && calledProjectMethods.contains(
                    methodIdentity(representative.methodId()))) {
                continue;
            }
            result.add(new ImpactRoot(representative,
                    cycle ? ImpactPathRootKind
                            .STRONGLY_CONNECTED_COMPONENT
                            : ImpactPathRootKind.METHOD));
        }
        return result.stream().sorted(Comparator
                .comparing(ImpactRoot::node, order)).toList();
    }

    private boolean isEntrypoint(
            final QueryNode node,
            final ModuleCallGraphSession session) {
        return node instanceof WalaQueryNode wala
                && session.getGraph().getEntrypointNodes()
                .contains(wala.walaNode());
    }

    private List<ImpactPath> materialize(
            final BoundChangePoint point,
            final ImpactSeed seed,
            final ReverseTrace reverse,
            final List<ImpactRoot> roots,
            final SeedProgressTracker tracker) {
        final Map<String, ImpactPath> byAffectedMethod =
                new LinkedHashMap<>();
        for (ImpactRoot root : roots) {
            final List<QueryNode> nodes = pathNodes(
                    root.node(), reverse, tracker);
            if (nodes.isEmpty()) {
                continue;
            }
            final ImpactClassification classification =
                    seed.node().origin() == CodeOrigin.PROJECT
                            ? ImpactClassification.DIRECT
                            : ImpactClassification.TRANSITIVE;
            final ImpactPath path = new ImpactPath(nodes,
                    new ChangePointTerminal(point, seed.evidence()),
                    classification, root.kind());
            final String key = methodIdentity(
                    root.node().methodId());
            byAffectedMethod.putIfAbsent(key, path);
        }
        return List.copyOf(byAffectedMethod.values());
    }

    private WalaQueryNode queryNode(
            final ModuleId moduleId,
            final CGNode node,
            final ModuleCallGraphSession session) {
        final IMethod method = node.getMethod();
        final String owner = owner(method.getReference());
        final ClassOwnership ownership =
                session.getOwnership().ownershipOf(owner);
        final CodeOrigin codeOrigin = origin(node, session);
        final String module = codeOrigin == CodeOrigin.PROJECT
                ? moduleId.stableKey() : codeOrigin.name();
        final String source = ownership == null
                ? codeOrigin == CodeOrigin.JDK
                ? "<jdk>" : "<synthetic>"
                : ownership.getSource().toString();
        return new WalaQueryNode(node, new MethodId(owner,
                method.getName().toString(),
                method.getDescriptor().toString(), module, source),
                codeOrigin);
    }


    private CodeOrigin origin(
            final CGNode node,
            final ModuleCallGraphSession session) {
        return session.originOf(
                node.getMethod().getDeclaringClass());
    }

    private boolean isSyntheticRoot(
            final CGNode node,
            final ModuleCallGraphSession session) {
        return node.equals(session.getGraph().getFakeRootNode())
                || node.equals(session.getGraph().getFakeWorldClinitNode());
    }

    private boolean isAdded(final ChangePointKind kind) {
        return kind == ChangePointKind.CLASS_ADDED
                || kind == ChangePointKind.METHOD_ADDED
                || kind == ChangePointKind.FIELD_ADDED;
    }

    private Comparator<QueryNode> queryNodeComparator() {
        return Comparator.comparing((QueryNode node) ->
                        methodIdentity(node.methodId()))
                .thenComparingInt(this::queryNodeNumber);
    }

    private Comparator<ImpactPath> pathComparator() {
        return Comparator
                .comparing((ImpactPath path) ->
                        path.getRootMethod().owner())
                .thenComparing(path -> path.getRootMethod().name())
                .thenComparing(path ->
                        path.getRootMethod().descriptor())
                .thenComparing(path -> path.getTerminal()
                        .getChangePoint().stableKey());
    }

    private Comparator<ImpactPath> representativePathComparator() {
        return Comparator
                .comparingInt((ImpactPath path) ->
                        path.getNodes().size() - 1)
                .thenComparing((left, right) -> compareNodeSequences(
                        left.getNodes(), right.getNodes()))
                .thenComparing(path -> path.getTerminal()
                        .getImpactEvidence().stableKey());
    }

    private Comparator<StructuralReferencePath> structuralPathComparator() {
        return Comparator
                .comparingInt((StructuralReferencePath path) ->
                        Math.max(0, path.getNodes().size() - 1))
                .thenComparing((left, right) -> compareNodeSequences(
                        left.getNodes(), right.getNodes()));
    }

    private Comparator<StructuralReferencePath> structuralReportComparator() {
        return Comparator
                .comparing((StructuralReferencePath path) ->
                        path.getChangePoint().stableKey())
                .thenComparing(path -> path.getReference().stableKey())
                .thenComparing(this::affectedMethodKey)
                .thenComparing((left, right) -> compareNodeSequences(
                        left.getNodes(), right.getNodes()));
    }

    private String affectedMethodKey(final StructuralReferencePath path) {
        return path.getAffectedMethod() == null ? ""
                : methodIdentity(path.getAffectedMethod());
    }

    private String pathIdentity(final ImpactPath path) {
        final StringBuilder result = new StringBuilder();
        for (QueryNode node : path.getNodes()) {
            result.append(methodIdentity(node.methodId())).append('@')
                    .append(queryNodeNumber(node)).append('|');
        }
        return result.append(path.getClassification()).append('|')
                .append(path.getRootKind()).toString();
    }

    private int compareNodeSequences(
            final List<QueryNode> left,
            final List<QueryNode> right) {
        final Comparator<QueryNode> comparator = Comparator
                .comparing((QueryNode node) -> node.methodId().owner())
                .thenComparing(node -> node.methodId().name())
                .thenComparing(node -> node.methodId().descriptor())
                .thenComparing(node -> node.methodId().module())
                .thenComparing(node -> node.methodId().sourceId())
                .thenComparing(QueryNode::origin)
                .thenComparingInt(this::queryNodeNumber);
        final int size = Math.min(left.size(), right.size());
        for (int index = 0; index < size; index++) {
            final int result = comparator.compare(
                    left.get(index), right.get(index));
            if (result != 0) {
                return result;
            }
        }
        return Integer.compare(left.size(), right.size());
    }

    private String methodIdentity(final MethodReference method) {
        return owner(method) + "#" + method.getName() + "#"
                + method.getDescriptor();
    }

    private String methodIdentity(final MethodId method) {
        return method.owner() + "#" + method.name() + "#"
                + method.descriptor();
    }

    private int queryNodeNumber(final QueryNode node) {
        return node instanceof WalaQueryNode
                ? ((WalaQueryNode) node).walaNode().getGraphNodeId()
                : Integer.MAX_VALUE;
    }

    private List<QueryNode> predecessors(
            final ModuleId moduleId,
            final QueryNode node,
            final ModuleCallGraphSession session,
            final ChaLocalReceiverEdgeRefiner edgeRefiner) {
        final Set<QueryNode> result = new LinkedHashSet<>();
        if (node instanceof WalaQueryNode) {
            final CGNode walaNode = ((WalaQueryNode) node).walaNode();
            final List<CGNode> walaPredecessors = iteratorList(
                    session.getGraph().getPredNodes(walaNode));
            walaPredecessors.removeIf(value ->
                    isSyntheticRoot(value, session));
            for (CGNode predecessor : walaPredecessors) {
                if (edgeRefiner.shouldTraverse(predecessor, walaNode)) {
                    result.add(queryNode(
                            moduleId, predecessor, session));
                }
            }
        }
        return new ArrayList<>(result);
    }

    private String owner(final MethodReference method) {
        return owner(method.getDeclaringClass());
    }

    private String owner(final TypeReference type) {
        final String value = type.getName().toString();
        return value.startsWith("L") ? value.substring(1) : value;
    }

    private <T> List<T> iteratorList(final Iterator<T> iterator) {
        final List<T> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }


    /**
     * QueryNode-local backward slice, released before the query returns.
     *
     * @param seed query seed
     * @param next next-node links toward the seed
     * @param visited nodes visited by this QueryNode only
     * @param predecessors retained incoming edges in the backward slice
     * @param successors retained outgoing edges in the backward slice
     */
    private record ReverseTrace(
            QueryNode seed,
            Map<QueryNode, QueryNode> next,
            Set<QueryNode> visited,
            Map<QueryNode, Set<QueryNode>> predecessors,
            Map<QueryNode, Set<QueryNode>> successors) {
    }

    /**
     * Selected PROJECT representative of one root component.
     *
     * @param node representative PROJECT node
     * @param kind ordinary method root or root SCC
     */
    private record ImpactRoot(
            QueryNode node,
            ImpactPathRootKind kind) {
    }

    /** Mutable planning bucket for one exact QueryNode. */
    private static final class QueryWorkBuilder {

        /** Exact QueryNode. */
        private final QueryNode node;

        /** Ordinary evidence bindings. */
        private final List<OrdinarySeedBinding> ordinary = new ArrayList<>();

        /** Structural evidence bindings. */
        private final List<StructuralSeedBinding> structural =
                new ArrayList<>();

        QueryWorkBuilder(final QueryNode value) {
            node = value;
        }

        QueryNode node() {
            return node;
        }

        List<OrdinarySeedBinding> ordinary() {
            return ordinary;
        }

        List<StructuralSeedBinding> structural() {
            return structural;
        }
    }

    /**
     * One ordinary terminal evidence binding.
     *
     * @param point bound change point
     * @param seed impact seed
     */
    private record OrdinarySeedBinding(
            BoundChangePoint point,
            ImpactSeed seed) {
    }

    /**
     * One structural evidence binding.
     *
     * @param match structural match
     * @param node resolved query node
     */
    private record StructuralSeedBinding(
            StructuralReferenceMatch match,
            QueryNode node) {
    }

    /**
     * Immutable work for one unique QueryNode.
     *
     * @param ordinal stable QueryNode ordinal
     * @param node exact query node
     * @param ordinary ordinary evidence bindings
     * @param structural structural evidence bindings
     */
    private record QueryWork(
            long ordinal,
            QueryNode node,
            List<OrdinarySeedBinding> ordinary,
            List<StructuralSeedBinding> structural) {

        QueryWork {
            ordinary = List.copyOf(ordinary);
            structural = List.copyOf(structural);
        }

        int evidenceSeeds() {
            return ordinary.size() + structural.size();
        }
    }

    /**
     * Per-ChangePoint state retained after serial seed resolution.
     *
     * @param kind change kind
     * @param observation reference observation
     * @param hasSeeds whether the point produced a seed
     */
    private record PointState(
            ChangePointKind kind,
            ReferenceObservation observation,
            boolean hasSeeds) {
    }

    /**
     * Complete immutable plan before QueryNode execution starts.
     *
     * @param works ordered QueryNode work
     * @param seedCount total evidence bindings
     * @param fixedDispositions dispositions resolved during planning
     * @param pointStates per-change state
     * @param observations evidence observations
     * @param limitations query limitations
     * @param directStructural direct project structural paths
     * @param plannedStructural planned structural matches
     * @param structuralObservedPoints structurally observed changes
     */
    private record QueryPlan(
            List<QueryWork> works,
            int seedCount,
            Map<BoundChangePoint, ChangePointDisposition> fixedDispositions,
            Map<BoundChangePoint, PointState> pointStates,
            Map<BoundChangePoint, List<ImpactEvidence>> observations,
            Set<QueryLimitation> limitations,
            List<StructuralReferencePath> directStructural,
            Set<StructuralReferenceMatch> plannedStructural,
            Set<BoundChangePoint> structuralObservedPoints) {

        QueryPlan {
            works = List.copyOf(works);
            fixedDispositions = Collections.unmodifiableMap(
                    new LinkedHashMap<>(fixedDispositions));
            pointStates = Collections.unmodifiableMap(
                    new LinkedHashMap<>(pointStates));
            observations = Collections.unmodifiableMap(
                    new LinkedHashMap<>(observations));
            limitations = Set.copyOf(limitations);
            directStructural = List.copyOf(directStructural);
            plannedStructural = Set.copyOf(plannedStructural);
            structuralObservedPoints = Set.copyOf(structuralObservedPoints);
        }
    }

    /**
     * One QueryNode result without its ReverseTrace.
     *
     * @param paths ordinary paths
     * @param structuralPaths structural paths
     * @param recovered recovered structural matches
     */
    private record QueryNodeResult(
            List<ImpactPath> paths,
            List<StructuralReferencePath> structuralPaths,
            Set<StructuralReferenceMatch> recovered) {

        QueryNodeResult {
            paths = List.copyOf(paths);
            structuralPaths = List.copyOf(structuralPaths);
            recovered = Set.copyOf(recovered);
        }
    }

    /** Completion-order aggregate containing final lightweight results only. */
    private static final class QueryExecution {

        /** Ordinary candidate paths. */
        private final List<ImpactPath> paths = new ArrayList<>();

        /** Structural candidate paths. */
        private final List<StructuralReferencePath> structuralPaths =
                new ArrayList<>();

        /** Structural bindings that reached PROJECT. */
        private final Set<StructuralReferenceMatch> recovered =
                new LinkedHashSet<>();

        void merge(final QueryNodeResult result) {
            paths.addAll(result.paths());
            structuralPaths.addAll(result.structuralPaths());
            recovered.addAll(result.recovered());
        }

        List<ImpactPath> paths() {
            return paths;
        }

        List<StructuralReferencePath> structuralPaths() {
            return structuralPaths;
        }

        Set<StructuralReferenceMatch> recovered() {
            return recovered;
        }
    }
}
