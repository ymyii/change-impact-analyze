package io.github.dependencyanalysis.maven;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Verifies the Plugin runtime remains loadable by JDK 8. */
class PluginBytecodeVersionTest {

    /** Class-file magic. */
    private static final int CLASS_FILE_MAGIC = 0xCAFEBABE;

    /** Java 8 class major version. */
    private static final int JAVA_EIGHT_MAJOR = 52;

    @Test
    void pluginClassesTargetJavaEight() throws Exception {
        final String resource = "/"
                + ResolveArtifactPathsMojo.class.getName()
                .replace('.', '/') + ".class";
        try (InputStream input = getClass().getResourceAsStream(resource);
             DataInputStream data = new DataInputStream(input)) {
            assertThat(input).isNotNull();
            assertThat(data.readInt()).isEqualTo(CLASS_FILE_MAGIC);
            data.readUnsignedShort();
            assertThat(data.readUnsignedShort())
                    .isLessThanOrEqualTo(JAVA_EIGHT_MAJOR);
        }
    }
}
