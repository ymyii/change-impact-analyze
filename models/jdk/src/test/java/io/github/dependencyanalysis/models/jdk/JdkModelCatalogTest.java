package io.github.dependencyanalysis.models.jdk;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Catalog parsing and validation tests. */
class JdkModelCatalogTest {

    /** Minimum accepted initial catalog size. */
    private static final int MINIMUM_CATALOG_SIZE = 120;

    @Test
    void committedCatalogCoversAllModelFamilies() {
        final var entries = new JdkModelCatalog().load();

        assertThat(entries).hasSizeGreaterThan(MINIMUM_CATALOG_SIZE);
        assertThat(entries).extracting(CatalogEntry::template)
                .contains(SummaryTemplate.STORE, SummaryTemplate.LOAD,
                        SummaryTemplate.CALLBACK_NEW,
                        SummaryTemplate.FILE_VISITOR,
                        SummaryTemplate.SERIALIZE_READ,
                        SummaryTemplate.SERIALIZE_WRITE);
        assertThat(entries).extracting(CatalogEntry::owner)
                .contains("java/util/Collection", "java/util/stream/Stream",
                        "java/time/LocalDate",
                        "java/util/concurrent/CompletableFuture",
                        "java/nio/file/Files",
                        "java/io/ObjectInputStream");
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
}
