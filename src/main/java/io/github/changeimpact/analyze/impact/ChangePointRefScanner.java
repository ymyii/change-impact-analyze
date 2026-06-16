package io.github.changeimpact.analyze.impact;

import io.github.changeimpact.analyze.build.BuildResult;
import io.github.changeimpact.analyze.build.ModuleBuildOutput;
import io.github.changeimpact.analyze.bytecode.ChangePoint;
import io.github.changeimpact.analyze.bytecode.ChangePointKind;
import io.github.changeimpact.analyze.callgraph.MethodId;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scans application bytecode to
 * find methods that reference
 * change points. Package-private
 * for use by
 * {@link ImpactTracer}.
 */
final class ChangePointRefScanner {

    /** Class file suffix. */
    private static final String
            CLASS_SUFFIX = ".class";

    /**
     * Creates a new scanner.
     */
    ChangePointRefScanner() {
    }

    /**
     * Scans application bytecode
     * to find seed methods for
     * each change point.
     *
     * @param cps   change points
     * @param build build result
     * @return map of change point
     *  to seed method ids
     */
    Map<ChangePoint, Set<MethodId>>
            scan(
            final List<ChangePoint>
                    cps,
            final BuildResult build) {
        final Map<ChangePoint,
                Set<MethodId>> result =
                new LinkedHashMap<>();
        for (final ChangePoint cp
                : cps) {
            if (!isScannable(
                    cp.getKind())) {
                continue;
            }
            result.put(cp,
                    new HashSet<>());
        }
        if (result.isEmpty()) {
            return result;
        }
        final Map<String,
                List<ChangePoint>>
                byOwner = groupByOwner(
                new ArrayList<>(
                        result.keySet()));
        for (final ModuleBuildOutput
                out
                : build.getOutputs()) {
            final String mod =
                    out.getModulePath()
                            .toString();
            scanDirectory(
                    out.getClassesDir(),
                    mod, byOwner,
                    result);
        }
        return result;
    }

    /**
     * Returns true if the change
     * kind requires bytecode
     * scanning.
     *
     * @param kind change point kind
     * @return true if scannable
     */
    static boolean isScannable(
            final ChangePointKind
                    kind) {
        return kind
                != ChangePointKind
                .CLASS_ADDED
                && kind
                != ChangePointKind
                .METHOD_ADDED
                && kind
                != ChangePointKind
                .FIELD_ADDED;
    }

    /**
     * Groups change points by
     * owner class name.
     *
     * @param cps change points
     * @return map of owner to cps
     */
    private Map<String,
            List<ChangePoint>>
            groupByOwner(
            final List<ChangePoint>
                    cps) {
        final Map<String,
                List<ChangePoint>>
                map = new HashMap<>();
        for (final ChangePoint cp
                : cps) {
            map.computeIfAbsent(
                            cp.getOwner(),
                            k -> new ArrayList<>())
                    .add(cp);
        }
        return map;
    }

    /**
     * Scans a classes directory
     * for references.
     *
     * @param dir     classes dir
     * @param module  module name
     * @param byOwner cps by owner
     * @param result  output map
     */
    private void scanDirectory(
            final Path dir,
            final String module,
            final Map<String,
                    List<ChangePoint>>
                    byOwner,
            final Map<ChangePoint,
                    Set<MethodId>>
                    result) {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try {
            Files.walkFileTree(dir,
                    new ScannerVisitor(
                            dir, module,
                            byOwner, result));
        } catch (IOException e) {
            throw new ImpactException(
                    "Failed to scan: "
                            + dir, e);
        }
    }

    /**
     * File visitor that scans
     * each class file.
     */
    private static final class
            ScannerVisitor
            extends SimpleFileVisitor<Path> {

        /** Classes root directory. */
        private final Path root;

        /** Module name. */
        private final String module;

        /** Change points by owner. */
        private final Map<String,
                List<ChangePoint>>
                byOwner;

        /** Result map. */
        private final Map<ChangePoint,
                Set<MethodId>> result;

        /**
         * Creates a scanner
         * visitor.
         *
         * @param rootDir root dir
         * @param modName module name
         * @param ownerMap cps by owner
         * @param resMap   result map
         */
        ScannerVisitor(
                final Path rootDir,
                final String modName,
                final Map<String,
                        List<ChangePoint>>
                        ownerMap,
                final Map<ChangePoint,
                        Set<MethodId>>
                        resMap) {
            this.root = rootDir;
            this.module = modName;
            this.byOwner = ownerMap;
            this.result = resMap;
        }

        @Override
        public FileVisitResult
                visitFile(
                final Path file,
                final BasicFileAttributes
                        attrs) {
            if (!file.toString()
                    .endsWith(CLASS_SUFFIX)) {
                return FileVisitResult
                        .CONTINUE;
            }
            scanClassFile(file);
            return FileVisitResult
                    .CONTINUE;
        }

        /**
         * Scans a single class
         * file for references.
         *
         * @param file class file
         */
        private void scanClassFile(
                final Path file) {
            try (InputStream is =
                    Files.newInputStream(
                            file)) {
                final ClassReader cr =
                        new ClassReader(
                                is);
                final RefClassVisitor cv =
                        new RefClassVisitor(
                                byOwner,
                                result,
                                module);
                cr.accept(cv,
                        ClassReader
                                .SKIP_FRAMES);
            } catch (IOException e) {
                // skip unreadable
            }
        }
    }

    /**
     * ClassVisitor that delegates
     * to method and field visitors
     * for reference matching.
     */
    private static final class
            RefClassVisitor
            extends ClassVisitor {

        /** Change points by owner. */
        private final Map<String,
                List<ChangePoint>>
                byOwner;

        /** Result map. */
        private final Map<ChangePoint,
                Set<MethodId>> result;

        /** Module name. */
        private final String module;

        /** Current class name. */
        private String className;

        /**
         * Creates a ref class
         * visitor.
         *
         * @param ownerMap cps by owner
         * @param resMap   result map
         * @param modName  module name
         */
        RefClassVisitor(
                final Map<String,
                        List<ChangePoint>>
                        ownerMap,
                final Map<ChangePoint,
                        Set<MethodId>>
                        resMap,
                final String modName) {
            super(Opcodes.ASM9);
            this.byOwner = ownerMap;
            this.result = resMap;
            this.module = modName;
        }

        @Override
        public void visit(
                final int version,
                final int access,
                final String name,
                final String signature,
                final String superName,
                final String[] interfaces) {
            this.className = name;
            super.visit(version,
                    access, name,
                    signature, superName,
                    interfaces);
        }

        @Override
        public MethodVisitor
                visitMethod(
                final int access,
                final String name,
                final String desc,
                final String signature,
                final String[] exc) {
            return new RefMethodVisitor(
                    byOwner, result,
                    module, className,
                    name, desc);
        }
    }

    /**
     * MethodVisitor that checks
     * each instruction against
     * change points.
     */
    private static final class
            RefMethodVisitor
            extends MethodVisitor {

        /** Change points by owner. */
        private final Map<String,
                List<ChangePoint>>
                byOwner;

        /** Result map. */
        private final Map<ChangePoint,
                Set<MethodId>> result;

        /** Module name. */
        private final String module;

        /** Enclosing class name. */
        private final String owner;

        /** Method name. */
        private final String name;

        /** Method descriptor. */
        private final String desc;

        /**
         * Creates a ref method
         * visitor.
         *
         * @param ownerMap cps by owner
         * @param resMap   result map
         * @param modName  module name
         * @param clsOwner class name
         * @param mName    method name
         * @param mDesc    method desc
         */
        RefMethodVisitor(
                final Map<String,
                        List<ChangePoint>>
                        ownerMap,
                final Map<ChangePoint,
                        Set<MethodId>>
                        resMap,
                final String modName,
                final String clsOwner,
                final String mName,
                final String mDesc) {
            super(Opcodes.ASM9);
            this.byOwner = ownerMap;
            this.result = resMap;
            this.module = modName;
            this.owner = clsOwner;
            this.name = mName;
            this.desc = mDesc;
        }

        @Override
        public void visitMethodInsn(
                final int opcode,
                final String instrOwner,
                final String instrName,
                final String instrDesc,
                final boolean isIface) {
            matchMethodRef(
                    instrOwner,
                    instrName,
                    instrDesc);
        }

        @Override
        public void visitFieldInsn(
                final int opcode,
                final String instrOwner,
                final String instrName,
                final String instrDesc) {
            matchFieldRef(
                    instrOwner,
                    instrName,
                    instrDesc);
            matchClassRef(instrOwner);
        }

        @Override
        public void visitTypeInsn(
                final int opcode,
                final String typeDesc) {
            matchClassRef(typeDesc);
        }

        /**
         * Matches a method
         * invocation against
         * change points.
         *
         * @param instrOwner owner
         * @param instrName  name
         * @param instrDesc  desc
         */
        private void matchMethodRef(
                final String instrOwner,
                final String instrName,
                final String instrDesc) {
            matchClassRef(instrOwner);
            final List<ChangePoint>
                    cps = byOwner.get(
                    instrOwner);
            if (cps == null) {
                return;
            }
            for (final ChangePoint cp
                    : cps) {
                if (isMethodMatch(
                        cp, instrOwner,
                        instrName,
                        instrDesc)) {
                    addSeed(cp);
                }
            }
        }

        /**
         * Matches a field access
         * against change points.
         *
         * @param instrOwner owner
         * @param instrName  name
         * @param instrDesc  desc
         */
        private void matchFieldRef(
                final String instrOwner,
                final String instrName,
                final String instrDesc) {
            final List<ChangePoint>
                    cps = byOwner.get(
                    instrOwner);
            if (cps == null) {
                return;
            }
            for (final ChangePoint cp
                    : cps) {
                if (isFieldMatch(
                        cp, instrOwner,
                        instrName,
                        instrDesc)) {
                    addSeed(cp);
                }
            }
        }

        /**
         * Matches a class
         * reference for
         * CLASS_REMOVED.
         *
         * @param refOwner referenced
         *                 class
         */
        private void matchClassRef(
                final String refOwner) {
            final List<ChangePoint>
                    cps = byOwner.get(
                    refOwner);
            if (cps == null) {
                return;
            }
            for (final ChangePoint cp
                    : cps) {
                if (cp.getKind()
                        == ChangePointKind
                        .CLASS_REMOVED) {
                    addSeed(cp);
                }
            }
        }

        /**
         * Checks if a change
         * point matches a method
         * invocation.
         *
         * @param cp   change point
         * @param own  instr owner
         * @param nam  instr name
         * @param dsc  instr desc
         * @return true if match
         */
        private boolean isMethodMatch(
                final ChangePoint cp,
                final String own,
                final String nam,
                final String dsc) {
            final ChangePointKind k =
                    cp.getKind();
            if (k != ChangePointKind
                    .METHOD_BODY_CHANGED
                    && k != ChangePointKind
                    .METHOD_REMOVED
                    && k != ChangePointKind
                    .METHOD_DESCRIPTOR_CHANGED) {
                return false;
            }
            return cp.getOwner()
                            .equals(own)
                    && nam.equals(
                            cp.getName())
                    && dsc.equals(
                            cp.getDescriptor());
        }

        /**
         * Checks if a change
         * point matches a field
         * access.
         *
         * @param cp   change point
         * @param own  instr owner
         * @param nam  instr name
         * @param dsc  instr desc
         * @return true if match
         */
        private boolean isFieldMatch(
                final ChangePoint cp,
                final String own,
                final String nam,
                final String dsc) {
            final ChangePointKind k =
                    cp.getKind();
            if (k != ChangePointKind
                    .FIELD_REMOVED
                    && k != ChangePointKind
                    .FIELD_DESCRIPTOR_CHANGED) {
                return false;
            }
            return cp.getOwner()
                            .equals(own)
                    && nam.equals(
                            cp.getName())
                    && dsc.equals(
                            cp.getDescriptor());
        }

        /**
         * Adds the current method
         * as a seed for the given
         * change point.
         *
         * @param cp change point
         */
        private void addSeed(
                final ChangePoint cp) {
            final MethodId seed =
                    new MethodId(
                            owner, name,
                            desc, module,
                            owner
                                    + CLASS_SUFFIX);
            result.computeIfAbsent(cp,
                            k -> new HashSet<>())
                    .add(seed);
        }
    }
}
