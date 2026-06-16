package io.github.changeimpact.analyze.callgraph;

import com.ibm.wala.classLoader.BinaryDirectoryTreeModule;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.ModuleEntry;
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
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.TypeName;

import io.github.changeimpact.analyze.build.BuildResult;
import io.github.changeimpact.analyze.build.ModuleBuildOutput;
import io.github.changeimpact.analyze.diagnostic.DiagnosticCollector;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

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
        Objects.requireNonNull(
                result, "result");
        final long t0 =
                System.currentTimeMillis();
        final Runtime rt =
                Runtime.getRuntime();
        final long maxMem =
                rt.totalMemory()
                        - rt.freeMemory();
        try {
            return buildInternal(
                    result, t0, rt, maxMem);
        } catch (CallGraphException e) {
            throw e;
        } catch (Exception e) {
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
     * @param t0     start time millis
     * @param rt     runtime instance
     * @param maxMem initial memory
     * @return immutable call graph
     */
    private CallGraph buildInternal(
            final BuildResult result,
            final long t0,
            final Runtime rt,
            final long maxMem) {
        final AnalysisScope scope =
                createScope(result);
        final IClassHierarchy cha =
                createClassHierarchy(scope);
        final Iterable<Entrypoint> eps =
                createEntrypoints(scope, cha);
        final com.ibm.wala.ipa.callgraph.CallGraph
                walaCg =
                buildRtaCallGraph(
                        scope, cha, eps);
        final Map<String, String> modMap =
                buildModuleMap(result);
        final Set<MethodId> methods =
                new HashSet<>();
        final List<CallEdge> edges =
                new ArrayList<>();
        extractEdges(
                walaCg, cha, modMap,
                methods, edges);
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
     * @return analysis scope
     */
    private AnalysisScope createScope(
            final BuildResult result) {
        final AnalysisScope scope =
                AnalysisScope
                        .createJavaAnalysisScope();
        addJdkPrimordial(scope);
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
     * Adds JDK primordial classes
     * to the analysis scope using
     * the JRT filesystem.
     *
     * @param scope analysis scope
     */
    private void addJdkPrimordial(
            final AnalysisScope scope) {
        try {
            final FileSystem jrtFs =
                    getOrCreateJrtFs();
            if (jrtFs == null) {
                return;
            }
            final Path modules =
                    jrtFs.getPath("modules");
            scope.addToScope(
                    ClassLoaderReference
                            .Primordial,
                    new JrtDirectoryModule(
                            modules));
        } catch (Exception e) {
            diagnostics.warn(STAGE,
                    "Failed to add JDK"
                            + " primordial: "
                            + e.getMessage());
        }
    }

    /**
     * Gets or creates the JRT
     * filesystem.
     *
     * @return JRT filesystem or null
     */
    private FileSystem getOrCreateJrtFs() {
        final URI uri =
                URI.create("jrt:/");
        try {
            return FileSystems
                    .newFileSystem(
                            uri,
                            Collections
                                    .emptyMap());
        } catch (FileSystemAlreadyExistsException e) {
            return FileSystems
                    .getFileSystem(uri);
        } catch (Exception e) {
            return null;
        }
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
    private Iterable<Entrypoint>
            createEntrypoints(
            final AnalysisScope scope,
            final IClassHierarchy cha) {
        return new AllApplicationEntrypoints(
                scope, cha);
    }

    /**
     * Builds an RTA call graph.
     *
     * @param scope analysis scope
     * @param cha   class hierarchy
     * @param eps   entry points
     * @return WALA call graph
     */
    private com.ibm.wala.ipa.callgraph.CallGraph
            buildRtaCallGraph(
            final AnalysisScope scope,
            final IClassHierarchy cha,
            final Iterable<Entrypoint> eps) {
        final AnalysisOptions opts =
                new AnalysisOptions(
                        scope, eps);
        final IAnalysisCacheView cache =
                new AnalysisCacheImpl();
        Util.addDefaultSelectors(
                opts, cha);
        Util.addDefaultBypassLogic(
                opts,
                getClass().getClassLoader(),
                cha);
        final CallGraphBuilder<InstanceKey>
                builder =
                Util.makeRTABuilder(
                        opts, cache, cha);
        try {
            return builder.makeCallGraph(
                    opts, null);
        } catch (Exception e) {
            throw new CallGraphException(
                    "RTA call graph"
                            + " construction failed",
                    e);
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

    /**
     * Module that wraps a JRT
     * modules directory and provides
     * class file entries.
     */
    private static final class
            JrtDirectoryModule
            implements Module {

        /** Root modules path. */
        private final Path root;

        /**
         * Creates a JRT directory module.
         *
         * @param modulesPath modules path
         */
        JrtDirectoryModule(
                final Path modulesPath) {
            this.root = modulesPath;
        }

        @Override
        public Iterator<? extends
                ModuleEntry> getEntries() {
            try {
                final List<ModuleEntry>
                        entries =
                        new ArrayList<>();
                try (Stream<Path> mods =
                        Files.list(root)) {
                    mods.filter(
                                    Files::isDirectory)
                            .forEach(modDir ->
                                    collectClasses(
                                            modDir,
                                            entries));
                }
                return entries.iterator();
            } catch (IOException e) {
                return Collections
                        .<ModuleEntry>emptyList()
                        .iterator();
            }
        }

        /**
         * Collects class file entries
         * from a module directory.
         *
         * @param modDir  module directory
         * @param entries output list
         */
        private void collectClasses(
                final Path modDir,
                final List<ModuleEntry>
                        entries) {
            try (Stream<Path> walk =
                    Files.walk(modDir)) {
                walk.filter(Files::isRegularFile)
                        .filter(p -> p
                                .toString()
                                .endsWith(
                                        ".class"))
                        .forEach(p ->
                                entries.add(
                                        new JrtModuleEntry(
                                                p)));
            } catch (IOException e) {
                // skip module
            }
        }
    }

    /**
     * Module entry for a single
     * class file in the JRT filesystem.
     */
    private static final class
            JrtModuleEntry
            implements ModuleEntry {

        /** Class file path. */
        private final Path path;

        /**
         * Creates a JRT module entry.
         *
         * @param filePath class file path
         */
        JrtModuleEntry(final Path filePath) {
            this.path = filePath;
        }

        @Override
        public String getName() {
            final String s =
                    path.toString();
            // JRT paths look like:
            // modules/java.base/java/lang/Object.class
            // We need: java/lang/Object.class
            final int firstSlash =
                    s.indexOf('/');
            if (firstSlash < 0) {
                return s;
            }
            final int secondSlash =
                    s.indexOf('/',
                            firstSlash + 1);
            if (secondSlash < 0) {
                return s;
            }
            return s.substring(
                    secondSlash + 1);
        }

        @Override
        public boolean isClassFile() {
            return path.toString()
                    .endsWith(".class");
        }

        @Override
        public boolean isSourceFile() {
            return false;
        }

        @Override
        public InputStream getInputStream() {
            try {
                return Files.newInputStream(
                        path);
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Cannot read: "
                                + path, e);
            }
        }

        @Override
        public boolean isModuleFile() {
            return false;
        }

        @Override
        public Module asModule() {
            return null;
        }

        @Override
        public String getClassName() {
            final String name = getName();
            if (name.endsWith(".class")) {
                return name.substring(
                        0, name.length()
                                - ".class"
                                        .length());
            }
            return name;
        }

        @Override
        public Module getContainer() {
            return null;
        }
    }
}
