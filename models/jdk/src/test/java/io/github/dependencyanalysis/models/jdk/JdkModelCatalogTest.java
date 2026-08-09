package io.github.dependencyanalysis.models.jdk;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Catalog parsing and validation tests. */
class JdkModelCatalogTest {

    @Test
    void testCatalogCoversEveryEngineTemplate() {
        final var entries = new JdkModelCatalog().load(
                new JdkModelDefinition("engine-test", getClass(),
                        "/engine-test-models.tsv"));

        assertThat(entries).extracting(CatalogEntry::template)
                .containsExactlyInAnyOrder(SummaryTemplate.values());
    }

    @Test
    void rejectsDuplicateExactTargets() {
        final String line = "java/util/List\tget\t(I)Ljava/lang/Object;"
                + "\tfalse\tLOAD\t-\tCOLLECTION_ELEMENT\t-\t-\n";
        final ByteArrayInputStream input = new ByteArrayInputStream(
                (line + line).getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new JdkModelCatalog().parse(input))
                .isInstanceOf(JdkModelException.class)
                .hasMessageContaining("Duplicate catalog target");
    }

    @Test
    void rejectsMalformedCallbackTargets() {
        final String line = "java/util/List\tget\t(I)Ljava/lang/Object;"
                + "\tfalse\tCALLBACK_RESULT\t-\t-\tbad\t-\n";
        final ByteArrayInputStream input = new ByteArrayInputStream(
                line.getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> new JdkModelCatalog().parse(input))
                .isInstanceOf(JdkModelException.class)
                .hasMessageContaining("Invalid callback specification");
    }

    @Test
    void rejectsMalformedDescriptorDuringInstallation() throws Exception {
        final String line = "java/util/List\tget\t(not-a-descriptor)"
                + "\tfalse\tLOAD\t-\tCOLLECTION_ELEMENT\t-\t-\n";
        final ByteArrayInputStream input = new ByteArrayInputStream(
                line.getBytes(StandardCharsets.UTF_8));
        final CatalogEntry entry = new JdkModelCatalog().parse(input).get(0);

        assertThatThrownBy(entry::reference)
                .isInstanceOf(JdkModelException.class)
                .hasMessageContaining("Invalid catalog method");
    }
}
