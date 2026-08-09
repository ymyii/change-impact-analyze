package io.github.dependencyanalysis.models.jdk;

import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Host jrt layout capability test for the common model engine. */
class JdkRuntimeCompatibilityTest {

    @Test
    void installsAgainstHostJrtWithoutRtJarAssumptions() throws Exception {
        final IClassHierarchy hierarchy = TestHierarchies.hostJdk();
        final AnalysisOptions options = TestHierarchies.options(hierarchy);

        final JdkModelDefinition definition = new JdkModelDefinition(
                "engine-test", getClass(), "/engine-test-models.tsv");
        final JdkModelMetadata metadata = JdkModels.install(
                options, hierarchy, definition).snapshot();

        assertThat(metadata.availableTargetCount()).isPositive();
        assertThat(metadata.availableTargets())
                .anyMatch(value -> value.contains(
                        "Ljava/util/stream/Stream")
                        && value.contains("map("));
        assertThat(metadata.catalogTargetCount()).isEqualTo(
                metadata.availableTargetCount()
                        + metadata.unavailableTargetCount());
    }
}
