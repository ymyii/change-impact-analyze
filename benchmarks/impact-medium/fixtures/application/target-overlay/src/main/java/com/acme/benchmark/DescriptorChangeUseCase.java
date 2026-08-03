package com.acme.benchmark;

import com.acme.impact.bridge.LegacyImpactBridge;

public class DescriptorChangeUseCase {
    public static String executeOldDescriptor() {
        return new LegacyImpactBridge().callOldDescriptor("impact");
    }
}
