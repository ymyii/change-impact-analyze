package io.github.dependencyanalysis.callgraph.scope;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Deterministic winner evidence for one conflicting binary class name. */
public final class DuplicateClassResolution {

    /** Internal JVM binary name. */
    private final String binaryName;

    /** Effective definition exposed to WALA. */
    private final ClassOwnership winner;

    /** Every physical definition in classpath discovery order. */
    private final List<ClassOwnership> candidates;

    /** Human-readable precedence rule. */
    private final String precedenceReason;

    /**
     * Creates immutable duplicate resolution evidence.
     *
     * @param name internal binary name
     * @param effectiveWinner selected definition
     * @param definitions all definitions
     * @param reason winner rule
     */
    DuplicateClassResolution(
            final String name,
            final ClassOwnership effectiveWinner,
            final List<ClassOwnership> definitions,
            final String reason) {
        binaryName = Objects.requireNonNull(name, "binaryName");
        winner = Objects.requireNonNull(effectiveWinner, "winner");
        candidates = Collections.unmodifiableList(
                new ArrayList<>(Objects.requireNonNull(
                        definitions, "candidates")));
        precedenceReason = Objects.requireNonNull(reason,
                "precedenceReason");
    }

    /** @return internal JVM binary name */
    public String getBinaryName() {
        return binaryName;
    }

    /** @return effective class definition */
    public ClassOwnership getWinner() {
        return winner;
    }

    /** @return all definitions in discovery order */
    public List<ClassOwnership> getCandidates() {
        return candidates;
    }

    /** @return shadowed definitions in discovery order */
    public List<ClassOwnership> getLosers() {
        return candidates.stream()
                .filter(value -> value != winner)
                .toList();
    }

    /** @return stable precedence explanation */
    public String getPrecedenceReason() {
        return precedenceReason;
    }
}
