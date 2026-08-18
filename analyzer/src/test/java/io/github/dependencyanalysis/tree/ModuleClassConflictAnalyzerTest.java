package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.classpath.ClassConflictRisk;
import io.github.dependencyanalysis.classpath.CodeOrigin;
import io.github.dependencyanalysis.dependency.ArtifactCoord;
import io.github.dependencyanalysis.diagnostic.DiagnosticLog;
import io.github.dependencyanalysis.diagnostic.LogVerbosity;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Module effective-classpath conflict analysis tests. */
class ModuleClassConflictAnalyzerTest {

    /** Temporary classpath. */
    @TempDir
    private Path temporary;

    @Test
    void selectsProjectAndDecompilesOnlyConflictCandidates() throws Exception {
        final Path module = Files.createDirectory(temporary.resolve("module"));
        final Path project = Files.createDirectories(
                module.resolve("target/classes/sample"));
        Files.write(project.resolve("Duplicate.class"),
                classBytes("sample/Duplicate", 0));
        final Path output = module.resolve("target/classes").toRealPath();
        final Path dependency = jar(classBytes(
                "sample/Duplicate", Opcodes.ACC_FINAL));
        final ArtifactCoord app = new ArtifactCoord(
                "demo", "app", "jar", "1");
        final ArtifactCoord library = new ArtifactCoord(
                "demo", "library", "jar", "1");
        final ModuleClasspathEvidence evidence = new ModuleClasspathEvidence(
                17, app, module.toRealPath(), List.of(
                new ClasspathEvidenceEntry(0, CodeOrigin.PROJECT,
                        app, "", output),
                new ClasspathEvidenceEntry(1, CodeOrigin.DEPENDENCY,
                        library, "compile", dependency.toRealPath())),
                List.of());
        final DiagnosticLog log = new DiagnosticLog(new PrintStream(
                new ByteArrayOutputStream()), LogVerbosity.DEBUG);

        final ModuleClassConflictAnalyzer.Analysis result =
                new ModuleClassConflictAnalyzer(log).analyze(evidence);

        assertThat(result.issues()).isEmpty();
        assertThat(result.conflicts()).singleElement().satisfies(conflict -> {
            assertThat(conflict.risk()).isEqualTo(ClassConflictRisk.HIGH);
            assertThat(conflict.winner().origin())
                    .isEqualTo(CodeOrigin.PROJECT);
            assertThat(conflict.candidates()).hasSize(2)
                    .allSatisfy(candidate -> assertThat(
                            candidate.decompiled().isAvailable()).isTrue());
        });
    }

    private byte[] classBytes(final String name, final int extraAccess) {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | extraAccess,
                name, null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private Path jar(final byte[] bytes) throws Exception {
        final Path path = temporary.resolve("library.jar");
        try (JarOutputStream output = new JarOutputStream(
                Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry("sample/Duplicate.class"));
            output.write(bytes);
            output.closeEntry();
        }
        return path;
    }
}
