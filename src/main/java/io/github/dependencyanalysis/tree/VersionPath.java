package io.github.dependencyanalysis.tree;

import java.util.List;

/** One distinct path in module mediation output. */
public final class VersionPath {

    /** Complete path. */
    private final List<String> path;

    /** Version evidence in source order. */
    private final List<VersionEvidence> evidence;

    /** Maven conflict resolution result for this occurrence. */
    private final String resolvedVersion;

    /** Effective scope for this occurrence. */
    private final String scope;

    /** Selected flag. */
    private final boolean selected;

    /** Omitted reason. */
    private final String reason;

    /**
     * Creates a version path.
     *
     * @param dependencyPath complete path
     * @param requested requested version
     * @param isSelected selected flag
     * @param omittedReason omitted reason
     */
    public VersionPath(
            final List<String> dependencyPath,
            final String requested,
            final boolean isSelected,
            final String omittedReason) {
        this(dependencyPath, List.of(
                        new VersionEvidence(
                                VersionEvidenceSource
                                        .DEPENDENCY_PATH,
                                requested)),
                requested, "", isSelected, omittedReason);
    }

    /**
     * Creates a version path with complete source evidence.
     *
     * @param dependencyPath complete path
     * @param versionEvidence source evidence
     * @param isSelected selected flag
     * @param omittedReason omitted reason
     */
    public VersionPath(
            final List<String> dependencyPath,
            final List<VersionEvidence> versionEvidence,
            final boolean isSelected,
            final String omittedReason) {
        this(dependencyPath, versionEvidence,
                dependencyPathVersion(versionEvidence), "",
                isSelected, omittedReason);
    }

    /**
     * Creates a version path with occurrence-level evidence.
     *
     * @param dependencyPath complete path
     * @param versionEvidence source evidence
     * @param resolved resolved version
     * @param effectiveScope effective scope
     * @param isSelected selected flag
     * @param omittedReason omitted reason
     */
    public VersionPath(
            final List<String> dependencyPath,
            final List<VersionEvidence> versionEvidence,
            final String resolved,
            final String effectiveScope,
            final boolean isSelected,
            final String omittedReason) {
        path = List.copyOf(dependencyPath);
        evidence = List.copyOf(versionEvidence);
        resolvedVersion = resolved;
        scope = effectiveScope;
        selected = isSelected;
        reason = omittedReason;
    }

    static VersionPath from(
            final DependencyOccurrence occurrence) {
        final List<VersionEvidence> values =
                new java.util.ArrayList<>();
        values.add(new VersionEvidence(
                VersionEvidenceSource.DEPENDENCY_PATH,
                occurrence.getRequestedVersion()));
        if (!occurrence.getManagedFromVersion().isBlank()) {
            values.add(new VersionEvidence(
                    VersionEvidenceSource
                            .DEPENDENCY_MANAGEMENT,
                    occurrence.getEffectiveVersion()));
        }
        return new VersionPath(occurrence.getPath(),
                values, occurrence.getSelectedVersion(),
                occurrence.getEffectiveScope(),
                occurrence.isSelected(),
                occurrence.getOmittedReason());
    }

    /** @return complete path */
    public List<String> getPath() {
        return path;
    }

    /** @return requested version */
    public String getRequestedVersion() {
        return evidence.stream()
                .filter(item -> item.getSource()
                        == VersionEvidenceSource
                        .DEPENDENCY_PATH)
                .map(VersionEvidence::getVersion)
                .findFirst().orElse("");
    }

    /** @return complete version source evidence */
    public List<VersionEvidence> getEvidence() {
        return evidence;
    }

    /** @return resolved version for this occurrence */
    public String getResolvedVersion() {
        return resolvedVersion;
    }

    /** @return effective scope for this occurrence */
    public String getScope() {
        return scope;
    }

    /** @return selected flag */
    public boolean isSelected() {
        return selected;
    }

    /** @return omitted reason */
    public String getReason() {
        return reason;
    }

    String stableKey() {
        final String sources = evidence.stream()
                .map(item -> item.getSource() + "="
                        + item.getVersion())
                .collect(java.util.stream.Collectors
                        .joining(","));
        return String.join(" -> ", path)
                + "|" + sources
                + "|" + getRequestedVersion()
                + "|" + resolvedVersion
                + "|" + scope
                + "|" + selected
                + "|" + reason;
    }

    private static String dependencyPathVersion(
            final List<VersionEvidence> versionEvidence) {
        return versionEvidence.stream()
                .filter(item -> item.getSource()
                        == VersionEvidenceSource
                        .DEPENDENCY_PATH)
                .map(VersionEvidence::getVersion)
                .findFirst().orElse("");
    }
}
