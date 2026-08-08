package io.github.dependencyanalysis.impact;

/** Typed terminal or observation evidence. */
public interface ImpactEvidence {

    /** @return deterministic evidence identity */
    String stableKey();

    /** @return user-facing evidence projection */
    String render();
}
