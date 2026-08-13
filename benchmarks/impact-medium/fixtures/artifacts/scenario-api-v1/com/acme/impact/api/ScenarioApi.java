package com.acme.impact.api;

public class ScenarioApi {
    public int removedField;
    public String descriptorField;

    public int bodyChanged(final int value) {
        return value + 1;
    }

    public String removedMethod(final String value) {
        return "removed:" + value;
    }

    public String descriptorChanged(final String value) {
        return value.trim();
    }

    @Override
    public String toString() {
        return "scenario-v1";
    }

    @Override
    public int hashCode() {
        return 101;
    }
}
