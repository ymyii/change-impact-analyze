package com.acme.benchmark;

import com.acme.impact.api.ScenarioApi;
import com.acme.impact.bridge.LegacyImpactBridge;

public class FieldDescriptorUseCase {
    public static int executeNewDescriptor() {
        return new ScenarioApi().descriptorField;
    }

    public static Object executeOldDescriptor() {
        return new LegacyImpactBridge().readOldDescriptorField();
    }
}
