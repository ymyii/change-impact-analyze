package com.acme.benchmark;

import com.acme.impact.api.ScenarioApi;
import com.acme.impact.bridge.LegacyImpactBridge;

public class DescriptorChangeUseCase {
    public String executeNewDescriptor(final String value) {
        return new ScenarioApi().descriptorChanged(value);
    }

    public String executeOldDescriptor(final String value) {
        return new LegacyImpactBridge().callOldDescriptor(value);
    }
}
