package io.github.dependencyanalysis.impact;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import io.github.dependencyanalysis.callgraph.CallGraphMethodIdentity;
import io.github.dependencyanalysis.callgraph.CallGraphNodeIdentity;
import io.github.dependencyanalysis.callgraph.CallGraphNodePathStep;
import io.github.dependencyanalysis.callgraph.CallGraphNodeReachabilityPath;
import io.github.dependencyanalysis.callgraph.CallGraphNodeSentinelRole;
import io.github.dependencyanalysis.callgraph.CallGraphPathRootKind;
import io.github.dependencyanalysis.callgraph.CodeOrigin;

import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CallGraphDiagnosticsExporterTest {

    /** Expected diagnostics JSON Schema version. */
    private static final int EXPECTED_SCHEMA_VERSION = 4;

    @Test
    void writesSchemaV4TypedSentinelReachabilityPath() throws Exception {
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
            json.writeNumberField("schemaVersion",
                    CallGraphDiagnosticsExporter.SCHEMA_VERSION);
            CallGraphDiagnosticsExporter.writePaths(json, List.of(path));
            json.writeEndObject();
        }

        assertThat(CallGraphDiagnosticsExporter.SCHEMA_VERSION)
                .isEqualTo(EXPECTED_SCHEMA_VERSION);
        assertThat(output.toString())
                .contains("\"reachabilityPaths\"")
                .contains("\"rootKind\":\"FAKE_ROOT\"")
                .contains("\"sentinelRole\":\"FAKE_ROOT\"")
                .contains("\"sentinelRole\":\"FAKE_WORLD_CLINIT\"")
                .contains("\"sentinelRole\":\"NONE\"")
                .doesNotContain("entrypointPaths", "pathStatus",
                        "UNREACHABLE_FROM_DECLARED_ENTRYPOINTS");
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
