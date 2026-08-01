package io.github.dependencyanalysis.callgraph;

import io.github.dependencyanalysis.dependency.ResolvedArtifact;
import io.github.dependencyanalysis.impact.ModuleAnalysisUnit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/** Validates explicit references against Spring backend JDK exclusions. */
public final class ModuleScopeValidator {

    /** Reference scanner. */
    private final BytecodeClassReferenceScanner scanner =
            new BytecodeClassReferenceScanner();

    /** Exclusion matcher. */
    private final SpringBackendJdkExclusions exclusions =
            new SpringBackendJdkExclusions();

    /**
     * Validates PROJECT, REACTOR_DEPENDENCY, and DEPENDENCY bytecode.
     *
     * @param unit module scope input
     */
    public void validate(final ModuleAnalysisUnit unit) {
        try {
            validateDirectory(unit.getProjectClasses());
            for (Path path : unit.getReactorDependencyClasses()) {
                validateDirectory(path);
            }
            for (ResolvedArtifact artifact : unit.getTargetArtifacts()) {
                if ("jar".equals(artifact.getArtifact().getType())) {
                    validateJar(artifact.getPath());
                }
            }
        } catch (ScopeValidationException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ScopeValidationException(
                    "Unable to validate module scope", exception);
        }
    }

    private void validateDirectory(final Path directory) throws IOException {
        final List<Path> files;
        try (Stream<Path> stream = Files.walk(directory)) {
            files = stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".class"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
        for (Path file : files) {
            validateClass(file.toString(), Files.readAllBytes(file));
        }
    }

    private void validateJar(final Path path) throws IOException {
        try (JarFile jar = new JarFile(path.toFile(), false)) {
            final List<JarEntry> entries = new ArrayList<>();
            jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .filter(entry -> entry.getName().endsWith(".class"))
                    .filter(entry -> !entry.getName().startsWith(
                            "META-INF/versions/"))
                    .sorted(Comparator.comparing(JarEntry::getName))
                    .forEach(entries::add);
            for (JarEntry entry : entries) {
                try (InputStream input = jar.getInputStream(entry)) {
                    validateClass(path + "!/" + entry.getName(),
                            input.readAllBytes());
                }
            }
        }
    }

    private void validateClass(
            final String source, final byte[] bytes) {
        for (String reference : scanner.scan(bytes)) {
            if (exclusions.test(reference)) {
                throw new ScopeValidationException(
                        "Explicit reference to excluded JDK class: "
                                + reference + " from " + source);
            }
        }
    }
}
