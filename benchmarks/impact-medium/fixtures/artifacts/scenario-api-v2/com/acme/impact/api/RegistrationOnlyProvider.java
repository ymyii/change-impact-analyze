package com.acme.impact.api;

public class RegistrationOnlyProvider implements BenchmarkService {
    public RegistrationOnlyProvider() {
    }

    @Override
    public String run() {
        return "registration-only-provider";
    }
}
