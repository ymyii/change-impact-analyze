package com.acme.impact.api;

public class StableProvider implements BenchmarkService {
    public StableProvider() {
    }

    @Override
    public String run() {
        return "stable-provider";
    }
}
