package com.acme.benchmark;

import com.acme.impact.api.ScenarioApi;

public class RecursiveCallUseCase {
    public int execute(final int depth) {
        return recurse(depth);
    }

    private static int recurse(final int depth) {
        if (depth <= 0) {
            return new ScenarioApi().bodyChanged(depth);
        }
        return recurse(depth - 1);
    }
}
