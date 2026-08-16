package io.github.dependencyanalysis.bytecode;

/**
 * Internal normalized SSA comparison outcome.
 *
 * @param status comparison status
 * @param reason stable reason
 */
record SsaComparisonOutcome(SsaComparisonStatus status, String reason) {
}
