package io.github.dependencyanalysis.impact;

import picocli.CommandLine.ITypeConverter;

/** Picocli converter for dependency analysis scope. */
public final class DependencyAnalysisScopeModeConverter
        implements ITypeConverter<DependencyAnalysisScopeMode> {

    @Override
    public DependencyAnalysisScopeMode convert(final String value) {
        return DependencyAnalysisScopeMode.parse(value);
    }
}
