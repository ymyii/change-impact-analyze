package io.github.dependencyanalysis.impact;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import io.github.dependencyanalysis.callgraph.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.CallGraphStrategyCapabilities;
import io.github.dependencyanalysis.callgraph.CallGraphMethodIdentity;
import io.github.dependencyanalysis.callgraph.CallGraphNodeIdentity;
import io.github.dependencyanalysis.callgraph.CallGraphNodeIr;
import io.github.dependencyanalysis.callgraph.CallGraphNodePathStep;
import io.github.dependencyanalysis.callgraph.CallGraphNodeReachabilityPath;
import io.github.dependencyanalysis.callgraph.CallGraphRankedNode;
import io.github.dependencyanalysis.callgraph.CallGraphRelatedMethod;
import io.github.dependencyanalysis.callgraph.CallGraphTopologySnapshot;
import io.github.dependencyanalysis.callgraph.JdkModelSelection;
import io.github.dependencyanalysis.callgraph.ModuleCallGraphSession;
import io.github.dependencyanalysis.callgraph.WalaReflectionOptions;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.jar.IJarRepository;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

// Wiki: wiki/features/call-graph-engine.md - Benchmark diagnostics exporter
/** Atomically writes optional read-only Call Graph benchmark diagnostics. */
final class CallGraphDiagnosticsExporter {

    /** Diagnostics JSON Schema version. */
    static final int SCHEMA_VERSION = 7;

    /** SHA-256 algorithm name. */
    private static final String SHA_256 = "SHA-256";

    /** Exact related CGNode examples retained per child IMethod. */
    private static final int RELATED_NODE_EXAMPLE_LIMIT = 10;

    /** JSON factory. */
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    /** Target JDK runtime. */
    private final JavaRuntimeDescriptor javaRuntime;

    /** Source resolver. */
    private final CallGraphMethodSourceBuilder sources;

    /**
     * Creates an exporter.
     *
     * @param diagnostics diagnostics
     * @param runtime target JDK runtime
     * @param jarRepository command repository
     */
    CallGraphDiagnosticsExporter(
            final DiagnosticLog diagnostics,
            final JavaRuntimeDescriptor runtime,
            final IJarRepository jarRepository) {
        javaRuntime = runtime;
        sources = new CallGraphMethodSourceBuilder(
                diagnostics, runtime, jarRepository);
    }

    /**
     * Writes diagnostics for every successfully captured module graph.
     *
     * @param output destination JSON
     * @param algorithm Call Graph algorithm
     * @param kObjDepth k-object receiver allocation-string depth
     * @param reflectionOptions WALA ReflectionOptions
     * @param dependencyScope requested dependency method-body scope
     * @param jdkModel JDK Method Model selection
     * @param modules module analysis results
     * @throws IOException on publication failure
     */
    void write(
            final Path output,
            final CallGraphAlgorithm algorithm,
            final int kObjDepth,
            final WalaReflectionOptions reflectionOptions,
            final DependencyAnalysisScopeMode dependencyScope,
            final JdkModelSelection jdkModel,
            final List<ModuleAnalysisResult> modules) throws IOException {
        final Path destination = output.toAbsolutePath().normalize();
        Files.createDirectories(destination.getParent());
        final Path temporary = destination.resolveSibling(
                destination.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            try (JsonGenerator json = JSON_FACTORY.createGenerator(
                    Files.newBufferedWriter(temporary,
                            StandardCharsets.UTF_8))) {
                writeDocument(json, algorithm, kObjDepth, reflectionOptions,
                        dependencyScope, jdkModel, modules);
            }
            move(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void writeDocument(
            final JsonGenerator json,
            final CallGraphAlgorithm algorithm,
            final int kObjDepth,
            final WalaReflectionOptions reflectionOptions,
            final DependencyAnalysisScopeMode dependencyScope,
            final JdkModelSelection jdkModel,
            final List<ModuleAnalysisResult> modules) throws IOException {
        json.useDefaultPrettyPrinter();
        json.writeStartObject();
        writeConfiguration(json, algorithm, kObjDepth, reflectionOptions,
                dependencyScope, jdkModel);
        json.writeStringField("jdk", javaRuntime.getVersion());
        json.writeArrayFieldStart("modules");
        for (ModuleAnalysisResult module : modules) {
            final ModuleCallGraphSession session = module.getSession();
            if (session == null || session.getTopology().isEmpty()) {
                continue;
            }
            writeModule(json, module, session,
                    session.getTopology().orElseThrow());
        }
        json.writeEndArray();
        json.writeEndObject();
    }

    static void writeConfiguration(
            final JsonGenerator json,
            final CallGraphAlgorithm algorithm,
            final int kObjDepth,
            final WalaReflectionOptions reflectionOptions,
            final DependencyAnalysisScopeMode dependencyScope,
            final JdkModelSelection jdkModel) throws IOException {
        json.writeNumberField("schemaVersion", SCHEMA_VERSION);
        json.writeStringField("algorithm", algorithm.identifier());
        if (algorithm == CallGraphAlgorithm.K_OBJ) {
            json.writeNumberField("kObjDepth",
                    CallGraphAlgorithm.requireValidKObjDepth(kObjDepth));
        } else {
            json.writeNullField("kObjDepth");
        }
        json.writeStringField("reflectionOptions",
                reflectionOptions.identifier());
        json.writeStringField("reflectionApplied",
                algorithm == CallGraphAlgorithm.CHA
                        ? "not applied by cha" : "applied");
        json.writeStringField("jdkModel", jdkModel.identifier());
        json.writeStringField("requestedDependencyAnalysisScope",
                dependencyScope.identifier());
    }

    private void writeModule(
            final JsonGenerator json,
            final ModuleAnalysisResult module,
            final ModuleCallGraphSession session,
            final CallGraphTopologySnapshot topology) throws IOException {
        final Map<SourceKey, CallGraphMethodSource> cache =
                new LinkedHashMap<>();
        json.writeStartObject();
        json.writeStringField("module", module.getModuleId().stableKey());
        json.writeStringField("actualDependencyAnalysisScope",
                module.getUnit().getChangedPathSelection().actualMode()
                        .identifier());
        json.writeStringField("dependencyScopeFallbackReason",
                module.getUnit().getChangedPathSelection().fallbackReason()
                        .orElse(""));
        json.writeNumberField("entrypointCount",
                topology.entrypointCount());
        json.writeNumberField("cgNodeCount", topology.nodeCount());
        json.writeNumberField("cgEdgeCount", topology.edgeCount());
        final var boundary = session.getDependencyBoundary();
        json.writeNumberField("realExternalMethodNodeCount",
                boundary.realExternalMethodNodes());
        json.writeNumberField("noOpMethodNodeCount",
                boundary.noOpMethodNodes());
        json.writeNumberField("factoryMethodNodeCount",
                boundary.factoryMethodNodes());
        json.writeNumberField("dangerousTransferCount",
                boundary.dangerousTransfers().size());
        json.writeNumberField("bodyBoundaryHitCount",
                boundary.bodyBoundaryHits().size());
        writeCapabilities(json, session.getStrategyCapabilities());
        writeEvidenceSummary(json, session.getChangePointEvidence());
        final List<ArtifactCoord> externalArtifacts = module.getUnit()
                .getTargetArtifacts().stream().distinct()
                .sorted(java.util.Comparator.comparing(ArtifactCoord::toString))
                .toList();
        final long realArtifacts = externalArtifacts.stream().filter(value ->
                module.getUnit().getChangedPathSelection().policyFor(value)
                        == DependencyMethodBodyPolicy.REAL_IR).count();
        json.writeNumberField("realExternalArtifactCount", realArtifacts);
        json.writeNumberField("noOpExternalArtifactCount",
                externalArtifacts.size() - realArtifacts);
        json.writeArrayFieldStart("dependencyPaths");
        module.getUnit().getChangedPathSelection().paths().forEach(path -> {
            try {
                json.writeStartObject();
                json.writeStringField("seed", path.seed().toString());
                json.writeStringField("path", path.stablePath());
                json.writeEndObject();
            } catch (IOException exception) {
                throw new java.io.UncheckedIOException(exception);
            }
        });
        json.writeEndArray();
        writeRanks(json, "topCallers", "CALLEE", "topCallees", session,
                topology.topCallers(), cache);
        writeRanks(json, "topCallees", "CALLER", "topCallers", session,
                topology.topCallees(), cache);
        json.writeEndObject();
    }

    private void writeCapabilities(
            final JsonGenerator json,
            final CallGraphStrategyCapabilities capabilities)
            throws IOException {
        json.writeObjectFieldStart("strategyCapabilities");
        json.writeBooleanField("pointsToAnalysis",
                capabilities.pointsToAnalysis());
        json.writeBooleanField("jdkModelSupported",
                capabilities.jdkModelSupported());
        json.writeBooleanField("jdkBodiesTraversed",
                capabilities.jdkBodiesTraversed());
        json.writeBooleanField("callerLocalConstants",
                capabilities.callerLocalConstants());
        json.writeStringField("reflection",
                capabilities.reflection().name());
        json.writeStringField("serviceLoader",
                capabilities.serviceLoader().name());
        json.writeStringField("methodHandle",
                capabilities.methodHandle().name());
        json.writeStringField("invokeDynamic",
                capabilities.invokeDynamic().name());
        json.writeEndObject();
    }

    private void writeEvidenceSummary(
            final JsonGenerator json,
            final ChangePointEvidenceIndex evidence) throws IOException {
        final Map<String, Long> resolutions = evidence.resolutions().stream()
                .collect(java.util.stream.Collectors.groupingBy(value ->
                        value.status().name(), java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()));
        final Map<String, Long> kinds = evidence.resolutions().stream()
                .flatMap(value -> value.evidence().stream())
                .collect(java.util.stream.Collectors.groupingBy(value ->
                        value.kind().name(), java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()));
        final Map<String, Long> mechanisms = evidence.resolutions().stream()
                .flatMap(value -> value.evidence().stream())
                .collect(java.util.stream.Collectors.groupingBy(value ->
                        value.mechanism().name(), java.util.TreeMap::new,
                        java.util.stream.Collectors.counting()));
        json.writeObjectField("evidenceResolutionSummary", resolutions);
        json.writeObjectField("evidenceKindSummary", kinds);
        json.writeObjectField("evidenceMechanismSummary", mechanisms);
        json.writeNumberField("localConstantResolutionSuccessCount",
                evidence.localConstantSuccessCount());
        json.writeNumberField("localConstantResolutionUnresolvedCount",
                evidence.localConstantUnresolvedCount());
    }

    private void writeRanks(
            final JsonGenerator json,
            final String field,
            final String relatedDirection,
            final String childField,
            final ModuleCallGraphSession session,
            final List<CallGraphRankedNode> ranks,
            final Map<SourceKey, CallGraphMethodSource> cache)
            throws IOException {
        json.writeArrayFieldStart(field);
        int rank = 0;
        for (CallGraphRankedNode value : ranks) {
            rank++;
            final CallGraphMethodIdentity method = value.node().method();
            final SourceKey sourceKey = new SourceKey(
                    method, value.node().walaSynthetic());
            final CallGraphMethodSource source = cache.computeIfAbsent(
                    sourceKey, ignored -> sources.build(
                            session, method, value.node().walaSynthetic()));
            json.writeStartObject();
            json.writeNumberField("rank", rank);
            json.writeStringField("relatedDirection", relatedDirection);
            writeNode(json, "node", value.node());
            json.writeNumberField("relatedCgNodeCount",
                    value.relatedCgNodeCount());
            json.writeNumberField("distinctRelatedMethodCount",
                    value.distinctRelatedMethodCount());
            json.writeNumberField("rawEdgeCount", value.rawEdgeCount());
            json.writeBooleanField("cycle", value.cycle());
            writeRelatedMethods(json, childField,
                    value.topRelatedMethods());
            writeSource(json, source);
            writeIr(json, value.ir());
            writePaths(json, value.reachabilityPaths());
            json.writeEndObject();
        }
        json.writeEndArray();
    }

    private void writeRelatedMethods(
            final JsonGenerator json,
            final String field,
            final List<CallGraphRelatedMethod> methods) throws IOException {
        json.writeArrayFieldStart(field);
        int rank = 0;
        for (CallGraphRelatedMethod value : methods) {
            rank++;
            json.writeStartObject();
            json.writeNumberField("rank", rank);
            writeMethod(json, "method", value.method());
            json.writeNumberField("relatedCgNodeCount",
                    value.relatedCgNodeCount());
            json.writeNumberField("rawEdgeCount", value.rawEdgeCount());
            json.writeNumberField("omittedRelatedCgNodeCount", Math.max(
                    0, value.relatedCgNodeCount()
                    - RELATED_NODE_EXAMPLE_LIMIT));
            json.writeArrayFieldStart("relatedCgNodeExamples");
            for (CallGraphNodeIdentity node : value.relatedNodes().stream()
                    .limit(RELATED_NODE_EXAMPLE_LIMIT).toList()) {
                writeNodeValue(json, node);
            }
            json.writeEndArray();
            json.writeEndObject();
        }
        json.writeEndArray();
    }

    private void writeSource(
            final JsonGenerator json,
            final CallGraphMethodSource source) throws IOException {
        json.writeObjectFieldStart("source");
        json.writeStringField("status", source.status().name());
        json.writeStringField("sha256", source.sha256());
        json.writeStringField("classpathSource", source.classpathSource());
        json.writeStringField("reason", source.reason());
        json.writeStringField("text", source.source());
        json.writeEndObject();
    }

    private void writeIr(
            final JsonGenerator json,
            final CallGraphNodeIr ir) throws IOException {
        json.writeObjectFieldStart("ir");
        json.writeStringField("status",
                ir.isAvailable() ? "AVAILABLE" : "UNAVAILABLE");
        json.writeStringField("sha256",
                ir.isAvailable() ? digest(ir.text()) : "");
        json.writeStringField("reason", ir.reason());
        json.writeStringField("text", ir.text());
        json.writeEndObject();
    }

    static void writePaths(
            final JsonGenerator json,
            final List<CallGraphNodeReachabilityPath> paths)
            throws IOException {
        json.writeArrayFieldStart("reachabilityPaths");
        for (CallGraphNodeReachabilityPath path : paths) {
            json.writeStartObject();
            json.writeStringField("rootKind", path.rootKind().name());
            writeNode(json, "root", path.root());
            json.writeArrayFieldStart("steps");
            for (CallGraphNodePathStep step : path.steps()) {
                json.writeStartObject();
                writeNodeFields(json, step.node());
                json.writeBooleanField("cycle", step.cycle());
                json.writeEndObject();
            }
            json.writeEndArray();
            json.writeEndObject();
        }
        json.writeEndArray();
    }

    private static void writeNode(
            final JsonGenerator json,
            final String field,
            final CallGraphNodeIdentity node) throws IOException {
        json.writeObjectFieldStart(field);
        writeNodeFields(json, node);
        json.writeEndObject();
    }

    private static void writeNodeValue(
            final JsonGenerator json,
            final CallGraphNodeIdentity node) throws IOException {
        json.writeStartObject();
        writeNodeFields(json, node);
        json.writeEndObject();
    }

    private static void writeNodeFields(
            final JsonGenerator json,
            final CallGraphNodeIdentity node) throws IOException {
        json.writeNumberField("graphNodeId", node.graphNodeId());
        json.writeStringField("context", node.context());
        json.writeBooleanField("walaSynthetic", node.walaSynthetic());
        json.writeStringField("sentinelRole", node.sentinelRole().name());
        json.writeStringField("identity", node.stableKey());
        writeMethod(json, "method", node.method());
    }

    private static void writeMethod(
            final JsonGenerator json,
            final String field,
            final CallGraphMethodIdentity method) throws IOException {
        json.writeObjectFieldStart(field);
        json.writeStringField("owner", method.owner());
        json.writeStringField("name", method.name());
        json.writeStringField("descriptor", method.descriptor());
        json.writeStringField("origin", method.origin().name());
        json.writeStringField("identity", method.stableKey());
        json.writeEndObject();
    }

    private String digest(final String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance(
                    SHA_256).digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private void move(final Path source, final Path destination)
            throws IOException {
        try {
            Files.move(source, destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination,
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Method source cache key that preserves WALA-generated semantics.
     *
     * @param method context-independent Method identity
     * @param walaSynthetic whether WALA generated or summarized the Method
     */
    private record SourceKey(
            CallGraphMethodIdentity method,
            boolean walaSynthetic) {
    }
}
