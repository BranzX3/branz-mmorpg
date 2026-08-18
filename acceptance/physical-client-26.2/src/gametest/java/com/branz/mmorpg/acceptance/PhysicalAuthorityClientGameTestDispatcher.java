package com.branz.mmorpg.acceptance;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/** Keeps established physical acceptance modes isolated behind one stable Fabric entrypoint. */
public final class PhysicalAuthorityClientGameTestDispatcher implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        if (Boolean.getBoolean("branz.acceptance.physicalShieldD13")) {
            new PhysicalShieldD13ClientGameTest().runTest(context);
            return;
        }
        if (Boolean.getBoolean("branz.acceptance.physicalConsumableC4Restart")) {
            new PhysicalConsumableC4ClientGameTest().runRestartTest(context);
            return;
        }
        if (Boolean.getBoolean("branz.acceptance.physicalConsumableC4")) {
            new PhysicalConsumableC4ClientGameTest().runTest(context);
            System.out.println("PHYSICAL_AUTHORITY_SERVER_HANDSHAKE_CLIENT");
            System.out.println("PHYSICAL_AUTHORITY_STATUS_COMMAND_SENT_CLIENT");
            return;
        }
        if (Boolean.getBoolean("branz.acceptance.physicalConsumableUse")) {
            new PhysicalConsumableUseClientGameTest().runTest(context);
            System.out.println("PHYSICAL_AUTHORITY_SERVER_HANDSHAKE_CLIENT");
            System.out.println("PHYSICAL_AUTHORITY_STATUS_COMMAND_SENT_CLIENT");
            return;
        }
        if (Boolean.getBoolean("branz.acceptance.physicalConsumableLot")) {
            new PhysicalConsumableLotClientGameTest().runTest(context);
            return;
        }
        if (Boolean.getBoolean("branz.acceptance.physicalBrokenRestart")) {
            new PhysicalBrokenWeaponRestartClientGameTest().runTest(context);
            return;
        }
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
