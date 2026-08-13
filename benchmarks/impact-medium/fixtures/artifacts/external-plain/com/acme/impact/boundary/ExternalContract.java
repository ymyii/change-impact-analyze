package com.acme.impact.boundary;

public interface ExternalContract {
    default int interfaceMethod(final int value) {
        return abstractMethod(value);
    }

    int abstractMethod(int value);
}
