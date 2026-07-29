package io.github.dependencyanalysis.tree;

import java.nio.file.Path;
import java.util.List;

// Wiki: wiki/features/repository-dependency-tree-report.md - reactor scope
/** Deterministic Maven reactor inventory entry. */
public final class ReactorDescriptor {

    /** Root POM path relative to repository. */
    private final Path rootPom;

    /** Root project coordinate. */
    private final String coordinate;

    /** Active POM paths, including root. */
    private final List<Path> activePoms;

    /** Active POM paths directly requested by the user path. */
    private final List<Path> requestedPoms;

    /** Whether the user path directly selected the reactor root. */
    private final boolean rootSelected;

    /** Discovery violations. */
    private final List<String> violations;

    /**
     * Creates a reactor descriptor.
     *
     * @param pom root POM
     * @param projectCoordinate coordinate
     * @param poms active POMs
     * @param problems boundary or model problems
     */
    ReactorDescriptor(
            final Path pom,
            final String projectCoordinate,
            final List<Path> poms,
            final List<String> problems) {
        this(pom, projectCoordinate, poms, poms,
                problems);
    }

    /**
     * Creates a scoped reactor descriptor.
     *
     * @param pom root POM
     * @param projectCoordinate coordinate
     * @param poms complete active reactor POMs
     * @param selectedPoms active POMs directly requested by user path
     * @param problems boundary or model problems
     */
    ReactorDescriptor(
            final Path pom,
            final String projectCoordinate,
            final List<Path> poms,
            final List<Path> selectedPoms,
            final List<String> problems) {
        rootPom = pom;
        coordinate = projectCoordinate;
        activePoms = List.copyOf(poms);
        requestedPoms = List.copyOf(selectedPoms);
        rootSelected = requestedPoms.contains(rootPom);
        violations = List.copyOf(problems);
    }

    /** @return root POM */
    public Path getRootPom() {
        return rootPom;
    }

    /** @return root coordinate */
    public String getCoordinate() {
        return coordinate;
    }

    /** @return active POMs */
    public List<Path> getActivePoms() {
        return activePoms;
    }

    /** @return active POMs directly requested by user path */
    public List<Path> getRequestedPoms() {
        return requestedPoms;
    }

    /** @return whether the user path directly selected the reactor root */
    public boolean isRootSelected() {
        return rootSelected;
    }

    /** @return discovery violations */
    public List<String> getViolations() {
        return violations;
    }

    /** @return stable reactor id */
    public String getId() {
        return rootPom.toString()
                .replace('\\', '/');
    }
}
