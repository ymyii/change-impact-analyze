package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.WalaReflectionOptions;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Converts WALA ReflectionOptions enum names from CLI input. */
public final class WalaReflectionOptionsConverter
        implements ITypeConverter<WalaReflectionOptions> {

    @Override
    public WalaReflectionOptions convert(final String value) {
        try {
            return WalaReflectionOptions.parse(value);
        } catch (IllegalArgumentException exception) {
            throw new TypeConversionException(exception.getMessage());
        }
    }
}
