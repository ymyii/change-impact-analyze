package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.JavaLanguage;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.propagation.cfa.ZeroXCFABuilder;
import com.ibm.wala.ipa.cha.IClassHierarchy;

/** k-object builder with ClassFactory-compatible Context composition. */
final class KObjCallGraphBuilder extends ZeroXCFABuilder {

    KObjCallGraphBuilder(
            final int depth,
            final IClassHierarchy hierarchy,
            final AnalysisOptions options,
            final IAnalysisCacheView cache,
            final int instancePolicy) {
        super(JavaLanguage.get(), hierarchy, options, cache,
                null, null, instancePolicy);
        setContextSelector(new KObjContextSelector(
                depth, getContextSelector()));
    }
}
