package io.github.dependencyanalysis.callgraph.protocol.methodhandle;

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
public record LocalMethodHandleResolution(
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
    public enum Status {
        /** Exact local target found. */
        MODELED,
        /** MethodHandle operation recognized but target unresolved. */
        UNSUPPORTED,
        /** Callsite is outside the declared MethodHandle model. */
        NOT_APPLICABLE
    }

    /** Supported caller-local operation. */
    public enum Operation {
        /** Signature-polymorphic invokeExact. */
        INVOKE_EXACT,
        /** Varargs invokeWithArguments. */
        INVOKE_WITH_ARGUMENTS,
        /** Non-MethodHandle callsite. */
        NONE
    }

    /** Validates the immutable resolution. */
    public LocalMethodHandleResolution {
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

    /**
     * Creates an exact modeled resolution.
     *
     * @param operation modeled operation
     * @param target exact target
     * @param caller reachable caller
     * @param site source callsite
     * @return modeled resolution
     */
    public static LocalMethodHandleResolution modeled(
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

    /**
     * Creates an unsupported local resolution.
     *
     * @param operation recognized operation
     * @param caller reachable caller
     * @param site source callsite
     * @param detail limitation detail
     * @return unsupported resolution
     */
    public static LocalMethodHandleResolution unsupported(
            final Operation operation,
            final CGNode caller,
            final CallSiteReference site,
            final String detail) {
        return new LocalMethodHandleResolution(Status.UNSUPPORTED,
                operation, null, callerIdentity(caller),
                site.getProgramCounter(), "", "", "", detail);
    }

    /**
     * Creates a non-MethodHandle resolution.
     *
     * @param caller reachable caller
     * @param site source callsite
     * @return not-applicable resolution
     */
    public static LocalMethodHandleResolution notApplicable(
            final CGNode caller,
            final CallSiteReference site) {
        return new LocalMethodHandleResolution(Status.NOT_APPLICABLE,
                Operation.NONE, null, callerIdentity(caller),
                site.getProgramCounter(), "", "", "",
                "not-applicable");
    }

    /** @return optional exact target */
    public Optional<IMethod> targetMethod() {
        return Optional.ofNullable(target);
    }

    /** @return stable caller and bytecode location */
    public String location() {
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
