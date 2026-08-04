package io.github.dependencyanalysis.tree;

/** Lightweight checkpoint data for one published reactor page. */
public final class ReactorReportSummary {

    /** Published filename. */
    private final String filename;

    /** Reactor identifier. */
    private final String id;

    /** Reactor coordinate. */
    private final String coordinate;

    /** Reactor status. */
    private final ReactorStatus status;

    /** Status reason. */
    private final String reason;

    /** Module count. */
    private final int moduleCount;

    /** Dependency occurrence count. */
    private final long dependencyCount;

    /** Internal module conflict count. */
    private final long internalConflictCount;

    /** Cross-module conflict count. */
    private final long crossModuleConflictCount;

    private ReactorReportSummary(
            final String pageFilename,
            final ReactorTreeResult result) {
        filename = pageFilename;
        id = result.getReactor().getId();
        coordinate = result.getReactor().getCoordinate();
        status = result.getStatus();
        reason = result.getReason();
        moduleCount = result.getModules().size();
        dependencyCount = result.getModules().stream()
                .mapToLong(item -> item.getOccurrences()
                        .size()).sum();
        internalConflictCount = result.getModules().stream()
                .mapToLong(item -> new ModuleVersionAnalyzer()
                        .analyze(item).size()).sum();
        crossModuleConflictCount = new CrossModuleVersionAnalyzer()
                .analyze(result).size();
    }

    /**
     * Summarizes a complete reactor result without retaining it.
     *
     * @param pageFilename filename
     * @param result reactor result
     * @return lightweight summary
     */
    static ReactorReportSummary from(
            final String pageFilename,
            final ReactorTreeResult result) {
        return new ReactorReportSummary(pageFilename,
                result);
    }

    /** @return filename */
    public String getFilename() {
        return filename;
    }

    /** @return reactor id */
    public String getId() {
        return id;
    }

    /** @return coordinate */
    public String getCoordinate() {
        return coordinate;
    }

    /** @return status */
    public ReactorStatus getStatus() {
        return status;
    }

    /** @return reason */
    public String getReason() {
        return reason;
    }

    /** @return module count */
    public int getModuleCount() {
        return moduleCount;
    }

    /** @return dependency count */
    public long getDependencyCount() {
        return dependencyCount;
    }

    /** @return internal module conflict count */
    public long getInternalConflictCount() {
        return internalConflictCount;
    }

    /** @return cross-module conflict count */
    public long getCrossModuleConflictCount() {
        return crossModuleConflictCount;
    }

}
