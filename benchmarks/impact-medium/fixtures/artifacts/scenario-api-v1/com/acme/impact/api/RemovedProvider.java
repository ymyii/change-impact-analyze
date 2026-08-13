package com.acme.impact.api;

public class RemovedProvider implements BenchmarkService {
    public RemovedProvider() {
    }

    @Override
    public String run() {
        return "removed-provider";
    }
}
