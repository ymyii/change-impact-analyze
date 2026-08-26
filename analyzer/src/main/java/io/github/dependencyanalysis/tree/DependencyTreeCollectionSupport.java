package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.reactor.ReactorDescriptor;
import io.github.dependencyanalysis.reactor.ReactorScopeMode;
import io.github.dependencyanalysis.reactor.RepositoryInventory;
import io.github.dependencyanalysis.runtime.MavenDependencyPluginRuntime;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Canonical Maven dependency tree collection primitives. */
final class DependencyTreeCollectionSupport {

    private DependencyTreeCollectionSupport() {
    }

    /** @return unique tool-owned dependency output filename */
    static String outputName() {
        return "dependency-analyzer-" + UUID.randomUUID() + ".txt";
    }

    /**
     * Resolves dependency output files for all active Modules.
     *
     * @param snapshot repository snapshot
     * @param reactor Reactor descriptor
     * @param outputName output filename
     * @return POM to output path
     */
    static Map<Path, Path> outputs(
            final RepositorySnapshot snapshot,
            final ReactorDescriptor reactor,
            final String outputName) {
        final Map<Path, Path> result = new LinkedHashMap<>();
        for (Path pom : reactor.getActivePoms()) {
            final Path target = snapshot.getRoot()
                    .resolve(pom).getParent().resolve("target");
            if (!reactor.requiresProjectSelection()) {
                try {
                    Files.createDirectories(target);
                } catch (Exception exception) {
                    throw new IllegalStateException(
                            "Unable to prepare module output: " + pom,
                            exception);
                }
            }
            result.put(pom, target.resolve(outputName));
        }
        return result;
    }

    /**
     * Builds the canonical compile and dependency tree Maven arguments.
     *
     * @param snapshot repository snapshot
     * @param reactor Reactor descriptor
     * @param pluginRuntime prepared Plugin runtime
     * @param outputName dependency output filename
     * @return mutable argument list for adjacent enrichment collectors
     */
    static List<String> arguments(
            final RepositorySnapshot snapshot,
            final ReactorDescriptor reactor,
            final MavenDependencyPluginRuntime pluginRuntime,
            final String outputName) {
        final Path pom = snapshot.getRoot().resolve(reactor.getRootPom());
        final List<String> arguments = new ArrayList<>(
                pluginRuntime.getMavenArguments());
        arguments.add("-B");
        arguments.add("-f");
        arguments.add(pom.toString());
        if (reactor.requiresProjectSelection()) {
            arguments.add("-pl");
            arguments.add(String.join(",", projectSelectors(reactor)));
            arguments.add("-am");
        }
        arguments.add("compile");
        arguments.add(pluginRuntime.getGoal());
        arguments.add("-DoutputFile=" + Path.of("target", outputName));
        arguments.add("-DoutputType=text");
        arguments.add("-DappendOutput=false");
        arguments.add("-Dverbose=true");
        arguments.add("-Dtokens=standard");
        return arguments;
    }

    /**
     * Parses one Module dependency tree output.
     *
     * @param pom repository-relative POM
     * @param role bounded analysis role
     * @param inventory inventory
     * @param output dependency output
     * @param reactorKeys Reactor dependency identities
     * @param scopes selected scopes
     * @param complete complete mediation evidence flag
     * @return Module result
     */
    static ModuleTreeResult parseModule(
            final Path pom,
            final ModuleAnalysisRole role,
            final RepositoryInventory inventory,
            final Path output,
            final Set<DependencyKey> reactorKeys,
            final Set<String> scopes,
            final boolean complete) {
        try {
            if (!Files.isRegularFile(output) || Files.size(output) == 0L) {
                throw new IllegalStateException(
                        "Dependency tree output is unavailable");
            }
            final ParsedModuleTree parsed;
            try (BufferedReader reader = Files.newBufferedReader(
                    output, StandardCharsets.UTF_8)) {
                parsed = new DependencyTextParser().parse(
                        reader, reactorKeys, scopes);
            }
            return new ModuleTreeResult(pom, parsed.getRootCoordinate(),
                    parsed.getOccurrences(), complete, "", role);
        } catch (Exception exception) {
            return new ModuleTreeResult(pom, inventory.coordinateOf(pom),
                    List.of(), false, message(exception), role);
        }
    }

    /**
     * Resolves Modules included in the bounded report scope.
     *
     * @param inventory repository inventory
     * @param reactor Reactor descriptor
     * @return Modules included in the bounded report scope
     */
    static List<Path> analysisPoms(
            final RepositoryInventory inventory,
            final ReactorDescriptor reactor) {
        return inventory.analysisPoms(reactor,
                reactor.getScopeMode() == ReactorScopeMode.FULL_REACTOR
                        ? reactor.getActivePoms()
                        : reactor.getRequestedPoms());
    }

    /**
     * Resolves the role shared by Modules in the bounded report scope.
     *
     * @param reactor Reactor descriptor
     * @return shared analysis role
     */
    static ModuleAnalysisRole analysisRole(
            final ReactorDescriptor reactor) {
        return reactor.getScopeMode() == ReactorScopeMode.FULL_REACTOR
                ? ModuleAnalysisRole.REACTOR_ROOT_SCOPE
                : ModuleAnalysisRole.REQUESTED;
    }

    /**
     * Resolves Reactor dependency identities used by the parser.
     *
     * @param inventory repository inventory
     * @param reactor Reactor descriptor
     * @return Reactor dependency identities
     */
    static Set<DependencyKey> reactorKeys(
            final RepositoryInventory inventory,
            final ReactorDescriptor reactor) {
        final Set<DependencyKey> result = new HashSet<>();
        for (Path pom : reactor.getActivePoms()) {
            final String[] coordinate = inventory.coordinateOf(pom)
                    .split(":", -1);
            if (coordinate.length >= 2) {
                result.add(new DependencyKey(coordinate[0], coordinate[1],
                        inventory.packagingOf(pom), ""));
                result.add(new DependencyKey(coordinate[0], coordinate[1],
                        "jar", ""));
                result.add(new DependencyKey(coordinate[0], coordinate[1],
                        "pom", ""));
            }
        }
        return result;
    }

    /**
     * Removes only Analyzer-owned dependency output files.
     *
     * @param outputs Analyzer-owned outputs
     */
    static void cleanup(final java.util.Collection<Path> outputs) {
        for (Path output : outputs) {
            try {
                Files.deleteIfExists(output);
            } catch (Exception ignored) {
                // Best-effort tool-owned output cleanup.
            }
        }
    }

    /**
     * Produces a stable exception message.
     *
     * @param exception failure
     * @return stable exception message
     */
    static String message(final Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private static List<String> projectSelectors(
            final ReactorDescriptor reactor) {
        final Path rootDirectory = reactor.getRootPom().getParent() == null
                ? Path.of("") : reactor.getRootPom().getParent();
        return reactor.getRequestedPoms().stream()
                .sorted(Comparator.comparing(Path::toString))
                .map(pom -> pom.getParent() == null
                        ? Path.of("") : pom.getParent())
                .map(rootDirectory::relativize)
                .map(Path::toString)
                .toList();
    }
}
