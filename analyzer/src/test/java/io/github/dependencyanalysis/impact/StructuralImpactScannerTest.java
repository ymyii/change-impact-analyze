package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.bytecode.ChangePoint;
import io.github.dependencyanalysis.bytecode.ChangePointKind;
import io.github.dependencyanalysis.classpath.ClassOwnershipIndex;
import io.github.dependencyanalysis.classpath.CodeOrigin;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.dependency.DependencyScope;
import io.github.dependencyanalysis.testing.TestJarRepositories;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests class metadata evidence extraction. */
class StructuralImpactScannerTest {

    /** Temporary class roots. */
    @TempDir
    private Path temporary;

    @Test
    void detectsStructuralMetadataKinds() throws Exception {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC,
                "app/Consumer",
                "<T:Ldep/RemovedGeneric;>Ldep/RemovedSuper;",
                "dep/RemovedSuper",
                new String[]{"dep/RemovedInterface"});
        final org.objectweb.asm.AnnotationVisitor annotation =
                writer.visitAnnotation(
                        "Ldep/RemovedAnnotation;", true);
        annotation.visit("type",
                Type.getObjectType("dep/RemovedAnnotationValue"));
        annotation.visitEnd();
        writer.visitField(Opcodes.ACC_PRIVATE, "field",
                "Ldep/RemovedField;",
                "Ldep/RemovedField;", null).visitEnd();
        writer.visitMethod(Opcodes.ACC_PUBLIC, "method",
                "(Ldep/RemovedParameter;)V", null,
                new String[]{"dep/RemovedException"}).visitEnd();
        writer.visitEnd();
        final Set<String> targets = Set.of(
                "dep/RemovedGeneric", "dep/RemovedSuper",
                "dep/RemovedInterface", "dep/RemovedAnnotation",
                "dep/RemovedAnnotationValue",
                "dep/RemovedField", "dep/RemovedParameter",
                "dep/RemovedException");

        final Map<String, Set<String>> evidence =
                new StructuralImpactScanner(
                        TestJarRepositories.empty()).metadataEvidence(
                        writer.toByteArray(), targets);

        assertThat(evidence.get("dep/RemovedSuper"))
                .contains("SUPERCLASS", "CLASS_SIGNATURE");
        assertThat(evidence.get("dep/RemovedInterface"))
                .contains("INTERFACE");
        assertThat(evidence.get("dep/RemovedAnnotation"))
                .contains("CLASS_ANNOTATION");
        assertThat(evidence.get("dep/RemovedAnnotationValue"))
                .contains("CLASS_ANNOTATION_VALUE");
        assertThat(evidence.get("dep/RemovedGeneric"))
                .contains("CLASS_SIGNATURE");
        assertThat(evidence.get("dep/RemovedField"))
                .contains("FIELD_DESCRIPTOR:field:Ldep/RemovedField;",
                        "FIELD_SIGNATURE:field:Ldep/RemovedField;");
        assertThat(evidence.get("dep/RemovedParameter"))
                .contains("METHOD_DESCRIPTOR:method(Ldep/"
                        + "RemovedParameter;)V");
        assertThat(evidence.get("dep/RemovedException"))
                .contains("THROWS:method(Ldep/RemovedParameter;)V");
    }

    @Test
    void skipsStructuralEvidenceFromDuplicateLoser() throws Exception {
        final Path project = temporary.resolve("project");
        final Path winner = temporary.resolve("winner");
        final Path loser = temporary.resolve("loser");
        Files.createDirectories(project);
        write(winner, classBytes("sample/Consumer", "java/lang/Object"));
        write(loser, classBytes("sample/Consumer", "dep/Removed"));
        final ClassOwnershipIndex ownership = new ClassOwnershipIndex();
        ownership.addDirectory(project, CodeOrigin.PROJECT);
        ownership.addDirectory(winner, CodeOrigin.REACTOR_DEPENDENCY);
        ownership.addDirectory(loser, CodeOrigin.REACTOR_DEPENDENCY);
        final ModuleId moduleId = new ModuleId(new ArtifactCoord(
                "example", "app", "jar", "1"), Path.of("app"));
        final ArtifactCoord oldArtifact = new ArtifactCoord(
                "example", "dependency", "jar", "1");
        final ArtifactCoord newArtifact = new ArtifactCoord(
                "example", "dependency", "jar", "2");
        final BoundChangePoint point = new BoundChangePoint(
                new DependencyUpgradeKey(moduleId, DependencyScope.COMPILE,
                        oldArtifact, newArtifact),
                new ChangePoint(newArtifact, ChangePointKind.CLASS_REMOVED,
                        "dep/Removed", null, null, null, null));
        final ModuleAnalysisUnit unit = new ModuleAnalysisUnit(
                moduleId, ModulePresence.BOTH, project,
                List.of(winner, loser), List.of(), List.of(),
                new ModuleChangeSet(List.of(point), List.of()));

        assertThat(new StructuralImpactScanner(
                TestJarRepositories.empty()).scan(unit, ownership)
                .references()).isEmpty();
    }

    private byte[] classBytes(
            final String name,
            final String superclass) {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC,
                name, null, superclass, null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private void write(final Path root, final byte[] content)
            throws Exception {
        final Path file = root.resolve("sample/Consumer.class");
        Files.createDirectories(file.getParent());
        Files.write(file, content);
    }
}
