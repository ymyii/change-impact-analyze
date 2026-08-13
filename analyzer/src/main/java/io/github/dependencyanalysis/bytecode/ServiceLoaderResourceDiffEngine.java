package io.github.dependencyanalysis.bytecode;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.impact.DependencyUpgradeKey;
import io.github.dependencyanalysis.jar.IJarRepository;
import io.github.dependencyanalysis.jar.JarLease;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

// Wiki: wiki/features/bytecode-diff-engine.md - ServiceLoader Resource Diff
/** Compares valid ServiceLoader registrations for one upgraded artifact. */
public final class ServiceLoaderResourceDiffEngine {

    /** Service configuration path prefix. */
    private static final String SERVICE_PREFIX = "META-INF/services/";

    /** Included public ChangePoint kinds. */
    private final Set<ChangePointKind> kinds;

    /**
     * Creates an engine honoring the command ChangePoint filter.
     *
     * @param includedKinds command ChangePoint filter
     */
    public ServiceLoaderResourceDiffEngine(
            final Set<ChangePointKind> includedKinds) {
        kinds = Set.copyOf(Objects.requireNonNull(
                includedKinds, "includedKinds"));
    }

    /**
     * Compares baseline/target service resources without failing bytecode Diff.
     *
     * @param upgrade exact module-bound artifact upgrade
     * @param repository command JAR repository
     * @param bytecodePoints bytecode changes for class-removal deduplication
     * @return stable resource facts, public ChangePoints and issues
     */
    public ServiceLoaderResourceDiffResult diff(
            final DependencyUpgradeKey upgrade,
            final IJarRepository repository,
            final List<ChangePoint> bytecodePoints) {
        Objects.requireNonNull(upgrade, "upgrade");
        Objects.requireNonNull(repository, "repository");
        Objects.requireNonNull(bytecodePoints, "bytecodePoints");
        final List<ServiceLoaderResourceIssue> issues = new ArrayList<>();
        final JarSnapshot baseline = snapshot(upgrade.getOldArtifact(),
                repository, issues);
        final JarSnapshot target = snapshot(upgrade.getNewArtifact(),
                repository, issues);
        if (baseline == null || target == null) {
            return new ServiceLoaderResourceDiffResult(
                    List.of(), List.of(), List.of(), issues);
        }
        final Set<String> removedClasses = new LinkedHashSet<>();
        bytecodePoints.stream().filter(point -> point.getKind()
                == ChangePointKind.CLASS_REMOVED).forEach(point ->
                removedClasses.add(point.getOwner()));
        final List<ServiceProviderRegistration> baselineValid =
                new ArrayList<>();
        final List<ServiceProviderRegistration> removed = new ArrayList<>();
        final List<ChangePoint> points = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry
                : baseline.resources().entrySet()) {
            final String service = entry.getKey();
            final Set<String> targetProviders = target.resources()
                    .getOrDefault(service, Set.of());
            for (String provider : entry.getValue()) {
                if (!validBaselineProvider(
                        service, provider, baseline.classes())) {
                    continue;
                }
                final ServiceProviderRegistration registration =
                        new ServiceProviderRegistration(
                                SERVICE_PREFIX + service.replace('/', '.'),
                                service, provider,
                                upgrade.getNewArtifact());
                baselineValid.add(registration);
                if (targetProviders.contains(provider)) {
                    continue;
                }
                removed.add(registration);
                if (kinds.contains(ChangePointKind
                        .SERVICE_PROVIDER_REGISTRATION_REMOVED)
                        && !removedClasses.contains(provider)) {
                    points.add(ChangePoint
                            .serviceProviderRegistrationRemoved(
                                    registration));
                }
            }
        }
        return new ServiceLoaderResourceDiffResult(
                baselineValid, removed, points, issues);
    }

    private JarSnapshot snapshot(
            final ArtifactCoord artifact,
            final IJarRepository repository,
            final List<ServiceLoaderResourceIssue> issues) {
        try (JarLease lease = repository.open(artifact)) {
            return new JarSnapshot(resources(lease.jarFile(), artifact,
                    issues), classes(lease.jarFile()));
        } catch (IOException | RuntimeException exception) {
            issues.add(issue("SERVICE_LOADER_RESOURCE_READ_FAILED",
                    artifact.toString(), exception));
            return null;
        }
    }

    private Map<String, Set<String>> resources(
            final JarFile jar,
            final ArtifactCoord artifact,
            final List<ServiceLoaderResourceIssue> issues) {
        final Map<String, Set<String>> result = new LinkedHashMap<>();
        final List<JarEntry> entries = jar.stream()
                .filter(entry -> !entry.isDirectory())
                .filter(entry -> entry.getName().startsWith(SERVICE_PREFIX))
                .sorted(java.util.Comparator.comparing(JarEntry::getName))
                .toList();
        for (JarEntry entry : entries) {
            final String service = binaryName(entry.getName()
                    .substring(SERVICE_PREFIX.length()));
            if (service == null) {
                issues.add(new ServiceLoaderResourceIssue(
                        "SERVICE_LOADER_RESOURCE_INVALID",
                        artifact + "|" + entry.getName(),
                        "invalid service name"));
                continue;
            }
            try (InputStream input = jar.getInputStream(entry)) {
                final String content = new String(
                        input.readAllBytes(), StandardCharsets.UTF_8);
                final Set<String> providers = result.computeIfAbsent(
                        service, ignored -> new LinkedHashSet<>());
                for (String line : content.split("\\R")) {
                    final String raw = line.replaceFirst("#.*$", "").trim();
                    if (raw.isEmpty()) {
                        continue;
                    }
                    final String provider = binaryName(raw);
                    if (provider == null) {
                        issues.add(new ServiceLoaderResourceIssue(
                                "SERVICE_LOADER_PROVIDER_NAME_INVALID",
                                artifact + "|" + entry.getName(), raw));
                    } else {
                        providers.add(provider);
                    }
                }
            } catch (IOException | RuntimeException exception) {
                issues.add(issue("SERVICE_LOADER_RESOURCE_READ_FAILED",
                        artifact + "|" + entry.getName(), exception));
            }
        }
        return result;
    }

    private Map<String, ClassInfo> classes(final JarFile jar)
            throws IOException {
        final Map<String, ClassInfo> result = new HashMap<>();
        final List<JarEntry> entries = jar.stream()
                .filter(entry -> !entry.isDirectory())
                .filter(entry -> entry.getName().endsWith(".class"))
                .filter(entry -> !entry.getName().startsWith(
                        "META-INF/versions/"))
                .sorted(java.util.Comparator.comparing(JarEntry::getName))
                .toList();
        for (JarEntry entry : entries) {
            final ClassInfoBuilder builder = new ClassInfoBuilder();
            try (InputStream input = jar.getInputStream(entry)) {
                new ClassReader(input).accept(builder,
                        ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG
                                | ClassReader.SKIP_FRAMES);
            }
            final ClassInfo value = builder.build();
            result.put(value.name(), value);
        }
        return result;
    }

    private boolean validBaselineProvider(
            final String service,
            final String provider,
            final Map<String, ClassInfo> classes) {
        final ClassInfo type = classes.get(provider);
        return type != null && type.isConcretePublic()
                && type.publicNoArgConstructor()
                && assignable(provider, service, classes,
                new LinkedHashSet<>());
    }

    private boolean assignable(
            final String type,
            final String service,
            final Map<String, ClassInfo> classes,
            final Set<String> visited) {
        if (service.equals(type)) {
            return true;
        }
        if (!visited.add(type)) {
            return false;
        }
        final ClassInfo value = classes.get(type);
        if (value == null) {
            return false;
        }
        if (value.superName() != null && assignable(
                value.superName(), service, classes, visited)) {
            return true;
        }
        for (String implemented : value.interfaces()) {
            if (assignable(implemented, service, classes, visited)) {
                return true;
            }
        }
        return false;
    }

    private String binaryName(final String value) {
        final String internal = value.replace('.', '/');
        if (internal.isBlank() || internal.startsWith("/")
                || internal.endsWith("/")) {
            return null;
        }
        for (String part : internal.split("/")) {
            if (part.isEmpty()
                    || !Character.isJavaIdentifierStart(part.charAt(0))) {
                return null;
            }
            for (int index = 1; index < part.length(); index++) {
                if (!Character.isJavaIdentifierPart(part.charAt(index))) {
                    return null;
                }
            }
        }
        return internal;
    }

    private ServiceLoaderResourceIssue issue(
            final String code,
            final String location,
            final Exception exception) {
        final String detail = exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
        return new ServiceLoaderResourceIssue(code, location, detail);
    }

    /**
     * Immutable JAR resource and type snapshot.
     *
     * @param resources normalized ServiceLoader resources
     * @param classes minimal class metadata
     */
    private record JarSnapshot(
            Map<String, Set<String>> resources,
            Map<String, ClassInfo> classes) {
    }

    /**
     * Minimum class facts required for baseline provider validation.
     *
     * @param name internal class name
     * @param superName internal superclass name
     * @param interfaces direct interface internal names
     * @param access class access flags
     * @param publicNoArgConstructor public no-arg constructor presence
     */
    private record ClassInfo(
            String name,
            String superName,
            List<String> interfaces,
            int access,
            boolean publicNoArgConstructor) {

        boolean isConcretePublic() {
            return (access & Opcodes.ACC_PUBLIC) != 0
                    && (access & (Opcodes.ACC_ABSTRACT
                    | Opcodes.ACC_INTERFACE)) == 0;
        }
    }

    /** ASM visitor collecting one immutable ClassInfo. */
    private static final class ClassInfoBuilder extends ClassVisitor {

        /** Class name. */
        private String name;

        /** Superclass name. */
        private String superName;

        /** Direct interfaces. */
        private List<String> interfaces = List.of();

        /** Class access. */
        private int access;

        /** Whether a public no-arg constructor exists. */
        private boolean publicNoArgConstructor;

        ClassInfoBuilder() {
            super(Opcodes.ASM9);
        }

        @Override
        public void visit(
                final int version,
                final int modifiers,
                final String internalName,
                final String signature,
                final String parent,
                final String[] implemented) {
            name = internalName;
            superName = parent;
            interfaces = implemented == null
                    ? List.of() : List.of(implemented);
            access = modifiers;
        }

        @Override
        public MethodVisitor visitMethod(
                final int modifiers,
                final String methodName,
                final String descriptor,
                final String signature,
                final String[] exceptions) {
            if ("<init>".equals(methodName) && "()V".equals(descriptor)
                    && (modifiers & Opcodes.ACC_PUBLIC) != 0) {
                publicNoArgConstructor = true;
            }
            return null;
        }

        ClassInfo build() {
            return new ClassInfo(name, superName,
                    List.copyOf(interfaces), access,
                    publicNoArgConstructor);
        }
    }
}
