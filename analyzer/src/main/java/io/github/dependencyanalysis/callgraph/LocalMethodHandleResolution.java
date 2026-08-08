package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;

import java.util.Objects;
import java.util.Optional;

/**
 * Caller-local MethodHandle resolution with stable binary identity.
 *
 * @param status resolution status
 * @param operation modeled MethodHandle operation
 * @param target resolved target, or null
 * @param callerIdentity stable caller method identity
 * @param bytecodePc source callsite bytecode PC
 * @param targetOwner resolved target owner, or empty
 * @param targetName resolved target name, or empty
 * @param targetDescriptor resolved target descriptor, or empty
 * @param detail stable resolution detail
 */
record LocalMethodHandleResolution(
        Status status,
        Operation operation,
        IMethod target,
        String callerIdentity,
        int bytecodePc,
        String targetOwner,
        String targetName,
        String targetDescriptor,
        String detail) {

    /** Resolver outcome. */
    enum Status {
        /** Exact local target found. */
        MODELED,
        /** MethodHandle operation recognized but target unresolved. */
        UNSUPPORTED,
        /** Callsite is outside the declared MethodHandle model. */
        NOT_APPLICABLE
    }

    /** Supported caller-local operation. */
    enum Operation {
        /** Signature-polymorphic invokeExact. */
        INVOKE_EXACT,
        /** Varargs invokeWithArguments. */
        INVOKE_WITH_ARGUMENTS,
        /** Non-MethodHandle callsite. */
        NONE
    }

    LocalMethodHandleResolution {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(callerIdentity, "callerIdentity");
        Objects.requireNonNull(targetOwner, "targetOwner");
        Objects.requireNonNull(targetName, "targetName");
        Objects.requireNonNull(targetDescriptor, "targetDescriptor");
        Objects.requireNonNull(detail, "detail");
        if ((status == Status.MODELED) != (target != null)) {
            throw new IllegalArgumentException(
                    "Modeled MethodHandle resolution requires one target");
        }
        if (status == Status.MODELED
                && (operation == Operation.NONE || targetOwner.isBlank()
                || targetName.isBlank() || targetDescriptor.isBlank())) {
            throw new IllegalArgumentException(
                    "Modeled resolution requires stable target identity");
        }
        if (status == Status.NOT_APPLICABLE
                && operation != Operation.NONE) {
            throw new IllegalArgumentException(
                    "Not-applicable resolution cannot declare operation");
        }
    }

    static LocalMethodHandleResolution modeled(
            final Operation operation,
            final IMethod target,
            final CGNode caller,
            final CallSiteReference site) {
        final IMethod resolved = Objects.requireNonNull(target, "target");
        return new LocalMethodHandleResolution(Status.MODELED,
                operation, resolved, callerIdentity(caller),
                site.getProgramCounter(), owner(resolved),
                resolved.getName().toString(),
                resolved.getDescriptor().toString(), "resolved");
    }

    static LocalMethodHandleResolution unsupported(
            final Operation operation,
            final CGNode caller,
            final CallSiteReference site,
            final String detail) {
        return new LocalMethodHandleResolution(Status.UNSUPPORTED,
                operation, null, callerIdentity(caller),
                site.getProgramCounter(), "", "", "", detail);
    }

    static LocalMethodHandleResolution notApplicable(
            final CGNode caller,
            final CallSiteReference site) {
        return new LocalMethodHandleResolution(Status.NOT_APPLICABLE,
                Operation.NONE, null, callerIdentity(caller),
                site.getProgramCounter(), "", "", "",
                "not-applicable");
    }

    Optional<IMethod> targetMethod() {
        return Optional.ofNullable(target);
    }

    String location() {
        return callerIdentity + "|pc=" + bytecodePc;
    }

    private static String callerIdentity(final CGNode caller) {
        return Objects.requireNonNull(caller, "caller")
                .getMethod().getReference().toString();
    }

    private static String owner(final IMethod method) {
        final String value = method.getDeclaringClass().getName().toString();
        return value.startsWith("L") ? value.substring(1) : value;
    }
}
