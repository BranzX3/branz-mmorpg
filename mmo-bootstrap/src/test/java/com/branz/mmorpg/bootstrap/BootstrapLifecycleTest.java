package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BootstrapLifecycleTest {
    @Test
    void acceptsSessionsOnlyAfterReadyOrDegradedStartup() {
        BootstrapLifecycle ready = new BootstrapLifecycle();
        BootstrapLifecycle degraded = new BootstrapLifecycle();

        ready.beginStartup();
        degraded.beginStartup();
        assertFalse(ready.acceptsSessions());

        assertTrue(ready.completeStartup(StartupStatus.READY));
        assertTrue(degraded.completeStartup(StartupStatus.DEGRADED));

        assertTrue(ready.acceptsSessions());
        assertTrue(degraded.acceptsSessions());
    }

    @Test
    void maintenanceAndDisabledStatesRejectSessions() {
        BootstrapLifecycle lifecycle = new BootstrapLifecycle();
        lifecycle.beginStartup();
        lifecycle.completeStartup(StartupStatus.MAINTENANCE);

        assertFalse(lifecycle.acceptsSessions());
        lifecycle.disable();

        assertEquals(BootstrapLifecycle.State.DISABLED, lifecycle.state());
        assertFalse(lifecycle.acceptsSessions());
    }

    @Test
    void cannotStartTwiceOrCompleteAfterDisable() {
        BootstrapLifecycle lifecycle = new BootstrapLifecycle();
        lifecycle.beginStartup();

        assertThrows(IllegalStateException.class, lifecycle::beginStartup);
        lifecycle.disable();
        assertFalse(lifecycle.completeStartup(StartupStatus.READY));
    }
}
