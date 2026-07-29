package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.util
        .CommandResolver;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Wiki: wiki/features/repository-dependency-tree-report.md - inventory
/** Discovers repository POM ownership and active reactors. */
public final class ReactorInventoryBuilder {

    /**
     * Builds repository inventory.
     *
     * @param snapshot repository snapshot
     * @param mavenArguments validated Maven arguments
     * @return deterministic inventory
     * @throws Exception on Git, XML, or model failure
     */
    public RepositoryInventory build(
            final RepositorySnapshot snapshot,
            final List<String> mavenArguments)
            throws Exception {
        final List<Path> pomFiles =
                listPomFiles(snapshot.getRoot());
        final SafePomParser parser =
                new SafePomParser(mavenArguments);
        final Map<Path, PomDescriptor> poms =
                new LinkedHashMap<>();
        for (Path pom : pomFiles) {
            try {
                poms.put(pom, parser.parse(
                        snapshot.getRoot(), pom));
            } catch (Exception exception) {
                poms.put(pom, malformed(pom,
                        exception));
            }
        }
        final Set<Path> owned = new HashSet<>();
        for (PomDescriptor pom : poms.values()) {
            for (String module
                    : pom.getDeclaredModules()) {
                final Path modulePom = resolveModule(
                        pom.getPath(), module);
                if (modulePom != null
                        && poms.containsKey(modulePom)) {
                    owned.add(modulePom);
                }
            }
        }
        final List<ReactorDescriptor> reactors =
                new ArrayList<>();
        for (PomDescriptor pom : poms.values()) {
            if (!owned.contains(pom.getPath())) {
                final ReactorDescriptor reactor =
                        buildReactor(pom, poms,
                                snapshot.getAnalysisPath());
                if (!reactor.getRequestedPoms()
                        .isEmpty()) {
                    reactors.add(reactor);
                }
            }
        }
        reactors.sort(java.util.Comparator
                .comparing(ReactorDescriptor::getId));
        return new RepositoryInventory(
                reactors, poms,
                snapshot.getAnalysisPath());
    }

    private ReactorDescriptor buildReactor(
            final PomDescriptor root,
            final Map<Path, PomDescriptor> poms,
            final Path analysisPath) {
        final List<Path> active =
                new ArrayList<>();
        final List<String> violations =
                new ArrayList<>();
        final Deque<Path> queue =
                new ArrayDeque<>();
        final Set<Path> visited =
                new HashSet<>();
        queue.add(root.getPath());
        while (!queue.isEmpty()) {
            final Path path = queue.removeFirst();
            if (!visited.add(path)) {
                continue;
            }
            active.add(path);
            final PomDescriptor pom = poms.get(path);
            if (pom == null) {
                violations.add("Missing module POM: "
                        + path);
                continue;
            }
            if (!pom.getFailure().isBlank()) {
                violations.add(path + ": "
                        + pom.getFailure());
                continue;
            }
            for (String module
                    : pom.getActiveModules()) {
                final Path modulePom = resolveModule(
                        path, module);
                if (modulePom == null) {
                    violations.add(
                            "Module leaves repository: "
                                    + module);
                } else if (!poms.containsKey(
                        modulePom)) {
                    violations.add(
                            "Active module POM unavailable: "
                                    + modulePom);
                } else {
                    queue.add(modulePom);
                }
            }
        }
        active.sort(java.util.Comparator
                .comparing(Path::toString));
        final List<Path> selected = active.stream()
                .filter(path -> isInsideAnalysisPath(
                        path, analysisPath))
                .toList();
        return new ReactorDescriptor(
                root.getPath(), root.getCoordinate(),
                active, selected, violations);
    }

    private boolean isInsideAnalysisPath(
            final Path pom,
            final Path analysisPath) {
        if (analysisPath.toString().isEmpty()) {
            return true;
        }
        final Path directory = pom.getParent() == null
                ? Path.of("") : pom.getParent();
        return directory.startsWith(analysisPath);
    }

    private PomDescriptor malformed(
            final Path pom,
            final Exception exception) {
        final String message = exception.getMessage()
                == null ? exception.getClass()
                .getSimpleName() : exception.getMessage();
        return new PomDescriptor(pom,
                "invalid:" + pom + ":?", "pom",
                List.of(), List.of(),
                "Malformed POM: " + message);
    }

    private Path resolveModule(
            final Path ownerPom,
            final String module) {
        final Path parent = ownerPom.getParent() == null
                ? Path.of("") : ownerPom.getParent();
        final Path modulePath = parent
                .resolve(module).normalize();
        if (modulePath.isAbsolute()
                || modulePath.startsWith("..")) {
            return null;
        }
        if (modulePath.getFileName() != null
                && modulePath.getFileName()
                .toString().equals("pom.xml")) {
            return modulePath;
        }
        return modulePath.resolve("pom.xml")
                .normalize();
    }

    private List<Path> listPomFiles(
            final Path root)
            throws IOException,
            InterruptedException {
        final String fileOutput = run(root,
                List.of("git", "ls-files",
                        "--cached", "--others",
                        "--exclude-standard", "-z"));
        final Set<Path> submodules =
                submodulePaths(root);
        final List<Path> result =
                new ArrayList<>();
        for (String item : fileOutput.split(
                "\\x00", -1)) {
            if (item.isEmpty()) {
                continue;
            }
            final Path path = Path.of(item)
                    .normalize();
            if (!"pom.xml".equals(path
                    .getFileName().toString())) {
                continue;
            }
            if (insideSubmodule(path, submodules)) {
                continue;
            }
            if (Files.isRegularFile(root.resolve(path),
                    LinkOption.NOFOLLOW_LINKS)) {
                result.add(path);
            }
        }
        result.sort(java.util.Comparator
                .comparing(Path::toString));
        return result;
    }

    private Set<Path> submodulePaths(
            final Path root)
            throws IOException,
            InterruptedException {
        final String output = run(root,
                List.of("git", "ls-files",
                        "--stage", "-z"));
        final Set<Path> result =
                new HashSet<>();
        for (String line : output.split(
                "\\x00", -1)) {
            if (line.startsWith("160000 ")) {
                final int tab = line.indexOf('\t');
                if (tab >= 0) {
                    result.add(Path.of(
                            line.substring(tab + 1))
                            .normalize());
                }
            }
        }
        return result;
    }

    private boolean insideSubmodule(
            final Path file,
            final Set<Path> submodules) {
        for (Path submodule : submodules) {
            if (file.startsWith(submodule)) {
                return true;
            }
        }
        return false;
    }

    private String run(
            final Path root,
            final List<String> command)
            throws IOException,
            InterruptedException {
        final Process process =
                new ProcessBuilder(
                        CommandResolver.resolve(command))
                        .directory(root.toFile())
                        .redirectErrorStream(true)
                        .start();
        final String output = new String(
                process.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new IOException(
                    "Git file discovery failed: "
                            + output.trim());
        }
        return output;
    }
}
