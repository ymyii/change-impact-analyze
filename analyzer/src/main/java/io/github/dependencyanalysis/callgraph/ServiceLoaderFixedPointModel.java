package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.cfg.ControlFlowGraph;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.FileModule;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.ModuleEntry;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.classLoader.ProgramCounter;
import com.ibm.wala.classLoader.SyntheticClass;
import com.ibm.wala.core.util.strings.Atom;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.callgraph.Context;
import com.ibm.wala.ipa.callgraph.ContextItem;
import com.ibm.wala.ipa.callgraph.ContextKey;
import com.ibm.wala.ipa.callgraph.ContextSelector;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.impl.Everywhere;
import com.ibm.wala.ipa.callgraph.propagation.AllocationSiteInNode;
import com.ibm.wala.ipa.callgraph.propagation.ConstantKey;
import com.ibm.wala.ipa.callgraph.propagation.ConcreteTypeKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKey;
import com.ibm.wala.ipa.callgraph.propagation.InstanceKeyFactory;
import com.ibm.wala.ipa.callgraph.propagation.SSAContextInterpreter;
import com.ibm.wala.ipa.callgraph.propagation.SSAPropagationCallGraphBuilder;
import com.ibm.wala.ipa.callgraph.propagation.cfa
        .DelegatingSSAContextInterpreter;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.ipa.summaries.SummarizedMethod;
import com.ibm.wala.shrike.shrikeBT.Constants;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction.Dispatch;
import com.ibm.wala.ssa.ConstantValue;
import com.ibm.wala.ssa.DefUse;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.IRView;
import com.ibm.wala.ssa.ISSABasicBlock;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SSAInstructionFactory;
import com.ibm.wala.ssa.SSAOptions;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.types.annotations.Annotation;
import com.ibm.wala.util.intset.IntSet;
import com.ibm.wala.util.intset.IntSetUtil;
import com.ibm.wala.util.intset.MutableIntSet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

// Wiki: wiki/features/call-graph-engine.md - ServiceLoader fixed-point model
/** Installs the supported JDK 8 ServiceLoader protocol before graph solving. */
final class ServiceLoaderFixedPointModel {

    /** Service configuration resource prefix. */
    private static final String SERVICE_PREFIX = "META-INF/services/";

    /** ServiceLoader internal name. */
    private static final String SERVICE_LOADER = "java/util/ServiceLoader";

    /** Isolated Context for an unknown requested service. */
    private static final TypeReference UNKNOWN_SERVICE =
            TypeReference.findOrCreate(
                    ClassLoaderReference.Application,
                    "Lwala/serviceloader/UnknownService");

    /** Aggregate ZeroCFA service identity. */
    private static final TypeReference ALL_CONFIGURED_SERVICES =
            TypeReference.findOrCreate(
                    ClassLoaderReference.Application,
                    "Lwala/serviceloader/AllConfiguredServices");

    /** Service type context key. */
    private static final ContextKey SERVICE_TYPE_KEY = new ContextKey() {
        @Override
        public String toString() {
            return "SERVICE_LOADER_TYPE";
        }
    };

    /** ServiceLoader source identity context key. */
    private static final ContextKey LOADER_IDENTITY_KEY = new ContextKey() {
        @Override
        public String toString() {
            return "SERVICE_LOADER_IDENTITY";
        }
    };

    /** Parameters relevant to load and receiver-sensitive operations. */
    private static final IntSet FIRST_PARAMETER =
            IntSetUtil.make(new int[]{0});

    /** Valid providers by service type. */
    private final Map<TypeReference, List<ProviderDefinition>> providers;

    /** Synthetic iterator classes by service type. */
    private final Map<TypeReference, ProviderIteratorClass> iterators;

    /** Active Call Graph algorithm. */
    private final CallGraphAlgorithm algorithm;

    /** Stable coverage limitations accumulated while the graph is built. */
    private final Set<String> limitations = new LinkedHashSet<>();

    private ServiceLoaderFixedPointModel(
            final Map<TypeReference, List<ProviderDefinition>> values,
            final IClassHierarchy hierarchy,
            final CallGraphAlgorithm selectedAlgorithm) {
        providers = Collections.unmodifiableMap(new LinkedHashMap<>(values));
        algorithm = Objects.requireNonNull(
                selectedAlgorithm, "selectedAlgorithm");
        final Map<TypeReference, ProviderIteratorClass> classes =
                new LinkedHashMap<>();
        for (Map.Entry<TypeReference, List<ProviderDefinition>> entry
                : providers.entrySet()) {
            final ProviderIteratorClass iterator =
                    new ProviderIteratorClass(
                            entry.getKey(), entry.getValue(), hierarchy);
            hierarchy.addClass(iterator);
            classes.put(entry.getKey(), iterator);
        }
        if (algorithm == CallGraphAlgorithm.ZERO_CFA) {
            final ProviderIteratorClass aggregate =
                    new ProviderIteratorClass(
                            ALL_CONFIGURED_SERVICES,
                            aggregateProviders(values), hierarchy);
            hierarchy.addClass(aggregate);
            classes.put(ALL_CONFIGURED_SERVICES, aggregate);
        }
        final ProviderIteratorClass unknownIterator =
                new ProviderIteratorClass(
                        UNKNOWN_SERVICE, List.of(), hierarchy);
        hierarchy.addClass(unknownIterator);
        classes.put(UNKNOWN_SERVICE, unknownIterator);
        iterators = Collections.unmodifiableMap(classes);
    }

    /**
     * Reads Application resources and validates provider declarations.
     *
     * @param scope target analysis scope
     * @param hierarchy target class hierarchy
     * @param algorithm Call Graph algorithm
     * @return fixed-point model
     */
    static ServiceLoaderFixedPointModel create(
            final AnalysisScope scope,
            final IClassHierarchy hierarchy,
            final CallGraphAlgorithm algorithm) {
        final Map<String, Set<String>> resources = resources(scope);
        final Map<TypeReference, List<ProviderDefinition>> definitions =
                new LinkedHashMap<>();
        final List<String> initialLimitations = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : resources.entrySet()
                .stream().sorted(Map.Entry.comparingByKey()).toList()) {
            final IClass service = lookup(hierarchy, entry.getKey());
            if (service == null) {
                initialLimitations.add("Unresolved ServiceLoader service: "
                        + entry.getKey());
                continue;
            }
            final List<ProviderDefinition> valid = new ArrayList<>();
            for (String providerName : entry.getValue().stream()
                    .sorted().toList()) {
                final IClass provider = lookup(hierarchy, providerName);
                final IMethod constructor = provider == null ? null
                        : publicConstructor(provider);
                if (provider == null || provider.isAbstract()
                        || provider.isInterface() || !provider.isPublic()
                        || constructor == null
                        || !hierarchy.isAssignableFrom(service, provider)) {
                    initialLimitations.add(
                            "Invalid ServiceLoader provider: "
                                    + providerName + " for "
                                    + entry.getKey());
                    continue;
                }
                valid.add(new ProviderDefinition(provider, constructor));
            }
            definitions.put(service.getReference(), List.copyOf(valid));
        }
        final ServiceLoaderFixedPointModel result =
                new ServiceLoaderFixedPointModel(
                        definitions, hierarchy, algorithm);
        result.limitations.addAll(initialLimitations);
        return result;
    }

    private static List<ProviderDefinition> aggregateProviders(
            final Map<TypeReference, List<ProviderDefinition>> values) {
        final Map<String, ProviderDefinition> unique = new LinkedHashMap<>();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(TypeReference::toString)))
                .flatMap(entry -> entry.getValue().stream())
                .sorted(Comparator.comparing(provider ->
                        provider.type().getReference().toString()))
                .forEach(provider -> unique.putIfAbsent(
                        provider.type().getReference().toString(), provider));
        return List.copyOf(unique.values());
    }

    /**
     * Installs the model outside all previously installed WALA extensions.
     *
     * @param builder active fixed-point builder
     */
    void install(final SSAPropagationCallGraphBuilder builder) {
        builder.setContextSelector(new ServiceLoaderContextSelector(
                builder.getContextSelector(), this));
        builder.setContextInterpreter(
                new DelegatingSSAContextInterpreter(
                        new ServiceLoaderContextInterpreter(this),
                        builder.getCFAContextInterpreter()));
        if (algorithm == CallGraphAlgorithm.ZERO_CFA) {
            builder.setInstanceKeys(
                    new ZeroCfaServiceLoaderInstanceKeys(
                            builder.getInstanceKeys()));
        }
    }

    /** @return immutable stable limitations */
    List<String> limitations() {
        return limitations.stream().sorted().toList();
    }

    /**
     * @param node graph node
     * @return true for a synthetic ServiceLoader model node
     */
    boolean models(final CGNode node) {
        return node.getContext().isA(ServiceTypeContext.class)
                || node.getMethod().getDeclaringClass()
                instanceof ProviderIteratorClass;
    }

    /**
     * @param node graph node
     * @return service type modeled for a node, or null
     */
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

    /**
     * Returns a stable service label for synthetic edge evidence.
     *
     * @param node synthetic ServiceLoader model node
     * @return exact service type or ZeroCFA aggregate label
     */
    String serviceLabel(final CGNode node) {
        final TypeReference type = serviceType(node);
        return ALL_CONFIGURED_SERVICES.equals(type)
                ? "ALL_CONFIGURED_SERVICES" : String.valueOf(type);
    }

    private void unresolvedLoad(
            final CGNode caller,
            final CallSiteReference site,
            final String reason) {
        limitations.add("Unresolved ServiceLoader service type: caller="
                + caller.getMethod().getReference() + "; pc="
                + site.getProgramCounter() + "; reason=" + reason);
    }

    private static Map<String, Set<String>> resources(
            final AnalysisScope scope) {
        final Map<String, Set<String>> result = new LinkedHashMap<>();
        final List<Module> modules = new ArrayList<>(scope.getModules(
                ClassLoaderReference.Application));
        modules.sort(Comparator.comparing(Object::toString));
        for (Module module : modules) {
            final List<ModuleEntry> entries = new ArrayList<>();
            module.getEntries().forEachRemaining(entries::add);
            entries.stream().filter(entry -> !entry.isClassFile())
                    .filter(entry -> resourceName(entry) != null)
                    .sorted(Comparator.comparing(
                            ServiceLoaderFixedPointModel::resourceName))
                    .forEach(entry -> readResource(entry, result));
        }
        return result;
    }

    private static void readResource(
            final ModuleEntry entry,
            final Map<String, Set<String>> result) {
        final String resource = Objects.requireNonNull(
                resourceName(entry), "resourceName");
        final String service = resource.substring(
                SERVICE_PREFIX.length()).replace('.', '/');
        try (InputStream input = entry.getInputStream()) {
            final String content = new String(
                    input.readAllBytes(), StandardCharsets.UTF_8);
            final Set<String> values = result.computeIfAbsent(
                    service, ignored -> new LinkedHashSet<>());
            for (String line : content.split("\\R")) {
                final String provider = line.replaceFirst(
                        "#.*$", "").trim();
                if (!provider.isEmpty()) {
                    values.add(provider.replace('.', '/'));
                }
            }
        } catch (IOException | RuntimeException exception) {
            throw new CallGraphException(
                    "Unable to read ServiceLoader resource: "
                            + entry.getName(), exception);
        }
    }

    private static String resourceName(final ModuleEntry entry) {
        final String name = entry.getName().replace('\\', '/');
        if (name.startsWith(SERVICE_PREFIX)) {
            return name;
        }
        if (!(entry instanceof FileModule file)) {
            return null;
        }
        final String absolute = file.getAbsolutePath().replace('\\', '/');
        final int marker = absolute.indexOf('/' + SERVICE_PREFIX);
        return marker < 0 ? null : absolute.substring(marker + 1);
    }

    private static IClass lookup(
            final IClassHierarchy hierarchy,
            final String internalName) {
        for (IClass type : hierarchy) {
            if (internalName.equals(owner(type.getReference()))) {
                return type;
            }
        }
        return null;
    }

    private static IMethod publicConstructor(final IClass provider) {
        return provider.getDeclaredMethods().stream()
                .filter(IMethod::isInit)
                .filter(IMethod::isPublic)
                .filter(method -> "()V".equals(
                        method.getDescriptor().toString()))
                .findFirst().orElse(null);
    }

    private static String owner(final TypeReference type) {
        final String value = type.getName().toString();
        return value.startsWith("L") ? value.substring(1) : value;
    }

    private static boolean loadMethod(final MethodReference method) {
        if (!SERVICE_LOADER.equals(owner(method.getDeclaringClass()))) {
            return false;
        }
        final String name = method.getName().toString();
        final String descriptor = method.getDescriptor().toString();
        return "load".equals(name) && (descriptor.equals(
                "(Ljava/lang/Class;)Ljava/util/ServiceLoader;")
                || descriptor.equals("(Ljava/lang/Class;"
                + "Ljava/lang/ClassLoader;)Ljava/util/ServiceLoader;"))
                || "loadInstalled".equals(name) && descriptor.equals(
                "(Ljava/lang/Class;)Ljava/util/ServiceLoader;");
    }

    private static boolean iteratorMethod(final MethodReference method) {
        return SERVICE_LOADER.equals(owner(method.getDeclaringClass()))
                && "iterator".equals(method.getName().toString())
                && "()Ljava/util/Iterator;".equals(
                method.getDescriptor().toString());
    }

    private static boolean twoParameterLoad(final MethodReference method) {
        return SERVICE_LOADER.equals(owner(method.getDeclaringClass()))
                && "load".equals(method.getName().toString())
                && ("(Ljava/lang/Class;Ljava/lang/ClassLoader;)"
                + "Ljava/util/ServiceLoader;").equals(
                method.getDescriptor().toString());
    }

    private static TypeReference serviceType(final Context context) {
        if (!(context instanceof ServiceTypeContext serviceContext)) {
            return null;
        }
        return serviceContext.serviceType;
    }

    /** Service-specific Context for load and iterator calls. */
    private static final class ServiceTypeContext implements Context {

        /** Base WALA Context. */
        private final Context base;

        /** Exact requested service. */
        private final TypeReference serviceType;

        /** Explicit loader or implicit load-site identity. */
        private final ContextItem loaderIdentity;

        ServiceTypeContext(
                final Context baseContext,
                final TypeReference requestedType,
                final ContextItem identity) {
            base = Objects.requireNonNull(baseContext, "baseContext");
            serviceType = Objects.requireNonNull(
                    requestedType, "requestedType");
            loaderIdentity = Objects.requireNonNull(
                    identity, "loaderIdentity");
        }

        @Override
        public ContextItem get(final ContextKey name) {
            if (SERVICE_TYPE_KEY.equals(name)) {
                return new ServiceTypeItem(serviceType);
            }
            return LOADER_IDENTITY_KEY.equals(name)
                    ? loaderIdentity : base.get(name);
        }

        @Override
        public boolean equals(final Object other) {
            return other instanceof ServiceTypeContext that
                    && base.equals(that.base)
                    && serviceType.equals(that.serviceType)
                    && loaderIdentity.equals(that.loaderIdentity);
        }

        @Override
        public int hashCode() {
            return Objects.hash(base, serviceType, loaderIdentity);
        }

        @Override
        public String toString() {
            return "service-loader:" + serviceType + ":"
                    + loaderIdentity + ":" + base;
        }
    }

    /**
     * Context value wrapper.
     *
     * @param value requested service
     */
    private record ServiceTypeItem(TypeReference value)
            implements ContextItem {
    }

    /**
     * Context selector preserving service identity through allocations.
     *
     * @param base previously installed selector
     * @param model owning model
     */
    private record ServiceLoaderContextSelector(
            ContextSelector base,
            ServiceLoaderFixedPointModel model) implements ContextSelector {

        @Override
        public Context getCalleeTarget(
                final CGNode caller,
                final CallSiteReference site,
                final IMethod callee,
                final InstanceKey[] actualParameters) {
            final Context baseContext = base.getCalleeTarget(
                    caller, site, callee, actualParameters);
            final Context safeBaseContext = baseContext == null
                    ? Everywhere.EVERYWHERE : baseContext;
            if (loadMethod(callee.getReference())) {
                final TypeReference service = constantServiceType(
                        actualParameters);
                final ContextItem loaderIdentity = loaderIdentity(
                        caller, site, callee.getReference(),
                        actualParameters);
                if (service == null) {
                    model.unresolvedLoad(caller, site,
                            "non-constant Class argument");
                    return new ServiceTypeContext(
                            safeBaseContext, UNKNOWN_SERVICE,
                            loaderIdentity);
                }
                if (!model.providers.containsKey(service)) {
                    model.unresolvedLoad(caller, site,
                            "missing or unresolved service configuration "
                                    + service);
                    return new ServiceTypeContext(
                            safeBaseContext, UNKNOWN_SERVICE,
                            loaderIdentity);
                }
                return new ServiceTypeContext(
                        safeBaseContext, service, loaderIdentity);
            }
            if (iteratorMethod(callee.getReference())) {
                final ServiceTypeContext allocation = allocationContext(
                        actualParameters);
                return allocation == null ? baseContext
                        : new ServiceTypeContext(
                                safeBaseContext, allocation.serviceType,
                                allocation.loaderIdentity);
            }
            return baseContext;
        }

        @Override
        public IntSet getRelevantParameters(
                final CGNode caller,
                final CallSiteReference site) {
            final MutableIntSet result = IntSetUtil.makeMutableCopy(
                    base.getRelevantParameters(caller, site));
            if (loadMethod(site.getDeclaredTarget())
                    || iteratorMethod(site.getDeclaredTarget())) {
                result.addAll(FIRST_PARAMETER);
            }
            if (twoParameterLoad(site.getDeclaredTarget())) {
                result.add(1);
            }
            return result;
        }

        private ContextItem loaderIdentity(
                final CGNode caller,
                final CallSiteReference site,
                final MethodReference method,
                final InstanceKey[] parameters) {
            if (twoParameterLoad(method) && parameters != null
                    && parameters.length > 1 && parameters[1] != null) {
                return parameters[1];
            }
            return new LoadSiteIdentity(
                    caller.getGraphNodeId(), site.getProgramCounter(),
                    method.getName().toString());
        }

        private TypeReference constantServiceType(
                final InstanceKey[] parameters) {
            if (parameters == null || parameters.length == 0
                    || !(parameters[0] instanceof ConstantKey<?> key)) {
                return null;
            }
            if (key.getValue() instanceof IClass type) {
                return type.getReference();
            }
            return key.getValue() instanceof TypeReference type
                    ? type : null;
        }

        private ServiceTypeContext allocationContext(
                final InstanceKey[] parameters) {
            if (parameters == null || parameters.length == 0) {
                return null;
            }
            if (parameters[0]
                    instanceof AllocationSiteInNode allocation) {
                final Context context = allocation.getNode().getContext();
                return context instanceof ServiceTypeContext serviceContext
                        ? serviceContext : null;
            }
            if (parameters[0] instanceof ConstantKey<?> constant
                    && constant.getValue()
                    instanceof ServiceLoaderInstanceIdentity identity) {
                return new ServiceTypeContext(
                        Everywhere.EVERYWHERE,
                        identity.serviceType(),
                        identity.loaderIdentity());
            }
            if (model.algorithm == CallGraphAlgorithm.ZERO_CFA
                    && parameters[0] instanceof ConcreteTypeKey concrete
                    && SERVICE_LOADER.equals(owner(
                    concrete.type().getReference()))) {
                return new ServiceTypeContext(
                        Everywhere.EVERYWHERE,
                        ALL_CONFIGURED_SERVICES,
                        AggregateServiceIdentity.INSTANCE);
            }
            return null;
        }
    }

    /** Stable receiver identity after ZeroCFA class-based merging. */
    private enum AggregateServiceIdentity implements ContextItem {

        /** Single aggregate receiver identity. */
        INSTANCE
    }

    /**
     * Constant identity retained for modeled ServiceLoader allocations.
     *
     * @param serviceType requested service
     * @param loaderIdentity explicit loader or stable load site
     */
    private record ServiceLoaderInstanceIdentity(
            TypeReference serviceType,
            ContextItem loaderIdentity) {
    }

    /**
     * Keeps model-created ServiceLoader receivers constant-specific while
     * delegating every ordinary ZeroCFA allocation to class-based keys.
     *
     * @param delegate selected ZeroCFA instance-key policy
     */
    private record ZeroCfaServiceLoaderInstanceKeys(
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
                    instanceof ServiceTypeContext context)
                    || !SERVICE_LOADER.equals(owner(
                    allocation.getDeclaredType()))
                    || base == null) {
                return base;
            }
            return new ConstantKey<>(
                    new ServiceLoaderInstanceIdentity(
                            context.serviceType, context.loaderIdentity),
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

    /**
     * Implicit loader identity for load/loadInstalled calls.
     *
     * @param callerNode graph node id
     * @param bytecodePc call-site PC
     * @param operation load operation
     */
    private record LoadSiteIdentity(
            int callerNode,
            int bytecodePc,
            String operation) implements ContextItem {
    }

    /** Interpreter for service-specific ServiceLoader library calls. */
    private static final class ServiceLoaderContextInterpreter
            implements SSAContextInterpreter {

        /** Owning model. */
        private final ServiceLoaderFixedPointModel model;

        /** Generated IR cache. */
        private final Map<CGNode, IR> irs = new HashMap<>();

        ServiceLoaderContextInterpreter(
                final ServiceLoaderFixedPointModel value) {
            model = value;
        }

        @Override
        public boolean understands(final CGNode node) {
            return node.getContext().isA(ServiceTypeContext.class)
                    && (loadMethod(node.getMethod().getReference())
                    || iteratorMethod(node.getMethod().getReference()));
        }

        @Override
        public IR getIR(final CGNode node) {
            return irs.computeIfAbsent(node, this::makeIr);
        }

        private IR makeIr(final CGNode node) {
            final TypeReference service = serviceType(node.getContext());
            final MethodReference reference = node.getMethod().getReference();
            final MethodSummary summary = new MethodSummary(reference);
            final SSAInstructionFactory instructions = node.getMethod()
                    .getDeclaringClass().getClassLoader().getLanguage()
                    .instructionFactory();
            final int value = reference.getNumberOfParameters() + 2;
            final TypeReference allocated;
            if (loadMethod(reference)) {
                summary.setStatic(true);
                allocated = reference.getReturnType();
            } else {
                final ProviderIteratorClass iterator =
                        model.iterators.get(service);
                if (iterator == null) {
                    throw new IllegalStateException(
                            "Missing ServiceLoader iterator: " + service);
                }
                allocated = iterator.getReference();
            }
            summary.addStatement(instructions.NewInstruction(0, value,
                    NewSiteReference.make(0, allocated)));
            summary.addStatement(instructions.ReturnInstruction(
                    1, value, false));
            return new SummarizedMethod(reference, summary,
                    node.getMethod().getDeclaringClass()).makeIR(
                    node.getContext(), SSAOptions.defaultOptions());
        }

        @Override
        public Iterator<NewSiteReference> iterateNewSites(
                final CGNode node) {
            return getIR(node).iterateNewSites();
        }

        @Override
        public Iterator<FieldReference> iterateFieldsRead(
                final CGNode node) {
            return Collections.emptyIterator();
        }

        @Override
        public Iterator<FieldReference> iterateFieldsWritten(
                final CGNode node) {
            return Collections.emptyIterator();
        }

        @Override
        public boolean recordFactoryType(
                final CGNode node, final IClass klass) {
            return false;
        }

        @Override
        public Iterator<CallSiteReference> iterateCallSites(
                final CGNode node) {
            return getIR(node).iterateCallSites();
        }

        @Override
        public IRView getIRView(final CGNode node) {
            return getIR(node);
        }

        @Override
        public DefUse getDU(final CGNode node) {
            return new DefUse(getIR(node));
        }

        @Override
        public int getNumberOfStatements(final CGNode node) {
            return getIR(node).getInstructions().length;
        }

        @Override
        public ControlFlowGraph<SSAInstruction, ISSABasicBlock> getCFG(
                final CGNode node) {
            return getIR(node).getControlFlowGraph();
        }
    }

    /**
     * Valid provider and its required constructor.
     *
     * @param type provider type
     * @param constructor public zero-argument constructor
     */
    private record ProviderDefinition(IClass type, IMethod constructor) {
    }

    /** Synthetic provider iterator carrying one exact service identity. */
    private static final class ProviderIteratorClass extends SyntheticClass {

        /** Modeled service. */
        private final TypeReference serviceType;

        /** Iterator methods. */
        private final Map<Selector, IMethod> methods;

        ProviderIteratorClass(
                final TypeReference service,
                final List<ProviderDefinition> providerDefinitions,
                final IClassHierarchy hierarchy) {
            super(iteratorReference(service), hierarchy);
            serviceType = service;
            final Map<Selector, IMethod> values = new LinkedHashMap<>();
            final IMethod next = next(providerDefinitions);
            final IMethod hasNext = hasNext();
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
                final List<ProviderDefinition> providerDefinitions) {
            final MethodReference reference = MethodReference.findOrCreate(
                    getReference(), Selector.make(
                            "next()Ljava/lang/Object;"));
            final MethodSummary summary = new MethodSummary(reference);
            final SSAInstructionFactory instructions = getClassLoader()
                    .getInstructionFactory();
            int index = 0;
            int value = 2;
            final int[] instances = new int[providerDefinitions.size()];
            for (int position = 0;
                    position < providerDefinitions.size(); position++) {
                final ProviderDefinition provider =
                        providerDefinitions.get(position);
                final int instance = value++;
                instances[position] = instance;
                summary.addStatement(instructions.NewInstruction(
                        index, instance, NewSiteReference.make(
                                index, provider.type().getReference())));
                index++;
                summary.addStatement(instructions.InvokeInstruction(
                        index, new int[]{instance}, value++,
                        CallSiteReference.make(index,
                                provider.constructor().getReference(),
                                Dispatch.SPECIAL), null));
                index++;
            }
            final int result;
            if (instances.length == 0) {
                result = value;
                summary.addConstant(result, new ConstantValue(null));
            } else if (instances.length == 1) {
                result = instances[0];
            } else {
                result = value;
                summary.addStatement(instructions.PhiInstruction(
                        index++, result, instances));
            }
            summary.addStatement(instructions.ReturnInstruction(
                    index, result, false));
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
