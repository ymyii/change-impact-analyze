package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.DecompiledMethod;
import io.github.dependencyanalysis.bytecode.MethodBodyDecompiler;
import io.github.dependencyanalysis.callgraph.topology.CallGraphMethodIdentity;
import io.github.dependencyanalysis.callgraph.scope.ClassOwnership;
import io.github.dependencyanalysis.callgraph.scope.ClassSource;
import io.github.dependencyanalysis.callgraph.model.CodeOrigin;
import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphSession;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.jar.IJarRepository;
import io.github.dependencyanalysis.jar.JarLease;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Resolves and decompiles exact project, dependency, or JDK Methods. */
final class CallGraphMethodSourceBuilder {

    /** SHA-256 algorithm name. */
    private static final String SHA_256 = "SHA-256";

    /** Target JDK runtime. */
    private final JavaRuntimeDescriptor javaRuntime;

    /** Command-scoped repository. */
    private final IJarRepository repository;

    /** Vineflower wrapper. */
    private final MethodBodyDecompiler decompiler;

    /**
     * Creates a source builder.
     *
     * @param diagnostics diagnostic collector
     * @param runtime target JDK runtime
     * @param jarRepository command repository
     */
    CallGraphMethodSourceBuilder(
            final DiagnosticLog diagnostics,
            final JavaRuntimeDescriptor runtime,
            final IJarRepository jarRepository) {
        javaRuntime = Objects.requireNonNull(runtime, "runtime");
        repository = Objects.requireNonNull(jarRepository, "repository");
        final List<Path> libraries = new ArrayList<>(
                runtime.getBootClassPath());
        libraries.addAll(runtime.getExtensionClassPath());
        decompiler = new MethodBodyDecompiler(diagnostics, libraries);
    }

    /**
     * Builds exact source evidence for one Method.
     *
     * @param session owning Call Graph session
     * @param method context-independent Method identity
     * @param walaSynthetic whether WALA generated or summarized the Method
     * @return source evidence
     */
    CallGraphMethodSource build(
            final ModuleCallGraphSession session,
            final CallGraphMethodIdentity method,
            final boolean walaSynthetic) {
        if (walaSynthetic || method.origin() == CodeOrigin.SYNTHETIC) {
            return unavailable(
                    "WALA synthetic/summary Method has no bytecode source",
                    "");
        }
        final String owner = method.owner().replace('.', '/');
        final ClassOwnership ownership = session.getOwnership()
                .ownershipOf(owner);
        try {
            if (ownership != null) {
                return fromSource(ownership.getSource(), owner, method);
            }
            if (method.origin() == CodeOrigin.JDK) {
                final Path jdkSource = findJdkSource(owner);
                if (jdkSource == null) {
                    return unavailable("JDK class entry was not found", "");
                }
                return build(jdkSource, owner, method, jdkSource.toString());
            }
            return unavailable("Class ownership source was not found", "");
        } catch (IOException | RuntimeException exception) {
            return unavailable(summarize(exception), "");
        }
    }

    private CallGraphMethodSource fromSource(
            final ClassSource source,
            final String owner,
            final CallGraphMethodIdentity method) throws IOException {
        if (source.path().isPresent()) {
            final Path path = source.path().orElseThrow();
            return build(path, owner, method, source.stableKey());
        }
        final ArtifactCoord artifact = source.artifact().orElseThrow();
        try (JarLease lease = repository.open(artifact)) {
            return build(Path.of(lease.jarFile().getName()), owner, method,
                    source.stableKey());
        }
    }

    private CallGraphMethodSource build(
            final Path classpathEntry,
            final String owner,
            final CallGraphMethodIdentity method,
            final String sourceLabel) throws IOException {
        final DecompiledMethod decompiled = decompiler.decompileMethod(
                classpathEntry, owner, method.name(), method.descriptor(),
                "benchmark", sourceLabel);
        if (decompiled.isAvailable()) {
            return available(CallGraphMethodSourceStatus.DECOMPILED,
                    decompiled.getSource(), "", sourceLabel);
        }
        try {
            final String asm = asmMethod(
                    classpathEntry, owner, method.name(),
                    method.descriptor());
            return available(CallGraphMethodSourceStatus.ASM_FALLBACK,
                    asm, decompiled.getFailureReason(), sourceLabel);
        } catch (IOException | RuntimeException exception) {
            return unavailable(decompiled.getFailureReason() + "; ASM: "
                    + summarize(exception), sourceLabel);
        }
    }

    private Path findJdkSource(final String owner) throws IOException {
        final List<Path> candidates = new ArrayList<>(
                javaRuntime.getBootClassPath());
        candidates.addAll(javaRuntime.getExtensionClassPath());
        for (Path candidate : candidates) {
            if (contains(candidate, owner)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean contains(
            final Path classpathEntry,
            final String owner) throws IOException {
        final String entry = owner + ".class";
        if (Files.isDirectory(classpathEntry)) {
            return Files.isRegularFile(classpathEntry.resolve(entry));
        }
        try (ZipFile zip = new ZipFile(classpathEntry.toFile())) {
            return zip.getEntry(entry) != null;
        }
    }

    private String asmMethod(
            final Path classpathEntry,
            final String owner,
            final String name,
            final String descriptor) throws IOException {
        final ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(readClass(classpathEntry, owner)).accept(node, 0);
        for (MethodNode method : node.methods) {
            if (name.equals(method.name) && descriptor.equals(method.desc)) {
                final Textifier printer = new Textifier();
                method.accept(new TraceMethodVisitor(printer));
                final StringWriter output = new StringWriter();
                printer.print(new PrintWriter(output));
                return output.toString();
            }
        }
        throw new IOException("Method not found: " + name + descriptor);
    }

    private byte[] readClass(
            final Path classpathEntry,
            final String owner) throws IOException {
        final String entryName = owner + ".class";
        if (Files.isDirectory(classpathEntry)) {
            final Path classFile = classpathEntry.resolve(entryName);
            if (!Files.isRegularFile(classFile)) {
                throw new IOException("Class entry not found: " + entryName);
            }
            return Files.readAllBytes(classFile);
        }
        try (ZipFile zip = new ZipFile(classpathEntry.toFile())) {
            final ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                throw new IOException("Class entry not found: " + entryName);
            }
            try (InputStream input = zip.getInputStream(entry)) {
                return input.readAllBytes();
            }
        }
    }

    private CallGraphMethodSource available(
            final CallGraphMethodSourceStatus status,
            final String source,
            final String reason,
            final String sourceLabel) {
        return new CallGraphMethodSource(status, source, reason,
                digest(source), sourceLabel);
    }

    private CallGraphMethodSource unavailable(
            final String reason,
            final String sourceLabel) {
        return new CallGraphMethodSource(
                CallGraphMethodSourceStatus.UNAVAILABLE,
                "", reason, "", sourceLabel);
    }

    private String digest(final String source) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(
                    SHA_256).digest(source.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String summarize(final Throwable failure) {
        final String message = failure.getMessage();
        return failure.getClass().getSimpleName()
                + (message == null || message.isBlank()
                ? "" : ": " + message);
    }
}
