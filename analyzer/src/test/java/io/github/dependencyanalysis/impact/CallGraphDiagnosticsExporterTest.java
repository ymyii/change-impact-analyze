package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.impact.refinement.ResultRefinementAlgorithm;
import io.github.dependencyanalysis.impact.refinement.ResultRefinementSelection;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import io.github.dependencyanalysis.callgraph.topology.CallGraphMethodIdentity;
import io.github.dependencyanalysis.callgraph.topology.CallGraphNodeIdentity;
import io.github.dependencyanalysis.callgraph.topology.CallGraphNodePathStep;
import io.github.dependencyanalysis.callgraph.topology.CallGraphNodeReachabilityPath;
import io.github.dependencyanalysis.callgraph.topology.CallGraphNodeSentinelRole;
import io.github.dependencyanalysis.callgraph.topology.CallGraphPathRootKind;
import io.github.dependencyanalysis.callgraph.strategy.CallGraphAlgorithm;
import io.github.dependencyanalysis.callgraph.model.CodeOrigin;
import io.github.dependencyanalysis.callgraph.jdk.JdkModelSelection;
import io.github.dependencyanalysis.callgraph.strategy.WalaReflectionOptions;

import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CallGraphDiagnosticsExporterTest {

    /** Expected diagnostics JSON Schema version. */
    private static final int EXPECTED_SCHEMA_VERSION = 10;

    @Test
    void writesSchemaV10ConfigurationAndTypedSentinelPath() throws Exception {
        final CallGraphNodeIdentity fakeRoot = node(
                0, "com.ibm.wala.FakeRoot", "fakeRootMethod",
                CallGraphNodeSentinelRole.FAKE_ROOT);
        final CallGraphNodeIdentity fakeWorld = node(
                1, "com.ibm.wala.FakeWorldClinit", "fakeWorldClinit",
                CallGraphNodeSentinelRole.FAKE_WORLD_CLINIT);
        final CallGraphNodeIdentity target = node(
                2, "example.Target", "call",
                CallGraphNodeSentinelRole.NONE);
        final CallGraphNodeReachabilityPath path =
                new CallGraphNodeReachabilityPath(
                        CallGraphPathRootKind.FAKE_ROOT, fakeRoot,
                        List.of(new CallGraphNodePathStep(fakeRoot, false),
                                new CallGraphNodePathStep(fakeWorld, false),
                                new CallGraphNodePathStep(target, true)));
        final StringWriter output = new StringWriter();

        try (JsonGenerator json = new JsonFactory().createGenerator(output)) {
            json.writeStartObject();
            CallGraphDiagnosticsExporter.writeConfiguration(
                    json, CallGraphAlgorithm.K_OBJ,
                    CallGraphAlgorithm.defaultKObjDepth(),
                    WalaReflectionOptions.defaultOptions(),
                    DependencyAnalysisScopeMode.CHANGED_PATHS,
                    JdkModelSelection.JDK8,
                    ResultRefinementSelection.of(
                            ResultRefinementAlgorithm.SSA_EQUIVALENCE));
            CallGraphDiagnosticsExporter.writePaths(json, List.of(path));
            json.writeEndObject();
        }

        assertThat(CallGraphDiagnosticsExporter.SCHEMA_VERSION)
                .isEqualTo(EXPECTED_SCHEMA_VERSION);
        assertThat(output.toString())
                .contains("\"schemaVersion\":10")
                .contains("\"reflectionApplied\":\"applied\"")
                .contains("\"kObjDepth\":1")
                .contains("\"jdkModel\":\"jdk8\"")
                .contains("\"resultRefinementAlgorithms\""
                        + ":[\"ssa-equivalence\"]")
                .contains("\"reachabilityPaths\"")
                .contains("\"rootKind\":\"FAKE_ROOT\"")
                .contains("\"sentinelRole\":\"FAKE_ROOT\"")
                .contains("\"sentinelRole\":\"FAKE_WORLD_CLINIT\"")
                .contains("\"sentinelRole\":\"NONE\"")
                .doesNotContain("entrypointPaths", "pathStatus",
                        "UNREACHABLE_FROM_DECLARED_ENTRYPOINTS",
                        "availableTarget", "unavailableTarget",
                        "hitTarget");
    }

    @Test
    void writesKObjDepthForKObjConfiguration() throws Exception {
        final StringWriter output = new StringWriter();

        try (JsonGenerator json = new JsonFactory().createGenerator(output)) {
            json.writeStartObject();
            CallGraphDiagnosticsExporter.writeConfiguration(
                    json, CallGraphAlgorithm.K_OBJ, 2,
                    WalaReflectionOptions.defaultOptions(),
                    DependencyAnalysisScopeMode.CHANGED_PATHS,
                    JdkModelSelection.JDK8,
                    ResultRefinementSelection.defaultSelection());
            json.writeEndObject();
        }

        assertThat(output.toString())
                .contains("\"algorithm\":\"k-obj\"")
                .contains("\"kObjDepth\":2");
    }

    @Test
    void writesChaReflectionNotAppliedAndNoneModel() throws Exception {
        final StringWriter output = new StringWriter();

        try (JsonGenerator json = new JsonFactory().createGenerator(output)) {
            json.writeStartObject();
            CallGraphDiagnosticsExporter.writeConfiguration(
                    json, CallGraphAlgorithm.CHA,
                    CallGraphAlgorithm.defaultKObjDepth(),
                    WalaReflectionOptions.defaultOptions(),
                    DependencyAnalysisScopeMode.CHANGED_PATHS,
                    JdkModelSelection.NONE,
                    ResultRefinementSelection.defaultSelection());
            json.writeEndObject();
        }

        assertThat(output.toString())
                .contains("\"algorithm\":\"cha\"")
                .contains("\"reflectionApplied\":\"not applied by cha\"")
                .contains("\"jdkModel\":\"none\"");
    }

    @Test
    void writesCountMapWithoutObjectCodec() throws Exception {
        final StringWriter output = new StringWriter();

        try (JsonGenerator json = new JsonFactory().createGenerator(output)) {
            json.writeStartObject();
            CallGraphDiagnosticsExporter.writeCountMap(
                    json, "counts", Map.of("A", 2L, "B", 1L));
            json.writeEndObject();
        }

        assertThat(output.toString())
                .contains("\"counts\"")
                .contains("\"A\":2")
                .contains("\"B\":1");
    }

    private CallGraphNodeIdentity node(
            final int nodeId,
            final String owner,
            final String name,
            final CallGraphNodeSentinelRole role) {
        return new CallGraphNodeIdentity(
                nodeId,
                new CallGraphMethodIdentity(
                        owner, name, "()V", CodeOrigin.SYNTHETIC),
                "Everywhere", true, role);
    }
}
