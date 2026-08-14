package io.github.dependencyanalysis.impact.refinement;

import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.TypeConversionException;

/** Converts the unified result-refinement CLI selection. */
public final class ResultRefinementSelectionConverter
        implements ITypeConverter<ResultRefinementSelection> {

    @Override
    public ResultRefinementSelection convert(final String value) {
        try {
            return ResultRefinementSelection.parse(value);
        } catch (IllegalArgumentException exception) {
            throw new TypeConversionException(exception.getMessage());
        }
    }
}
