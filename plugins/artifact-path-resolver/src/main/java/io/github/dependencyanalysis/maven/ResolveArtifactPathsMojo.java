package io.github.dependencyanalysis.maven;

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.artifact.DefaultArtifactType;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Wiki: wiki/features/dependency-tree-extraction.md - GraphML 驱动物理路径绑定
/**
 * Resolves GraphML-selected external artifacts in the current Maven session.
 */
@Mojo(name = "resolve-artifact-paths", threadSafe = true)
public final class ResolveArtifactPathsMojo extends AbstractMojo {

    /** Stable implementation marker printed for runtime verification. */
    private static final String IMPLEMENTATION_MARKER = "graphml-v2";

    /** Resolver request context for project dependencies. */
    private static final String REQUEST_CONTEXT = "project";

    /** System dependency scope. */
    private static final String SYSTEM_SCOPE = "system";

    /** Current effective project. */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** Current reactor session. */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /** Current repository system session. */
    @Parameter(defaultValue = "${repositorySystemSession}",
            readonly = true, required = true)
    private RepositorySystemSession repositorySystemSession;

    /** Effective project repositories after Maven settings processing. */
    @Parameter(defaultValue = "${project.remoteProjectRepositories}",
            readonly = true, required = true)
    private List<RemoteRepository> remoteRepositories;

    /** Maven Resolver entrypoint supplied by the running Maven. */
    @Component
    private RepositorySystem repositorySystem;

    /** Descriptor for the Plugin version Maven actually selected. */
    @Parameter(defaultValue = "${plugin}", readonly = true, required = true)
    private PluginDescriptor pluginDescriptor;

    /** Module-local Dependency Plugin GraphML filename. */
    @Parameter(property = "cia.dependencyGraphFileName", required = true)
    private String dependencyGraphFileName;

    /** Module-local output filename. */
    @Parameter(property = "cia.resolvedArtifactsFileName", required = true)
    private String resolvedArtifactsFileName;

    @Override
    public void execute()
            throws MojoExecutionException, MojoFailureException {
        final Path graphml = moduleFile(
                "cia.dependencyGraphFileName", dependencyGraphFileName);
        final Path output = moduleFile(
                "cia.resolvedArtifactsFileName", resolvedArtifactsFileName);
        logImplementationEvidence(graphml.getFileName().toString());
        if (!Files.isRegularFile(graphml)) {
            throw new MojoFailureException(
                    "Dependency GraphML file is unavailable: " + graphml);
        }

        final GraphmlDependencyReader.Graph graph;
        try {
            graph = GraphmlDependencyReader.read(graphml);
        } catch (GraphmlDependencyReader.GraphmlReadException exception) {
            throw new MojoFailureException(exception.getMessage(), exception);
        }
        validateModule(graph.getModule(), graphml);

        final Set<String> reactors = reactorIdentities();
        final Map<String, Candidate> candidates =
                new LinkedHashMap<String, Candidate>();
        final Map<String, Artifact> systemCandidates =
                new LinkedHashMap<String, Artifact>();
        final Map<String, SystemArtifact> systemArtifacts =
                systemArtifacts();
        for (GraphmlDependencyReader.Dependency dependency
                : graph.getDependencies()) {
            final Artifact artifact = toResolverArtifact(
                    dependency.getCoordinate());
            final String identity = ArtifactCoordinates.from(artifact)
                    .identity();
            if (!reactors.contains(identity)) {
                if (SYSTEM_SCOPE.equals(dependency.getScope())) {
                    systemCandidates.put(identity, artifact);
                } else {
                    candidates.put(identity, new Candidate(artifact));
                }
            }
        }

        final Map<String, ResolvedArtifactPath> resolvedByIdentity =
                new LinkedHashMap<String, ResolvedArtifactPath>();
        for (ResolvedArtifactPath artifact : resolve(
                new ArrayList<Candidate>(candidates.values()))) {
            addResolvedBinding(resolvedByIdentity, artifact);
        }
        for (Map.Entry<String, Artifact> entry
                : systemCandidates.entrySet()) {
            addResolvedBinding(resolvedByIdentity,
                    resolveSystemArtifact(systemArtifacts,
                            entry.getKey(), entry.getValue()));
        }
        final List<ResolvedArtifactPath> resolved =
                new ArrayList<ResolvedArtifactPath>(
                        resolvedByIdentity.values());
        Collections.sort(resolved, ResolvedArtifactPath.ORDER);
        try {
            ArtifactPathJsonWriter.write(output, resolved);
        } catch (IOException exception) {
            throw new MojoExecutionException(
                    "Unable to publish resolved artifact JSON: " + output,
                    exception);
        }
    }

    private void logImplementationEvidence(final String graphFilename) {
        final String version = pluginDescriptor == null
                ? "unavailable" : pluginDescriptor.getVersion();
        String sourceValue = "unavailable";
        try {
            final CodeSource codeSource = ResolveArtifactPathsMojo.class
                    .getProtectionDomain().getCodeSource();
            if (codeSource == null || codeSource.getLocation() == null) {
                throw new IOException("code source is absent");
            }
            final Path source = Paths.get(codeSource.getLocation().toURI())
                    .toAbsolutePath().normalize();
            sourceValue = source.toString();
            if (!Files.isRegularFile(source)) {
                throw new IOException(
                        "code source is not a regular JAR: " + source);
            }
        } catch (IOException | URISyntaxException
                 | RuntimeException exception) {
            getLog().warn("Artifact Path Plugin JAR evidence unavailable: "
                    + safeEvidence(exception.getMessage()));
        }
        getLog().info("Artifact Path Plugin implementation="
                + IMPLEMENTATION_MARKER
                + "; version=" + safeEvidence(version)
                + "; source=" + safeEvidence(sourceValue)
                + "; dependencyGraphFileName="
                + safeEvidence(graphFilename));
    }

    private String safeEvidence(final String value) {
        if (value == null) {
            return "unavailable";
        }
        return value.replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private Path moduleFile(
            final String property,
            final String filenameValue) throws MojoFailureException {
        if (filenameValue == null || filenameValue.trim().isEmpty()) {
            throw new MojoFailureException(property + " must be a filename");
        }
        final String value = filenameValue.trim();
        final Path filename;
        try {
            filename = Paths.get(value);
        } catch (RuntimeException exception) {
            throw new MojoFailureException(
                    "Invalid " + property + ": " + value, exception);
        }
        if (filename.isAbsolute()
                || filename.getNameCount() != 1
                || value.contains("/")
                || value.contains("\\")
                || value.equals(".")
                || value.equals("..")
                || value.contains("..")) {
            throw new MojoFailureException(
                    property + " must be a filename: " + value);
        }
        return project.getBasedir().toPath().toAbsolutePath()
                .normalize().resolve(filename);
    }

    private void validateModule(
            final GraphmlDependencyReader.Coordinate graphModule,
            final Path graphml) throws MojoFailureException {
        final ArtifactCoordinates expected = moduleCoordinates();
        final ArtifactCoordinates actual = ArtifactCoordinates.from(
                toResolverArtifact(graphModule));
        if (!expected.identity().equals(actual.identity())) {
            throw new MojoFailureException(
                    "GraphML root does not match current MavenProject: "
                            + "expected="
                            + expected.identity() + "; actual="
                            + actual.identity() + "; file=" + graphml);
        }
    }

    private Artifact toResolverArtifact(
            final GraphmlDependencyReader.Coordinate coordinate) {
        return toResolverArtifact(
                coordinate.getGroupId(),
                coordinate.getArtifactId(),
                coordinate.getType(),
                coordinate.getClassifier(),
                coordinate.getVersion());
    }

    private Artifact toResolverArtifact(
            final String groupId,
            final String artifactId,
            final String typeValue,
            final String classifierValue,
            final String version) {
        final String type = defaultValue(typeValue, "jar");
        ArtifactType artifactType = repositorySystemSession
                .getArtifactTypeRegistry().get(type);
        if (artifactType == null) {
            artifactType = new DefaultArtifactType(type);
        }
        final Map<String, String> properties =
                new HashMap<String, String>();
        properties.put(ArtifactProperties.TYPE, type);
        final String classifier = defaultValue(
                classifierValue,
                defaultValue(artifactType.getClassifier(), ""));
        return new DefaultArtifact(
                groupId,
                artifactId,
                classifier,
                artifactType.getExtension(),
                version,
                properties,
                artifactType);
    }

    private Map<String, SystemArtifact> systemArtifacts()
            throws MojoFailureException {
        final Map<String, SystemArtifact> artifacts =
                new LinkedHashMap<String, SystemArtifact>();
        for (org.apache.maven.model.Dependency dependency
                : project.getDependencies()) {
            if (!SYSTEM_SCOPE.equals(dependency.getScope())) {
                continue;
            }
            final Artifact artifact = toResolverArtifact(
                    dependency.getGroupId(),
                    dependency.getArtifactId(),
                    dependency.getType(),
                    dependency.getClassifier(),
                    dependency.getVersion());
            final String identity = ArtifactCoordinates.from(artifact)
                    .identity();
            final String systemPath = dependency.getSystemPath();
            if (systemPath == null || systemPath.trim().isEmpty()) {
                throw new MojoFailureException(
                        "System dependency has no systemPath: " + identity);
            }
            final SystemArtifact previous = artifacts.put(identity,
                    new SystemArtifact(systemPath));
            if (previous != null) {
                throw new MojoFailureException(
                        "Duplicate effective system dependency: " + identity);
            }
        }
        return artifacts;
    }

    private ResolvedArtifactPath resolveSystemArtifact(
            final Map<String, SystemArtifact> systemArtifacts,
            final String identity,
            final Artifact artifact) throws MojoFailureException {
        final SystemArtifact systemArtifact = systemArtifacts.get(identity);
        if (systemArtifact == null) {
            throw new MojoFailureException(
                    "GraphML system dependency has no matching effective "
                            + "MavenProject dependency: " + identity);
        }
        final Path declaredPath;
        try {
            declaredPath = Paths.get(systemArtifact.getPath());
        } catch (RuntimeException exception) {
            throw new MojoFailureException(
                    "Invalid system dependency path: "
                            + systemArtifact.getPath(), exception);
        }
        if (!declaredPath.isAbsolute()) {
            throw new MojoFailureException(
                    "System dependency path must be absolute: "
                            + declaredPath);
        }
        final Path path;
        try {
            path = declaredPath.toRealPath();
        } catch (IOException exception) {
            throw new MojoFailureException(
                    "System dependency path is unavailable: "
                            + systemArtifact.getPath(), exception);
        }
        if (!path.isAbsolute() || !Files.isRegularFile(path)) {
            throw new MojoFailureException(
                    "System dependency is not an absolute file: " + path);
        }
        return new ResolvedArtifactPath(
                ArtifactCoordinates.from(artifact), path);
    }

    private Artifact toResolverArtifact(
            final org.apache.maven.artifact.Artifact artifact) {
        final ArtifactHandler handler = artifact.getArtifactHandler();
        final String type = defaultValue(artifact.getType(),
                project.getPackaging());
        final String extension = handler == null
                ? type : defaultValue(handler.getExtension(), type);
        final String classifier = defaultValue(
                artifact.getClassifier(), handler == null
                        ? "" : defaultValue(handler.getClassifier(), ""));
        final Map<String, String> properties =
                new HashMap<String, String>();
        properties.put(ArtifactProperties.TYPE, type);
        final ArtifactType artifactType = new DefaultArtifactType(
                type, extension, classifier,
                handler == null ? "none" : defaultValue(
                        handler.getLanguage(), "none"),
                handler != null && handler.isAddedToClasspath(),
                handler != null && handler.isIncludesDependencies());
        return new DefaultArtifact(artifact.getGroupId(),
                artifact.getArtifactId(), classifier, null,
                artifact.getVersion(), properties, artifactType);
    }

    private Set<String> reactorIdentities() {
        final Set<String> identities = new LinkedHashSet<String>();
        final List<MavenProject> projects = session.getProjects() == null
                ? Collections.<MavenProject>emptyList()
                : session.getProjects();
        for (MavenProject reactorProject : projects) {
            if (reactorProject.getArtifact() != null) {
                identities.add(ArtifactCoordinates.from(
                        toResolverArtifact(reactorProject.getArtifact()))
                        .identity());
            }
        }
        return identities;
    }

    private List<ResolvedArtifactPath> resolve(
            final List<Candidate> candidates)
            throws MojoExecutionException {
        if (candidates.isEmpty()) {
            return new ArrayList<ResolvedArtifactPath>();
        }
        final List<ArtifactRequest> requests =
                new ArrayList<ArtifactRequest>();
        final Map<ArtifactRequest, Candidate> byRequest =
                new LinkedHashMap<ArtifactRequest, Candidate>();
        final List<RemoteRepository> repositories = remoteRepositories == null
                ? Collections.<RemoteRepository>emptyList()
                : remoteRepositories;
        for (Candidate candidate : candidates) {
            final ArtifactRequest request = new ArtifactRequest(
                    candidate.getArtifact(), repositories, REQUEST_CONTEXT);
            requests.add(request);
            byRequest.put(request, candidate);
        }
        final List<ArtifactResult> results;
        try {
            results = repositorySystem.resolveArtifacts(
                    repositorySystemSession, requests);
        } catch (ArtifactResolutionException exception) {
            throw new MojoExecutionException(
                    resolutionFailure(exception.getResults()), exception);
        }
        final List<ResolvedArtifactPath> resolved =
                new ArrayList<ResolvedArtifactPath>();
        for (ArtifactResult result : results) {
            final Candidate candidate = byRequest.get(result.getRequest());
            final File file = result.getArtifact() == null
                    ? null : result.getArtifact().getFile();
            if (!result.isResolved() || candidate == null || file == null) {
                throw new MojoExecutionException(
                        "Resolver did not return a file for "
                                + result.getRequest().getArtifact());
            }
            final Path path;
            try {
                path = file.toPath().toRealPath();
            } catch (IOException exception) {
                throw new MojoExecutionException(
                        "Resolved artifact path is unavailable: " + file,
                        exception);
            }
            if (!path.isAbsolute() || !Files.isRegularFile(path)) {
                throw new MojoExecutionException(
                        "Resolved artifact is not an absolute file: " + path);
            }
            resolved.add(new ResolvedArtifactPath(
                    ArtifactCoordinates.from(result.getArtifact()),
                    path));
        }
        return resolved;
    }

    void addResolvedBinding(
            final Map<String, ResolvedArtifactPath> resolved,
            final ResolvedArtifactPath candidate)
            throws MojoFailureException {
        final String identity = candidate.getCoordinates().identity();
        final ResolvedArtifactPath previous = resolved.get(identity);
        if (previous == null) {
            resolved.put(identity, candidate);
            return;
        }
        if (!previous.getAbsolutePath().equals(
                candidate.getAbsolutePath())) {
            throw new MojoFailureException(
                    "Artifact coordinates map to multiple physical paths: "
                            + identity + "; paths=["
                            + previous.getAbsolutePath() + ", "
                            + candidate.getAbsolutePath() + "]");
        }
    }

    private String resolutionFailure(final List<ArtifactResult> results) {
        final StringBuilder message = new StringBuilder(
                "Unable to resolve selected external artifacts");
        for (ArtifactResult result : results) {
            if (!result.isResolved()) {
                message.append("; ")
                        .append(result.getRequest().getArtifact());
            }
        }
        return message.toString();
    }

    private ArtifactCoordinates moduleCoordinates() {
        return ArtifactCoordinates.from(
                toResolverArtifact(project.getArtifact()));
    }

    private static String defaultValue(
            final String value,
            final String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    /** One GraphML-selected external artifact. */
    private static final class Candidate {

        /** Artifact to resolve. */
        private final Artifact artifact;

        Candidate(final Artifact selectedArtifact) {
            artifact = selectedArtifact;
        }

        Artifact getArtifact() {
            return artifact;
        }

    }

    /** Effective MavenProject system dependency path. */
    private static final class SystemArtifact {

        /** Declared absolute system path. */
        private final String path;

        SystemArtifact(final String systemPath) {
            path = systemPath;
        }

        String getPath() {
            return path;
        }
    }
}
