package io.github.dependencyanalysis.models.jdk;

import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.MethodTargetSelector;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.BypassSyntheticClassLoader;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.types.MethodReference;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Installs conservative Synthetic IR for stable public JDK contracts. */
public final class JdkModels {

    private JdkModels() {
    }

    // Wiki: wiki/features/jdk-method-models.md - Model installation boundary.
    /**
     * Installs exact JDK method summaries around the current target selector.
     *
     * @param options configured WALA analysis options
     * @param hierarchy active class hierarchy
     * @param definition version-specific model definition
     * @return per-hierarchy model session
     */
    public static JdkModelSession install(
            final AnalysisOptions options,
            final IClassHierarchy hierarchy,
            final JdkModelDefinition definition) {
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(hierarchy, "hierarchy");
        Objects.requireNonNull(definition, "definition");
        final MethodTargetSelector parent = options.getMethodTargetSelector();
        if (parent == null) {
            throw new JdkModelException(
                    "JDK models require an existing method target selector");
        }
        final ModelSyntheticTypes syntheticTypes =
                new ModelSyntheticTypes(hierarchy);
        final ModelStateClass state = new ModelStateClass(
                hierarchy, definition.modelId());
        syntheticTypes.register(state);
        final JdkSummaryBuilder builder = new JdkSummaryBuilder(
                hierarchy, state, syntheticTypes);
        final Map<MethodReference, MethodSummary> summaries =
                new LinkedHashMap<>();
        final Set<MethodReference> unavailable = new LinkedHashSet<>();
        final List<CatalogEntry> entries = new JdkModelCatalog().load(
                definition);
        final CatalogContractValidator validator =
                new CatalogContractValidator(hierarchy);
        validator.validateNativeConflicts(entries);
        for (CatalogEntry entry : entries) {
            final MethodReference reference = entry.reference();
            final IMethod resolved = hierarchy.resolveMethod(reference);
            if (resolved == null) {
                unavailable.add(reference);
                continue;
            }
            validator.validateResolved(entry, resolved);
            final MethodSummary previous = summaries.put(reference,
                    builder.build(entry, resolved));
            if (previous != null) {
                throw new JdkModelException(
                        "Duplicate resolved model target: " + reference);
            }
        }
        final JdkModelSession session = new JdkModelSession(
                definition.modelId(), summaries.keySet(), unavailable);
        options.setSelector(new RecordingBypassMethodTargetSelector(
                parent, summaries, hierarchy, session));
        return session;
    }

    /**
     * Checks whether the active hierarchy exposes WALA's Synthetic loader.
     *
     * @param hierarchy active class hierarchy
     * @return true when models can register synthetic state and return types
     */
    public static boolean supports(final IClassHierarchy hierarchy) {
        Objects.requireNonNull(hierarchy, "hierarchy");
        final IClassLoader loader = hierarchy.getLoader(
                hierarchy.getScope().getSyntheticLoader());
        return loader instanceof BypassSyntheticClassLoader;
    }
}
