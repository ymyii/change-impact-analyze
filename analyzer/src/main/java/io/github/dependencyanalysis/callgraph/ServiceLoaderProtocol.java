package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

/** Immutable ServiceLoader protocol identities shared by all strategies. */
final class ServiceLoaderProtocol {

    /** ServiceLoader internal name. */
    private static final String SERVICE_LOADER = "java/util/ServiceLoader";

    /** Isolated Context identity for an unknown requested service. */
    private static final TypeReference UNKNOWN_SERVICE =
            TypeReference.findOrCreate(
                    ClassLoaderReference.Application,
                    "Lwala/serviceloader/UnknownService");

    /** Aggregate ZeroCFA service identity. */
    private static final TypeReference ALL_CONFIGURED_SERVICES =
            TypeReference.findOrCreate(
                    ClassLoaderReference.Application,
                    "Lwala/serviceloader/AllConfiguredServices");

    private ServiceLoaderProtocol() {
    }

    static TypeReference unknownService() {
        return UNKNOWN_SERVICE;
    }

    static TypeReference allConfiguredServices() {
        return ALL_CONFIGURED_SERVICES;
    }

    static boolean isServiceLoader(final TypeReference type) {
        return SERVICE_LOADER.equals(owner(type));
    }

    static boolean loadMethod(final MethodReference method) {
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

    static boolean singleParameterLoad(final MethodReference method) {
        return SERVICE_LOADER.equals(owner(method.getDeclaringClass()))
                && "load".equals(method.getName().toString())
                && "(Ljava/lang/Class;)Ljava/util/ServiceLoader;".equals(
                method.getDescriptor().toString());
    }

    static boolean iteratorMethod(final MethodReference method) {
        return SERVICE_LOADER.equals(owner(method.getDeclaringClass()))
                && "iterator".equals(method.getName().toString())
                && "()Ljava/util/Iterator;".equals(
                method.getDescriptor().toString());
    }

    static boolean twoParameterLoad(final MethodReference method) {
        return SERVICE_LOADER.equals(owner(method.getDeclaringClass()))
                && "load".equals(method.getName().toString())
                && ("(Ljava/lang/Class;Ljava/lang/ClassLoader;)"
                + "Ljava/util/ServiceLoader;").equals(
                method.getDescriptor().toString());
    }

    private static String owner(final TypeReference type) {
        final String value = type.getName().toString();
        return value.startsWith("L") ? value.substring(1) : value;
    }
}
