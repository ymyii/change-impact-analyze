package io.github.dependencyanalysis.dependency;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Tests Maven dependency source Glob selection. */
class DependencyArtifactSelectionTest {

    @Test
    void defaultsToAllAndIgnoresVersionTypeAndClassifier() {
        final DependencyArtifactSelection all =
                DependencyArtifactSelection.allDependencies();
        final DependencyArtifactSelection selected =
                DependencyArtifactSelection.parse(
                        List.of("com.acme:library"), List.of());

        assertThat(all.matches(artifact(
                "other", "anything", "pom", "1", "tests"))).isTrue();
        assertThat(selected.matches(artifact(
                "com.acme", "library", "zip", "99", "sources")))
                .isTrue();
    }

    @Test
    void starAndQuestionMatchInsideSegmentsIncludingGroupDots() {
        final DependencyArtifactSelection selected =
                DependencyArtifactSelection.parse(
                        List.of("com.*.service:api-?"), List.of());

        assertThat(selected.matches(artifact(
                "com.acme.platform.service", "api-a"))).isTrue();
        assertThat(selected.matches(artifact(
                "com.acme.platform.service", "api-ab"))).isFalse();
        assertThat(selected.matches(artifact(
                "org.acme.service", "api-a"))).isFalse();
    }

    @Test
    void includeUsesUnionExcludeWinsAndMatchingIsCaseSensitive() {
        final DependencyArtifactSelection selected =
                DependencyArtifactSelection.parse(
                        List.of("com.acme:api-*", "org.example:core"),
                        List.of("com.acme:api-internal"));

        assertThat(selected.matches(artifact("com.acme", "api-public")))
                .isTrue();
        assertThat(selected.matches(artifact("org.example", "core")))
                .isTrue();
        assertThat(selected.matches(artifact("com.acme", "api-internal")))
                .isFalse();
        assertThat(selected.matches(artifact("Com.acme", "api-public")))
                .isFalse();
    }

    @Test
    void duplicatePatternsRetainFirstOccurrenceOnly() {
        final DependencyArtifactSelection selected =
                DependencyArtifactSelection.parse(
                        List.of("g:a", "g:a", "g:b"),
                        List.of("x:y", "x:y"));

        assertThat(selected.includes()).extracting(
                        MavenArtifactPattern::expression)
                .containsExactly("g:a", "g:b");
        assertThat(selected.excludes()).extracting(
                        MavenArtifactPattern::expression)
                .containsExactly("x:y");
    }

    @Test
    void rejectsMalformedPatterns() {
        for (String value : List.of("", "group", ":artifact",
                "group:", "group:artifact:extra", "group :artifact",
                "group:arti fact", " group:artifact", "group:artifact ")) {
            assertThatIllegalArgumentException().isThrownBy(() ->
                    MavenArtifactPattern.parse(value));
        }
    }

    private ArtifactCoord artifact(
            final String group,
            final String artifact) {
        return artifact(group, artifact, "jar", "1", "");
    }

    private ArtifactCoord artifact(
            final String group,
            final String artifact,
            final String type,
            final String version,
            final String classifier) {
        return new ArtifactCoord(group, artifact, type, version, classifier);
    }
}
