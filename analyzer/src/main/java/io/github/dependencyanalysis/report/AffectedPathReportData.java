package io.github.dependencyanalysis.report;

import io.github.dependencyanalysis.impact.BoundChangePoint;

import java.util.Map;

/**
 * Shared offline data contract for Module Summary and Affected Paths.
 *
 * @param manifest affected path shard manifest
 * @param members browser-facing data by changed member
 */
record AffectedPathReportData(
        AffectedPathReportManifest manifest,
        Map<BoundChangePoint, MemberData> members) {

    /**
     * Browser-facing changed-member projection.
     *
     * @param signature complete JVM member identity
     * @param codeDiffStatus code comparison status
     * @param codeDiffId diff shard record ID, or null
     */
    record MemberData(
            String signature,
            String codeDiffStatus,
            Integer codeDiffId) {
    }
}
