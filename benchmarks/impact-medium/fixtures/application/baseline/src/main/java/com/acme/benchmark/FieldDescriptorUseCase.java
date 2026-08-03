package com.acme.benchmark;

import com.acme.impact.api.ScenarioApi;
import com.acme.impact.bridge.LegacyImpactBridge;

public class FieldDescriptorUseCase {
    public String executeNewDescriptor() {
        return new ScenarioApi().descriptorField;
    }

    public Object executeOldDescriptor() {
        return new LegacyImpactBridge().readOldDescriptorField();
    }
}
