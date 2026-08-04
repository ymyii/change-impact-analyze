package io.github.dependencyanalysis.cli;

import io.github.dependencyanalysis.runtime.BuildMetadata;

import picocli.CommandLine.IVersionProvider;

/** Supplies the filtered Analyzer release version to picocli. */
public final class DependencyAnalyzerVersionProvider
        implements IVersionProvider {

    @Override
    public String[] getVersion() {
        return new String[]{BuildMetadata.getAnalyzerVersion()};
    }
}
