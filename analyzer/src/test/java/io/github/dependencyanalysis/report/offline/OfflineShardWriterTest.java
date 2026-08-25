package io.github.dependencyanalysis.report.offline;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Shared offline shard boundary and escaping tests. */
class OfflineShardWriterTest {

    /** Tiny byte limit forcing record splits. */
    private static final int TINY_SHARD_BYTES = 150;

    /** Expected shard count after byte and oversize splits. */
    private static final int EXPECTED_SHARDS = 3;

    /** Temporary output. */
    @TempDir
    private Path temporary;

    @Test
    void splitsAtTargetAndLetsOversizedRecordStandAlone() throws Exception {
        final OfflineShardWriter writer = new OfflineShardWriter(
                7, "window.__TEST_SHARD__", TINY_SHARD_BYTES);
        final List<String> values = List.of("small", "x".repeat(300),
                "after");

        final List<OfflineShardDescriptor> descriptors = writer.write(
                "rows", values.size(), id -> json -> {
                    json.writeStartObject();
                    json.writeNumberField("id", id);
                    json.writeStringField("value", values.get(id));
                    json.writeEndObject();
                }, temporary, "data");

        assertThat(descriptors).hasSize(EXPECTED_SHARDS);
        assertThat(descriptors.get(1).records()).isEqualTo(1);
        assertThat(descriptors.get(1).bytes())
                .isGreaterThan(TINY_SHARD_BYTES);
        assertThat(descriptors)
                .extracting(OfflineShardDescriptor::firstId,
                        OfflineShardDescriptor::lastId,
                        OfflineShardDescriptor::file)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                0, 0, "data/rows-00000.js"),
                        org.assertj.core.groups.Tuple.tuple(
                                1, 1, "data/rows-00001.js"),
                        org.assertj.core.groups.Tuple.tuple(
                                2, 2, "data/rows-00002.js"));
    }

    @Test
    void writesScriptSafeCallbackPayload() throws Exception {
        final OfflineShardWriter writer = new OfflineShardWriter(
                5, "window.__TEST_SHARD__", 4096);
        writer.write("rows", 1, id -> json -> {
            json.writeStartObject();
            json.writeNumberField("id", id);
            json.writeStringField("value",
                    "</script><script>window.injected=true</script>");
            json.writeEndObject();
        }, temporary, "data");

        final String payload = Files.readString(
                temporary.resolve("rows-00000.js"));
        assertThat(payload)
                .startsWith("window.__TEST_SHARD__({\"schemaVersion\":5")
                .doesNotContain("</script>")
                .contains("\\u003c/script\\u003e");
    }
}
