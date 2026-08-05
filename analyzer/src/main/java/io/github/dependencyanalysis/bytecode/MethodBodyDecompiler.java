package io.github.dependencyanalysis.bytecode;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.jar.JarLocationResult;

import org.jetbrains.java.decompiler.api.Decompiler;
import org.jetbrains.java.decompiler.main.extern.IContextSource;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

// Wiki: wiki/features/bytecode-diff-engine.md - Method body decompilation
/**
 * Produces best-effort Java-like old/new
 * evidence for changed method bodies.
 */
public final class MethodBodyDecompiler {

    /** Diagnostic stage name. */
    public static final String STAGE =
            "method-body-decompile";

    /** Maximum diagnostic reason length. */
    private static final int MAX_REASON = 300;

    /** Ellipsis length. */
    private static final int ELLIPSIS_LENGTH = 3;

    /** Diagnostics. */
    private final DiagnosticLog diagnostics;

    /** Exact dependency/JDK library context. */
    private final List<Path> libraries;

    /**
     * Creates a method body decompiler.
     *
     * @param collector diagnostics
     */
    public MethodBodyDecompiler(
            final DiagnosticLog collector) {
        this(collector, List.of());
    }

    /**
     * Creates a method body decompiler with library context.
     *
     * @param collector diagnostics
     * @param libraryPaths exact dependency and JDK paths
     */
    public MethodBodyDecompiler(
            final DiagnosticLog collector,
            final List<Path> libraryPaths) {
        diagnostics = Objects.requireNonNull(
                collector, "diagnostics");
        libraries = List.copyOf(libraryPaths);
    }

    /**
     * Decompiles both sides of one method
     * body change without writing source files.
     *
     * @param location old/new jar location
     * @param point method body change point
     * @return best-effort evidence
     */
    public MethodBodyEvidence decompile(
            final JarLocationResult location,
            final ChangePoint point) {
        Objects.requireNonNull(location,
                "location");
        Objects.requireNonNull(point,
                "changePoint");
        if (point.getKind()
                != ChangePointKind.METHOD_BODY_CHANGED) {
            throw new IllegalArgumentException(
                    "Only METHOD_BODY_CHANGED can "
                            + "be decompiled");
        }
        final ArtifactCoord oldArtifact =
                location.getChange().getOldArtifact();
        final ArtifactCoord newArtifact =
                location.getChange().getNewArtifact();
        final DecompiledMethod oldResult =
                decompileMethod(location.getOldJar(), point,
                        point.getOldDescriptor(), "old", oldArtifact);
        final DecompiledMethod newResult =
                decompileMethod(location.getNewJar(), point,
                        point.getNewDescriptor(), "new", newArtifact);
        return new MethodBodyEvidence(point,
                oldArtifact, newArtifact,
                oldResult, newResult);
    }

    /**
     * Decompiles one exact method descriptor in memory.
     *
     * @param jar physical artifact
     * @param point changed member
     * @param descriptor exact side descriptor
     * @param side evidence side label
     * @param artifact side artifact coordinate
     * @return best-effort Java representation
     */
    public DecompiledMethod decompileMethod(
            final Path jar,
            final ChangePoint point,
            final String descriptor,
            final String side,
            final ArtifactCoord artifact) {
        Objects.requireNonNull(descriptor, "descriptor");
        return decompileSide(jar, point, side, artifact,
                point.getOwner() + "." + point.getName() + descriptor);
    }

    /**
     * Decompiles one complete class in memory.
     *
     * @param jar physical artifact
     * @param point changed class
     * @param side evidence side label
     * @param artifact side artifact coordinate
     * @return best-effort Java representation
     */
    public DecompiledMethod decompileClass(
            final Path jar,
            final ChangePoint point,
            final String side,
            final ArtifactCoord artifact) {
        return decompileSide(jar, point, side, artifact, null);
    }

    private DecompiledMethod decompileSide(
            final Path jar,
            final ChangePoint point,
            final String side,
            final ArtifactCoord artifact,
            final String methodId) {
        try {
            final byte[] classBytes = readClass(
                    jar, point.getOwner());
            final CapturingSaver saver =
                    new CapturingSaver();
            final CapturingLogger logger =
                    new CapturingLogger();
            final IContextSource source =
                    new SingleClassSource(
                            point.getOwner(),
                            classBytes);
            final Decompiler.Builder builder = Decompiler.builder()
                    .inputs(source)
                    .output(saver)
                    .logger(logger)
                    .option(IFernflowerPreferences
                            .THREADS, "1")
                    .option(IFernflowerPreferences
                            .SKIP_EXTRA_FILES, true)
                    .option(IFernflowerPreferences
                            .REMOVE_SYNTHETIC, false)
                    .option(IFernflowerPreferences
                            .REMOVE_BRIDGE, false)
                    .option(IFernflowerPreferences
                            .HIDE_DEFAULT_CONSTRUCTOR,
                            false)
                    .option(IFernflowerPreferences
                            .DUMP_BYTECODE_ON_ERROR,
                            false)
                    .option(IFernflowerPreferences
                            .DUMP_EXCEPTION_ON_ERROR,
                            false);
            final List<Path> sideLibraries = new java.util.ArrayList<>();
            sideLibraries.add(jar);
            libraries.stream().filter(value -> !value.equals(jar))
                    .forEach(sideLibraries::add);
            if (!sideLibraries.isEmpty()) {
                builder.libraries(sideLibraries.stream().map(Path::toFile)
                        .toArray(java.io.File[]::new));
            }
            if (methodId != null) {
                builder.option(IFernflowerPreferences.METHOD_TO_DECOMPILE,
                        methodId);
            }
            builder.build().decompile();
            if (logger.getError() != null) {
                return unavailable(side, artifact,
                        point, logger.getError());
            }
            final String content = saver.getContent();
            if (content == null
                    || content.isBlank()) {
                return unavailable(side, artifact,
                        point,
                        "Decompiler produced no source");
            }
            if (content.contains(
                    "$VF: Unable to decompile")) {
                return unavailable(side, artifact,
                        point,
                        "Vineflower could not "
                                + "decompile the method");
            }
            return DecompiledMethod.available(
                    normalizeNewlines(content));
        } catch (IOException
                | RuntimeException exception) {
            return unavailable(side, artifact,
                    point, summarize(exception));
        }
    }

    private DecompiledMethod unavailable(
            final String side,
            final ArtifactCoord artifact,
            final ChangePoint point,
            final String reason) {
        final String value = compact(reason);
        diagnostics.warn(STAGE,
                "Unable to decompile " + side
                        + " method/class code: artifact="
                        + artifact + ", method="
                        + point.getOwner() + "."
                        + String.valueOf(point.getName())
                        + String.valueOf(point.getDescriptor())
                        + ", reason=" + value);
        return DecompiledMethod.unavailable(value);
    }

    private byte[] readClass(
            final Path jar,
            final String owner)
            throws IOException {
        final String entryName = owner
                + IContextSource.CLASS_SUFFIX;
        try (ZipFile zip = new ZipFile(
                jar.toFile())) {
            final ZipEntry entry =
                    zip.getEntry(entryName);
            if (entry == null) {
                throw new IOException(
                        "Class entry not found: "
                                + entryName);
            }
            try (InputStream input =
                         zip.getInputStream(entry)) {
                return input.readAllBytes();
            }
        }
    }

    private String summarize(
            final Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        final String message = current.getMessage();
        if (message == null
                || message.isBlank()) {
            return current.getClass()
                    .getSimpleName();
        }
        return current.getClass().getSimpleName()
                + ": " + message;
    }

    private String compact(final String value) {
        final String normalized = value
                .replaceAll("\\s+", " ")
                .trim();
        if (normalized.length() <= MAX_REASON) {
            return normalized;
        }
        return normalized.substring(0,
                MAX_REASON - ELLIPSIS_LENGTH)
                + "...";
    }

    private String normalizeNewlines(
            final String value) {
        return value.replace("\r\n", "\n")
                .replace('\r', '\n');
    }

    /** In-memory source containing one class. */
    private static final class SingleClassSource
            implements IContextSource {

        /** Internal owner name. */
        private final String owner;

        /** Class bytes. */
        private final byte[] bytes;

        SingleClassSource(
                final String classOwner,
                final byte[] classBytes) {
            this.owner = classOwner;
            this.bytes = classBytes.clone();
        }

        @Override
        public String getName() {
            return "class " + owner;
        }

        @Override
        public Entries getEntries() {
            return new Entries(
                    List.of(Entry.atBase(owner)),
                    List.of(), List.of());
        }

        @Override
        public InputStream getInputStream(
                final String resource) {
            if (!(owner + CLASS_SUFFIX)
                    .equals(resource)) {
                return null;
            }
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public IOutputSink createOutputSink(
                final IResultSaver saver) {
            return new IOutputSink() {
                @Override
                public void begin() {
                }

                @Override
                public void acceptClass(
                        final String qualifiedName,
                        final String fileName,
                        final String content,
                        final int[] mapping) {
                    saver.saveClassFile("",
                            qualifiedName,
                            fileName, content,
                            mapping);
                }

                @Override
                public void acceptDirectory(
                        final String directory) {
                }

                @Override
                public void acceptOther(
                        final String path) {
                }

                @Override
                public void close() {
                }
            };
        }
    }

    /** Captures decompiled text in memory. */
    private static final class CapturingSaver
            implements IResultSaver {

        /** Captured source. */
        private String content;

        @Override
        public void saveFolder(final String path) {
        }

        @Override
        public void copyFile(
                final String source,
                final String path,
                final String entryName) {
        }

        @Override
        public void saveClassFile(
                final String path,
                final String qualifiedName,
                final String entryName,
                final String source,
                final int[] mapping) {
            content = source;
        }

        @Override
        public void createArchive(
                final String path,
                final String archiveName,
                final Manifest manifest) {
        }

        @Override
        public void saveDirEntry(
                final String path,
                final String archiveName,
                final String entryName) {
        }

        @Override
        public void copyEntry(
                final String source,
                final String path,
                final String archiveName,
                final String entry) {
        }

        @Override
        public void saveClassEntry(
                final String path,
                final String archiveName,
                final String qualifiedName,
                final String entryName,
                final String source) {
            content = source;
        }

        @Override
        public void closeArchive(
                final String path,
                final String archiveName) {
        }

        String getContent() {
            return content;
        }
    }

    /** Captures fatal decompiler log output. */
    private static final class CapturingLogger
            extends IFernflowerLogger {

        /** Last error message. */
        private String error;

        @Override
        public void writeMessage(
                final String message,
                final Severity severity) {
            if (severity == Severity.ERROR) {
                error = message;
            }
        }

        @Override
        public void writeMessage(
                final String message,
                final Severity severity,
                final Throwable throwable) {
            if (severity == Severity.ERROR) {
                final String cause = throwable == null
                        ? ""
                        : ": " + throwable
                        .getClass().getSimpleName()
                        + " " + throwable.getMessage();
                error = message + cause;
            }
        }

        String getError() {
            return error;
        }
    }
}
