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

/** Public common-engine installation and selector tests. */
class JdkModelsTest {

    /** Shared engine test definition. */
    private static final JdkModelDefinition DEFINITION = definition(
            "engine-test-models.tsv");

    /** Shared host JDK hierarchy. */
    private static IClassHierarchy hierarchy;

    @BeforeAll
    static void prepareHierarchy() throws Exception {
        hierarchy = TestHierarchies.hostJdk();
    }

    @Test
    void installsDefinitionAndUsesItsModelId() {
        final JdkModelMetadata metadata = JdkModels.install(
                TestHierarchies.options(hierarchy), hierarchy,
                DEFINITION).snapshot();

        assertThat(metadata.modelId()).isEqualTo("engine-test");
        assertThat(metadata.catalogTargetCount()).isPositive();
        assertThat(metadata.availableTargetCount()).isEqualTo(
                metadata.catalogTargetCount());
        assertThat(metadata.unavailableTargetCount()).isZero();
        assertThat(metadata.hitTargetCount()).isZero();
        assertThat(metadata.availableTargets()).isSorted();
    }

    @Test
    void selectsSyntheticIrAndRecordsDeterministicHit() {
        final AnalysisOptions options = TestHierarchies.options(hierarchy);
        final JdkModelSession session = JdkModels.install(
                options, hierarchy, DEFINITION);
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
        assertThat(session.snapshot().hitTargets())
                .containsExactly(selected.getReference().toString());
        assertThat(session.snapshot()).isEqualTo(session.snapshot());
    }

    @Test
    void requiresExistingSelector() {
        final AnalysisOptions options = new AnalysisOptions(
                hierarchy.getScope(), java.util.List.of());

        assertThatThrownBy(() -> JdkModels.install(
                options, hierarchy, DEFINITION))
                .isInstanceOf(JdkModelException.class)
                .hasMessageContaining("existing method target selector");
    }

    @Test
    void delegatesUnavailableTargetToParentSelector() {
        final AnalysisOptions options = TestHierarchies.options(hierarchy);
        final IMethod fallback = hierarchy.resolveMethod(
                MethodReference.findOrCreate(
                        TypeReference.JavaLangObject,
                        Selector.make("toString()Ljava/lang/String;")));
        options.setSelector((caller, site, receiver) -> fallback);
        final JdkModelSession session = JdkModels.install(
                options, hierarchy, definition("unavailable-models.tsv"));
        final MethodReference unavailable = MethodReference.findOrCreate(
                TypeReference.findOrCreate(
                        ClassLoaderReference.Primordial,
                        "Ljava/util/List"),
                Selector.make("missing()Ljava/lang/Object;"));
        final CallSiteReference site = CallSiteReference.make(
                0, unavailable, Dispatch.INTERFACE);

        assertThat(session.snapshot().unavailableTargetCount()).isOne();
        assertThat(options.getMethodTargetSelector().getCalleeTarget(
                null, site, hierarchy.lookupClass(
                        unavailable.getDeclaringClass())))
                .isSameAs(fallback);
    }

    @Test
    void rejectsResolvedStaticContractMismatch() {
        assertThatThrownBy(() -> JdkModels.install(
                TestHierarchies.options(hierarchy), hierarchy,
                definition("invalid-static-models.tsv")))
                .isInstanceOf(JdkModelException.class)
                .hasMessageContaining("static contract mismatch");
    }

    @Test
    void rejectsWalaNativeSummaryConflict() {
        assertThatThrownBy(() -> JdkModels.install(
                TestHierarchies.options(hierarchy), hierarchy,
                definition("native-conflict-models.tsv")))
                .isInstanceOf(JdkModelException.class)
                .hasMessageContaining("native summaries");
    }

    private static JdkModelDefinition definition(final String resource) {
        return new JdkModelDefinition("engine-test", JdkModelsTest.class,
                "/" + resource);
    }
}
