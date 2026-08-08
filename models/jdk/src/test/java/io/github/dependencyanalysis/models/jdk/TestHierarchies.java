package io.github.dependencyanalysis.models.jdk;

import com.ibm.wala.classLoader.JarFileModule;
import com.ibm.wala.classLoader.BinaryDirectoryTreeModule;
import com.ibm.wala.core.java11.JrtModule;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.ClassHierarchyException;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarFile;
import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

/** Shared real-JDK hierarchy fixtures. */
final class TestHierarchies {

    private TestHierarchies() {
    }

    static IClassHierarchy jdk8() throws Exception {
        return jdk8(null);
    }

    static IClassHierarchy jdk8(final Path applicationClasses)
            throws Exception {
        final String configured = System.getenv("TEST_JDK8_HOME");
        if (configured == null || configured.isBlank()) {
            throw new IllegalStateException("TEST_JDK8_HOME is required");
        }
        final Path root = Path.of(configured);
        final Path rt = firstExisting(List.of(
                root.resolve("jre/lib/rt.jar"),
                root.resolve("lib/rt.jar")));
        final AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();
        scope.addToScope(ClassLoaderReference.Primordial,
                new JarFileModule(new JarFile(rt.toFile(), false)));
        if (applicationClasses != null) {
            scope.addToScope(ClassLoaderReference.Application,
                    new BinaryDirectoryTreeModule(
                            applicationClasses.toFile()));
        }
        return ClassHierarchyFactory.make(scope);
    }

    static IClassHierarchy hostJdk() throws IOException,
            ClassHierarchyException {
        return hostJdk(null);
    }

    static IClassHierarchy hostJdk(final Path applicationClasses)
            throws IOException, ClassHierarchyException {
        final AnalysisScope scope = AnalysisScope.createJavaAnalysisScope();
        scope.addToScope(ClassLoaderReference.Primordial,
                new JrtModule("java.base"));
        if (applicationClasses != null) {
            scope.addToScope(ClassLoaderReference.Application,
                    new BinaryDirectoryTreeModule(
                            applicationClasses.toFile()));
        }
        return ClassHierarchyFactory.make(scope);
    }

    static Path compileFixture() throws IOException {
        final Path output = Files.createTempDirectory(
                "jdk-model-fixture-classes");
        final Path source = Files.createTempDirectory(
                "jdk-model-fixture-source")
                .resolve("fixture/JdkModelFixture.java");
        Files.createDirectories(source.getParent());
        try (InputStream input = TestHierarchies.class.getResourceAsStream(
                "/fixtures/JdkModelFixture.java")) {
            if (input == null) {
                throw new IllegalStateException(
                        "JDK model fixture source is unavailable");
            }
            Files.copy(input, source);
        }
        final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException(
                    "A full JDK is required to compile the fixture");
        }
        try (StandardJavaFileManager manager = compiler
                .getStandardFileManager(null, null, null)) {
            final boolean success = compiler.getTask(null, manager, null,
                    List.of("--release", "8", "-d", output.toString()),
                    null, manager.getJavaFileObjects(source)).call();
            if (!success) {
                throw new IllegalStateException(
                        "Unable to compile JDK model fixture");
            }
        }
        return output;
    }

    static AnalysisOptions options(final IClassHierarchy hierarchy) {
        final AnalysisOptions result = new AnalysisOptions(
                hierarchy.getScope(), List.of());
        Util.addDefaultSelectors(result, hierarchy);
        return result;
    }

    private static Path firstExisting(final List<Path> paths) {
        return paths.stream().filter(Files::isRegularFile).findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "JDK 8 rt.jar is unavailable under " + paths));
    }
}
