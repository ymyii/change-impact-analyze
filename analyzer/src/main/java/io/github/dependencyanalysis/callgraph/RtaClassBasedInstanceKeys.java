package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.classLoader.ProgramCounter;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.ConcreteTypeKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKeyFactory;
import com.ibm.wala.types.TypeReference;

import java.util.Objects;

/** Quiet class-based RTA keys around WALA's non-allocation operations. */
final class RtaClassBasedInstanceKeys implements InstanceKeyFactory {

    /** WALA options containing the allocated-class selector. */
    private final AnalysisOptions options;

    /** Existing RTA factory for non-allocation operations. */
    private final InstanceKeyFactory delegate;

    RtaClassBasedInstanceKeys(
            final AnalysisOptions analysisOptions,
            final InstanceKeyFactory base) {
        options = Objects.requireNonNull(analysisOptions, "analysisOptions");
        delegate = Objects.requireNonNull(base, "base");
    }

    @Override
    public InstanceKey getInstanceKeyForAllocation(
            final CGNode node,
            final NewSiteReference allocation) {
        Objects.requireNonNull(allocation, "allocation");
        if (options.getClassTargetSelector() == null) {
            throw new IllegalStateException(
                    "Analysis options require a class target selector");
        }
        final IClass type = options.getClassTargetSelector()
                .getAllocatedTarget(node, allocation);
        return type == null ? null : new ConcreteTypeKey(type);
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
            final ProgramCounter instruction,
            final TypeReference type) {
        return delegate.getInstanceKeyForPEI(node, instruction, type);
    }

    @Override
    public InstanceKey getInstanceKeyForMetadataObject(
            final Object object,
            final TypeReference type) {
        return delegate.getInstanceKeyForMetadataObject(object, type);
    }
}
