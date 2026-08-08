package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.classLoader.ProgramCounter;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.propagation.ConstantKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKeyFactory;
import com.ibm.wala.types.TypeReference;

import java.util.Objects;

/**
 * ZeroCFA receiver identity wrapper owned by its strategy installer.
 *
 * @param delegate base WALA instance-key factory
 */
record ZeroCfaServiceLoaderInstanceKeys(
        InstanceKeyFactory delegate) implements InstanceKeyFactory {

    ZeroCfaServiceLoaderInstanceKeys {
        Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public InstanceKey getInstanceKeyForAllocation(
            final CGNode node,
            final NewSiteReference allocation) {
        final InstanceKey base = delegate.getInstanceKeyForAllocation(
                node, allocation);
        if (!(node.getContext()
                instanceof ZeroCfaServiceLoaderInstaller
                .ZeroServiceContext context)
                || !ServiceLoaderProtocol.isServiceLoader(
                        allocation.getDeclaredType())
                || base == null) {
            return base;
        }
        return new ConstantKey<>(
                new ZeroCfaServiceLoaderIdentity(
                        context.serviceType(), context.loaderIdentity()),
                base.concreteType());
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
