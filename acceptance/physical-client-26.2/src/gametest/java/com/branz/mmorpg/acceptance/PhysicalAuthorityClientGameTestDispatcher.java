package com.branz.mmorpg.acceptance;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Keeps the established physical acceptance entrypoint stable while isolating the B5 broken-state flow. */
public final class PhysicalAuthorityClientGameTestDispatcher implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("branz.acceptance.physicalPrimaryBroken")) {
            new PhysicalBrokenWeaponClientGameTest().runTest(context);
            return;
        }
        new PhysicalAuthorityClientGameTest().runTest(context);
    }
}
