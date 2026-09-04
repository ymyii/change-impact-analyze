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
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilder;
import org.apache.maven.shared.dependency.graph.DependencyCollectorBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilderException;
import org.apache.maven.shared.dependency.graph.DependencyNode;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Wiki: wiki/c4/containers/dependency-analyzer-evidence-plugin.md - Goal
/** Collects selected and raw Maven dependency graphs into Schema v3 JSON. */
@Mojo(name = "collect-dependency-evidence", threadSafe = true)
public final class CollectDependencyEvidenceMojo extends AbstractMojo {

    /** Resolver request context. */
    private static final String REQUEST_CONTEXT = "project";

    /** System dependency scope. */
    private static final String SYSTEM_SCOPE = "system";

    /** Plugin implementation marker. */
    private static final String IMPLEMENTATION_MARKER =
            "dependency-evidence-v3";

    /** Unsigned byte mask. */
    private static final int UNSIGNED_BYTE_MASK = 0xff;

    /** Current Maven project. */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** Current Maven session. */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /** Current repository session. */
    @Parameter(defaultValue = "${repositorySystemSession}",
            readonly = true, required = true)
    private RepositorySystemSession repositorySystemSession;

    /** Effective project repositories. */
    @Parameter(defaultValue = "${project.remoteProjectRepositories}",
            readonly = true, required = true)
    private List<RemoteRepository> remoteRepositories;

    /** Running Plugin descriptor. */
    @Parameter(defaultValue = "${plugin}", readonly = true, required = true)
    private PluginDescriptor pluginDescriptor;

    /** Absolute command-owned evidence directory. */
    @Parameter(property = "cia.dependencyEvidenceDirectory", required = true)
    private String evidenceDirectory;

    /** Random command owner token. */
    @Parameter(property = "cia.dependencyEvidenceOwner", required = true)
    private String evidenceOwner;

    /** Resolved graph API. */
    @Component
    private DependencyGraphBuilder dependencyGraphBuilder;

    /** Raw occurrence graph API. */
    @Component
    private DependencyCollectorBuilder dependencyCollectorBuilder;

    /** Maven Resolver entrypoint. */
    @Component
    private RepositorySystem repositorySystem;

    @Override
    public void execute()
            throws MojoExecutionException, MojoFailureException {
        final Path outputDirectory = validateOutputDirectory();
        final ProjectBuildingRequest request =
                new DefaultProjectBuildingRequest(
                        session.getProjectBuildingRequest());
        request.setProject(project);
        final DependencyNode selectedRoot;
        final DependencyNode rawRoot;
        try {
            selectedRoot = dependencyGraphBuilder.buildDependencyGraph(
                    request, null);
            rawRoot = dependencyCollectorBuilder.collectDependencyGraph(
                    request, null);
        } catch (DependencyGraphBuilderException
                 | DependencyCollectorBuilderException exception) {
            throw new MojoExecutionException(
                    "Unable to collect Maven dependency evidence",
                    exception);
        }
        final Set<String> reactorIdentities = reactorIdentities();
        final SelectedState selected = selectedState(
                selectedRoot, reactorIdentities);
        final DependencyEvidenceModel.OccurrenceGraph occurrences =
                normalizeOccurrences(rawRoot, selected);
        final List<ResolvedArtifactPath> artifacts = resolveArtifacts(
                selected.externalArtifacts);
        validateBindings(selected.externalArtifacts, artifacts);
        final Path moduleDirectory;
        try {
            moduleDirectory = project.getBasedir().toPath().toRealPath();
        } catch (IOException exception) {
            throw new MojoExecutionException(
                    "Unable to canonicalize Module directory", exception);
        }
        final ArtifactCoordinates module = ArtifactCoordinates.from(
                project.getArtifact());
        final DependencyEvidenceModel evidence = new DependencyEvidenceModel(
                module, moduleDirectory, selected.dependencies, occurrences,
                selected.reactorKeys, artifacts);
        final Path output = outputDirectory.resolve(
                "module-" + moduleHash(moduleDirectory, module) + ".json");
        try {
            DependencyEvidenceJsonWriter.write(output, evidence);
        } catch (IOException exception) {
            throw new MojoExecutionException(
                    "Unable to publish dependency evidence: " + output,
                    exception);
        }
        getLog().info("Dependency Evidence Plugin implementation="
                + IMPLEMENTATION_MARKER + "; version="
                + safeVersion() + "; output=" + output);
    }

    SelectedState selectedState(
            final DependencyNode root,
            final Set<String> reactorIdentities)
            throws MojoFailureException {
        if (root == null || root.getArtifact() == null) {
            throw new MojoFailureException(
                    "Resolved dependency graph has no Module root");
        }
        final SelectedState state = new SelectedState();
        for (DependencyNode child : root.getChildren()) {
            state.dependencies.addAll(selectedNodes(
                    child, reactorIdentities, state));
        }
        return state;
    }

    private List<DependencyEvidenceModel.SelectedDependency> selectedNodes(
            final DependencyNode node,
            final Set<String> reactorIdentities,
            final SelectedState state) throws MojoFailureException {
        if (node == null || node.getArtifact() == null) {
            return Collections.emptyList();
        }
        final ArtifactCoordinates coordinates = ArtifactCoordinates.from(
                node.getArtifact());
        final String scope = scope(node.getArtifact());
        if (!retainedScope(scope)) {
            return Collections.emptyList();
        }
        final boolean reactor = reactorIdentities.contains(
                coordinates.identity());
        final Winner winner = new Winner(coordinates);
        final Winner previous = state.winners.put(
                coordinates.analysisKey(), winner);
        if (previous != null
                && !previous.coordinates.identity().equals(
                coordinates.identity())) {
            throw new MojoFailureException(
                    "Resolved dependency winner is not unique: key="
                            + coordinates.analysisKey());
        }
        final List<DependencyEvidenceModel.SelectedDependency> children =
                new ArrayList<DependencyEvidenceModel.SelectedDependency>();
        for (DependencyNode child : node.getChildren()) {
            children.addAll(selectedNodes(child, reactorIdentities, state));
        }
        if (reactor) {
            if (state.reactorKeySet.add(coordinates.analysisKey())) {
                state.reactorKeys.add(coordinates.analysisKey());
            }
            return children;
        }
        state.externalArtifacts.put(
                coordinates.analysisKey(),
                new SelectedArtifact(node.getArtifact(), scope,
                        coordinates));
        return Collections.singletonList(
                new DependencyEvidenceModel.SelectedDependency(
                        coordinates, scope, children));
    }

    DependencyEvidenceModel.OccurrenceGraph normalizeOccurrences(
            final DependencyNode root,
            final SelectedState selected) throws MojoFailureException {
        if (root == null || root.getArtifact() == null) {
            throw new MojoFailureException(
                    "Raw dependency graph has no Module root");
        }
        final List<DependencyEvidenceModel.Occurrence> occurrences =
                new ArrayList<DependencyEvidenceModel.Occurrence>();
        final List<DependencyEvidenceModel.Edge> edges =
                new ArrayList<DependencyEvidenceModel.Edge>();
        occurrences.add(new DependencyEvidenceModel.Occurrence(
                "root", ArtifactCoordinates.from(project.getArtifact()),
                "", true, false));
        final Set<String> matched = new LinkedHashSet<String>();
        final int[] sequence = new int[]{0};
        for (DependencyNode child : root.getChildren()) {
            appendOccurrence(child, "root", selected, matched,
                    occurrences, edges, sequence);
        }
        if (!matched.containsAll(selected.winners.keySet())) {
            final Set<String> missing = new LinkedHashSet<String>(
                    selected.winners.keySet());
            missing.removeAll(matched);
            throw new MojoFailureException(
                    "Raw dependency graph is missing selected winners: "
                            + missing);
        }
        return new DependencyEvidenceModel.OccurrenceGraph(
                "root", occurrences, edges);
    }

    private void appendOccurrence(
            final DependencyNode node,
            final String parentId,
            final SelectedState selected,
            final Set<String> matched,
            final List<DependencyEvidenceModel.Occurrence> occurrences,
            final List<DependencyEvidenceModel.Edge> edges,
            final int[] sequence) {
        if (node == null || node.getArtifact() == null) {
            return;
        }
        final ArtifactCoordinates raw = ArtifactCoordinates.from(
                node.getArtifact());
        final Winner winner = selected.winners.get(raw.analysisKey());
        final String rawScope = scope(node.getArtifact());
        final boolean selectedReactor = selected.reactorKeySet.contains(
                raw.analysisKey());
        if (!retainedScope(rawScope)) {
            return;
        }
        if (winner == null) {
            return;
        }
        final String id = "occurrence-" + sequence[0]++;
        occurrences.add(new DependencyEvidenceModel.Occurrence(
                id, winner.coordinates, rawScope, false,
                selectedReactor));
        edges.add(new DependencyEvidenceModel.Edge(parentId, id));
        matched.add(raw.analysisKey());
        for (DependencyNode child : node.getChildren()) {
            appendOccurrence(child, id, selected, matched,
                    occurrences, edges, sequence);
        }
    }

    private List<ResolvedArtifactPath> resolveArtifacts(
            final Map<String, SelectedArtifact> selected)
            throws MojoExecutionException, MojoFailureException {
        final Map<String, SystemArtifact> systems = systemArtifacts();
        final List<ArtifactRequest> requests = new ArrayList<ArtifactRequest>();
        final Map<ArtifactRequest, SelectedArtifact> byRequest =
                new LinkedHashMap<ArtifactRequest, SelectedArtifact>();
        final List<ResolvedArtifactPath> result =
                new ArrayList<ResolvedArtifactPath>();
        final List<RemoteRepository> repositories = remoteRepositories == null
                ? Collections.<RemoteRepository>emptyList()
                : remoteRepositories;
        for (SelectedArtifact dependency : selected.values()) {
            if (SYSTEM_SCOPE.equals(dependency.scope)) {
                result.add(resolveSystemArtifact(
                        systems, dependency.coordinates,
                        toResolverArtifact(dependency.artifact)));
            } else {
                final ArtifactRequest request = new ArtifactRequest(
                        toResolverArtifact(dependency.artifact),
                        repositories, REQUEST_CONTEXT);
                requests.add(request);
                byRequest.put(request, dependency);
            }
        }
        if (!requests.isEmpty()) {
            final List<ArtifactResult> resolved;
            try {
                resolved = repositorySystem.resolveArtifacts(
                        repositorySystemSession, requests);
            } catch (ArtifactResolutionException exception) {
                throw new MojoExecutionException(
                        resolutionFailure(exception.getResults()),
                        exception);
            }
            for (ArtifactResult artifactResult : resolved) {
                final SelectedArtifact dependency = byRequest.get(
                        artifactResult.getRequest());
                final File file = artifactResult.getArtifact() == null
                        ? null : artifactResult.getArtifact().getFile();
                if (!artifactResult.isResolved()
                        || dependency == null || file == null) {
                    throw new MojoExecutionException(
                            "Resolver did not return a selected file for "
                                    + artifactResult.getRequest()
                                    .getArtifact());
                }
                result.add(new ResolvedArtifactPath(
                        ArtifactCoordinates.from(
                                artifactResult.getArtifact()),
                        canonicalFile(file.toPath())));
            }
        }
        Collections.sort(result, ResolvedArtifactPath.ORDER);
        return result;
    }

    private Map<String, SystemArtifact> systemArtifacts()
            throws MojoFailureException {
        final Map<String, SystemArtifact> result =
                new LinkedHashMap<String, SystemArtifact>();
        for (org.apache.maven.model.Dependency dependency
                : project.getDependencies()) {
            if (!SYSTEM_SCOPE.equals(dependency.getScope())) {
                continue;
            }
            final Artifact artifact = toResolverArtifact(
                    dependency.getGroupId(), dependency.getArtifactId(),
                    dependency.getType(), dependency.getClassifier(),
                    dependency.getVersion());
            final ArtifactCoordinates coordinates =
                    ArtifactCoordinates.from(artifact);
            if (dependency.getSystemPath() == null
                    || dependency.getSystemPath().trim().isEmpty()) {
                throw new MojoFailureException(
                        "System dependency has no systemPath: "
                                + coordinates.identity());
            }
            if (result.put(coordinates.analysisKey(),
                    new SystemArtifact(dependency.getSystemPath())) != null) {
                throw new MojoFailureException(
                        "Duplicate effective system dependency: "
                                + coordinates.analysisKey());
            }
        }
        return result;
    }

    private ResolvedArtifactPath resolveSystemArtifact(
            final Map<String, SystemArtifact> systems,
            final ArtifactCoordinates selected,
            final Artifact artifact) throws MojoFailureException {
        final SystemArtifact system = systems.get(selected.analysisKey());
        if (system == null) {
            throw new MojoFailureException(
                    "Selected system dependency has no matching effective "
                            + "MavenProject dependency: "
                            + selected.analysisKey());
        }
        final Path declared;
        try {
            declared = Paths.get(system.path);
        } catch (RuntimeException exception) {
            throw new MojoFailureException(
                    "Invalid system dependency path: " + system.path,
                    exception);
        }
        if (!declared.isAbsolute()) {
            throw new MojoFailureException(
                    "System dependency path must be absolute: " + declared);
        }
        try {
            return new ResolvedArtifactPath(
                    ArtifactCoordinates.from(artifact),
                    canonicalFile(declared));
        } catch (MojoExecutionException exception) {
            throw new MojoFailureException(exception.getMessage(), exception);
        }
    }

    private void validateBindings(
            final Map<String, SelectedArtifact> selected,
            final List<ResolvedArtifactPath> artifacts)
            throws MojoFailureException {
        final Set<String> expected = new LinkedHashSet<String>(
                selected.keySet());
        final Set<String> actual = new LinkedHashSet<String>();
        for (ResolvedArtifactPath artifact : artifacts) {
            if (!actual.add(artifact.getCoordinates().analysisKey())) {
                throw new MojoFailureException(
                        "Duplicate selected artifact binding: "
                                + artifact.getCoordinates().analysisKey());
            }
        }
        if (!expected.equals(actual)) {
            final Set<String> missing = new LinkedHashSet<String>(expected);
            missing.removeAll(actual);
            final Set<String> unexpected = new LinkedHashSet<String>(actual);
            unexpected.removeAll(expected);
            throw new MojoFailureException(
                    "Selected dependency bindings differ: missing="
                            + missing + "; unexpected=" + unexpected);
        }
    }

    private Path validateOutputDirectory() throws MojoFailureException {
        return DependencyEvidenceOutputDirectory.validate(
                evidenceDirectory, evidenceOwner,
                Paths.get(session.getExecutionRootDirectory()));
    }

    private Set<String> reactorIdentities() {
        final Set<String> result = new LinkedHashSet<String>();
        final List<MavenProject> projects = session.getProjects() == null
                ? Collections.<MavenProject>emptyList()
                : session.getProjects();
        for (MavenProject reactorProject : projects) {
            if (reactorProject.getArtifact() != null) {
                result.add(ArtifactCoordinates.from(
                        reactorProject.getArtifact()).identity());
            }
        }
        return result;
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
        final Map<String, String> properties = new HashMap<String, String>();
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
        final String classifier = defaultValue(
                classifierValue,
                defaultValue(artifactType.getClassifier(), ""));
        final Map<String, String> properties = new HashMap<String, String>();
        properties.put(ArtifactProperties.TYPE, type);
        return new DefaultArtifact(groupId, artifactId, classifier,
                artifactType.getExtension(), version, properties,
                artifactType);
    }

    private Path canonicalFile(final Path path)
            throws MojoExecutionException {
        try {
            final Path real = path.toRealPath();
            if (!Files.isRegularFile(real)) {
                throw new MojoExecutionException(
                        "Resolved artifact is not a regular file: " + real);
            }
            return real;
        } catch (IOException exception) {
            throw new MojoExecutionException(
                    "Resolved artifact path is unavailable: " + path,
                    exception);
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

    private String moduleHash(
            final Path moduleDirectory,
            final ArtifactCoordinates module)
            throws MojoExecutionException {
        try {
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(moduleDirectory.toString().getBytes(
                    StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(module.identity().getBytes(StandardCharsets.UTF_8));
            final StringBuilder result = new StringBuilder();
            for (byte value : digest.digest()) {
                result.append(String.format(
                        "%02x", value & UNSIGNED_BYTE_MASK));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new MojoExecutionException(
                    "SHA-256 is unavailable", exception);
        }
    }

    private String safeVersion() {
        return pluginDescriptor == null || pluginDescriptor.getVersion() == null
                ? "unavailable" : pluginDescriptor.getVersion();
    }

    private static String scope(
            final org.apache.maven.artifact.Artifact artifact) {
        return defaultValue(artifact.getScope(), "compile");
    }

    private static boolean retainedScope(final String scope) {
        return "compile".equals(scope) || "runtime".equals(scope)
                || "provided".equals(scope) || SYSTEM_SCOPE.equals(scope);
    }

    private static String defaultValue(
            final String value,
            final String fallback) {
        return value == null || value.isEmpty() ? fallback : value;
    }

    /** Selected graph accumulation, package-visible for contract tests. */
    static final class SelectedState {

        /** Winner by version-insensitive analysis key. */
        private final Map<String, Winner> winners =
                new LinkedHashMap<String, Winner>();

        /** Selected external dependency tree. */
        private final List<DependencyEvidenceModel.SelectedDependency>
                dependencies =
                new ArrayList<DependencyEvidenceModel.SelectedDependency>();

        /** Selected external artifacts. */
        private final Map<String, SelectedArtifact> externalArtifacts =
                new LinkedHashMap<String, SelectedArtifact>();

        /** Selected reactor keys in traversal order. */
        private final List<String> reactorKeys = new ArrayList<String>();

        /** Reactor key membership. */
        private final Set<String> reactorKeySet =
                new LinkedHashSet<String>();
    }

    /** One retained Maven winner. */
    private static final class Winner {

        /** Winner coordinates. */
        private final ArtifactCoordinates coordinates;

        Winner(final ArtifactCoordinates selected) {
            coordinates = selected;
        }
    }

    /** External selected artifact resolution input. */
    private static final class SelectedArtifact {

        /** Maven artifact. */
        private final org.apache.maven.artifact.Artifact artifact;

        /** Retained scope. */
        private final String scope;

        /** Selected coordinates. */
        private final ArtifactCoordinates coordinates;

        SelectedArtifact(
                final org.apache.maven.artifact.Artifact selected,
                final String selectedScope,
                final ArtifactCoordinates selectedCoordinates) {
            artifact = selected;
            scope = selectedScope;
            coordinates = selectedCoordinates;
        }
    }

    /** Effective system dependency path. */
    private static final class SystemArtifact {

        /** Declared path. */
        private final String path;

        SystemArtifact(final String systemPath) {
            path = systemPath;
        }
    }
}
