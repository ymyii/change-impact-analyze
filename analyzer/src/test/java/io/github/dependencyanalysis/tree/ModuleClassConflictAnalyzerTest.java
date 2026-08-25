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

    /** Maven JVM major used by classpath evidence fixtures. */
    private static final int JAVA_MAJOR = 17;

    /** Candidate count in the multi-source fixture. */
    private static final int MULTI_CANDIDATE_COUNT = 3;

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
        final Path dependency = jar("library.jar", classBytes(
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

    @Test
    void differentBytesWithIdenticalDecompiledTextAreLowRisk()
            throws Exception {
        final byte[] firstBytes = classBytes(
                "sample/Duplicate", 0, "First.java");
        final byte[] secondBytes = classBytes(
                "sample/Duplicate", 0, "Second.java");
        final ArtifactCoord first = new ArtifactCoord(
                "demo", "first", "jar", "1");
        final ArtifactCoord second = new ArtifactCoord(
                "demo", "second", "jar", "1");
        final ModuleClasspathEvidence evidence = evidence(List.of(
                dependency(0, first, jar("first.jar", firstBytes)),
                dependency(1, second, jar("second.jar", secondBytes))));

        final ModuleClassConflictAnalyzer.Analysis result = analyze(evidence);

        assertThat(firstBytes).isNotEqualTo(secondBytes);
        assertThat(result.conflicts()).singleElement().satisfies(conflict -> {
            assertThat(conflict.risk()).isEqualTo(ClassConflictRisk.LOW);
            assertThat(conflict.candidates())
                    .extracting(candidate -> candidate
                            .decompiled().getSource())
                    .containsOnly(conflict.candidates().get(0)
                            .decompiled().getSource());
        });
    }

    @Test
    void unavailableDecompilationFallsBackToDigestRisk() throws Exception {
        final ArtifactCoord first = new ArtifactCoord(
                "demo", "broken-first", "jar", "1");
        final ArtifactCoord second = new ArtifactCoord(
                "demo", "broken-second", "jar", "1");
        final ModuleClasspathEvidence evidence = evidence(List.of(
                dependency(0, first, jar("broken-first.jar",
                        classBytes("sample/Duplicate", 0))),
                dependency(1, second, jar("broken-second.jar",
                        new byte[]{4, 5, 6, 7}))));

        final ModuleClassConflictAnalyzer.Analysis result = analyze(evidence);

        assertThat(result.conflicts()).singleElement().satisfies(conflict -> {
            assertThat(conflict.risk()).isEqualTo(ClassConflictRisk.HIGH);
            assertThat(conflict.candidates())
                    .anySatisfy(candidate -> assertThat(
                            candidate.decompiled().isAvailable()).isTrue())
                    .anySatisfy(candidate -> assertThat(
                            candidate.decompiled().isAvailable()).isFalse());
        });
    }

    @Test
    void comparesEveryAvailableCandidateText() throws Exception {
        final ArtifactCoord first = new ArtifactCoord(
                "demo", "first", "jar", "1");
        final ArtifactCoord second = new ArtifactCoord(
                "demo", "second", "jar", "1");
        final ArtifactCoord third = new ArtifactCoord(
                "demo", "third", "jar", "1");
        final ModuleClasspathEvidence evidence = evidence(List.of(
                dependency(0, first, jar("multi-first.jar", classBytes(
                        "sample/Duplicate", 0, "First.java"))),
                dependency(1, second, jar("multi-second.jar", classBytes(
                        "sample/Duplicate", 0, "Second.java"))),
                dependency(2, third, jar("multi-third.jar", classBytes(
                        "sample/Duplicate", Opcodes.ACC_FINAL,
                        "Third.java")))));

        final ModuleClassConflictAnalyzer.Analysis result = analyze(evidence);

        assertThat(result.conflicts()).singleElement().satisfies(conflict -> {
            assertThat(conflict.risk()).isEqualTo(ClassConflictRisk.HIGH);
            assertThat(conflict.candidates()).hasSize(
                            MULTI_CANDIDATE_COUNT)
                    .allSatisfy(candidate -> assertThat(
                            candidate.decompiled().isAvailable()).isTrue());
        });
    }

    private ModuleClassConflictAnalyzer.Analysis analyze(
            final ModuleClasspathEvidence evidence) {
        final DiagnosticLog log = new DiagnosticLog(new PrintStream(
                new ByteArrayOutputStream()), LogVerbosity.DEBUG);
        return new ModuleClassConflictAnalyzer(log).analyze(evidence);
    }

    private ModuleClasspathEvidence evidence(
            final List<ClasspathEvidenceEntry> entries) throws Exception {
        return new ModuleClasspathEvidence(JAVA_MAJOR,
                new ArtifactCoord("demo", "app", "jar", "1"),
                temporary.toRealPath(), entries, List.of());
    }

    private ClasspathEvidenceEntry dependency(
            final int order,
            final ArtifactCoord coordinate,
            final Path path) throws Exception {
        return new ClasspathEvidenceEntry(order, CodeOrigin.DEPENDENCY,
                coordinate, "compile", path.toRealPath());
    }

    private byte[] classBytes(final String name, final int extraAccess) {
        return classBytes(name, extraAccess, null);
    }

    private byte[] classBytes(
            final String name,
            final int extraAccess,
            final String source) {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | extraAccess,
                name, null, "java/lang/Object", null);
        if (source != null) {
            writer.visitSource(source, null);
        }
        writer.visitEnd();
        return writer.toByteArray();
    }

    private Path jar(final String name, final byte[] bytes) throws Exception {
        final Path path = temporary.resolve(name);
        try (JarOutputStream output = new JarOutputStream(
                Files.newOutputStream(path))) {
            output.putNextEntry(new JarEntry("sample/Duplicate.class"));
            output.write(bytes);
            output.closeEntry();
        }
        return path;
    }
}
