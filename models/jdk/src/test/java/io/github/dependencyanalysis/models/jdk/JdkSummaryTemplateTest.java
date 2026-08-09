package io.github.dependencyanalysis.models.jdk;

import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.ipa.summaries.SummarizedMethod;
import com.ibm.wala.ssa.SSAGetInstruction;
import com.ibm.wala.ssa.SSAInvokeInstruction;
import com.ibm.wala.ssa.SSAPutInstruction;
import com.ibm.wala.ssa.SSAOptions;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic IR generation and representative semantic tests. */
class JdkSummaryTemplateTest {

    @Test
    void everyTemplateBuildsValidIrOnHostJdk() throws Exception {
        final IClassHierarchy hierarchy = TestHierarchies.hostJdk();
        final ModelSyntheticTypes types = new ModelSyntheticTypes(hierarchy);
        final ModelStateClass state = new ModelStateClass(
                hierarchy, "engine-test");
        types.register(state);
        final JdkSummaryBuilder builder = new JdkSummaryBuilder(
                hierarchy, state, types);
        final CatalogContractValidator validator =
                new CatalogContractValidator(hierarchy);
        final EnumSet<SummaryTemplate> generated = EnumSet.noneOf(
                SummaryTemplate.class);

        for (CatalogEntry entry : catalog()) {
            final IMethod resolved = hierarchy.resolveMethod(
                    entry.reference());
            if (resolved == null) {
                continue;
            }
            validator.validateResolved(entry, resolved);
            final MethodSummary summary = builder.build(entry, resolved);
            final SummarizedMethod method = new SummarizedMethod(
                    entry.reference(), summary,
                    resolved.getDeclaringClass());
            assertThat(summary.getStatements()).isNotEmpty();
            assertThat(method.makeIR(Everywhere.EVERYWHERE,
                    SSAOptions.defaultOptions())).isNotNull();
            generated.add(entry.template());
        }

        assertThat(generated).containsExactlyInAnyOrder(
                SummaryTemplate.values());
    }

    @Test
    void stateAndCallbackTemplatesEmitExpectedInstructions()
            throws Exception {
        final IClassHierarchy hierarchy = TestHierarchies.hostJdk();
        final ModelSyntheticTypes types = new ModelSyntheticTypes(hierarchy);
        final ModelStateClass state = new ModelStateClass(
                hierarchy, "engine-test");
        types.register(state);
        final JdkSummaryBuilder builder = new JdkSummaryBuilder(
                hierarchy, state, types);

        final MethodSummary store = summary(builder, hierarchy,
                "java/util/Collection",
                "add(Ljava/lang/Object;)Z");
        final MethodSummary load = summary(builder, hierarchy,
                "java/util/Iterator", "next()Ljava/lang/Object;");
        final MethodSummary callback = summary(builder, hierarchy,
                "java/util/stream/Stream",
                "map(Ljava/util/function/Function;)"
                        + "Ljava/util/stream/Stream;");

        assertThat(store.getStatements())
                .anyMatch(SSAPutInstruction.class::isInstance);
        assertThat(load.getStatements())
                .anyMatch(SSAGetInstruction.class::isInstance);
        assertThat(callback.getStatements())
                .anyMatch(SSAInvokeInstruction.class::isInstance);
    }

    private MethodSummary summary(
            final JdkSummaryBuilder builder,
            final IClassHierarchy hierarchy,
            final String owner,
            final String selector) {
        final CatalogEntry entry = catalog().stream()
                .filter(value -> value.owner().equals(owner))
                .filter(value -> (value.name() + value.descriptor())
                        .equals(selector))
                .findFirst().orElseThrow();
        return builder.build(entry,
                hierarchy.resolveMethod(entry.reference()));
    }

    private java.util.List<CatalogEntry> catalog() {
        return new JdkModelCatalog().load(new JdkModelDefinition(
                "engine-test", getClass(), "/engine-test-models.tsv"));
    }
}
