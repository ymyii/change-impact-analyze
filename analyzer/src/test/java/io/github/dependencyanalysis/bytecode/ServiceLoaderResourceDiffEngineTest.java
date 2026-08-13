package io.github.dependencyanalysis.bytecode;

import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.impact.DependencyUpgradeKey;
import io.github.dependencyanalysis.impact.ModuleId;
import io.github.dependencyanalysis.jar.IJarRepository;
import io.github.dependencyanalysis.testing.TestJarRepositories;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests stable ServiceLoader resource Diff and class-removal deduplication. */
class ServiceLoaderResourceDiffEngineTest {

    /** Temporary JAR directory. */
    @TempDir
    private Path temporary;

    @Test
    void registrationOnlyRemovalCreatesResourceChangePoint()
            throws Exception {
        final ArtifactCoord oldArtifact = artifact("1");
        final ArtifactCoord newArtifact = artifact("2");
        final Map<String, byte[]> classes = Map.of(
                "sample/Service.class", type("sample/Service", null, true),
                "sample/Provider.class", type("sample/Provider",
                        "sample/Service", false));
        final Path oldJar = jar("old.jar", classes,
                "# comment\nsample.Provider\n\nsample.Provider\n");
        final Path newJar = jar("new.jar", classes, "");

        try (IJarRepository repository = TestJarRepositories.pair(
                oldArtifact, oldJar, newArtifact, newJar)) {
            final ServiceLoaderResourceDiffResult result = engine().diff(
                    upgrade(oldArtifact, newArtifact), repository, List.of());

            assertThat(result.baselineRegistrations()).hasSize(1);
            assertThat(result.removedRegistrations()).hasSize(1);
            assertThat(result.changePoints()).singleElement().satisfies(
                    point -> assertThat(point.getKind()).isEqualTo(
                            ChangePointKind
                                    .SERVICE_PROVIDER_REGISTRATION_REMOVED));
            assertThat(result.issues()).isEmpty();
        }
    }

    @Test
    void providerClassRemovalRetainsRelationWithoutResourceChangePoint()
            throws Exception {
        final ArtifactCoord oldArtifact = artifact("1");
        final ArtifactCoord newArtifact = artifact("2");
        final Path oldJar = jar("old-class.jar", Map.of(
                "sample/Service.class", type("sample/Service", null, true),
                "sample/Provider.class", type("sample/Provider",
                        "sample/Service", false)), "sample.Provider\n");
        final Path newJar = jar("new-class.jar", Map.of(
                "sample/Service.class", type("sample/Service", null, true)),
                "");
        final ChangePoint removedClass = new ChangePoint(newArtifact,
                ChangePointKind.CLASS_REMOVED, "sample/Provider",
                null, null, null, null);

        try (IJarRepository repository = TestJarRepositories.pair(
                oldArtifact, oldJar, newArtifact, newJar)) {
            final ServiceLoaderResourceDiffResult result = engine().diff(
                    upgrade(oldArtifact, newArtifact), repository,
                    List.of(removedClass));

            assertThat(result.removedRegistrations()).hasSize(1);
            assertThat(result.changePoints()).isEmpty();
        }
    }

    private ServiceLoaderResourceDiffEngine engine() {
        return new ServiceLoaderResourceDiffEngine(Set.of(
                ChangePointKind.SERVICE_PROVIDER_REGISTRATION_REMOVED));
    }

    private DependencyUpgradeKey upgrade(
            final ArtifactCoord oldArtifact,
            final ArtifactCoord newArtifact) {
        return new DependencyUpgradeKey(new ModuleId(
                new ArtifactCoord("test", "app", "jar", "1"), Path.of(".")),
                DependencyScope.COMPILE, oldArtifact, newArtifact);
    }

    private ArtifactCoord artifact(final String version) {
        return new ArtifactCoord("test", "services", "jar", version);
    }

    private Path jar(
            final String name,
            final Map<String, byte[]> classes,
            final String serviceResource) throws Exception {
        final Path result = temporary.resolve(name);
        try (JarOutputStream output = new JarOutputStream(
                Files.newOutputStream(result))) {
            for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
            output.putNextEntry(new JarEntry(
                    "META-INF/services/sample.Service"));
            output.write(serviceResource.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return result;
    }

    private byte[] type(
            final String name,
            final String parent,
            final boolean service) {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC
                        | (service ? Opcodes.ACC_INTERFACE
                        | Opcodes.ACC_ABSTRACT : 0), name, null,
                parent == null ? "java/lang/Object" : parent, null);
        if (!service) {
            final MethodVisitor constructor = writer.visitMethod(
                    Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
            constructor.visitCode();
            constructor.visitVarInsn(Opcodes.ALOAD, 0);
            constructor.visitMethodInsn(Opcodes.INVOKESPECIAL,
                    "java/lang/Object", "<init>", "()V", false);
            constructor.visitInsn(Opcodes.RETURN);
            constructor.visitMaxs(1, 1);
            constructor.visitEnd();
        }
        writer.visitEnd();
        return writer.toByteArray();
    }
}
