package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ssa.SSAInvokeDynamicInstruction;

/** One exact invokedynamic bootstrap protocol model. */
@FunctionalInterface
public interface InvokeDynamicBootstrapModel {

    /**
     * Produces a fixed-point target or an explicit unsupported result.
     *
     * @param caller reachable caller Context
     * @param site invokedynamic call site
     * @param instruction invokedynamic SSA instruction
     * @param hierarchy active class hierarchy
     * @param syntheticClasses synthetic class registrar
     * @return model result
     */
    InvokeDynamicModelResult model(
            CGNode caller,
            CallSiteReference site,
            SSAInvokeDynamicInstruction instruction,
            IClassHierarchy hierarchy,
            SyntheticClassFactory syntheticClasses);
}
