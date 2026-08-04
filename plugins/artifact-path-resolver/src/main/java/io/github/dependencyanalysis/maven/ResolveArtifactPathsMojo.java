package io.github.dependencyanalysis.maven;

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.model.Exclusion;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.execution.MavenSession;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.ArtifactProperties;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.artifact.DefaultArtifactType;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves selected external artifact paths in the current Maven session. */
@Mojo(name = "resolve-artifact-paths", threadSafe = true)
public final class ResolveArtifactPathsMojo extends AbstractMojo {

    /** Resolver conflict verbose configuration property. */
    private static final String CONFLICT_VERBOSE =
            "aether.conflictResolver.verbose";

    /** Resolver loser node marker. */
    private static final String CONFLICT_WINNER = "conflict.winner";

    /** Selected scopes exposed to Analyzer. */
    private static final Set<String> INCLUDED_SCOPES;

    static {
        final Set<String> scopes = new LinkedHashSet<String>();
        scopes.add("compile");
        scopes.add("runtime");
        scopes.add("provided");
        INCLUDED_SCOPES = Collections.unmodifiableSet(scopes);
    }

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

    /** Module-local output filename. */
    @Parameter(property = "cia.resolvedArtifactsFileName", required = true)
    private String resolvedArtifactsFileName;

    @Override
    public void execute()
            throws MojoExecutionException, MojoFailureException {
        final Path output = outputPath();
        final DefaultRepositorySystemSession collectionSession =
                new DefaultRepositorySystemSession(repositorySystemSession);
        collectionSession.setConfigProperty(CONFLICT_VERBOSE, Boolean.FALSE);

        final CollectResult collected;
        try {
            collected = repositorySystem.collectDependencies(
                    collectionSession, collectRequest());
        } catch (DependencyCollectionException exception) {
            throw new MojoExecutionException(
                    "Unable to collect mediated project dependencies",
                    exception);
        }

        final Set<String> reactorIdentities = reactorIdentities();
        final Map<String, Candidate> candidates =
                new LinkedHashMap<String, Candidate>();
        collectCandidates(collected.getRoot(), reactorIdentities, candidates);
        final List<ResolvedArtifactPath> resolved = resolve(
                collectionSession, new ArrayList<Candidate>(
                        candidates.values()));
        Collections.sort(resolved, ResolvedArtifactPath.ORDER);

        try {
            final Path baseDirectory = project.getBasedir().toPath()
                    .toRealPath();
            ArtifactPathJsonWriter.write(output, moduleCoordinates(),
                    baseDirectory, resolved);
        } catch (IOException exception) {
            throw new MojoExecutionException(
                    "Unable to publish resolved artifact JSON: " + output,
                    exception);
        }
    }

    private Path outputPath() throws MojoFailureException {
        if (resolvedArtifactsFileName == null
                || resolvedArtifactsFileName.trim().isEmpty()) {
            throw new MojoFailureException(
                    "cia.resolvedArtifactsFileName must be a filename");
        }
        final String value = resolvedArtifactsFileName.trim();
        final Path filename;
        try {
            filename = Paths.get(value);
        } catch (RuntimeException exception) {
            throw new MojoFailureException(
                    "Invalid cia.resolvedArtifactsFileName: " + value,
                    exception);
        }
        if (filename.isAbsolute()
                || filename.getNameCount() != 1
                || value.contains("/")
                || value.contains("\\")
                || value.equals(".")
                || value.equals("..")
                || value.contains("..")) {
            throw new MojoFailureException(
                    "cia.resolvedArtifactsFileName must be a filename: "
                            + value);
        }
        return project.getBasedir().toPath().toAbsolutePath()
                .normalize().resolve(filename);
    }

    private CollectRequest collectRequest() {
        final CollectRequest request = new CollectRequest();
        request.setRootArtifact(toResolverArtifact(project.getArtifact()));
        request.setRequestContext("project");
        request.setRepositories(remoteRepositories == null
                ? Collections.<RemoteRepository>emptyList()
                : remoteRepositories);
        for (org.apache.maven.model.Dependency dependency
                : project.getDependencies()) {
            request.addDependency(toResolverDependency(dependency));
        }
        if (project.getDependencyManagement() != null) {
            for (org.apache.maven.model.Dependency managed
                    : project.getDependencyManagement().getDependencies()) {
                request.addManagedDependency(toResolverDependency(managed));
            }
        }
        return request;
    }

    private Dependency toResolverDependency(
            final org.apache.maven.model.Dependency dependency) {
        final String type = defaultValue(dependency.getType(), "jar");
        ArtifactType artifactType = repositorySystemSession
                .getArtifactTypeRegistry().get(type);
        if (artifactType == null) {
            artifactType = new DefaultArtifactType(type);
        }
        final Map<String, String> properties =
                new HashMap<String, String>();
        properties.put(ArtifactProperties.TYPE, type);
        if (dependency.getSystemPath() != null) {
            properties.put(ArtifactProperties.LOCAL_PATH,
                    dependency.getSystemPath());
        }
        final String classifier = defaultValue(
                dependency.getClassifier(),
                defaultValue(artifactType.getClassifier(), ""));
        final Artifact artifact = new DefaultArtifact(
                dependency.getGroupId(),
                dependency.getArtifactId(),
                classifier,
                artifactType.getExtension(),
                dependency.getVersion(),
                properties,
                artifactType);
        final List<org.eclipse.aether.graph.Exclusion> exclusions =
                new ArrayList<org.eclipse.aether.graph.Exclusion>();
        for (Exclusion exclusion : dependency.getExclusions()) {
            exclusions.add(new org.eclipse.aether.graph.Exclusion(
                    exclusion.getGroupId(), exclusion.getArtifactId(),
                    "*", "*"));
        }
        return new Dependency(artifact,
                defaultValue(dependency.getScope(), "compile"),
                dependency.isOptional(), exclusions);
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

    private void collectCandidates(
            final DependencyNode node,
            final Set<String> reactorIdentities,
            final Map<String, Candidate> candidates) {
        if (node == null) {
            return;
        }
        if (node.getData().get(CONFLICT_WINNER) != null) {
            return;
        }
        final Dependency dependency = node.getDependency();
        if (dependency != null
                && INCLUDED_SCOPES.contains(dependency.getScope())) {
            final ArtifactCoordinates coordinates =
                    ArtifactCoordinates.from(node.getArtifact());
            if (!reactorIdentities.contains(coordinates.identity())) {
                final String key = coordinates.identity() + ":"
                        + dependency.getScope();
                if (!candidates.containsKey(key)) {
                    candidates.put(key, new Candidate(
                            node, dependency.getScope()));
                }
            }
        }
        for (DependencyNode child : node.getChildren()) {
            collectCandidates(child, reactorIdentities, candidates);
        }
    }

    private List<ResolvedArtifactPath> resolve(
            final RepositorySystemSession resolverSession,
            final List<Candidate> candidates)
            throws MojoExecutionException {
        if (candidates.isEmpty()) {
            return new ArrayList<ResolvedArtifactPath>();
        }
        final List<ArtifactRequest> requests =
                new ArrayList<ArtifactRequest>();
        final Map<ArtifactRequest, Candidate> byRequest =
                new LinkedHashMap<ArtifactRequest, Candidate>();
        for (Candidate candidate : candidates) {
            final ArtifactRequest request =
                    new ArtifactRequest(candidate.getNode());
            requests.add(request);
            byRequest.put(request, candidate);
        }
        final List<ArtifactResult> results;
        try {
            results = repositorySystem.resolveArtifacts(
                    resolverSession, requests);
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
                    candidate.getScope(), path));
        }
        return resolved;
    }

    private String resolutionFailure(
            final List<ArtifactResult> results) {
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

    /** Selected graph node and effective scope. */
    private static final class Candidate {

        /** Mediated dependency node. */
        private final DependencyNode node;

        /** Effective selected scope. */
        private final String scope;

        Candidate(final DependencyNode dependencyNode,
                  final String dependencyScope) {
            node = dependencyNode;
            scope = dependencyScope;
        }

        DependencyNode getNode() {
            return node;
        }

        String getScope() {
            return scope;
        }
    }
}
