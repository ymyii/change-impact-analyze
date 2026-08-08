package io.github.dependencyanalysis.models.jdk;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.NewSiteReference;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.ipa.summaries.MethodSummary;
import com.ibm.wala.shrike.shrikeBT.IInvokeInstruction.Dispatch;
import com.ibm.wala.ssa.ConstantValue;
import com.ibm.wala.ssa.SSAInstructionFactory;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.Selector;
import com.ibm.wala.types.TypeReference;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Creates conservative Synthetic IR from catalog semantics. */
final class JdkSummaryBuilder {

    /** Callback token prefix length for arg:. */
    private static final int ARG_PREFIX_LENGTH = "arg:".length();

    /** Callback token prefix length for slot:. */
    private static final int SLOT_PREFIX_LENGTH = "slot:".length();

    /** Erased FileVisitor descriptor prefix. */
    private static final String FILE_VISITOR_PREFIX =
            "(Ljava/lang/Object;";

    /** BasicFileAttributes callback parameter. */
    private static final String FILE_ATTRIBUTES =
            "Ljava/nio/file/attribute/BasicFileAttributes;";

    /** FileVisitResult callback return. */
    private static final String FILE_VISIT_RESULT =
            ")Ljava/nio/file/FileVisitResult;";

    /** Active hierarchy. */
    private final IClassHierarchy hierarchy;

    /** Synthetic model state. */
    private final ModelStateClass state;

    /** Synthetic return types. */
    private final ModelSyntheticTypes syntheticTypes;

    /** Instruction factory. */
    private final SSAInstructionFactory instructions;

    JdkSummaryBuilder(
            final IClassHierarchy activeHierarchy,
            final ModelStateClass modelState,
            final ModelSyntheticTypes modelTypes) {
        hierarchy = activeHierarchy;
        state = modelState;
        syntheticTypes = modelTypes;
        instructions = hierarchy.getRootClass().getClassLoader()
                .getInstructionFactory();
    }

    MethodSummary build(
            final CatalogEntry entry,
            final IMethod resolved) {
        final SummaryEmitter emitter = new SummaryEmitter(
                entry.reference(), entry.staticMethod());
        try {
            switch (entry.template()) {
                case NO_OP -> emitter.defaultReturn();
                case RETURN_THIS -> emitter.returnValue(1);
                case RETURN_ARG -> emitter.returnValue(
                        emitter.argument(entry.dataArgument()));
                case RETURN_NEW -> emitter.returnNew();
                case STORE -> {
                    emitter.store(entry.slot(),
                            emitter.argument(entry.dataArgument()));
                    emitter.defaultReturn();
                }
                case STORE_RETURN_THIS -> {
                    emitter.store(entry.slot(),
                            emitter.argument(entry.dataArgument()));
                    emitter.returnValue(1);
                }
                case STORE_RETURN_NEW -> {
                    emitter.store(entry.slot(),
                            emitter.argument(entry.dataArgument()));
                    emitter.returnNew();
                }
                case MAP_STORE -> {
                    emitter.store(StateSlot.MAP_KEY, emitter.argument(0));
                    emitter.store(StateSlot.MAP_VALUE, emitter.argument(1));
                    emitter.defaultReturn();
                }
                case MAP_STORE_RETURN_NEW -> {
                    emitter.store(StateSlot.MAP_KEY, emitter.argument(0));
                    emitter.store(StateSlot.MAP_VALUE, emitter.argument(1));
                    emitter.returnNew();
                }
                case LOAD -> emitter.returnLoaded(entry.slot());
                case COPY_NEW -> {
                    final int value = emitter.load(entry.slot());
                    emitter.store(entry.resultSlot(), value);
                    emitter.returnNew();
                }
                case CALLBACK_VOID -> {
                    emitter.prepare(entry);
                    emitter.callbacks(entry.callbacks(), entry.resultSlot());
                    emitter.defaultReturn();
                }
                case CALLBACK_THIS -> {
                    emitter.prepare(entry);
                    emitter.callbacks(entry.callbacks(), entry.resultSlot());
                    emitter.returnValue(1);
                }
                case CALLBACK_RESULT -> {
                    emitter.prepare(entry);
                    emitter.returnCallback(
                            entry.callbacks(), entry.resultSlot());
                }
                case CALLBACK_NEW -> {
                    emitter.prepare(entry);
                    emitter.callbacks(entry.callbacks(), entry.resultSlot());
                    emitter.returnNew();
                }
                case FILE_VISITOR -> fileVisitor(emitter, entry);
                case RESOURCE_STREAM -> resourceStream(emitter, entry);
                case SERIALIZE_WRITE -> serializationWrite(emitter);
                case SERIALIZE_READ -> serializationRead(emitter);
                case SERIALIZE_VALIDATE -> serializationValidation(emitter);
                default -> throw new JdkModelException(
                        "Unsupported summary template: "
                                + entry.template());
            }
            return emitter.summary();
        } catch (JdkModelException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new JdkModelException(
                    "Unable to build summary for "
                            + resolved.getReference(), exception);
        }
    }

    private void fileVisitor(
            final SummaryEmitter emitter,
            final CatalogEntry entry) {
        final int visitor = emitter.argument(entry.dataArgument());
        final int path = emitter.argument(0);
        final int nullValue = emitter.nullValue();
        emitter.invoke(visitor, "java/nio/file/FileVisitor",
                "preVisitDirectory",
                FILE_VISITOR_PREFIX + FILE_ATTRIBUTES + FILE_VISIT_RESULT,
                Dispatch.INTERFACE, List.of(path, nullValue));
        emitter.invoke(visitor, "java/nio/file/FileVisitor", "visitFile",
                FILE_VISITOR_PREFIX + FILE_ATTRIBUTES + FILE_VISIT_RESULT,
                Dispatch.INTERFACE, List.of(path, nullValue));
        emitter.invoke(visitor, "java/nio/file/FileVisitor",
                "visitFileFailed",
                FILE_VISITOR_PREFIX + "Ljava/io/IOException;"
                        + FILE_VISIT_RESULT,
                Dispatch.INTERFACE, List.of(path, nullValue));
        emitter.invoke(visitor, "java/nio/file/FileVisitor",
                "postVisitDirectory",
                FILE_VISITOR_PREFIX + "Ljava/io/IOException;"
                        + FILE_VISIT_RESULT,
                Dispatch.INTERFACE, List.of(path, nullValue));
        emitter.returnValue(path);
    }

    private void resourceStream(
            final SummaryEmitter emitter,
            final CatalogEntry entry) {
        final TypeReference element = "lines".equals(entry.name())
                ? TypeReference.JavaLangString
                : TypeReference.findOrCreate(
                        ClassLoaderReference.Primordial,
                        "Ljava/nio/file/Path");
        emitter.store(StateSlot.STREAM_ELEMENT,
                emitter.newValue(element));
        emitter.returnNew();
    }

    private void serializationWrite(final SummaryEmitter emitter) {
        final int input = emitter.argument(0);
        emitter.store(StateSlot.SERIALIZED_OBJECT, input);
        for (IClass type : serializableTypes()) {
            invokeHook(emitter, type, input,
                    "writeObject(Ljava/io/ObjectOutputStream;)V",
                    List.of(1));
            invokeHook(emitter, type, input,
                    "writeReplace()Ljava/lang/Object;", List.of());
            if (implementsType(type, "Ljava/io/Externalizable")) {
                emitter.invoke(input, "java/io/Externalizable",
                        "writeExternal", "(Ljava/io/ObjectOutput;)V",
                        Dispatch.INTERFACE, List.of(1));
            }
        }
        emitter.invoke(1, "java/io/ObjectOutputStream", "replaceObject",
                "(Ljava/lang/Object;)Ljava/lang/Object;", Dispatch.VIRTUAL,
                List.of(input));
        invokeStreamHooks(emitter, 1,
                "Ljava/io/ObjectOutputStream", List.of(
                        "annotateClass(Ljava/lang/Class;)V",
                        "annotateProxyClass(Ljava/lang/Class;)V",
                        "replaceObject(Ljava/lang/Object;)Ljava/lang/Object;",
                        "writeClassDescriptor(Ljava/io/ObjectStreamClass;)V",
                        "writeStreamHeader()V"));
        emitter.defaultReturn();
    }

    private void serializationRead(final SummaryEmitter emitter) {
        final List<Integer> values = new ArrayList<>();
        final int stored = emitter.load(StateSlot.SERIALIZED_OBJECT);
        for (IClass type : serializableTypes()) {
            final int instance = emitter.newValue(type.getReference());
            values.add(instance);
            invokeFirstNonSerializableConstructor(emitter, type, instance);
            invokeHook(emitter, type, stored,
                    "readObject(Ljava/io/ObjectInputStream;)V", List.of(1));
            invokeHook(emitter, type, stored, "readObjectNoData()V",
                    List.of());
            invokeHook(emitter, type, stored,
                    "readResolve()Ljava/lang/Object;", List.of());
            if (implementsType(type, "Ljava/io/Externalizable")) {
                emitter.invoke(stored, "java/io/Externalizable",
                        "readExternal", "(Ljava/io/ObjectInput;)V",
                        Dispatch.INTERFACE, List.of(1));
            }
        }
        final int result;
        if (values.isEmpty()) {
            result = emitter.nullValue();
        } else if (values.size() == 1) {
            result = values.get(0);
        } else {
            result = emitter.phi(values);
        }
        emitter.store(StateSlot.SERIALIZED_OBJECT, result);
        final int resolved = emitter.invoke(1, "java/io/ObjectInputStream",
                "resolveObject", "(Ljava/lang/Object;)Ljava/lang/Object;",
                Dispatch.VIRTUAL, List.of(result));
        invokeStreamHooks(emitter, 1,
                "Ljava/io/ObjectInputStream", List.of(
                        "readClassDescriptor()Ljava/io/ObjectStreamClass;",
                        "readStreamHeader()V",
                        "resolveClass(Ljava/io/ObjectStreamClass;)"
                                + "Ljava/lang/Class;",
                        "resolveObject(Ljava/lang/Object;)Ljava/lang/Object;",
                        "resolveProxyClass([Ljava/lang/String;)"
                                + "Ljava/lang/Class;"));
        emitter.returnValue(resolved);
    }

    private void invokeStreamHooks(
            final SummaryEmitter emitter,
            final int receiver,
            final String baseName,
            final List<String> selectors) {
        for (IClass type : applicationSubclasses(baseName)) {
            for (String selector : selectors) {
                final int parameterCount = Selector.make(selector)
                        .descriptor().getNumberOfParameters();
                final List<Integer> parameters = new ArrayList<>();
                for (int position = 0;
                        position < parameterCount; position++) {
                    parameters.add(emitter.nullValue());
                }
                invokeHook(emitter, type, receiver, selector, parameters);
            }
        }
    }

    private void serializationValidation(final SummaryEmitter emitter) {
        emitter.invoke(emitter.argument(0),
                "java/io/ObjectInputValidation", "validateObject", "()V",
                Dispatch.INTERFACE, List.of());
        emitter.defaultReturn();
    }

    private void invokeFirstNonSerializableConstructor(
            final SummaryEmitter emitter,
            final IClass type,
            final int instance) {
        IClass current = type.getSuperclass();
        while (current != null && implementsType(
                current, "Ljava/io/Serializable")) {
            current = current.getSuperclass();
        }
        if (current == null) {
            return;
        }
        final IMethod constructor = current.getMethod(
                Selector.make("<init>()V"));
        if (constructor == null) {
            throw new JdkModelException(
                    "Serializable type has no resolvable first "
                            + "non-serializable constructor: "
                            + type.getReference());
        }
        emitter.invoke(instance,
                normalized(current.getName().toString()), "<init>",
                "()V", Dispatch.SPECIAL, List.of());
    }

    private void invokeHook(
            final SummaryEmitter emitter,
            final IClass type,
            final int instance,
            final String selector,
            final List<Integer> parameters) {
        final Selector expected = Selector.make(selector);
        final IMethod method = type.getDeclaredMethods().stream()
                .filter(candidate -> candidate.getSelector()
                        .equals(expected))
                .findFirst().orElse(null);
        if (method != null) {
            emitter.invoke(instance,
                    normalized(type.getName().toString()),
                    method.getName().toString(),
                    method.getDescriptor().toString(),
                    Dispatch.VIRTUAL,
                    parameters);
        }
    }

    private List<IClass> serializableTypes() {
        return applicationTypes("Ljava/io/Serializable");
    }

    private List<IClass> applicationTypes(final String contractName) {
        return java.util.stream.StreamSupport.stream(
                        hierarchy.spliterator(), false)
                .filter(type -> type.getClassLoader().getReference().equals(
                        ClassLoaderReference.Application))
                .filter(type -> !type.isInterface() && !type.isAbstract())
                .filter(type -> implementsType(
                        type, contractName))
                .sorted(Comparator.comparing(
                        type -> type.getReference().toString()))
                .toList();
    }

    private List<IClass> applicationSubclasses(final String baseName) {
        final IClass base = hierarchy.lookupClass(
                TypeReference.findOrCreate(
                        ClassLoaderReference.Primordial, baseName));
        if (base == null) {
            throw new JdkModelException(
                    "Serialization stream base is unresolved: "
                            + baseName);
        }
        return java.util.stream.StreamSupport.stream(
                        hierarchy.spliterator(), false)
                .filter(type -> type.getClassLoader().getReference().equals(
                        ClassLoaderReference.Application))
                .filter(type -> !type.isInterface() && !type.isAbstract())
                .filter(type -> hierarchy.isSubclassOf(type, base))
                .sorted(Comparator.comparing(
                        type -> type.getReference().toString()))
                .toList();
    }

    private boolean implementsType(
            final IClass type,
            final String internalName) {
        final IClass contract = hierarchy.lookupClass(
                TypeReference.findOrCreate(
                        ClassLoaderReference.Primordial, internalName));
        return contract != null && (type.equals(contract)
                || type.getAllImplementedInterfaces().contains(contract));
    }

    private String normalized(final String name) {
        return name.startsWith("L") ? name.substring(1) : name;
    }

    /** Mutable summary assembly state. */
    private final class SummaryEmitter {

        /** Summary. */
        private final MethodSummary value;

        /** Target reference. */
        private final MethodReference target;

        /** Static target flag. */
        private final boolean staticMethod;

        /** Next instruction index. */
        private int index;

        /** Next SSA value number. */
        private int nextValue;

        SummaryEmitter(
                final MethodReference method,
                final boolean isStatic) {
            target = method;
            staticMethod = isStatic;
            value = new MethodSummary(method);
            value.setStatic(isStatic);
            nextValue = value.getNumberOfParameters() + 1;
        }

        MethodSummary summary() {
            return value;
        }

        int argument(final int explicitIndex) {
            if (explicitIndex < 0
                    || explicitIndex >= target.getNumberOfParameters()) {
                throw new JdkModelException(
                        "Invalid argument index " + explicitIndex
                                + " for " + target);
            }
            return explicitIndex + (staticMethod ? 1 : 2);
        }

        int nullValue() {
            final int result = nextValue++;
            value.addConstant(result, new ConstantValue(null));
            return result;
        }

        int load(final StateSlot slot) {
            if (slot == null) {
                throw new JdkModelException(
                        "LOAD requires a state slot for " + target);
            }
            final int result = nextValue++;
            value.addStatement(instructions.GetInstruction(
                    index++, result, state.field(slot)));
            return result;
        }

        void store(final StateSlot slot, final int source) {
            if (slot == null) {
                throw new JdkModelException(
                        "STORE requires a state slot for " + target);
            }
            value.addStatement(instructions.PutInstruction(
                    index++, source, state.field(slot)));
        }

        void prepare(final CatalogEntry entry) {
            if (entry.slot() != null && entry.dataArgument() >= 0) {
                store(entry.slot(), argument(entry.dataArgument()));
            }
        }

        int newValue(final TypeReference declared) {
            final TypeReference allocated = syntheticTypes.concrete(declared);
            final int result = nextValue++;
            final NewSiteReference site = NewSiteReference.make(
                    index, allocated);
            if (allocated.isArrayType()) {
                final int size = nextValue++;
                value.addConstant(size, new ConstantValue(1));
                value.addStatement(instructions.NewInstruction(
                        index++, result, site, new int[]{size}));
            } else {
                value.addStatement(instructions.NewInstruction(
                        index++, result, site));
            }
            return result;
        }

        void returnNew() {
            final TypeReference returnType = target.getReturnType();
            if (!returnType.isReferenceType()) {
                defaultReturn();
                return;
            }
            returnValue(newValue(returnType));
        }

        void returnLoaded(final StateSlot slot) {
            returnReference(load(slot));
        }

        void returnCallback(
                final List<CallbackSpec> callbacks,
                final StateSlot resultSlot) {
            final int result = callbacks(callbacks, resultSlot);
            if (result < 0) {
                defaultReturn();
            } else {
                returnReference(result);
            }
        }

        int callbacks(
                final List<CallbackSpec> callbacks,
                final StateSlot resultSlot) {
            int result = -1;
            for (CallbackSpec callback : callbacks) {
                final List<Integer> uses = callback.inputs().stream()
                        .map(this::input).toList();
                result = invoke(argument(callback.receiverArgument()),
                        callback.owner(), callback.name(),
                        callback.descriptor(), callback.dispatch(), uses);
            }
            if (result >= 0 && resultSlot != null
                    && callbackReturnIsReference(callbacks)) {
                store(resultSlot, result);
            }
            return result;
        }

        private boolean callbackReturnIsReference(
                final List<CallbackSpec> callbacks) {
            if (callbacks.isEmpty()) {
                return false;
            }
            final CallbackSpec last = callbacks.get(callbacks.size() - 1);
            final MethodReference method = method(last.owner(), last.name(),
                    last.descriptor());
            return method.getReturnType().isReferenceType();
        }

        private int input(final String token) {
            if ("this".equals(token)) {
                if (staticMethod) {
                    throw new JdkModelException(
                            "Static summary has no receiver: " + target);
                }
                return 1;
            }
            if (token.startsWith("arg:")) {
                return argument(Integer.parseInt(
                        token.substring(ARG_PREFIX_LENGTH)));
            }
            if (token.startsWith("slot:")) {
                return load(StateSlot.valueOf(
                        token.substring(SLOT_PREFIX_LENGTH)));
            }
            if ("null".equals(token)) {
                return nullValue();
            }
            if ("zero".equals(token)) {
                return zeroValue();
            }
            throw new JdkModelException(
                    "Unsupported callback input " + token
                            + " for " + target);
        }

        int invoke(
                final int receiver,
                final String owner,
                final String name,
                final String descriptor,
                final Dispatch dispatch,
                final List<Integer> arguments) {
            final MethodReference method = method(owner, name, descriptor);
            final int[] uses = new int[arguments.size() + 1];
            uses[0] = receiver;
            for (int position = 0;
                    position < arguments.size(); position++) {
                uses[position + 1] = arguments.get(position);
            }
            final CallSiteReference site = CallSiteReference.make(
                    index, method, dispatch);
            final int exception = nextValue++;
            if (method.getReturnType().equals(TypeReference.Void)) {
                value.addStatement(instructions.InvokeInstruction(
                        index++, uses, exception, site, null));
                return -1;
            }
            final int result = nextValue++;
            value.addStatement(instructions.InvokeInstruction(
                    index++, result, uses, exception, site, null));
            return result;
        }

        private int zeroValue() {
            final int result = nextValue++;
            value.addConstant(result, new ConstantValue(0));
            return result;
        }

        int phi(final List<Integer> sources) {
            final int result = nextValue++;
            value.addStatement(instructions.PhiInstruction(index++, result,
                    sources.stream().mapToInt(Integer::intValue).toArray()));
            return result;
        }

        void returnReference(final int source) {
            final TypeReference returnType = target.getReturnType();
            if (!returnType.isReferenceType()
                    || returnType.equals(TypeReference.JavaLangObject)) {
                returnValue(source);
                return;
            }
            final int cast = nextValue++;
            value.addStatement(instructions.CheckCastInstruction(
                    index++, cast, source, returnType, true));
            returnValue(cast);
        }

        void returnValue(final int source) {
            value.addStatement(instructions.ReturnInstruction(
                    index++, source,
                    target.getReturnType().isPrimitiveType()));
        }

        void defaultReturn() {
            final TypeReference returnType = target.getReturnType();
            if (returnType.equals(TypeReference.Void)) {
                value.addStatement(instructions.ReturnInstruction(index++));
                return;
            }
            final int result = nextValue++;
            value.addConstant(result, returnType.isPrimitiveType()
                    ? new ConstantValue(0) : new ConstantValue(null));
            returnValue(result);
        }

        private MethodReference method(
                final String owner,
                final String name,
                final String descriptor) {
            final TypeReference application = TypeReference.findOrCreate(
                    ClassLoaderReference.Application, "L" + owner);
            final TypeReference declaring = hierarchy.lookupClass(
                    application) == null
                    ? TypeReference.findOrCreate(
                            ClassLoaderReference.Primordial, "L" + owner)
                    : application;
            return MethodReference.findOrCreate(
                    declaring,
                    Selector.make(name + descriptor));
        }
    }
}
