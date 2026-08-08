package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.classLoader.SyntheticClass;
import com.ibm.wala.core.util.strings.Atom;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextItem;
import com.ibm.wala.ipa.callgraph.ContextKey;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.ipa.summaries.SummarizedMethod;
import com.ibm.wala.shrike.shrikeBT.Constants;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction.Dispatch;
import com.ibm.wala.ssa.ConstantValue;
import com.ibm.wala.ssa.SSAInstructionFactory;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.types.annotations.Annotation;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** RTA-only ServiceLoader reachability summaries without return carriers. */
final class RtaServiceLoaderModel {

    /** RTA service identity visible through WALA Context wrappers. */
    static final ContextKey SERVICE_TYPE_KEY = new ContextKey() {
        @Override
        public String toString() {
            return "RTA_SERVICE_LOADER_MODELED_TYPE";
        }
    };

    /** Synthetic iterator classes by exact service type. */
    private final Map<TypeReference, ProviderIteratorClass> iterators;

    /** Resource/declaration validation limitations. */
    private final List<ModelLimitation> limitations;

    RtaServiceLoaderModel(
            final ServiceLoaderProtocolIndex index,
            final IClassHierarchy hierarchy) {
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(hierarchy, "hierarchy");
        limitations = index.limitations();
        final Map<TypeReference, ProviderIteratorClass> classes =
                new LinkedHashMap<>();
        index.providers().forEach((service, providers) -> {
            final ProviderIteratorClass iterator =
                    new ProviderIteratorClass(
                            service, providers, hierarchy);
            hierarchy.addClass(iterator);
            classes.put(service, iterator);
        });
        final ProviderIteratorClass unknown = new ProviderIteratorClass(
                ServiceLoaderProtocol.unknownService(), List.of(), hierarchy);
        hierarchy.addClass(unknown);
        classes.put(ServiceLoaderProtocol.unknownService(), unknown);
        iterators = Collections.unmodifiableMap(classes);
    }

    List<ModelLimitation> limitations() {
        return limitations;
    }

    TypeReference iteratorType(final TypeReference service) {
        final ProviderIteratorClass iterator = iterators.get(service);
        if (iterator == null) {
            throw new IllegalStateException(
                    "Missing RTA ServiceLoader iterator: " + service);
        }
        return iterator.getReference();
    }

    boolean models(final CGNode node) {
        return serviceType(node) != null
                || node.getMethod().getDeclaringClass()
                instanceof ProviderIteratorClass;
    }

    String serviceLabel(final CGNode node) {
        return String.valueOf(serviceType(node));
    }

    TypeReference serviceType(final CGNode node) {
        final TypeReference contextType = serviceType(node.getContext());
        if (contextType != null) {
            return contextType;
        }
        if (node.getMethod().getDeclaringClass()
                instanceof ProviderIteratorClass iterator) {
            return iterator.serviceType;
        }
        return null;
    }

    static ContextItem serviceTypeItem(final TypeReference service) {
        return new ServiceTypeItem(service);
    }

    private static TypeReference serviceType(final Context context) {
        final ContextItem item = context.get(SERVICE_TYPE_KEY);
        return item instanceof ServiceTypeItem service
                ? service.value() : null;
    }

    /** @param value modeled service type */
    private record ServiceTypeItem(TypeReference value)
            implements ContextItem {
    }

    /** RTA iterator allocates providers for reachability but returns null. */
    private static final class ProviderIteratorClass extends SyntheticClass {

        /** Modeled service. */
        private final TypeReference serviceType;

        /** Iterator methods. */
        private final Map<Selector, IMethod> methods;

        ProviderIteratorClass(
                final TypeReference service,
                final List<ServiceLoaderProviderDefinition> providers,
                final IClassHierarchy hierarchy) {
            super(iteratorReference(service), hierarchy);
            serviceType = service;
            final IMethod next = next(providers);
            final IMethod hasNext = hasNext();
            final Map<Selector, IMethod> values = new LinkedHashMap<>();
            values.put(next.getSelector(), next);
            values.put(hasNext.getSelector(), hasNext);
            methods = Collections.unmodifiableMap(values);
        }

        private static TypeReference iteratorReference(
                final TypeReference service) {
            final String name = service.getName().toString()
                    .replace('/', '$').replace(';', '$');
            return TypeReference.findOrCreate(
                    ClassLoaderReference.Application,
                    "Lwala/serviceloader/Iterator$" + name);
        }

        private IMethod next(
                final List<ServiceLoaderProviderDefinition> providers) {
            final MethodReference reference = MethodReference.findOrCreate(
                    getReference(), Selector.make(
                            "next()Ljava/lang/Object;"));
            final MethodSummary summary = new MethodSummary(reference);
            final SSAInstructionFactory instructions = getClassLoader()
                    .getInstructionFactory();
            int instructionIndex = 0;
            int value = 2;
            for (ServiceLoaderProviderDefinition provider : providers) {
                final int instance = value++;
                summary.addStatement(instructions.NewInstruction(
                        instructionIndex, instance, NewSiteReference.make(
                                instructionIndex,
                                provider.type().getReference())));
                instructionIndex++;
                summary.addStatement(instructions.InvokeInstruction(
                        instructionIndex, new int[]{instance}, value++,
                        CallSiteReference.make(instructionIndex,
                                provider.constructor().getReference(),
                                Dispatch.SPECIAL), null));
                instructionIndex++;
            }
            final int result = value;
            summary.addConstant(result, new ConstantValue(null));
            summary.addStatement(instructions.ReturnInstruction(
                    instructionIndex, result, false));
            return new SummarizedMethod(reference, summary, this);
        }

        private IMethod hasNext() {
            final MethodReference reference = MethodReference.findOrCreate(
                    getReference(), Selector.make("hasNext()Z"));
            final MethodSummary summary = new MethodSummary(reference);
            summary.addConstant(2, new ConstantValue(1));
            summary.addStatement(getClassLoader().getInstructionFactory()
                    .ReturnInstruction(0, 2, true));
            return new SummarizedMethod(reference, summary, this);
        }

        @Override
        public boolean isPublic() {
            return true;
        }

        @Override
        public boolean isPrivate() {
            return false;
        }

        @Override
        public int getModifiers() {
            return Constants.ACC_PUBLIC | Constants.ACC_FINAL
                    | Constants.ACC_SUPER;
        }

        @Override
        public IClass getSuperclass() {
            return getClassHierarchy().getRootClass();
        }

        @Override
        public Collection<? extends IClass> getDirectInterfaces() {
            final IClass iterator = getClassHierarchy().lookupClass(
                    TypeReference.findOrCreate(
                            ClassLoaderReference.Primordial,
                            "Ljava/util/Iterator"));
            return iterator == null ? List.of() : List.of(iterator);
        }

        @Override
        public Collection<IClass> getAllImplementedInterfaces() {
            final Set<IClass> result = new LinkedHashSet<>();
            for (IClass direct : getDirectInterfaces()) {
                result.add(direct);
                result.addAll(direct.getAllImplementedInterfaces());
            }
            return result;
        }

        @Override
        public IMethod getMethod(final Selector selector) {
            return methods.get(selector);
        }

        @Override
        public IField getField(final Atom name) {
            return null;
        }

        @Override
        public IMethod getClassInitializer() {
            return null;
        }

        @Override
        public Collection<? extends IMethod> getDeclaredMethods() {
            return methods.values();
        }

        @Override
        public Collection<IField> getAllInstanceFields() {
            return List.of();
        }

        @Override
        public Collection<IField> getAllStaticFields() {
            return List.of();
        }

        @Override
        public Collection<IField> getAllFields() {
            return List.of();
        }

        @Override
        public Collection<? extends IMethod> getAllMethods() {
            return methods.values();
        }

        @Override
        public Collection<IField> getDeclaredInstanceFields() {
            return List.of();
        }

        @Override
        public Collection<IField> getDeclaredStaticFields() {
            return List.of();
        }

        @Override
        public boolean isReferenceType() {
            return true;
        }

        @Override
        public Collection<Annotation> getAnnotations() {
            return List.of();
        }
    }
}
