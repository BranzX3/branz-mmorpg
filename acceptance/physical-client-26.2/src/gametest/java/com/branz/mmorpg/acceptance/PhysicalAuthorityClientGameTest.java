package com.branz.mmorpg.acceptance;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.lwjgl.glfw.GLFW;

public final class PhysicalAuthorityClientGameTest implements FabricClientGameTest {
    private static final int SERVER_HANDSHAKE_LEVEL = 7;
    private static final int PRIMARY_STAGE_HANDSHAKE_LEVEL = 8;
    private static final int HOTBAR_MOVE_ONE_HANDSHAKE_LEVEL = 9;
    private static final int HOTBAR_MOVE_TWO_HANDSHAKE_LEVEL = 10;
    private static final int INVENTORY_IMAGE_WIDTH = 176;
    private static final int INVENTORY_IMAGE_HEIGHT = 166;
    private static final int HOTBAR_FIRST_SLOT_CENTER_X = 16;
    private static final int HOTBAR_SLOT_STEP = 18;
    private static final int HOTBAR_CENTER_Y = 150;

    @Override
    public void runTest(ClientGameTestContext context) {
        String address = System.getProperty("branz.acceptance.server", "localhost:25565");
        boolean primaryInputAcceptance =
                Boolean.getBoolean("branz.acceptance.physicalPrimaryInput");
        boolean hotbarAcceptance = Boolean.getBoolean("branz.acceptance.physicalHotbar");
        connect(context, address);
        context.waitFor(client -> client.level != null && client.player != null, 20 * 30);
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.experienceLevel == SERVER_HANDSHAKE_LEVEL,
                20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_SERVER_HANDSHAKE_CLIENT");

        context.waitFor(client -> client.gui.screen() == null, 20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_GAMEPLAY_SCREEN_READY_CLIENT");
        if (hotbarAcceptance) {
            runHotbarAcceptance(context, address);
        } else {
            if (primaryInputAcceptance) {
                context.waitFor(
                        client ->
                                client.player != null
                                        && client.player.experienceLevel
                                                == PRIMARY_STAGE_HANDSHAKE_LEVEL,
                        20 * 30);
                context.waitFor(
                        client ->
                                client.player != null && !client.player.getMainHandItem().isEmpty(),
                        20 * 30);
                System.out.println("PHYSICAL_AUTHORITY_PRIMARY_PROJECTION_READY_CLIENT");
                context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                System.out.println("PHYSICAL_AUTHORITY_PRIMARY_MOUSE_SENT_CLIENT");
                context.waitTicks(5);
            }
            sendStatus(context, "PHYSICAL_AUTHORITY_STATUS_COMMAND_SENT_CLIENT");
        }

        context.waitFor(client -> client.level == null && client.player == null, 20 * 30);
        context.setScreen(TitleScreen::new);
        context.waitForScreen(TitleScreen.class);
    }

    private static void runHotbarAcceptance(ClientGameTestContext context, String address) {
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.experienceLevel == PRIMARY_STAGE_HANDSHAKE_LEVEL,
                20 * 30);
        context.waitFor(
                client ->
                        client.player != null
                                && !client.player.getInventory().getItem(0).isEmpty(),
                20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_HOTBAR_PROJECTION_READY_CLIENT");
        sendStatus(context, "PHYSICAL_AUTHORITY_HOTBAR_STATUS_INITIAL_CLIENT");

        moveHotbarItem(context, 0, 1);
        System.out.println("PHYSICAL_AUTHORITY_HOTBAR_MOVE1_MOUSE_SENT_CLIENT");
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.experienceLevel
                                        == HOTBAR_MOVE_ONE_HANDSHAKE_LEVEL,
                20 * 30);
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.getInventory().getItem(0).isEmpty()
                                && !client.player.getInventory().getItem(1).isEmpty(),
                20 * 30);
        closeInventory(context);
        sendStatus(context, "PHYSICAL_AUTHORITY_HOTBAR_STATUS_AFTER_MOVE1_CLIENT");

        reconnect(context, address);
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.getInventory().getItem(0).isEmpty()
                                && !client.player.getInventory().getItem(1).isEmpty(),
                20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_HOTBAR_RECONNECT1_PROJECTED_CLIENT");
        sendStatus(context, "PHYSICAL_AUTHORITY_HOTBAR_STATUS_RECONNECT1_CLIENT");

        moveHotbarItem(context, 1, 2);
        System.out.println("PHYSICAL_AUTHORITY_HOTBAR_MOVE2_MOUSE_SENT_CLIENT");
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.experienceLevel
                                        == HOTBAR_MOVE_TWO_HANDSHAKE_LEVEL,
                20 * 30);
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.getInventory().getItem(1).isEmpty()
                                && !client.player.getInventory().getItem(2).isEmpty(),
                20 * 30);
        closeInventory(context);
        sendStatus(context, "PHYSICAL_AUTHORITY_HOTBAR_STATUS_AFTER_MOVE2_CLIENT");

        reconnect(context, address);
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.getInventory().getItem(1).isEmpty()
                                && !client.player.getInventory().getItem(2).isEmpty(),
                20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_HOTBAR_RECONNECT2_PROJECTED_CLIENT");
        sendStatus(context, "PHYSICAL_AUTHORITY_HOTBAR_STATUS_RECONNECT2_CLIENT");
        System.out.println("PHYSICAL_AUTHORITY_HOTBAR_SEQUENCE_COMPLETE_CLIENT");
    }

    private static void moveHotbarItem(
            ClientGameTestContext context, int sourceSlot, int destinationSlot) {
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        setHotbarCursor(context, sourceSlot);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        setHotbarCursor(context, destinationSlot);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void setHotbarCursor(ClientGameTestContext context, int hotbarSlot) {
        double[] position =
                context.computeOnClient(
                        client -> {
                            double guiWidth = client.getWindow().getGuiScaledWidth();
                            double guiHeight = client.getWindow().getGuiScaledHeight();
                            double left = (guiWidth - INVENTORY_IMAGE_WIDTH) / 2.0;
                            double top = (guiHeight - INVENTORY_IMAGE_HEIGHT) / 2.0;
                            double guiX = left + HOTBAR_FIRST_SLOT_CENTER_X + hotbarSlot * HOTBAR_SLOT_STEP;
                            double guiY = top + HOTBAR_CENTER_Y;
                            double rawX =
                                    guiX * client.getWindow().getScreenWidth() / guiWidth;
                            double rawY =
                                    guiY * client.getWindow().getScreenHeight() / guiHeight;
                            return new double[] {rawX, rawY};
                        });
        context.getInput().setCursorPos(position[0], position[1]);
    }

    private static void closeInventory(ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(null);
    }

    private static void reconnect(ClientGameTestContext context, String address) {
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() != null, 20 * 5);
        context.clickScreenButton("menu.disconnect");
        context.waitFor(client -> client.level == null && client.player == null, 20 * 15);
        connect(context, address);
        context.waitFor(client -> client.level != null && client.player != null, 20 * 30);
        context.waitFor(client -> client.gui.screen() == null, 20 * 30);
    }

    private static void sendStatus(ClientGameTestContext context, String marker) {
        context.getInput().pressKey(options -> options.keyChat);
        context.waitFor(client -> client.gui.screen() instanceof ChatScreen, 20 * 5);
        context.getInput().typeChars("/mmo physical status");
        context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
        context.waitFor(client -> client.gui.screen() == null, 20 * 5);
        context.waitTicks(10);
        System.out.println(marker);
    }

    private static void connect(ClientGameTestContext context, String address) {
        context.runOnClient(
                client -> {
                    ServerData server =
                            new ServerData(
                                    "Branz physical authority acceptance",
                                    address,
                                    ServerData.Type.OTHER);
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
