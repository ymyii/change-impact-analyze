package io.github.dependencyanalysis.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions
        .assertThat;
import static org.assertj.core.api.Assertions
        .assertThatThrownBy;

/** Maven version boundary tests. */
class MavenVersionTest {

    @Test
    void supportsThreeSixThreeThroughThreeX() {
        assertThat(MavenVersion.parse(
                "Apache Maven 3.6.3").isSupported())
                .isTrue();
        assertThat(MavenVersion.parse(
                "Apache Maven 3.9.16").isSupported())
                .isTrue();
    }

    @Test
    void rejectsOldAndMavenFour() {
        assertThat(MavenVersion.parse(
                "Apache Maven 3.6.2").isSupported())
                .isFalse();
        assertThat(MavenVersion.parse(
                "Apache Maven 4.0.0").isSupported())
                .isFalse();
    }

    @Test
    void rejectsUnparseableOutput() {
        assertThatThrownBy(() -> MavenVersion
                .parse("not Maven"))
                .isInstanceOf(
                        IllegalArgumentException.class);
    }
}
