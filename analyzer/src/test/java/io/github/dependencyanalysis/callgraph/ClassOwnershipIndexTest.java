package io.github.dependencyanalysis.callgraph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests deterministic duplicate class ownership. */
class ClassOwnershipIndexTest {

    /** Temporary class roots. */
    @TempDir
    private Path temporary;

    @Test
    void identicalDuplicateUsesHigherPriorityOrigin() throws Exception {
        final Path dependency = temporary.resolve("dependency");
        final Path project = temporary.resolve("project");
        final byte[] bytes = classBytes("sample/Duplicate", 0);
        write(dependency, bytes);
        write(project, bytes);
        final ClassOwnershipIndex index = new ClassOwnershipIndex();

        index.addDirectory(dependency, CodeOrigin.DEPENDENCY);
        index.addDirectory(project, CodeOrigin.PROJECT);

        assertThat(index.ownershipOf("Lsample/Duplicate").getOrigin())
                .isEqualTo(CodeOrigin.PROJECT);
    }

    @Test
    void conflictingDuplicateSelectsWinnerAndRetainsEvidence()
            throws Exception {
        final Path first = temporary.resolve("first");
        final Path second = temporary.resolve("second");
        write(first, classBytes("sample/Duplicate", 0));
        write(second, classBytes("sample/Duplicate",
                Opcodes.ACC_FINAL));
        final ClassOwnershipIndex index = new ClassOwnershipIndex();
        index.addDirectory(first, CodeOrigin.PROJECT);

        index.addDirectory(second, CodeOrigin.DEPENDENCY);

        assertThat(index.ownershipOf("sample/Duplicate").getSource())
                .isEqualTo(ClassSource.path(first));
        assertThat(index.duplicateClassResolutions()).singleElement()
                .satisfies(resolution -> {
                    assertThat(resolution.getBinaryName())
                            .isEqualTo("sample/Duplicate");
                    assertThat(resolution.getWinner().getOrigin())
                            .isEqualTo(CodeOrigin.PROJECT);
                    assertThat(resolution.getLosers()).singleElement()
                            .extracting(ClassOwnership::getSource)
                            .isEqualTo(ClassSource.path(second));
                    assertThat(resolution.getPrecedenceReason())
                            .isEqualTo("Current module target/classes "
                                    + "precedence");
                });
    }

    @Test
    void sameOriginUsesFirstClasspathDefinition() throws Exception {
        final Path first = temporary.resolve("first-dependency");
        final Path second = temporary.resolve("second-dependency");
        write(first, classBytes("sample/Duplicate", 0));
        write(second, classBytes("sample/Duplicate", Opcodes.ACC_FINAL));
        final ClassOwnershipIndex index = new ClassOwnershipIndex();

        index.addDirectory(first, CodeOrigin.DEPENDENCY);
        index.addDirectory(second, CodeOrigin.DEPENDENCY);

        assertThat(index.ownershipOf("sample/Duplicate").getSource())
                .isEqualTo(ClassSource.path(first));
        assertThat(index.duplicateClassResolutions()).singleElement()
                .extracting(DuplicateClassResolution::getPrecedenceReason)
                .isEqualTo("External dependency classpath order");
    }

    @Test
    void reactorDefinitionWinsOverExternalDependency() throws Exception {
        final Path external = temporary.resolve("external-dependency");
        final Path reactor = temporary.resolve("reactor-dependency");
        write(external, classBytes("sample/Duplicate", 0));
        write(reactor, classBytes("sample/Duplicate", Opcodes.ACC_FINAL));
        final ClassOwnershipIndex index = new ClassOwnershipIndex();

        index.addDirectory(external, CodeOrigin.DEPENDENCY);
        index.addDirectory(reactor, CodeOrigin.REACTOR_DEPENDENCY);

        assertThat(index.ownershipOf("sample/Duplicate").getSource())
                .isEqualTo(ClassSource.path(reactor));
        assertThat(index.duplicateClassResolutions()).singleElement()
                .extracting(DuplicateClassResolution::getPrecedenceReason)
                .isEqualTo("Reactor dependency classpath order");
    }

    @Test
    void identicalDuplicateDoesNotProduceConflictEvidence()
            throws Exception {
        final Path first = temporary.resolve("identical-first");
        final Path second = temporary.resolve("identical-second");
        final byte[] bytes = classBytes("sample/Duplicate", 0);
        write(first, bytes);
        write(second, bytes);
        final ClassOwnershipIndex index = new ClassOwnershipIndex();

        index.addDirectory(first, CodeOrigin.DEPENDENCY);
        index.addDirectory(second, CodeOrigin.DEPENDENCY);

        assertThat(index.duplicateClassResolutions()).isEmpty();
        assertThat(index.isEffectiveDefinition(
                "sample/Duplicate", first)).isTrue();
        assertThat(index.isEffectiveDefinition(
                "sample/Duplicate", second)).isFalse();
    }

    @Test
    void excludesRootModuleInfoFromDirectories() throws Exception {
        final Path project = temporary.resolve("module-project");
        final Path reactor = temporary.resolve("module-reactor");
        write(project, "module-info.class",
                classBytes("module-info", 0));
        write(reactor, "module-info.class",
                classBytes("module-info", Opcodes.ACC_FINAL));
        final ClassOwnershipIndex index = new ClassOwnershipIndex();

        index.addDirectory(project, CodeOrigin.PROJECT);
        index.addDirectory(reactor, CodeOrigin.REACTOR_DEPENDENCY);

        assertThat(index.ownershipOf("module-info")).isNull();
        assertThat(index.binaryNames()).doesNotContain("module-info");
    }

    @Test
    void excludesRootModuleInfoFromJarsButIndexesClasses()
            throws Exception {
        final byte[] ordinary = classBytes("sample/Owned", 0);
        final Path first = jar("first-module.jar", Map.of(
                "module-info.class", classBytes("module-info", 0),
                "META-INF/versions/9/sample/Versioned.class",
                classBytes("sample/Versioned", 0),
                "sample/Owned.class", ordinary));
        final Path second = jar("second-module.jar", Map.of(
                "module-info.class", classBytes("module-info",
                        Opcodes.ACC_FINAL)));
        final ClassOwnershipIndex index = new ClassOwnershipIndex();

        index.addJar(first, CodeOrigin.DEPENDENCY);
        index.addJar(second, CodeOrigin.REACTOR_DEPENDENCY);

        assertThat(index.ownershipOf("module-info")).isNull();
        assertThat(index.binaryNames()).containsExactly("sample/Owned");
        assertThat(index.ownershipOf("sample/Owned").getSource())
                .isEqualTo(ClassSource.path(first));
    }

    @Test
    void validatesOnlyJdkNamesThatConflictWithIndexedClasses()
            throws Exception {
        final Path project = temporary.resolve("jdk-project");
        final byte[] bytes = classBytes("sample/Duplicate", 0);
        write(project, bytes);
        final Path jar = jar("jdk.jar", Map.of(
                "sample/Duplicate.class", bytes,
                "jdk/Only.class", classBytes("jdk/Only", 0)));
        final ClassOwnershipIndex index = new ClassOwnershipIndex();
        index.addDirectory(project, CodeOrigin.PROJECT);

        index.validateJarDuplicates(jar, CodeOrigin.JDK, name -> true);

        assertThat(index.ownershipOf("sample/Duplicate").getOrigin())
                .isEqualTo(CodeOrigin.JDK);
        assertThat(index.ownershipOf("jdk/Only")).isNull();
    }

    @Test
    void conflictingJdkDefinitionWinsByParentLoaderPrecedence()
            throws Exception {
        final Path project = temporary.resolve("conflicting-jdk-project");
        write(project, classBytes("sample/Duplicate", 0));
        final Path jar = jar("conflicting-jdk.jar", Map.of(
                "sample/Duplicate.class", classBytes(
                        "sample/Duplicate", Opcodes.ACC_FINAL)));
        final ClassOwnershipIndex index = new ClassOwnershipIndex();
        index.addDirectory(project, CodeOrigin.PROJECT);

        index.validateJarDuplicates(jar, CodeOrigin.JDK, name -> true);

        assertThat(index.ownershipOf("sample/Duplicate").getSource())
                .isEqualTo(ClassSource.path(jar));
        assertThat(index.duplicateClassResolutions()).singleElement()
                .satisfies(resolution -> {
                    assertThat(resolution.getWinner().getOrigin())
                            .isEqualTo(CodeOrigin.JDK);
                    assertThat(resolution.getPrecedenceReason())
                            .isEqualTo("JDK parent/bootstrap precedence");
                });
    }

    private void write(final Path root, final byte[] bytes)
            throws Exception {
        write(root, "sample/Duplicate.class", bytes);
    }

    private void write(
            final Path root,
            final String relative,
            final byte[] bytes) throws Exception {
        final Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    private byte[] classBytes(final String name, final int extraAccess) {
        final ClassWriter writer = new ClassWriter(0);
        writer.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | extraAccess,
                name, null, "java/lang/Object", null);
        writer.visitEnd();
        return writer.toByteArray();
    }

    private Path jar(
            final String name,
            final java.util.Map<String, byte[]> entries) throws Exception {
        final Path path = temporary.resolve(name);
        try (JarOutputStream output = new JarOutputStream(
                Files.newOutputStream(path))) {
            for (java.util.Map.Entry<String, byte[]> entry
                    : entries.entrySet()) {
                output.putNextEntry(new JarEntry(entry.getKey()));
                output.write(entry.getValue());
                output.closeEntry();
            }
        }
        return path;
    }
}
