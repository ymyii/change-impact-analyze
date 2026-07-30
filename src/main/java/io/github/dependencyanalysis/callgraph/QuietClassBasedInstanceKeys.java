package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.classLoader.ProgramCounter;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.ClassBasedInstanceKeys;
import com.ibm.wala.ipa.callgraph.propagation.ConcreteTypeKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKeyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.TypeReference;

/**
 * Class-based allocation keys without WALA's unconditional method-handle
 * debug print. All non-allocation behavior delegates to WALA.
 */
final class QuietClassBasedInstanceKeys
        implements InstanceKeyFactory {

    /** Analysis options used to resolve allocation targets. */
    private final AnalysisOptions options;

    /** WALA implementation for the remaining key kinds. */
    private final ClassBasedInstanceKeys delegate;

    /**
     * Creates the factory.
     *
     * @param analysisOptions analysis options
     * @param hierarchy class hierarchy
     */
    QuietClassBasedInstanceKeys(
            final AnalysisOptions analysisOptions,
            final IClassHierarchy hierarchy) {
        options = analysisOptions;
        delegate = new ClassBasedInstanceKeys(
                analysisOptions, hierarchy);
    }

    @Override
    public InstanceKey getInstanceKeyForAllocation(
            final CGNode node,
            final NewSiteReference allocation) {
        if (allocation == null) {
            throw new IllegalArgumentException(
                    "allocation is null");
        }
        if (options.getClassTargetSelector() == null) {
            throw new IllegalStateException(
                    "options did not specify class target selector");
        }
        final IClass target = options
                .getClassTargetSelector()
                .getAllocatedTarget(node, allocation);
        return target == null
                ? null : new ConcreteTypeKey(target);
    }

    @Override
    public InstanceKey getInstanceKeyForMultiNewArray(
            final CGNode node,
            final NewSiteReference allocation,
            final int dimension) {
        return delegate.getInstanceKeyForMultiNewArray(
                node, allocation, dimension);
    }

    @Override
    public <T> InstanceKey getInstanceKeyForConstant(
            final TypeReference type,
            final T value) {
        return delegate.getInstanceKeyForConstant(type, value);
    }

    @Override
    public InstanceKey getInstanceKeyForPEI(
            final CGNode node,
            final ProgramCounter programCounter,
            final TypeReference type) {
        return delegate.getInstanceKeyForPEI(
                node, programCounter, type);
    }

    @Override
    public InstanceKey getInstanceKeyForMetadataObject(
            final Object object,
            final TypeReference type) {
        return delegate.getInstanceKeyForMetadataObject(
                object, type);
    }
}
