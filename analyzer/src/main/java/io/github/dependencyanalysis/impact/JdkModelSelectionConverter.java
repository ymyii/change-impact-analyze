package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.JdkModelSelection;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Converts stable impact CLI values to JDK Method Model selections. */
public final class JdkModelSelectionConverter
        implements ITypeConverter<JdkModelSelection> {

    @Override
    public JdkModelSelection convert(final String value) {
        try {
            return JdkModelSelection.parse(value);
        } catch (IllegalArgumentException exception) {
            throw new TypeConversionException(exception.getMessage());
        }
    }
}
