package io.github.dependencyanalysis.models.jdk;

import com.ibm.wala.types.MethodReference;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Per-hierarchy mutable hit recorder with immutable snapshots. */
public final class JdkModelSession {

    /** Stable model identifier. */
    public static final String MODEL_ID = "jdk";

    /** Available modeled targets. */
    private final Set<MethodReference> available;

    /** Unavailable catalog targets. */
    private final Set<MethodReference> unavailable;

    /** Targets selected while constructing one Call Graph. */
    private final Set<MethodReference> hits = new LinkedHashSet<>();

    JdkModelSession(
            final Collection<MethodReference> availableTargets,
            final Collection<MethodReference> unavailableTargets) {
        available = Set.copyOf(Objects.requireNonNull(
                availableTargets, "availableTargets"));
        unavailable = Set.copyOf(Objects.requireNonNull(
                unavailableTargets, "unavailableTargets"));
    }

    void recordHit(final MethodReference method) {
        if (available.contains(method)) {
            hits.add(method);
        }
    }

    /**
     * Returns a deterministic immutable snapshot.
     *
     * @return current model metadata
     */
    public JdkModelMetadata snapshot() {
        final List<String> availableValues = sorted(available);
        final List<String> unavailableValues = sorted(unavailable);
        final List<String> hitValues = sorted(hits);
        return new JdkModelMetadata(MODEL_ID,
                availableValues.size() + unavailableValues.size(),
                availableValues.size(), unavailableValues.size(),
                hitValues.size(), availableValues, unavailableValues,
                hitValues);
    }

    private List<String> sorted(
            final Collection<MethodReference> methods) {
        return methods.stream().map(MethodReference::toString)
                .sorted().toList();
    }
}
