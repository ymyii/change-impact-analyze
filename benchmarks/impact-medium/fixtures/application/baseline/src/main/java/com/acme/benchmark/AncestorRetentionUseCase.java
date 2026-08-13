package com.acme.benchmark;

import com.acme.impact.api.ScenarioApi;
import com.acme.impact.boundary.ExternalAncestor;

public final class AncestorRetentionUseCase extends ExternalAncestor {
    @Override
    public int abstractMethod(final int value) {
        return new ScenarioApi().bodyChanged(value);
    }

    public int exercise(final int value) {
        return inheritedPublic(value)
                + grandMethod(value)
                + interfaceMethod(value);
    }
}
