package io.github.dependencyanalysis.callgraph;

import com.ibm.wala.classLoader.FileModule;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.ModuleEntry;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;
import com.ibm.wala.types.TypeReference;

import io.github.dependencyanalysis.impact.ModuleAnalysisReason;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Immutable Application ServiceLoader resource and provider facts. */
final class ServiceLoaderProtocolIndex {

    /** Service configuration resource prefix. */
    private static final String SERVICE_PREFIX = "META-INF/services/";

    /** Valid providers by exact service type. */
    private final Map<TypeReference,
            List<ServiceLoaderProviderDefinition>> providers;

    /** Resource/declaration validation limitations. */
    private final List<ModelLimitation> limitations;

    private ServiceLoaderProtocolIndex(
            final Map<TypeReference,
                    List<ServiceLoaderProviderDefinition>> values,
            final List<ModelLimitation> validationLimitations) {
        final Map<TypeReference,
                List<ServiceLoaderProviderDefinition>> copied =
                new LinkedHashMap<>();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(TypeReference::toString)))
                .forEach(entry -> copied.put(entry.getKey(),
                        List.copyOf(entry.getValue())));
        providers = Collections.unmodifiableMap(copied);
        limitations = validationLimitations.stream()
                .distinct().sorted().toList();
    }

    static ServiceLoaderProtocolIndex create(
            final AnalysisScope scope,
            final IClassHierarchy hierarchy) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(hierarchy, "hierarchy");
        final Map<TypeReference,
                List<ServiceLoaderProviderDefinition>> definitions =
                new LinkedHashMap<>();
        final List<ModelLimitation> validation = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : resources(scope)
                .entrySet().stream().sorted(Map.Entry.comparingByKey())
                .toList()) {
            final IClass service = lookup(hierarchy, entry.getKey());
            if (service == null) {
                validation.add(limitation(
                        "SERVICE_LOADER_SERVICE_UNRESOLVED",
                        "resource=" + entry.getKey(),
                        "Service type is absent from target hierarchy"));
                continue;
            }
            final List<ServiceLoaderProviderDefinition> valid =
                    new ArrayList<>();
            for (String providerName : entry.getValue().stream()
                    .sorted().toList()) {
                final IClass provider = lookup(hierarchy, providerName);
                final IMethod constructor = provider == null ? null
                        : publicConstructor(provider);
                if (provider == null || provider.isAbstract()
                        || provider.isInterface() || !provider.isPublic()
                        || constructor == null
                        || !hierarchy.isAssignableFrom(service, provider)) {
                    validation.add(limitation(
                            "SERVICE_LOADER_PROVIDER_INVALID",
                            "resource=" + entry.getKey(),
                            "provider=" + providerName));
                    continue;
                }
                valid.add(new ServiceLoaderProviderDefinition(
                        provider, constructor));
            }
            definitions.put(service.getReference(), List.copyOf(valid));
        }
        return new ServiceLoaderProtocolIndex(definitions, validation);
    }

    Map<TypeReference, List<ServiceLoaderProviderDefinition>> providers() {
        return providers;
    }

    boolean hasProviders(final TypeReference service) {
        return providers.containsKey(service);
    }

    List<ModelLimitation> limitations() {
        return limitations;
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
                            ServiceLoaderProtocolIndex::resourceName))
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

    private static ModelLimitation limitation(
            final String code,
            final String location,
            final String detail) {
        return new ModelLimitation(ModelKind.SERVICE_LOADER, code,
                ModuleAnalysisReason.INCONCLUSIVE_SERVICE_LOADER,
                location, detail);
    }
}
