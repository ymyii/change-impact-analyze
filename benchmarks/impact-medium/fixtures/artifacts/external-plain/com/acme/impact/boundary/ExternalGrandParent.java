package com.acme.impact.boundary;

public abstract class ExternalGrandParent {
    public int grandMethod(final int value) {
        return abstractMethod(value);
    }

    protected abstract int abstractMethod(int value);
}
