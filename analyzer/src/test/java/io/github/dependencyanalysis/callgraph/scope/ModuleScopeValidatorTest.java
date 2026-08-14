package io.github.dependencyanalysis.callgraph.scope;

import io.github.dependencyanalysis.impact.ModuleCallGraphInputAdapter;


import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.ResolvedArtifact;
import io.github.dependencyanalysis.impact.ModuleAnalysisUnit;
import io.github.dependencyanalysis.impact.ModuleChangeSet;
import io.github.dependencyanalysis.impact.ModuleId;
import io.github.dependencyanalysis.impact.ModulePresence;
import io.github.dependencyanalysis.testing.TestJarRepositories;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Tests source-aware excluded JDK reference validation. */
class ModuleScopeValidatorTest {

    /** External finding count used to verify bounded examples. */
    private static final int FINDING_COUNT = 7;

    /** Temporary class roots and JARs. */
    @TempDir
    private Path temporary;

    @Test
    void aggregatesExternalDependencyReferencesAsWarning()
            throws Exception {
        final Path project = directory("project");
        final Map<String, byte[]> entries = new LinkedHashMap<>();
        for (int index = FINDING_COUNT - 1; index >= 0; index--) {
            final String name = "sample/Ref" + index;
            final String parent = index % 2 == 0
                    ? "java/applet/Applet" : "javax/swing/JFrame";
            entries.put(name + ".class", classBytes(name, parent));
        }
        final ResolvedArtifact artifact = artifact(
                "legacy.jar", jar("legacy.jar", entries));

        final ScopeValidationResult result =
                new ModuleScopeValidator(TestJarRepositories.of(
                        List.of(artifact))).validate(
                        new ModuleCallGraphInputAdapter().adapt(
                                unit(project, List.of(), List.of(artifact))));

        assertThat(result.hasWarnings()).isTrue();
        assertThat(result.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning.artifact()).isEqualTo(
                    artifact.getArtifact());
            assertThat(warning.findingCount()).isEqualTo(FINDING_COUNT);
            assertThat(warning.excludedTypeCount()).isEqualTo(2);
            assertThat(warning.examples()).containsExactly(
                    "sample/Ref0.class -> java/applet/Applet",
                    "sample/Ref1.class -> javax/swing/JFrame",
                    "sample/Ref2.class -> java/applet/Applet",
                    "sample/Ref3.class -> javax/swing/JFrame",
                    "sample/Ref4.class -> java/applet/Applet");
            assertThat(warning.omittedCount()).isEqualTo(2);
            assertThat(warning.summary())
                    .contains("artifact=example:legacy:jar:1")
                    .contains("findings=7")
                    .contains("excludedTypes=2")
                    .contains("omitted=2");
        });
        assertThat(result.limitations()).singleElement()
                .asString().contains("Call Graph scope");
    }

    @Test
    void projectReferenceRemainsBlocking() throws Exception {
        final Path project = directory("project-strict");
        writeClass(project, "sample/Project",
                "java/applet/Applet");

        assertThatThrownBy(() -> new ModuleScopeValidator(
                TestJarRepositories.empty()).validate(
                new ModuleCallGraphInputAdapter().adapt(
                        unit(project, List.of(), List.of()))))
                .isInstanceOf(ScopeValidationException.class)
                .hasMessageContaining("java/applet/Applet")
                .hasMessageContaining("Project.class");
    }

    @Test
    void reactorDependencyReferenceRemainsBlocking() throws Exception {
        final Path project = directory("project-clean");
        final Path reactor = directory("reactor-strict");
        writeClass(reactor, "sample/Reactor",
                "javax/swing/JFrame");

        assertThatThrownBy(() -> new ModuleScopeValidator(
                TestJarRepositories.empty()).validate(
                new ModuleCallGraphInputAdapter().adapt(
                        unit(project, List.of(reactor), List.of()))))
                .isInstanceOf(ScopeValidationException.class)
                .hasMessageContaining("javax/swing/JFrame")
                .hasMessageContaining("Reactor.class");
    }

    @Test
    void cleanScopeReturnsNoWarnings() throws Exception {
        final Path project = directory("clean-project");
        writeClass(project, "sample/Clean", "java/lang/Object");
        final ResolvedArtifact artifact = artifact("clean.jar",
                jar("clean.jar", Map.of("sample/Dependency.class",
                        classBytes("sample/Dependency",
                                "java/lang/Object"))));

        final ScopeValidationResult result =
                new ModuleScopeValidator(TestJarRepositories.of(
                        List.of(artifact))).validate(
                        new ModuleCallGraphInputAdapter().adapt(
                                unit(project, List.of(), List.of(artifact))));

        assertThat(result.hasWarnings()).isFalse();
        assertThat(result.warnings()).isEmpty();
        assertThat(result.limitations()).isEmpty();
    }

    @Test
    void unreadableExternalJarRemainsBlocking() throws Exception {
        final Path project = directory("corrupt-project");
        final Path corrupt = temporary.resolve("corrupt.jar");
        Files.writeString(corrupt, "not a jar");

        final ResolvedArtifact artifact = artifact("corrupt.jar", corrupt);
        assertThatThrownBy(() -> TestJarRepositories.of(
                List.of(artifact)))
                .isInstanceOf(java.io.IOException.class);
    }

    private ModuleAnalysisUnit unit(
            final Path project,
            final List<Path> reactors,
            final List<ResolvedArtifact> artifacts) {
        return new ModuleAnalysisUnit(
                new ModuleId(new ArtifactCoord(
                        "example", "app", "jar", "1"), Path.of("app")),
                ModulePresence.BOTH, project, reactors, artifacts.stream()
                .map(ResolvedArtifact::getArtifact).toList(),
                List.of(), new ModuleChangeSet(List.of(), List.of()));
    }

    private ResolvedArtifact artifact(
            final String name, final Path path) {
        final String artifactId = name.substring(0, name.length() - 4);
        return new ResolvedArtifact(new ArtifactCoord(
                "example", artifactId, "jar", "1"), path);
    }

    private Path directory(final String name) throws Exception {
        return Files.createDirectories(temporary.resolve(name));
    }

    private void writeClass(
            final Path root,
            final String name,
            final String parent) throws Exception {
        final Path path = root.resolve(name + ".class");
        Files.createDirectories(path.getParent());
        Files.write(path, classBytes(name, parent));
    }

    private byte[] classBytes(
            final String name, final String parent) {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC,
                name, null, parent, null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private Path jar(
            final String name,
            final Map<String, byte[]> entries) throws Exception {
        final Path path = temporary.resolve(name);
        try (JarOutputStream output = new JarOutputStream(
                Files.newOutputStream(path))) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return path;
    }
}
