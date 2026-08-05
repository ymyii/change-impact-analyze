package io.github.dependencyanalysis.bytecode;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Indexes all class files in a jar
 * into a map of internal name to
 * {@link ClassInfo}. Package-private.
 */
final class JarClassIndexer {

    /** Module info class name. */
    private static final String MODULE =
            "module-info.class";

    /** Class file suffix. */
    private static final String SUFFIX =
            ".class";

    /** Private constructor. */
    private JarClassIndexer() {
    }

    /**
     * Indexes classes through an existing repository-owned JAR handle.
     *
     * @param jar open JAR
     * @return indexed classes
     * @throws BytecodeDiffException on unreadable or invalid bytecode
     */
    static Map<String, ClassInfo> index(final JarFile jar)
            throws BytecodeDiffException {
        Objects.requireNonNull(jar, "jar");
        final Path diagnosticPath = Path.of(jar.getName());
        final Map<String, ClassInfo> map = new HashMap<>();
        try {
            final List<JarEntry> entries = jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().endsWith(SUFFIX))
                    .filter(entry -> !MODULE.equals(entry.getName()))
                    .toList();
            for (JarEntry entry : entries) {
                try (InputStream input = jar.getInputStream(entry)) {
                    final ClassInfo info = readClass(input.readAllBytes(),
                            diagnosticPath, entry.getName());
                    map.put(info.getInternalName(), info);
                }
            }
        } catch (IOException exception) {
            throw new BytecodeDiffException(diagnosticPath, null,
                    "Failed to read jar", exception);
        }
        return map;
    }

    /**
     * Reads class info from bytes.
     *
     * @param bytes class file bytes
     * @param jar   jar path for errors
     * @param name  entry name
     * @return class info
     * @throws BytecodeDiffException
     *  if parsing fails
     */
    private static ClassInfo readClass(
            final byte[] bytes,
            final Path jar,
            final String name)
            throws BytecodeDiffException {
        final ClassReader reader;
        try {
            reader = new ClassReader(bytes);
        } catch (RuntimeException e) {
            throw new BytecodeDiffException(
                    jar, name,
                    "Failed to parse class",
                    e);
        }
        final List<RawMethod> rawMethods =
                new ArrayList<>();
        final List<FieldInfo> fields =
                new ArrayList<>();
        final String[] className =
                new String[1];
        final ClassVisitor cv =
                new ClassVisitor(
                        Opcodes.ASM9) {

                    @Override
                    public void visit(
                            final int ver,
                            final int acc,
                            final String n,
                            final String sig,
                            final String sup,
                            final String[] ifaces) {
                        className[0] = n;
                    }

                    @Override
                    public MethodVisitor
                            visitMethod(
                            final int acc,
                            final String n,
                            final String desc,
                            final String sig,
                            final String[] exc) {
                        rawMethods.add(
                                new RawMethod(
                                        acc, n,
                                        desc));
                        return null;
                    }

                    @Override
                    public FieldVisitor
                            visitField(
                            final int acc,
                            final String n,
                            final String desc,
                            final String sig,
                            final Object val) {
                        fields.add(
                                new FieldInfo(
                                        n, desc));
                        return null;
                    }
                };
        try {
            reader.accept(
                    cv, ClassReader.SKIP_DEBUG);
        } catch (RuntimeException e) {
            throw new BytecodeDiffException(
                    jar, name,
                    "Failed to visit class",
                    e);
        }
        final String cn = className[0];
        if (cn == null) {
            throw new BytecodeDiffException(
                    jar, name,
                    "Class name not found");
        }
        final List<MethodInfo> methods =
                new ArrayList<>();
        for (final RawMethod raw
                : rawMethods) {
            final String hash =
                    computeHash(
                            raw.getAccess(),
                            raw.getName(),
                            raw.getDesc(),
                            bytes,
                            jar, name);
            methods.add(
                    new MethodInfo(
                            raw.getName(),
                            raw.getDesc(),
                            hash));
        }
        return new ClassInfo(
                cn, methods, fields);
    }

    /**
     * Computes a body hash for a method.
     *
     * @param acc   access flags
     * @param mname method name
     * @param desc  descriptor
     * @param bytes class bytes
     * @param jar   jar path
     * @param cls   class entry name
     * @return hex hash or null
     * @throws BytecodeDiffException
     *  if hashing fails
     */
    private static String computeHash(
            final int acc,
            final String mname,
            final String desc,
            final byte[] bytes,
            final Path jar,
            final String cls)
            throws BytecodeDiffException {
        final boolean isAbstract =
                (acc & Opcodes.ACC_ABSTRACT)
                        != 0;
        final boolean isNative =
                (acc & Opcodes.ACC_NATIVE)
                        != 0;
        if (isAbstract || isNative) {
            return null;
        }
        try {
            final StableHashMethodVisitor mv =
                    new StableHashMethodVisitor();
            final ClassReader rdr =
                    new ClassReader(bytes);
            final MethodExtractor ext =
                    new MethodExtractor(
                            mname, desc, mv);
            rdr.accept(ext,
                    ClassReader.SKIP_DEBUG);
            mv.visitEnd();
            return mv.getHash();
        } catch (NoSuchAlgorithmException e) {
            throw new BytecodeDiffException(
                    jar, cls,
                    "SHA-256 unavailable", e);
        } catch (RuntimeException e) {
            throw new BytecodeDiffException(
                    jar, cls,
                    "Failed to hash method",
                    e);
        }
    }

    /**
     * Raw method data collected
     * during first pass.
     */
    private static final class RawMethod {

        /** Access flags. */
        private final int access;

        /** Method name. */
        private final String name;

        /** Method descriptor. */
        private final String desc;

        /**
         * Creates a raw method.
         *
         * @param acc  access flags
         * @param nam  method name
         * @param dsc  descriptor
         */
        RawMethod(
                final int acc,
                final String nam,
                final String dsc) {
            this.access = acc;
            this.name = nam;
            this.desc = dsc;
        }

        /**
         * Returns the access flags.
         *
         * @return access flags
         */
        int getAccess() {
            return access;
        }

        /**
         * Returns the method name.
         *
         * @return method name
         */
        String getName() {
            return name;
        }

        /**
         * Returns the descriptor.
         *
         * @return method descriptor
         */
        String getDesc() {
            return desc;
        }
    }

    /**
     * Extracts a single method and
     * delegates to a target visitor.
     */
    private static final class
            MethodExtractor
            extends ClassVisitor {

        /** Target method name. */
        private final String targetName;

        /** Target method descriptor. */
        private final String targetDesc;

        /** Hash visitor. */
        private final MethodVisitor hashMv;

        /**
         * Creates a method extractor.
         *
         * @param name target name
         * @param desc target descriptor
         * @param mv   hash visitor
         */
        MethodExtractor(
                final String name,
                final String desc,
                final MethodVisitor mv) {
            super(Opcodes.ASM9);
            this.targetName = name;
            this.targetDesc = desc;
            this.hashMv = mv;
        }

        @Override
        public MethodVisitor visitMethod(
                final int acc,
                final String name,
                final String desc,
                final String sig,
                final String[] exc) {
            if (targetName.equals(name)
                    && targetDesc.equals(
                            desc)) {
                return hashMv;
            }
            return null;
        }
    }
}
