package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IField;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.SyntheticClass;
import com.ibm.wala.core.util.strings.Atom;
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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Creates stable RTA MethodHandle bridge summaries. */
final class RtaMethodHandleSummaryFactory {

    /** Bytes retained from the stable summary identity digest. */
    private static final int DIGEST_BYTES = 12;

    /** Per-build immutable summaries by protocol identity. */
    private final Map<BridgeKey, IMethod> summaries =
            new LinkedHashMap<>();

    /** Bridge summaries by their synthetic method reference. */
    private final Map<MethodReference, IMethod> references =
            new LinkedHashMap<>();

    /** Synthetic application class containing all bridge summaries. */
    private final BridgeClass bridgeClass;

    RtaMethodHandleSummaryFactory(final IClassHierarchy hierarchy) {
        bridgeClass = new BridgeClass(hierarchy);
        hierarchy.addClass(bridgeClass);
    }

    IMethod bridge(
            final LocalMethodHandleResolution resolution,
            final CallSiteReference site) {
        final IMethod target = resolution.targetMethod().orElseThrow();
        final BridgeKey key = new BridgeKey(resolution.operation(),
                site.getDeclaredTarget().getDescriptor().toString(),
                target.getReference().toString());
        final IMethod bridge = summaries.computeIfAbsent(key,
                ignored -> create(key, target, site));
        references.putIfAbsent(bridge.getReference(), bridge);
        return bridge;
    }

    Optional<IMethod> find(final MethodReference reference) {
        return Optional.ofNullable(references.get(reference));
    }

    private IMethod create(
            final BridgeKey key,
            final IMethod target,
            final CallSiteReference site) {
        final MethodReference reference = MethodReference.findOrCreate(
                bridgeClass.getReference(),
                Atom.findOrCreateUnicodeAtom(
                        "wala$rta$methodHandle$" + digest(key.stableKey())),
                site.getDeclaredTarget().getDescriptor());
        final MethodSummary summary = new MethodSummary(reference);
        summary.setStatic(true);
        final SSAInstructionFactory instructions = bridgeClass
                .getClassLoader().getLanguage().instructionFactory();
        final int[] parameters = targetParameters(
                summary, target.getReference());
        final int exception = nextValue(summary) + parameters.length;
        final TypeReference bridgeReturn = reference.getReturnType();
        final TypeReference targetReturn = target.getReturnType();
        if (targetReturn.equals(TypeReference.Void)) {
            summary.addStatement(instructions.InvokeInstruction(
                    0, parameters, exception,
                    CallSiteReference.make(0, target.getReference(),
                            Dispatch.STATIC), null));
            appendReturn(summary, instructions, 1, bridgeReturn, -1);
        } else {
            final int returned = exception + 1;
            summary.addStatement(instructions.InvokeInstruction(
                    0, returned, parameters, exception,
                    CallSiteReference.make(0, target.getReference(),
                            Dispatch.STATIC), null));
            appendReturn(summary, instructions, 1, bridgeReturn,
                    compatibleReturn(bridgeReturn, targetReturn)
                            ? returned : -1);
        }
        final IMethod bridge = new SummarizedMethod(
                reference, summary, bridgeClass);
        bridgeClass.add(bridge);
        return bridge;
    }

    private int[] targetParameters(
            final MethodSummary summary,
            final MethodReference target) {
        final int[] parameters = new int[target.getNumberOfParameters()];
        final int available = summary.getMethod().getNumberOfParameters();
        int synthetic = nextValue(summary);
        for (int index = 0; index < parameters.length; index++) {
            if (index < available) {
                parameters[index] = index + 1;
            } else {
                parameters[index] = synthetic++;
                summary.addConstant(parameters[index],
                        target.getParameterType(index).isPrimitiveType()
                                ? new ConstantValue(0)
                                : new ConstantValue(null));
            }
        }
        return parameters;
    }

    private int nextValue(final MethodSummary summary) {
        return summary.getMethod().getNumberOfParameters() + 1;
    }

    private boolean compatibleReturn(
            final TypeReference bridge,
            final TypeReference target) {
        return bridge.equals(target)
                || !bridge.isPrimitiveType() && !target.isPrimitiveType();
    }

    private void appendReturn(
            final MethodSummary summary,
            final SSAInstructionFactory instructions,
            final int index,
            final TypeReference returnType,
            final int returned) {
        if (returnType.equals(TypeReference.Void)) {
            summary.addStatement(instructions.ReturnInstruction(index));
            return;
        }
        int value = returned;
        if (value < 0) {
            value = nextValue(summary) + summary.getNumberOfStatements() + 1;
            summary.addConstant(value, returnType.isPrimitiveType()
                    ? new ConstantValue(0) : new ConstantValue(null));
        }
        summary.addStatement(instructions.ReturnInstruction(
                index, value, returnType.isPrimitiveType()));
    }

    private String digest(final String value) {
        try {
            final byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, DIGEST_BYTES);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 is unavailable", exception);
        }
    }

    /**
     * Stable summary cache identity.
     *
     * @param operation modeled MethodHandle operation
     * @param callsiteDescriptor signature-polymorphic callsite descriptor
     * @param targetIdentity resolved binary target identity
     */
    private record BridgeKey(
            LocalMethodHandleResolution.Operation operation,
            String callsiteDescriptor,
            String targetIdentity) {

        BridgeKey {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(callsiteDescriptor, "callsiteDescriptor");
            Objects.requireNonNull(targetIdentity, "targetIdentity");
        }

        String stableKey() {
            return operation + "|" + callsiteDescriptor + "|"
                    + targetIdentity;
        }
    }

    /** Synthetic container that avoids JDK MethodHandle reachability. */
    private static final class BridgeClass extends SyntheticClass {

        /** Stable summaries by selector. */
        private final Map<Selector, IMethod> methods =
                new LinkedHashMap<>();

        BridgeClass(final IClassHierarchy hierarchy) {
            super(TypeReference.findOrCreate(
                    ClassLoaderReference.Application,
                    "Lwala/methodhandle/RtaBridge"), hierarchy);
        }

        void add(final IMethod method) {
            methods.putIfAbsent(method.getSelector(), method);
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
            return List.of();
        }

        @Override
        public Collection<IClass> getAllImplementedInterfaces() {
            return Collections.emptySet();
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
            return List.copyOf(methods.values());
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
            return List.copyOf(methods.values());
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
