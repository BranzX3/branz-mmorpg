package com.branz.mmorpg.acceptance;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

public final class OnboardingFoundationClientGameTest implements FabricClientGameTest {
    private static final int GREATSWORD_SLOT = 10;
    private static final int CHRONICLE_HOTBAR_SLOT = 8;
    private static final int SCENE_HUB_MENU_SLOTS = 54 + 36;
    private static final int BUFFER_CONFIRMED_LEVEL = 7;

    @Override
    public void runTest(ClientGameTestContext context) {
        String address = System.getProperty("branz.acceptance.server", "localhost:25565");
        String mode = System.getProperty("branz.acceptance.mode", "defense");
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
        context.waitFor(
                client ->
                        client.player != null
                                && client.player
                                        .getInventory()
                                        .getItem(CHRONICLE_HOTBAR_SLOT)
                                        .is(Items.WRITTEN_BOOK),
                20 * 10);
        System.out.println("ONBOARDING_CHRONICLE_VISIBLE_CLIENT");
        context.runOnClient(
                client -> {
                    if (client.gui.screen() instanceof AbstractContainerScreen<?>) {
                        throw new IllegalStateException(
                                "foundation inventory reopened after durable reconnect");
                    }
                });

        if (mode.equals("directional-buffer")) {
            runDirectionalBuffer(context);
            return;
        }
        if (!mode.equals("defense")) {
            throw new IllegalStateException("unsupported acceptance mode: " + mode);
        }
        runDefense(context);
    }

    private static void runDirectionalBuffer(ClientGameTestContext context) {
        context.waitTicks(5);
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        context.waitFor(
                client ->
                        client.player != null
                                && !client.player.getMainHandItem().isEmpty()
                                && !client.player.getMainHandItem().is(Items.WRITTEN_BOOK),
                20 * 5);
        System.out.println("DIRECTIONAL_BUFFER_STARTER_WEAPON_SELECTED_CLIENT");

        context.waitTicks(20);
        context.getInput().holdKey(GLFW.GLFW_KEY_W);
        context.waitTick();
        context.getInput().pressMouse(0);
        System.out.println("DIRECTIONAL_BUFFER_FORWARD_PRIMARY_INPUT_CLIENT");
        context.waitTick();
        context.getInput().releaseKey(GLFW.GLFW_KEY_W);

        // Headless client ticks can advance much slower than Paper ticks. This delay places the
        // second physical LMB at the authored PRIMARY_2 queue window on the server.
        context.waitTicks(4);
        context.getInput().pressMouse(0);
        System.out.println("DIRECTIONAL_BUFFER_FOLLOWUP_BUFFER_INPUT_CLIENT");

        // The acceptance-only server probe replicates this vanilla XP level only after it has
        // observed BUFFERED PRIMARY_2. Wait for that server-confirmed state, then issue another real
        // physical LMB so the refresh assertion does not depend on guessing headless tick ratios.
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.experienceLevel == BUFFER_CONFIRMED_LEVEL,
                20 * 5);
        System.out.println("DIRECTIONAL_BUFFER_BUFFER_CONFIRMED_CLIENT");
        context.getInput().pressMouse(0);
        System.out.println("DIRECTIONAL_BUFFER_FOLLOWUP_REFRESH_INPUT_CLIENT");

        finishAfterServerShutdown(context);
    }

    private static void runDefense(ClientGameTestContext context) {
        context.waitTicks(20);
        context.getInput().pressKey(GLFW.GLFW_KEY_9);
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.getMainHandItem().is(Items.WRITTEN_BOOK),
                20 * 2);
        System.out.println("ONBOARDING_CHRONICLE_SELECTED_CLIENT");
        context.waitTicks(10);
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
        System.out.println("ONBOARDING_CHRONICLE_SCENE_CLIENT_PASS");

        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() == null, 20 * 5);
        context.waitTicks(5);
        context.getInput().pressKey(GLFW.GLFW_KEY_1);
        context.waitFor(
                client ->
                        client.player != null
                                && !client.player.getMainHandItem().isEmpty()
                                && !client.player.getMainHandItem().is(Items.WRITTEN_BOOK),
                20 * 5);
        System.out.println("ONBOARDING_STARTER_WEAPON_SELECTED_CLIENT");
        context.waitTicks(20);
        context.getInput().pressMouse(0);
        System.out.println("ONBOARDING_FIRST_COMBAT_INPUT_CLIENT");

        context.waitTicks(12);
        context.getInput().holdKey(GLFW.GLFW_KEY_W);
        context.waitTick();
        context.getInput().holdKey(GLFW.GLFW_KEY_LEFT_SHIFT);
        context.waitTicks(3);
        context.getInput().releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT);
        context.getInput().releaseKey(GLFW.GLFW_KEY_W);
        System.out.println("ONBOARDING_DIRECTIONAL_DODGE_INPUT_CLIENT");

        finishAfterServerShutdown(context);
    }

    private static void finishAfterServerShutdown(ClientGameTestContext context) {
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
