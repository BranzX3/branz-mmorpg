package com.branz.mmorpg.bootstrap;

import java.util.concurrent.atomic.AtomicReference;

final class BootstrapLifecycle {
    enum State {
        NEW,
        ENABLED,
        DISABLED
    }

    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);

    void enable() {
        if (!state.compareAndSet(State.NEW, State.ENABLED)) {
            throw new IllegalStateException("Plugin can only be enabled from NEW state");
        }
    }

    void disable() {
        state.set(State.DISABLED);
    }

    State state() {
        return state.get();
    }
}
