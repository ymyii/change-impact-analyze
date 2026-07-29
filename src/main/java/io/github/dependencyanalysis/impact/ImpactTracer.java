package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.build.BuildResult;
import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.callgraph.CallEdge;
import io.github.dependencyanalysis.callgraph.CallGraph;
import io.github.dependencyanalysis.callgraph.MethodId;
import io.github.dependencyanalysis.diagnostic.DiagnosticCollector;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

// Wiki: wiki/features/impact-tracing.md - 影响追踪入口
/**
 * Main entry point for impact
 * tracing. Traces change points
 * back to affected business
 * methods through the call graph.
 *
 * <p>The trace runs four stages:
 * resolve seeds, reverse BFS,
 * build paths, and sort.</p>
 */
public final class ImpactTracer {

    /** Diagnostic stage name. */
    private static final String STAGE =
            "impact-trace";

    /** Diagnostic collector. */
    private final DiagnosticCollector
            diagnostics;

    /**
     * Creates a new tracer.
     *
     * @param diag diagnostic collector
     */
    public ImpactTracer(
            final DiagnosticCollector
                    diag) {
        this.diagnostics =
                Objects.requireNonNull(
                        diag, "diagnostics");
    }

    /**
     * Traces change points to
     * affected business methods.
     *
     * @param cps   change points
     * @param graph call graph
     * @param build build result
     * @return impact result
     * @throws ImpactException on
     *  any failure
     */
    public ImpactResult trace(
            final List<ChangePoint>
                    cps,
            final CallGraph graph,
            final BuildResult build) {
        Objects.requireNonNull(
                cps, "changePoints");
        Objects.requireNonNull(
                graph, "graph");
        Objects.requireNonNull(
                build, "build");
        diagnostics.startStage(STAGE);
        try {
            final ImpactResult result =
                    traceInternal(
                            cps, graph,
                            build);
            diagnostics.info(STAGE,
                    "Impact paths: "
                            + result.getPaths()
                            .size());
            diagnostics.endStage(
                    STAGE);
            return result;
        } catch (ImpactException e) {
            diagnostics.failStage(
                    STAGE,
                    e.getMessage());
            throw e;
        } catch (Exception e) {
            final String msg =
                    "Impact trace failed: "
                            + e.getMessage();
            diagnostics.failStage(
                    STAGE, msg);
            throw new ImpactException(
                    msg, e);
        }
    }

    /**
     * Internal trace logic
     * running four stages.
     *
     * @param cps   change points
     * @param graph call graph
     * @param build build result
     * @return impact result
     */
    private ImpactResult
            traceInternal(
            final List<ChangePoint>
                    cps,
            final CallGraph graph,
            final BuildResult build) {
        final Map<NotReportedReason,
                Integer> notReported =
                new EnumMap<>(
                        NotReportedReason
                                .class);
        final Map<ChangePoint,
                Set<MethodId>> seeds =
                resolveSeeds(cps, build);
        final Map<String, MethodId>
                cgIndex = buildCgIndex(
                graph);
        final Map<String, String>
                ownerModule =
                buildOwnerModuleMap(
                        build, graph);
        final List<ImpactPath> paths =
                new ArrayList<>();
        for (final ChangePoint cp
                : cps) {
            if (!ChangePointRefScanner
                    .isScannable(
                            cp.getKind())) {
                incrementReason(
                        notReported,
                        NotReportedReason
                                .CHANGE_KIND_NOT_APPLICABLE);
                continue;
            }
            final Set<MethodId> seedSet =
                    seeds.get(cp);
            if (seedSet == null
                    || seedSet.isEmpty()) {
                incrementReason(
                        notReported,
                        NotReportedReason
                                .NO_SEED_FOUND);
                continue;
            }
            for (final MethodId seed
                    : seedSet) {
                final MethodId resolved =
                        resolveInCg(
                                seed, cgIndex);
                if (resolved == null) {
                    incrementReason(
                            notReported,
                            NotReportedReason
                                    .INCOMPLETE_CHAIN);
                    continue;
                }
                final List<ImpactPath>
                        seedPaths =
                        reverseTrace(
                                resolved, cp,
                                graph,
                                ownerModule);
                paths.addAll(seedPaths);
            }
        }
        sortPaths(paths);
        return new ImpactResult(
                paths, notReported);
    }

    /**
     * Builds an index of call
     * graph methods by key
     * (owner#name#desc).
     *
     * @param graph call graph
     * @return index map
     */
    private Map<String, MethodId>
            buildCgIndex(
            final CallGraph graph) {
        final Map<String, MethodId>
                index = new HashMap<>();
        for (final MethodId m
                : graph.getMethods()) {
            index.put(methodKey(m), m);
        }
        return index;
    }

    /**
     * Builds a lookup key for
     * a method id using owner,
     * name and descriptor.
     *
     * @param m method id
     * @return lookup key
     */
    private static String methodKey(
            final MethodId m) {
        return m.owner() + "#"
                + m.name() + "#"
                + m.descriptor();
    }

    /**
     * Builds a map from class
     * owner to module name
     * using the scanner seeds
     * and build result.
     *
     * @param build build result
     * @param graph call graph
     * @return owner to module map
     */
    private Map<String, String>
            buildOwnerModuleMap(
            final BuildResult build,
            final CallGraph graph) {
        final Map<String, String>
                map = new HashMap<>();
        for (final MethodId m
                : graph.getMethods()) {
            if (!m.module().isEmpty()) {
                map.put(m.owner(),
                        m.module());
            }
        }
        for (final var out
                : build.getOutputs()) {
            final String mod =
                    out.getModulePath()
                            .toString();
            final Path dir =
                    out.getClassesDir();
            collectOwners(dir, mod, map);
        }
        return map;
    }

    /**
     * Collects class owners
     * from a classes directory.
     *
     * @param dir classes dir
     * @param mod module name
     * @param map owner map
     */
    private void collectOwners(
            final Path dir,
            final String mod,
            final Map<String, String>
                    map) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try {
            Files.walk(dir)
                    .filter(p -> p.toString()
                            .endsWith(".class"))
                    .forEach(p -> {
                        final String rel =
                                dir.relativize(p)
                                        .toString();
                        final String owner =
                                rel.replace(
                                        ".class", "")
                                        .replace(
                                                java.io.File
                                                        .separatorChar,
                                                '/');
                        if (!owner.contains(
                                "$$")) {
                            map.putIfAbsent(
                                    owner, mod);
                        }
                    });
        } catch (IOException e) {
            // skip
        }
    }

    /**
     * Resolves a scanner seed
     * to a call graph method id.
     *
     * @param seed  scanner seed
     * @param index cg index
     * @return resolved id or null
     */
    private MethodId resolveInCg(
            final MethodId seed,
            final Map<String, MethodId>
                    index) {
        final MethodId exact =
                index.get(
                        methodKey(seed));
        if (exact != null) {
            return exact;
        }
        return null;
    }

    /**
     * Stage 1: resolve seed
     * methods by scanning
     * application bytecode.
     *
     * @param cps   change points
     * @param build build result
     * @return map of cp to seeds
     */
    private Map<ChangePoint,
            Set<MethodId>> resolveSeeds(
            final List<ChangePoint>
                    cps,
            final BuildResult build) {
        final ChangePointRefScanner
                scanner =
                new ChangePointRefScanner();
        final Map<ChangePoint,
                Set<MethodId>> seeds =
                scanner.scan(cps, build);
        diagnostics.info(STAGE,
                "Seeds resolved: "
                        + countSeeds(seeds));
        return seeds;
    }

    /**
     * Counts total seeds.
     *
     * @param seeds seed map
     * @return total count
     */
    private int countSeeds(
            final Map<ChangePoint,
                    Set<MethodId>>
                    seeds) {
        int total = 0;
        for (final Set<MethodId> s
                : seeds.values()) {
            total += s.size();
        }
        return total;
    }

    /**
     * Stage 2+3: reverse BFS
     * from seed and build
     * impact paths.
     *
     * @param seed        seed method
     * @param cp          change point
     * @param graph       call graph
     * @param ownerModule owner to
     *                    module map
     * @return list of paths
     */
    private List<ImpactPath>
            reverseTrace(
            final MethodId seed,
            final ChangePoint cp,
            final CallGraph graph,
            final Map<String, String>
                    ownerModule) {
        final Set<MethodId> visited =
                new HashSet<>();
        final Map<MethodId, CallEdge>
                predecessor =
                new HashMap<>();
        final Queue<MethodId> queue =
                new LinkedList<>();
        visited.add(seed);
        queue.add(seed);
        while (!queue.isEmpty()) {
            final MethodId current =
                    queue.poll();
            final List<CallEdge> incoming =
                    graph.getIncomingEdges(
                            current);
            for (final CallEdge edge
                    : incoming) {
                final MethodId caller =
                        edge.getCaller();
                if (!visited.contains(
                        caller)) {
                    visited.add(caller);
                    predecessor.put(
                            caller, edge);
                    queue.add(caller);
                }
            }
        }
        final List<MethodId> roots =
                findRoots(visited, seed,
                        graph);
        final List<ImpactPath> paths =
                new ArrayList<>();
        for (final MethodId root
                : roots) {
            paths.add(buildPath(
                    root, seed, cp,
                    predecessor,
                    ownerModule));
        }
        return paths;
    }

    /**
     * Finds root methods in
     * the visited set. Roots
     * are methods that have
     * no incoming edges from
     * within the visited set.
     * If all methods are in
     * cycles (no roots), the
     * seed is used as root.
     *
     * @param visited visited set
     * @param seed    seed method
     * @param graph   call graph
     * @return list of roots
     */
    private List<MethodId> findRoots(
            final Set<MethodId> visited,
            final MethodId seed,
            final CallGraph graph) {
        final Set<MethodId> nonRoots =
                new HashSet<>();
        for (final MethodId m
                : visited) {
            final List<CallEdge> incoming =
                    graph.getIncomingEdges(
                            m);
            for (final CallEdge edge
                    : incoming) {
                if (visited.contains(
                        edge.getCaller())) {
                    nonRoots.add(m);
                    break;
                }
            }
        }
        final List<MethodId> roots =
                new ArrayList<>();
        for (final MethodId m
                : visited) {
            if (!nonRoots.contains(m)) {
                roots.add(m);
            }
        }
        if (roots.isEmpty()) {
            roots.add(seed);
        }
        roots.sort(methodIdComparator());
        return roots;
    }

    /**
     * Builds an impact path
     * from root to seed.
     *
     * @param root        root method
     * @param seed        seed method
     * @param cp          change point
     * @param predecessor predecessor map
     * @param ownerModule owner to
     *                    module map
     * @return impact path
     */
    private ImpactPath buildPath(
            final MethodId root,
            final MethodId seed,
            final ChangePoint cp,
            final Map<MethodId, CallEdge>
                    predecessor,
            final Map<String, String>
                    ownerModule) {
        final List<CallEdge> pathEdges =
                new ArrayList<>();
        final Set<String> modules =
                new LinkedHashSet<>();
        final List<String> boundaries =
                new ArrayList<>();
        MethodId current = root;
        while (!current.equals(seed)) {
            final CallEdge edge =
                    predecessor.get(
                            current);
            if (edge == null) {
                break;
            }
            pathEdges.add(edge);
            addModuleAndBoundary(
                    edge, modules,
                    boundaries,
                    ownerModule);
            current = edge.getCallee();
        }
        final String seedMod =
                resolveModule(
                        seed.owner(),
                        ownerModule);
        modules.add(seedMod);
        return new ImpactPath(
                root, cp, pathEdges,
                modules, boundaries);
    }

    /**
     * Adds module and detects
     * cross-module boundary
     * for an edge.
     *
     * @param edge        call edge
     * @param modules     module set
     * @param boundaries  boundary list
     * @param ownerModule owner to
     *                    module map
     */
    private void addModuleAndBoundary(
            final CallEdge edge,
            final Set<String> modules,
            final List<String>
                    boundaries,
            final Map<String, String>
                    ownerModule) {
        final String callerMod =
                resolveModule(
                        edge.getCaller()
                                .owner(),
                        ownerModule);
        final String calleeMod =
                resolveModule(
                        edge.getCallee()
                                .owner(),
                        ownerModule);
        modules.add(callerMod);
        modules.add(calleeMod);
        if (!callerMod.isEmpty()
                && !calleeMod.isEmpty()
                && !callerMod.equals(
                        calleeMod)) {
            boundaries.add(
                    callerMod + "->"
                            + calleeMod);
        }
    }

    /**
     * Resolves module name for
     * an owner class.
     *
     * @param owner       class owner
     * @param ownerModule owner map
     * @return module name
     */
    private String resolveModule(
            final String owner,
            final Map<String, String>
                    ownerModule) {
        final String mod =
                ownerModule.get(owner);
        return mod == null ? "" : mod;
    }

    /**
     * Stage 4: sort paths with
     * stable sort key.
     *
     * @param paths paths to sort
     */
    private void sortPaths(
            final List<ImpactPath>
                    paths) {
        paths.sort(
                pathComparator());
    }

    /**
     * Returns the comparator
     * for impact paths.
     *
     * @return path comparator
     */
    static Comparator<ImpactPath>
            pathComparator() {
        return Comparator
                .comparing(
                        (ImpactPath p) ->
                                p.getAffectedMethod()
                                        .owner())
                .thenComparing(p ->
                        p.getAffectedMethod()
                                .name())
                .thenComparing(p ->
                        p.getAffectedMethod()
                                .descriptor())
                .thenComparing(p ->
                        p.getChangePoint()
                                .getOwner())
                .thenComparing(p ->
                        p.getChangePoint()
                                .getName()
                                == null
                                ? ""
                                : p.getChangePoint()
                                .getName())
                .thenComparingInt(p ->
                        p.getChangePoint()
                                .getKind()
                                .ordinal());
    }

    /**
     * Returns a comparator
     * for method ids.
     *
     * @return method id comparator
     */
    private static Comparator<MethodId>
            methodIdComparator() {
        return Comparator
                .comparing(MethodId::owner)
                .thenComparing(
                        MethodId::name)
                .thenComparing(
                        MethodId::descriptor);
    }

    /**
     * Increments a not-reported
     * reason count.
     *
     * @param map    stats map
     * @param reason reason
     */
    private void incrementReason(
            final Map<NotReportedReason,
                    Integer> map,
            final NotReportedReason
                    reason) {
        map.merge(reason, 1,
                Integer::sum);
    }
}
