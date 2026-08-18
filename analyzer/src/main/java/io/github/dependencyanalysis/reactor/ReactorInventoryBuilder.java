package io.github.dependencyanalysis.reactor;

import io.github.dependencyanalysis.util.CommandResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Wiki: wiki/architecture/dependency-analysis-pipelines.md - Shared scope
// Wiki: wiki/features/repository-dependency-tree-report.md - Tree resolver
/** Resolves one requested POM to an active Maven reactor scope. */
public final class ReactorInventoryBuilder {

    /** Maven coordinate segment count. */
    private static final int COORDINATE_SEGMENTS = 3;

    /**
     * Resolves one analysis directory inside an existing Git checkout.
     *
     * @param repositoryRoot Git checkout root
     * @param analysisPath requested Git-root-relative directory
     * @param mavenArguments validated Maven arguments
     * @return inventory containing exactly one resolved scope
     * @throws Exception on Git, XML, activation, or module graph failure
     */
    public RepositoryInventory build(
            final Path repositoryRoot,
            final Path analysisPath,
            final List<String> mavenArguments) throws Exception {
        return build(repositoryRoot, analysisPath,
                MavenActivationContext.resolve(mavenArguments,
                        System.getProperty("java.version", "")));
    }

    /**
     * Resolves one analysis directory using explicit activation inputs.
     *
     * @param repositoryRoot Git checkout root
     * @param analysisPath requested Git-root-relative directory
     * @param activation Maven activation inputs
     * @return inventory containing exactly one resolved scope
     * @throws Exception on Git, XML, activation, or module graph failure
     */
    public RepositoryInventory build(
            final Path repositoryRoot,
            final Path analysisPath,
            final MavenActivationContext activation) throws Exception {
        final Path root = repositoryRoot.toRealPath().normalize();
        final Path relative = normalizeAnalysisPath(analysisPath);
        final Path requestedPom = relative.resolve("pom.xml").normalize();
        final Resolver resolver = new Resolver(root,
                new SafePomParser(activation));
        final PomDescriptor requested = resolver.requiredPom(requestedPom);

        final ReactorScopeMode mode;
        final Path rootPom;
        final List<Path> activePoms;
        if (!requested.getActiveModules().isEmpty()) {
            mode = ReactorScopeMode.FULL_REACTOR;
            rootPom = requestedPom;
            activePoms = resolver.activeClosure(rootPom);
        } else {
            final OwnedScope owned = findOwningAncestor(
                    relative, requestedPom, resolver);
            if (owned == null) {
                mode = ReactorScopeMode.STANDALONE;
                rootPom = requestedPom;
                activePoms = List.of(requestedPom);
            } else {
                mode = ReactorScopeMode.SINGLE_MODULE;
                rootPom = owned.rootPom();
                activePoms = owned.activePoms();
            }
        }
        validateCoordinates(activePoms, resolver);
        final PomDescriptor rootDescriptor = resolver.requiredPom(rootPom);
        final ReactorDescriptor reactor = new ReactorDescriptor(
                rootPom, rootDescriptor.getCoordinate(), activePoms,
                List.of(requestedPom), mode, List.of());
        return new RepositoryInventory(List.of(reactor),
                resolver.descriptors(), relative);
    }

    private Path normalizeAnalysisPath(final Path value) {
        final Path result = value == null ? Path.of("") : value.normalize();
        if (result.isAbsolute() || result.startsWith("..")) {
            throw new IllegalArgumentException(
                    "Analysis path must stay inside Git root: " + value);
        }
        return result;
    }

    private OwnedScope findOwningAncestor(
            final Path analysisPath,
            final Path requestedPom,
            final Resolver resolver) throws Exception {
        final List<Path> candidates = new ArrayList<>();
        Path directory = analysisPath.getParent();
        while (directory != null) {
            candidates.add(directory.resolve("pom.xml").normalize());
            directory = directory.getParent();
        }
        if (!analysisPath.toString().isEmpty()) {
            candidates.add(Path.of("pom.xml"));
        }
        Collections.reverse(candidates);
        for (Path candidate : candidates.stream().distinct().toList()) {
            if (!resolver.isEligiblePom(candidate)) {
                continue;
            }
            final List<Path> closure = resolver.activeClosure(candidate);
            if (closure.contains(requestedPom)) {
                return new OwnedScope(candidate, closure);
            }
        }
        return null;
    }

    private void validateCoordinates(
            final List<Path> activePoms,
            final Resolver resolver) throws Exception {
        final Map<String, Path> coordinates = new LinkedHashMap<>();
        for (Path pom : activePoms) {
            final String coordinate = resolver.requiredPom(pom).getCoordinate();
            final String[] parts = coordinate.split(":", -1);
            if (parts.length != COORDINATE_SEGMENTS || invalid(parts[0])
                    || invalid(parts[1]) || invalid(parts[2])) {
                throw new IllegalStateException(
                        "Invalid Maven project coordinate: "
                                + coordinate + "; pom=" + pom);
            }
            final String key = parts[0] + ":" + parts[1];
            final Path previous = coordinates.putIfAbsent(key, pom);
            if (previous != null) {
                throw new IllegalStateException(
                        "Duplicate version-independent module coordinate: "
                                + key + "; " + previous + ", " + pom);
            }
        }
    }

    private boolean invalid(final String value) {
        return value.isBlank() || "?".equals(value)
                || value.contains("${");
    }

    /**
     * One matching ancestor aggregator and its active closure.
     *
     * @param rootPom ancestor aggregator POM
     * @param activePoms complete active closure
     */
    private record OwnedScope(Path rootPom, List<Path> activePoms) {
    }

    /** Bounded POM graph resolver with per-path parse caching. */
    private static final class Resolver {

        /** Real Git checkout root. */
        private final Path root;

        /** Secure POM parser. */
        private final SafePomParser parser;

        /** Parsed descriptors by normalized repository-relative path. */
        private final Map<Path, PomDescriptor> descriptors =
                new LinkedHashMap<>();

        Resolver(final Path repositoryRoot, final SafePomParser pomParser) {
            root = repositoryRoot;
            parser = pomParser;
        }

        Map<Path, PomDescriptor> descriptors() {
            return Map.copyOf(descriptors);
        }

        PomDescriptor requiredPom(final Path relativePom) throws Exception {
            final Path path = relativePom.normalize();
            final PomDescriptor cached = descriptors.get(path);
            if (cached != null) {
                return cached;
            }
            requireEligiblePom(path);
            final PomDescriptor parsed = parser.parse(root, path);
            descriptors.put(path, parsed);
            return parsed;
        }

        List<Path> activeClosure(final Path rootPom) throws Exception {
            final Set<Path> visiting = new LinkedHashSet<>();
            final Set<Path> visited = new LinkedHashSet<>();
            visit(rootPom.normalize(), visiting, visited);
            return visited.stream().sorted(
                    Comparator.comparing(Path::toString)).toList();
        }

        private void visit(
                final Path pom,
            final Set<Path> visiting,
            final Set<Path> visited) throws Exception {
            if (visited.contains(pom)) {
                throw new IllegalStateException(
                        "Duplicate active Maven module path: " + pom);
            }
            if (!visiting.add(pom)) {
                throw new IllegalStateException(
                        "Active Maven module cycle: " + visiting
                                + " -> " + pom);
            }
            final PomDescriptor descriptor = requiredPom(pom);
            for (String module : descriptor.getActiveModules()) {
                visit(resolveModule(pom, module), visiting, visited);
            }
            visiting.remove(pom);
            visited.add(pom);
        }

        private Path resolveModule(final Path ownerPom, final String module) {
            final Path parent = ownerPom.getParent() == null
                    ? Path.of("") : ownerPom.getParent();
            final Path modulePath;
            try {
                modulePath = parent.resolve(module).normalize();
            } catch (RuntimeException exception) {
                throw new IllegalStateException(
                        "Invalid Maven module path: " + module, exception);
            }
            if (modulePath.isAbsolute() || modulePath.startsWith("..")) {
                throw new IllegalStateException(
                        "Maven module leaves Git root: " + module
                                + "; owner=" + ownerPom);
            }
            if (modulePath.getFileName() != null
                    && "pom.xml".equals(modulePath.getFileName().toString())) {
                return modulePath;
            }
            return modulePath.resolve("pom.xml").normalize();
        }

        boolean isEligiblePom(final Path relativePom) {
            try {
                requireEligiblePom(relativePom);
                return true;
            } catch (Exception exception) {
                return false;
            }
        }

        private void requireEligiblePom(final Path relativePom)
                throws IOException, InterruptedException {
            if (relativePom.isAbsolute() || relativePom.startsWith("..")) {
                throw new IOException(
                        "POM leaves Git root: " + relativePom);
            }
            final Path pom = root.resolve(relativePom).normalize();
            if (!Files.isRegularFile(pom)
                    || !Files.isReadable(pom)) {
                throw new IOException(
                        "Active Maven POM is unavailable: " + relativePom);
            }
            final Path real = pom.toRealPath().normalize();
            if (!real.startsWith(root)) {
                throw new IOException(
                        "POM resolves outside Git root: " + relativePom);
            }
            final Path realRelative = root.relativize(real);
            if (insideSubmodule(relativePom)
                    || (!realRelative.equals(relativePom)
                    && insideSubmodule(realRelative))) {
                throw new IOException(
                        "Maven POM is inside a Git submodule: " + relativePom);
            }
            if (isIgnored(relativePom)
                    || (!realRelative.equals(relativePom)
                    && isIgnored(realRelative))) {
                throw new IOException(
                        "Maven POM is ignored by Git: " + relativePom);
            }
        }

        private boolean isIgnored(final Path relativePom)
                throws IOException, InterruptedException {
            final int exit = runExit(List.of("git", "check-ignore", "-q",
                    "--", relativePom.toString()));
            if (exit == 0) {
                return true;
            }
            if (exit == 1) {
                return false;
            }
            throw new IOException(
                    "Git ignore check failed for " + relativePom);
        }

        private boolean insideSubmodule(final Path relativePom)
                throws IOException, InterruptedException {
            Path candidate = relativePom.getParent();
            while (candidate != null && !candidate.toString().isEmpty()) {
                final String output = run(List.of("git", "ls-files",
                        "--stage", "-z", "--", candidate.toString()));
                for (String line : output.split("\\x00", -1)) {
                    if (line.startsWith("160000 ")) {
                        return true;
                    }
                }
                candidate = candidate.getParent();
            }
            return false;
        }

        private String run(final List<String> command)
                throws IOException, InterruptedException {
            final Process process = new ProcessBuilder(
                    CommandResolver.resolve(command))
                    .directory(root.toFile())
                    .redirectErrorStream(true).start();
            final String output = new String(
                    process.getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            if (process.waitFor() != 0) {
                throw new IOException("Git command failed: " + output.trim());
            }
            return output;
        }

        private int runExit(final List<String> command)
                throws IOException, InterruptedException {
            final Process process = new ProcessBuilder(
                    CommandResolver.resolve(command))
                    .directory(root.toFile())
                    .redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            return process.waitFor();
        }
    }
}
