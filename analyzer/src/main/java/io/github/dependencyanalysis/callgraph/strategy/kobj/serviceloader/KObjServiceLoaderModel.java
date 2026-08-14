package io.github.dependencyanalysis.callgraph.strategy.kobj.serviceloader;

import io.github.dependencyanalysis.callgraph.protocol.ModelLimitation;
import io.github.dependencyanalysis.callgraph.protocol.serviceloader.ServiceLoaderProtocol;
import io.github.dependencyanalysis.callgraph.protocol.serviceloader.ServiceLoaderProtocolIndex;
import io.github.dependencyanalysis.callgraph.protocol.serviceloader.ServiceLoaderProviderDefinition;
import io.github.dependencyanalysis.callgraph.protocol.serviceloader.ServiceLoaderProviderSummaryMethods;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.SyntheticClass;
import com.ibm.wala.core.util.strings.Atom;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.ContextItem;
import com.ibm.wala.ipa.callgraph.ContextKey;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.shrike.shrikeBT.Constants;
import com.ibm.wala.types.ClassLoaderReference;
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

/** Strategy-owned ServiceLoader synthetic execution. */
public final class KObjServiceLoaderModel {

    /** Service identity visible through WALA Context wrappers. */
    public static final ContextKey SERVICE_TYPE_KEY = new ContextKey() {
        @Override
        public String toString() {
            return "K_OBJ_SERVICE_LOADER_MODELED_TYPE";
        }
    };

    /** Iterator classes by exact or unknown service type. */
    private final Map<TypeReference, ProviderIteratorClass> iterators;

    /** Resource/declaration validation limitations. */
    private final List<ModelLimitation> limitations;

    KObjServiceLoaderModel(
            final ServiceLoaderProtocolIndex index,
            final IClassHierarchy hierarchy) {
        Objects.requireNonNull(index, "index");
        Objects.requireNonNull(hierarchy, "hierarchy");
        limitations = index.limitations();
        final Map<TypeReference, ProviderIteratorClass> classes =
                new LinkedHashMap<>();
        index.providers().forEach((service, providers) -> add(
                classes, service, providers, hierarchy));
        add(classes, ServiceLoaderProtocol.unknownService(),
                List.of(), hierarchy);
        iterators = Collections.unmodifiableMap(classes);
    }

    List<ModelLimitation> limitations() {
        return limitations;
    }

    TypeReference iteratorType(final TypeReference service) {
        final ProviderIteratorClass iterator = iterators.get(service);
        if (iterator == null) {
            throw new IllegalStateException(
                    "Missing k-obj ServiceLoader iterator: "
                            + service);
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
        final ContextItem item = node.getContext().get(SERVICE_TYPE_KEY);
        if (item instanceof ServiceTypeItem service) {
            return service.value();
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

    private static void add(
            final Map<TypeReference, ProviderIteratorClass> classes,
            final TypeReference service,
            final List<ServiceLoaderProviderDefinition> providers,
            final IClassHierarchy hierarchy) {
        final ProviderIteratorClass iterator =
                new ProviderIteratorClass(service, providers, hierarchy);
        hierarchy.addClass(iterator);
        classes.put(service, iterator);
    }

    /** @param value modeled service type */
    private record ServiceTypeItem(TypeReference value)
            implements ContextItem {
    }

    /** Allocation-sensitive provider-return iterator. */
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
            final IMethod next = ServiceLoaderProviderSummaryMethods.next(
                    getReference(), this, providers);
            final IMethod hasNext =
                    ServiceLoaderProviderSummaryMethods.hasNext(
                            getReference(), this);
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
