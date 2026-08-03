package com.acme.impact.api;

public class ScenarioApi {
    public int descriptorField;
    public long addedField;

    public int bodyChanged(final int value) {
        return value + 2;
    }

    public String descriptorChanged(final Object value) {
        return String.valueOf(value).trim();
    }

    public String addedMethod(final String value) {
        return "added:" + value;
    }
}
