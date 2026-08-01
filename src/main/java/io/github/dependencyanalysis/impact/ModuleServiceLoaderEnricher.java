package io.github.dependencyanalysis.impact;

import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAAbstractInvokeInstruction;
import com.ibm.wala.ssa.SSAInstruction;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.MethodReference;
import com.ibm.wala.types.TypeReference;

import io.github.dependencyanalysis.callgraph.ClassOwnership;
import io.github.dependencyanalysis.callgraph.CodeOrigin;
import io.github.dependencyanalysis.callgraph.EdgeKind;
import io.github.dependencyanalysis.callgraph.MethodId;
import io.github.dependencyanalysis.callgraph.ModuleCallGraphSession;
import io.github.dependencyanalysis.dependency.ResolvedArtifact;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Builds the module-local conservative ServiceLoader overlay. */
public final class ModuleServiceLoaderEnricher {

    /** Service resource prefix. */
    private static final String SERVICE_PREFIX = "META-INF/services/";

    /** ServiceLoader internal class name. */
    private static final String SERVICE_LOADER = "java/util/ServiceLoader";

    /**
     * Resolves reachable service loads against scope resources.
     *
     * @param unit module input
     * @param session live WALA graph
     * @return conservative overlay
     */
    public ServiceLoaderOverlay enrich(
            final ModuleAnalysisUnit unit,
            final ModuleCallGraphSession session) {
        final List<String> limitations = new ArrayList<>();
        try {
            final Map<String, Set<String>> providers = resources(unit);
            final Map<String, QueryEdge> edges = new LinkedHashMap<>();
            for (CGNode caller : session.getGraph()) {
                final IR ir = caller.getIR();
                if (ir == null) {
                    continue;
                }
                for (SSAInstruction instruction : ir.getInstructions()) {
                    if (!(instruction
                            instanceof SSAAbstractInvokeInstruction)) {
                        continue;
                    }
                    final SSAAbstractInvokeInstruction invoke =
                            (SSAAbstractInvokeInstruction) instruction;
                    if (!isServiceLoad(invoke.getDeclaredTarget())) {
                        continue;
                    }
                    final TypeReference serviceType = serviceType(
                            invoke, ir.getSymbolTable());
                    if (serviceType == null) {
                        limitations.add(
                                "Unresolved ServiceLoader service type: "
                                + methodIdentity(caller.getMethod()));
                        continue;
                    }
                    final String serviceName = owner(serviceType);
                    final Set<String> registered = providers.get(serviceName);
                    if (registered == null || registered.isEmpty()) {
                        continue;
                    }
                    addProviderEdges(caller, invoke,
                            serviceType, registered,
                            new OverlayBuildContext(
                                    unit.getModuleId(), session,
                                    edges, limitations));
                }
            }
            return new ServiceLoaderOverlay(
                    List.copyOf(edges.values()), stable(limitations));
        } catch (Exception exception) {
            limitations.add("ServiceLoader resource scan failed: "
                    + exception.getMessage());
            return new ServiceLoaderOverlay(List.of(), stable(limitations));
        }
    }

    private void addProviderEdges(
            final CGNode loadCaller,
            final SSAAbstractInvokeInstruction load,
            final TypeReference serviceType,
            final Set<String> registered,
            final OverlayBuildContext context) {
        final ModuleCallGraphSession session = context.session();
        final IClass service = session.getHierarchy().lookupClass(serviceType);
        if (service == null) {
            context.limitations().add("Unresolved ServiceLoader service: "
                    + owner(serviceType));
            return;
        }
        for (String providerName : registered.stream().sorted().toList()) {
            final IClass provider = resolveClass(providerName, session);
            if (provider == null || provider.isAbstract()
                    || provider.isInterface()
                    || !session.getHierarchy().isAssignableFrom(
                            service, provider)) {
                context.limitations().add("Invalid ServiceLoader provider: "
                        + providerName + " for " + owner(serviceType));
                continue;
            }
            final IMethod constructor = provider.getDeclaredMethods().stream()
                    .filter(IMethod::isInit)
                    .filter(IMethod::isPublic)
                    .filter(method -> "()V".equals(
                            method.getDescriptor().toString()))
                    .findFirst().orElse(null);
            if (constructor == null || !provider.isPublic()) {
                context.limitations().add(
                        "ServiceLoader provider lacks public "
                        + "zero-arg constructor: " + providerName);
                continue;
            }
            final QueryNode caller = queryNode(
                    context.moduleId(), loadCaller, session);
            for (QueryNode target : methodNodes(
                    context.moduleId(), constructor, session)) {
                addEdge(context.edges(), new QueryEdge(caller, target,
                        EdgeKind.SERVICE_LOADER,
                        evidence(service, provider, "constructor"),
                        load.getProgramCounter()));
            }
            addCompatibleInvokes(context.moduleId(), service, provider,
                    session, context.edges());
        }
    }

    private void addCompatibleInvokes(
            final ModuleId moduleId,
            final IClass service,
            final IClass provider,
            final ModuleCallGraphSession session,
            final Map<String, QueryEdge> edges) {
        for (CGNode caller : session.getGraph()) {
            final IR ir = caller.getIR();
            if (ir == null) {
                continue;
            }
            for (SSAInstruction instruction : ir.getInstructions()) {
                if (!(instruction
                        instanceof SSAAbstractInvokeInstruction)) {
                    continue;
                }
                final SSAAbstractInvokeInstruction invoke =
                        (SSAAbstractInvokeInstruction) instruction;
                final IClass declared = session.getHierarchy().lookupClass(
                        invoke.getDeclaredTarget().getDeclaringClass());
                if (declared == null
                        || !compatibleServiceType(
                                service, declared, session)) {
                    continue;
                }
                final IMethod implementation = provider.getMethod(
                        invoke.getDeclaredTarget().getSelector());
                if (implementation == null || implementation.isAbstract()) {
                    continue;
                }
                final QueryNode source = queryNode(
                        moduleId, caller, session);
                for (QueryNode target : methodNodes(
                        moduleId, implementation, session)) {
                    addEdge(edges, new QueryEdge(source, target,
                            EdgeKind.SERVICE_LOADER,
                            evidence(service, provider,
                                    implementation.getName().toString()),
                            invoke.getProgramCounter()));
                }
            }
        }
    }

    private boolean compatibleServiceType(
            final IClass service,
            final IClass declared,
            final ModuleCallGraphSession session) {
        return session.getHierarchy().isAssignableFrom(service, declared)
                || session.getHierarchy().isAssignableFrom(
                        declared, service);
    }

    private List<QueryNode> methodNodes(
            final ModuleId moduleId,
            final IMethod method,
            final ModuleCallGraphSession session) {
        final Set<CGNode> nodes = session.getGraph().getNodes(
                method.getReference());
        if (!nodes.isEmpty()) {
            return nodes.stream()
                    .sorted(Comparator.comparingInt(CGNode::getGraphNodeId))
                    .map(node -> (QueryNode) queryNode(
                            moduleId, node, session))
                    .toList();
        }
        final CodeOrigin origin = session.originOf(
                method.getDeclaringClass());
        return List.of(new OverlayMethodNode(
                methodId(moduleId, method, origin, session), origin));
    }

    private WalaQueryNode queryNode(
            final ModuleId moduleId,
            final CGNode node,
            final ModuleCallGraphSession session) {
        final CodeOrigin origin = session.originOf(
                node.getMethod().getDeclaringClass());
        return new WalaQueryNode(node,
                methodId(moduleId, node.getMethod(), origin, session),
                origin);
    }

    private MethodId methodId(
            final ModuleId moduleId,
            final IMethod method,
            final CodeOrigin origin,
            final ModuleCallGraphSession session) {
        final String owner = owner(
                method.getDeclaringClass().getReference());
        final ClassOwnership ownership =
                session.getOwnership().ownershipOf(owner);
        return new MethodId(owner, method.getName().toString(),
                method.getDescriptor().toString(),
                origin == CodeOrigin.PROJECT
                        ? moduleId.stableKey() : origin.name(),
                ownership == null ? "<jdk>"
                        : ownership.getSource().toString());
    }

    private TypeReference serviceType(
            final SSAAbstractInvokeInstruction invoke,
            final SymbolTable symbols) {
        if (invoke.getNumberOfUses() == 0) {
            return null;
        }
        final int valueNumber = invoke.getUse(0);
        if (!symbols.isConstant(valueNumber)) {
            return null;
        }
        final Object value = symbols.getConstantValue(valueNumber);
        return value instanceof TypeReference
                ? (TypeReference) value : null;
    }

    private boolean isServiceLoad(final MethodReference method) {
        final String owner = owner(method.getDeclaringClass());
        final String name = method.getName().toString();
        return SERVICE_LOADER.equals(owner)
                && ("load".equals(name) || "loadInstalled".equals(name));
    }

    private IClass resolveClass(
            final String name,
            final ModuleCallGraphSession session) {
        for (IClass type : session.getHierarchy()) {
            if (name.equals(owner(type.getReference()))) {
                return type;
            }
        }
        return null;
    }

    private Map<String, Set<String>> resources(
            final ModuleAnalysisUnit unit) throws IOException {
        final Map<String, Set<String>> result = new HashMap<>();
        readResourceDirectory(unit.getProjectClasses(), result);
        for (Path path : unit.getReactorDependencyClasses()) {
            readResourceDirectory(path, result);
        }
        for (ResolvedArtifact artifact : unit.getTargetArtifacts()) {
            if ("jar".equals(artifact.getArtifact().getType())) {
                readJarResources(artifact.getPath(), result);
            }
        }
        return result;
    }

    private void readResourceDirectory(
            final Path root,
            final Map<String, Set<String>> result) throws IOException {
        final Path directory = root.resolve(SERVICE_PREFIX);
        if (!Files.isDirectory(directory)) {
            return;
        }
        try (java.util.stream.Stream<Path> stream = Files.list(directory)) {
            for (Path file : stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(Path::toString)).toList()) {
                addProviders(file.getFileName().toString(),
                        Files.readString(file), result);
            }
        }
    }

    private void readJarResources(
            final Path path,
            final Map<String, Set<String>> result) throws IOException {
        try (JarFile jar = new JarFile(path.toFile(), false)) {
            for (JarEntry entry : jar.stream()
                    .filter(value -> !value.isDirectory())
                    .filter(value -> value.getName().startsWith(
                            SERVICE_PREFIX))
                    .sorted(Comparator.comparing(JarEntry::getName))
                    .toList()) {
                try (InputStream input = jar.getInputStream(entry)) {
                    addProviders(entry.getName().substring(
                                    SERVICE_PREFIX.length()),
                            new String(input.readAllBytes(),
                                    StandardCharsets.UTF_8), result);
                }
            }
        }
    }

    private void addProviders(
            final String service,
            final String content,
            final Map<String, Set<String>> result) {
        final Set<String> values = result.computeIfAbsent(
                service.replace('.', '/'), key -> new LinkedHashSet<>());
        for (String line : content.split("\\R")) {
            final String value = line.replaceFirst("#.*$", "").trim();
            if (!value.isEmpty()) {
                values.add(value.replace('.', '/'));
            }
        }
    }

    private void addEdge(
            final Map<String, QueryEdge> edges,
            final QueryEdge edge) {
        final String key = edge.getCaller().methodId() + "->"
                + edge.getCallee().methodId() + "@"
                + edge.getBytecodePc() + ":" + edge.getEvidence();
        edges.putIfAbsent(key, edge);
    }

    private String evidence(
            final IClass service,
            final IClass provider,
            final String operation) {
        return "SERVICE_LOADER|CONSERVATIVE|service="
                + owner(service.getReference()) + "|provider="
                + owner(provider.getReference()) + "|operation=" + operation;
    }

    private String methodIdentity(final IMethod method) {
        return owner(method.getDeclaringClass().getReference()) + "#"
                + method.getName() + "#" + method.getDescriptor();
    }

    private String owner(final TypeReference type) {
        final String value = type.getName().toString();
        return value.startsWith("L") ? value.substring(1) : value;
    }

    private List<String> stable(final List<String> values) {
        return values.stream().distinct().sorted().toList();
    }

    /**
     * Shared mutable collectors for one service resolution operation.
     *
     * @param moduleId current module
     * @param session live graph
     * @param edges overlay edge collector
     * @param limitations coverage limitations
     */
    private record OverlayBuildContext(
            ModuleId moduleId,
            ModuleCallGraphSession session,
            Map<String, QueryEdge> edges,
            List<String> limitations) {
    }
}
