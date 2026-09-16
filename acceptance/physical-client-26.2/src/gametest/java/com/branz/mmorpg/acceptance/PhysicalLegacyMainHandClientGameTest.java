package com.branz.mmorpg.acceptance;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/** Section A: exact legacy Chronicle MAIN_HAND seed/probe and target migration client phases. */
final class PhysicalLegacyMainHandClientGameTest {
    private static final int SERVER_HANDSHAKE_LEVEL = 7;
    private static final int CHRONICLE_HOTBAR_SLOT = 8;
    private static final int MAIN_HAND_PHYSICAL_SLOT = 0;
    private static final int STORAGE_SIZE = 36;
    private static final int LEGACY_FILLER_COUNT_BEFORE_SLOT_ZERO = 34;
    // Historical Chronicle contract: legacy SceneHub owns menu slots 0..53 exactly.
    private static final int LEGACY_SCENE_MENU_SLOTS = 54;
    private static final int CONNECTION_TIMEOUT_TICKS = 20 * 60;
    private static final int CONTAINER_IMAGE_WIDTH = 176;
    private static final int CONTAINER_IMAGE_HEIGHT = 222;
    private static final double SLOT_HITBOX_SIZE = 16.0;
    private static final double SLOT_CENTER_OFFSET = SLOT_HITBOX_SIZE / 2.0;

    private static final String SWORD_ID = "weapon.training_sword";
    private static final String DEV_MODULE_NAME = "Persisted Test Item";
    private static final String EQUIPMENT_PAGE = "Character & Equipment";
    private static final String CONFIRM_SCENE = "Confirm Scene transaction";
    private static final String EQUIPMENT_COMMITTED = "Equipment committed.";
    private static final String NEGATIVE_LEGACY_PROJECTION_LOCK =
            "Inventory projection remains locked: PROJECTION_INVALID_DATABASE_SLOT Inventory slot 0 conflicts with native main-hand projection.";
    private static final String NEGATIVE_TARGET_LOCK =
            "MMO character remains locked: CHARACTER_TRANSACTION_REJECTED Legacy MAIN_HAND migration requires one free character inventory slot.";

    private static final List<String> RECEIVED_GAME_MESSAGES = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean GAME_MESSAGE_LISTENER_REGISTERED = new AtomicBoolean();

    void runTest(ClientGameTestContext context) {
        registerGameMessageCapture();
        RECEIVED_GAME_MESSAGES.clear();
        String phase = System.getProperty("branz.acceptance.physicalLegacyMainHandPhase", "").trim();
        if (phase.isEmpty()) {
            throw new AssertionError(
                    "Section A legacy MAIN_HAND phase was not supplied by the fixed harness action");
        }
        String address = System.getProperty("branz.acceptance.server", "localhost:25565");
        connect(context, address);
        waitForServerHandshake(context);
        switch (phase) {
            case "legacy-seed" -> runLegacySeed(context);
            case "legacy-probe" -> runLegacyProbe(context);
            case "target-verify" ->
                    runTargetStatus(context, "PHYSICAL_AUTHORITY_A_TARGET_STATUS_CLIENT");
            case "target-stable" ->
                    runTargetStatus(context, "PHYSICAL_AUTHORITY_A_TARGET_STABLE_STATUS_CLIENT");
            case "negative-prep" -> runLegacySeed(context);
            case "negative-fill" -> runNegativeLegacyFill(context);
            case "negative-fail" -> runNegativeTargetFailure(context);
            default -> throw new AssertionError("Unknown fixed Section A client phase: " + phase);
        }
        disconnectToTitle(context);
    }

    private static void runLegacySeed(ClientGameTestContext context) {
        grantItem(context, SWORD_ID, 1);
        openChronicle(context);
        clickChronicleMenuEntry(context, EQUIPMENT_PAGE);
        context.waitFor(
                client -> chronicleMenuContains(client.gui.screen(), SWORD_ID),
                20 * 10);
        clickChronicleMenuEntry(context, SWORD_ID);
        context.waitFor(
                client -> chronicleMenuContains(client.gui.screen(), CONFIRM_SCENE),
                20 * 10);
        int commitStart = RECEIVED_GAME_MESSAGES.size();
        clickChronicleMenuEntry(context, CONFIRM_SCENE);
        context.waitFor(
                client -> messageEqualsSince(commitStart, EQUIPMENT_COMMITTED), 20 * 15);
        closeContainer(context);
        context.waitFor(
                client ->
                        client.player != null
                                && hasProjection(client.player.getMainHandItem(), SWORD_ID),
                20 * 15);
        System.out.println("PHYSICAL_AUTHORITY_A_LEGACY_SEEDED_CLIENT");
    }

    private static void runLegacyProbe(ClientGameTestContext context) {
        context.waitFor(
                client ->
                        client.player != null
                                && hasProjection(client.player.getMainHandItem(), SWORD_ID),
                20 * 30);
        int firstMessage = RECEIVED_GAME_MESSAGES.size();
        sendCommand(context, "/paper dumpitem");
        context.waitFor(
                client -> {
                    String dump = projectionDumpSince(firstMessage);
                    return dump.contains("projection_value_id")
                            && dump.contains("projection_definition_id")
                            && dump.contains("projection_authority_version")
                            && dump.contains("projection_content_version");
                },
                20 * 15);
        String dump = projectionDumpSince(firstMessage);
        for (String line : dump.split("\\n")) {
            if (!line.isBlank()) {
                System.out.println("PHYSICAL_AUTHORITY_A_LEGACY_DUMP_CLIENT " + line);
            }
        }
        System.out.println("PHYSICAL_AUTHORITY_A_LEGACY_PROBE_COMPLETE_CLIENT");
    }

    private static void runTargetStatus(ClientGameTestContext context, String marker) {
        String status = probeStatusLine(context, SWORD_ID);
        if (!status.contains(" loc=CHARACTER_INVENTORY/slot:")
                || !status.contains(" durability=120/120 ")
                || status.contains(" loc=NATIVE_EQUIPPED/MAIN_HAND ")) {
            throw new AssertionError(
                    "Migrated Training Sword has unexpected target authority: " + status);
        }
        System.out.println(marker + " " + status);
    }

    private static void runNegativeLegacyFill(ClientGameTestContext context) {
        context.waitFor(
                client ->
                        client.player != null
                                && hasProjection(client.player.getMainHandItem(), SWORD_ID),
                20 * 30);
        for (int index = 0; index < LEGACY_FILLER_COUNT_BEFORE_SLOT_ZERO; index++) {
            grantItem(context, SWORD_ID, index + 2);
        }
        assertAllNonChronicleStorageOccupied(context);

        sendCommand(
                context,
                "/item replace entity @s hotbar."
                        + MAIN_HAND_PHYSICAL_SLOT
                        + " with minecraft:air");
        context.waitFor(
                client ->
                        client.player != null
                                && client.player
                                        .getInventory()
                                        .getItem(MAIN_HAND_PHYSICAL_SLOT)
                                        .isEmpty(),
                20 * 10);

        int lockStart = RECEIVED_GAME_MESSAGES.size();
        grantItemWithoutProjectionWait(context, SWORD_ID);
        context.waitFor(
                client -> messageEqualsSince(lockStart, NEGATIVE_LEGACY_PROJECTION_LOCK),
                20 * 20);
        System.out.println(
                "PHYSICAL_AUTHORITY_A_NEGATIVE_FULL_DB_CLIENT inventory_rows=35 main_hand=1");
    }

    private static void runNegativeTargetFailure(ClientGameTestContext context) {
        // Migration can reject immediately after join; capture starts before connect.
        context.waitFor(client -> messageEqualsSince(0, NEGATIVE_TARGET_LOCK), 20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_A_NEGATIVE_TARGET_LOCKED_CLIENT");
    }

    private static void grantItem(
            ClientGameTestContext context, String definitionId, int expectedProjectedSwordCount) {
        sendCommand(context, "/mmo dev");
        context.waitFor(
                client ->
                        client.player != null
                                && devMenuContains(
                                        client.gui.screen(),
                                        client.player.getInventory(),
                                        DEV_MODULE_NAME),
                20 * 10);
        clickDevMenuEntry(context, DEV_MODULE_NAME);
        context.waitFor(
                client ->
                        client.player != null
                                && devMenuContains(
                                        client.gui.screen(),
                                        client.player.getInventory(),
                                        definitionId),
                20 * 10);
        clickDevMenuEntry(context, definitionId);
        context.waitFor(
                client -> {
                    if (client.player == null) {
                        return false;
                    }
                    int projected = 0;
                    for (int slot = 0; slot < STORAGE_SIZE; slot++) {
                        if (hasProjection(client.player.getInventory().getItem(slot), definitionId)) {
                            projected++;
                        }
                    }
                    return projected >= expectedProjectedSwordCount;
                },
                20 * 20);
        closeContainer(context);
    }

    private static void grantItemWithoutProjectionWait(
            ClientGameTestContext context, String definitionId) {
        sendCommand(context, "/mmo dev");
        context.waitFor(
                client ->
                        client.player != null
                                && devMenuContains(
                                        client.gui.screen(),
                                        client.player.getInventory(),
                                        DEV_MODULE_NAME),
                20 * 10);
        clickDevMenuEntry(context, DEV_MODULE_NAME);
        context.waitFor(
                client ->
                        client.player != null
                                && devMenuContains(
                                        client.gui.screen(),
                                        client.player.getInventory(),
                                        definitionId),
                20 * 10);
        clickDevMenuEntry(context, definitionId);
        context.waitTicks(20);
        if (context.computeOnClient(client -> client.gui.screen() != null)) {
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitForScreen(null);
        }
    }

    private static void assertAllNonChronicleStorageOccupied(ClientGameTestContext context) {
        boolean occupied =
                context.computeOnClient(
                        client -> {
                            if (client.player == null) {
                                return false;
                            }
                            for (int slot = 0; slot < STORAGE_SIZE; slot++) {
                                ItemStack stack = client.player.getInventory().getItem(slot);
                                if (slot == CHRONICLE_HOTBAR_SLOT) {
                                    if (!stack.is(Items.WRITTEN_BOOK)) {
                                        return false;
                                    }
                                } else if (stack.isEmpty()) {
                                    return false;
                                }
                            }
                            return true;
                        });
        if (!occupied) {
            throw new AssertionError(
                    "Legacy negative precondition did not occupy every physical storage slot");
        }
    }

    private static void openChronicle(ClientGameTestContext context) {
        context.getInput().pressKey(options -> options.keyHotbarSlots[CHRONICLE_HOTBAR_SLOT]);
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.getMainHandItem().is(Items.WRITTEN_BOOK),
                20 * 10);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitFor(
                client -> chronicleMenuContains(client.gui.screen(), EQUIPMENT_PAGE),
                20 * 10);
    }

    private static String probeStatusLine(ClientGameTestContext context, String definitionId) {
        for (int attempt = 0; attempt < 12; attempt++) {
            int firstNewMessage = RECEIVED_GAME_MESSAGES.size();
            sendCommand(context, "/mmo physical status");
            context.waitTicks(10);
            String status = statusLineSince(firstNewMessage, definitionId);
            if (status != null) {
                return status;
            }
            context.waitTicks(10);
            status = statusLineSince(firstNewMessage, definitionId);
            if (status != null) {
                return status;
            }
        }
        throw new AssertionError(
                "Timed out waiting for physical authority status: " + definitionId);
    }

    private static String statusLineSince(int firstMessage, String definitionId) {
        String token = " def=" + definitionId + " ";
        for (int index = RECEIVED_GAME_MESSAGES.size() - 1; index >= firstMessage; index--) {
            String message = RECEIVED_GAME_MESSAGES.get(index);
            if (message.startsWith("ITEM uuid=") && message.contains(token)) {
                return message;
            }
        }
        return null;
    }

    private static String projectionDumpSince(int firstMessage) {
        StringBuilder dump = new StringBuilder();
        for (int index = firstMessage; index < RECEIVED_GAME_MESSAGES.size(); index++) {
            String message = RECEIVED_GAME_MESSAGES.get(index);
            if (message.contains("projection_")) {
                if (!dump.isEmpty()) {
                    dump.append('\n');
                }
                dump.append(message.replace('\n', ' ').replace('\r', ' '));
            }
        }
        return dump.toString();
    }

    private static boolean messageEqualsSince(int firstMessage, String expected) {
        for (int index = RECEIVED_GAME_MESSAGES.size() - 1; index >= firstMessage; index--) {
            if (expected.equals(RECEIVED_GAME_MESSAGES.get(index))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Historical Chronicle contract: SceneHub inventory size is exactly 54 menu slots.
     * Player inventory/hotbar slots appended by the container are intentionally never searched.
     */
    private static boolean chronicleMenuContains(Object candidate, String namePrefix) {
        if (!(candidate instanceof AbstractContainerScreen<?> screen)) {
            return false;
        }
        if (screen.getMenu().slots.size() < LEGACY_SCENE_MENU_SLOTS) {
            return false;
        }
        return screen.getMenu().slots.subList(0, LEGACY_SCENE_MENU_SLOTS).stream()
                .anyMatch(slot -> namedSlot(slot, namePrefix));
    }

    /** Dev UI is a different inventory contract; scope it by top-container ownership only. */
    private static boolean devMenuContains(
            Object candidate, Container playerInventory, String namePrefix) {
        if (!(candidate instanceof AbstractContainerScreen<?> screen)) {
            return false;
        }
        return screen.getMenu().slots.stream()
                .anyMatch(
                        slot ->
                                slot.container != playerInventory
                                        && namedSlot(slot, namePrefix));
    }

    private static boolean namedSlot(Slot slot, String namePrefix) {
        return slot.hasItem()
                && slot.getItem().getHoverName().getString().startsWith(namePrefix);
    }

    private static Slot findChronicleMenuEntry(
            AbstractContainerScreen<?> screen, String namePrefix) {
        if (screen.getMenu().slots.size() < LEGACY_SCENE_MENU_SLOTS) {
            throw new AssertionError(
                    "Chronicle menu has fewer than "
                            + LEGACY_SCENE_MENU_SLOTS
                            + " legacy content slots");
        }
        return screen.getMenu().slots.subList(0, LEGACY_SCENE_MENU_SLOTS).stream()
                .filter(slot -> namedSlot(slot, namePrefix))
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "Chronicle menu entry not found in legacy slots 0..53: "
                                                + namePrefix));
    }

    private static Slot findDevMenuEntry(
            AbstractContainerScreen<?> screen,
            Container playerInventory,
            String namePrefix) {
        return screen.getMenu().slots.stream()
                .filter(slot -> slot.container != playerInventory && namedSlot(slot, namePrefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Dev menu entry not found: " + namePrefix));
    }

    private static void clickChronicleMenuEntry(
            ClientGameTestContext context, String namePrefix) {
        double[] target =
                context.computeOnClient(
                        client -> {
                            if (!(client.gui.screen()
                                    instanceof AbstractContainerScreen<?> screen)) {
                                throw new AssertionError(
                                        "Expected an open Chronicle container for " + namePrefix);
                            }
                            return cursorTarget(client, findChronicleMenuEntry(screen, namePrefix));
                        });
        clickTarget(context, target, namePrefix);
    }

    private static void clickDevMenuEntry(ClientGameTestContext context, String namePrefix) {
        double[] target =
                context.computeOnClient(
                        client -> {
                            if (!(client.gui.screen()
                                            instanceof AbstractContainerScreen<?> screen)
                                    || client.player == null) {
                                throw new AssertionError(
                                        "Expected an open dev container for " + namePrefix);
                            }
                            Slot slot =
                                    findDevMenuEntry(
                                            screen, client.player.getInventory(), namePrefix);
                            return cursorTarget(client, slot);
                        });
        clickTarget(context, target, namePrefix);
    }

    private static double[] cursorTarget(
            net.minecraft.client.Minecraft client,
            Slot slot) {
        double guiWidth = client.getWindow().getGuiScaledWidth();
        double guiHeight = client.getWindow().getGuiScaledHeight();
        double screenWidth = client.getWindow().getScreenWidth();
        double screenHeight = client.getWindow().getScreenHeight();
        double left = (guiWidth - CONTAINER_IMAGE_WIDTH) / 2.0;
        double top = (guiHeight - CONTAINER_IMAGE_HEIGHT) / 2.0;
        double guiX = left + slot.x + SLOT_CENTER_OFFSET;
        double guiY = top + slot.y + SLOT_CENTER_OFFSET;
        return new double[] {
            guiX * screenWidth / guiWidth,
            guiY * screenHeight / guiHeight,
            slot.x,
            slot.y,
            left,
            top
        };
    }

    private static void clickTarget(
            ClientGameTestContext context, double[] target, String label) {
        context.getInput().setCursorPos(target[0], target[1]);
        assertCursorInsideSlot(context, target, label);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void assertCursorInsideSlot(
            ClientGameTestContext context, double[] target, String label) {
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
            throw new AssertionError("Physical cursor missed " + label);
        }
    }

    private static boolean hasProjection(ItemStack stack, String definitionId) {
        return stack != null
                && !stack.isEmpty()
                && definitionId.equals(stack.getHoverName().getString());
    }

    private static void closeContainer(ClientGameTestContext context) {
        if (context.computeOnClient(client -> client.gui.screen() != null)) {
            context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
            context.waitForScreen(null);
        }
        context.waitTicks(3);
    }

    private static void registerGameMessageCapture() {
        if (!GAME_MESSAGE_LISTENER_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ClientReceiveMessageEvents.GAME.register(
                (message, overlay) -> RECEIVED_GAME_MESSAGES.add(message.getString()));
    }

    private static void sendCommand(ClientGameTestContext context, String command) {
        context.getInput().pressKey(options -> options.keyChat);
        context.waitFor(client -> client.gui.screen() instanceof ChatScreen, 20 * 5);
        context.getInput().typeChars(command);
        context.getInput().pressKey(GLFW.GLFW_KEY_ENTER);
        context.waitFor(client -> !(client.gui.screen() instanceof ChatScreen), 20 * 5);
    }

    private static void waitForServerHandshake(ClientGameTestContext context) {
        context.waitFor(
                client -> client.level != null && client.player != null,
                CONNECTION_TIMEOUT_TICKS);
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.experienceLevel == SERVER_HANDSHAKE_LEVEL,
                20 * 30);
        context.waitFor(client -> client.gui.screen() == null, 20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_A_HANDSHAKE_CLIENT");
    }

    private static void disconnectToTitle(ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() != null, 20 * 5);
        context.clickScreenButton("menu.disconnect");
        context.waitFor(client -> client.level == null && client.player == null, 20 * 30);
        context.setScreen(TitleScreen::new);
        context.waitForScreen(TitleScreen.class);
    }

    private static void connect(ClientGameTestContext context, String address) {
        context.runOnClient(
                client -> {
                    ServerData server =
                            new ServerData(
                                    "Branz legacy MAIN_HAND acceptance",
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
