package com.branz.mmorpg.acceptance;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;

public final class PhysicalLmbClientGameTest implements FabricClientGameTest {
    @Override
    public void runTest(ClientGameTestContext context) {
        String address = System.getProperty("branz.acceptance.server", "localhost:25565");
        context.runOnClient(
                client -> {
                    ServerData server = new ServerData("Branz acceptance", address, ServerData.Type.OTHER);
                    ConnectScreen.startConnecting(
                            client.gui.screen(),
                            client,
                            ServerAddress.parseString(address),
                            server,
                            false,
                            null);
                });
        context.waitFor(client -> client.level != null && client.player != null, 20 * 20);
        context.waitTicks(80);
        context.getInput().pressMouse(0);
        context.waitFor(client -> client.level == null && client.player == null, 20 * 20);
        context.setScreen(TitleScreen::new);
        context.waitForScreen(TitleScreen.class);
    }
}
