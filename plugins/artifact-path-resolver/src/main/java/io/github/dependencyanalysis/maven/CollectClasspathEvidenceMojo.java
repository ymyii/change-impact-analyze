package io.github.dependencyanalysis.maven;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

// Wiki: wiki/c4/containers/dependency-analyzer-evidence-plugin.md - Goal
// - Classpath Evidence Schema v1
/** Collects ordered physical classpath entries into Schema v1 JSON. */
@Mojo(name = "collect-classpath-evidence", threadSafe = true,
        requiresDependencyResolution = ResolutionScope.TEST)
public final class CollectClasspathEvidenceMojo extends AbstractMojo {

    /** Mask used while encoding a digest byte. */
    private static final int UNSIGNED_BYTE_MASK = 0xff;

    /** Supported Maven dependency scopes. */
    private static final Set<String> ALLOWED_SCOPES =
            Collections.unmodifiableSet(new LinkedHashSet<String>(
                    Arrays.asList("compile", "runtime", "provided",
                            "test", "system")));

    /** Current Maven Module. */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** Current Maven session. */
    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    private MavenSession session;

    /** Executing Plugin descriptor. */
    @Parameter(defaultValue = "${plugin}", readonly = true, required = true)
    private PluginDescriptor pluginDescriptor;

    /** Command-owned evidence output directory. */
    @Parameter(property = "cia.classpathEvidenceDirectory", required = true)
    private String evidenceDirectory;

    /** Command owner token. */
    @Parameter(property = "cia.classpathEvidenceOwner", required = true)
    private String evidenceOwner;

    /** Included Maven dependency scopes. */
    @Parameter(property = "cia.classpathEvidenceScopes",
            defaultValue = "compile,runtime,provided,test,system")
    private String scopes;

    @Override
    public void execute()
            throws MojoExecutionException, MojoFailureException {
        final Path outputDirectory = DependencyEvidenceOutputDirectory
                .validate(evidenceDirectory, evidenceOwner,
                        Paths.get(session.getExecutionRootDirectory()),
                        "cia.classpathEvidence");
        final Set<String> includedScopes = parseScopes();
        final Map<String, MavenProject> reactor = reactorProjects();
        final List<ClasspathEvidenceModel.Entry> entries = new ArrayList<>();
        final List<String> issues = new ArrayList<>();
        final Set<String> seen = new LinkedHashSet<>();
        final ArtifactCoordinates module = ArtifactCoordinates.from(
                project.getArtifact());
        final Path moduleDirectory = canonicalDirectory(project.getBasedir());
        addProjectEntry(entries, seen, module);
        final Set<org.apache.maven.artifact.Artifact> artifacts =
                project.getArtifacts() == null
                        ? Collections.<org.apache.maven.artifact.Artifact>
                        emptySet() : project.getArtifacts();
        for (org.apache.maven.artifact.Artifact artifact : artifacts) {
            final String scope = normalizedScope(artifact.getScope());
            if (!includedScopes.contains(scope)) {
                continue;
            }
            final ArtifactCoordinates coordinates =
                    ArtifactCoordinates.from(artifact);
            final MavenProject reactorProject = reactor.get(
                    reactorKey(coordinates));
            if (reactorProject != null) {
                addReactorEntry(entries, issues, seen, coordinates,
                        scope, reactorProject);
            } else {
                addExternalEntry(entries, issues, seen, coordinates,
                        scope, artifact.getFile());
            }
        }
        final ClasspathEvidenceModel evidence = new ClasspathEvidenceModel(
                javaMajor(), module, moduleDirectory, entries, issues);
        final Path output = outputDirectory.resolve(
                "classpath-module-" + moduleHash(moduleDirectory, module)
                        + ".json");
        try {
            ClasspathEvidenceJsonWriter.write(output, evidence);
        } catch (IOException exception) {
            throw new MojoExecutionException(
                    "Unable to publish classpath evidence: " + output,
                    exception);
        }
        getLog().info("Classpath Evidence Plugin version=" + safeVersion()
                + "; entries=" + entries.size() + "; issues="
                + issues.size() + "; output=" + output);
    }

    private void addProjectEntry(
            final List<ClasspathEvidenceModel.Entry> entries,
            final Set<String> seen,
            final ArtifactCoordinates coordinates)
            throws MojoExecutionException {
        final Path output = existingDirectory(
                project.getBuild().getOutputDirectory());
        if (output != null && seen.add("PROJECT|" + coordinates.identity())) {
            entries.add(new ClasspathEvidenceModel.Entry(entries.size(),
                    "PROJECT", coordinates, "", output));
        }
    }

    private void addReactorEntry(
            final List<ClasspathEvidenceModel.Entry> entries,
            final List<String> issues,
            final Set<String> seen,
            final ArtifactCoordinates coordinates,
            final String scope,
            final MavenProject reactorProject)
            throws MojoExecutionException {
        if (!coordinates.getClassifier().isEmpty()) {
            issues.add("Unsupported compiled Reactor classifier: "
                    + coordinates.identity());
            return;
        }
        final Path output = existingDirectory(
                reactorProject.getBuild().getOutputDirectory());
        if (output == null) {
            return;
        }
        if (seen.add("REACTOR_DEPENDENCY|" + coordinates.identity())) {
            entries.add(new ClasspathEvidenceModel.Entry(entries.size(),
                    "REACTOR_DEPENDENCY", coordinates, scope, output));
        }
    }

    private void addExternalEntry(
            final List<ClasspathEvidenceModel.Entry> entries,
            final List<String> issues,
            final Set<String> seen,
            final ArtifactCoordinates coordinates,
            final String scope,
            final File file) throws MojoExecutionException {
        if (file == null) {
            issues.add("Selected external artifact has no physical file: "
                    + coordinates.identity());
            return;
        }
        final Path path = canonicalFile(file.toPath());
        if (seen.add("DEPENDENCY|" + coordinates.identity())) {
            entries.add(new ClasspathEvidenceModel.Entry(entries.size(),
                    "DEPENDENCY", coordinates, scope, path));
        }
    }

    private Set<String> parseScopes() throws MojoFailureException {
        final Set<String> result = new LinkedHashSet<>();
        Arrays.stream(scopes == null ? new String[0] : scopes.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .forEach(result::add);
        if (result.isEmpty() || !ALLOWED_SCOPES.containsAll(result)) {
            throw new MojoFailureException(
                    "Invalid cia.classpathEvidenceScopes: " + result);
        }
        return result;
    }

    private Map<String, MavenProject> reactorProjects() {
        final Map<String, MavenProject> result = new LinkedHashMap<>();
        final List<MavenProject> projects = session.getProjects() == null
                ? Collections.<MavenProject>emptyList()
                : session.getProjects();
        for (MavenProject value : projects) {
            if (value.getArtifact() != null && value != project) {
                result.put(reactorKey(ArtifactCoordinates.from(
                        value.getArtifact())), value);
            }
        }
        return result;
    }

    private String reactorKey(final ArtifactCoordinates coordinates) {
        return coordinates.getGroupId() + ":"
                + coordinates.getArtifactId() + ":"
                + coordinates.getBaseVersion();
    }

    private Path existingDirectory(final String value)
            throws MojoExecutionException {
        if (value == null || value.isEmpty()) {
            return null;
        }
        final Path path = Paths.get(value);
        if (!Files.exists(path)) {
            return null;
        }
        try {
            final Path real = path.toRealPath();
            if (!Files.isDirectory(real)) {
                throw new MojoExecutionException(
                        "Classpath output is not a directory: " + real);
            }
            return real;
        } catch (IOException exception) {
            throw new MojoExecutionException(
                    "Unable to canonicalize classpath output: " + path,
                    exception);
        }
    }

    private Path canonicalDirectory(final File value)
            throws MojoExecutionException {
        try {
            return value.toPath().toRealPath();
        } catch (IOException exception) {
            throw new MojoExecutionException(
                    "Unable to canonicalize Module directory", exception);
        }
    }

    private Path canonicalFile(final Path value)
            throws MojoExecutionException {
        try {
            final Path real = value.toRealPath();
            if (!Files.isRegularFile(real)) {
                throw new MojoExecutionException(
                        "Classpath artifact is not a regular file: " + real);
            }
            return real;
        } catch (IOException exception) {
            throw new MojoExecutionException(
                    "Unable to canonicalize classpath artifact: " + value,
                    exception);
        }
    }

    private int javaMajor() throws MojoFailureException {
        final String value = System.getProperty("java.specification.version");
        try {
            return value.startsWith("1.")
                    ? Integer.parseInt(value.substring(2))
                    : Integer.parseInt(value);
        } catch (RuntimeException exception) {
            throw new MojoFailureException(
                    "Unable to parse Maven JVM major version: " + value,
                    exception);
        }
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
                result.append(String.format("%02x",
                        value & UNSIGNED_BYTE_MASK));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new MojoExecutionException(
                    "SHA-256 is unavailable", exception);
        }
    }

    private String normalizedScope(final String value) {
        return value == null || value.isEmpty()
                ? "compile" : value.toLowerCase(Locale.ROOT);
    }

    private String safeVersion() {
        return pluginDescriptor == null || pluginDescriptor.getVersion() == null
                ? "unavailable" : pluginDescriptor.getVersion();
    }
}
