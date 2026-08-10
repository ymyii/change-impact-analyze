package com.acme.benchmark;

import com.acme.impact.api.ScenarioApi;
import com.acme.impact.boundary.ExternalFactory;
import com.acme.impact.boundary.ExternalPlain;
import com.acme.impact.boundary.ExternalSink;
import com.acme.benchmark.reactor.ReactorPath;

public class BoundaryUseCase {
    public int exercise() {
        final ScenarioApi changed = new ScenarioApi();
        ExternalSink.accept(changed);
        final ScenarioApi materialized =
                (ScenarioApi) ExternalFactory.create();
        ExternalPlain.call();
        return materialized.bodyChanged(
                new ReactorPath().invokeChangedBody(1));
    }
}
