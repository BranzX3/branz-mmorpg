package com.branz.mmorpg.acceptance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.mixin.client.gametest.input.MouseHandlerAccessor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/** Section C4: reject physical lot split/swap, then persist one valid whole-lot move across restart. */
final class PhysicalConsumableC4ClientGameTest {
    private static final int SERVER_HANDSHAKE_LEVEL = 7;
    private static final int FIRST_STORAGE_SLOT = 9;
    private static final int SECOND_STORAGE_SLOT = 10;
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
    private static final String DEFINITION_ID = "consumable.training_weapon_coating";
    private static final String DEV_MODULE_NAME = "Persisted Test Item";
    private static final String GRANT_LABEL_PREFIX = DEFINITION_ID + " (Shift = lot x64)";
    private static final Pattern COATING_STATUS =
            Pattern.compile(
                    "^LOT uuid=([0-9a-fA-F-]{36}) def=consumable\\.training_weapon_coating loc=([^ ]+) ver=(\\d+) qty=(\\d+) tx=([0-9a-fA-F-]{36}) content=(\\S+)$");
    private static final List<String> RECEIVED_GAME_MESSAGES = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean GAME_MESSAGE_LISTENER_REGISTERED = new AtomicBoolean();

    void runTest(ClientGameTestContext context) {
        registerGameMessageCapture();
        RECEIVED_GAME_MESSAGES.clear();
        connect(context, "Branz physical consumable C4 acceptance");
        waitForServerHandshake(context, "PHYSICAL_AUTHORITY_C4_HANDSHAKE_CLIENT");
        prepareMainInventoryGrantSlots(context);

        grantCoatingLot(context);
        Map<String, LotAuthority> firstSnapshot =
                captureCoatingSnapshot(context, 1, "PHYSICAL_AUTHORITY_C4_STATUS_FIRST_CLIENT");
        LotAuthority first = only(firstSnapshot);
        requireStaged(first, FIRST_STORAGE_SLOT, "first");
        System.out.println("PHYSICAL_AUTHORITY_C4_FIRST_LOT_READY_CLIENT");

        grantCoatingLot(context);
        Map<String, LotAuthority> staged =
                captureCoatingSnapshot(context, 2, "PHYSICAL_AUTHORITY_C4_STATUS_STAGED_CLIENT");
        LotAuthority stagedFirst = staged.get(first.uuid());
        if (!first.equals(stagedFirst)) {
            throw new AssertionError(
                    "Granting the second C4 lot changed the first authority: first="
                            + first
                            + " observed="
                            + stagedFirst);
        }
        LotAuthority second = staged.values().stream()
                .filter(row -> !row.uuid().equals(first.uuid()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Second C4 coating lot is missing"));
        requireStaged(second, SECOND_STORAGE_SLOT, "second");
        System.out.println("PHYSICAL_AUTHORITY_C4_SECOND_LOT_READY_CLIENT");

        freeTargetHotbarSlot(context);
        openInventory(context);
        waitForCanonicalInventory(context);

        // A real right-click creates a vanilla half-stack attempt. The MMO authority must reject it
        // and project both authoritative x64 lots back with an empty cursor.
        setInventoryCursor(context, FIRST_STORAGE_SLOT);
        context.waitTicks(1);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        System.out.println("PHYSICAL_AUTHORITY_C4_SPLIT_MOUSE_SENT_CLIENT");
        waitForSplitTransient(context);
        System.out.println("PHYSICAL_AUTHORITY_C4_SPLIT_TRANSIENT_CLIENT");
        waitForCanonicalInventory(context);
        Map<String, LotAuthority> afterSplit =
                captureCoatingSnapshot(context, 2, "PHYSICAL_AUTHORITY_C4_STATUS_AFTER_SPLIT_CLIENT");
        assertAuthoritySetEquals(staged, afterSplit, "split rejection");
        System.out.println("PHYSICAL_AUTHORITY_C4_SPLIT_RECONCILED_CLIENT");

        // Pick the full first lot up, then physically click the occupied second-lot slot. This is a
        // signed-lot swap/merge attempt and must also fail closed with both lots restored.
        setInventoryCursor(context, FIRST_STORAGE_SLOT);
        context.waitTicks(1);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitFor(client -> firstLotOnCursor(client), 20 * 5);
        System.out.println("PHYSICAL_AUTHORITY_C4_SWAP_PICKUP_CLIENT");
        setInventoryCursor(context, SECOND_STORAGE_SLOT);
        context.waitTicks(1);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        System.out.println("PHYSICAL_AUTHORITY_C4_SWAP_MOUSE_SENT_CLIENT");
        waitForCanonicalInventory(context);
        Map<String, LotAuthority> afterSwap =
                captureCoatingSnapshot(context, 2, "PHYSICAL_AUTHORITY_C4_STATUS_AFTER_SWAP_CLIENT");
        assertAuthoritySetEquals(staged, afterSwap, "swap rejection");
        System.out.println("PHYSICAL_AUTHORITY_C4_SWAP_RECONCILED_CLIENT");

        // The valid control case: move the first lot as one whole signed stack into gameplay slot 7.
        moveWholeFirstLot(context);
        Map<String, LotAuthority> moved =
                waitForWholeMove(context, staged, first.uuid(), second.uuid());
        LotAuthority movedFirst = moved.get(first.uuid());
        LotAuthority movedSecond = moved.get(second.uuid());
        if (!isWholeMove(first, movedFirst, TARGET_HOTBAR_SLOT)) {
            throw new AssertionError("C4 whole-lot move authority is invalid: " + movedFirst);
        }
        if (!second.equals(movedSecond)) {
            throw new AssertionError("C4 valid move changed the untouched second lot: " + movedSecond);
        }
        System.out.println("PHYSICAL_AUTHORITY_C4_STATUS_MOVED_CLIENT");
        System.out.println("PHYSICAL_AUTHORITY_C4_WHOLE_MOVE_CLIENT");

        assertAuthorityStable(context, moved, 2);
        System.out.println("PHYSICAL_AUTHORITY_C4_PRE_RESTART_STABLE_CLIENT");
        disconnectToTitle(context);
    }

    void runRestartTest(ClientGameTestContext context) {
        registerGameMessageCapture();
        RECEIVED_GAME_MESSAGES.clear();
        connect(context, "Branz physical consumable C4 restart acceptance");
        waitForServerHandshake(context, "PHYSICAL_AUTHORITY_C4_RESTART_HANDSHAKE_CLIENT");
        context.waitFor(
                client ->
                        client.player != null
                                && hasCoatingProjection(
                                        client.player.getInventory().getItem(TARGET_HOTBAR_SLOT),
                                        EXPECTED_QUANTITY)
                                && hasCoatingProjection(
                                        client.player.getInventory().getItem(SECOND_STORAGE_SLOT),
                                        EXPECTED_QUANTITY)
                                && client.player
                                        .getInventory()
                                        .getItem(CHRONICLE_HOTBAR_SLOT)
                                        .is(Items.WRITTEN_BOOK),
                20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_C4_RESTART_PROJECTED_CLIENT");

        Map<String, LotAuthority> snapshot =
                captureCoatingSnapshot(
                        context, 2, "PHYSICAL_AUTHORITY_C4_RESTART_STATUS_CLIENT");
        boolean movedLot = snapshot.values().stream().anyMatch(
                row -> row.quantity() == EXPECTED_QUANTITY
                        && ("CHARACTER_INVENTORY/slot:" + TARGET_HOTBAR_SLOT)
                                .equals(row.location()));
        boolean untouchedLot = snapshot.values().stream().anyMatch(
                row -> row.quantity() == EXPECTED_QUANTITY
                        && ("CHARACTER_INVENTORY/slot:" + SECOND_STORAGE_SLOT)
                                .equals(row.location()));
        if (!movedLot || !untouchedLot) {
            throw new AssertionError("C4 restart authority locations are invalid: " + snapshot);
        }
        assertAuthorityStable(context, snapshot, 2);
        System.out.println("PHYSICAL_AUTHORITY_C4_RESTART_STABLE_CLIENT");
        disconnectToTitle(context);
    }

    private static void prepareMainInventoryGrantSlots(ClientGameTestContext context) {
        context.waitFor(
                client ->
                        client.player != null
                                && client.player
                                        .getInventory()
                                        .getItem(CHRONICLE_HOTBAR_SLOT)
                                        .is(Items.WRITTEN_BOOK),
                20 * 30);
        for (int slot = 0; slot <= TARGET_HOTBAR_SLOT; slot++) {
            sendCommand(context, "/item replace entity @s hotbar." + slot + " with minecraft:stone");
            int expectedSlot = slot;
            context.waitFor(
                    client ->
                            client.player != null
                                    && client.player
                                            .getInventory()
                                            .getItem(expectedSlot)
                                            .is(Items.STONE),
                    20 * 5);
        }
    }

    private static void grantCoatingLot(ClientGameTestContext context) {
        sendCommand(context, "/mmo dev");
        context.waitFor(client -> menuContains(client.gui.screen(), DEV_MODULE_NAME), 20 * 10);
        clickMenuEntry(context, DEV_MODULE_NAME, false);
        context.waitFor(client -> menuContains(client.gui.screen(), GRANT_LABEL_PREFIX), 20 * 10);
        clickMenuEntry(context, GRANT_LABEL_PREFIX, true);
        context.waitTicks(20);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(null);
    }

    private static void freeTargetHotbarSlot(ClientGameTestContext context) {
        sendCommand(
                context,
                "/item replace entity @s hotbar." + TARGET_HOTBAR_SLOT + " with minecraft:air");
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.getInventory().getItem(TARGET_HOTBAR_SLOT).isEmpty(),
                20 * 10);
    }

    private static void openInventory(ClientGameTestContext context) {
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        waitForCanonicalInventory(context);
    }

    private static void waitForCanonicalInventory(ClientGameTestContext context) {
        context.waitFor(
                client -> {
                    if (!(client.gui.screen() instanceof InventoryScreen screen)
                            || client.player == null) {
                        return false;
                    }
                    Slot first = findPlayerSlot(screen, client.player.getInventory(), FIRST_STORAGE_SLOT);
                    Slot second = findPlayerSlot(screen, client.player.getInventory(), SECOND_STORAGE_SLOT);
                    Slot target = findPlayerSlot(screen, client.player.getInventory(), TARGET_HOTBAR_SLOT);
                    return hasCoatingProjection(first.getItem(), EXPECTED_QUANTITY)
                            && hasCoatingProjection(second.getItem(), EXPECTED_QUANTITY)
                            && target.getItem().isEmpty()
                            && screen.getMenu().getCarried().isEmpty();
                },
                20 * 10);
    }

    private static void waitForSplitTransient(ClientGameTestContext context) {
        context.waitFor(
                client -> {
                    if (!(client.gui.screen() instanceof InventoryScreen screen)
                            || client.player == null) {
                        return false;
                    }
                    Slot first = findPlayerSlot(screen, client.player.getInventory(), FIRST_STORAGE_SLOT);
                    var carried = screen.getMenu().getCarried();
                    return hasCoatingProjection(first.getItem())
                            && first.getItem().getCount() < EXPECTED_QUANTITY
                            && hasCoatingProjection(carried)
                            && carried.getCount() > 0;
                },
                20 * 3);
    }

    private static boolean firstLotOnCursor(net.minecraft.client.Minecraft client) {
        if (!(client.gui.screen() instanceof InventoryScreen screen) || client.player == null) {
            return false;
        }
        Slot first = findPlayerSlot(screen, client.player.getInventory(), FIRST_STORAGE_SLOT);
        return first.getItem().isEmpty()
                && hasCoatingProjection(screen.getMenu().getCarried(), EXPECTED_QUANTITY);
    }

    private static void moveWholeFirstLot(ClientGameTestContext context) {
        setInventoryCursor(context, FIRST_STORAGE_SLOT);
        context.waitTicks(1);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitFor(client -> firstLotOnCursor(client), 20 * 5);
        setInventoryCursor(context, TARGET_HOTBAR_SLOT);
        context.waitTicks(1);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitFor(
                client -> {
                    if (!(client.gui.screen() instanceof InventoryScreen screen)
                            || client.player == null) {
                        return false;
                    }
                    Slot first = findPlayerSlot(screen, client.player.getInventory(), FIRST_STORAGE_SLOT);
                    Slot target = findPlayerSlot(screen, client.player.getInventory(), TARGET_HOTBAR_SLOT);
                    return first.getItem().isEmpty()
                            && hasCoatingProjection(target.getItem(), EXPECTED_QUANTITY)
                            && screen.getMenu().getCarried().isEmpty();
                },
                20 * 10);
        context.waitTicks(20);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(null);
    }

    private static Map<String, LotAuthority> waitForWholeMove(
            ClientGameTestContext context,
            Map<String, LotAuthority> staged,
            String firstUuid,
            String secondUuid) {
        for (int attempt = 0; attempt < 16; attempt++) {
            Map<String, LotAuthority> observed = pollCoatingSnapshot(context, 2);
            LotAuthority beforeFirst = staged.get(firstUuid);
            LotAuthority beforeSecond = staged.get(secondUuid);
            LotAuthority afterFirst = observed.get(firstUuid);
            LotAuthority afterSecond = observed.get(secondUuid);
            if (afterFirst != null
                    && afterSecond != null
                    && isWholeMove(beforeFirst, afterFirst, TARGET_HOTBAR_SLOT)
                    && beforeSecond.equals(afterSecond)) {
                return observed;
            }
            if (!staged.equals(observed)) {
                throw new AssertionError(
                        "Unexpected C4 authority while waiting for whole-lot move: staged="
                                + staged
                                + " observed="
                                + observed);
            }
        }
        throw new AssertionError("Timed out waiting for C4 whole-lot move authority");
    }

    private static void assertAuthorityStable(
            ClientGameTestContext context, Map<String, LotAuthority> expected, int probes) {
        for (int probe = 0; probe < probes; probe++) {
            Map<String, LotAuthority> observed = pollCoatingSnapshot(context, expected.size());
            assertAuthoritySetEquals(expected, observed, "stability probe " + probe);
        }
    }

    private static Map<String, LotAuthority> captureCoatingSnapshot(
            ClientGameTestContext context, int expectedCount, String marker) {
        Map<String, LotAuthority> snapshot = pollCoatingSnapshot(context, expectedCount);
        System.out.println(marker);
        return snapshot;
    }

    private static Map<String, LotAuthority> pollCoatingSnapshot(
            ClientGameTestContext context, int expectedCount) {
        for (int attempt = 0; attempt < 12; attempt++) {
            int firstNewMessage = RECEIVED_GAME_MESSAGES.size();
            sendCommand(context, "/mmo physical status");
            context.waitTicks(10);
            Map<String, LotAuthority> snapshot = coatingStatusesSince(firstNewMessage);
            if (snapshot.size() == expectedCount) {
                return snapshot;
            }
            context.waitTicks(10);
            snapshot = coatingStatusesSince(firstNewMessage);
            if (snapshot.size() == expectedCount) {
                return snapshot;
            }
        }
        throw new AssertionError(
                "Timed out waiting for " + expectedCount + " authoritative C4 coating lots");
    }

    private static Map<String, LotAuthority> coatingStatusesSince(int firstMessage) {
        Map<String, LotAuthority> result = new HashMap<>();
        for (int index = firstMessage; index < RECEIVED_GAME_MESSAGES.size(); index++) {
            String message = RECEIVED_GAME_MESSAGES.get(index);
            if (!message.startsWith("LOT uuid=") || !message.contains(" def=" + DEFINITION_ID + " ")) {
                continue;
            }
            LotAuthority row = parseCoatingStatus(message);
            result.put(row.uuid(), row);
        }
        return Map.copyOf(result);
    }

    private static LotAuthority parseCoatingStatus(String status) {
        Matcher match = COATING_STATUS.matcher(status);
        if (!match.matches()) {
            throw new AssertionError("Unexpected coating authority status: " + status);
        }
        return new LotAuthority(
                match.group(1).toLowerCase(),
                match.group(2),
                Integer.parseInt(match.group(3)),
                Integer.parseInt(match.group(4)),
                match.group(5).toLowerCase(),
                match.group(6));
    }

    private static boolean isWholeMove(LotAuthority before, LotAuthority after, int slot) {
        return before != null
                && after != null
                && before.uuid().equals(after.uuid())
                && before.contentVersion().equals(after.contentVersion())
                && before.quantity() == after.quantity()
                && after.version() == before.version() + 1
                && !before.transactionId().equals(after.transactionId())
                && ("CHARACTER_INVENTORY/slot:" + slot).equals(after.location());
    }

    private static void requireStaged(LotAuthority authority, int slot, String label) {
        if (authority == null
                || authority.quantity() != EXPECTED_QUANTITY
                || !("CHARACTER_INVENTORY/slot:" + slot).equals(authority.location())) {
            throw new AssertionError("C4 " + label + " lot staged incorrectly: " + authority);
        }
    }

    private static LotAuthority only(Map<String, LotAuthority> snapshot) {
        if (snapshot.size() != 1) {
            throw new AssertionError("Expected exactly one C4 lot: " + snapshot);
        }
        return snapshot.values().iterator().next();
    }

    private static void assertAuthoritySetEquals(
            Map<String, LotAuthority> expected,
            Map<String, LotAuthority> observed,
            String phase) {
        if (!expected.equals(observed)) {
            throw new AssertionError(
                    "C4 authority changed during " + phase + ": expected=" + expected + " observed=" + observed);
        }
    }

    private static void waitForServerHandshake(ClientGameTestContext context, String marker) {
        context.waitFor(client -> client.level != null && client.player != null, CONNECTION_TIMEOUT_TICKS);
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.experienceLevel == SERVER_HANDSHAKE_LEVEL,
                20 * 30);
        context.waitFor(client -> client.gui.screen() == null, 20 * 30);
        System.out.println(marker);
    }

    private static void clickMenuEntry(
            ClientGameTestContext context, String namePrefix, boolean shiftClick) {
        setDevMenuCursor(context, namePrefix);
        if (!shiftClick) {
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            return;
        }
        context.getInput().holdShift();
        context.waitTicks(1);
        try {
            context.runOnClient(
                    client -> {
                        MouseHandlerAccessor mouse = (MouseHandlerAccessor) client.mouseHandler;
                        MouseButtonInfo button =
                                new MouseButtonInfo(
                                        GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_MOD_SHIFT);
                        long window = client.getWindow().handle();
                        mouse.invokeOnButton(window, button, GLFW.GLFW_PRESS);
                        mouse.invokeOnButton(window, button, GLFW.GLFW_RELEASE);
                    });
            context.waitTicks(1);
        } finally {
            context.getInput().releaseShift();
        }
    }

    private static void setDevMenuCursor(ClientGameTestContext context, String namePrefix) {
        double[] target =
                context.computeOnClient(
                        client -> {
                            if (!(client.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
                                throw new AssertionError("Expected an open dev container");
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
                            return new double[] {
                                guiX * screenWidth / guiWidth,
                                guiY * screenHeight / guiHeight,
                                slot.x,
                                slot.y,
                                left,
                                top
                            };
                        });
        context.getInput().setCursorPos(target[0], target[1]);
        assertCursorInsideSlot(context, target, namePrefix);
    }

    private static Slot findMenuEntry(AbstractContainerScreen<?> screen, String namePrefix) {
        return screen.getMenu().slots.stream()
                .filter(
                        slot ->
                                slot.hasItem()
                                        && slot.getItem()
                                                .getHoverName()
                                                .getString()
                                                .startsWith(namePrefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Dev menu entry not found: " + namePrefix));
    }

    private static boolean menuContains(Object screenValue, String namePrefix) {
        if (!(screenValue instanceof AbstractContainerScreen<?> screen)) {
            return false;
        }
        return screen.getMenu().slots.stream()
                .anyMatch(
                        slot ->
                                slot.hasItem()
                                        && slot.getItem()
                                                .getHoverName()
                                                .getString()
                                                .startsWith(namePrefix));
    }

    private static Slot findPlayerSlot(InventoryScreen screen, Object inventory, int storageSlot) {
        return screen.getMenu().slots.stream()
                .filter(
                        slot -> slot.container == inventory && slot.getContainerSlot() == storageSlot)
                .findFirst()
                .orElseThrow(
                        () -> new AssertionError("Player storage slot not found: " + storageSlot));
    }

    private static void setInventoryCursor(ClientGameTestContext context, int storageSlot) {
        double[] target =
                context.computeOnClient(
                        client -> {
                            if (!(client.gui.screen() instanceof InventoryScreen screen)
                                    || client.player == null) {
                                throw new AssertionError("InventoryScreen and player are required");
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
                            return new double[] {
                                guiX * screenWidth / guiWidth,
                                guiY * screenHeight / guiHeight,
                                slot.x,
                                slot.y,
                                left,
                                top
                            };
                        });
        context.getInput().setCursorPos(target[0], target[1]);
        assertCursorInsideSlot(context, target, "storage slot " + storageSlot);
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

    private static boolean hasCoatingProjection(net.minecraft.world.item.ItemStack stack) {
        return !stack.isEmpty() && DEFINITION_ID.equals(stack.getHoverName().getString());
    }

    private static boolean hasCoatingProjection(
            net.minecraft.world.item.ItemStack stack, int expectedCount) {
        return hasCoatingProjection(stack) && stack.getCount() == expectedCount;
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

    private static void connect(ClientGameTestContext context, String label) {
        String address = System.getProperty("branz.acceptance.server", "localhost:25565");
        context.runOnClient(
                client -> {
                    ServerData server = new ServerData(label, address, ServerData.Type.OTHER);
                    ConnectScreen.startConnecting(
                            client.gui.screen(),
                            client,
                            ServerAddress.parseString(address),
                            server,
                            false,
                            null);
                });
    }

    private static void disconnectToTitle(ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() != null, 20 * 5);
        context.clickScreenButton("menu.disconnect");
        context.waitFor(client -> client.level == null && client.player == null, 20 * 15);
        context.setScreen(TitleScreen::new);
        context.waitForScreen(TitleScreen.class);
    }

    private record LotAuthority(
            String uuid,
            String location,
            int version,
            int quantity,
            String transactionId,
            String contentVersion) {}
}
