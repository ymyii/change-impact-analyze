package com.acme.benchmark;

import com.acme.impact.api.ScenarioApi;

public class BodyChangeUseCase {
    public int execute(final int value) {
        return new ScenarioApi().bodyChanged(value);
    }
}
