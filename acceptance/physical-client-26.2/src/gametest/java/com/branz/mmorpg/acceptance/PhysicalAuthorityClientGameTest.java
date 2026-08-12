package com.branz.mmorpg.acceptance;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
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
    private static final int FIRST_GAMEPLAY_HOTBAR_SLOT = 0;
    private static final int LAST_GAMEPLAY_HOTBAR_SLOT = 7;
    private static final double SLOT_HITBOX_SIZE = 16.0;
    private static final double SLOT_CENTER_OFFSET = SLOT_HITBOX_SIZE / 2.0;
    private static final float MISS_AIM_MAX_PITCH = -85.0F;
    private static final List<String> RECEIVED_GAME_MESSAGES = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean GAME_MESSAGE_LISTENER_REGISTERED = new AtomicBoolean();

    @Override
    public void runTest(ClientGameTestContext context) {
        registerGameMessageCapture();
        RECEIVED_GAME_MESSAGES.clear();
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
                String before =
                        sendStatusAndCaptureTrainingBlade(
                                context, "PHYSICAL_AUTHORITY_PRIMARY_MISS_STATUS_BEFORE_CLIENT");
                aimSkyForMiss(context);
                context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
                System.out.println("PHYSICAL_AUTHORITY_PRIMARY_MOUSE_SENT_CLIENT");
                context.waitTicks(60);
                System.out.println("PHYSICAL_AUTHORITY_PRIMARY_MISS_SETTLED_CLIENT");
                String after =
                        sendStatusAndCaptureTrainingBlade(
                                context, "PHYSICAL_AUTHORITY_PRIMARY_MISS_STATUS_AFTER_CLIENT");
                if (!before.equals(after)) {
                    throw new AssertionError(
                            "Authoritative Training Blade changed across deterministic miss: before="
                                    + before
                                    + " after="
                                    + after);
                }
                System.out.println("PHYSICAL_AUTHORITY_PRIMARY_MISS_AUTHORITY_STABLE_CLIENT");
                System.out.println("PHYSICAL_AUTHORITY_STATUS_COMMAND_SENT_CLIENT");
            } else {
                sendStatus(context, "PHYSICAL_AUTHORITY_STATUS_COMMAND_SENT_CLIENT");
            }
        }

        context.waitFor(client -> client.level == null && client.player == null, 20 * 30);
        context.setScreen(TitleScreen::new);
        context.waitForScreen(TitleScreen.class);
    }

    private static void registerGameMessageCapture() {
        if (!GAME_MESSAGE_LISTENER_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ClientReceiveMessageEvents.GAME.register(
                (message, overlay) -> RECEIVED_GAME_MESSAGES.add(message.getString()));
    }

    private static String sendStatusAndCaptureTrainingBlade(
            ClientGameTestContext context, String marker) {
        int firstNewMessage = RECEIVED_GAME_MESSAGES.size();
        context.getInput().pressKey(options -> options.keyChat);
        context.waitFor(client -> client.gui.screen() instanceof ChatScreen, 20 * 5);
        context.getInput().typeChars("/mmo physical status");
        context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
        context.waitFor(client -> client.gui.screen() == null, 20 * 5);
        context.waitFor(
                client -> trainingBladeStatusSince(firstNewMessage) != null,
                20 * 5);
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

    private static void aimSkyForMiss(ClientGameTestContext context) {
        context.getInput().moveCursor(0.0, -10000.0);
        context.waitFor(
                client -> client.player != null && client.player.getXRot() <= MISS_AIM_MAX_PITCH,
                20 * 5);
        float pitch =
                context.computeOnClient(
                        client -> {
                            if (client.player == null) {
                                throw new AssertionError("Player disappeared while staging miss aim");
                            }
                            return client.player.getXRot();
                        });
        System.out.printf("PHYSICAL_AUTHORITY_PRIMARY_MISS_AIM_SKY_CLIENT pitch=%.2f%n", pitch);
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

        int moveOneDestination = moveHotbarItem(context, 0);
        System.out.println(
                "PHYSICAL_AUTHORITY_HOTBAR_MOVE1_MOUSE_SENT_CLIENT source=0 destination="
                        + moveOneDestination);
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.experienceLevel
                                        == HOTBAR_MOVE_ONE_HANDSHAKE_LEVEL,
                20 * 30);
        context.waitFor(
                client ->
                        client.player != null
                                && !client.player
                                        .getInventory()
                                        .getItem(moveOneDestination)
                                        .isEmpty(),
                20 * 30);
        closeInventory(context);
        sendStatus(context, "PHYSICAL_AUTHORITY_HOTBAR_STATUS_AFTER_MOVE1_CLIENT");

        reconnect(context, address);
        context.waitFor(
                client ->
                        client.player != null
                                && !client.player
                                        .getInventory()
                                        .getItem(moveOneDestination)
                                        .isEmpty(),
                20 * 30);
        System.out.println(
                "PHYSICAL_AUTHORITY_HOTBAR_RECONNECT1_PROJECTED_CLIENT slot="
                        + moveOneDestination);
        sendStatus(context, "PHYSICAL_AUTHORITY_HOTBAR_STATUS_RECONNECT1_CLIENT");

        int moveTwoDestination = moveHotbarItem(context, moveOneDestination);
        System.out.println(
                "PHYSICAL_AUTHORITY_HOTBAR_MOVE2_MOUSE_SENT_CLIENT source="
                        + moveOneDestination
                        + " destination="
                        + moveTwoDestination);
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.experienceLevel
                                        == HOTBAR_MOVE_TWO_HANDSHAKE_LEVEL,
                20 * 30);
        context.waitFor(
                client ->
                        client.player != null
                                && !client.player
                                        .getInventory()
                                        .getItem(moveTwoDestination)
                                        .isEmpty(),
                20 * 30);
        closeInventory(context);
        sendStatus(context, "PHYSICAL_AUTHORITY_HOTBAR_STATUS_AFTER_MOVE2_CLIENT");

        reconnect(context, address);
        context.waitFor(
                client ->
                        client.player != null
                                && !client.player
                                        .getInventory()
                                        .getItem(moveTwoDestination)
                                        .isEmpty(),
                20 * 30);
        System.out.println(
                "PHYSICAL_AUTHORITY_HOTBAR_RECONNECT2_PROJECTED_CLIENT slot="
                        + moveTwoDestination);
        sendStatus(context, "PHYSICAL_AUTHORITY_HOTBAR_STATUS_RECONNECT2_CLIENT");
        System.out.println("PHYSICAL_AUTHORITY_HOTBAR_SEQUENCE_COMPLETE_CLIENT");
    }

    private static int moveHotbarItem(ClientGameTestContext context, int sourceSlot) {
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        int destinationSlot = selectEmptyGameplayHotbarDestination(context, sourceSlot);
        System.out.println(
                "PHYSICAL_AUTHORITY_HOTBAR_DESTINATION_SELECTED_CLIENT source="
                        + sourceSlot
                        + " destination="
                        + destinationSlot);

        setHotbarCursor(context, sourceSlot);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitFor(
                client -> {
                    if (!(client.gui.screen() instanceof InventoryScreen screen)
                            || client.player == null) {
                        return false;
                    }
                    Slot source =
                            findHotbarSlot(screen, client.player.getInventory(), sourceSlot);
                    return source.getItem().isEmpty() && !screen.getMenu().getCarried().isEmpty();
                },
                20 * 5);
        System.out.println(
                "PHYSICAL_AUTHORITY_HOTBAR_PICKUP_OBSERVED_CLIENT source=" + sourceSlot);

        setHotbarCursor(context, destinationSlot);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitFor(
                client -> {
                    if (!(client.gui.screen() instanceof InventoryScreen screen)
                            || client.player == null) {
                        return false;
                    }
                    Slot destination =
                            findHotbarSlot(screen, client.player.getInventory(), destinationSlot);
                    return !destination.getItem().isEmpty()
                            && screen.getMenu().getCarried().isEmpty();
                },
                20 * 5);
        System.out.println(
                "PHYSICAL_AUTHORITY_HOTBAR_PLACE_OBSERVED_CLIENT destination="
                        + destinationSlot);
        context.waitTicks(2);
        return destinationSlot;
    }

    private static int selectEmptyGameplayHotbarDestination(
            ClientGameTestContext context, int sourceSlot) {
        return context.computeOnClient(
                client -> {
                    if (!(client.gui.screen() instanceof InventoryScreen screen)
                            || client.player == null) {
                        throw new AssertionError(
                                "InventoryScreen and player must be present for destination selection");
                    }
                    for (int slot = LAST_GAMEPLAY_HOTBAR_SLOT;
                            slot >= FIRST_GAMEPLAY_HOTBAR_SLOT;
                            slot--) {
                        if (slot == sourceSlot) {
                            continue;
                        }
                        Slot candidate =
                                findHotbarSlot(screen, client.player.getInventory(), slot);
                        if (candidate.getItem().isEmpty()) {
                            return slot;
                        }
                    }
                    throw new AssertionError(
                            "No empty gameplay hotbar destination is available for physical acceptance");
                });
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
