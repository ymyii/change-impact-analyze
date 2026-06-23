package io.github.changeimpact.analyze.dependency;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

/**
 * Tests for {@link ArtifactCoord}.
 */
class ArtifactCoordTest {

    @Test
    void parseFourSegments() {
        final ArtifactCoord coord =
                ArtifactCoord.parse(
                        "com.example:app:jar:1.0");
        assertThat(coord.getGroupId())
                .isEqualTo("com.example");
        assertThat(coord.getArtifactId())
                .isEqualTo("app");
        assertThat(coord.getType())
                .isEqualTo("jar");
        assertThat(coord.getVersion())
                .isEqualTo("1.0");
    }

    @Test
    void parseFiveSegments() {
        final ArtifactCoord coord =
                ArtifactCoord.parse(
                        "org.slf4j:slf4j-api"
                                + ":jar:2.0.13"
                                + ":compile");
        assertThat(coord.getGroupId())
                .isEqualTo("org.slf4j");
        assertThat(coord.getArtifactId())
                .isEqualTo("slf4j-api");
        assertThat(coord.getType())
                .isEqualTo("jar");
        assertThat(coord.getVersion())
                .isEqualTo("2.0.13");
    }

    @Test
    void parseSixSegmentsWithClassifier() {
        final ArtifactCoord coord =
                ArtifactCoord.parse(
                        "io.netty"
                                + ":netty-transport"
                                + "-native-epoll"
                                + ":jar:linux-x86_64"
                                + ":4.1.118.Final"
                                + ":provider");
        assertThat(coord.getGroupId())
                .isEqualTo("io.netty");
        assertThat(coord.getArtifactId())
                .isEqualTo(
                        "netty-transport"
                                + "-native-epoll");
        assertThat(coord.getType())
                .isEqualTo("jar");
        assertThat(coord.getClassifier())
                .isEqualTo("linux-x86_64");
        assertThat(coord.getVersion())
                .isEqualTo("4.1.118.Final");
    }

    @Test
    void parseTrimsWhitespace() {
        final ArtifactCoord coord =
                ArtifactCoord.parse(
                        "  com.example:app"
                                + ":jar:1.0  ");
        assertThat(coord.getGroupId())
                .isEqualTo("com.example");
    }

    @Test
    void parseThrowsOnEmpty() {
        assertThatThrownBy(() ->
                ArtifactCoord.parse(""))
                .isInstanceOf(
                        IllegalArgumentException
                                .class);
    }

    @Test
    void parseThrowsOnTooFewSegments() {
        assertThatThrownBy(() ->
                ArtifactCoord.parse(
                        "com.example:app"))
                .isInstanceOf(
                        IllegalArgumentException
                                .class);
    }

    @Test
    void parseThrowsOnTooManySegments() {
        assertThatThrownBy(() ->
                ArtifactCoord.parse(
                        "a:b:c:d:e:f:g"))
                .isInstanceOf(
                        IllegalArgumentException
                                .class);
    }

    @Test
    void parseThrowsOnNull() {
        assertThatThrownBy(() ->
                ArtifactCoord.parse(null))
                .isInstanceOf(
                        NullPointerException
                                .class);
    }

    @Test
    void toStringFormat() {
        final ArtifactCoord coord =
                new ArtifactCoord(
                        "com.example",
                        "app",
                        "jar",
                        "1.0");
        assertThat(coord.toString())
                .isEqualTo(
                        "com.example:app"
                                + ":jar:1.0");
    }

    @Test
    void diffKeyFormat() {
        final ArtifactCoord coord =
                new ArtifactCoord(
                        "com.example",
                        "app",
                        "jar",
                        "1.0");
        assertThat(coord.diffKey())
                .isEqualTo(
                        "com.example:app:jar");
    }

    @Test
    void equalsAndHashCode() {
        final ArtifactCoord a =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0");
        final ArtifactCoord b =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0");
        final ArtifactCoord c =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "2.0");
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode())
                .isEqualTo(b.hashCode());
        assertThat(a).isNotEqualTo(c);
    }

    @Test
    void equalsSameInstance() {
        final ArtifactCoord a =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0");
        assertThat(a).isEqualTo(a);
    }

    @Test
    void equalsNullAndOtherType() {
        final ArtifactCoord a =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0");
        assertThat(a).isNotEqualTo(null);
        assertThat(a).isNotEqualTo("str");
    }

    @Test
    void constructorWithClassifier() {
        final ArtifactCoord coord =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0", "sources");
        assertThat(coord.getClassifier())
                .isEqualTo("sources");
    }

    @Test
    void defaultClassifierIsEmpty() {
        final ArtifactCoord coord =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0");
        assertThat(coord.getClassifier())
                .isEmpty();
    }

    @Test
    void diffKeyWithClassifier() {
        final ArtifactCoord coord =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0", "sources");
        assertThat(coord.diffKey())
                .isEqualTo(
                        "g:a:jar:sources");
    }

    @Test
    void diffKeyWithoutClassifier() {
        final ArtifactCoord coord =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0");
        assertThat(coord.diffKey())
                .isEqualTo("g:a:jar");
    }

    @Test
    void toStringWithClassifier() {
        final ArtifactCoord coord =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0", "sources");
        assertThat(coord.toString())
                .isEqualTo(
                        "g:a:jar:1.0"
                                + ":sources");
    }

    @Test
    void equalsWithDifferentClassifier() {
        final ArtifactCoord a =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0", "sources");
        final ArtifactCoord b =
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0", "javadoc");
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void constructorThrowsOnNullClassifier() {
        assertThatThrownBy(() ->
                new ArtifactCoord(
                        "g", "a", "jar",
                        "1.0", null))
                .isInstanceOf(
                        NullPointerException
                                .class);
    }
}
