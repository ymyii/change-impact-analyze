package io.github.dependencyanalysis.maven;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

/** Writes Artifact Path Schema v1 without publishing partial output. */
final class ArtifactPathJsonWriter {

    /** Current JSON contract version. */
    private static final int SCHEMA_VERSION = 1;

    /** Streaming JSON factory. */
    private static final JsonFactory JSON_FACTORY = new JsonFactory();

    private ArtifactPathJsonWriter() {
    }

    static void write(
            final Path output,
            final ArtifactCoordinates module,
            final Path moduleDirectory,
            final List<ResolvedArtifactPath> artifacts)
            throws IOException {
        final Path temporary = output.resolveSibling(
                output.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            try (OutputStream stream = Files.newOutputStream(temporary);
                 JsonGenerator json = JSON_FACTORY.createGenerator(stream)) {
                json.useDefaultPrettyPrinter();
                json.writeStartObject();
                json.writeNumberField("schemaVersion", SCHEMA_VERSION);
                json.writeObjectFieldStart("module");
                json.writeFieldName("coordinates");
                writeCoordinates(json, module);
                json.writeStringField(
                        "baseDirectory", moduleDirectory.toString());
                json.writeEndObject();
                json.writeArrayFieldStart("artifacts");
                for (ResolvedArtifactPath artifact : artifacts) {
                    json.writeStartObject();
                    json.writeFieldName("coordinates");
                    writeCoordinates(json, artifact.getCoordinates());
                    json.writeStringField("scope", artifact.getScope());
                    json.writeStringField("absolutePath",
                            artifact.getAbsolutePath().toString());
                    json.writeEndObject();
                }
                json.writeEndArray();
                json.writeEndObject();
            }
            atomicReplace(temporary, output);
        } finally {
            Files.deleteIfExists(temporary);
        }
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

    private static void atomicReplace(
            final Path source,
            final Path target) throws IOException {
        Files.move(source, target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }
}
