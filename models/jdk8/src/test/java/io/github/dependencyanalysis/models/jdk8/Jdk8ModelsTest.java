package io.github.dependencyanalysis.models.jdk8;

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
import io.github.dependencyanalysis.models.jdk.JdkModelMetadata;
import io.github.dependencyanalysis.models.jdk.JdkModelSession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Public installation, selection and metadata tests. */
class Jdk8ModelsTest {

    /** Exact JDK 8 catalog target count. */
    private static final int CATALOG_TARGETS = 384;

    /** Shared JDK 8 hierarchy. */
    private static IClassHierarchy hierarchy;

    @BeforeAll
    static void prepareHierarchy() throws Exception {
        hierarchy = TestHierarchies.jdk8();
    }

    @Test
    void installsAvailableTargetsAndTracksUnavailableTargets() {
        final AnalysisOptions options = TestHierarchies.options(hierarchy);

        final JdkModelSession session = Jdk8Models.install(
                options, hierarchy);
        final JdkModelMetadata metadata = session.snapshot();

        assertThat(metadata.modelId()).isEqualTo(Jdk8Models.MODEL_ID);
        assertThat(metadata.catalogTargetCount()).isEqualTo(CATALOG_TARGETS);
        assertThat(metadata.availableTargetCount()).isEqualTo(
                CATALOG_TARGETS);
        assertThat(metadata.unavailableTargetCount()).isZero();
        assertThat(metadata.hitTargetCount()).isZero();
        assertThat(metadata.unavailableTargets()).isEmpty();
        assertThat(metadata.availableTargets()).noneMatch(target ->
                target.contains("toList()Ljava/util/List;"));
        assertThat(metadata.availableTargets()).anyMatch(target ->
                target.contains("Ljava/util/Collection"));
        assertThat(metadata.availableTargets()).anyMatch(target ->
                target.contains("Ljava/util/stream/Stream"));
        assertThat(metadata.availableTargets()).anyMatch(target ->
                target.contains("Ljava/time/LocalDate"));
        assertThat(metadata.availableTargets()).anyMatch(target ->
                target.contains("Ljava/util/concurrent/CompletableFuture"));
        assertThat(metadata.availableTargets()).anyMatch(target ->
                target.contains("Ljava/nio/file/Files"));
        assertThat(metadata.availableTargets()).anyMatch(target ->
                target.contains("Ljava/io/ObjectInputStream"));
        assertThat(Jdk8Models.supports(hierarchy)).isTrue();
    }

    @Test
    void selectsSyntheticIrAndRecordsExactHit() {
        final AnalysisOptions options = TestHierarchies.options(hierarchy);
        final JdkModelSession session = Jdk8Models.install(
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

}
