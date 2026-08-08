package io.github.dependencyanalysis.models.jdk;

import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Cross-layout model capability tests. */
class JdkRuntimeCompatibilityTest {

    /** Minimum methods expected across supported runtime layouts. */
    private static final int MINIMUM_AVAILABLE_SIZE = 100;

    @Test
    void installsAgainstHostJrtWithoutRtJarAssumptions() throws Exception {
        final IClassHierarchy hierarchy = TestHierarchies.hostJdk();
        final AnalysisOptions options = TestHierarchies.options(hierarchy);

        final JdkModelMetadata metadata = JdkModels.install(
                options, hierarchy).snapshot();

        assertThat(metadata.availableTargetCount())
                .isGreaterThan(MINIMUM_AVAILABLE_SIZE);
        assertThat(metadata.availableTargets())
                .anyMatch(value -> value.contains(
                        "Ljava/util/stream/Stream")
                        && value.contains("map("));
        assertThat(metadata.catalogTargetCount()).isEqualTo(
                metadata.availableTargetCount()
                        + metadata.unavailableTargetCount());
    }
}
