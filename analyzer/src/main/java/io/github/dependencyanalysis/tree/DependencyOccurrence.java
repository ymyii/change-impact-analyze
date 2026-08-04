package io.github.dependencyanalysis.tree;

import java.util.List;
import java.util.Objects;

/** One selected or omitted dependency path occurrence. */
public final class DependencyOccurrence {

    /** Conflict key. */
    private final DependencyKey key;

    /** Requested version. */
    private final String requestedVersion;

    /** Version before management. */
    private final String managedFromVersion;

    /** Effective node version. */
    private final String effectiveVersion;

    /** Selected version. */
    private final String selectedVersion;

    /** Effective scope. */
    private final String effectiveScope;

    /** Scope before management. */
    private final String managedFromScope;

    /** Optional flag, null if unknown. */
    private final Boolean optional;

    /** Selected flag. */
    private final boolean selected;

    /** Omitted reason. */
    private final String omittedReason;

    /** Complete path coordinates. */
    private final List<String> path;

    /** Reactor module flag. */
    private final boolean reactorModule;

    /**
     * Creates an occurrence.
     *
     * @param dependencyKey key
     * @param versions version fields
     * @param scopes scope fields
     * @param optionalFlag optional flag
     * @param selection selection fields
     * @param dependencyPath complete path
     * @param isReactorModule reactor module flag
     */
    DependencyOccurrence(
            final DependencyKey dependencyKey,
            final OccurrenceVersions versions,
            final OccurrenceScopes scopes,
            final Boolean optionalFlag,
            final OccurrenceSelection selection,
            final List<String> dependencyPath,
            final boolean isReactorModule) {
        key = Objects.requireNonNull(
                dependencyKey, "dependencyKey");
        requestedVersion = versions.requested();
        managedFromVersion = versions.managedFrom();
        effectiveVersion = versions.effective();
        selectedVersion = versions.selected();
        effectiveScope = scopes.effective();
        managedFromScope = scopes.managedFrom();
        optional = optionalFlag;
        selected = selection.selected();
        omittedReason = selection.reason();
        path = List.copyOf(dependencyPath);
        reactorModule = isReactorModule;
    }

    /** @return conflict key */
    public DependencyKey getKey() {
        return key;
    }

    /** @return requested version */
    public String getRequestedVersion() {
        return requestedVersion;
    }

    /** @return version before management */
    public String getManagedFromVersion() {
        return managedFromVersion;
    }

    /** @return effective version */
    public String getEffectiveVersion() {
        return effectiveVersion;
    }

    /** @return selected version */
    public String getSelectedVersion() {
        return selectedVersion;
    }

    /** @return effective scope */
    public String getEffectiveScope() {
        return effectiveScope;
    }

    /** @return scope before management */
    public String getManagedFromScope() {
        return managedFromScope;
    }

    /** @return optional flag, null when unknown */
    public Boolean getOptional() {
        return optional;
    }

    /** @return selected flag */
    public boolean isSelected() {
        return selected;
    }

    /** @return omitted reason */
    public String getOmittedReason() {
        return omittedReason;
    }

    /** @return complete path */
    public List<String> getPath() {
        return path;
    }

    /** @return reactor module flag */
    public boolean isReactorModule() {
        return reactorModule;
    }

    /** @return display coordinate */
    public String coordinate() {
        final String classifier = key.getClassifier()
                .isBlank() ? ""
                : ":" + key.getClassifier();
        return key.getGroupId() + ":"
                + key.getArtifactId() + ":"
                + key.getType() + classifier + ":"
                + effectiveVersion + ":"
                + effectiveScope;
    }
}
