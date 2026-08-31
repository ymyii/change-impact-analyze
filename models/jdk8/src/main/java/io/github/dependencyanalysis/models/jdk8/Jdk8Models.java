package io.github.dependencyanalysis.models.jdk8;

import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import io.github.dependencyanalysis.models.jdk.JdkModelDefinition;
import io.github.dependencyanalysis.models.jdk.JdkModelSession;
import io.github.dependencyanalysis.models.jdk.JdkModels;

/** Installs conservative Synthetic IR for exact public JDK 8 contracts. */
public final class Jdk8Models {

    /** Stable model identifier. */
    public static final String MODEL_ID = "jdk8";

    /** Exact JDK 8 catalog resource. */
    private static final String CATALOG =
            "/io/github/dependencyanalysis/models/jdk8/jdk8-models.tsv";

    /** Immutable JDK 8 model definition. */
    private static final JdkModelDefinition DEFINITION =
            new JdkModelDefinition(MODEL_ID, Jdk8Models.class, CATALOG);

    private Jdk8Models() {
    }

    // Wiki: wiki/implementation/jdk-method-models.md - Model install
    /**
     * Installs exact JDK 8 summaries around the current target selector.
     *
     * @param options configured WALA analysis options
     * @param hierarchy active JDK 8 class hierarchy
     * @return per-hierarchy JDK 8 model session
     */
    public static JdkModelSession install(
            final AnalysisOptions options,
            final IClassHierarchy hierarchy) {
        return JdkModels.install(options, hierarchy, DEFINITION);
    }

    /**
     * Checks whether the hierarchy supports the common model engine.
     *
     * @param hierarchy active class hierarchy
     * @return true when WALA exposes its Synthetic loader
     */
    public static boolean supports(final IClassHierarchy hierarchy) {
        return JdkModels.supports(hierarchy);
    }
}
