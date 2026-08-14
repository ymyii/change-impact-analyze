package io.github.dependencyanalysis.callgraph.protocol.invokedynamic;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.classLoader.SyntheticClass;
import com.ibm.wala.core.util.strings.Atom;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.ipa.summaries.SummarizedMethod;
import com.ibm.wala.shrike.shrikeBT.Constants;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction.Dispatch;
import com.ibm.wala.shrike.shrikeCT.BootstrapMethodsReader.BootstrapMethod;
import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.SSAInstructionFactory;
import com.ibm.wala.ssa.SSAInvokeDynamicInstruction;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.Descriptor;
import com.ibm.wala.types.FieldReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;
import com.ibm.wala.types.annotations.Annotation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Java 8 LambdaMetafactory.altMetafactory fixed-point model. */
final class AltMetafactoryBootstrapModel
        implements InvokeDynamicBootstrapModel {

    /** altMetafactory bootstrap key. */
    static final InvokeDynamicBootstrapKey KEY =
            new InvokeDynamicBootstrapKey(
                    "java/lang/invoke/LambdaMetafactory",
                    "altMetafactory",
                    "(Ljava/lang/invoke/MethodHandles$Lookup;"
                            + "Ljava/lang/String;"
                            + "Ljava/lang/invoke/MethodType;"
                            + "[Ljava/lang/Object;)"
                            + "Ljava/lang/invoke/CallSite;");

    /** FLAG_SERIALIZABLE. */
    private static final int FLAG_SERIALIZABLE = 1;

    /** FLAG_MARKERS. */
    private static final int FLAG_MARKERS = 2;

    /** FLAG_BRIDGES. */
    private static final int FLAG_BRIDGES = 4;

    /** Flags bootstrap argument position. */
    private static final int FLAGS_ARGUMENT = 3;

    /** First variable protocol argument position and minimum argument count. */
    private static final int PROTOCOL_ARGUMENT = 4;

    /** Stable hexadecimal synthetic-name radix. */
    private static final int HEX_RADIX = 16;

    /** Method-handle kinds. */
    private static final int REF_INVOKEVIRTUAL = 5;

    /** Static method handle. */
    private static final int REF_INVOKESTATIC = 6;

    /** Special method handle. */
    private static final int REF_INVOKESPECIAL = 7;

    /** Constructor method handle. */
    private static final int REF_NEWINVOKESPECIAL = 8;

    /** Interface method handle. */
    private static final int REF_INVOKEINTERFACE = 9;

    /** Per-hierarchy factory cache keyed by exact reachable call site. */
    private final Map<IClassHierarchy, Map<FactoryKey, IMethod>> factories =
            new WeakHashMap<>();

    @Override
    public synchronized InvokeDynamicModelResult model(
            final CGNode caller,
            final CallSiteReference site,
            final SSAInvokeDynamicInstruction instruction,
            final IClassHierarchy hierarchy,
            final SyntheticClassFactory syntheticClasses) {
        final FactoryKey key = new FactoryKey(
                caller.getMethod().getReference(),
                site.getProgramCounter());
        final Map<FactoryKey, IMethod> hierarchyFactories =
                factories.computeIfAbsent(hierarchy,
                        ignored -> new HashMap<>());
        final IMethod existing = hierarchyFactories.get(key);
        if (existing != null) {
            return InvokeDynamicModelResult.modeled(existing);
        }
        try {
            final AltLambdaClass lambda = new AltLambdaClass(
                    caller, site, instruction, hierarchy);
            syntheticClasses.register(lambda);
            final MethodSummary summary = factorySummary(
                    caller, site, instruction, lambda);
            final IClass declaring = hierarchy.lookupClass(
                    summary.getMethod().getDeclaringClass());
            if (declaring == null) {
                return InvokeDynamicModelResult.unsupported(
                        "altMetafactory factory declaring class unresolved");
            }
            final IMethod target = new SummarizedMethod(
                    summary.getMethod(), summary, declaring);
            hierarchyFactories.put(key, target);
            return InvokeDynamicModelResult.modeled(target);
        } catch (RuntimeException exception) {
            return InvokeDynamicModelResult.unsupported(
                    "altMetafactory unsupported: "
                            + exception.getClass().getSimpleName() + ": "
                            + exception.getMessage());
        }
    }

    private MethodSummary factorySummary(
            final CGNode caller,
            final CallSiteReference site,
            final SSAInvokeDynamicInstruction instruction,
            final AltLambdaClass lambda) {
        final MethodReference target = site.getDeclaredTarget();
        final String callerName = caller.getMethod().getDeclaringClass()
                .getName().toString().replace("/", "$").substring(1);
        final MethodReference reference = MethodReference.findOrCreate(
                target.getDeclaringClass(),
                Atom.findOrCreateUnicodeAtom(target.getName() + "$"
                        + callerName + "$"
                        + callerMethodId(caller) + "$"
                        + site.getProgramCounter()),
                target.getDescriptor());
        final MethodSummary summary = new MethodSummary(reference);
        summary.setStatic(site.isStatic());
        final SSAInstructionFactory instructions = caller.getMethod()
                .getDeclaringClass().getClassLoader()
                .getInstructionFactory();
        int index = 0;
        final int instance = target.getNumberOfParameters() + 2;
        summary.addStatement(instructions.NewInstruction(
                index, instance, NewSiteReference.make(
                        index, lambda.getReference())));
        index++;
        for (int position = 0;
                position < target.getNumberOfParameters(); position++) {
            summary.addStatement(instructions.PutInstruction(
                    index++, instance, position + 1,
                    lambda.capture(position).getReference()));
        }
        summary.addStatement(instructions.ReturnInstruction(
                index, instance, false));
        return summary;
    }

    /** Synthetic lambda class with marker and bridge support. */
    private static final class AltLambdaClass extends SyntheticClass {

        /** Original invokedynamic instruction. */
        private final SSAInvokeDynamicInstruction invoke;

        /** Captured value fields. */
        private final Map<Atom, IField> fields;

        /** SAM and bridge trampolines. */
        private final Map<Selector, IMethod> methods;

        /** Direct functional and marker interfaces. */
        private final List<IClass> interfaces;

        AltLambdaClass(
                final CGNode caller,
                final CallSiteReference site,
                final SSAInvokeDynamicInstruction instruction,
                final IClassHierarchy hierarchy) {
            super(typeReference(caller, site), hierarchy);
            invoke = instruction;
            final AltProtocol protocol = parseProtocol(instruction);
            interfaces = interfaces(protocol, hierarchy);
            fields = fields();
            final Map<Selector, IMethod> values = new LinkedHashMap<>();
            for (String descriptor : protocol.trampolineDescriptors()) {
                final IMethod method = trampoline(descriptor);
                values.put(method.getSelector(), method);
            }
            methods = Collections.unmodifiableMap(values);
        }

        private static TypeReference typeReference(
                final CGNode caller,
                final CallSiteReference site) {
            final String callerName = caller.getMethod().getDeclaringClass()
                    .getName().toString().replace("/", "$").substring(1);
            return TypeReference.findOrCreate(
                    ClassLoaderReference.Primordial,
                    "Lwala/lambda/Alt$" + callerName + "$"
                            + callerMethodId(caller) + "$"
                            + site.getProgramCounter());
        }

        private static AltProtocol parseProtocol(
                final SSAInvokeDynamicInstruction instruction) {
            final BootstrapMethod bootstrap = instruction.getBootstrap();
            try {
                if (bootstrap.callArgumentCount() < PROTOCOL_ARGUMENT) {
                    throw new IllegalArgumentException(
                            "altMetafactory arguments are incomplete");
                }
                final String sam = methodType(bootstrap, 0);
                methodType(bootstrap, 2);
                final int flags = integer(bootstrap, FLAGS_ARGUMENT);
                if ((flags & ~(FLAG_SERIALIZABLE
                        | FLAG_MARKERS | FLAG_BRIDGES)) != 0) {
                    throw new IllegalArgumentException(
                            "altMetafactory flags are unsupported: "
                                    + flags);
                }
                int cursor = PROTOCOL_ARGUMENT;
                final List<String> markers = new ArrayList<>();
                if ((flags & FLAG_MARKERS) != 0) {
                    final int count = integer(bootstrap, cursor++);
                    for (int index = 0; index < count; index++) {
                        markers.add(className(bootstrap, cursor++));
                    }
                }
                final List<String> bridges = new ArrayList<>();
                if ((flags & FLAG_BRIDGES) != 0) {
                    final int count = integer(bootstrap, cursor++);
                    for (int index = 0; index < count; index++) {
                        bridges.add(methodType(bootstrap, cursor++));
                    }
                }
                if (cursor != bootstrap.callArgumentCount()) {
                    throw new IllegalArgumentException(
                            "altMetafactory trailing arguments");
                }
                final LinkedHashSet<String> descriptors =
                        new LinkedHashSet<>();
                descriptors.add(sam);
                descriptors.addAll(bridges);
                return new AltProtocol(flags, markers,
                        List.copyOf(descriptors));
            } catch (InvalidClassFileException exception) {
                throw new IllegalArgumentException(
                        "invalid altMetafactory constant pool", exception);
            }
        }

        private static int integer(
                final BootstrapMethod bootstrap,
                final int argument) throws InvalidClassFileException {
            return bootstrap.getCP().getCPInt(
                    bootstrap.callArgumentIndex(argument));
        }

        private static String className(
                final BootstrapMethod bootstrap,
                final int argument) throws InvalidClassFileException {
            return bootstrap.getCP().getCPClass(
                    bootstrap.callArgumentIndex(argument));
        }

        private static String methodType(
                final BootstrapMethod bootstrap,
                final int argument) throws InvalidClassFileException {
            return bootstrap.getCP().getCPMethodType(
                    bootstrap.callArgumentIndex(argument));
        }

        private List<IClass> interfaces(
                final AltProtocol protocol,
                final IClassHierarchy hierarchy) {
            final LinkedHashSet<IClass> result = new LinkedHashSet<>();
            final IClass functional = hierarchy.lookupClass(
                    invoke.getDeclaredResultType());
            if (functional == null) {
                throw new IllegalArgumentException(
                        "functional interface unresolved: "
                                + invoke.getDeclaredResultType());
            }
            result.add(functional);
            for (String marker : protocol.markers()) {
                final IClass markerType = requireType(hierarchy, marker);
                if (!markerType.isInterface()) {
                    throw new IllegalArgumentException(
                            "marker type is not an interface: " + marker);
                }
                result.add(markerType);
            }
            if ((protocol.flags() & FLAG_SERIALIZABLE) != 0) {
                result.add(requireType(hierarchy, "java/io/Serializable"));
            }
            return List.copyOf(result);
        }

        private IClass requireType(
                final IClassHierarchy hierarchy,
                final String name) {
            for (IClass type : hierarchy) {
                final String value = type.getName().toString();
                if (name.equals(value.startsWith("L")
                        ? value.substring(1) : value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException(
                    "marker interface unresolved: " + name);
        }

        private Map<Atom, IField> fields() {
            final Map<Atom, IField> result = new LinkedHashMap<>();
            for (int index = 0;
                    index < invoke.getNumberOfPositionalParameters();
                    index++) {
                final int position = index;
                final Atom name = captureName(position);
                result.put(name, new IField() {
                    @Override
                    public IClass getDeclaringClass() {
                        return AltLambdaClass.this;
                    }

                    @Override
                    public Atom getName() {
                        return name;
                    }

                    @Override
                    public Collection<Annotation> getAnnotations() {
                        return List.of();
                    }

                    @Override
                    public IClassHierarchy getClassHierarchy() {
                        return AltLambdaClass.this.getClassHierarchy();
                    }

                    @Override
                    public TypeReference getFieldTypeReference() {
                        return invoke.getDeclaredTarget()
                                .getParameterType(position);
                    }

                    @Override
                    public FieldReference getReference() {
                        return FieldReference.findOrCreate(
                                AltLambdaClass.this.getReference(),
                                name, getFieldTypeReference());
                    }

                    @Override
                    public boolean isFinal() {
                        return true;
                    }

                    @Override
                    public boolean isPrivate() {
                        return true;
                    }

                    @Override
                    public boolean isProtected() {
                        return false;
                    }

                    @Override
                    public boolean isPublic() {
                        return false;
                    }

                    @Override
                    public boolean isStatic() {
                        return false;
                    }

                    @Override
                    public boolean isVolatile() {
                        return false;
                    }
                });
            }
            return result;
        }

        IField capture(final int index) {
            return fields.get(captureName(index));
        }

        private Atom captureName(final int index) {
            return Atom.findOrCreateUnicodeAtom("c" + index);
        }

        private IMethod trampoline(final String descriptor) {
            try {
                final MethodReference reference = MethodReference.findOrCreate(
                        getReference(), invoke.getDeclaredTarget().getName(),
                        Descriptor.findOrCreateUTF8(descriptor));
                final MethodSummary summary = new MethodSummary(reference);
                final SSAInstructionFactory instructions = getClassLoader()
                        .getInstructionFactory();
                int index = 0;
                final int functionalArguments =
                        reference.getNumberOfParameters();
                int value = functionalArguments + 2;
                final int captureStart = value;
                for (int position = 0;
                        position < fields.size(); position++) {
                    summary.addStatement(instructions.GetInstruction(
                            index++, value++, 1,
                            capture(position).getReference()));
                }
                final MethodReference implementation = implementation();
                final int kind = implementationKind();
                final boolean constructor =
                        kind == REF_NEWINVOKESPECIAL;
                final int expected = functionalArguments + fields.size()
                        + (constructor ? 1 : 0);
                final int implementationParameters =
                        implementation.getNumberOfParameters()
                                + (kind == REF_INVOKESTATIC ? 0 : 1);
                if (implementationParameters != expected) {
                    throw new IllegalArgumentException(
                            "lambda parameter adaptation unsupported: "
                                    + implementation);
                }
                final int[] parameters = new int[expected];
                int parameter = 0;
                int newInstance = -1;
                if (constructor) {
                    newInstance = value++;
                    summary.addStatement(instructions.NewInstruction(
                            index, newInstance, NewSiteReference.make(
                                    index, implementation
                                            .getDeclaringClass())));
                    index++;
                    parameters[parameter++] = newInstance;
                }
                for (int position = 0;
                        position < fields.size(); position++) {
                    parameters[parameter++] = captureStart + position;
                }
                for (int position = 0;
                        position < functionalArguments; position++) {
                    parameters[parameter++] = position + 2;
                }
                if (implementation.getReturnType().equals(
                        TypeReference.Void)) {
                    summary.addStatement(instructions.InvokeInstruction(
                            index, parameters, value++,
                            CallSiteReference.make(index, implementation,
                                    dispatch(kind)), null));
                    index++;
                    if (constructor) {
                        summary.addStatement(instructions.ReturnInstruction(
                                index, newInstance, false));
                    } else {
                        summary.addStatement(instructions.ReturnInstruction(
                                index));
                    }
                } else {
                    final int returned = value++;
                    summary.addStatement(instructions.InvokeInstruction(
                            index, returned, parameters, value++,
                            CallSiteReference.make(index, implementation,
                                    dispatch(kind)), null));
                    index++;
                    summary.addStatement(instructions.ReturnInstruction(
                            index, returned, implementation.getReturnType()
                                    .isPrimitiveType()));
                }
                return new SummarizedMethod(reference, summary, this);
            } catch (InvalidClassFileException exception) {
                throw new IllegalArgumentException(
                        "invalid lambda trampoline", exception);
            }
        }

        private MethodReference implementation()
                throws InvalidClassFileException {
            final BootstrapMethod bootstrap = invoke.getBootstrap();
            final int index = bootstrap.callArgumentIndex(1);
            final String owner = bootstrap.getCP().getCPHandleClass(index);
            TypeReference declaring = TypeReference.findOrCreate(
                    ClassLoaderReference.Application, "L" + owner);
            for (IClass type : getClassHierarchy()) {
                final String candidate = type.getName().toString();
                if (owner.equals(candidate.startsWith("L")
                        ? candidate.substring(1) : candidate)) {
                    declaring = type.getReference();
                    break;
                }
            }
            return MethodReference.findOrCreate(
                    declaring,
                    bootstrap.getCP().getCPHandleName(index),
                    bootstrap.getCP().getCPHandleType(index));
        }

        private int implementationKind()
                throws InvalidClassFileException {
            return invoke.getBootstrap().getCP().getCPHandleKind(
                    invoke.getBootstrap().callArgumentIndex(1));
        }

        private Dispatch dispatch(final int kind) {
            return switch (kind) {
                case REF_INVOKEVIRTUAL -> Dispatch.VIRTUAL;
                case REF_INVOKESTATIC -> Dispatch.STATIC;
                case REF_INVOKESPECIAL, REF_NEWINVOKESPECIAL ->
                        Dispatch.SPECIAL;
                case REF_INVOKEINTERFACE -> Dispatch.INTERFACE;
                default -> throw new IllegalArgumentException(
                        "unsupported method handle kind: " + kind);
            };
        }

        @Override
        public boolean isPublic() {
            return false;
        }

        @Override
        public boolean isPrivate() {
            return false;
        }

        @Override
        public int getModifiers() {
            return Constants.ACC_FINAL | Constants.ACC_SUPER;
        }

        @Override
        public IClass getSuperclass() {
            return getClassHierarchy().getRootClass();
        }

        @Override
        public Collection<? extends IClass> getDirectInterfaces() {
            return interfaces;
        }

        @Override
        public Collection<IClass> getAllImplementedInterfaces() {
            final Set<IClass> result = new LinkedHashSet<>(interfaces);
            for (IClass type : interfaces) {
                result.addAll(type.getAllImplementedInterfaces());
            }
            return result;
        }

        @Override
        public IMethod getMethod(final Selector selector) {
            return methods.get(selector);
        }

        @Override
        public IField getField(final Atom name) {
            return fields.get(name);
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
            return fields.values();
        }

        @Override
        public Collection<IField> getAllStaticFields() {
            return List.of();
        }

        @Override
        public Collection<IField> getAllFields() {
            return fields.values();
        }

        @Override
        public Collection<? extends IMethod> getAllMethods() {
            return methods.values();
        }

        @Override
        public Collection<IField> getDeclaredInstanceFields() {
            return fields.values();
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

    /**
     * Parsed altMetafactory tail.
     *
     * @param flags protocol flags
     * @param markers marker interfaces
     * @param trampolineDescriptors SAM and bridge descriptors
     */
    private record AltProtocol(
            int flags,
            List<String> markers,
            List<String> trampolineDescriptors) {
    }

    /**
     * Exact classfile call-site identity, independent of WALA Context.
     *
     * @param caller caller method
     * @param bytecodePc invokedynamic bytecode PC
     */
    private record FactoryKey(
            MethodReference caller,
            int bytecodePc) {
    }

    private static String callerMethodId(final CGNode caller) {
        return Integer.toUnsignedString(
                caller.getMethod().getReference().toString().hashCode(),
                HEX_RADIX);
    }
}
