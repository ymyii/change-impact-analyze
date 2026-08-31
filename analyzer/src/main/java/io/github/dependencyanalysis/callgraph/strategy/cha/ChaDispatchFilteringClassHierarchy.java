package io.github.dependencyanalysis.callgraph.strategy.cha;

import com.ibm.wala.classLoader.ClassLoaderFactory;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IClassLoader;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;

import java.util.Collection;
import java.util.Comparator;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * IClassHierarchy decorator filtering selected CHA targets before expansion.
 */
public final class ChaDispatchFilteringClassHierarchy
        implements IClassHierarchy {

    /** Stable target ordering independent of hierarchy hash iteration. */
    private static final Comparator<IMethod> METHOD_ORDER = Comparator
            .comparing((IMethod method) -> method.getDeclaringClass()
                    .getClassLoader().getReference().toString())
            .thenComparing(method -> method.getDeclaringClass()
                    .getName().toString())
            .thenComparing(method -> method.getName().toString())
            .thenComparing(method -> method.getDescriptor().toString());

    /** Original target hierarchy. */
    private final IClassHierarchy delegate;

    /** Immutable target retention policy. */
    private final ChaDispatchTargetPolicy policy;

    /**
     * Creates a filtered hierarchy view.
     *
     * @param hierarchy original hierarchy
     * @param targetPolicy dispatch target policy
     */
    public ChaDispatchFilteringClassHierarchy(
            final IClassHierarchy hierarchy,
            final ChaDispatchTargetPolicy targetPolicy) {
        delegate = Objects.requireNonNull(hierarchy, "hierarchy");
        policy = Objects.requireNonNull(targetPolicy, "targetPolicy");
    }

    @Override
    public Set<IMethod> getPossibleTargets(final MethodReference reference) {
        return filter(reference, delegate.getPossibleTargets(reference));
    }

    @Override
    public Set<IMethod> getPossibleTargets(
            final IClass receiver, final MethodReference reference) {
        return filter(reference,
                delegate.getPossibleTargets(receiver, reference));
    }

    private Set<IMethod> filter(
            final MethodReference reference,
            final Set<IMethod> targets) {
        final MethodReference policyReference = resolveDeclaration(reference);
        if (!policy.filtersDispatch(policyReference)) {
            return targets;
        }
        final LinkedHashSet<IMethod> retained = new LinkedHashSet<>();
        targets.stream().filter(target ->
                        policy.retainsDispatchTarget(
                                policyReference, target))
                .sorted(METHOD_ORDER)
                .forEach(retained::add);
        return Collections.unmodifiableSet(retained);
    }

    /**
     * Resolves the declared type through parent delegation before policy use.
     *
     * <p>Shrike records bytecode references with the caller's loader. An
     * Application reference to a JDK type therefore becomes Primordial or
     * Extension only after hierarchy lookup.</p>
     *
     * @param reference original call-site declaration
     * @return resolved declaration, or the original unresolved reference
     */
    private MethodReference resolveDeclaration(
            final MethodReference reference) {
        final IClass resolved = delegate.lookupClass(
                reference.getDeclaringClass());
        if (resolved == null || !resolved.getName().equals(
                reference.getDeclaringClass().getName())) {
            return reference;
        }
        final TypeReference resolvedType = resolved.getReference();
        return resolvedType.equals(reference.getDeclaringClass())
                ? reference : MethodReference.findOrCreate(
                        resolvedType, reference.getSelector());
    }

    @Override
    public ClassLoaderFactory getFactory() {
        return delegate.getFactory();
    }

    @Override
    public AnalysisScope getScope() {
        return delegate.getScope();
    }

    @Override
    public IClassLoader[] getLoaders() {
        return delegate.getLoaders();
    }

    @Override
    public IClassLoader getLoader(final ClassLoaderReference reference) {
        return delegate.getLoader(reference);
    }

    @Override
    public boolean addClass(final IClass type) {
        return delegate.addClass(type);
    }

    @Override
    public int getNumberOfClasses() {
        return delegate.getNumberOfClasses();
    }

    @Override
    public boolean isRootClass(final IClass type) {
        return delegate.isRootClass(type);
    }

    @Override
    public IClass getRootClass() {
        return delegate.getRootClass();
    }

    @Override
    public int getNumber(final IClass type) {
        return delegate.getNumber(type);
    }

    @Override
    public Set<TypeReference> getUnresolvedClasses() {
        return delegate.getUnresolvedClasses();
    }

    @Override
    public IMethod resolveMethod(final MethodReference reference) {
        return filter(delegate.resolveMethod(reference));
    }

    @Override
    public IField resolveField(final FieldReference reference) {
        return delegate.resolveField(reference);
    }

    @Override
    public IField resolveField(
            final IClass type, final FieldReference reference) {
        return delegate.resolveField(type, reference);
    }

    @Override
    public IMethod resolveMethod(
            final IClass type, final Selector selector) {
        return filter(delegate.resolveMethod(type, selector));
    }

    private IMethod filter(final IMethod target) {
        return target == null || !policy.filters()
                || policy.retains(target) ? target : null;
    }

    @Override
    public IClass lookupClass(final TypeReference reference) {
        return delegate.lookupClass(reference);
    }

    @Override
    public boolean isInterface(final TypeReference reference) {
        return delegate.isInterface(reference);
    }

    @Override
    public IClass getLeastCommonSuperclass(
            final IClass first, final IClass second) {
        return delegate.getLeastCommonSuperclass(first, second);
    }

    @Override
    public TypeReference getLeastCommonSuperclass(
            final TypeReference first, final TypeReference second) {
        return delegate.getLeastCommonSuperclass(first, second);
    }

    @Override
    public boolean isSubclassOf(
            final IClass child, final IClass parent) {
        return delegate.isSubclassOf(child, parent);
    }

    @Override
    public boolean implementsInterface(
            final IClass type, final IClass interfaceType) {
        return delegate.implementsInterface(type, interfaceType);
    }

    @Override
    public Collection<IClass> computeSubClasses(
            final TypeReference reference) {
        return delegate.computeSubClasses(reference);
    }

    @Override
    public Collection<TypeReference> getJavaLangErrorTypes() {
        return delegate.getJavaLangErrorTypes();
    }

    @Override
    public Collection<TypeReference> getJavaLangRuntimeExceptionTypes() {
        return delegate.getJavaLangRuntimeExceptionTypes();
    }

    @Override
    public Set<IClass> getImplementors(final TypeReference reference) {
        return delegate.getImplementors(reference);
    }

    @Override
    public int getNumberOfImmediateSubclasses(final IClass type) {
        return delegate.getNumberOfImmediateSubclasses(type);
    }

    @Override
    public Collection<IClass> getImmediateSubclasses(final IClass type) {
        return delegate.getImmediateSubclasses(type);
    }

    @Override
    public boolean isAssignableFrom(
            final IClass parent, final IClass child) {
        return delegate.isAssignableFrom(parent, child);
    }

    @Override
    public void clearCaches() {
        delegate.clearCaches();
    }

    @Override
    public Iterator<IClass> iterator() {
        return delegate.iterator();
    }
}
