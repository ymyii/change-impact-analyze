package io.github.dependencyanalysis.maven;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/** Atomically writes Classpath Evidence Schema v1. */
final class ClasspathEvidenceJsonWriter {

    /** Classpath Evidence Schema version. */
    private static final int SCHEMA_VERSION = 1;

    /** JSON stream factory. */
    private static final JsonFactory JSON = new JsonFactory();

    private ClasspathEvidenceJsonWriter() {
    }

    static void write(
            final Path output,
            final ClasspathEvidenceModel evidence) throws IOException {
        if (Files.exists(output)) {
            throw new IOException("Classpath evidence already exists: "
                    + output);
        }
        final Path temporary = output.resolveSibling(
                output.getFileName() + ".tmp-" + UUID.randomUUID());
        try {
            try (OutputStream stream = Files.newOutputStream(temporary);
                 JsonGenerator json = JSON.createGenerator(stream)) {
                json.useDefaultPrettyPrinter();
                json.writeStartObject();
                json.writeNumberField("schemaVersion", SCHEMA_VERSION);
                json.writeNumberField("javaMajor", evidence.getJavaMajor());
                json.writeFieldName("module");
                writeCoordinates(json, evidence.getModule());
                json.writeStringField("moduleDirectory",
                        evidence.getModuleDirectory().toString());
                json.writeArrayFieldStart("entries");
                for (ClasspathEvidenceModel.Entry entry
                        : evidence.getEntries()) {
                    json.writeStartObject();
                    json.writeNumberField("order", entry.getOrder());
                    json.writeStringField("origin", entry.getOrigin());
                    json.writeFieldName("coordinates");
                    writeCoordinates(json, entry.getCoordinates());
                    json.writeStringField("scope", entry.getScope());
                    json.writeStringField("absolutePath",
                            entry.getPath().toString());
                    json.writeEndObject();
                }
                json.writeEndArray();
                json.writeArrayFieldStart("issues");
                for (String issue : evidence.getIssues()) {
                    json.writeString(issue);
                }
                json.writeEndArray();
                json.writeEndObject();
            }
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE);
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
}
