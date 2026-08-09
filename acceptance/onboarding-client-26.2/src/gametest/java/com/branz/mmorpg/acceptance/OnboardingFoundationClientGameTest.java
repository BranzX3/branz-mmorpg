package com.branz.mmorpg.acceptance;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.lwjgl.glfw.GLFW;

public final class OnboardingFoundationClientGameTest implements FabricClientGameTest {
    private static final int GREATSWORD_SLOT = 10;
    private static final int SCENE_HUB_MENU_SLOTS = 54 + 36;

    @Override
    public void runTest(ClientGameTestContext context) {
        String address = System.getProperty("branz.acceptance.server", "localhost:25565");
        connect(context, address);
        context.waitFor(client -> client.level != null && client.player != null, 20 * 30);
        context.waitFor(client -> client.gui.screen() instanceof AbstractContainerScreen<?>, 20 * 30);
        double[] cursor =
                context.computeOnClient(
                        client -> {
                            if (!(client.gui.screen()
                                    instanceof AbstractContainerScreen<?> screen)) {
                                throw new IllegalStateException(
                                        "starting foundation inventory did not open");
                            }
                            int menuSlots = screen.getMenu().slots.size();
                            if (menuSlots <= GREATSWORD_SLOT) {
                                throw new IllegalStateException(
                                        "starting foundation inventory is too small");
                            }
                            int containerSlots = menuSlots - 36;
                            if (containerSlots <= 0 || containerSlots % 9 != 0) {
                                throw new IllegalStateException(
                                        "starting foundation inventory has unexpected slot geometry: "
                                                + menuSlots);
                            }
                            int rows = containerSlots / 9;
                            int imageWidth = 176;
                            int imageHeight = 114 + rows * 18;
                            int left = (screen.width - imageWidth) / 2;
                            int top = (screen.height - imageHeight) / 2;
                            int column = GREATSWORD_SLOT % 9;
                            int row = GREATSWORD_SLOT / 9;
                            double guiX = left + 8 + column * 18 + 8;
                            double guiY = top + 18 + row * 18 + 8;
                            double rawX =
                                    guiX
                                            * client.getWindow().getScreenWidth()
                                            / (double) screen.width;
                            double rawY =
                                    guiY
                                            * client.getWindow().getScreenHeight()
                                            / (double) screen.height;
                            return new double[] {rawX, rawY};
                        });
        context.getInput().setCursorPos(cursor[0], cursor[1]);
        context.waitTick();
        context.getInput().pressMouse(0);
        context.waitFor(client -> client.level == null && client.player == null, 20 * 30);

        connect(context, address);
        context.waitFor(client -> client.level != null && client.player != null, 20 * 30);
        context.waitTicks(5);
        context.runOnClient(
                client -> {
                    if (client.gui.screen() instanceof AbstractContainerScreen<?>) {
                        throw new IllegalStateException(
                                "foundation inventory reopened after durable reconnect");
                    }
                });

        // Exercise the normal-player path instead of calling Bukkit or Minecraft internals:
        // physically select hotbar slot 9, then physically right-click the Chronicle.
        context.getInput().pressKey(GLFW.GLFW_KEY_9);
        context.getInput().pressMouse(1);
        context.waitFor(
                client -> client.gui.screen() instanceof AbstractContainerScreen<?>, 20 * 10);
        context.runOnClient(
                client -> {
                    if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
                        throw new IllegalStateException("Chronicle RMB did not open a container");
                    }
                    int menuSlots = screen.getMenu().slots.size();
                    if (menuSlots != SCENE_HUB_MENU_SLOTS) {
                        throw new IllegalStateException(
                                "Chronicle RMB opened an unexpected menu: " + menuSlots + " slots");
                    }
                });

        context.waitFor(client -> client.level == null && client.player == null, 20 * 30);
        context.setScreen(TitleScreen::new);
        context.waitForScreen(TitleScreen.class);
    }

    private static void connect(ClientGameTestContext context, String address) {
        context.runOnClient(
                client -> {
                    ServerData server =
                            new ServerData("Branz onboarding acceptance", address, ServerData.Type.OTHER);
                    ConnectScreen.startConnecting(
                            client.gui.screen(),
                            client,
                            ServerAddress.parseString(address),
                            server,
                            false,
                            null);
                });
    }
}
