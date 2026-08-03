package com.acme.benchmark;

public final class BenchmarkApplication {
    private BenchmarkApplication() {
    }

    public static void main(final String[] args) {
        new ImpactFacade().exerciseAll("impact");
    }
}
