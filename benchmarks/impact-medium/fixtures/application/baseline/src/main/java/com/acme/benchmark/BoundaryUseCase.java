package com.acme.benchmark;

import com.acme.impact.boundary.ExternalPlain;
import com.acme.benchmark.reactor.ReactorPath;

public class BoundaryUseCase {
    public int exercise() {
        ExternalPlain.call();
        return new ReactorPath().invokeChangedBody(1);
    }
}
