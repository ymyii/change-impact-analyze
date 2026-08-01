package io.github.dependencyanalysis.impact;

import com.ibm.wala.ipa.callgraph.CGNode;

import io.github.dependencyanalysis.callgraph.ModuleCallGraphSession;
import io.github.dependencyanalysis.dependency.ResolvedArtifact;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/** Indexes invokedynamic bootstrap handles owned by reachable methods. */
final class InvokeDynamicEvidenceIndex {

    /** ASM API. */
    private static final int API = Opcodes.ASM9;

    /** Indexed evidence. */
    private final List<BootstrapEvidence> evidence;

    private InvokeDynamicEvidenceIndex(
            final List<BootstrapEvidence> values) {
        evidence = List.copyOf(values);
    }

    /**
     * Builds an index for one module target scope.
     *
     * @param unit module input
     * @param session live WALA graph
     * @return immutable evidence index
     */
    static InvokeDynamicEvidenceIndex build(
            final ModuleAnalysisUnit unit,
            final ModuleCallGraphSession session) {
        final Map<String, List<CGNode>> reachable = reachable(session);
        final Set<BootstrapEvidence> result = new LinkedHashSet<>();
        try {
            scanDirectory(unit.getProjectClasses(), reachable, result);
            for (Path path : unit.getReactorDependencyClasses()) {
                scanDirectory(path, reachable, result);
            }
            for (ResolvedArtifact artifact : unit.getTargetArtifacts()) {
                if ("jar".equals(artifact.getArtifact().getType())) {
                    scanJar(artifact.getPath(), reachable, result);
                }
            }
        } catch (IOException exception) {
            throw new ImpactException(
                    "Unable to scan invokedynamic evidence", exception);
        }
        final List<BootstrapEvidence> sorted = new ArrayList<>(result);
        sorted.sort(Comparator
                .comparing(BootstrapEvidence::targetOwner)
                .thenComparing(BootstrapEvidence::targetName)
                .thenComparing(BootstrapEvidence::targetDescriptor)
                .thenComparing(value -> value.caller()
                        .getMethod().getReference().toString())
                .thenComparing(BootstrapEvidence::detail)
                .thenComparingInt(value -> value.caller()
                        .getGraphNodeId()));
        return new InvokeDynamicEvidenceIndex(sorted);
    }

    /**
     * Finds matching old-side method handles.
     *
     * @param owner internal owner
     * @param name method name
     * @param descriptor JVM descriptor
     * @return stable evidence list
     */
    List<BootstrapEvidence> find(
            final String owner,
            final String name,
            final String descriptor) {
        return evidence.stream()
                .filter(value -> owner.equals(value.targetOwner()))
                .filter(value -> name.equals(value.targetName()))
                .filter(value -> descriptor.equals(
                        value.targetDescriptor()))
                .toList();
    }

    private static Map<String, List<CGNode>> reachable(
            final ModuleCallGraphSession session) {
        final Map<String, List<CGNode>> result = new LinkedHashMap<>();
        for (CGNode node : session.getGraph()) {
            final String owner = node.getMethod().getDeclaringClass()
                    .getName().toString();
            result.computeIfAbsent(
                    (owner.startsWith("L") ? owner.substring(1) : owner)
                            + "#" + node.getMethod().getName()
                            + "#" + node.getMethod().getDescriptor(),
                    ignored -> new ArrayList<>()).add(node);
        }
        return result;
    }

    private static void scanDirectory(
            final Path directory,
            final Map<String, List<CGNode>> reachable,
            final Set<BootstrapEvidence> result) throws IOException {
        final List<Path> files;
        try (Stream<Path> stream = Files.walk(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".class"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
        for (Path file : files) {
            scanClass(Files.readAllBytes(file), reachable, result);
        }
    }

    private static void scanJar(
            final Path path,
            final Map<String, List<CGNode>> reachable,
            final Set<BootstrapEvidence> result) throws IOException {
        try (JarFile jar = new JarFile(path.toFile(), false)) {
            final List<JarEntry> entries = jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .filter(entry -> !entry.getName().startsWith(
                            "META-INF/versions/"))
                    .sorted(Comparator.comparing(JarEntry::getName))
                    .toList();
            for (JarEntry entry : entries) {
                try (InputStream input = jar.getInputStream(entry)) {
                    scanClass(input.readAllBytes(), reachable, result);
                }
            }
        }
    }

    private static void scanClass(
            final byte[] bytes,
            final Map<String, List<CGNode>> reachable,
            final Set<BootstrapEvidence> result) {
        new ClassReader(bytes).accept(new ClassVisitor(API) {

            /** Current class internal name. */
            private String owner;

            @Override
            public void visit(
                    final int version,
                    final int access,
                    final String name,
                    final String signature,
                    final String superName,
                    final String[] interfaces) {
                owner = name;
            }

            @Override
            public MethodVisitor visitMethod(
                    final int access,
                    final String name,
                    final String descriptor,
                    final String signature,
                    final String[] exceptions) {
                final String callerKey = owner + "#" + name
                        + "#" + descriptor;
                final List<CGNode> callers = reachable.get(callerKey);
                if (callers == null || callers.isEmpty()) {
                    return null;
                }
                return new MethodVisitor(API) {
                    @Override
                    public void visitInvokeDynamicInsn(
                            final String dynamicName,
                            final String dynamicDescriptor,
                            final Handle bootstrap,
                            final Object... arguments) {
                        addHandle(bootstrap, dynamicName,
                                dynamicDescriptor, callers, result);
                        for (Object argument : arguments) {
                            addConstant(argument, dynamicName,
                                    dynamicDescriptor, callers, result);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
    }

    private static void addConstant(
            final Object value,
            final String dynamicName,
            final String dynamicDescriptor,
            final List<CGNode> callers,
            final Set<BootstrapEvidence> result) {
        if (value instanceof Handle) {
            addHandle((Handle) value, dynamicName,
                    dynamicDescriptor, callers, result);
        } else if (value instanceof ConstantDynamic) {
            final ConstantDynamic dynamic = (ConstantDynamic) value;
            addHandle(dynamic.getBootstrapMethod(), dynamicName,
                    dynamicDescriptor, callers, result);
            for (int index = 0;
                    index < dynamic.getBootstrapMethodArgumentCount();
                    index++) {
                addConstant(dynamic.getBootstrapMethodArgument(index),
                        dynamicName, dynamicDescriptor, callers, result);
            }
        }
    }

    private static void addHandle(
            final Handle handle,
            final String dynamicName,
            final String dynamicDescriptor,
            final List<CGNode> callers,
            final Set<BootstrapEvidence> result) {
        if (!handle.getDesc().startsWith("(")) {
            return;
        }
        final String detail = "invokedynamic=" + dynamicName
                + dynamicDescriptor + "; handle=" + handle.getOwner()
                + "#" + handle.getName() + handle.getDesc();
        for (CGNode caller : callers) {
            result.add(new BootstrapEvidence(handle.getOwner(),
                    handle.getName(), handle.getDesc(), caller, detail));
        }
    }
}

/**
 * Reachable bootstrap method-handle evidence.
 *
 * @param targetOwner handle owner
 * @param targetName handle name
 * @param targetDescriptor handle descriptor
 * @param caller reachable caller Context
 * @param detail stable evidence
 */
record BootstrapEvidence(
        String targetOwner,
        String targetName,
        String targetDescriptor,
        CGNode caller,
        String detail) {
}
