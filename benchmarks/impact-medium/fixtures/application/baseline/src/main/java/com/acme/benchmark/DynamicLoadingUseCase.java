package com.acme.benchmark;

import com.acme.impact.api.BenchmarkService;

import java.util.ServiceLoader;

public class DynamicLoadingUseCase {
    private static String removedType =
            "com.acme.impact.api.RemovedType";
    private static Class<BenchmarkService> serviceType =
            BenchmarkService.class;

    public Class<?> classForNameDirect() throws Exception {
        return Class.forName("com.acme.impact.api.RemovedType");
    }

    public Class<?> classForNameLocal() throws Exception {
        final String name = "com.acme.impact.api.RemovedType";
        return Class.forName(name);
    }

    public Class<?> classForNameSamePhi(final boolean flag)
            throws Exception {
        final String name;
        if (flag) {
            name = "com.acme.impact.api.RemovedType";
        } else {
            name = "com.acme.impact.api.RemovedType";
        }
        return Class.forName(name);
    }

    public Class<?> classForNameConcat(final String prefix)
            throws Exception {
        return Class.forName(prefix + ".RemovedType");
    }

    public Class<?> classForNameField() throws Exception {
        return Class.forName(removedType);
    }

    public Class<?> classForNameReturn() throws Exception {
        return Class.forName(removedTypeName());
    }

    public ServiceLoader<BenchmarkService> serviceLoaderDirect() {
        return ServiceLoader.load(BenchmarkService.class);
    }

    public ServiceLoader<BenchmarkService> serviceLoaderLocal() {
        final Class<BenchmarkService> type = BenchmarkService.class;
        return ServiceLoader.load(type);
    }

    public ServiceLoader<BenchmarkService> serviceLoaderSamePhi(
            final boolean flag) {
        final Class<BenchmarkService> type;
        if (flag) {
            type = BenchmarkService.class;
        } else {
            type = BenchmarkService.class;
        }
        return ServiceLoader.load(type);
    }

    public ServiceLoader<BenchmarkService> serviceLoaderField() {
        return ServiceLoader.load(serviceType);
    }

    public ServiceLoader<BenchmarkService> serviceLoaderReturn() {
        return ServiceLoader.load(serviceType());
    }

    private static String removedTypeName() {
        return "com.acme.impact.api.RemovedType";
    }

    private static Class<BenchmarkService> serviceType() {
        return BenchmarkService.class;
    }
}
