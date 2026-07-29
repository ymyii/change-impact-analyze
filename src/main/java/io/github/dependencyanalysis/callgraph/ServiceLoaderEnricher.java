package io.github.dependencyanalysis.callgraph;

import io.github.dependencyanalysis.build.ModuleBuildOutput;
import io.github.dependencyanalysis.diagnostic.DiagnosticCollector;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Enriches call graph with
 * META-INF/services edges.
 */
final class ServiceLoaderEnricher {

    /** Stage name for diagnostics. */
    private static final String STAGE =
            "call-graph";

    /** Synthetic method name. */
    private static final String SVC_LOADER =
            "<service-loader>";

    /** Init method name. */
    private static final String INIT =
            "<init>";

    /** Void descriptor. */
    private static final String VOID_DESC =
            "()V";

    /** Diagnostic collector. */
    private final DiagnosticCollector diag;

    /** Current method set. */
    private Set<MethodId> methods;

    /** Current edge list. */
    private List<CallEdge> edges;

    /** Current override map. */
    private Map<MethodId, Set<MethodId>> ovrMap;

    /**
     * Creates a new enricher.
     *
     * @param collector diagnostic collector
     */
    ServiceLoaderEnricher(
            final DiagnosticCollector
                    collector) {
        this.diag = collector;
    }

    /**
     * Enriches the call graph with
     * service loader edges.
     *
     * @param outputs module outputs
     * @param mts     method set
     * @param edgs    edge list
     * @param ovr     override map
     */
    void enrich(
            final List<ModuleBuildOutput> outputs,
            final Set<MethodId> mts,
            final List<CallEdge> edgs,
            final Map<MethodId, Set<MethodId>> ovr) {
        this.methods = mts;
        this.edges = edgs;
        this.ovrMap = ovr;
        for (final ModuleBuildOutput out
                : outputs) {
            processModule(out);
        }
    }

    /**
     * Processes a single module's
     * META-INF/services directory.
     *
     * @param out module output
     */
    private void processModule(
            final ModuleBuildOutput out) {
        final Path svcDir =
                out.getClassesDir()
                        .resolve("META-INF")
                        .resolve("services");
        if (!Files.isDirectory(svcDir)) {
            return;
        }
        try (DirectoryStream<Path> stream =
                Files.newDirectoryStream(
                        svcDir)) {
            for (final Path file : stream) {
                if (!Files.isRegularFile(
                        file)) {
                    continue;
                }
                processServiceFile(
                        file,
                        out.getClassesDir());
            }
        } catch (IOException e) {
            diag.warn(STAGE,
                    "Failed to scan"
                            + " META-INF/services: "
                            + e.getMessage());
        }
    }

    /**
     * Processes a single service file.
     *
     * @param file       service file
     * @param classesDir classes dir
     */
    private void processServiceFile(
            final Path file,
            final Path classesDir) {
        final String ifaceName =
                file.getFileName()
                        .toString();
        final String ifaceInternal =
                ifaceName.replace('.', '/');
        try {
            final List<String> lines =
                    Files.readAllLines(file);
            for (final String line : lines) {
                final String trimmed =
                        line.trim();
                if (trimmed.isEmpty()
                        || trimmed.startsWith(
                                "#")) {
                    continue;
                }
                processProvider(
                        trimmed, ifaceName,
                        ifaceInternal,
                        classesDir);
            }
        } catch (IOException e) {
            diag.warn(STAGE,
                    "Failed to read"
                            + " service file: "
                            + file + ": "
                            + e.getMessage());
        }
    }

    /**
     * Processes a single provider
     * entry.
     *
     * @param providerName  provider name
     * @param ifaceName     interface name
     * @param ifaceInternal interface internal
     * @param classesDir    classes dir
     */
    private void processProvider(
            final String providerName,
            final String ifaceName,
            final String ifaceInternal,
            final Path classesDir) {
        final String provInternal =
                providerName.replace('.', '/');
        final Path provFile =
                classesDir.resolve(
                        provInternal + ".class");
        if (!Files.exists(provFile)) {
            diag.info(STAGE,
                    "Provider not in scope: "
                            + providerName);
            return;
        }
        final MethodId caller =
                new MethodId(
                        ifaceInternal,
                        SVC_LOADER,
                        VOID_DESC,
                        "",
                        ifaceInternal + ".class");
        final MethodId callee =
                new MethodId(
                        provInternal,
                        INIT,
                        VOID_DESC,
                        "",
                        provInternal + ".class");
        methods.add(caller);
        methods.add(callee);
        final String evidence =
                "META-INF/services/"
                        + ifaceName;
        edges.add(new CallEdge(
                caller, callee,
                EdgeKind.SERVICE,
                evidence));
        addServiceOverrides(
                ifaceInternal,
                provInternal,
                classesDir);
        diag.info(STAGE,
                "ServiceLoader: "
                        + ifaceName
                        + " -> "
                        + providerName);
    }

    /**
     * Adds override entries for
     * service interface methods.
     *
     * @param ifaceInternal interface internal
     * @param provInternal  provider internal
     * @param classesDir    classes dir
     */
    private void addServiceOverrides(
            final String ifaceInternal,
            final String provInternal,
            final Path classesDir) {
        final Path ifaceFile =
                classesDir.resolve(
                        ifaceInternal + ".class");
        final Path provFile =
                classesDir.resolve(
                        provInternal + ".class");
        if (!Files.exists(ifaceFile)
                || !Files.exists(provFile)) {
            return;
        }
        try {
            final Set<String> ifaceSigs =
                    readMethodSigs(ifaceFile);
            final Set<String> provSigs =
                    readMethodSigs(provFile);
            for (final String sig : ifaceSigs) {
                if (!provSigs.contains(sig)) {
                    continue;
                }
                final int idx =
                        sig.indexOf('(');
                final String mName =
                        sig.substring(0, idx);
                final String mDesc =
                        sig.substring(idx);
                addOneOverride(
                        ifaceInternal,
                        provInternal,
                        mName, mDesc);
            }
        } catch (IOException e) {
            diag.warn(STAGE,
                    "Failed to read methods: "
                            + e.getMessage());
        }
    }

    /**
     * Adds a single override entry.
     *
     * @param ifaceInternal interface internal
     * @param provInternal  provider internal
     * @param mName         method name
     * @param mDesc         method descriptor
     */
    private void addOneOverride(
            final String ifaceInternal,
            final String provInternal,
            final String mName,
            final String mDesc) {
        final MethodId ifaceMid =
                new MethodId(
                        ifaceInternal,
                        mName, mDesc,
                        "",
                        ifaceInternal
                                + ".class");
        final MethodId provMid =
                new MethodId(
                        provInternal,
                        mName, mDesc,
                        "",
                        provInternal
                                + ".class");
        ovrMap.computeIfAbsent(
                        ifaceMid,
                        k -> new HashSet<>())
                .add(provMid);
    }

    /**
     * Reads method signatures from
     * a class file.
     *
     * @param classFile class file path
     * @return set of name+descriptor
     * @throws IOException on read error
     */
    private Set<String> readMethodSigs(
            final Path classFile)
            throws IOException {
        final Set<String> sigs =
                new HashSet<>();
        final byte[] bytes =
                Files.readAllBytes(
                        classFile);
        final ClassReader cr =
                new ClassReader(bytes);
        cr.accept(new ClassVisitor(
                Opcodes.ASM9) {
            @Override
            public MethodVisitor
                    visitMethod(
                    final int access,
                    final String name,
                    final String descriptor,
                    final String signature,
                    final String[] exceptions) {
                if (name != null
                        && descriptor != null) {
                    sigs.add(name
                            + descriptor);
                }
                return null;
            }
        }, ClassReader.SKIP_CODE
                | ClassReader.SKIP_DEBUG
                | ClassReader.SKIP_FRAMES);
        return sigs;
    }
}
