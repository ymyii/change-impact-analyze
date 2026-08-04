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

// Wiki: wiki/features/call-graph-engine.md - JDK exclusion validation boundary
/** Validates explicit references against Spring backend JDK exclusions. */
public final class ModuleScopeValidator {

    /** Maximum source/reference examples retained per artifact. */
    private static final int MAX_EXAMPLES = 5;

    /** Reference scanner. */
    private final BytecodeClassReferenceScanner scanner =
            new BytecodeClassReferenceScanner();

    /** Exclusion matcher. */
    private final SpringBackendJdkExclusions exclusions =
            new SpringBackendJdkExclusions();

    /**
     * Validates PROJECT, REACTOR_DEPENDENCY, and DEPENDENCY bytecode.
     * External dependency references are returned as warnings.
     *
     * @param unit module scope input
     * @return non-blocking external dependency findings
     */
    public ScopeValidationResult validate(final ModuleAnalysisUnit unit) {
        try {
            validateDirectory(unit.getProjectClasses());
            for (Path path : unit.getReactorDependencyClasses()) {
                validateDirectory(path);
            }
            final List<ScopeValidationWarning> warnings =
                    new ArrayList<>();
            for (ResolvedArtifact artifact : unit.getTargetArtifacts()) {
                if ("jar".equals(artifact.getArtifact().getType())) {
                    final ScopeValidationWarning warning =
                            validateExternalJar(artifact);
                    if (warning != null) {
                        warnings.add(warning);
                    }
                }
            }
            return new ScopeValidationResult(warnings);
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

    private ScopeValidationWarning validateExternalJar(
            final ResolvedArtifact artifact) throws IOException {
        final Path path = artifact.getPath();
        final List<ReferenceFinding> findings = new ArrayList<>();
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
                    collectFindings(entry.getName(), input.readAllBytes(),
                            findings);
                }
            }
        }
        if (findings.isEmpty()) {
            return null;
        }
        findings.sort(Comparator
                .comparing(ReferenceFinding::source)
                .thenComparing(ReferenceFinding::reference));
        final int excludedTypeCount = (int) findings.stream()
                .map(ReferenceFinding::reference)
                .distinct().count();
        final List<String> examples = findings.stream()
                .limit(MAX_EXAMPLES)
                .map(finding -> finding.source() + " -> "
                        + finding.reference())
                .toList();
        return new ScopeValidationWarning(artifact, findings.size(),
                excludedTypeCount, examples);
    }

    private void validateClass(
            final String source, final byte[] bytes) {
        for (String reference : excludedReferences(bytes)) {
            throw new ScopeValidationException(
                    "Explicit reference to excluded JDK class: "
                            + reference + " from " + source);
        }
    }

    private void collectFindings(
            final String source,
            final byte[] bytes,
            final List<ReferenceFinding> findings) {
        for (String reference : excludedReferences(bytes)) {
            findings.add(new ReferenceFinding(source, reference));
        }
    }

    private List<String> excludedReferences(final byte[] bytes) {
        return scanner.scan(bytes).stream()
                .filter(exclusions::test)
                .sorted()
                .toList();
    }

    /**
     * One distinct class-level excluded reference.
     *
     * @param source source class path
     * @param reference excluded JDK class
     */
    private record ReferenceFinding(String source, String reference) {
    }
}
