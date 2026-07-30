package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.BinaryDirectoryTreeModule;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.CallGraphBuilder;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.AllApplicationEntrypoints;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.PropagationCallGraphBuilder;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.TypeName;

import io.github.dependencyanalysis.build.BuildResult;
import io.github.dependencyanalysis.build.ModuleBuildOutput;
import io.github.dependencyanalysis.diagnostic.DiagnosticCollector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// Wiki: wiki/features/call-graph-engine.md - Call Graph 构建入口
/**
 * Builds a call graph from a
 * {@link BuildResult} using the WALA
 * RTA engine.
 */
public final class CallGraphEngine {

    /** Stage name for diagnostics. */
    private static final String STAGE =
            "call-graph";

    /** Diagnostic collector. */
    private final DiagnosticCollector
            diagnostics;

    /**
     * Creates a new engine.
     *
     * @param diag diagnostic collector
     */
    public CallGraphEngine(
            final DiagnosticCollector
                    diag) {
        this.diagnostics =
                Objects.requireNonNull(
                        diag, "diagnostics");
    }

    /**
     * Builds a call graph from a
     * build result using WALA RTA.
     *
     * @param result build result
     * @return immutable call graph
     * @throws CallGraphException on
     *  any failure
     */
    public CallGraph build(
            final BuildResult result) {
        final AnalysisScope scope =
                AnalysisScope.createJavaAnalysisScope();
        try {
            scope.addStdLibs(true,
                    ClassLoaderReference.Primordial);
        } catch (IOException exception) {
            throw new CallGraphException(
                    "Unable to prepare analyzer JDK scope",
                    exception);
        }
        return build(result, scope, 0L);
    }

    /**
     * Builds a call graph with a prepared target JDK scope.
     *
     * @param result build result
     * @param baseScope prepared target JDK scope
     * @param timeoutSeconds RTA timeout, zero for unlimited
     * @return immutable call graph
     */
    public CallGraph build(
            final BuildResult result,
            final AnalysisScope baseScope,
            final long timeoutSeconds) {
        Objects.requireNonNull(
                result, "result");
        Objects.requireNonNull(
                baseScope, "baseScope");
        if (timeoutSeconds < 0) {
            throw new IllegalArgumentException(
                    "timeoutSeconds must be >= 0");
        }
        final long t0 =
                System.currentTimeMillis();
        final Runtime rt =
                Runtime.getRuntime();
        final long maxMem =
                rt.totalMemory()
                        - rt.freeMemory();
        diagnostics.startStage(STAGE);
        try {
            final CallGraph graph = buildInternal(
                    result, baseScope, timeoutSeconds,
                    t0, rt, maxMem);
            diagnostics.endStage(STAGE);
            return graph;
        } catch (CallGraphException e) {
            diagnostics.failStage(STAGE,
                    e.getMessage());
            throw e;
        } catch (Exception e) {
            diagnostics.failStage(STAGE,
                    "Call graph build failed: "
                            + e.getMessage());
            throw new CallGraphException(
                    "Call graph build failed",
                    e);
        }
    }

    /**
     * Internal build logic that
     * orchestrates WALA phases.
     *
     * @param result build result
     * @param baseScope prepared target JDK scope
     * @param timeoutSeconds RTA timeout
     * @param t0     start time millis
     * @param rt     runtime instance
     * @param maxMem initial memory
     * @return immutable call graph
     */
    private CallGraph buildInternal(
            final BuildResult result,
            final AnalysisScope baseScope,
            final long timeoutSeconds,
            final long t0,
            final Runtime rt,
            final long maxMem) {
        final AnalysisScope scope =
                createScope(result, baseScope);
        diagnostics.info(STAGE,
                "Application scope prepared");
        final IClassHierarchy cha =
                createClassHierarchy(scope);
        diagnostics.info(STAGE,
                "CHA classes: " + cha.getNumberOfClasses());
        final List<Entrypoint> eps =
                createEntrypoints(scope, cha);
        diagnostics.info(STAGE,
                "Application entrypoints: " + eps.size());
        final com.ibm.wala.ipa.callgraph.CallGraph
                walaCg =
                buildRtaCallGraph(
                        scope, cha, eps,
                        timeoutSeconds);
        diagnostics.info(STAGE,
                "RTA construction completed");
        final Map<String, String> modMap =
                buildModuleMap(result);
        final Set<MethodId> methods =
                new HashSet<>();
        final List<CallEdge> edges =
                new ArrayList<>();
        extractEdges(
                walaCg, cha, modMap,
                methods, edges);
        diagnostics.info(STAGE,
                "Application edges extracted: methods="
                        + methods.size() + ", edges="
                        + edges.size());
        final Map<MethodId,
                Set<MethodId>> ovr =
                buildOverrideMap(
                        cha, modMap);
        addAllAppMethods(
                cha, modMap, methods);
        new ServiceLoaderEnricher(
                diagnostics).enrich(
                result.getOutputs(),
                methods, edges, ovr);
        new ReflectionEnricher(
                diagnostics).enrich(
                result.getOutputs(),
                methods, edges);
        final long elapsed =
                System.currentTimeMillis()
                        - t0;
        final long mem =
                computeMemUsed(rt, maxMem);
        final CallGraphStats st =
                new CallGraphStats(
                        methods.size(),
                        edges.size(),
                        elapsed, mem);
        diagnostics.info(STAGE,
                "Call graph: "
                        + methods.size()
                        + " methods, "
                        + edges.size()
                        + " edges");
        return new CallGraph(
                methods, edges, ovr, st);
    }

    /**
     * Creates the WALA analysis
     * scope from build outputs.
     *
     * @param result build result
     * @param scope prepared target JDK scope
     * @return analysis scope
     */
    private AnalysisScope createScope(
            final BuildResult result,
            final AnalysisScope scope) {
        for (final ModuleBuildOutput out
                : result.getOutputs()) {
            final File dir =
                    out.getClassesDir()
                            .toFile();
            if (!dir.exists()
                    || !dir.isDirectory()) {
                throw new CallGraphException(
                        "Classes directory"
                                + " does not exist: "
                                + out.getClassesDir());
            }
            scope.addToScope(
                    ClassLoaderReference
                            .Application,
                    new BinaryDirectoryTreeModule(
                            dir));
        }
        return scope;
    }

    /**
     * Creates the class hierarchy
     * from the analysis scope.
     *
     * @param scope analysis scope
     * @return class hierarchy
     */
    private IClassHierarchy
            createClassHierarchy(
            final AnalysisScope scope) {
        try {
            return ClassHierarchyFactory
                    .make(scope);
        } catch (ClassHierarchyException e) {
            throw new CallGraphException(
                    "Failed to build"
                            + " class hierarchy",
                    e);
        }
    }

    /**
     * Creates entry points from all
     * application methods.
     *
     * @param scope analysis scope
     * @param cha   class hierarchy
     * @return iterable of entry points
     */
    private List<Entrypoint>
            createEntrypoints(
            final AnalysisScope scope,
            final IClassHierarchy cha) {
        final List<Entrypoint> result =
                new ArrayList<>();
        for (Entrypoint entrypoint
                : new AllApplicationEntrypoints(
                        scope, cha)) {
            result.add(entrypoint);
        }
        return result;
    }

    /**
     * Builds an RTA call graph.
     *
     * @param scope analysis scope
     * @param cha   class hierarchy
     * @param eps   entry points
     * @param timeoutSeconds RTA timeout
     * @return WALA call graph
     */
    private com.ibm.wala.ipa.callgraph.CallGraph
            buildRtaCallGraph(
            final AnalysisScope scope,
            final IClassHierarchy cha,
            final Iterable<Entrypoint> eps,
            final long timeoutSeconds) {
        final AnalysisOptions opts =
                new AnalysisOptions(
                        scope, eps);
        final IAnalysisCacheView cache =
                new AnalysisCacheImpl();
        Util.addDefaultSelectors(
                opts, cha);
        final CallGraphBuilder<InstanceKey>
                builder =
                Util.makeRTABuilder(
                        opts, cache, cha);
        if (builder instanceof PropagationCallGraphBuilder) {
            ((PropagationCallGraphBuilder) builder)
                    .setInstanceKeys(
                            new QuietClassBasedInstanceKeys(
                                    opts, cha));
        }
        try (CallGraphProgressMonitor monitor =
                     new CallGraphProgressMonitor(
                             diagnostics, timeoutSeconds)) {
            try {
                return builder.makeCallGraph(
                        opts, monitor);
            } catch (Exception exception) {
                if (monitor.isTimedOut()) {
                    throw new CallGraphException(
                            "RTA call graph timed out after "
                                    + timeoutSeconds
                                    + " seconds",
                            exception);
                }
                final String detail = exception.getMessage() == null
                        ? exception.getClass().getSimpleName()
                        : exception.getMessage();
                throw new CallGraphException(
                        "RTA call graph construction failed: "
                                + detail,
                        exception);
            }
        }
    }

    /**
     * Builds a module path to module
     * name map from build outputs.
     *
     * @param result build result
     * @return module map
     */
    private Map<String, String>
            buildModuleMap(
            final BuildResult result) {
        final Map<String, String> map =
                new HashMap<>();
        for (final ModuleBuildOutput out
                : result.getOutputs()) {
            map.put(
                    out.getClassesDir()
                            .toFile()
                            .getAbsolutePath(),
                    out.getModulePath()
                            .toString());
        }
        return map;
    }

    /**
     * Extracts call edges from the
     * WALA call graph.
     *
     * @param cg     WALA call graph
     * @param cha    class hierarchy
     * @param modMap module map
     * @param methods output method set
     * @param edges  output edge list
     */
    private void extractEdges(
            final com.ibm.wala.ipa.callgraph.CallGraph
                    cg,
            final IClassHierarchy cha,
            final Map<String, String> modMap,
            final Set<MethodId> methods,
            final List<CallEdge> edges) {
        final Iterator<CGNode> it =
                cg.iterator();
        while (it.hasNext()) {
            final CGNode node = it.next();
            final IMethod m =
                    node.getMethod();
            if (!isApplication(m, cha)) {
                continue;
            }
            final MethodId caller =
                    toMethodId(m, cha, modMap);
            methods.add(caller);
            final Iterator<CallSiteReference>
                    siteIt =
                    node.iterateCallSites();
            while (siteIt.hasNext()) {
                final CallSiteReference site =
                        siteIt.next();
                final Set<CGNode> targets =
                        cg.getPossibleTargets(
                                node, site);
                for (final CGNode tgt
                        : targets) {
                    final IMethod tm =
                            tgt.getMethod();
                    if (!isApplication(
                            tm, cha)) {
                        continue;
                    }
                    final MethodId callee =
                            toMethodId(
                                    tm, cha,
                                    modMap);
                    methods.add(callee);
                    final EdgeKind kind =
                            resolveEdgeKind(
                                    site, tm);
                    if (!caller.equals(
                            callee)) {
                        edges.add(new CallEdge(
                                caller, callee,
                                kind,
                                kind.name()));
                    }
                }
            }
        }
    }

    /**
     * Resolves the edge kind from
     * a call site reference.
     *
     * @param site   call site reference
     * @param target target method
     * @return edge kind
     */
    private EdgeKind resolveEdgeKind(
            final CallSiteReference site,
            final IMethod target) {
        final IInvokeInstruction.IDispatch
                disp =
                site.getInvocationCode();
        if (disp
                == IInvokeInstruction.Dispatch
                        .STATIC) {
            return EdgeKind.INVOKE_STATIC;
        }
        if (disp
                == IInvokeInstruction.Dispatch
                        .SPECIAL) {
            return EdgeKind.INVOKE_SPECIAL;
        }
        if (disp
                == IInvokeInstruction.Dispatch
                        .INTERFACE) {
            return EdgeKind.INVOKE_INTERFACE;
        }
        return EdgeKind.INVOKE_VIRTUAL;
    }

    /**
     * Adds all application class
     * methods to the method set.
     *
     * @param cha    class hierarchy
     * @param modMap module map
     * @param methods method set
     */
    private void addAllAppMethods(
            final IClassHierarchy cha,
            final Map<String, String> modMap,
            final Set<MethodId> methods) {
        for (final IClass klass : cha) {
            if (!isApplicationClass(
                    klass, cha)) {
                continue;
            }
            final Collection<? extends IMethod>
                    dms =
                    klass.getDeclaredMethods();
            for (final IMethod dm : dms) {
                methods.add(toMethodId(
                        dm, cha, modMap));
            }
        }
    }

    /**
     * Builds the override map from
     * the class hierarchy.
     *
     * @param cha    class hierarchy
     * @param modMap module map
     * @return override map
     */
    private Map<MethodId,
            Set<MethodId>> buildOverrideMap(
            final IClassHierarchy cha,
            final Map<String, String> modMap) {
        final Map<MethodId,
                Set<MethodId>> map =
                new HashMap<>();
        for (final IClass klass : cha) {
            if (!isApplicationClass(
                    klass, cha)) {
                continue;
            }
            final Collection<? extends IMethod>
                    dms =
                    klass.getDeclaredMethods();
            for (final IMethod dm : dms) {
                if (dm.isStatic()) {
                    continue;
                }
                findOverriddenMethods(
                        klass, dm, cha,
                        modMap, map);
            }
        }
        return map;
    }

    /**
     * Finds methods that the given
     * method overrides and populates
     * the override map.
     *
     * @param klass  declaring class
     * @param method method to check
     * @param cha    class hierarchy
     * @param modMap module map
     * @param map    override map
     */
    private void findOverriddenMethods(
            final IClass klass,
            final IMethod method,
            final IClassHierarchy cha,
            final Map<String, String> modMap,
            final Map<MethodId,
                    Set<MethodId>> map) {
        final IClass sup =
                klass.getSuperclass();
        if (sup != null
                && isApplicationClass(
                        sup, cha)) {
            final IMethod parent =
                    sup.getMethod(
                            method.getSelector());
            if (parent != null
                    && !parent.isStatic()
                    && isApplicationClass(
                            parent
                                    .getDeclaringClass(),
                            cha)) {
                final MethodId pid =
                        toMethodId(
                                parent, cha,
                                modMap);
                final MethodId cid =
                        toMethodId(
                                method, cha,
                                modMap);
                map.computeIfAbsent(pid,
                                k -> new HashSet<>())
                        .add(cid);
            }
        }
        for (final IClass iface
                : klass.getDirectInterfaces()) {
            if (!isApplicationClass(
                    iface, cha)) {
                continue;
            }
            final IMethod ifaceMethod =
                    iface.getMethod(
                            method.getSelector());
            if (ifaceMethod != null) {
                final MethodId pid =
                        toMethodId(
                                ifaceMethod,
                                cha, modMap);
                final MethodId cid =
                        toMethodId(
                                method, cha,
                                modMap);
                map.computeIfAbsent(pid,
                                k -> new HashSet<>())
                        .add(cid);
            }
        }
    }

    /**
     * Converts a WALA IMethod to a
     * MethodId.
     *
     * @param m      WALA method
     * @param cha    class hierarchy
     * @param modMap module map
     * @return method id
     */
    private MethodId toMethodId(
            final IMethod m,
            final IClassHierarchy cha,
            final Map<String, String> modMap) {
        final String owner =
                toInternalName(
                        m.getDeclaringClass()
                                .getName());
        final String name =
                m.getName().toString();
        final Descriptor desc =
                m.getDescriptor();
        final String descriptor =
                desc.toString();
        final String mod =
                resolveModule(
                        m.getDeclaringClass(),
                        modMap);
        return new MethodId(
                owner, name, descriptor,
                mod, owner + ".class");
    }

    /**
     * Converts a WALA TypeName to a
     * JVM internal name.
     *
     * @param typeName WALA type name
     * @return JVM internal name
     */
    private String toInternalName(
            final TypeName typeName) {
        String s = typeName.toString();
        if (s.startsWith("L")) {
            s = s.substring(1);
        }
        if (s.endsWith(";")) {
            s = s.substring(0,
                    s.length() - 1);
        }
        return s;
    }

    /**
     * Resolves module name for a
     * class from the module map.
     *
     * @param klass  WALA class
     * @param modMap module map
     * @return module name
     */
    private String resolveModule(
            final IClass klass,
            final Map<String, String> modMap) {
        final String loader =
                klass.getClassLoader()
                        .toString();
        for (final Map.Entry<String, String>
                entry : modMap.entrySet()) {
            if (loader.contains(
                    entry.getKey())) {
                return entry.getValue();
            }
        }
        return "";
    }

    /**
     * Checks if a method belongs to
     * the application class loader.
     *
     * @param m   WALA method
     * @param cha class hierarchy
     * @return true if application
     */
    private boolean isApplication(
            final IMethod m,
            final IClassHierarchy cha) {
        return isApplicationClass(
                m.getDeclaringClass(), cha);
    }

    /**
     * Checks if a class belongs to
     * the application class loader.
     *
     * @param klass WALA class
     * @param cha   class hierarchy
     * @return true if application
     */
    private boolean isApplicationClass(
            final IClass klass,
            final IClassHierarchy cha) {
        return cha.getScope()
                .isApplicationLoader(
                        klass.getClassLoader());
    }

    /**
     * Computes peak memory usage.
     *
     * @param rt     runtime instance
     * @param minMem initial memory
     * @return peak memory bytes
     */
    private long computeMemUsed(
            final Runtime rt,
            final long minMem) {
        final long cur =
                rt.totalMemory()
                        - rt.freeMemory();
        return Math.max(cur, minMem);
    }

}
