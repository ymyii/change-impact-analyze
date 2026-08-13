package com.acme.impact.boundary;

public abstract class ExternalAncestor extends ExternalGrandParent
        implements ExternalContract {
    public int inheritedPublic(final int value) {
        return helper(value);
    }

    private int helper(final int value) {
        return abstractMethod(value);
    }

    @Override
    public abstract int abstractMethod(int value);
}
