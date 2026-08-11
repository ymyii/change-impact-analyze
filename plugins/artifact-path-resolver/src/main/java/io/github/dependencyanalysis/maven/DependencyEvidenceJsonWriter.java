package io.github.dependencyanalysis.maven;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Atomically writes Dependency Evidence Schema v3. */
final class DependencyEvidenceJsonWriter {

    /** Current evidence contract version. */
    private static final int SCHEMA_VERSION = 3;

    /** Streaming JSON factory. */
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    private DependencyEvidenceJsonWriter() {
    }

    static void write(
            final Path output,
            final DependencyEvidenceModel evidence) throws IOException {
        if (Files.exists(output)) {
            throw new IOException(
                    "Dependency evidence already exists: " + output);
        }
        final Path temporary = output.resolveSibling(
                output.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            try (OutputStream stream = Files.newOutputStream(temporary);
                 JsonGenerator json = JSON_FACTORY.createGenerator(stream)) {
                json.useDefaultPrettyPrinter();
                json.writeStartObject();
                json.writeNumberField("schemaVersion", SCHEMA_VERSION);
                json.writeFieldName("module");
                writeCoordinates(json, evidence.getModule());
                json.writeStringField("moduleDirectory",
                        evidence.getModuleDirectory().toString());
                json.writeArrayFieldStart("dependencies");
                for (DependencyEvidenceModel.SelectedDependency dependency
                        : evidence.getDependencies()) {
                    writeDependency(json, dependency);
                }
                json.writeEndArray();
                writeOccurrenceGraph(json, evidence.getOccurrenceGraph());
                json.writeArrayFieldStart("selectedReactorKeys");
                for (String key : evidence.getSelectedReactorKeys()) {
                    json.writeString(key);
                }
                json.writeEndArray();
                json.writeArrayFieldStart("artifacts");
                for (ResolvedArtifactPath artifact
                        : evidence.getArtifacts()) {
                    json.writeStartObject();
                    json.writeFieldName("coordinates");
                    writeCoordinates(json, artifact.getCoordinates());
                    json.writeStringField("absolutePath",
                            artifact.getAbsolutePath().toString());
                    json.writeEndObject();
                }
                json.writeEndArray();
                json.writeEndObject();
            }
            Files.move(temporary, output,
                    StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void writeDependency(
            final JsonGenerator json,
            final DependencyEvidenceModel.SelectedDependency dependency)
            throws IOException {
        json.writeStartObject();
        json.writeFieldName("coordinates");
        writeCoordinates(json, dependency.getCoordinates());
        json.writeStringField("scope", dependency.getScope());
        json.writeArrayFieldStart("children");
        for (DependencyEvidenceModel.SelectedDependency child
                : dependency.getChildren()) {
            writeDependency(json, child);
        }
        json.writeEndArray();
        json.writeEndObject();
    }

    private static void writeOccurrenceGraph(
            final JsonGenerator json,
            final DependencyEvidenceModel.OccurrenceGraph graph)
            throws IOException {
        json.writeObjectFieldStart("occurrenceGraph");
        json.writeStringField("rootId", graph.getRootId());
        json.writeArrayFieldStart("occurrences");
        for (DependencyEvidenceModel.Occurrence occurrence
                : graph.getOccurrences()) {
            json.writeStartObject();
            json.writeStringField("id", occurrence.getId());
            json.writeFieldName("coordinates");
            writeCoordinates(json, occurrence.getCoordinates());
            json.writeStringField("scope", occurrence.getScope());
            json.writeBooleanField("moduleRoot",
                    occurrence.isModuleRoot());
            json.writeBooleanField("reactor", occurrence.isReactor());
            json.writeEndObject();
        }
        json.writeEndArray();
        json.writeArrayFieldStart("edges");
        for (DependencyEvidenceModel.Edge edge : graph.getEdges()) {
            json.writeStartObject();
            json.writeStringField("parentId", edge.getParentId());
            json.writeStringField("childId", edge.getChildId());
            json.writeEndObject();
        }
        json.writeEndArray();
        json.writeEndObject();
    }

    private static void writeCoordinates(
            final JsonGenerator json,
            final ArtifactCoordinates coordinates) throws IOException {
        json.writeStartObject();
        json.writeStringField("groupId", coordinates.getGroupId());
        json.writeStringField("artifactId", coordinates.getArtifactId());
        json.writeStringField("type", coordinates.getType());
        json.writeStringField("extension", coordinates.getExtension());
        json.writeStringField("classifier", coordinates.getClassifier());
        json.writeStringField("version", coordinates.getVersion());
        json.writeStringField("baseVersion", coordinates.getBaseVersion());
        json.writeEndObject();
    }
}
