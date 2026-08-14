package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.callgraph.scope.ClassOwnershipIndex;
import io.github.dependencyanalysis.callgraph.scope.ClassSource;
import io.github.dependencyanalysis.callgraph.model.CodeOrigin;
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
        final MetadataCollector collector =
                new MetadataCollector(targetNames);
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
    public StructuralReferenceIndex scan(
            final ModuleAnalysisUnit unit,
            final ClassOwnershipIndex ownership) {
        final Set<String> targets = targets(unit);
        if (targets.isEmpty()) {
            return StructuralReferenceIndex.empty();
        }
        final List<StructuralReference> references = new ArrayList<>();
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
        return new StructuralReferenceIndex(references);
    }

    private Set<String> targets(
            final ModuleAnalysisUnit unit) {
        final Set<String> result = new LinkedHashSet<>();
        for (BoundChangePoint point : unit.getChangePoints()) {
            if (point.getChangePoint().getKind()
                    == ChangePointKind.CLASS_REMOVED
                    || point.getChangePoint().getKind()
                    == ChangePointKind.CLASS_ACCESS_NARROWED) {
                result.add(point.getChangePoint().getOwner());
            }
        }
        return result;
    }

    private void scanDirectory(
            final Path directory,
            final CodeOrigin origin,
            final ClassOwnershipIndex ownership,
            final Set<String> targets,
            final List<StructuralReference> references,
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
            final Set<String> targets,
            final List<StructuralReference> references,
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
            final Set<String> targets,
            final List<StructuralReference> references,
            final Set<String> seen) {
        final MetadataCollector collector = new MetadataCollector(targets);
        new ClassReader(bytes).accept(collector,
                ClassReader.SKIP_CODE
                        | ClassReader.SKIP_DEBUG
                        | ClassReader.SKIP_FRAMES);
        for (MetadataReference reference : collector.references()) {
            final StructuralReference value = structuralReference(
                    collector.owner(), origin, reference);
            if (seen.add(value.stableKey())) {
                references.add(value);
            }
        }
    }

    private StructuralReference structuralReference(
            final String owner,
            final CodeOrigin origin,
            final MetadataReference metadata) {
        return new StructuralReference(owner, origin, metadata.kind(),
                metadata.member(), metadata.target(), metadata.evidence());
    }

    /** Metadata collection visitor. */
    private static final class MetadataCollector extends ClassVisitor {

        /** Relevant changed types. */
        private final Set<String> targets;

        /** Matched metadata records. */
        private final Set<MetadataReference> references =
                new LinkedHashSet<>();

        /** Current class. */
        private String owner;

        /** @param changedTypes relevant removed classes */
        MetadataCollector(final Set<String> changedTypes) {
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
            addInternal(superName, StructuralReferenceKind.SUPERCLASS,
                    "", "SUPERCLASS");
            if (interfaces != null) {
                for (String value : interfaces) {
                    addInternal(value, StructuralReferenceKind.INTERFACE,
                            "", "INTERFACE");
                }
            }
            addSignature(signature, StructuralReferenceKind.SIGNATURE,
                    "", "CLASS_SIGNATURE");
        }

        @Override
        public AnnotationVisitor visitAnnotation(
                final String descriptor, final boolean visible) {
            addDescriptor(descriptor, StructuralReferenceKind.ANNOTATION,
                    "", "CLASS_ANNOTATION");
            return annotationVisitor(StructuralReferenceKind.ANNOTATION,
                    "", "CLASS_ANNOTATION_VALUE");
        }

        @Override
        public AnnotationVisitor visitTypeAnnotation(
                final int typeRef,
                final org.objectweb.asm.TypePath typePath,
                final String descriptor,
                final boolean visible) {
            addDescriptor(descriptor, StructuralReferenceKind.ANNOTATION,
                    "", "CLASS_TYPE_ANNOTATION");
            return annotationVisitor(StructuralReferenceKind.ANNOTATION,
                    "", "CLASS_TYPE_ANNOTATION_VALUE");
        }

        @Override
        public FieldVisitor visitField(
                final int access,
                final String name,
                final String descriptor,
                final String signature,
                final Object value) {
            addDescriptor(descriptor, StructuralReferenceKind.FIELD_TYPE,
                    name, "FIELD_DESCRIPTOR:" + name);
            addSignature(signature, StructuralReferenceKind.SIGNATURE,
                    name, "FIELD_SIGNATURE:" + name);
            return new FieldVisitor(API) {
                @Override
                public AnnotationVisitor visitAnnotation(
                        final String desc, final boolean visible) {
                    addDescriptor(desc, StructuralReferenceKind.ANNOTATION,
                            name, "FIELD_ANNOTATION:" + name);
                    return annotationVisitor(
                            StructuralReferenceKind.ANNOTATION, name,
                            "FIELD_ANNOTATION_VALUE:" + name);
                }

                @Override
                public AnnotationVisitor visitTypeAnnotation(
                        final int typeRef,
                        final org.objectweb.asm.TypePath typePath,
                        final String desc,
                        final boolean visible) {
                    addDescriptor(desc, StructuralReferenceKind.ANNOTATION,
                            name, "FIELD_TYPE_ANNOTATION:" + name);
                    return annotationVisitor(
                            StructuralReferenceKind.ANNOTATION, name,
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
            addMethodDescriptor(descriptor, method);
            addSignature(signature, StructuralReferenceKind.SIGNATURE,
                    method, "METHOD_SIGNATURE:" + method);
            if (exceptions != null) {
                for (String value : exceptions) {
                    addInternal(value, StructuralReferenceKind.THROWS,
                            method, "THROWS:" + method);
                }
            }
            return new MethodVisitor(API) {
                @Override
                public AnnotationVisitor visitAnnotationDefault() {
                    return annotationVisitor(
                            StructuralReferenceKind.ANNOTATION, method,
                            "ANNOTATION_DEFAULT_VALUE:" + method);
                }

                @Override
                public AnnotationVisitor visitAnnotation(
                        final String desc, final boolean visible) {
                    addDescriptor(desc, StructuralReferenceKind.ANNOTATION,
                            method, "METHOD_ANNOTATION:" + method);
                    return annotationVisitor(
                            StructuralReferenceKind.ANNOTATION, method,
                            "METHOD_ANNOTATION_VALUE:" + method);
                }

                @Override
                public AnnotationVisitor visitTypeAnnotation(
                        final int typeRef,
                        final org.objectweb.asm.TypePath typePath,
                        final String desc,
                        final boolean visible) {
                    addDescriptor(desc, StructuralReferenceKind.ANNOTATION,
                            method, "METHOD_TYPE_ANNOTATION:" + method);
                    return annotationVisitor(
                            StructuralReferenceKind.ANNOTATION, method,
                            "METHOD_TYPE_ANNOTATION_VALUE:" + method);
                }

                @Override
                public AnnotationVisitor visitParameterAnnotation(
                        final int parameter,
                        final String desc,
                        final boolean visible) {
                    final String member = method + ":" + parameter;
                    addDescriptor(desc, StructuralReferenceKind.ANNOTATION,
                            member, "PARAMETER_ANNOTATION:" + member);
                    return annotationVisitor(
                            StructuralReferenceKind.ANNOTATION, member,
                            "PARAMETER_ANNOTATION_VALUE:" + member);
                }
            };
        }

        private AnnotationVisitor annotationVisitor(
                final StructuralReferenceKind kind,
                final String member,
                final String evidence) {
            return new AnnotationVisitor(API) {
                @Override
                public void visit(final String name, final Object value) {
                    if (value instanceof Type) {
                        addType((Type) value, kind, member, evidence);
                    }
                }

                @Override
                public void visitEnum(
                        final String name,
                        final String descriptor,
                        final String value) {
                    addDescriptor(descriptor, kind, member, evidence);
                }

                @Override
                public AnnotationVisitor visitAnnotation(
                        final String name, final String descriptor) {
                    addDescriptor(descriptor, kind, member, evidence);
                    return annotationVisitor(kind, member, evidence);
                }

                @Override
                public AnnotationVisitor visitArray(final String name) {
                    return annotationVisitor(kind, member, evidence);
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
                final String signature,
                final StructuralReferenceKind kind,
                final String member,
                final String evidence) {
            if (signature == null) {
                return;
            }
            final SignatureVisitor visitor = new SignatureVisitor(API) {

                /** Current possibly nested class name. */
                private String current;

                @Override
                public void visitClassType(final String name) {
                    current = name;
                    addInternal(name, kind, member, evidence);
                }

                @Override
                public void visitInnerClassType(final String name) {
                    current = current == null ? name
                            : current + "$" + name;
                    addInternal(current, kind, member, evidence);
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
                final String descriptor, final String member) {
            for (Type type : Type.getArgumentTypes(descriptor)) {
                addType(type, StructuralReferenceKind.METHOD_PARAMETER,
                        member, "METHOD_DESCRIPTOR:" + member);
            }
            addType(Type.getReturnType(descriptor),
                    StructuralReferenceKind.METHOD_RETURN,
                    member, "METHOD_DESCRIPTOR:" + member);
        }

        private void addDescriptor(
                final String descriptor,
                final StructuralReferenceKind kind,
                final String member,
                final String evidence) {
            addType(Type.getType(descriptor), kind, member, evidence);
        }

        private void addType(
                final Type type,
                final StructuralReferenceKind kind,
                final String member,
                final String evidence) {
            if (type.getSort() == Type.ARRAY) {
                addType(type.getElementType(), kind, member, evidence);
            } else if (type.getSort() == Type.OBJECT) {
                addInternal(type.getInternalName(), kind, member, evidence);
            }
        }

        private void addInternal(
                final String value,
                final StructuralReferenceKind kind,
                final String member,
                final String evidence) {
            if (value != null && targets.contains(value)) {
                references.add(new MetadataReference(
                        value, kind, member, evidence));
            }
        }
    }

    /**
     * @param target referenced class
     * @param kind typed structural relationship
     * @param member declaring member, or empty for class metadata
     * @param evidence presentation detail
     */
    private record MetadataReference(
            String target,
            StructuralReferenceKind kind,
            String member,
            String evidence) {
    }
}
