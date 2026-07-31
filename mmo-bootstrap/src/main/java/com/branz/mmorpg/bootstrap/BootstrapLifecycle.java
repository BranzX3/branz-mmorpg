package com.branz.mmorpg.bootstrap;

import java.util.concurrent.atomic.AtomicReference;

final class BootstrapLifecycle {
    enum State {
        NEW,
        STARTING,
        READY,
        DEGRADED,
        MAINTENANCE,
        DISABLED
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);

    void beginStartup() {
        if (!state.compareAndSet(State.NEW, State.STARTING)) {
            throw new IllegalStateException("Plugin can only start from NEW state");
        }
    }

    boolean completeStartup(StartupStatus status) {
        State target =
                switch (status) {
                    case READY -> State.READY;
                    case DEGRADED -> State.DEGRADED;
                    case MAINTENANCE -> State.MAINTENANCE;
                };
        return state.compareAndSet(State.STARTING, target);
    }

    void disable() {
        state.set(State.DISABLED);
    }

    State state() {
        return state.get();
    }

    boolean acceptsSessions() {
        State current = state();
        return current == State.READY || current == State.DEGRADED;
    }
}
