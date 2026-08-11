package com.branz.mmorpg.acceptance;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.inventory.Slot;
import org.lwjgl.glfw.GLFW;

public final class PhysicalAuthorityClientGameTest implements FabricClientGameTest {
    private static final int SERVER_HANDSHAKE_LEVEL = 7;
    private static final int PRIMARY_STAGE_HANDSHAKE_LEVEL = 8;
    private static final int HOTBAR_MOVE_ONE_HANDSHAKE_LEVEL = 9;
    private static final int HOTBAR_MOVE_TWO_HANDSHAKE_LEVEL = 10;
    private static final int INVENTORY_IMAGE_WIDTH = 176;
    private static final int INVENTORY_IMAGE_HEIGHT = 166;
    private static final double SLOT_HITBOX_SIZE = 16.0;
    private static final double SLOT_CENTER_OFFSET = SLOT_HITBOX_SIZE / 2.0;

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
        double[] target =
                context.computeOnClient(
                        client -> {
                            if (!(client.gui.screen() instanceof InventoryScreen screen)
                                    || client.player == null) {
                                throw new AssertionError(
                                        "InventoryScreen and player must be present for hotbar input");
                            }
                            Slot slot =
                                    screen.getMenu().slots.stream()
                                            .filter(
                                                    candidate ->
                                                            candidate.container
                                                                            == client.player
                                                                                    .getInventory()
                                                                    && candidate.getContainerSlot()
                                                                            == hotbarSlot)
                                            .findFirst()
                                            .orElseThrow(
                                                    () ->
                                                            new AssertionError(
                                                                    "Hotbar slot not found in InventoryScreen: "
                                                                            + hotbarSlot));
                            double guiWidth = client.getWindow().getGuiScaledWidth();
                            double guiHeight = client.getWindow().getGuiScaledHeight();
                            double screenWidth = client.getWindow().getScreenWidth();
                            double screenHeight = client.getWindow().getScreenHeight();
                            double left = (guiWidth - INVENTORY_IMAGE_WIDTH) / 2.0;
                            double top = (guiHeight - INVENTORY_IMAGE_HEIGHT) / 2.0;
                            double guiX = left + slot.x + SLOT_CENTER_OFFSET;
                            double guiY = top + slot.y + SLOT_CENTER_OFFSET;
                            double rawX = guiX * screenWidth / guiWidth;
                            double rawY = guiY * screenHeight / guiHeight;
                            return new double[] {
                                rawX,
                                rawY,
                                guiX,
                                guiY,
                                slot.x,
                                slot.y,
                                left,
                                top,
                                screenWidth,
                                screenHeight,
                                guiWidth,
                                guiHeight
                            };
                        });
        context.getInput().setCursorPos(target[0], target[1]);
        double[] observed =
                context.computeOnClient(
                        client -> {
                            double rawX = client.mouseHandler.xpos();
                            double rawY = client.mouseHandler.ypos();
                            double guiX =
                                    rawX
                                            * client.getWindow().getGuiScaledWidth()
                                            / client.getWindow().getScreenWidth();
                            double guiY =
                                    rawY
                                            * client.getWindow().getGuiScaledHeight()
                                            / client.getWindow().getScreenHeight();
                            return new double[] {rawX, rawY, guiX, guiY};
                        });
        double slotLeft = target[6] + target[4];
        double slotTop = target[7] + target[5];
        boolean overTarget =
                observed[2] >= slotLeft
                        && observed[2] < slotLeft + SLOT_HITBOX_SIZE
                        && observed[3] >= slotTop
                        && observed[3] < slotTop + SLOT_HITBOX_SIZE;
        System.out.printf(
                "PHYSICAL_AUTHORITY_HOTBAR_CURSOR_SLOT_CLIENT slot=%d raw=%.2f,%.2f gui=%.2f,%.2f target=%.2f,%.2f slotOrigin=%.2f,%.2f screen=%.0fx%.0f guiSize=%.0fx%.0f over=%s%n",
                hotbarSlot,
                observed[0],
                observed[1],
                observed[2],
                observed[3],
                target[2],
                target[3],
                slotLeft,
                slotTop,
                target[8],
                target[9],
                target[10],
                target[11],
                overTarget);
        if (!overTarget) {
            throw new AssertionError(
                    "Physical cursor did not land inside InventoryScreen hotbar slot " + hotbarSlot);
        }
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
