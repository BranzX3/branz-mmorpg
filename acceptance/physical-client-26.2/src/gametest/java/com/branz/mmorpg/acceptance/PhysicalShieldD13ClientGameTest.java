package com.branz.mmorpg.acceptance;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
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

/** Section D1-D3: physical F-key shield equip, two-shield swap, unequip and reconnect authority. */
final class PhysicalShieldD13ClientGameTest {
    private static final int SERVER_HANDSHAKE_LEVEL = 7;
    private static final int FIRST_STORAGE_SLOT = 9;
    private static final int SECOND_STORAGE_SLOT = 10;
    private static final int SHIELD_HOTBAR_SLOT = 7;
    private static final int UNEQUIP_HOTBAR_SLOT = 6;
    private static final int CHRONICLE_HOTBAR_SLOT = 8;
    private static final int CONNECTION_TIMEOUT_TICKS = 20 * 60;
    private static final int INVENTORY_IMAGE_WIDTH = 176;
    private static final int INVENTORY_IMAGE_HEIGHT = 166;
    private static final int DEV_CONTAINER_IMAGE_WIDTH = 176;
    private static final int DEV_CONTAINER_IMAGE_HEIGHT = 222;
    private static final double SLOT_HITBOX_SIZE = 16.0;
    private static final double SLOT_CENTER_OFFSET = SLOT_HITBOX_SIZE / 2.0;
    private static final String DEFINITION_ID = "equipment.training_shield";
    private static final String DEV_MODULE_NAME = "Persisted Test Item";
    private static final Pattern SHIELD_STATUS =
            Pattern.compile(
                    "^ITEM uuid=([0-9a-fA-F-]{36}) def=equipment\\.training_shield loc=([^ ]+) ver=(\\d+) durability=(\\d+)/(\\d+) tx=([0-9a-fA-F-]{36}) content=(\\S+)$");
    private static final List<String> RECEIVED_GAME_MESSAGES = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean GAME_MESSAGE_LISTENER_REGISTERED = new AtomicBoolean();

    void runTest(ClientGameTestContext context) {
        registerGameMessageCapture();
        RECEIVED_GAME_MESSAGES.clear();
        String address = System.getProperty("branz.acceptance.server", "localhost:25565");
        connect(context, address);
        waitForServerHandshake(context);
        prepareMainInventoryGrantSlots(context);

        grantShield(context);
        Map<String, ItemAuthority> firstOnly =
                captureShieldSnapshot(context, 1, "PHYSICAL_AUTHORITY_SHIELD_D13_STATUS_FIRST_CLIENT");
        ItemAuthority first = only(firstOnly);
        requireLocation(first, FIRST_STORAGE_SLOT, "first");
        requireFreshShield(first, "first");
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D13_FIRST_READY_CLIENT");

        grantShield(context);
        Map<String, ItemAuthority> staged =
                captureShieldSnapshot(context, 2, "PHYSICAL_AUTHORITY_SHIELD_D13_STATUS_STAGED_CLIENT");
        ItemAuthority stagedFirst = staged.get(first.uuid());
        if (!first.equals(stagedFirst)) {
            throw new AssertionError(
                    "Granting second D1-D3 shield changed the first authority: first="
                            + first
                            + " observed="
                            + stagedFirst);
        }
        ItemAuthority second = staged.values().stream()
                .filter(row -> !row.uuid().equals(first.uuid()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Second D1-D3 shield is missing"));
        requireLocation(second, SECOND_STORAGE_SLOT, "second");
        requireFreshShield(second, "second");
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D13_SECOND_READY_CLIENT");

        freeHotbarSlot(context, SHIELD_HOTBAR_SLOT);
        freeHotbarSlot(context, UNEQUIP_HOTBAR_SLOT);

        moveShield(context, FIRST_STORAGE_SLOT, SHIELD_HOTBAR_SLOT);
        Map<String, ItemAuthority> firstMoved =
                waitForTransition(
                        context,
                        staged,
                        2,
                        snapshot ->
                                isSingleMove(
                                                staged.get(first.uuid()),
                                                snapshot.get(first.uuid()),
                                                inventoryLocation(SHIELD_HOTBAR_SLOT))
                                        && second.equals(snapshot.get(second.uuid())),
                        "PHYSICAL_AUTHORITY_SHIELD_D13_STATUS_FIRST_MOVED_CLIENT");
        ItemAuthority movedFirst = firstMoved.get(first.uuid());
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D13_FIRST_MOVED_CLIENT");

        selectShieldHotbar(context);
        context.getInput().pressKey(options -> options.keySwapOffhand);
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D13_EQUIP_F_SENT_CLIENT");
        Map<String, ItemAuthority> firstEquipped =
                waitForTransition(
                        context,
                        firstMoved,
                        2,
                        snapshot ->
                                isSingleMove(
                                                movedFirst,
                                                snapshot.get(first.uuid()),
                                                "NATIVE_EQUIPPED/OFF_HAND")
                                        && second.equals(snapshot.get(second.uuid())),
                        "PHYSICAL_AUTHORITY_SHIELD_D13_STATUS_EQUIPPED_CLIENT");
        ItemAuthority equippedFirst = firstEquipped.get(first.uuid());
        waitForClientOffhand(context, true);
        context.waitFor(
                client ->
                        client.player != null
                                && client.player
                                        .getInventory()
                                        .getItem(SHIELD_HOTBAR_SLOT)
                                        .isEmpty(),
                20 * 10);
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D13_EQUIPPED_CLIENT");

        moveShield(context, SECOND_STORAGE_SLOT, SHIELD_HOTBAR_SLOT);
        Map<String, ItemAuthority> secondMoved =
                waitForTransition(
                        context,
                        firstEquipped,
                        2,
                        snapshot ->
                                equippedFirst.equals(snapshot.get(first.uuid()))
                                        && isSingleMove(
                                                second,
                                                snapshot.get(second.uuid()),
                                                inventoryLocation(SHIELD_HOTBAR_SLOT)),
                        "PHYSICAL_AUTHORITY_SHIELD_D13_STATUS_SECOND_MOVED_CLIENT");
        ItemAuthority movedSecond = secondMoved.get(second.uuid());
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D13_SECOND_MOVED_CLIENT");

        selectShieldHotbar(context);
        context.getInput().pressKey(options -> options.keySwapOffhand);
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D13_SWAP_F_SENT_CLIENT");
        Map<String, ItemAuthority> swapped =
                waitForTransition(
                        context,
                        secondMoved,
                        2,
                        snapshot ->
                                isSingleMove(
                                                equippedFirst,
                                                snapshot.get(first.uuid()),
                                                inventoryLocation(SHIELD_HOTBAR_SLOT))
                                        && isSingleMove(
                                                movedSecond,
                                                snapshot.get(second.uuid()),
                                                "NATIVE_EQUIPPED/OFF_HAND")
                                        && snapshot.get(first.uuid())
                                                .transactionId()
                                                .equals(snapshot.get(second.uuid()).transactionId()),
                        "PHYSICAL_AUTHORITY_SHIELD_D13_STATUS_SWAPPED_CLIENT");
        ItemAuthority swappedFirst = swapped.get(first.uuid());
        ItemAuthority swappedSecond = swapped.get(second.uuid());
        waitForClientOffhand(context, true);
        context.waitFor(
                client ->
                        client.player != null
                                && hasShieldProjection(
                                        client.player
                                                .getInventory()
                                                .getItem(SHIELD_HOTBAR_SLOT)),
                20 * 10);
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D13_ATOMIC_SWAP_CLIENT");

        reconnect(context, address);
        context.waitFor(
                client ->
                        client.player != null
                                && hasShieldProjection(client.player.getOffhandItem())
                                && hasShieldProjection(
                                        client.player
                                                .getInventory()
                                                .getItem(SHIELD_HOTBAR_SLOT))
                                && client.player
                                        .getInventory()
                                        .getItem(CHRONICLE_HOTBAR_SLOT)
                                        .is(Items.WRITTEN_BOOK),
                20 * 30);
        Map<String, ItemAuthority> reconnect =
                captureShieldSnapshot(
                        context, 2, "PHYSICAL_AUTHORITY_SHIELD_D13_STATUS_RECONNECT_CLIENT");
        if (!swapped.equals(reconnect)) {
            throw new AssertionError(
                    "D1-D3 reconnect did not reconstruct byte-stable shield authority: swapped="
                            + swapped
                            + " reconnect="
                            + reconnect);
        }
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D13_RECONNECT_STABLE_CLIENT");

        selectEmptyUnequipHotbar(context);
        context.getInput().pressKey(options -> options.keySwapOffhand);
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D13_UNEQUIP_F_SENT_CLIENT");
        Map<String, ItemAuthority> unequipped =
                waitForTransition(
                        context,
                        reconnect,
                        2,
                        snapshot ->
                                swappedFirst.equals(snapshot.get(first.uuid()))
                                        && isSingleMove(
                                                swappedSecond,
                                                snapshot.get(second.uuid()),
                                                inventoryLocation(UNEQUIP_HOTBAR_SLOT)),
                        "PHYSICAL_AUTHORITY_SHIELD_D13_STATUS_UNEQUIPPED_CLIENT");
        waitForClientOffhand(context, false);
        context.waitFor(
                client ->
                        client.player != null
                                && hasShieldProjection(
                                        client.player
                                                .getInventory()
                                                .getItem(UNEQUIP_HOTBAR_SLOT)),
                20 * 10);
        Map<String, ItemAuthority> finalStable =
                captureShieldSnapshot(
                        context, 2, "PHYSICAL_AUTHORITY_SHIELD_D13_STATUS_FINAL_CLIENT");
        if (!unequipped.equals(finalStable)) {
            throw new AssertionError(
                    "D1-D3 final shield authority was not stable: unequipped="
                            + unequipped
                            + " final="
                            + finalStable);
        }
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D13_UNEQUIPPED_CLIENT");
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D13_COMPLETE_CLIENT");
        disconnectToTitle(context);
    }

    private static void waitForServerHandshake(ClientGameTestContext context) {
        context.waitFor(client -> client.level != null && client.player != null, CONNECTION_TIMEOUT_TICKS);
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.experienceLevel == SERVER_HANDSHAKE_LEVEL,
                20 * 30);
        context.waitFor(client -> client.gui.screen() == null, 20 * 30);
        context.waitFor(
                client ->
                        client.player != null
                                && client.player
                                        .getInventory()
                                        .getItem(CHRONICLE_HOTBAR_SLOT)
                                        .is(Items.WRITTEN_BOOK),
                20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D13_HANDSHAKE_CLIENT");
    }

    private static void prepareMainInventoryGrantSlots(ClientGameTestContext context) {
        for (int slot = 0; slot <= SHIELD_HOTBAR_SLOT; slot++) {
            sendCommand(context, "/item replace entity @s hotbar." + slot + " with minecraft:stone");
            int expectedSlot = slot;
            context.waitFor(
                    client ->
                            client.player != null
                                    && client.player
                                            .getInventory()
                                            .getItem(expectedSlot)
                                            .is(Items.STONE),
                    20 * 15);
        }
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D13_FILLER_READY_CLIENT");
    }

    private static void grantShield(ClientGameTestContext context) {
        sendCommand(context, "/mmo dev");
        context.waitFor(client -> menuContains(client.gui.screen(), DEV_MODULE_NAME), 20 * 10);
        clickMenuEntry(context, DEV_MODULE_NAME);
        context.waitFor(client -> menuContains(client.gui.screen(), DEFINITION_ID), 20 * 10);
        clickMenuEntry(context, DEFINITION_ID);
        context.waitTicks(20);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(null);
    }

    private static void freeHotbarSlot(ClientGameTestContext context, int slot) {
        sendCommand(context, "/item replace entity @s hotbar." + slot + " with minecraft:air");
        context.waitFor(
                client -> client.player != null && client.player.getInventory().getItem(slot).isEmpty(),
                20 * 15);
    }

    private static void moveShield(ClientGameTestContext context, int sourceSlot, int destinationSlot) {
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        context.waitFor(
                client -> {
                    if (!(client.gui.screen() instanceof InventoryScreen screen)
                            || client.player == null) {
                        return false;
                    }
                    Slot source = findPlayerSlot(screen, client.player.getInventory(), sourceSlot);
                    Slot destination =
                            findPlayerSlot(screen, client.player.getInventory(), destinationSlot);
                    return hasShieldProjection(source.getItem()) && destination.getItem().isEmpty();
                },
                20 * 10);
        setInventoryCursor(context, sourceSlot);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitFor(
                client -> {
                    if (!(client.gui.screen() instanceof InventoryScreen screen)
                            || client.player == null) {
                        return false;
                    }
                    Slot source = findPlayerSlot(screen, client.player.getInventory(), sourceSlot);
                    return source.getItem().isEmpty()
                            && hasShieldProjection(screen.getMenu().getCarried());
                },
                20 * 10);
        setInventoryCursor(context, destinationSlot);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        context.waitFor(
                client -> {
                    if (!(client.gui.screen() instanceof InventoryScreen screen)
                            || client.player == null) {
                        return false;
                    }
                    Slot destination =
                            findPlayerSlot(screen, client.player.getInventory(), destinationSlot);
                    return hasShieldProjection(destination.getItem())
                            && screen.getMenu().getCarried().isEmpty();
                },
                20 * 10);
        context.waitTicks(2);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(null);
    }

    private static void selectShieldHotbar(ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_8);
        context.waitFor(
                client -> client.player != null && hasShieldProjection(client.player.getMainHandItem()),
                20 * 10);
        context.waitTicks(2);
    }

    private static void selectEmptyUnequipHotbar(ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_7);
        context.waitFor(
                client -> client.player != null && client.player.getMainHandItem().isEmpty(),
                20 * 10);
        context.waitTicks(2);
    }

    private static void waitForClientOffhand(ClientGameTestContext context, boolean present) {
        context.waitFor(
                client ->
                        client.player != null
                                && (hasShieldProjection(client.player.getOffhandItem()) == present),
                20 * 15);
    }

    private static Map<String, ItemAuthority> captureShieldSnapshot(
            ClientGameTestContext context, int expectedCount, String marker) {
        for (int attempt = 0; attempt < 12; attempt++) {
            int firstNewMessage = RECEIVED_GAME_MESSAGES.size();
            sendCommand(context, "/mmo physical status");
            context.waitTicks(10);
            Map<String, ItemAuthority> snapshot = shieldStatusesSince(firstNewMessage);
            if (snapshot.size() == expectedCount) {
                System.out.println(marker);
                return snapshot;
            }
            context.waitTicks(10);
            snapshot = shieldStatusesSince(firstNewMessage);
            if (snapshot.size() == expectedCount) {
                System.out.println(marker);
                return snapshot;
            }
        }
        throw new AssertionError("Timed out waiting for " + expectedCount + " shield authority rows");
    }

    private static Map<String, ItemAuthority> waitForTransition(
            ClientGameTestContext context,
            Map<String, ItemAuthority> before,
            int expectedCount,
            Predicate<Map<String, ItemAuthority>> expectedTransition,
            String marker) {
        for (int attempt = 0; attempt < 16; attempt++) {
            int firstNewMessage = RECEIVED_GAME_MESSAGES.size();
            sendCommand(context, "/mmo physical status");
            context.waitTicks(10);
            Map<String, ItemAuthority> snapshot = shieldStatusesSince(firstNewMessage);
            if (snapshot.size() != expectedCount) {
                context.waitTicks(10);
                snapshot = shieldStatusesSince(firstNewMessage);
            }
            if (snapshot.size() != expectedCount) {
                continue;
            }
            if (snapshot.equals(before)) {
                context.waitTicks(10);
                continue;
            }
            if (expectedTransition.test(snapshot)) {
                System.out.println(marker);
                return snapshot;
            }
            throw new AssertionError(
                    "D1-D3 shield authority changed to an unexpected state: before="
                            + before
                            + " observed="
                            + snapshot);
        }
        throw new AssertionError("Timed out waiting for D1-D3 shield authority transition");
    }

    private static Map<String, ItemAuthority> shieldStatusesSince(int firstMessage) {
        Map<String, ItemAuthority> result = new HashMap<>();
        for (int index = firstMessage; index < RECEIVED_GAME_MESSAGES.size(); index++) {
            String message = RECEIVED_GAME_MESSAGES.get(index);
            if (!message.startsWith("ITEM uuid=") || !message.contains(" def=" + DEFINITION_ID + " ")) {
                continue;
            }
            ItemAuthority row = parseShieldStatus(message);
            result.put(row.uuid(), row);
        }
        return Map.copyOf(result);
    }

    private static ItemAuthority parseShieldStatus(String status) {
        Matcher match = SHIELD_STATUS.matcher(status);
        if (!match.matches()) {
            throw new AssertionError("Unexpected Training Shield authority status: " + status);
        }
        return new ItemAuthority(
                match.group(1).toLowerCase(),
                match.group(2),
                Integer.parseInt(match.group(3)),
                Integer.parseInt(match.group(4)),
                Integer.parseInt(match.group(5)),
                match.group(6).toLowerCase(),
                match.group(7));
    }

    private static boolean isSingleMove(
            ItemAuthority before, ItemAuthority after, String expectedLocation) {
        return before != null
                && after != null
                && before.uuid().equals(after.uuid())
                && before.contentVersion().equals(after.contentVersion())
                && before.currentDurability() == after.currentDurability()
                && before.maximumDurability() == after.maximumDurability()
                && after.version() == before.version() + 1
                && !before.transactionId().equals(after.transactionId())
                && expectedLocation.equals(after.location());
    }

    private static void requireLocation(ItemAuthority authority, int slot, String label) {
        if (authority == null || !inventoryLocation(slot).equals(authority.location())) {
            throw new AssertionError("D1-D3 " + label + " shield staged incorrectly: " + authority);
        }
    }

    private static void requireFreshShield(ItemAuthority authority, String label) {
        if (authority.currentDurability() != 180 || authority.maximumDurability() != 180) {
            throw new AssertionError("D1-D3 " + label + " shield is not fresh 180/180: " + authority);
        }
    }

    private static String inventoryLocation(int slot) {
        return "CHARACTER_INVENTORY/slot:" + slot;
    }

    private static ItemAuthority only(Map<String, ItemAuthority> snapshot) {
        if (snapshot.size() != 1) {
            throw new AssertionError("Expected exactly one D1-D3 shield: " + snapshot);
        }
        return snapshot.values().iterator().next();
    }

    private static void clickMenuEntry(ClientGameTestContext context, String namePrefix) {
        setDevMenuCursor(context, namePrefix);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
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

    private static boolean hasShieldProjection(net.minecraft.world.item.ItemStack stack) {
        return !stack.isEmpty() && DEFINITION_ID.equals(stack.getHoverName().getString());
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

    private static void reconnect(ClientGameTestContext context, String address) {
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() != null, 20 * 5);
        context.clickScreenButton("menu.disconnect");
        context.waitFor(client -> client.level == null && client.player == null, 20 * 15);
        connect(context, address);
        context.waitFor(client -> client.level != null && client.player != null, CONNECTION_TIMEOUT_TICKS);
        context.waitFor(client -> client.gui.screen() == null, 20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D13_RECONNECTED_CLIENT");
    }

    private static void connect(ClientGameTestContext context, String address) {
        context.runOnClient(
                client -> {
                    ServerData server =
                            new ServerData(
                                    "Branz physical shield D1-D3 acceptance",
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

    private static void disconnectToTitle(ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() != null, 20 * 5);
        context.clickScreenButton("menu.disconnect");
        context.waitFor(client -> client.level == null && client.player == null, 20 * 15);
        context.setScreen(TitleScreen::new);
        context.waitForScreen(TitleScreen.class);
    }

    private record ItemAuthority(
            String uuid,
            String location,
            int version,
            int currentDurability,
            int maximumDurability,
            String transactionId,
            String contentVersion) {}
}
