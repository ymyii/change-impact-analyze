package io.github.dependencyanalysis.report.offline;

/**
 * One deterministic local JavaScript shard.
 *
 * @param id kind-local shard ID
 * @param file page-relative local file
 * @param firstId first record ID
 * @param lastId last record ID
 * @param records record count
 * @param bytes encoded file size
 */
public record OfflineShardDescriptor(
        int id,
        String file,
        int firstId,
        int lastId,
        int records,
        long bytes) {
}
