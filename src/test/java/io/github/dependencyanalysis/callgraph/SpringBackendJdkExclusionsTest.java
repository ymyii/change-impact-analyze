package io.github.dependencyanalysis.callgraph;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests the intentionally narrow Spring backend JDK boundary. */
class SpringBackendJdkExclusionsTest {

    @Test
    void excludesOnlyConfiguredClassesAndJars() {
        final SpringBackendJdkExclusions exclusions =
                new SpringBackendJdkExclusions();

        assertThat(exclusions.test("Ljavax/swing/JFrame")).isTrue();
        assertThat(exclusions.test("java/applet/Applet")).isTrue();
        assertThat(exclusions.test("java/awt/Graphics")).isFalse();
        assertThat(exclusions.test("javax/accessibility/Accessible"))
                .isFalse();
        assertThat(exclusions.excludesJar(Path.of("jfxrt.jar"))).isTrue();
        assertThat(exclusions.excludesJar(Path.of("rt.jar"))).isFalse();
    }
}
