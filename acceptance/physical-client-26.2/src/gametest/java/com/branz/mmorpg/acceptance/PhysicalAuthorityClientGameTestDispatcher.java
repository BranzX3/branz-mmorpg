package com.branz.mmorpg.acceptance;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Keeps established physical acceptance modes isolated behind one stable Fabric entrypoint. */
public final class PhysicalAuthorityClientGameTestDispatcher implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("branz.acceptance.physicalChronicleSlot")) {
            new PhysicalChronicleSlotClientGameTest().runTest(context);
            return;
        }
        if (Boolean.getBoolean("branz.acceptance.physicalPrimaryBroken")) {
            new PhysicalBrokenWeaponClientGameTest().runTest(context);
            return;
        }
        new PhysicalAuthorityClientGameTest().runTest(context);
    }
}
