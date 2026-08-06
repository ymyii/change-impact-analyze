package io.github.dependencyanalysis.impact;

import io.github.dependencyanalysis.callgraph.CallGraphAlgorithm;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Converts stable impact CLI values to Call Graph algorithms. */
public final class CallGraphAlgorithmConverter
        implements ITypeConverter<CallGraphAlgorithm> {

    @Override
    public CallGraphAlgorithm convert(final String value) {
        try {
            return CallGraphAlgorithm.parse(value);
        } catch (IllegalArgumentException exception) {
            throw new TypeConversionException(exception.getMessage());
        }
    }
}
