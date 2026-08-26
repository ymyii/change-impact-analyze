package io.github.dependencyanalysis.tree;

import io.github.dependencyanalysis.runtime.MavenRuntimeDescriptor;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

record TreeDiffReportMetadata(
        TreeDiffSideMetadata baseline,
        TreeDiffSideMetadata target,
        Path requestedPath,
        Path analysisPath,
        Set<String> scopes,
        MavenRuntimeDescriptor runtime,
        String dependencyPluginVersion,
        List<String> mavenArguments) {

    TreeDiffReportMetadata {
        requestedPath = requestedPath.toAbsolutePath().normalize();
        analysisPath = analysisPath.normalize();
        scopes = Collections.unmodifiableSet(new LinkedHashSet<>(
                scopes.stream().sorted().toList()));
        mavenArguments = List.copyOf(mavenArguments);
    }
}
