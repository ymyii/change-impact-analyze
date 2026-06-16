package io.github.changeimpact.analyze.callgraph;

import io.github.changeimpact.analyze.build.ModuleBuildOutput;
import io.github.changeimpact.analyze.diagnostic.DiagnosticCollector;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Enriches call graph with
 * Class.forName reflection edges
 * and detects dynamic proxy usage.
 */
final class ReflectionEnricher {

    /** Stage name for diagnostics. */
    private static final String STAGE =
            "call-graph";

    /** Class.forName owner. */
    private static final String CLS_OWNER =
            "java/lang/Class";

    /** Class.forName method name. */
    private static final String FOR_NAME =
            "forName";

    /** Proxy owner. */
    private static final String PROXY_OWNER =
            "java/lang/reflect/Proxy";

    /** Proxy newProxyInstance name. */
    private static final String NEW_PROXY =
            "newProxyInstance";

    /** Diagnostic collector. */
    private final DiagnosticCollector diag;

    /** Current method set. */
    private Set<MethodId> methods;

    /** Current edge list. */
    private List<CallEdge> edges;

    /** Existing edge keys for dedup. */
    private Set<String> existing;

    /** All application classes dirs. */
    private List<Path> allDirs;

    /**
     * Creates a new enricher.
     *
     * @param collector diagnostic collector
     */
    ReflectionEnricher(
            final DiagnosticCollector
                    collector) {
        this.diag = collector;
    }

    /**
     * Enriches the call graph with
     * reflection edges.
     *
     * @param outputs module outputs
     * @param mts     method set
     * @param edgs    edge list
     */
    void enrich(
            final List<ModuleBuildOutput> outputs,
            final Set<MethodId> mts,
            final List<CallEdge> edgs) {
        this.methods = mts;
        this.edges = edgs;
        this.existing = buildEdgeKeySet(edgs);
        this.allDirs = collectDirs(outputs);
        for (final ModuleBuildOutput out
                : outputs) {
            scanModule(out);
        }
    }

    /**
     * Collects all classes directories
     * from module outputs.
     *
     * @param outputs module outputs
     * @return list of classes dirs
     */
    private List<Path> collectDirs(
            final List<ModuleBuildOutput> outputs) {
        final List<Path> dirs =
                new ArrayList<>();
        for (final ModuleBuildOutput out
                : outputs) {
            dirs.add(out.getClassesDir());
        }
        return dirs;
    }

    /**
     * Builds a set of existing edge
     * keys for deduplication.
     *
     * @param edgs existing edges
     * @return set of edge keys
     */
    private Set<String> buildEdgeKeySet(
            final List<CallEdge> edgs) {
        final Set<String> set =
                new HashSet<>();
        for (final CallEdge e : edgs) {
            set.add(edgeKey(
                    e.getCaller(),
                    e.getCallee()));
        }
        return set;
    }

    /**
     * Creates a dedup key for an edge.
     *
     * @param caller caller method id
     * @param callee callee method id
     * @return edge key string
     */
    private String edgeKey(
            final MethodId caller,
            final MethodId callee) {
        return caller.owner() + "."
                + caller.name()
                + caller.descriptor()
                + "->"
                + callee.owner() + "."
                + callee.name()
                + callee.descriptor();
    }

    /**
     * Scans a module for reflection
     * patterns.
     *
     * @param out module output
     */
    private void scanModule(
            final ModuleBuildOutput out) {
        final Path classesDir =
                out.getClassesDir();
        if (!Files.isDirectory(classesDir)) {
            return;
        }
        try (Stream<Path> walk =
                Files.walk(classesDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.toString()
                            .endsWith(".class"))
                    .forEach(p -> scanClass(p));
        } catch (IOException e) {
            diag.warn(STAGE,
                    "Reflection scan failed: "
                            + e.getMessage());
        }
    }

    /**
     * Scans a single class file for
     * reflection patterns.
     *
     * @param classFile class file path
     */
    private void scanClass(
            final Path classFile) {
        try {
            final byte[] bytes =
                    Files.readAllBytes(
                            classFile);
            final ClassReader cr =
                    new ClassReader(bytes);
            final String ownerName =
                    cr.getClassName();
            final ReflectContext ctx =
                    new ReflectContext(
                            ownerName);
            cr.accept(new ReflectVisitor(
                    ctx), 0);
        } catch (IOException e) {
            // skip unreadable class
        }
    }

    /**
     * Shared context for reflection
     * detection visitors.
     */
    private final class ReflectContext {

        /** Owner class internal name. */
        private final String owner;

        /**
         * Creates a new context.
         *
         * @param ownerName owner name
         */
        ReflectContext(
                final String ownerName) {
            this.owner = ownerName;
        }
    }

    /**
     * ClassVisitor that delegates to
     * method-level reflection detector.
     */
    private final class ReflectVisitor
            extends ClassVisitor {

        /** Shared context. */
        private final ReflectContext ctx;

        /**
         * Creates a new visitor.
         *
         * @param context shared context
         */
        ReflectVisitor(
                final ReflectContext context) {
            super(Opcodes.ASM9);
            this.ctx = context;
        }

        @Override
        public MethodVisitor visitMethod(
                final int access,
                final String name,
                final String descriptor,
                final String signature,
                final String[] exceptions) {
            return new ReflectMethodVisitor(
                    ctx, name, descriptor);
        }
    }

    /**
     * MethodVisitor that detects
     * Class.forName and Proxy patterns.
     */
    private final class ReflectMethodVisitor
            extends MethodVisitor {

        /** Shared context. */
        private final ReflectContext ctx;

        /** Method name. */
        private final String mName;

        /** Method descriptor. */
        private final String mDesc;

        /** Last LDC string constant. */
        private String lastLdc;

        /**
         * Creates a new visitor.
         *
         * @param context shared context
         * @param name    method name
         * @param desc    method desc
         */
        ReflectMethodVisitor(
                final ReflectContext context,
                final String name,
                final String desc) {
            super(Opcodes.ASM9);
            this.ctx = context;
            this.mName = name;
            this.mDesc = desc;
        }

        @Override
        public void visitLdcInsn(
                final Object value) {
            if (value instanceof String) {
                lastLdc = (String) value;
            } else {
                lastLdc = null;
            }
            super.visitLdcInsn(value);
        }

        @Override
        public void visitMethodInsn(
                final int opcode,
                final String mOwner,
                final String name,
                final String descriptor,
                final boolean isIface) {
            if (CLS_OWNER.equals(mOwner)
                    && FOR_NAME.equals(name)) {
                handleForName();
            }
            if (PROXY_OWNER.equals(mOwner)
                    && NEW_PROXY.equals(name)) {
                handleProxy();
            }
            lastLdc = null;
            super.visitMethodInsn(
                    opcode, mOwner,
                    name, descriptor,
                    isIface);
        }

        /**
         * Handles a Class.forName call.
         */
        private void handleForName() {
            final String literal = lastLdc;
            lastLdc = null;
            if (literal == null) {
                diag.warn(STAGE,
                        "Non-literal"
                                + " Class.forName in "
                                + ctx.owner + "."
                                + mName + mDesc);
                return;
            }
            final String internal =
                    literal.replace('.', '/');
            if (!isInScope(internal)) {
                diag.warn(STAGE,
                        "Class.forName literal"
                                + " not in scope: "
                                + literal + " in "
                                + ctx.owner + "."
                                + mName + mDesc);
                return;
            }
            final MethodId caller =
                    new MethodId(
                            ctx.owner, mName, mDesc,
                            "",
                            ctx.owner + ".class");
            final MethodId callee =
                    new MethodId(
                            internal,
                            "<clinit>", "()V",
                            "",
                            internal + ".class");
            final String key =
                    edgeKey(caller, callee);
            if (existing.contains(key)) {
                return;
            }
            methods.add(caller);
            methods.add(callee);
            edges.add(new CallEdge(
                    caller, callee,
                    EdgeKind.REFLECTION_LITERAL,
                    "Class.forName(\""
                            + literal + "\")"));
            existing.add(key);
        }

        /**
         * Handles a Proxy.newProxyInstance
         * call.
         */
        private void handleProxy() {
            diag.warn(STAGE,
                    "Dynamic proxy in "
                            + ctx.owner + "."
                            + mName + mDesc
                            + "; skipped");
        }

        /**
         * Checks if a class internal name
         * is in any application classes dir.
         *
         * @param internal internal class name
         * @return true if in scope
         */
        private boolean isInScope(
                final String internal) {
            for (final Path dir : allDirs) {
                final Path f = dir.resolve(
                        internal + ".class");
                if (Files.exists(f)) {
                    return true;
                }
            }
            return false;
        }
    }
}
