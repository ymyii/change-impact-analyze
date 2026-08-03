package com.acme.benchmark;

import com.acme.impact.api.ScenarioApi;

public class BodyChangeUseCase {
    public static int execute() {
        return new ScenarioApi().bodyChanged(7);
    }
}
