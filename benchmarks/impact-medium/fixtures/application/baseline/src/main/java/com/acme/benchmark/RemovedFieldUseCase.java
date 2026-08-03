package com.acme.benchmark;

import com.acme.impact.bridge.LegacyImpactBridge;

public class RemovedFieldUseCase {
    public int execute() {
        return new LegacyImpactBridge().readRemovedField();
    }
}
