package io.github.dependencyanalysis.jar;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ResolvedArtifact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.jar.JarFile;

/** Default deterministic {@link IJarRepository} implementation. */
public final class CoordinateJarRepository implements IJarRepository {

    /** Selected canonical path retained only inside the repository. */
    private final Map<ArtifactCoord, Path> paths;

    /** Outstanding leases closed as a command-level safety net. */
    private final Set<DefaultJarLease> leases = new LinkedHashSet<>();

    /** Closed state. */
    private boolean closed;

    private CoordinateJarRepository(final Map<ArtifactCoord, Path> values) {
        paths = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * Builds one immutable repository from Resolver-provided bindings.
     *
     * @param artifacts all command bindings
     * @param warning conflict warning consumer
     * @return deterministic repository
     */
    public static CoordinateJarRepository create(
            final Collection<ResolvedArtifact> artifacts,
            final Consumer<String> warning) throws IOException {
        Objects.requireNonNull(artifacts, "artifacts");
        Objects.requireNonNull(warning, "warning");
        final Map<ArtifactCoord, Set<Path>> candidates =
                new LinkedHashMap<>();
        for (ResolvedArtifact artifact : artifacts) {
            if (!"jar".equals(artifact.getArtifact().getType())) {
                continue;
            }
            candidates.computeIfAbsent(artifact.getArtifact(),
                    ignored -> new LinkedHashSet<>())
                    .add(canonical(artifact.getPath()));
        }
        final Map<ArtifactCoord, Path> selected = new LinkedHashMap<>();
        candidates.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(ArtifactCoord::toString)))
                .forEach(entry -> {
                    final List<Path> ranked = new ArrayList<>(
                            entry.getValue());
                    ranked.sort(Comparator.comparing(Path::toString));
                    final Path winner = ranked.get(0);
                    selected.put(entry.getKey(), winner);
                    if (ranked.size() > 1) {
                        warning.accept("Artifact coordinate maps to multiple "
                                + "JAR paths; selected lexicographically "
                                + "first: "
                                + "artifact=" + entry.getKey()
                                + "; selected=" + winner
                                + "; candidates=" + ranked);
                    }
                });
        for (Map.Entry<ArtifactCoord, Path> entry : selected.entrySet()) {
            if (!Files.isRegularFile(entry.getValue())) {
                throw new IOException("JAR file is unavailable: "
                        + entry.getKey());
            }
            try (JarFile ignored = new JarFile(
                    entry.getValue().toFile(), false)) {
                ignored.size();
            }
        }
        return new CoordinateJarRepository(selected);
    }

    @Override
    public Set<ArtifactCoord> coordinates() {
        return paths.keySet();
    }

    @Override
    public synchronized JarLease open(final ArtifactCoord coordinate)
            throws IOException {
        Objects.requireNonNull(coordinate, "coordinate");
        if (closed) {
            throw new IOException("JAR repository is closed");
        }
        final Path path = paths.get(coordinate);
        if (path == null) {
            throw new IOException("JAR coordinate is unavailable: "
                    + coordinate);
        }
        if (!Files.isRegularFile(path)) {
            throw new IOException("JAR file is unavailable: "
                    + coordinate);
        }
        final DefaultJarLease lease = new DefaultJarLease(
                coordinate, new JarFile(path.toFile(), false));
        leases.add(lease);
        return lease;
    }

    @Override
    public synchronized void close() throws IOException {
        if (closed) {
            return;
        }
        closed = true;
        IOException failure = null;
        for (DefaultJarLease lease : List.copyOf(leases)) {
            try {
                lease.close();
            } catch (IOException exception) {
                if (failure == null) {
                    failure = exception;
                } else {
                    failure.addSuppressed(exception);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static Path canonical(final Path path) {
        try {
            return path.toRealPath();
        } catch (IOException exception) {
            return path.toAbsolutePath().normalize();
        }
    }

    /** Default tracked lease. */
    private final class DefaultJarLease implements JarLease {

        /** Logical coordinate. */
        private final ArtifactCoord coordinate;

        /** Physical handle. */
        private final JarFile jar;

        /** Lease state. */
        private boolean leaseClosed;

        DefaultJarLease(
                final ArtifactCoord artifact,
                final JarFile handle) {
            coordinate = artifact;
            jar = handle;
        }

        @Override
        public ArtifactCoord coordinate() {
            return coordinate;
        }

        @Override
        public JarFile jarFile() {
            if (leaseClosed) {
                throw new IllegalStateException("JAR lease is closed: "
                        + coordinate);
            }
            return jar;
        }

        @Override
        public void close() throws IOException {
            synchronized (CoordinateJarRepository.this) {
                if (leaseClosed) {
                    return;
                }
                leaseClosed = true;
                leases.remove(this);
            }
            jar.close();
        }
    }
}
