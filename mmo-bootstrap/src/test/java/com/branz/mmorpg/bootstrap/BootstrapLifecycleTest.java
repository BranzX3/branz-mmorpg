package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class BootstrapLifecycleTest {
    @Test
    void enablesAndDisablesCleanly() {
        BootstrapLifecycle lifecycle = new BootstrapLifecycle();

        lifecycle.enable();
        assertEquals(BootstrapLifecycle.State.ENABLED, lifecycle.state());
        lifecycle.disable();

        assertEquals(BootstrapLifecycle.State.DISABLED, lifecycle.state());
    }

    @Test
    void cannotEnableTwice() {
        BootstrapLifecycle lifecycle = new BootstrapLifecycle();
        lifecycle.enable();

        assertThrows(IllegalStateException.class, lifecycle::enable);
    }
}
