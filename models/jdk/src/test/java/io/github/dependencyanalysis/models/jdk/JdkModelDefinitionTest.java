package io.github.dependencyanalysis.models.jdk;

import com.ibm.wala.ipa.cha.IClassHierarchy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Version-specific catalog definition contract tests. */
class JdkModelDefinitionTest {

    @Test
    void rejectsUnsafeModelId() {
        assertThatThrownBy(() -> new JdkModelDefinition(
                "JDK/8", getClass(), "/catalog.tsv"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modelId");
    }

    @Test
    void rejectsRelativeCatalogResource() {
        assertThatThrownBy(() -> new JdkModelDefinition(
                "jdk8", getClass(), "catalog.tsv"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute classpath resource");
    }

    @Test
    void failsFastWhenCatalogResourceIsMissing() throws Exception {
        final JdkModelDefinition definition = new JdkModelDefinition(
                "missing", getClass(), "/missing-catalog.tsv");
        final IClassHierarchy hierarchy = TestHierarchies.hostJdk();

        assertThatThrownBy(() -> JdkModels.install(
                TestHierarchies.options(hierarchy), hierarchy, definition))
                .isInstanceOf(JdkModelException.class)
                .hasMessageContaining("catalog is unavailable");
    }
}
