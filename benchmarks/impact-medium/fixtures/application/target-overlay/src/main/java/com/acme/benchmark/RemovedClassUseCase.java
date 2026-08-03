package com.acme.benchmark;

import com.acme.impact.bridge.LegacyImpactBridge;

public class RemovedClassUseCase {
    public static Object execute() {
        return new LegacyImpactBridge().instantiateRemovedType();
    }
}
