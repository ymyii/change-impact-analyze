package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.impact.refinement.ResultRefinementAlgorithm;
import io.github.dependencyanalysis.impact.refinement.ResultRefinementSelection;
import io.github.dependencyanalysis.impact.refinement.cha.ChaLocalReceiverRefinementSummary;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;

import io.github.dependencyanalysis.callgraph.strategy.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphStrategyCapabilities;
import io.github.dependencyanalysis.callgraph.topology.CallGraphMethodIdentity;
import io.github.dependencyanalysis.callgraph.topology.CallGraphNodeIdentity;
import io.github.dependencyanalysis.callgraph.topology.CallGraphNodeIr;
import io.github.dependencyanalysis.callgraph.topology.CallGraphNodePathStep;
import io.github.dependencyanalysis.callgraph.topology.CallGraphNodeReachabilityPath;
import io.github.dependencyanalysis.callgraph.topology.CallGraphRankedNode;
import io.github.dependencyanalysis.callgraph.topology.CallGraphRelatedMethod;
import io.github.dependencyanalysis.callgraph.topology.CallGraphTopologySnapshot;
import io.github.dependencyanalysis.callgraph.jdk.JdkModelSelection;
import io.github.dependencyanalysis.callgraph.engine.ModuleCallGraphSession;
import io.github.dependencyanalysis.callgraph.strategy.WalaReflectionOptions;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.jar.IJarRepository;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.ReportTaskCache;

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
    static final int SCHEMA_VERSION = 9;

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
     * @param configuration command-wide analysis configuration
     * @param modules module analysis results
     * @throws IOException on publication failure
     */
    void write(
            final Path output,
            final AnalysisRunConfiguration configuration,
            final List<ModuleAnalysisResult> modules) throws IOException {
        final Path destination = output.toAbsolutePath().normalize();
        Files.createDirectories(destination.getParent());
        final Path temporary = destination.resolveSibling(
                destination.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            try (JsonGenerator json = JSON_FACTORY.createGenerator(
                    Files.newBufferedWriter(temporary,
                            StandardCharsets.UTF_8))) {
                writeDocument(json, configuration, modules);
            }
            move(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void writeDocument(
            final JsonGenerator json,
            final AnalysisRunConfiguration configuration,
            final List<ModuleAnalysisResult> modules) throws IOException {
        json.useDefaultPrettyPrinter();
        json.writeStartObject();
        writeConfiguration(json, configuration.callGraphAlgorithm(),
                configuration.kObjDepth(), configuration.reflectionOptions(),
                configuration.dependencyAnalysisScope(),
                configuration.jdkModel(), configuration.resultRefinements());
        json.writeStringField("jdk", javaRuntime.getVersion());
        json.writeArrayFieldStart("modules");
        for (ModuleAnalysisResult module : modules) {
            final ModuleCallGraphSession session = module.getSession();
            if (session == null || session.getTopology().isEmpty()) {
                continue;
            }
            writeModule(json, module, session,
                    session.getTopology().orElseThrow(),
                    configuration.resultRefinements());
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
            final JdkModelSelection jdkModel,
            final ResultRefinementSelection refinements) throws IOException {
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
        json.writeArrayFieldStart("resultRefinementAlgorithms");
        for (String identifier : refinements.identifiers()) {
            json.writeString(identifier);
        }
        json.writeEndArray();
    }

    private void writeModule(
            final JsonGenerator json,
            final ModuleAnalysisResult module,
            final ModuleCallGraphSession session,
            final CallGraphTopologySnapshot topology,
            final ResultRefinementSelection refinements) throws IOException {
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
        json.writeNumberField("ancestorRetainedExternalTypeCount",
                boundary.ancestorRetainedExternalTypeCount());
        json.writeNumberField("ancestorRetainedExternalMethodNodeCount",
                boundary.ancestorRetainedExternalMethodNodeCount());
        json.writeNumberField("prunedExternalMethodTargetCount",
                boundary.prunedExternalMethodTargetCount());
        json.writeNumberField("dangerousTransferCount",
                boundary.dangerousTransfers().size());
        json.writeNumberField("bodyBoundaryHitCount",
                boundary.bodyBoundaryHits().size());
        writeCapabilities(json, session.getStrategyCapabilities());
        writeEvidenceSummary(json, module.getChangePointEvidence());
        writeResultRefinements(json, module, refinements);
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

    /**
     * Writes one live Module as a cache JSON record before session release.
     *
     * @param json fragment writer
     * @param module live module result
     * @param refinements command-wide result-refinement selection
     * @throws IOException on JSON failure
     */
    void writeModuleRecord(
            final JsonGenerator json,
            final ModuleAnalysisResult module,
            final ResultRefinementSelection refinements) throws IOException {
        final ModuleCallGraphSession session = module.getSession();
        if (session == null || session.getTopology().isEmpty()) {
            throw new IllegalArgumentException(
                    "Module diagnostics requires live topology");
        }
        writeModule(json, module, session,
                session.getTopology().orElseThrow(), refinements);
    }

    /**
     * Streams completed Module fragments into one atomic diagnostics file.
     *
     * @param output final diagnostics path
     * @param configuration command-wide analysis configuration
     * @param fragments stable completed Module fragments
     * @throws IOException on publication failure
     */
    void writeFragments(
            final Path output,
            final AnalysisRunConfiguration configuration,
            final List<ReportTaskCache.Fragment> fragments)
            throws IOException {
        final Path destination = output.toAbsolutePath().normalize();
        Files.createDirectories(destination.getParent());
        final Path temporary = destination.resolveSibling(
                destination.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            try (JsonGenerator json = JSON_FACTORY.createGenerator(
                    Files.newBufferedWriter(temporary,
                            StandardCharsets.UTF_8))) {
                json.useDefaultPrettyPrinter();
                json.writeStartObject();
                writeConfiguration(json, configuration.callGraphAlgorithm(),
                        configuration.kObjDepth(),
                        configuration.reflectionOptions(),
                        configuration.dependencyAnalysisScope(),
                        configuration.jdkModel(),
                        configuration.resultRefinements());
                json.writeStringField("jdk", javaRuntime.getVersion());
                json.writeArrayFieldStart("modules");
                for (ReportTaskCache.Fragment fragment : fragments.stream()
                        .filter(value -> "diagnostic-module".equals(
                                value.kind()))
                        .sorted(java.util.Comparator.comparing(
                                ReportTaskCache.Fragment::stableKey))
                        .toList()) {
                    requireComplete(fragment);
                    try (JsonParser parser = JSON_FACTORY.createParser(
                            Files.newBufferedReader(fragment.path(),
                                    StandardCharsets.UTF_8))) {
                        if (parser.nextToken() != JsonToken.START_OBJECT) {
                            throw new IOException(
                                    "Invalid diagnostics fragment: "
                                            + fragment.path());
                        }
                        json.copyCurrentStructure(parser);
                        if (parser.nextToken() != null) {
                            throw new IOException(
                                    "Multiple diagnostics records: "
                                            + fragment.path());
                        }
                    }
                }
                json.writeEndArray();
                json.writeEndObject();
            }
            move(temporary, destination);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private void writeResultRefinements(
            final JsonGenerator json,
            final ModuleAnalysisResult module,
            final ResultRefinementSelection refinements) throws IOException {
        json.writeObjectFieldStart("resultRefinements");
        json.writeObjectFieldStart("cha-local-receiver-inference");
        final ChaLocalReceiverRefinementSummary receiver =
                module.getReceiverRefinement();
        json.writeStringField("status", receiver.status().label());
        final ChaLocalReceiverRefinementSummary.Metrics metrics =
                receiver.metrics();
        json.writeNumberField("predecessorEdgeRequests",
                metrics.predecessorEdgeRequests());
        json.writeNumberField("uniqueEvaluatedEdges",
                metrics.uniqueEvaluatedEdges());
        json.writeNumberField("cacheHits", metrics.cacheHits());
        json.writeNumberField("callsitesChecked", metrics.callsitesChecked());
        json.writeNumberField("invokeInstancesChecked",
                metrics.invokeInstancesChecked());
        json.writeNumberField("prunedEdges", metrics.prunedEdges());
        json.writeNumberField("retainedFeasibleEdges",
                metrics.retainedFeasibleEdges());
        json.writeNumberField("retainedUnknownEdges",
                metrics.retainedUnknownEdges());
        json.writeNumberField("notApplicableEdges",
                metrics.notApplicableEdges());
        json.writeNumberField("exactResolutions",
                metrics.exactResolutions());
        json.writeNumberField("upperBoundResolutions",
                metrics.upperBoundResolutions());
        json.writeNumberField("noNormalTargetResolutions",
                metrics.noNormalTargetResolutions());
        json.writeNumberField("unknownResolutions",
                metrics.unknownResolutions());
        json.writeArrayFieldStart("examples");
        for (ChaLocalReceiverRefinementSummary.EdgeExample example
                : receiver.examples()) {
            json.writeStartObject();
            json.writeStringField("caller", example.caller());
            json.writeStringField("callee", example.callee());
            json.writeNumberField("programCounter",
                    example.programCounter());
            json.writeStringField("invocationKind",
                    example.invocationKind());
            json.writeStringField("decision", example.decision());
            json.writeStringField("reason", example.reason());
            json.writeStringField("receiverSummary",
                    example.receiverSummary());
            json.writeEndObject();
        }
        json.writeEndArray();
        json.writeEndObject();
        json.writeObjectFieldStart("ssa-equivalence");
        final boolean ssaSelected = refinements.isEnabled(
                ResultRefinementAlgorithm.SSA_EQUIVALENCE);
        json.writeStringField("status", ssaSelected
                ? "applied" : "not selected");
        json.writeNumberField("comparisonCount",
                module.getEquivalenceResults().size());
        json.writeEndObject();
        json.writeEndObject();
    }

    private void requireComplete(final ReportTaskCache.Fragment fragment)
            throws IOException {
        if (!Files.isRegularFile(fragment.path())
                || !Files.isRegularFile(fragment.completeMarker())) {
            throw new IOException(
                    "Incomplete report cache fragment: " + fragment.path());
        }
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
        writeCountMap(json, "evidenceResolutionSummary", resolutions);
        writeCountMap(json, "evidenceKindSummary", kinds);
        writeCountMap(json, "evidenceMechanismSummary", mechanisms);
        json.writeNumberField("localConstantResolutionSuccessCount",
                evidence.localConstantSuccessCount());
        json.writeNumberField("localConstantResolutionUnresolvedCount",
                evidence.localConstantUnresolvedCount());
    }

    static void writeCountMap(
            final JsonGenerator json,
            final String field,
            final Map<String, Long> values) throws IOException {
        json.writeObjectFieldStart(field);
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            json.writeNumberField(entry.getKey(), entry.getValue());
        }
        json.writeEndObject();
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
