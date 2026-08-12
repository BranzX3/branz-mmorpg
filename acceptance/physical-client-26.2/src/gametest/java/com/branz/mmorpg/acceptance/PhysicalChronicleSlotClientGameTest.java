package com.branz.mmorpg.acceptance;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/** Section B6: a physical MMO value must never displace the permanent Chronicle in hotbar slot 9. */
final class PhysicalChronicleSlotClientGameTest {
    private static final int SERVER_HANDSHAKE_LEVEL = 7;
    private static final int PRIMARY_STAGE_HANDSHAKE_LEVEL = 8;
    private static final int SOURCE_HOTBAR_SLOT = 0;
    private static final int CHRONICLE_HOTBAR_SLOT = 8;
    private static final int INVENTORY_IMAGE_WIDTH = 176;
    private static final int INVENTORY_IMAGE_HEIGHT = 166;
    private static final double SLOT_HITBOX_SIZE = 16.0;
    private static final double SLOT_CENTER_OFFSET = SLOT_HITBOX_SIZE / 2.0;
    private static final String REJECTION_MESSAGE =
            "This MMO inventory action is not available in the physical-item slice yet.";
    private static final List<String> RECEIVED_GAME_MESSAGES = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean GAME_MESSAGE_LISTENER_REGISTERED = new AtomicBoolean();

    void runTest(ClientGameTestContext context) {
        registerGameMessageCapture();
        RECEIVED_GAME_MESSAGES.clear();
        String address = System.getProperty("branz.acceptance.server", "localhost:25565");
        connect(context, address);
        waitForStagedAuthority(context);
        System.out.println("PHYSICAL_AUTHORITY_CHRONICLE_STAGE_READY_CLIENT");

        String before =
                sendStatusAndCaptureTrainingBlade(
                        context, "PHYSICAL_AUTHORITY_CHRONICLE_STATUS_BEFORE_CLIENT");

        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        waitForChronicle(context);
        System.out.println("PHYSICAL_AUTHORITY_CHRONICLE_PRESENT_BEFORE_CLIENT");

        setHotbarCursor(context, SOURCE_HOTBAR_SLOT);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitFor(
                client -> {
                    if (!(client.gui.screen() instanceof InventoryScreen screen)
                            || client.player == null) {
                        return false;
                    }
                    Slot source =
                            findHotbarSlot(
                                    screen,
                                    client.player.getInventory(),
                                    SOURCE_HOTBAR_SLOT);
                    return source.getItem().isEmpty() && !screen.getMenu().getCarried().isEmpty();
                },
                20 * 5);
        System.out.println("PHYSICAL_AUTHORITY_CHRONICLE_PICKUP_OBSERVED_CLIENT");

        int rejectionMessageStart = RECEIVED_GAME_MESSAGES.size();
        setHotbarCursor(context, CHRONICLE_HOTBAR_SLOT);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        System.out.println("PHYSICAL_AUTHORITY_CHRONICLE_SLOT9_MOUSE_SENT_CLIENT");
        context.waitFor(client -> rejectionMessageSince(rejectionMessageStart), 20 * 5);
        System.out.println("PHYSICAL_AUTHORITY_CHRONICLE_REJECTION_MESSAGE_CLIENT");
        waitForChronicle(context);
        System.out.println("PHYSICAL_AUTHORITY_CHRONICLE_PRESENT_AFTER_REJECT_CLIENT");

        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(null);
        context.waitTicks(20);
        context.waitFor(
                client ->
                        client.player != null
                                && !client.player
                                        .getInventory()
                                        .getItem(SOURCE_HOTBAR_SLOT)
                                        .isEmpty()
                                && client.player
                                        .getInventory()
                                        .getItem(CHRONICLE_HOTBAR_SLOT)
                                        .is(Items.WRITTEN_BOOK),
                20 * 10);
        System.out.println("PHYSICAL_AUTHORITY_CHRONICLE_RECONCILED_CLIENT");

        String afterClose =
                sendStatusAndCaptureTrainingBlade(
                        context, "PHYSICAL_AUTHORITY_CHRONICLE_STATUS_AFTER_CLOSE_CLIENT");
        if (!before.equals(afterClose)) {
            throw new AssertionError(
                    "Chronicle-slot rejection changed authoritative Training Blade state: before="
                            + before
                            + " afterClose="
                            + afterClose);
        }
        System.out.println("PHYSICAL_AUTHORITY_CHRONICLE_AUTHORITY_STABLE_CLIENT");

        reconnect(context, address);
        context.waitFor(
                client ->
                        client.player != null
                                && !client.player
                                        .getInventory()
                                        .getItem(SOURCE_HOTBAR_SLOT)
                                        .isEmpty()
                                && client.player
                                        .getInventory()
                                        .getItem(CHRONICLE_HOTBAR_SLOT)
                                        .is(Items.WRITTEN_BOOK),
                20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_CHRONICLE_RECONNECT_PROJECTED_CLIENT");
        String afterReconnect =
                sendStatusAndCaptureTrainingBlade(
                        context, "PHYSICAL_AUTHORITY_CHRONICLE_STATUS_RECONNECT_CLIENT");
        if (!before.equals(afterReconnect)) {
            throw new AssertionError(
                    "Chronicle-slot rejection did not reconstruct the same authority after reconnect: before="
                            + before
                            + " afterReconnect="
                            + afterReconnect);
        }
        System.out.println("PHYSICAL_AUTHORITY_CHRONICLE_RECONNECT_STABLE_CLIENT");
        System.out.println("PHYSICAL_AUTHORITY_STATUS_COMMAND_SENT_CLIENT");

        context.waitFor(client -> client.level == null && client.player == null, 20 * 30);
        context.setScreen(TitleScreen::new);
        context.waitForScreen(TitleScreen.class);
    }

    private static void waitForStagedAuthority(ClientGameTestContext context) {
        context.waitFor(client -> client.level != null && client.player != null, 20 * 30);
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.experienceLevel == SERVER_HANDSHAKE_LEVEL,
                20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_SERVER_HANDSHAKE_CLIENT");
        context.waitFor(client -> client.gui.screen() == null, 20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_GAMEPLAY_SCREEN_READY_CLIENT");
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.experienceLevel == PRIMARY_STAGE_HANDSHAKE_LEVEL
                                && !client.player
                                        .getInventory()
                                        .getItem(SOURCE_HOTBAR_SLOT)
                                        .isEmpty()
                                && client.player
                                        .getInventory()
                                        .getItem(CHRONICLE_HOTBAR_SLOT)
                                        .is(Items.WRITTEN_BOOK),
                20 * 30);
    }

    private static void waitForChronicle(ClientGameTestContext context) {
        context.waitFor(
                client -> {
                    if (!(client.gui.screen() instanceof InventoryScreen screen)
                            || client.player == null) {
                        return false;
                    }
                    Slot chronicle =
                            findHotbarSlot(
                                    screen,
                                    client.player.getInventory(),
                                    CHRONICLE_HOTBAR_SLOT);
                    return chronicle.getItem().is(Items.WRITTEN_BOOK);
                },
                20 * 5);
    }

    private static void registerGameMessageCapture() {
        if (!GAME_MESSAGE_LISTENER_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ClientReceiveMessageEvents.GAME.register(
                (message, overlay) -> RECEIVED_GAME_MESSAGES.add(message.getString()));
    }

    private static boolean rejectionMessageSince(int firstMessage) {
        for (int index = RECEIVED_GAME_MESSAGES.size() - 1; index >= firstMessage; index--) {
            if (REJECTION_MESSAGE.equals(RECEIVED_GAME_MESSAGES.get(index))) {
                return true;
            }
        }
        return false;
    }

    private static String sendStatusAndCaptureTrainingBlade(
            ClientGameTestContext context, String marker) {
        int firstNewMessage = RECEIVED_GAME_MESSAGES.size();
        context.getInput().pressKey(options -> options.keyChat);
        context.waitFor(client -> client.gui.screen() instanceof ChatScreen, 20 * 5);
        context.getInput().typeChars("/mmo physical status");
        context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
        context.waitFor(client -> client.gui.screen() == null, 20 * 5);
        context.waitFor(client -> trainingBladeStatusSince(firstNewMessage) != null, 20 * 5);
        String status = trainingBladeStatusSince(firstNewMessage);
        if (status == null) {
            throw new AssertionError("Training Blade authority status disappeared after capture wait");
        }
        System.out.println(marker);
        return status;
    }

    private static String trainingBladeStatusSince(int firstMessage) {
        for (int index = RECEIVED_GAME_MESSAGES.size() - 1; index >= firstMessage; index--) {
            String message = RECEIVED_GAME_MESSAGES.get(index);
            if (message.startsWith("ITEM uuid=")
                    && message.contains(" def=weapon.training_blade ")) {
                return message;
            }
        }
        return null;
    }

    private static Slot findHotbarSlot(InventoryScreen screen, Object inventory, int hotbarSlot) {
        return screen.getMenu().slots.stream()
                .filter(
                        candidate ->
                                candidate.container == inventory
                                        && candidate.getContainerSlot() == hotbarSlot)
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "Hotbar slot not found in InventoryScreen: " + hotbarSlot));
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
                                    findHotbarSlot(
                                            screen, client.player.getInventory(), hotbarSlot);
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
                            return new double[] {rawX, rawY, guiX, guiY, slot.x, slot.y, left, top};
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
                            return new double[] {guiX, guiY};
                        });
        double slotLeft = target[6] + target[4];
        double slotTop = target[7] + target[5];
        if (observed[0] < slotLeft
                || observed[0] >= slotLeft + SLOT_HITBOX_SIZE
                || observed[1] < slotTop
                || observed[1] >= slotTop + SLOT_HITBOX_SIZE) {
            throw new AssertionError(
                    "Physical cursor did not land inside InventoryScreen hotbar slot " + hotbarSlot);
        }
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

    private static void connect(ClientGameTestContext context, String address) {
        context.runOnClient(
                client -> {
                    ServerData server =
                            new ServerData(
                                    "Branz Chronicle slot acceptance",
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
