package com.acme.impact.boundary;

import com.acme.impact.api.ScenarioApi;

public final class ExternalSink {
    private ExternalSink() {
    }

    public static void accept(final ScenarioApi changed) {
        changed.bodyChanged(42);
    }
}
