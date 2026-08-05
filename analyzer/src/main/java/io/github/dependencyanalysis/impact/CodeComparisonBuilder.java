package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.bytecode.DecompiledMethod;
import io.github.dependencyanalysis.bytecode.MethodBodyDecompiler;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.jar.IJarRepository;
import io.github.dependencyanalysis.jar.JarLease;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TraceClassVisitor;
import org.objectweb.asm.util.TraceMethodVisitor;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Builds decompiled Java and ASM fallback evidence for one relevant change. */
final class CodeComparisonBuilder {

    /** Decompiled source producer. */
    private final MethodBodyDecompiler decompiler;

    /** Unified diff producer. */
    private final UnifiedDiffGenerator diffs = new UnifiedDiffGenerator();

    /** Command-scoped dependency repository. */
    private final IJarRepository jarRepository;

    /**
     * Creates a code comparison builder.
     *
     * @param diagnostics diagnostic collector
     * @param repository dependency repository
     */
    CodeComparisonBuilder(
            final DiagnosticLog diagnostics,
            final IJarRepository repository) {
        decompiler = new MethodBodyDecompiler(diagnostics);
        jarRepository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Creates a code comparison builder using the target JDK 8 context.
     *
     * @param diagnostics diagnostic collector
     * @param runtime target JDK 8
     * @param repository dependency repository
     */
    CodeComparisonBuilder(
            final DiagnosticLog diagnostics,
            final JavaRuntimeDescriptor runtime,
            final IJarRepository repository) {
        final java.util.ArrayList<Path> libraries =
                new java.util.ArrayList<>(runtime.getBootClassPath());
        libraries.addAll(runtime.getExtensionClassPath());
        decompiler = new MethodBodyDecompiler(diagnostics, libraries);
        jarRepository = Objects.requireNonNull(repository, "repository");
    }

    /**
     * Builds evidence for a path-associated changed member.
     *
     * @param bound module-bound change
     * @return code comparison evidence
     */
    CodeComparisonEvidence build(final BoundChangePoint bound) {
        final ChangePoint point = bound.getChangePoint();
        final DependencyUpgradeKey key = bound.getDependencyUpgradeKey();
        try (JarLease oldLease = jarRepository.open(key.getOldArtifact());
             JarLease newLease = jarRepository.open(key.getNewArtifact())) {
            final Path oldPath = Path.of(oldLease.jarFile().getName());
            final Path newPath = Path.of(newLease.jarFile().getName());
            final SideText oldSide = side(true, point, oldPath,
                    key.getOldArtifact());
            final SideText newSide = side(false, point, newPath,
                    key.getNewArtifact());
            final String fallback = asmFallback(
                    point, oldPath, newPath);
            if (!oldSide.available() || !newSide.available()) {
                return new CodeComparisonEvidence(
                        CodeComparisonStatus.UNAVAILABLE, List.of(), fallback,
                        unavailableReason(oldSide, newSide));
            }
            final List<UnifiedDiffHunk> hunks = diffs.diff(
                    oldSide.text(), newSide.text());
            if (!hunks.isEmpty()) {
                return new CodeComparisonEvidence(
                        CodeComparisonStatus.AVAILABLE, hunks, "", "");
            }
            return new CodeComparisonEvidence(
                    CodeComparisonStatus.ASM_FALLBACK, List.of(), fallback,
                    "Bytecode differs, but the decompiled Java text "
                            + "is identical");
        } catch (IOException exception) {
            return new CodeComparisonEvidence(
                    CodeComparisonStatus.UNAVAILABLE, List.of(), "",
                    summarize(exception));
        }
    }

    private SideText side(
            final boolean old,
            final ChangePoint point,
            final Path jar,
            final ArtifactCoord artifact) {
        final ChangePointKind kind = point.getKind();
        if (!old && (kind == ChangePointKind.METHOD_REMOVED
                || kind == ChangePointKind.FIELD_REMOVED
                || kind == ChangePointKind.CLASS_REMOVED)) {
            return SideText.available("");
        }
        if (kind == ChangePointKind.METHOD_BODY_CHANGED
                || kind == ChangePointKind.METHOD_REMOVED
                || kind == ChangePointKind.METHOD_DESCRIPTOR_CHANGED) {
            final String descriptor = old ? point.getOldDescriptor()
                    : point.getNewDescriptor();
            return fromDecompiled(decompiler.decompileMethod(jar, point,
                    descriptor, old ? "old" : "new", artifact));
        }
        if (kind == ChangePointKind.FIELD_REMOVED
                || kind == ChangePointKind.FIELD_DESCRIPTOR_CHANGED) {
            final String descriptor = old ? point.getOldDescriptor()
                    : point.getNewDescriptor();
            try {
                return SideText.available(fieldDeclaration(
                        jar, point, descriptor));
            } catch (IOException | RuntimeException exception) {
                return SideText.unavailable(summarize(exception));
            }
        }
        if (kind == ChangePointKind.CLASS_REMOVED) {
            return fromDecompiled(decompiler.decompileClass(jar, point,
                    old ? "old" : "new", artifact));
        }
        return SideText.unavailable(
                "Change kind does not support code comparison: " + kind);
    }

    private SideText fromDecompiled(final DecompiledMethod value) {
        return value.isAvailable()
                ? SideText.available(value.getSource())
                : SideText.unavailable(value.getFailureReason());
    }

    private String fieldDeclaration(
            final Path jar,
            final ChangePoint point,
            final String descriptor) throws IOException {
        final ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(readClass(jar, point.getOwner())).accept(
                node, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG
                        | ClassReader.SKIP_FRAMES);
        for (FieldNode field : node.fields) {
            if (Objects.equals(point.getName(), field.name)
                    && Objects.equals(descriptor, field.desc)) {
                return fieldModifiers(field.access)
                        + Type.getType(field.desc).getClassName() + " "
                        + field.name
                        + (field.value == null ? ""
                        : " = " + fieldLiteral(field.value))
                        + ";\n";
            }
        }
        throw new IOException("Field not found: " + point.getName()
                + descriptor);
    }

    private String fieldLiteral(final Object value) {
        if (value instanceof String) {
            return "\"" + value.toString().replace("\\", "\\\\")
                    .replace("\"", "\\\"") + "\"";
        }
        if (value instanceof Character) {
            return "'" + value.toString().replace("'", "\\'") + "'";
        }
        if (value instanceof Long) {
            return value + "L";
        }
        if (value instanceof Float) {
            return value + "F";
        }
        if (value instanceof Double) {
            return value + "D";
        }
        return value.toString();
    }

    private String fieldModifiers(final int access) {
        final StringBuilder result = new StringBuilder();
        appendModifier(result, access, Opcodes.ACC_PUBLIC, "public");
        appendModifier(result, access, Opcodes.ACC_PROTECTED, "protected");
        appendModifier(result, access, Opcodes.ACC_PRIVATE, "private");
        appendModifier(result, access, Opcodes.ACC_STATIC, "static");
        appendModifier(result, access, Opcodes.ACC_FINAL, "final");
        appendModifier(result, access, Opcodes.ACC_VOLATILE, "volatile");
        appendModifier(result, access, Opcodes.ACC_TRANSIENT, "transient");
        return result.toString();
    }

    private void appendModifier(
            final StringBuilder result,
            final int access,
            final int mask,
            final String label) {
        if ((access & mask) != 0) {
            result.append(label).append(' ');
        }
    }

    private String asmFallback(
            final ChangePoint point,
            final Path oldPath,
            final Path newPath) {
        try {
            final String oldText = asmText(oldPath, point, true);
            final String newText = isRemoved(point.getKind()) ? ""
                    : asmText(newPath, point, false);
            return unifiedText(diffs.diff(oldText, newText),
                    "old/bytecode.asm", "new/bytecode.asm");
        } catch (IOException | RuntimeException exception) {
            return "ASM fallback unavailable: " + summarize(exception);
        }
    }

    private boolean isRemoved(final ChangePointKind kind) {
        return kind == ChangePointKind.METHOD_REMOVED
                || kind == ChangePointKind.FIELD_REMOVED
                || kind == ChangePointKind.CLASS_REMOVED;
    }

    private String asmText(
            final Path jar,
            final ChangePoint point,
            final boolean old) throws IOException {
        if (point.getKind() == ChangePointKind.METHOD_BODY_CHANGED
                || point.getKind() == ChangePointKind.METHOD_REMOVED
                || point.getKind()
                == ChangePointKind.METHOD_DESCRIPTOR_CHANGED) {
            return asmMethod(jar, point, old);
        }
        if (point.getKind() == ChangePointKind.FIELD_REMOVED
                || point.getKind()
                == ChangePointKind.FIELD_DESCRIPTOR_CHANGED) {
            return asmField(jar, point, old);
        }
        final StringWriter output = new StringWriter();
        new ClassReader(readClass(jar, point.getOwner())).accept(
                new TraceClassVisitor(new PrintWriter(output)), 0);
        return output.toString();
    }

    private String asmMethod(
            final Path jar,
            final ChangePoint point,
            final boolean old) throws IOException {
        final ClassNode node = classNode(jar, point.getOwner());
        final String descriptor = old ? point.getOldDescriptor()
                : point.getNewDescriptor();
        for (MethodNode method : node.methods) {
            if (Objects.equals(point.getName(), method.name)
                    && Objects.equals(descriptor, method.desc)) {
                final Textifier printer = new Textifier();
                method.accept(new TraceMethodVisitor(printer));
                return printerText(printer);
            }
        }
        throw new IOException("Method not found: " + point.getName()
                + descriptor);
    }

    private String asmField(
            final Path jar,
            final ChangePoint point,
            final boolean old) throws IOException {
        final ClassNode node = classNode(jar, point.getOwner());
        final String descriptor = old ? point.getOldDescriptor()
                : point.getNewDescriptor();
        for (FieldNode field : node.fields) {
            if (Objects.equals(point.getName(), field.name)
                    && Objects.equals(descriptor, field.desc)) {
                final Textifier printer = new Textifier();
                printer.visitField(field.access, field.name, field.desc,
                        field.signature, field.value);
                return printerText(printer);
            }
        }
        throw new IOException("Field not found: " + point.getName()
                + descriptor);
    }

    private ClassNode classNode(
            final Path jar, final String owner) throws IOException {
        final ClassNode node = new ClassNode(Opcodes.ASM9);
        new ClassReader(readClass(jar, owner)).accept(node, 0);
        return node;
    }

    private String printerText(final Textifier printer) {
        final StringWriter output = new StringWriter();
        printer.print(new PrintWriter(output));
        return output.toString();
    }

    private String unifiedText(
            final List<UnifiedDiffHunk> hunks,
            final String oldLabel,
            final String newLabel) {
        if (hunks.isEmpty()) {
            return "No textual ASM difference was produced.";
        }
        final StringBuilder result = new StringBuilder()
                .append("--- ").append(oldLabel).append('\n')
                .append("+++ ").append(newLabel).append('\n');
        hunks.forEach(hunk -> result.append(hunk.toUnifiedText()));
        return result.toString();
    }

    private byte[] readClass(
            final Path jar, final String owner) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            final ZipEntry entry = zip.getEntry(owner + ".class");
            if (entry == null) {
                throw new IOException("Class entry not found: "
                        + owner + ".class");
            }
            try (InputStream input = zip.getInputStream(entry)) {
                return input.readAllBytes();
            }
        }
    }

    private String unavailableReason(
            final SideText oldSide, final SideText newSide) {
        final StringBuilder result = new StringBuilder();
        if (!oldSide.available()) {
            result.append("Baseline: ").append(oldSide.reason());
        }
        if (!newSide.available()) {
            if (result.length() > 0) {
                result.append("; ");
            }
            result.append("Target: ").append(newSide.reason());
        }
        return result.toString();
    }

    private String summarize(final Throwable throwable) {
        final String message = throwable.getMessage();
        return throwable.getClass().getSimpleName() + (message == null
                || message.isBlank() ? "" : ": " + message);
    }

    /**
     * @param text side text
     * @param reason failure reason
     */
    private record SideText(String text, String reason) {

        static SideText available(final String value) {
            return new SideText(value, null);
        }

        static SideText unavailable(final String value) {
            return new SideText(null, value);
        }

        boolean available() {
            return text != null;
        }
    }
}
