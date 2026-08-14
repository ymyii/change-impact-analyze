package io.github.dependencyanalysis.callgraph.protocol.serviceloader;

import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

/** Immutable ServiceLoader protocol identities shared by all strategies. */
public final class ServiceLoaderProtocol {

    /** ServiceLoader internal name. */
    private static final String SERVICE_LOADER = "java/util/ServiceLoader";

    /** Isolated Context identity for an unknown requested service. */
    private static final TypeReference UNKNOWN_SERVICE =
            TypeReference.findOrCreate(
                    ClassLoaderReference.Application,
                    "Lwala/serviceloader/UnknownService");

    /** Aggregate fallback service identity. */
    private static final TypeReference ALL_CONFIGURED_SERVICES =
            TypeReference.findOrCreate(
                    ClassLoaderReference.Application,
                    "Lwala/serviceloader/AllConfiguredServices");

    private ServiceLoaderProtocol() {
    }

    /** @return isolated identity for an unknown service */
    public static TypeReference unknownService() {
        return UNKNOWN_SERVICE;
    }

    /** @return aggregate identity for all configured services */
    public static TypeReference allConfiguredServices() {
        return ALL_CONFIGURED_SERVICES;
    }

    /**
     * @param type candidate type
     * @return whether the type is ServiceLoader
     */
    public static boolean isServiceLoader(final TypeReference type) {
        return SERVICE_LOADER.equals(owner(type));
    }

    /**
     * @param method candidate method
     * @return whether the method loads a service
     */
    public static boolean loadMethod(final MethodReference method) {
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

    /**
     * @param method candidate method
     * @return whether the method is single-parameter load
     */
    public static boolean singleParameterLoad(final MethodReference method) {
        return SERVICE_LOADER.equals(owner(method.getDeclaringClass()))
                && "load".equals(method.getName().toString())
                && "(Ljava/lang/Class;)Ljava/util/ServiceLoader;".equals(
                method.getDescriptor().toString());
    }

    /**
     * @param method candidate method
     * @return whether the method obtains a provider iterator
     */
    public static boolean iteratorMethod(final MethodReference method) {
        return SERVICE_LOADER.equals(owner(method.getDeclaringClass()))
                && "iterator".equals(method.getName().toString())
                && "()Ljava/util/Iterator;".equals(
                method.getDescriptor().toString());
    }

    /**
     * @param method candidate method
     * @return whether the method is two-parameter load
     */
    public static boolean twoParameterLoad(final MethodReference method) {
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
