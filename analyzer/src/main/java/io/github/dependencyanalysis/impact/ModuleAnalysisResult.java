package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.CallGraphStats;
import io.github.dependencyanalysis.callgraph.DuplicateClassResolution;
import io.github.dependencyanalysis.callgraph.ModuleCallGraphSession;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Complete result and evidence for one target module. */
public final class ModuleAnalysisResult {

    /** Module input. */
    private final ModuleAnalysisUnit unit;

    /** Module status. */
    private final ModuleAnalysisStatus status;

    /** Structured status reason. */
    private final ModuleAnalysisReason reason;

    /** Human-readable detail. */
    private final String detail;

    /** Live session retained through SSA filtering. */
    private final ModuleCallGraphSession session;

    /** Lightweight report metrics detached from the live session. */
    private final ModuleCallGraphSnapshot callGraphSnapshot;

    /** Candidate paths before SSA filtering. */
    private final List<ImpactPath> candidatePaths;

    /** Final paths after SSA filtering. */
    private final List<ImpactPath> finalPaths;

    /** Structural Reference Paths. */
    private final List<StructuralReferencePath> structuralPaths;

    /** ChangePoint dispositions. */
    private final Map<BoundChangePoint, ChangePointDisposition> dispositions;

    /** Typed access/reference observations. */
    private final Map<BoundChangePoint, List<ImpactEvidence>> observations;

    /** SSA comparison results. */
    private final Map<BoundChangePoint,
            MethodEquivalenceResult> equivalenceResults;

    /** User-reviewable code comparisons by relevant changed member. */
    private final Map<BoundChangePoint,
            CodeComparisonEvidence> codeComparisons;

    /** Deterministic duplicate class resolution evidence. */
    private final List<DuplicateClassResolution> duplicateClassResolutions;

    /** Coverage limitations. */
    private final List<String> limitations;

    /** Total module elapsed time. */
    private final long elapsedMillis;

    /** Stable per-module stage elapsed metrics. */
    private final Map<String, Long> stageElapsedMillis;

    private ModuleAnalysisResult(final Builder builder) {
        unit = Objects.requireNonNull(builder.unit, "unit");
        status = Objects.requireNonNull(builder.status, "status");
        reason = Objects.requireNonNull(builder.reason, "reason");
        detail = Objects.requireNonNull(builder.detail, "detail");
        session = builder.session;
        callGraphSnapshot = builder.callGraphSnapshot;
        candidatePaths = immutable(builder.candidatePaths);
        finalPaths = immutable(builder.finalPaths);
        structuralPaths = immutable(builder.structuralPaths);
        dispositions = immutableMap(builder.dispositions);
        final Map<BoundChangePoint, List<ImpactEvidence>> evidence =
                new LinkedHashMap<>();
        builder.observations.forEach((point, values) ->
                evidence.put(point, immutable(values)));
        observations = immutableMap(evidence);
        equivalenceResults = immutableMap(builder.equivalenceResults);
        codeComparisons = immutableMap(builder.codeComparisons);
        duplicateClassResolutions = immutable(
                builder.duplicateClassResolutions);
        limitations = immutable(builder.limitations);
        elapsedMillis = builder.elapsedMillis;
        stageElapsedMillis = Collections.unmodifiableMap(
                new LinkedHashMap<>(builder.stageElapsedMillis));
    }

    private static <T> List<T> immutable(final List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values));
    }

    private static <K, V> Map<K, V> immutableMap(
            final Map<K, V> values) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /** @return module input */
    public ModuleAnalysisUnit getUnit() {
        return unit;
    }

    /** @return module identity */
    public ModuleId getModuleId() {
        return unit.getModuleId();
    }

    /** @return module status */
    public ModuleAnalysisStatus getStatus() {
        return status;
    }

    /** @return structured status reason */
    public ModuleAnalysisReason getReason() {
        return reason;
    }

    /** @return human-readable detail */
    public String getDetail() {
        return detail;
    }

    /** @return live Call Graph session, nullable */
    public ModuleCallGraphSession getSession() {
        return session;
    }

    /** @return detached Call Graph report metrics, nullable */
    public ModuleCallGraphSnapshot getCallGraphSnapshot() {
        return callGraphSnapshot;
    }

    /** @return pre-filter candidate paths */
    public List<ImpactPath> getCandidatePaths() {
        return candidatePaths;
    }

    /** @return final paths */
    public List<ImpactPath> getFinalPaths() {
        return finalPaths;
    }

    /** @return Structural Reference Paths */
    public List<StructuralReferencePath> getStructuralPaths() {
        return structuralPaths;
    }

    /** @return ChangePoint dispositions */
    public Map<BoundChangePoint, ChangePointDisposition> getDispositions() {
        return dispositions;
    }

    /** @return typed access/reference observations */
    public Map<BoundChangePoint, List<ImpactEvidence>> getObservations() {
        return observations;
    }

    /** @return SSA comparison results */
    public Map<BoundChangePoint, MethodEquivalenceResult>
            getEquivalenceResults() {
        return equivalenceResults;
    }

    /** @return path-associated code comparison evidence */
    public Map<BoundChangePoint, CodeComparisonEvidence>
            getCodeComparisons() {
        return codeComparisons;
    }

    /** @return deterministic conflicting duplicate class resolutions */
    public List<DuplicateClassResolution> getDuplicateClassResolutions() {
        return duplicateClassResolutions;
    }

    /** @return coverage limitations */
    public List<String> getLimitations() {
        return limitations;
    }

    /** @return total module elapsed milliseconds */
    public long getElapsedMillis() {
        return elapsedMillis;
    }

    /** @return stable per-module stage elapsed metrics */
    public Map<String, Long> getStageElapsedMillis() {
        return stageElapsedMillis;
    }

    /** @return Call Graph metrics, nullable */
    public CallGraphStats getCallGraphStats() {
        return session != null ? session.getStats()
                : callGraphSnapshot == null ? null : callGraphSnapshot.stats();
    }

    /** @return mutable builder initialized from this result */
    public Builder toBuilder() {
        return new Builder(unit)
                .status(status, reason, detail)
                .session(session)
                .callGraphSnapshot(callGraphSnapshot)
                .candidatePaths(candidatePaths)
                .finalPaths(finalPaths)
                .structuralPaths(structuralPaths)
                .dispositions(dispositions)
                .observations(observations)
                .equivalenceResults(equivalenceResults)
                .codeComparisons(codeComparisons)
                .duplicateClassResolutions(duplicateClassResolutions)
                .limitations(limitations)
                .elapsedMillis(elapsedMillis)
                .stageElapsedMillis(stageElapsedMillis);
    }

    /** Builder avoids wide public constructors. */
    public static final class Builder {

        /** Module input. */
        private final ModuleAnalysisUnit unit;

        /** Status. */
        private ModuleAnalysisStatus status = ModuleAnalysisStatus.SUCCESS;

        /** Reason. */
        private ModuleAnalysisReason reason = ModuleAnalysisReason.NONE;

        /** Detail. */
        private String detail = "";

        /** Session. */
        private ModuleCallGraphSession session;

        /** Detached Call Graph report metrics. */
        private ModuleCallGraphSnapshot callGraphSnapshot;

        /** Candidate paths. */
        private List<ImpactPath> candidatePaths = List.of();

        /** Final paths. */
        private List<ImpactPath> finalPaths = List.of();

        /** Structural Reference Paths. */
        private List<StructuralReferencePath> structuralPaths = List.of();

        /** Dispositions. */
        private Map<BoundChangePoint, ChangePointDisposition> dispositions =
                Map.of();

        /** Typed observations. */
        private Map<BoundChangePoint, List<ImpactEvidence>> observations =
                Map.of();

        /** Equivalence results. */
        private Map<BoundChangePoint, MethodEquivalenceResult>
                equivalenceResults = Map.of();

        /** Code comparisons. */
        private Map<BoundChangePoint, CodeComparisonEvidence>
                codeComparisons = Map.of();

        /** Duplicate class resolution evidence. */
        private List<DuplicateClassResolution> duplicateClassResolutions =
                List.of();

        /** Limitations. */
        private List<String> limitations = List.of();

        /** Elapsed. */
        private long elapsedMillis;

        /** Per-module stage elapsed metrics. */
        private Map<String, Long> stageElapsedMillis = Map.of();

        /** @param value module input */
        public Builder(final ModuleAnalysisUnit value) {
            unit = Objects.requireNonNull(value, "unit");
        }

        /**
         * Sets status.
         *
         * @param value status
         * @param statusReason reason
         * @param statusDetail detail
         * @return this builder
         */
        public Builder status(
                final ModuleAnalysisStatus value,
                final ModuleAnalysisReason statusReason,
                final String statusDetail) {
            status = value;
            reason = statusReason;
            detail = statusDetail;
            return this;
        }

        /**
         * @param value session
         * @return this builder
         */
        public Builder session(final ModuleCallGraphSession value) {
            session = value;
            return this;
        }

        /**
         * @param value detached Call Graph report metrics
         * @return this builder
         */
        public Builder callGraphSnapshot(final ModuleCallGraphSnapshot value) {
            callGraphSnapshot = value;
            return this;
        }

        /**
         * @param values paths
         * @return this builder
         */
        public Builder candidatePaths(final List<ImpactPath> values) {
            candidatePaths = List.copyOf(values);
            return this;
        }

        /**
         * @param values paths
         * @return this builder
         */
        public Builder finalPaths(final List<ImpactPath> values) {
            finalPaths = List.copyOf(values);
            return this;
        }

        /**
         * @param values structural paths
         * @return this builder
         */
        public Builder structuralPaths(
                final List<StructuralReferencePath> values) {
            structuralPaths = List.copyOf(values);
            return this;
        }

        /**
         * @param values dispositions
         * @return this builder
         */
        public Builder dispositions(
                final Map<BoundChangePoint,
                        ChangePointDisposition> values) {
            dispositions = Map.copyOf(values);
            return this;
        }

        /**
         * @param values typed reference observations
         * @return this builder
         */
        public Builder observations(
                final Map<BoundChangePoint,
                        List<ImpactEvidence>> values) {
            final Map<BoundChangePoint, List<ImpactEvidence>> copy =
                    new LinkedHashMap<>();
            values.forEach((point, evidence) ->
                    copy.put(point, List.copyOf(evidence)));
            observations = Map.copyOf(copy);
            return this;
        }

        /**
         * @param values comparisons
         * @return this builder
         */
        public Builder equivalenceResults(
                final Map<BoundChangePoint,
                        MethodEquivalenceResult> values) {
            equivalenceResults = Map.copyOf(values);
            return this;
        }

        /**
         * @param values path-associated code comparisons
         * @return this builder
         */
        public Builder codeComparisons(
                final Map<BoundChangePoint,
                        CodeComparisonEvidence> values) {
            codeComparisons = Map.copyOf(values);
            return this;
        }

        /**
         * @param values deterministic duplicate class resolutions
         * @return this builder
         */
        public Builder duplicateClassResolutions(
                final List<DuplicateClassResolution> values) {
            duplicateClassResolutions = List.copyOf(values);
            return this;
        }

        /**
         * @param values coverage limitations
         * @return this builder
         */
        public Builder limitations(final List<String> values) {
            limitations = List.copyOf(values);
            return this;
        }

        /**
         * @param value elapsed milliseconds
         * @return this builder
         */
        public Builder elapsedMillis(final long value) {
            elapsedMillis = value;
            return this;
        }

        /**
         * @param values stable per-module stage elapsed metrics
         * @return this builder
         */
        public Builder stageElapsedMillis(
                final Map<String, Long> values) {
            stageElapsedMillis = new LinkedHashMap<>(values);
            return this;
        }

        /** @return immutable result */
        public ModuleAnalysisResult build() {
            return new ModuleAnalysisResult(this);
        }
    }
}
