package com.acme.benchmark;

import com.acme.impact.api.ScenarioApi;

import java.util.function.Function;
import java.util.stream.Stream;

public class JdkModelUseCase {
    public Stream<Integer> execute(final Stream<String> values) {
        return values.map(new ChangedMapper());
    }

    private static final class ChangedMapper
            implements Function<String, Integer> {
        @Override
        public Integer apply(final String value) {
            return new ScenarioApi().bodyChanged(value.length());
        }
    }
}
