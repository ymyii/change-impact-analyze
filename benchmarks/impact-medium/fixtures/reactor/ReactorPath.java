package com.acme.benchmark.reactor;

import com.acme.impact.api.ScenarioApi;

public class ReactorPath {
    public int invokeChangedBody(final int value) {
        return new ScenarioApi().bodyChanged(value);
    }
}
