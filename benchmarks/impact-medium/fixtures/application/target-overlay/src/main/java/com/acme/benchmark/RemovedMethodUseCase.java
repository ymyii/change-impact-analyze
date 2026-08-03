package com.acme.benchmark;

import com.acme.impact.bridge.LegacyImpactBridge;

public class RemovedMethodUseCase {
    public static String execute() {
        return new LegacyImpactBridge().callRemovedMethod("impact");
    }
}
