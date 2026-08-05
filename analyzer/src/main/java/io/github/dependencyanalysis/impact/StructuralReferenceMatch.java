package io.github.dependencyanalysis.impact;

/**
 * Binds one metadata reference to the changed dependency class.
 *
 * @param changePoint changed dependency class
 * @param reference structured metadata reference
 */
public record StructuralReferenceMatch(
        BoundChangePoint changePoint,
        StructuralReference reference) {
}
