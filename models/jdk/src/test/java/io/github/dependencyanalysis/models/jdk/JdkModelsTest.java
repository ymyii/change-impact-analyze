package io.github.dependencyanalysis.models.jdk;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.SummarizedMethod;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction.Dispatch;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Public installation, selection and metadata tests. */
class JdkModelsTest {

    /** Minimum accepted initial catalog size. */
    private static final int MINIMUM_CATALOG_SIZE = 120;

    /** Minimum methods available on the configured JDK 8. */
    private static final int MINIMUM_AVAILABLE_SIZE = 100;

    /** Shared JDK 8 hierarchy. */
    private static IClassHierarchy hierarchy;

    @BeforeAll
    static void prepareHierarchy() throws Exception {
        hierarchy = TestHierarchies.jdk8();
    }

    @Test
    void installsAvailableTargetsAndTracksUnavailableTargets() {
        final AnalysisOptions options = TestHierarchies.options(hierarchy);

        final JdkModelSession session = JdkModels.install(
                options, hierarchy);
        final JdkModelMetadata metadata = session.snapshot();

        assertThat(metadata.modelId()).isEqualTo("jdk");
        assertThat(metadata.catalogTargetCount())
                .isGreaterThan(MINIMUM_CATALOG_SIZE);
        assertThat(metadata.availableTargetCount())
                .isGreaterThan(MINIMUM_AVAILABLE_SIZE);
        assertThat(metadata.catalogTargetCount()).isEqualTo(
                metadata.availableTargetCount()
                        + metadata.unavailableTargetCount());
        assertThat(metadata.hitTargetCount()).isZero();
        assertThat(metadata.unavailableTargets()).hasSize(1)
                .allMatch(target -> target.contains(
                        "Stream, toList()Ljava/util/List;"));
        assertThat(JdkModels.supports(hierarchy)).isTrue();
    }

    @Test
    void selectsSyntheticIrAndRecordsExactHit() {
        final AnalysisOptions options = TestHierarchies.options(hierarchy);
        final JdkModelSession session = JdkModels.install(
                options, hierarchy);
        final MethodReference target = MethodReference.findOrCreate(
                TypeReference.findOrCreate(
                        ClassLoaderReference.Primordial,
                        "Ljava/util/Collection"),
                Selector.make("add(Ljava/lang/Object;)Z"));
        final CallSiteReference site = CallSiteReference.make(
                0, target, Dispatch.INTERFACE);
        final IMethod selected = options.getMethodTargetSelector()
                .getCalleeTarget(null, site, hierarchy.lookupClass(
                        TypeReference.findOrCreate(
                                ClassLoaderReference.Primordial,
                                "Ljava/util/ArrayList")));

        assertThat(selected).isInstanceOf(SummarizedMethod.class);
        assertThat(selected.getReference().getSelector())
                .isEqualTo(target.getSelector());
        assertThat(((SummarizedMethod) selected).getStatements())
                .isNotEmpty();
        assertThat(session.snapshot().hitTargets())
                .contains(selected.getReference().toString());
    }

    @Test
    void requiresAnExistingTargetSelector() {
        final AnalysisOptions options = new AnalysisOptions(
                hierarchy.getScope(), java.util.List.of());

        assertThatThrownBy(() -> JdkModels.install(options, hierarchy))
                .isInstanceOf(JdkModelException.class)
                .hasMessageContaining("existing method target selector");
    }

    @Test
    void delegatesUnavailableCrossVersionTargets() {
        final AnalysisOptions options = TestHierarchies.options(hierarchy);
        final IMethod fallback = hierarchy.resolveMethod(
                MethodReference.findOrCreate(
                        TypeReference.JavaLangObject,
                        Selector.make("toString()Ljava/lang/String;")));
        options.setSelector((caller, site, receiver) -> fallback);
        JdkModels.install(options, hierarchy);
        final MethodReference unavailable = MethodReference.findOrCreate(
                TypeReference.findOrCreate(
                        ClassLoaderReference.Primordial,
                        "Ljava/util/stream/Stream"),
                Selector.make("toList()Ljava/util/List;"));
        final CallSiteReference site = CallSiteReference.make(
                0, unavailable, Dispatch.INTERFACE);

        assertThat(options.getMethodTargetSelector().getCalleeTarget(
                null, site, hierarchy.lookupClass(
                        unavailable.getDeclaringClass())))
                .isSameAs(fallback);
    }
}
