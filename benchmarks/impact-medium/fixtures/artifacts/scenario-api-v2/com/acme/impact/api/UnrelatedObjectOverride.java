package com.acme.impact.api;

public class UnrelatedObjectOverride {
    @Override
    public String toString() {
        return "unchanged";
    }

    @Override
    public int hashCode() {
        return 303;
    }
}
