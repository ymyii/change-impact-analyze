package io.github.dependencyanalysis.callgraph.protocol.serviceloader;

import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.ModuleEntry;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;

/** WALA module exposing only directory-based ServiceLoader resources. */
public final class ServiceResourceDirectoryModule implements Module {

    /** Service configuration root. */
    private static final Path SERVICE_ROOT =
            Path.of("META-INF", "services");

    /** Stable resource entries. */
    private final List<ModuleEntry> entries;

    /**
     * Creates a module from one class directory.
     *
     * @param classRoot class directory
     * @throws IOException when resources cannot be listed
     */
    public ServiceResourceDirectoryModule(final Path classRoot)
            throws IOException {
        final Path root = classRoot.resolve(SERVICE_ROOT);
        if (!Files.isDirectory(root)) {
            entries = List.of();
            return;
        }
        try (var paths = Files.walk(root)) {
            entries = paths.filter(Files::isRegularFile)
                    .sorted()
                    .map(path -> new ResourceEntry(
                            classRoot, path, this))
                    .map(ModuleEntry.class::cast)
                    .toList();
        }
    }

    @Override
    public Iterator<? extends ModuleEntry> getEntries() {
        return entries.iterator();
    }

    /**
     * One non-class resource.
     *
     * @param root owning classes root
     * @param path resource path
     * @param container owning module
     */
    private record ResourceEntry(
            Path root,
            Path path,
            Module container) implements ModuleEntry {

        @Override
        public String getName() {
            return root.relativize(path).toString().replace('\\', '/');
        }

        @Override
        public boolean isClassFile() {
            return false;
        }

        @Override
        public boolean isSourceFile() {
            return false;
        }

        @Override
        public InputStream getInputStream() {
            try {
                return Files.newInputStream(path);
            } catch (IOException exception) {
                throw new UncheckedIOException(
                        "Unable to open ServiceLoader resource: "
                                + getName(), exception);
            }
        }

        @Override
        public boolean isModuleFile() {
            return false;
        }

        @Override
        public Module asModule() {
            throw new UnsupportedOperationException(
                    "ServiceLoader resource is not a nested module");
        }

        @Override
        public String getClassName() {
            return null;
        }

        @Override
        public Module getContainer() {
            return container;
        }
    }
}
