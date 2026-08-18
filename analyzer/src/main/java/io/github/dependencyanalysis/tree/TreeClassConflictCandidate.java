package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.bytecode.DecompiledMethod;
import io.github.dependencyanalysis.classpath.CodeOrigin;

import java.nio.file.Path;
import java.util.Objects;

/**
 * One effective class definition candidate with best-effort source.
 *
 * @param origin logical source kind
 * @param source logical Maven coordinate
 * @param scope Maven scope
 * @param digest SHA-256 class bytes digest
 * @param effectiveEntry exact effective class entry
 * @param physicalPath analysis-only classpath path
 * @param decompiled best-effort source result
 */
public record TreeClassConflictCandidate(
        CodeOrigin origin,
        String source,
        String scope,
        String digest,
        String effectiveEntry,
        Path physicalPath,
        DecompiledMethod decompiled) {

    /** Validates immutable candidate evidence. */
    public TreeClassConflictCandidate {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(digest, "digest");
        Objects.requireNonNull(effectiveEntry, "effectiveEntry");
        physicalPath = Objects.requireNonNull(
                physicalPath, "physicalPath").toAbsolutePath().normalize();
        Objects.requireNonNull(decompiled, "decompiled");
    }
}
