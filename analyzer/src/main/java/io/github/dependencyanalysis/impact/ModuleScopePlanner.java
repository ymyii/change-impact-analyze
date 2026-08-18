package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.reactor.ReactorDescriptor;
import io.github.dependencyanalysis.reactor.ReactorInventoryBuilder;
import io.github.dependencyanalysis.reactor.ReactorScopeMode;
import io.github.dependencyanalysis.reactor.RepositoryInventory;
import io.github.dependencyanalysis.reactor.MavenActivationContext;
import io.github.dependencyanalysis.util.CommandResolver;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Wiki: wiki/architecture/dependency-analysis-pipelines.md - Impact scope
/** Resolves reactor-root and leaf-module execution modes. */
final class ModuleScopePlanner {

    /** Minimum coordinate segments produced by the safe POM parser. */
    private static final int COORDINATE_SEGMENTS = 3;

    /**
     * Resolves the requested workspace path to one reactor scope.
     *
     * @param requestedPath workspace path prepared for one Git side
     * @param mavenArguments validated Maven arguments
     * @return resolved reactor scope
     * @throws Exception on Git or POM inventory failure
     */
    ReactorAnalysisScope plan(
            final Path requestedPath,
            final List<String> mavenArguments) throws Exception {
        return plan(requestedPath, MavenActivationContext.resolve(
                mavenArguments, System.getProperty("java.version", "")));
    }

    /**
     * Resolves one workspace path using shared Maven activation inputs.
     *
     * @param requestedPath workspace path prepared for one Git side
     * @param activation Maven activation inputs
     * @return resolved reactor scope
     * @throws Exception on Git or POM scope failure
     */
    ReactorAnalysisScope plan(
            final Path requestedPath,
            final MavenActivationContext activation) throws Exception {
        final Path repositoryRoot = gitRoot(requestedPath);
        final Path relative = repositoryRoot.relativize(
                requestedPath.toRealPath());
        final RepositoryInventory inventory =
                new ReactorInventoryBuilder().build(
                        repositoryRoot, relative, activation);
        final Path requestedPom = relative.resolve("pom.xml").normalize();
        final ReactorDescriptor reactor = selectReactor(
                inventory, requestedPom);
        if (!reactor.getViolations().isEmpty()) {
            throw new IllegalStateException(
                    "Maven reactor inventory is invalid: "
                            + reactor.getViolations());
        }
        final boolean reactorMode = reactor.getScopeMode()
                == ReactorScopeMode.FULL_REACTOR;
        final AnalysisMode mode = reactorMode
                ? AnalysisMode.REACTOR : AnalysisMode.SINGLE_MODULE;
        final List<Path> selectedPoms = reactorMode
                ? inventory.analysisPoms(
                        reactor, reactor.getActivePoms())
                : inventory.analysisPoms(reactor, List.of(requestedPom));
        final Path rootRelative = parentOf(reactor.getRootPom());
        final Path reactorRoot = repositoryRoot.resolve(rootRelative)
                .toAbsolutePath().normalize();
        final List<ModuleId> modules = moduleIds(
                inventory, selectedPoms, rootRelative);
        final List<ModuleId> allModules = moduleIds(inventory,
                inventory.analysisPoms(
                        reactor, reactor.getActivePoms()), rootRelative);
        final Set<ArtifactCoord> reactorCoordinates =
                new LinkedHashSet<>(allModules.stream()
                        .map(ModuleId::getCoordinate).toList());
        final List<String> projectArguments;
        if (reactor.requiresProjectSelection()) {
            if (modules.size() != 1) {
                throw new IllegalStateException(
                        "Leaf scope must resolve exactly one module: "
                                + modules);
            }
            projectArguments = List.of("-pl",
                    modules.get(0).getRelativePath().toString(), "-am");
        } else {
            projectArguments = List.of();
        }
        return new ReactorAnalysisScope(mode, repositoryRoot,
                reactorRoot, modules, allModules, reactorCoordinates,
                projectArguments);
    }

    private ReactorDescriptor selectReactor(
            final RepositoryInventory inventory,
            final Path requestedPom) {
        if (inventory.getReactors().size() != 1) {
            throw new IllegalStateException(
                    "Requested POM must resolve exactly one Maven scope: "
                            + requestedPom + "; scopes="
                            + inventory.getReactors().size());
        }
        final ReactorDescriptor result = inventory.getReactors().get(0);
        if (!result.getActivePoms().contains(requestedPom)) {
            throw new IllegalStateException(
                    "Requested POM is absent from resolved Maven scope: "
                            + requestedPom);
        }
        return result;
    }

    private List<ModuleId> moduleIds(
            final RepositoryInventory inventory,
            final List<Path> poms,
            final Path reactorRoot) {
        final List<ModuleId> result = new ArrayList<>();
        final Map<String, ModuleId> keys = new HashMap<>();
        for (Path pom : poms) {
            final ArtifactCoord coordinate = coordinate(
                    inventory.coordinateOf(pom),
                    inventory.packagingOf(pom));
            final ModuleId module = new ModuleId(coordinate,
                    reactorRoot.relativize(parentOf(pom)));
            final ModuleId conflict = keys.putIfAbsent(
                    module.coordinateKey(), module);
            if (conflict != null) {
                throw new IllegalStateException(
                        "Duplicate version-independent module coordinate: "
                                + module.coordinateKey() + "; "
                                + conflict.getRelativePath() + ", "
                                + module.getRelativePath());
            }
            result.add(module);
        }
        result.sort(ModuleId::compareTo);
        return List.copyOf(result);
    }

    private ArtifactCoord coordinate(
            final String value, final String packaging) {
        final String[] parts = value.split(":", -1);
        if (parts.length != COORDINATE_SEGMENTS) {
            throw new IllegalStateException(
                    "Invalid module coordinate: " + value);
        }
        return new ArtifactCoord(parts[0], parts[1],
                packaging, parts[2]);
    }

    private Path parentOf(final Path pom) {
        return pom.getParent() == null ? Path.of("") : pom.getParent();
    }

    private Path gitRoot(final Path requestedPath) throws Exception {
        final Process process = new ProcessBuilder(
                CommandResolver.resolve(List.of("git", "rev-parse",
                        "--show-toplevel")))
                .directory(requestedPath.toFile())
                .redirectErrorStream(true)
                .start();
        final String output = new String(process.getInputStream()
                .readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.waitFor() != 0) {
            throw new IllegalStateException(
                    "Unable to resolve checkout root: " + output);
        }
        return Path.of(output).toRealPath();
    }
}
