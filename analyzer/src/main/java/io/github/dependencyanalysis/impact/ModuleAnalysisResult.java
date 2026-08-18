package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.impact.pruning.ImpactPathPruningSummary;

import io.github.dependencyanalysis.callgraph.engine.CallGraphStats;
import io.github.dependencyanalysis.classpath.ClassConflictResolution;
import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphSession;

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

    /** Live session retained through report snapshotting. */
    private final ModuleCallGraphSession session;

    /** Lightweight report metrics detached from the live session. */
    private final ModuleCallGraphSnapshot callGraphSnapshot;

    /** Frozen post-graph terminal evidence. */
    private final ChangePointEvidenceIndex changePointEvidence;

    /** Reportable Impact Paths. */
    private final List<ImpactPath> impactPaths;

    /** Structural Reference Paths. */
    private final List<StructuralReferencePath> structuralPaths;

    /** ChangePoint dispositions. */
    private final Map<BoundChangePoint, ChangePointDisposition> dispositions;

    /** Typed access/reference observations. */
    private final Map<BoundChangePoint, List<ImpactEvidence>> observations;

    /** Query-time fixed Impact Path pruning summary. */
    private final ImpactPathPruningSummary impactPathPruning;

    /** User-reviewable code comparisons by relevant changed member. */
    private final Map<BoundChangePoint,
            CodeComparisonEvidence> codeComparisons;

    /** Deterministic class conflict resolution evidence. */
    private final List<ClassConflictResolution> classConflictResolutions;

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
        changePointEvidence = builder.changePointEvidence;
        impactPaths = immutable(builder.impactPaths);
        structuralPaths = immutable(builder.structuralPaths);
        dispositions = immutableMap(builder.dispositions);
        final Map<BoundChangePoint, List<ImpactEvidence>> evidence =
                new LinkedHashMap<>();
        builder.observations.forEach((point, values) ->
                evidence.put(point, immutable(values)));
        observations = immutableMap(evidence);
        impactPathPruning = Objects.requireNonNull(
                builder.impactPathPruning, "impactPathPruning");
        codeComparisons = immutableMap(builder.codeComparisons);
        classConflictResolutions = immutable(
                builder.classConflictResolutions);
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

    /** @return frozen terminal evidence, nullable for unbuilt modules */
    public ChangePointEvidenceIndex getChangePointEvidence() {
        return changePointEvidence;
    }

    /** @return reportable Impact Paths */
    public List<ImpactPath> getImpactPaths() {
        return impactPaths;
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

    /** @return query-time fixed Impact Path pruning summary */
    public ImpactPathPruningSummary getImpactPathPruning() {
        return impactPathPruning;
    }

    /** @return path-associated code comparison evidence */
    public Map<BoundChangePoint, CodeComparisonEvidence>
            getCodeComparisons() {
        return codeComparisons;
    }

    /** @return deterministic class conflict resolutions */
    public List<ClassConflictResolution> getClassConflictResolutions() {
        return classConflictResolutions;
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
        return callGraphSnapshot == null ? null : callGraphSnapshot.stats();
    }

    /** @return mutable builder initialized from this result */
    public Builder toBuilder() {
        return new Builder(unit)
                .status(status, reason, detail)
                .session(session)
                .callGraphSnapshot(callGraphSnapshot)
                .changePointEvidence(changePointEvidence)
                .impactPaths(impactPaths)
                .structuralPaths(structuralPaths)
                .dispositions(dispositions)
                .observations(observations)
                .impactPathPruning(impactPathPruning)
                .codeComparisons(codeComparisons)
                .classConflictResolutions(classConflictResolutions)
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

        /** Frozen terminal evidence. */
        private ChangePointEvidenceIndex changePointEvidence;

        /** Reportable Impact Paths. */
        private List<ImpactPath> impactPaths = List.of();

        /** Structural Reference Paths. */
        private List<StructuralReferencePath> structuralPaths = List.of();

        /** Dispositions. */
        private Map<BoundChangePoint, ChangePointDisposition> dispositions =
                Map.of();

        /** Typed observations. */
        private Map<BoundChangePoint, List<ImpactEvidence>> observations =
                Map.of();

        /** Query-time fixed Impact Path pruning summary. */
        private ImpactPathPruningSummary impactPathPruning =
                ImpactPathPruningSummary.notExecuted();

        /** Code comparisons. */
        private Map<BoundChangePoint, CodeComparisonEvidence>
                codeComparisons = Map.of();

        /** Class conflict resolution evidence. */
        private List<ClassConflictResolution> classConflictResolutions =
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
         * @param value frozen terminal evidence
         * @return this builder
         */
        public Builder changePointEvidence(
                final ChangePointEvidenceIndex value) {
            changePointEvidence = value;
            return this;
        }

        /**
         * @param values paths
         * @return this builder
         */
        public Builder impactPaths(final List<ImpactPath> values) {
            impactPaths = List.copyOf(values);
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
         * @param value query-time fixed Impact Path pruning summary
         * @return this builder
         */
        public Builder impactPathPruning(
                final ImpactPathPruningSummary value) {
            impactPathPruning = Objects.requireNonNull(value, "value");
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
         * @param values deterministic class conflict resolutions
         * @return this builder
         */
        public Builder classConflictResolutions(
                final List<ClassConflictResolution> values) {
            classConflictResolutions = List.copyOf(values);
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
