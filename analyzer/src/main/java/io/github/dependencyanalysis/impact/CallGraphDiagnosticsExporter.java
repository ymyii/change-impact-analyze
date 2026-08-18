package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.impact.pruning.ImpactPathPruningSummary;

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
import io.github.dependencyanalysis.callgraph.strategy.cha
        .JdkDeclaredDispatchPruningSummary;
import io.github.dependencyanalysis.callgraph.strategy.WalaReflectionOptions;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.bytecode.SsaComparisonEvidence;
import io.github.dependencyanalysis.bytecode.SsaComparisonStatus;
import io.github.dependencyanalysis.bytecode.DecompileComparisonStatus;
import io.github.dependencyanalysis.bytecode.DecompileComparisonSummary;
import io.github.dependencyanalysis.jar.IJarRepository;
import io.github.dependencyanalysis.runtime.JavaRuntimeDescriptor;
import io.github.dependencyanalysis.runtime.ReportCache;

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
    static final int SCHEMA_VERSION = 13;

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
        json.writeObjectFieldStart("ssaEquivalence");
        json.writeBooleanField("enabled", true);
        json.writeBooleanField("fixed", true);
        json.writeEndObject();
        json.writeArrayFieldStart("impactPathPruningExtensions");
        for (String identifier
                : ImpactPathPruningSummary.FIXED_EXTENSION_IDS) {
            json.writeString(identifier);
        }
        json.writeEndArray();
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
        json.writeNumberField("ancestorRetainedExternalTypeCount",
                boundary.ancestorRetainedExternalTypeCount());
        json.writeNumberField("ancestorRetainedExternalMethodNodeCount",
                boundary.ancestorRetainedExternalMethodNodeCount());
        json.writeNumberField("prunedExternalMethodTargetCount",
                boundary.prunedExternalMethodTargetCount());
        final JdkDeclaredDispatchPruningSummary jdkDispatch =
                session.getJdkDispatchPruning();
        json.writeNumberField("jdkDeclaredDispatchPrunedTargetCount",
                jdkDispatch.prunedTargetCount());
        json.writeArrayFieldStart("jdkDeclaredDispatchPrunedExamples");
        for (JdkDeclaredDispatchPruningSummary.TargetExample example
                : jdkDispatch.examples()) {
            json.writeStartObject();
            json.writeStringField("declaredTarget",
                    example.declaredTarget());
            json.writeStringField("removedTarget", example.removedTarget());
            json.writeStringField("removedOrigin", example.removedOrigin());
            json.writeStringField("reason", example.reason());
            json.writeEndObject();
        }
        json.writeEndArray();
        json.writeNumberField("dangerousTransferCount",
                boundary.dangerousTransfers().size());
        json.writeNumberField("bodyBoundaryHitCount",
                boundary.bodyBoundaryHits().size());
        writeCapabilities(json, session.getStrategyCapabilities());
        writeEvidenceSummary(json, module.getChangePointEvidence());
        writeImpactPathPruning(json, module);
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
     * @throws IOException on JSON failure
     */
    void writeModuleRecord(
            final JsonGenerator json,
            final ModuleAnalysisResult module) throws IOException {
        final ModuleCallGraphSession session = module.getSession();
        if (session == null || session.getTopology().isEmpty()) {
            throw new IllegalArgumentException(
                    "Module diagnostics requires live topology");
        }
        writeModule(json, module, session,
                session.getTopology().orElseThrow());
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
            final List<ReportCache.Fragment> fragments)
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
                        configuration.jdkModel());
                json.writeStringField("jdk", javaRuntime.getVersion());
                json.writeArrayFieldStart("modules");
                for (ReportCache.Fragment fragment : fragments.stream()
                        .filter(value -> "diagnostic-module".equals(
                                value.kind()))
                        .sorted(java.util.Comparator.comparing(
                                ReportCache.Fragment::stableKey))
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

    private void writeImpactPathPruning(
            final JsonGenerator json,
            final ModuleAnalysisResult module) throws IOException {
        json.writeObjectFieldStart("impactPathPruning");
        json.writeArrayFieldStart("extensions");
        for (ImpactPathPruningSummary.ExtensionSummary extension
                : module.getImpactPathPruning().extensions()) {
            json.writeStartObject();
            json.writeStringField("identifier", extension.identifier());
            json.writeBooleanField("experimental", extension.experimental());
            json.writeStringField("status", extension.status().label());
            writePruningMetrics(json, extension.metrics());
            json.writeArrayFieldStart("examples");
            for (ImpactPathPruningSummary.EdgeExample example
                    : extension.examples()) {
                json.writeStartObject();
                json.writeStringField("caller", example.caller());
                json.writeStringField("callee", example.callee());
                json.writeNumberField("programCounter",
                        example.programCounter());
                json.writeStringField("decision", example.decision());
                json.writeStringField("reason", example.reason());
                json.writeStringField("inferredReceiverSummary",
                        example.inferredReceiverSummary());
                json.writeEndObject();
            }
            json.writeEndArray();
            json.writeEndObject();
        }
        json.writeEndArray();
        json.writeEndObject();
        json.writeObjectFieldStart("changePointCollection");
        json.writeObjectFieldStart("ssaEquivalence");
        final List<SsaComparisonEvidence> comparisons = module.getUnit()
                .getSsaComparisons();
        final int semanticEligible = module.getUnit()
                .getDecompileComparisons().size();
        json.writeBooleanField("enabled", true);
        json.writeBooleanField("fixed", true);
        json.writeNumberField("evaluationOrder", 2);
        json.writeStringField("shortCircuitedBy",
                "DECOMPILED_JAVA_TEXT_IDENTICAL");
        json.writeNumberField("eligible", semanticEligible);
        json.writeNumberField("executed", comparisons.size());
        json.writeNumberField("skipped",
                semanticEligible - comparisons.size());
        json.writeNumberField("matchedSuppressed",
                comparisonCount(comparisons, SsaComparisonStatus.MATCHED));
        json.writeNumberField("different",
                comparisonCount(comparisons, SsaComparisonStatus.DIFFERENT));
        json.writeNumberField("unknown",
                comparisonCount(comparisons, SsaComparisonStatus.UNKNOWN));
        json.writeNumberField("elapsedMillis", comparisons.stream()
                .mapToLong(SsaComparisonEvidence::getElapsedMillis).sum());
        json.writeStringField("limitation",
                "Normalized SSA matching is not proof of source or complete "
                        + "runtime behavior equivalence and may suppress "
                        + "real changes.");
        json.writeArrayFieldStart("comparisons");
        for (SsaComparisonEvidence comparison : comparisons.stream()
                .sorted(java.util.Comparator.comparing(
                        SsaComparisonEvidence::stableKey)).toList()) {
            json.writeStartObject();
            json.writeStringField("oldArtifact",
                    comparison.getOldArtifact().toString());
            json.writeStringField("newArtifact",
                    comparison.getNewArtifact().toString());
            json.writeStringField("owner", comparison.getOwner());
            json.writeStringField("name", comparison.getName());
            json.writeStringField("descriptor",
                    comparison.getDescriptor());
            json.writeStringField("oldHash", comparison.getOldHash());
            json.writeStringField("newHash", comparison.getNewHash());
            json.writeNumberField("oldMajorVersion",
                    comparison.getOldMajorVersion());
            json.writeNumberField("newMajorVersion",
                    comparison.getNewMajorVersion());
            json.writeStringField("status",
                    comparison.getStatus().name());
            json.writeStringField("reason", comparison.getReason());
            json.writeNumberField("elapsedMillis",
                    comparison.getElapsedMillis());
            json.writeEndObject();
        }
        json.writeEndArray();
        json.writeEndObject();
        writeDecompileEquivalence(json, module.getUnit()
                .getDecompileComparisons());
        json.writeEndObject();
    }

    private void writeDecompileEquivalence(
            final JsonGenerator json,
            final List<DecompileComparisonSummary> comparisons)
            throws IOException {
        json.writeObjectFieldStart("decompiledJavaEquivalence");
        json.writeBooleanField("enabled", true);
        json.writeBooleanField("fixed", true);
        json.writeNumberField("evaluationOrder", 1);
        json.writeStringField("shortCircuitWhen", "IDENTICAL");
        json.writeNumberField("eligible", comparisons.size());
        json.writeNumberField("identical",
                decompileComparisonCount(comparisons,
                        DecompileComparisonStatus.IDENTICAL));
        json.writeNumberField("different",
                decompileComparisonCount(comparisons,
                        DecompileComparisonStatus.DIFFERENT));
        json.writeNumberField("unknown",
                decompileComparisonCount(comparisons,
                        DecompileComparisonStatus.UNKNOWN));
        json.writeNumberField("elapsedMillis", comparisons.stream()
                .mapToLong(DecompileComparisonSummary::elapsedMillis).sum());
        json.writeArrayFieldStart("comparisons");
        for (DecompileComparisonSummary comparison : comparisons.stream()
                .sorted(java.util.Comparator.comparing(
                        DecompileComparisonSummary::stableKey)).toList()) {
            json.writeStartObject();
            json.writeStringField("oldArtifact",
                    comparison.oldArtifact().toString());
            json.writeStringField("newArtifact",
                    comparison.newArtifact().toString());
            json.writeStringField("owner", comparison.owner());
            json.writeStringField("name", comparison.name());
            json.writeStringField("descriptor", comparison.descriptor());
            json.writeStringField("oldHash", comparison.oldHash());
            json.writeStringField("newHash", comparison.newHash());
            json.writeNumberField("oldMajorVersion",
                    comparison.oldMajorVersion());
            json.writeNumberField("newMajorVersion",
                    comparison.newMajorVersion());
            json.writeStringField("status", comparison.status().name());
            json.writeStringField("reason", comparison.reason());
            json.writeNumberField("elapsedMillis",
                    comparison.elapsedMillis());
            json.writeArrayFieldStart("suppressionReasons");
            for (Enum<?> reason : comparison.suppressionReasons().stream()
                    .sorted().toList()) {
                json.writeString(reason.name());
            }
            json.writeEndArray();
            json.writeEndObject();
        }
        json.writeEndArray();
        json.writeEndObject();
    }

    private void writePruningMetrics(
            final JsonGenerator json,
            final ImpactPathPruningSummary.Metrics metrics)
            throws IOException {
        json.writeObjectFieldStart("metrics");
        json.writeNumberField("requests", metrics.requests());
        json.writeNumberField("uniqueEvaluations",
                metrics.uniqueEvaluations());
        json.writeNumberField("cacheHits", metrics.cacheHits());
        json.writeNumberField("pruned", metrics.pruned());
        json.writeNumberField("feasible", metrics.feasible());
        json.writeNumberField("unknown", metrics.unknown());
        json.writeNumberField("notApplicable", metrics.notApplicable());
        json.writeNumberField("failOpenErrors", metrics.failOpenErrors());
        json.writeNumberField("callsitesChecked",
                metrics.callsitesChecked());
        json.writeNumberField("invokeInstancesChecked",
                metrics.invokeInstancesChecked());
        json.writeNumberField("exactResolutions",
                metrics.exactResolutions());
        json.writeNumberField("upperBoundResolutions",
                metrics.upperBoundResolutions());
        json.writeNumberField("noNormalTargetResolutions",
                metrics.noNormalTargetResolutions());
        json.writeNumberField("unknownResolutions",
                metrics.unknownResolutions());
        json.writeEndObject();
    }

    private long comparisonCount(
            final List<SsaComparisonEvidence> comparisons,
            final SsaComparisonStatus status) {
        return comparisons.stream()
                .filter(value -> value.getStatus() == status).count();
    }

    private long decompileComparisonCount(
            final List<DecompileComparisonSummary> comparisons,
            final DecompileComparisonStatus status) {
        return comparisons.stream()
                .filter(value -> value.status() == status).count();
    }

    private void requireComplete(final ReportCache.Fragment fragment)
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
