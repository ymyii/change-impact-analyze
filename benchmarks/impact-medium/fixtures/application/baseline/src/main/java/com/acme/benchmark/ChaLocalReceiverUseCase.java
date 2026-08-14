package com.acme.benchmark;

import com.acme.impact.api.ScenarioApi;

public final class ChaLocalReceiverUseCase {
    public int changedReceiverPath(final int value) {
        final Receiver receiver = new ChangedReceiver();
        return receiver.invoke(value);
    }

    public int unrelatedReceiverPath(final int value) {
        final Receiver receiver = new UnrelatedReceiver();
        return receiver.invoke(value);
    }

    private interface Receiver {
        int invoke(int value);
    }

    private static final class ChangedReceiver implements Receiver {
        @Override
        public int invoke(final int value) {
            return new ScenarioApi().bodyChanged(value);
        }
    }

    private static final class UnrelatedReceiver implements Receiver {
        @Override
        public int invoke(final int value) {
            return value;
        }
    }
}
