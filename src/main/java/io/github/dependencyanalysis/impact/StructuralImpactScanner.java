package io.github.dependencyanalysis.impact;

import com.ibm.wala.ipa.callgraph.CGNode;

import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.callgraph.CodeOrigin;
import io.github.dependencyanalysis.callgraph.ModuleCallGraphSession;
import io.github.dependencyanalysis.dependency.ResolvedArtifact;

import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;

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

/** Resolves class metadata references outside method-level WALA paths. */
final class StructuralImpactScanner {

    /** ASM API. */
    private static final int API = Opcodes.ASM9;

    /**
     * Extracts stable metadata evidence for selected classes.
     *
     * @param bytes class bytes
     * @param targetNames selected internal class names
     * @return target to evidence values
     */
    Map<String, Set<String>> metadataEvidence(
            final byte[] bytes, final Set<String> targetNames) {
        final Map<String, List<BoundChangePoint>> selected =
                new LinkedHashMap<>();
        targetNames.stream().sorted().forEach(name ->
                selected.put(name, List.of()));
        final MetadataCollector collector =
                new MetadataCollector(selected);
        new ClassReader(bytes).accept(collector,
                ClassReader.SKIP_CODE
                        | ClassReader.SKIP_DEBUG
                        | ClassReader.SKIP_FRAMES);
        final Map<String, Set<String>> result = new LinkedHashMap<>();
        for (MetadataReference reference : collector.references()) {
            result.computeIfAbsent(reference.target(),
                    ignored -> new LinkedHashSet<>())
                    .add(reference.evidence());
        }
        return result;
    }

    /**
     * Scans target scope metadata for removed classes.
     *
     * @param unit module input
     * @param session live module graph
     * @return classified structural references
     */
    StructuralScanResult scan(
            final ModuleAnalysisUnit unit,
            final ModuleCallGraphSession session) {
        final Map<String, List<BoundChangePoint>> targets = targets(unit);
        if (targets.isEmpty()) {
            return StructuralScanResult.empty();
        }
        final Set<String> reachable = reachableClasses(session);
        final List<StructuralImpact> impacts = new ArrayList<>();
        final Set<BoundChangePoint> unreachable = new LinkedHashSet<>();
        final Set<String> seen = new LinkedHashSet<>();
        try {
            scanDirectory(unit.getProjectClasses(), CodeOrigin.PROJECT,
                    targets, reachable, impacts, unreachable, seen);
            for (Path path : unit.getReactorDependencyClasses()) {
                scanDirectory(path, CodeOrigin.REACTOR_DEPENDENCY,
                        targets, reachable, impacts, unreachable, seen);
            }
            for (ResolvedArtifact artifact : unit.getTargetArtifacts()) {
                if ("jar".equals(artifact.getArtifact().getType())) {
                    scanJar(artifact.getPath(), CodeOrigin.DEPENDENCY,
                            targets, reachable, impacts, unreachable, seen);
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new ImpactException(
                    "Unable to scan structural metadata", exception);
        }
        impacts.sort(Comparator
                .comparing((StructuralImpact value) ->
                        value.getChangePoint().stableKey())
                .thenComparing(StructuralImpact::getReferencingClass)
                .thenComparing(StructuralImpact::getEvidence));
        return new StructuralScanResult(impacts, unreachable);
    }

    private Map<String, List<BoundChangePoint>> targets(
            final ModuleAnalysisUnit unit) {
        final Map<String, List<BoundChangePoint>> result =
                new LinkedHashMap<>();
        for (BoundChangePoint point : unit.getChangePoints()) {
            if (point.getChangePoint().getKind()
                    == ChangePointKind.CLASS_REMOVED) {
                result.computeIfAbsent(
                        point.getChangePoint().getOwner(),
                        ignored -> new ArrayList<>()).add(point);
            }
        }
        return result;
    }

    private Set<String> reachableClasses(
            final ModuleCallGraphSession session) {
        final Set<String> result = new LinkedHashSet<>();
        for (CGNode node : session.getGraph()) {
            final String value = node.getMethod().getDeclaringClass()
                    .getName().toString();
            result.add(value.startsWith("L")
                    ? value.substring(1) : value);
        }
        return result;
    }

    private void scanDirectory(
            final Path directory,
            final CodeOrigin origin,
            final Map<String, List<BoundChangePoint>> targets,
            final Set<String> reachable,
            final List<StructuralImpact> impacts,
            final Set<BoundChangePoint> unreachable,
            final Set<String> seen) throws IOException {
        final List<Path> files;
        try (Stream<Path> stream = Files.walk(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".class"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
        for (Path file : files) {
            scanClass(Files.readAllBytes(file), origin, targets,
                    reachable, impacts, unreachable, seen);
        }
    }

    private void scanJar(
            final Path jarPath,
            final CodeOrigin origin,
            final Map<String, List<BoundChangePoint>> targets,
            final Set<String> reachable,
            final List<StructuralImpact> impacts,
            final Set<BoundChangePoint> unreachable,
            final Set<String> seen) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile(), false)) {
            final List<JarEntry> entries = jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .filter(entry -> !entry.getName().startsWith(
                            "META-INF/versions/"))
                    .sorted(Comparator.comparing(JarEntry::getName))
                    .toList();
            for (JarEntry entry : entries) {
                try (InputStream input = jar.getInputStream(entry)) {
                    scanClass(input.readAllBytes(), origin, targets,
                            reachable, impacts, unreachable, seen);
                }
            }
        }
    }

    private void scanClass(
            final byte[] bytes,
            final CodeOrigin origin,
            final Map<String, List<BoundChangePoint>> targets,
            final Set<String> reachable,
            final List<StructuralImpact> impacts,
            final Set<BoundChangePoint> unreachable,
            final Set<String> seen) {
        final MetadataCollector collector = new MetadataCollector(targets);
        new ClassReader(bytes).accept(collector,
                ClassReader.SKIP_CODE
                        | ClassReader.SKIP_DEBUG
                        | ClassReader.SKIP_FRAMES);
        final boolean reportable = origin == CodeOrigin.PROJECT
                || reachable.contains(collector.owner());
        for (MetadataReference reference : collector.references()) {
            for (BoundChangePoint point
                    : targets.get(reference.target())) {
                if (!reportable) {
                    unreachable.add(point);
                    continue;
                }
                final String key = point.stableKey() + "|"
                        + collector.owner() + "|" + origin + "|"
                        + reference.evidence();
                if (seen.add(key)) {
                    impacts.add(new StructuralImpact(
                            point.getDependencyUpgradeKey().getModuleId(),
                            point, collector.owner(), origin,
                            reference.evidence()));
                }
            }
        }
    }

    /** Metadata collection visitor. */
    private static final class MetadataCollector extends ClassVisitor {

        /** Relevant changed types. */
        private final Map<String, List<BoundChangePoint>> targets;

        /** Matched metadata records. */
        private final Set<MetadataReference> references =
                new LinkedHashSet<>();

        /** Current class. */
        private String owner;

        /** @param changedTypes relevant removed classes */
        MetadataCollector(
                final Map<String, List<BoundChangePoint>> changedTypes) {
            super(API);
            targets = changedTypes;
        }

        @Override
        public void visit(
                final int version,
                final int access,
                final String name,
                final String signature,
                final String superName,
                final String[] interfaces) {
            owner = name;
            addInternal(superName, "SUPERCLASS");
            if (interfaces != null) {
                for (String value : interfaces) {
                    addInternal(value, "INTERFACE");
                }
            }
            addSignature(signature, "CLASS_SIGNATURE");
        }

        @Override
        public AnnotationVisitor visitAnnotation(
                final String descriptor, final boolean visible) {
            addDescriptor(descriptor, "CLASS_ANNOTATION");
            return annotationVisitor("CLASS_ANNOTATION_VALUE");
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(
                final int typeRef,
                final org.objectweb.asm.TypePath typePath,
                final String descriptor,
                final boolean visible) {
            addDescriptor(descriptor, "CLASS_TYPE_ANNOTATION");
            return annotationVisitor("CLASS_TYPE_ANNOTATION_VALUE");
        }

        @Override
        public FieldVisitor visitField(
                final int access,
                final String name,
                final String descriptor,
                final String signature,
                final Object value) {
            addDescriptor(descriptor, "FIELD_DESCRIPTOR:" + name);
            addSignature(signature, "FIELD_SIGNATURE:" + name);
            return new FieldVisitor(API) {
                @Override
                public AnnotationVisitor visitAnnotation(
                        final String desc, final boolean visible) {
                    addDescriptor(desc, "FIELD_ANNOTATION:" + name);
                    return annotationVisitor(
                            "FIELD_ANNOTATION_VALUE:" + name);
                }

                @Override
                public AnnotationVisitor visitTypeAnnotation(
                        final int typeRef,
                        final org.objectweb.asm.TypePath typePath,
                        final String desc,
                        final boolean visible) {
                    addDescriptor(desc, "FIELD_TYPE_ANNOTATION:" + name);
                    return annotationVisitor(
                            "FIELD_TYPE_ANNOTATION_VALUE:" + name);
                }
            };
        }

        @Override
        public MethodVisitor visitMethod(
                final int access,
                final String name,
                final String descriptor,
                final String signature,
                final String[] exceptions) {
            final String method = name + descriptor;
            addMethodDescriptor(descriptor,
                    "METHOD_DESCRIPTOR:" + method);
            addSignature(signature, "METHOD_SIGNATURE:" + method);
            if (exceptions != null) {
                for (String value : exceptions) {
                    addInternal(value, "THROWS:" + method);
                }
            }
            return new MethodVisitor(API) {
                @Override
                public AnnotationVisitor visitAnnotationDefault() {
                    return annotationVisitor(
                            "ANNOTATION_DEFAULT_VALUE:" + method);
                }

                @Override
                public AnnotationVisitor visitAnnotation(
                        final String desc, final boolean visible) {
                    addDescriptor(desc,
                            "METHOD_ANNOTATION:" + method);
                    return annotationVisitor(
                            "METHOD_ANNOTATION_VALUE:" + method);
                }

                @Override
                public AnnotationVisitor visitTypeAnnotation(
                        final int typeRef,
                        final org.objectweb.asm.TypePath typePath,
                        final String desc,
                        final boolean visible) {
                    addDescriptor(desc,
                            "METHOD_TYPE_ANNOTATION:" + method);
                    return annotationVisitor(
                            "METHOD_TYPE_ANNOTATION_VALUE:" + method);
                }

                @Override
                public AnnotationVisitor visitParameterAnnotation(
                        final int parameter,
                        final String desc,
                        final boolean visible) {
                    addDescriptor(desc, "PARAMETER_ANNOTATION:"
                            + method + ":" + parameter);
                    return annotationVisitor(
                            "PARAMETER_ANNOTATION_VALUE:"
                                    + method + ":" + parameter);
                }
            };
        }

        private AnnotationVisitor annotationVisitor(
                final String evidence) {
            return new AnnotationVisitor(API) {
                @Override
                public void visit(final String name, final Object value) {
                    if (value instanceof Type) {
                        addType((Type) value, evidence);
                    }
                }

                @Override
                public void visitEnum(
                        final String name,
                        final String descriptor,
                        final String value) {
                    addDescriptor(descriptor, evidence);
                }

                @Override
                public AnnotationVisitor visitAnnotation(
                        final String name, final String descriptor) {
                    addDescriptor(descriptor, evidence);
                    return annotationVisitor(evidence);
                }

                @Override
                public AnnotationVisitor visitArray(final String name) {
                    return annotationVisitor(evidence);
                }
            };
        }

        String owner() {
            return owner;
        }

        Set<MetadataReference> references() {
            return references;
        }

        private void addSignature(
                final String signature, final String evidence) {
            if (signature == null) {
                return;
            }
            final SignatureVisitor visitor = new SignatureVisitor(API) {

                /** Current possibly nested class name. */
                private String current;

                @Override
                public void visitClassType(final String name) {
                    current = name;
                    addInternal(name, evidence);
                }

                @Override
                public void visitInnerClassType(final String name) {
                    current = current == null ? name
                            : current + "$" + name;
                    addInternal(current, evidence);
                }
            };
            final SignatureReader reader = new SignatureReader(signature);
            if (signature.startsWith("L")
                    || signature.startsWith("T")
                    || signature.startsWith("[")) {
                reader.acceptType(visitor);
            } else {
                reader.accept(visitor);
            }
        }

        private void addMethodDescriptor(
                final String descriptor, final String evidence) {
            for (Type type : Type.getArgumentTypes(descriptor)) {
                addType(type, evidence);
            }
            addType(Type.getReturnType(descriptor), evidence);
        }

        private void addDescriptor(
                final String descriptor, final String evidence) {
            addType(Type.getType(descriptor), evidence);
        }

        private void addType(final Type type, final String evidence) {
            if (type.getSort() == Type.ARRAY) {
                addType(type.getElementType(), evidence);
            } else if (type.getSort() == Type.OBJECT) {
                addInternal(type.getInternalName(), evidence);
            }
        }

        private void addInternal(
                final String value, final String evidence) {
            if (value != null && targets.containsKey(value)) {
                references.add(new MetadataReference(value, evidence));
            }
        }
    }

    /**
     * @param target referenced class
     * @param evidence metadata source
     */
    private record MetadataReference(String target, String evidence) {
    }
}

/** Structural scan output. */
final class StructuralScanResult {

    /** Reportable references. */
    private final List<StructuralImpact> impacts;

    /** Changed types referenced only by unreachable non-project classes. */
    private final Set<BoundChangePoint> unreachable;

    StructuralScanResult(
            final List<StructuralImpact> values,
            final Set<BoundChangePoint> unreachablePoints) {
        impacts = List.copyOf(values);
        unreachable = Set.copyOf(unreachablePoints);
    }

    static StructuralScanResult empty() {
        return new StructuralScanResult(List.of(), Set.of());
    }

    List<StructuralImpact> impacts() {
        return impacts;
    }

    boolean hasImpact(final BoundChangePoint point) {
        return impacts.stream().anyMatch(value ->
                value.getChangePoint().equals(point));
    }

    boolean hasUnreachable(final BoundChangePoint point) {
        return unreachable.contains(point);
    }
}
