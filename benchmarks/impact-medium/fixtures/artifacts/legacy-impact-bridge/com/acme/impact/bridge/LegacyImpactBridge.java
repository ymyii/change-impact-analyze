package com.acme.impact.bridge;

import com.acme.impact.api.RemovedType;
import com.acme.impact.api.ScenarioApi;

public class LegacyImpactBridge {
    private RemovedType structuralReference;

    public String callRemovedMethod(final String value) {
        return new ScenarioApi().removedMethod(value);
    }

    public String callOldDescriptor(final String value) {
        return new ScenarioApi().descriptorChanged(value);
    }

    public int readRemovedField() {
        return new ScenarioApi().removedField;
    }

    public Object readOldDescriptorField() {
        return new ScenarioApi().descriptorField;
    }

    public Object instantiateRemovedType() {
        return null;
    }
}
