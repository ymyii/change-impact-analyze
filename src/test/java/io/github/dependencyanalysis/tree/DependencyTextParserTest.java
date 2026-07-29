package io.github.dependencyanalysis.tree;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions
        .assertThat;

/** Cross-plugin dependency text parser tests. */
class DependencyTextParserTest {

    /** Expected occurrence count. */
    private static final int OCCURRENCE_COUNT = 4;

    /** Omitted occurrence index. */
    private static final int OMITTED_INDEX = 3;

    /** Expected full path depth. */
    private static final int PATH_DEPTH = 3;

    /** Managed fixture occurrence count. */
    private static final int MANAGED_OCCURRENCE_COUNT = 3;

    @Test
    void parsesSelectedOmittedManagedAndClassifier() {
        final String text =
                "com.example:app:jar:1.0\n"
                + "+- org.demo:alpha:jar:2.0:compile\n"
                + "|  \\- org.demo:shared:jar:tests:"
                + "2.0:test (optional)\n"
                + "\\- org.demo:shared:jar:3.0:compile "
                + "(version managed from 1.0; "
                + "scope managed from runtime)\n"
                + "   \\- org.demo:alpha:jar:1.0:compile "
                + "(omitted for conflict with 2.0)\n";

        final ParsedModuleTree result =
                new DependencyTextParser().parse(
                        text, Set.of(new DependencyKey(
                                "org.demo", "alpha",
                                "jar", "")),
                        Set.of("compile", "test"));

        assertThat(result.getRootCoordinate())
                .isEqualTo("com.example:app:jar:1.0");
        assertThat(result.getOccurrences())
                .hasSize(OCCURRENCE_COUNT);
        assertThat(result.getOccurrences().get(0)
                .isReactorModule()).isTrue();
        assertThat(result.getOccurrences().get(1)
                .getKey().getClassifier())
                .isEqualTo("tests");
        assertThat(result.getOccurrences().get(2)
                .getRequestedVersion())
                .isEqualTo("1.0");
        assertThat(result.getOccurrences().get(2)
                .getManagedFromScope())
                .isEqualTo("runtime");
        assertThat(result.getOccurrences().get(
                OMITTED_INDEX)
                .isSelected()).isFalse();
        assertThat(result.getOccurrences().get(
                OMITTED_INDEX)
                .getSelectedVersion()).isEqualTo("2.0");
        assertThat(result.getOccurrences().get(
                OMITTED_INDEX)
                .getPath()).hasSize(PATH_DEPTH);
    }

    @Test
    void filtersUnselectedScopes() {
        final String text = """
                com.example:app:jar:1.0
                +- org.demo:a:jar:1.0:compile
                \\- org.demo:b:jar:1.0:test
                """;

        final ParsedModuleTree result =
                new DependencyTextParser().parse(
                        text, Set.of(),
                        Set.of("compile"));

        assertThat(result.getOccurrences())
                .extracting(item -> item.getKey()
                        .getArtifactId())
                .containsExactly("a");
    }

    @Test
    void recognizesMavenReactorModuleMarker() {
        final ParsedModuleTree result =
                new DependencyTextParser().parse("""
                        com.example:app:pom:1.0
                        \\- com.example:web:war:1.0:compile -- module web
                        """, Set.of(), Set.of("compile"));

        assertThat(result.getOccurrences())
                .singleElement()
                .satisfies(item -> assertThat(
                        item.isReactorModule()).isTrue());
    }

    @Test
    void preservesManagedVersionWithDuplicateAndConflict() {
        final ParsedModuleTree result =
                new DependencyTextParser().parse(
                        "com.example:app:jar:1.0\n"
                        + "+- org.demo:a:jar:2.0:compile "
                        + "(version managed from 1.0)\n"
                        + "+- org.demo:b:jar:2.0:compile "
                        + "(version managed from 1.0; "
                        + "omitted for duplicate)\n"
                        + "\\- org.demo:c:jar:2.0:compile "
                        + "(version managed from 1.0; "
                        + "omitted for conflict with 3.0)\n",
                        Set.of(), Set.of("compile"));

        assertThat(result.getOccurrences())
                .hasSize(MANAGED_OCCURRENCE_COUNT);
        assertManaged(result.getOccurrences().get(0),
                true, "2.0", "");
        assertManaged(result.getOccurrences().get(1),
                false, "2.0", "duplicate");
        assertManaged(result.getOccurrences().get(2),
                false, "3.0", "conflict");
    }

    private void assertManaged(
            final DependencyOccurrence occurrence,
            final boolean selected,
            final String selectedVersion,
            final String reason) {
        assertThat(occurrence.getRequestedVersion())
                .isEqualTo("1.0");
        assertThat(occurrence.getManagedFromVersion())
                .isEqualTo("1.0");
        assertThat(occurrence.getEffectiveVersion())
                .isEqualTo("2.0");
        assertThat(occurrence.getSelectedVersion())
                .isEqualTo(selectedVersion);
        assertThat(occurrence.isSelected())
                .isEqualTo(selected);
        assertThat(occurrence.getOmittedReason())
                .isEqualTo(reason);
    }
}
