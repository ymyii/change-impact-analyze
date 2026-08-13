package com.acme.benchmark;

public class ObjectDispatchUseCase {
    public int execute(final Object value) {
        return value.toString().length() + value.hashCode();
    }
}
