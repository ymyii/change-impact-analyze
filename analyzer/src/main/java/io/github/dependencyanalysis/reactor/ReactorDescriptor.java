package io.github.dependencyanalysis.reactor;

import java.nio.file.Path;
import java.util.List;

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

    /** Resolved command scope mode. */
    private final ReactorScopeMode scopeMode;

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
    public ReactorDescriptor(
            final Path pom,
            final String projectCoordinate,
            final List<Path> poms,
            final List<String> problems) {
        this(pom, projectCoordinate, poms, poms,
                ReactorScopeMode.FULL_REACTOR, problems);
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
    public ReactorDescriptor(
            final Path pom,
            final String projectCoordinate,
            final List<Path> poms,
            final List<Path> selectedPoms,
            final List<String> problems) {
        this(pom, projectCoordinate, poms, selectedPoms,
                selectedPoms.contains(pom)
                        ? ReactorScopeMode.FULL_REACTOR
                        : ReactorScopeMode.SINGLE_MODULE,
                problems);
    }

    /**
     * Creates a descriptor with an explicit scope mode.
     *
     * @param pom root POM
     * @param projectCoordinate root coordinate
     * @param poms complete active reactor POMs
     * @param selectedPoms directly requested POMs
     * @param mode resolved scope mode
     * @param problems discovery violations
     */
    public ReactorDescriptor(
            final Path pom,
            final String projectCoordinate,
            final List<Path> poms,
            final List<Path> selectedPoms,
            final ReactorScopeMode mode,
            final List<String> problems) {
        rootPom = pom;
        coordinate = projectCoordinate;
        activePoms = List.copyOf(poms);
        requestedPoms = List.copyOf(selectedPoms);
        scopeMode = mode;
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

    /** @return resolved scope mode */
    public ReactorScopeMode getScopeMode() {
        return scopeMode;
    }

    /** @return whether Maven needs {@code -pl/-am} */
    public boolean requiresProjectSelection() {
        return scopeMode == ReactorScopeMode.SINGLE_MODULE;
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
