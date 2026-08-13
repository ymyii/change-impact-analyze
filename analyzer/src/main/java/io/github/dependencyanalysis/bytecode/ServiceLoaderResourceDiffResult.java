package io.github.dependencyanalysis.bytecode;

import java.util.List;
import java.util.Objects;

/**
 * Immutable baseline/target ServiceLoader resource Diff result.
 *
 * @param baselineRegistrations valid baseline registrations
 * @param removedRegistrations removed valid registrations
 * @param changePoints registration-only public ChangePoints
 * @param issues non-fatal resource issues
 */
public record ServiceLoaderResourceDiffResult(
        List<ServiceProviderRegistration> baselineRegistrations,
        List<ServiceProviderRegistration> removedRegistrations,
        List<ChangePoint> changePoints,
        List<ServiceLoaderResourceIssue> issues) {

    /** Copies and orders all output. */
    public ServiceLoaderResourceDiffResult {
        baselineRegistrations = Objects.requireNonNull(
                baselineRegistrations, "baselineRegistrations").stream()
                .distinct().sorted(java.util.Comparator.comparing(
                        ServiceProviderRegistration::stableKey)).toList();
        removedRegistrations = Objects.requireNonNull(
                removedRegistrations, "removedRegistrations").stream()
                .distinct().sorted(java.util.Comparator.comparing(
                        ServiceProviderRegistration::stableKey)).toList();
        changePoints = Objects.requireNonNull(changePoints, "changePoints")
                .stream().distinct().sorted(java.util.Comparator.comparing(
                        point -> point.getServiceRegistration()
                                .orElseThrow().stableKey())).toList();
        issues = Objects.requireNonNull(issues, "issues").stream()
                .distinct().sorted().toList();
    }

    /** @return empty successful result */
    public static ServiceLoaderResourceDiffResult empty() {
        return new ServiceLoaderResourceDiffResult(
                List.of(), List.of(), List.of(), List.of());
    }
}
