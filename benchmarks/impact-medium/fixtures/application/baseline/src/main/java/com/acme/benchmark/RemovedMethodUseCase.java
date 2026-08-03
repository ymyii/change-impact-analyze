package com.acme.benchmark;

import com.acme.impact.bridge.LegacyImpactBridge;

public class RemovedMethodUseCase {
    public String execute(final String value) {
        return new LegacyImpactBridge().callRemovedMethod(value);
    }
}
