package io.github.dependencyanalysis.tree;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Repository-level Maven reactor inventory. */
public final class RepositoryInventory {

    /** Reactors. */
    private final List<ReactorDescriptor> reactors;

    /** POM models by normalized relative path. */
    private final Map<java.nio.file.Path,
            PomDescriptor> poms;

    /** Analysis directory relative to Git root. */
    private final Path analysisPath;

    /**
     * Creates an inventory.
     *
     * @param inventoryReactors reactors
     * @param pomDescriptors parsed POMs
     */
    RepositoryInventory(
            final List<ReactorDescriptor>
                    inventoryReactors,
            final Map<java.nio.file.Path,
                    PomDescriptor> pomDescriptors) {
        this(inventoryReactors, pomDescriptors,
                Path.of(""));
    }

    /**
     * Creates an inventory.
     *
     * @param inventoryReactors selected reactors
     * @param pomDescriptors all repository POMs
     * @param relativeAnalysisPath analysis directory relative to Git root
     */
    RepositoryInventory(
            final List<ReactorDescriptor>
                    inventoryReactors,
            final Map<java.nio.file.Path,
                    PomDescriptor> pomDescriptors,
            final Path relativeAnalysisPath) {
        reactors = List.copyOf(inventoryReactors);
        poms = Map.copyOf(pomDescriptors);
        analysisPath = Objects.requireNonNull(
                relativeAnalysisPath,
                "relativeAnalysisPath").normalize();
    }

    /** @return deterministic reactors */
    public List<ReactorDescriptor> getReactors() {
        return reactors;
    }

    /** @return analysis directory relative to Git root */
    public Path getAnalysisPath() {
        return analysisPath;
    }

    /**
     * Returns coordinate for one POM.
     *
     * @param pom relative POM
     * @return coordinate
     */
    public String coordinateOf(
            final java.nio.file.Path pom) {
        return poms.get(pom).getCoordinate();
    }

    /**
     * Returns packaging for one POM.
     *
     * @param pom relative POM
     * @return Maven packaging
     */
    String packagingOf(
            final java.nio.file.Path pom) {
        return poms.get(pom).getPackaging();
    }

    /**
     * Filters POMs eligible for parser and Report analysis.
     *
     * <p>A multi-project {@code pom}-packaging root remains in the
     * reactor inventory and Maven execution, but is not itself an
     * analyzed module.</p>
     *
     * @param reactor owning reactor
     * @param candidates candidate POMs
     * @return analysis-eligible POMs in candidate order
     */
    List<Path> analysisPoms(
            final ReactorDescriptor reactor,
            final List<Path> candidates) {
        return candidates.stream()
                .filter(pom -> !isPureAggregatorRoot(
                        reactor, pom))
                .toList();
    }

    private boolean isPureAggregatorRoot(
            final ReactorDescriptor reactor,
            final Path pom) {
        return pom.equals(reactor.getRootPom())
                && "pom".equals(packagingOf(
                        reactor.getRootPom()))
                && reactor.getActivePoms().stream()
                .anyMatch(active -> !active.equals(
                        reactor.getRootPom()));
    }
}
