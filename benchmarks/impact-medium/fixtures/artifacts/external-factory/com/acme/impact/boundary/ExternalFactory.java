package com.acme.impact.boundary;

import com.acme.impact.api.ScenarioApi;

public final class ExternalFactory {
    private ExternalFactory() {
    }

    public static Object create() {
        return new ScenarioApi();
    }
}
