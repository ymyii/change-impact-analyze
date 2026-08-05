package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.callgraph.ClassOwnershipIndex;
import io.github.dependencyanalysis.callgraph.ClassSource;
import io.github.dependencyanalysis.callgraph.CodeOrigin;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.jar.IJarRepository;
import io.github.dependencyanalysis.jar.JarLease;

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
public final class StructuralImpactScanner {

    /** ASM API. */
    private static final int API = Opcodes.ASM9;

    /** Command-scoped dependency repository. */
    private final IJarRepository jarRepository;

    /** @param repository dependency repository */
    public StructuralImpactScanner(final IJarRepository repository) {
        jarRepository = java.util.Objects.requireNonNull(
                repository, "repository");
    }

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
     * @param ownership effective target class definitions
     * @return classified structural references
     */
    public StructuralScanResult scan(
            final ModuleAnalysisUnit unit,
            final ClassOwnershipIndex ownership) {
        final Map<String, List<BoundChangePoint>> targets = targets(unit);
        if (targets.isEmpty()) {
            return StructuralScanResult.empty();
        }
        final List<StructuralReferenceMatch> references = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        try {
            scanDirectory(unit.getProjectClasses(), CodeOrigin.PROJECT,
                    ownership, targets, references, seen);
            for (Path path : unit.getReactorDependencyClasses()) {
                scanDirectory(path, CodeOrigin.REACTOR_DEPENDENCY,
                        ownership, targets, references, seen);
            }
            for (ArtifactCoord artifact : unit.getTargetArtifacts()) {
                scanJar(artifact, CodeOrigin.DEPENDENCY,
                        ownership, targets, references, seen);
            }
        } catch (IOException | RuntimeException exception) {
            throw new ImpactException(
                    "Unable to scan structural metadata", exception);
        }
        references.sort(Comparator
                .comparing((StructuralReferenceMatch value) ->
                        value.changePoint().stableKey())
                .thenComparing(value -> value.reference().stableKey()));
        return new StructuralScanResult(references);
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

    private void scanDirectory(
            final Path directory,
            final CodeOrigin origin,
            final ClassOwnershipIndex ownership,
            final Map<String, List<BoundChangePoint>> targets,
            final List<StructuralReferenceMatch> references,
            final Set<String> seen) throws IOException {
        final List<Path> files;
        try (Stream<Path> stream = Files.walk(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".class"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
        for (Path file : files) {
            final String name = className(
                    directory.relativize(file).toString());
            if (!ownership.isEffectiveDefinition(name, directory)) {
                continue;
            }
            scanClass(Files.readAllBytes(file), origin, targets,
                    references, seen);
        }
    }

    private void scanJar(
            final ArtifactCoord artifact,
            final CodeOrigin origin,
            final ClassOwnershipIndex ownership,
            final Map<String, List<BoundChangePoint>> targets,
            final List<StructuralReferenceMatch> references,
            final Set<String> seen) throws IOException {
        try (JarLease lease = jarRepository.open(artifact)) {
            final JarFile jar = lease.jarFile();
            final List<JarEntry> entries = jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .filter(entry -> !entry.getName().startsWith(
                            "META-INF/versions/"))
                    .sorted(Comparator.comparing(JarEntry::getName))
                    .toList();
            for (JarEntry entry : entries) {
                if (!ownership.isEffectiveDefinition(
                        className(entry.getName()),
                        ClassSource.artifact(artifact))) {
                    continue;
                }
                try (InputStream input = jar.getInputStream(entry)) {
                    scanClass(input.readAllBytes(), origin, targets,
                            references, seen);
                }
            }
        }
    }

    private String className(final String value) {
        final String unix = value.replace('\\', '/');
        return unix.substring(0,
                unix.length() - ".class".length());
    }

    private void scanClass(
            final byte[] bytes,
            final CodeOrigin origin,
            final Map<String, List<BoundChangePoint>> targets,
            final List<StructuralReferenceMatch> references,
            final Set<String> seen) {
        final MetadataCollector collector = new MetadataCollector(targets);
        new ClassReader(bytes).accept(collector,
                ClassReader.SKIP_CODE
                        | ClassReader.SKIP_DEBUG
                        | ClassReader.SKIP_FRAMES);
        for (MetadataReference reference : collector.references()) {
            for (BoundChangePoint point
                    : targets.get(reference.target())) {
                final String key = point.stableKey() + "|"
                        + collector.owner() + "|" + origin + "|"
                        + reference.evidence();
                if (seen.add(key)) {
                    references.add(new StructuralReferenceMatch(
                            point, structuralReference(collector.owner(),
                            origin, reference)));
                }
            }
        }
    }

    private StructuralReference structuralReference(
            final String owner,
            final CodeOrigin origin,
            final MetadataReference metadata) {
        final String evidence = metadata.evidence();
        final int separator = evidence.indexOf(':');
        final String prefix = separator < 0 ? evidence
                : evidence.substring(0, separator);
        final String member = separator < 0 ? ""
                : evidence.substring(separator + 1);
        return new StructuralReference(owner, origin,
                structuralKind(prefix, evidence, metadata.target()), member,
                metadata.target(), evidence);
    }

    private StructuralReferenceKind structuralKind(
            final String prefix,
            final String evidence,
            final String target) {
        if ("SUPERCLASS".equals(prefix)) {
            return StructuralReferenceKind.SUPERCLASS;
        }
        if ("INTERFACE".equals(prefix)) {
            return StructuralReferenceKind.INTERFACE;
        }
        if (prefix.contains("ANNOTATION")) {
            return StructuralReferenceKind.ANNOTATION;
        }
        if (prefix.startsWith("FIELD")) {
            return StructuralReferenceKind.FIELD_TYPE;
        }
        if ("THROWS".equals(prefix)) {
            return StructuralReferenceKind.THROWS;
        }
        if (prefix.contains("SIGNATURE")) {
            return StructuralReferenceKind.SIGNATURE;
        }
        if ("METHOD_DESCRIPTOR".equals(prefix)) {
            final int descriptor = evidence.indexOf('(');
            if (descriptor >= 0) {
                final String value = evidence.substring(descriptor);
                if (Type.getReturnType(value).getSort() != Type.VOID
                        && containsTarget(Type.getReturnType(value), target)) {
                    return StructuralReferenceKind.METHOD_RETURN;
                }
            }
            return StructuralReferenceKind.METHOD_PARAMETER;
        }
        return StructuralReferenceKind.METADATA;
    }

    private boolean containsTarget(final Type type, final String target) {
        final Type value = type.getSort() == Type.ARRAY
                ? type.getElementType() : type;
        return value.getSort() == Type.OBJECT
                && target.equals(value.getInternalName());
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
