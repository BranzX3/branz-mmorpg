package com.branz.mmorpg.acceptance;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/** Section C1-C2: grant one whole consumable lot, then physically move it into hotbar and reconnect. */
final class PhysicalConsumableLotClientGameTest {
    private static final int SERVER_HANDSHAKE_LEVEL = 7;
    private static final int SOURCE_STORAGE_SLOT = 9;
    private static final int TARGET_HOTBAR_SLOT = 7;
    private static final int CHRONICLE_HOTBAR_SLOT = 8;
    private static final int EXPECTED_QUANTITY = 64;
    private static final int CONNECTION_TIMEOUT_TICKS = 20 * 60;
    private static final int INVENTORY_IMAGE_WIDTH = 176;
    private static final int INVENTORY_IMAGE_HEIGHT = 166;
    private static final int DEV_CONTAINER_IMAGE_WIDTH = 176;
    private static final int DEV_CONTAINER_IMAGE_HEIGHT = 222;
    private static final double SLOT_HITBOX_SIZE = 16.0;
    private static final double SLOT_CENTER_OFFSET = SLOT_HITBOX_SIZE / 2.0;
    private static final String DEFINITION_ID = "consumable.training_body_tonic";
    private static final String DEV_MODULE_NAME = "Persisted Test Item";
    private static final String GRANT_LABEL_PREFIX = DEFINITION_ID + " (Shift = lot x64)";
    private static final Pattern TONIC_STATUS =
            Pattern.compile(
                    "^LOT uuid=([0-9a-fA-F-]{36}) def=consumable\\.training_body_tonic loc=([^ ]+) ver=(\\d+) qty=(\\d+) tx=([0-9a-fA-F-]{36}) content=(\\S+)$");
    private static final List<String> RECEIVED_GAME_MESSAGES = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean GAME_MESSAGE_LISTENER_REGISTERED = new AtomicBoolean();

    void runTest(ClientGameTestContext context) {
        registerGameMessageCapture();
        RECEIVED_GAME_MESSAGES.clear();
        String address = System.getProperty("branz.acceptance.server", "localhost:25565");
        connect(context, address);
        waitForServerHandshake(context);
        prepareMainInventoryGrantSlot(context);

        openDevHub(context);
        clickMenuEntry(context, DEV_MODULE_NAME, false);
        context.waitFor(client -> menuContains(client.gui.screen(), GRANT_LABEL_PREFIX), 20 * 10);
        clickMenuEntry(context, GRANT_LABEL_PREFIX, true);
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.getInventory().getItem(SOURCE_STORAGE_SLOT).getCount()
                                        == EXPECTED_QUANTITY,
                20 * 20);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(null);
        System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_STAGE_READY_CLIENT");

        freeTargetHotbarSlot(context);
        System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_TARGET_READY_CLIENT");

        String beforeLine =
                sendStatusAndCaptureTonic(
                        context, "PHYSICAL_AUTHORITY_CONSUMABLE_STATUS_BEFORE_CLIENT");
        LotAuthority before = parseTonicStatus(beforeLine);
        if (!"CHARACTER_INVENTORY/slot:9".equals(before.location())
                || before.quantity() != EXPECTED_QUANTITY) {
            throw new AssertionError("C1 tonic was not staged as one x64 lot in main inventory: " + before);
        }

        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        setInventoryCursor(context, SOURCE_STORAGE_SLOT);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitFor(
                client -> {
                    if (!(client.gui.screen() instanceof InventoryScreen screen)
                            || client.player == null) {
                        return false;
                    }
                    Slot source = findPlayerSlot(screen, client.player.getInventory(), SOURCE_STORAGE_SLOT);
                    return source.getItem().isEmpty()
                            && screen.getMenu().getCarried().getCount() == EXPECTED_QUANTITY;
                },
                20 * 5);
        System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_PICKUP_OBSERVED_CLIENT");

        setInventoryCursor(context, TARGET_HOTBAR_SLOT);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_PLACE_MOUSE_SENT_CLIENT");
        context.waitFor(
                client -> {
                    if (!(client.gui.screen() instanceof InventoryScreen screen)
                            || client.player == null) {
                        return false;
                    }
                    Slot target = findPlayerSlot(screen, client.player.getInventory(), TARGET_HOTBAR_SLOT);
                    return screen.getMenu().getCarried().isEmpty()
                            && target.getItem().getCount() == EXPECTED_QUANTITY;
                },
                20 * 10);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(null);

        String movedLine =
                sendStatusAndCaptureTonic(
                        context, "PHYSICAL_AUTHORITY_CONSUMABLE_STATUS_AFTER_MOVE_CLIENT");
        LotAuthority moved = parseTonicStatus(movedLine);
        if (!before.uuid().equals(moved.uuid())
                || !before.contentVersion().equals(moved.contentVersion())
                || moved.quantity() != before.quantity()
                || moved.version() != before.version() + 1
                || before.transactionId().equals(moved.transactionId())
                || !"CHARACTER_INVENTORY/slot:7".equals(moved.location())) {
            throw new AssertionError(
                    "C2 whole-lot move did not preserve quantity/identity with exactly one authority move: before="
                            + before
                            + " moved="
                            + moved);
        }
        System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_MOVED_ONCE_CLIENT");

        reconnect(context, address);
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.getInventory().getItem(TARGET_HOTBAR_SLOT).getCount()
                                        == EXPECTED_QUANTITY
                                && client.player
                                        .getInventory()
                                        .getItem(CHRONICLE_HOTBAR_SLOT)
                                        .is(Items.WRITTEN_BOOK),
                20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_RECONNECT_PROJECTED_CLIENT");
        String reconnectLine =
                sendStatusAndCaptureTonic(
                        context, "PHYSICAL_AUTHORITY_CONSUMABLE_STATUS_RECONNECT_CLIENT");
        if (!movedLine.equals(reconnectLine)) {
            throw new AssertionError(
                    "C2 reconnect did not reconstruct byte-stable lot authority: moved="
                            + movedLine
                            + " reconnect="
                            + reconnectLine);
        }
        System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_RECONNECT_STABLE_CLIENT");
        System.out.println("PHYSICAL_AUTHORITY_STATUS_COMMAND_SENT_CLIENT");

        context.waitFor(client -> client.level == null && client.player == null, 20 * 30);
        context.setScreen(TitleScreen::new);
        context.waitForScreen(TitleScreen.class);
    }

    private static void waitForServerHandshake(ClientGameTestContext context) {
        context.waitFor(client -> client.level != null && client.player != null, CONNECTION_TIMEOUT_TICKS);
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.experienceLevel == SERVER_HANDSHAKE_LEVEL,
                20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_SERVER_HANDSHAKE_CLIENT");
        context.waitFor(client -> client.gui.screen() == null, 20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_GAMEPLAY_SCREEN_READY_CLIENT");
    }

    private static void prepareMainInventoryGrantSlot(ClientGameTestContext context) {
        for (int slot = 0; slot <= TARGET_HOTBAR_SLOT; slot++) {
            sendCommand(
                    context,
                    "/item replace entity @s hotbar."
                            + slot
                            + " with minecraft:stone");
        }
        context.waitFor(
                client -> {
                    if (client.player == null) {
                        return false;
                    }
                    for (int slot = 0; slot <= TARGET_HOTBAR_SLOT; slot++) {
                        if (client.player.getInventory().getItem(slot).isEmpty()) {
                            return false;
                        }
                    }
                    return client.player
                            .getInventory()
                            .getItem(CHRONICLE_HOTBAR_SLOT)
                            .is(Items.WRITTEN_BOOK);
                },
                20 * 10);
        System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_FILLER_READY_CLIENT");
    }

    private static void freeTargetHotbarSlot(ClientGameTestContext context) {
        sendCommand(
                context,
                "/item replace entity @s hotbar."
                        + TARGET_HOTBAR_SLOT
                        + " with minecraft:air");
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.getInventory().getItem(TARGET_HOTBAR_SLOT).isEmpty(),
                20 * 10);
    }

    private static void openDevHub(ClientGameTestContext context) {
        sendCommand(context, "/mmo dev");
        context.waitFor(client -> menuContains(client.gui.screen(), DEV_MODULE_NAME), 20 * 10);
    }

    private static void clickMenuEntry(
            ClientGameTestContext context, String namePrefix, boolean shiftClick) {
        setDevMenuCursor(context, namePrefix);
        if (shiftClick) {
            context.getInput().holdShift();
            context.waitTicks(1);
        }
        try {
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        } finally {
            if (shiftClick) {
                context.getInput().releaseShift();
            }
        }
    }

    private static void setDevMenuCursor(ClientGameTestContext context, String namePrefix) {
        double[] target =
                context.computeOnClient(
                        client -> {
                            if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
                                throw new AssertionError(
                                        "Expected an open container screen for dev preparation");
                            }
                            Slot slot = findMenuEntry(screen, namePrefix);
                            double guiWidth = client.getWindow().getGuiScaledWidth();
                            double guiHeight = client.getWindow().getGuiScaledHeight();
                            double screenWidth = client.getWindow().getScreenWidth();
                            double screenHeight = client.getWindow().getScreenHeight();
                            double left = (guiWidth - DEV_CONTAINER_IMAGE_WIDTH) / 2.0;
                            double top = (guiHeight - DEV_CONTAINER_IMAGE_HEIGHT) / 2.0;
                            double guiX = left + slot.x + SLOT_CENTER_OFFSET;
                            double guiY = top + slot.y + SLOT_CENTER_OFFSET;
                            double rawX = guiX * screenWidth / guiWidth;
                            double rawY = guiY * screenHeight / guiHeight;
                            return new double[] {rawX, rawY, slot.x, slot.y, left, top};
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
        double slotLeft = target[4] + target[2];
        double slotTop = target[5] + target[3];
        if (observed[0] < slotLeft
                || observed[0] >= slotLeft + SLOT_HITBOX_SIZE
                || observed[1] < slotTop
                || observed[1] >= slotTop + SLOT_HITBOX_SIZE) {
            throw new AssertionError(
                    "Physical cursor did not land inside dev menu entry: " + namePrefix);
        }
    }

    private static Slot findMenuEntry(AbstractContainerScreen<?> screen, String namePrefix) {
        return screen.getMenu().slots.stream()
                .filter(
                        candidate ->
                                candidate.hasItem()
                                        && candidate
                                                .getItem()
                                                .getHoverName()
                                                .getString()
                                                .startsWith(namePrefix))
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError("Dev menu entry not found: " + namePrefix));
    }

    private static boolean menuContains(Object screenValue, String namePrefix) {
        if (!(screenValue instanceof AbstractContainerScreen<?> screen)) {
            return false;
        }
        return screen.getMenu().slots.stream()
                .anyMatch(
                        candidate ->
                                candidate.hasItem()
                                        && candidate
                                                .getItem()
                                                .getHoverName()
                                                .getString()
                                                .startsWith(namePrefix));
    }

    private static void registerGameMessageCapture() {
        if (!GAME_MESSAGE_LISTENER_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ClientReceiveMessageEvents.GAME.register(
                (message, overlay) -> RECEIVED_GAME_MESSAGES.add(message.getString()));
    }

    private static String sendStatusAndCaptureTonic(
            ClientGameTestContext context, String marker) {
        for (int attempt = 0; attempt < 12; attempt++) {
            int firstNewMessage = RECEIVED_GAME_MESSAGES.size();
            sendCommand(context, "/mmo physical status");
            context.waitTicks(10);
            String status = tonicStatusSince(firstNewMessage);
            if (status != null) {
                System.out.println(marker);
                return status;
            }
            context.waitTicks(10);
        }
        throw new AssertionError("Timed out waiting for authoritative tonic lot status");
    }

    private static void sendCommand(ClientGameTestContext context, String command) {
        context.getInput().pressKey(options -> options.keyChat);
        context.waitFor(client -> client.gui.screen() instanceof ChatScreen, 20 * 5);
        context.getInput().typeChars(command);
        context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
        context.waitFor(client -> !(client.gui.screen() instanceof ChatScreen), 20 * 5);
    }

    private static String tonicStatusSince(int firstMessage) {
        for (int index = RECEIVED_GAME_MESSAGES.size() - 1; index >= firstMessage; index--) {
            String message = RECEIVED_GAME_MESSAGES.get(index);
            if (message.startsWith("LOT uuid=") && message.contains(" def=" + DEFINITION_ID + " ")) {
                return message;
            }
        }
        return null;
    }

    private static LotAuthority parseTonicStatus(String status) {
        Matcher match = TONIC_STATUS.matcher(status);
        if (!match.matches()) {
            throw new AssertionError("Unexpected tonic authority status: " + status);
        }
        return new LotAuthority(
                match.group(1).toLowerCase(),
                match.group(2),
                Integer.parseInt(match.group(3)),
                Integer.parseInt(match.group(4)),
                match.group(5).toLowerCase(),
                match.group(6));
    }

    private static Slot findPlayerSlot(InventoryScreen screen, Object inventory, int storageSlot) {
        return screen.getMenu().slots.stream()
                .filter(
                        candidate ->
                                candidate.container == inventory
                                        && candidate.getContainerSlot() == storageSlot)
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "Player storage slot not found in InventoryScreen: " + storageSlot));
    }

    private static void setInventoryCursor(ClientGameTestContext context, int storageSlot) {
        double[] target =
                context.computeOnClient(
                        client -> {
                            if (!(client.gui.screen() instanceof InventoryScreen screen)
                                    || client.player == null) {
                                throw new AssertionError(
                                        "InventoryScreen and player must be present for physical input");
                            }
                            Slot slot = findPlayerSlot(screen, client.player.getInventory(), storageSlot);
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
                            return new double[] {rawX, rawY, slot.x, slot.y, left, top};
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
        double slotLeft = target[4] + target[2];
        double slotTop = target[5] + target[3];
        if (observed[0] < slotLeft
                || observed[0] >= slotLeft + SLOT_HITBOX_SIZE
                || observed[1] < slotTop
                || observed[1] >= slotTop + SLOT_HITBOX_SIZE) {
            throw new AssertionError(
                    "Physical cursor did not land inside InventoryScreen storage slot " + storageSlot);
        }
    }

    private static void reconnect(ClientGameTestContext context, String address) {
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() != null, 20 * 5);
        context.clickScreenButton("menu.disconnect");
        context.waitFor(client -> client.level == null && client.player == null, 20 * 15);
        connect(context, address);
        context.waitFor(client -> client.level != null && client.player != null, CONNECTION_TIMEOUT_TICKS);
        context.waitFor(client -> client.gui.screen() == null, 20 * 30);
    }

    private static void connect(ClientGameTestContext context, String address) {
        context.runOnClient(
                client -> {
                    ServerData server =
                            new ServerData(
                                    "Branz physical consumable lot acceptance",
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

    private record LotAuthority(
            String uuid,
            String location,
            int version,
            int quantity,
            String transactionId,
            String contentVersion) {}
}
