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
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/** Sections D4 and D6: real guarded shield wear plus Staff ownership of swap-hands/F input. */
final class PhysicalShieldD46ClientGameTest {
    private static final int SERVER_HANDSHAKE_LEVEL = 7;
    private static final int SWORD_HOTBAR_SLOT = 7;
    private static final int SHIELD_HOTBAR_SLOT = 6;
    private static final int STAFF_HOTBAR_SLOT = 5;
    private static final int CHRONICLE_HOTBAR_SLOT = 8;
    private static final int CONNECTION_TIMEOUT_TICKS = 20 * 60;
    private static final int DEV_CONTAINER_IMAGE_WIDTH = 176;
    private static final int DEV_CONTAINER_IMAGE_HEIGHT = 222;
    private static final double SLOT_HITBOX_SIZE = 16.0;
    private static final double SLOT_CENTER_OFFSET = SLOT_HITBOX_SIZE / 2.0;
    private static final String SWORD_ID = "weapon.training_sword";
    private static final String SHIELD_ID = "equipment.training_shield";
    private static final String STAFF_ID = "weapon.training_staff";
    private static final String DEV_MODULE_NAME = "Persisted Test Item";
    private static final String GRANT_SUCCESS_MESSAGE =
            "Persisted test value x1 granted and projected.";
    private static final String GUARD_READY_MESSAGE = "WEAPON GUARD";
    private static final String STAFF_EMPTY_MESSAGE = "ATTUNE A STAFF SPELL AT REST";
    private static final String SOURCE_TAG = "branz_d46_source";
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
        fillGameplayHotbar(context);

        // D4/D6 are not inventory-movement acceptance. D1-D3 already proves physical LMB
        // movement. Open one destination at a time so the normal persisted grant path projects
        // each signed unique item directly into its intended gameplay hotbar slot.
        freeHotbarSlot(context, SWORD_HOTBAR_SLOT);
        grantItem(context, SWORD_ID, SWORD_HOTBAR_SLOT);
        freeHotbarSlot(context, SHIELD_HOTBAR_SLOT);
        grantItem(context, SHIELD_ID, SHIELD_HOTBAR_SLOT);
        freeHotbarSlot(context, STAFF_HOTBAR_SLOT);
        grantItem(context, STAFF_ID, STAFF_HOTBAR_SLOT);

        ItemAuthority staged =
                captureShield(context, "PHYSICAL_AUTHORITY_SHIELD_D46_STATUS_STAGED_CLIENT");
        requireShield(
                staged,
                inventoryLocation(SHIELD_HOTBAR_SLOT),
                180,
                180,
                "staged");
        context.waitFor(
                client ->
                        client.player != null
                                && hasProjection(
                                        client.player
                                                .getInventory()
                                                .getItem(SWORD_HOTBAR_SLOT),
                                        SWORD_ID)
                                && hasProjection(
                                        client.player
                                                .getInventory()
                                                .getItem(SHIELD_HOTBAR_SLOT),
                                        SHIELD_ID)
                                && hasProjection(
                                        client.player
                                                .getInventory()
                                                .getItem(STAFF_HOTBAR_SLOT),
                                        STAFF_ID),
                20 * 10);
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D46_ITEMS_READY_CLIENT");

        selectHotbar(context, SHIELD_HOTBAR_SLOT, SHIELD_ID);
        context.getInput().pressKey(options -> options.keySwapOffhand);
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D46_EQUIP_F_SENT_CLIENT");
        ItemAuthority equipped =
                waitForShieldTransition(
                        context,
                        staged,
                        candidate ->
                                isLocationMove(
                                        staged, candidate, "NATIVE_EQUIPPED/OFF_HAND"),
                        "PHYSICAL_AUTHORITY_SHIELD_D46_STATUS_EQUIPPED_CLIENT");
        waitForShieldOffhand(context);
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D46_EQUIPPED_CLIENT");

        selectHotbar(context, SWORD_HOTBAR_SLOT, SWORD_ID);
        context.waitTicks(60);
        sendCommand(
                context,
                "/execute at @s run summon minecraft:husk ^ ^ ^12 {PersistenceRequired:1b,Silent:1b,Tags:[\""
                        + SOURCE_TAG
                        + "\"]}");
        sendCommand(
                context,
                "/effect give @e[tag="
                        + SOURCE_TAG
                        + ",limit=1] minecraft:slowness infinite 255 true");
        context.waitTicks(40);
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D46_SOURCE_STAGED_CLIENT");

        // Guard is legal only while ENGAGED. Let the real hostile produce one ordinary melee HIT,
        // which production records as hostile activity, then move it back out before arming guard.
        int firstPrimerMessage = RECEIVED_GAME_MESSAGES.size();
        sendCommand(
                context,
                "/execute at @s run tp @e[tag=" + SOURCE_TAG + ",limit=1] ^ ^ ^1.5");
        context.waitFor(client -> hitOutcomeSince(firstPrimerMessage), 20 * 15);
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D46_ENGAGEMENT_PRIMER_HIT_CLIENT");
        sendCommand(
                context,
                "/execute at @s run tp @e[tag=" + SOURCE_TAG + ",limit=1] ^ ^ ^12");
        context.waitTicks(10);

        int firstGuardMessage = RECEIVED_GAME_MESSAGES.size();
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        context.waitFor(
                client -> messageEqualsSince(firstGuardMessage, GUARD_READY_MESSAGE), 20 * 10);
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D46_GUARD_ACTIVE_CLIENT");

        ItemAuthority beforeImpact =
                captureShield(
                        context,
                        "PHYSICAL_AUTHORITY_SHIELD_D46_STATUS_BEFORE_IMPACT_CLIENT");
        requireSameAuthority(equipped, beforeImpact, "guard activation changed shield authority");
        int firstImpactMessage = RECEIVED_GAME_MESSAGES.size();
        sendCommand(
                context,
                "/execute at @s run tp @e[tag=" + SOURCE_TAG + ",limit=1] ^ ^ ^1.5");
        context.waitFor(client -> guardedOutcomeSince(firstImpactMessage), 20 * 15);
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D46_REAL_BLOCKED_IMPACT_CLIENT");
        sendCommand(context, "/kill @e[tag=" + SOURCE_TAG + "]");

        ItemAuthority worn =
                waitForShieldTransition(
                        context,
                        beforeImpact,
                        candidate -> isOnePointWear(beforeImpact, candidate),
                        "PHYSICAL_AUTHORITY_SHIELD_D46_STATUS_WORN_CLIENT");
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D46_WORN_ONCE_CLIENT");
        context.waitTicks(40);
        ItemAuthority stableAfterImpact =
                captureShield(
                        context,
                        "PHYSICAL_AUTHORITY_SHIELD_D46_STATUS_WORN_STABLE_CLIENT");
        requireSameAuthority(
                worn,
                stableAfterImpact,
                "one real blocked impact spent shield durability more than once");
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D46_NO_DOUBLE_SPEND_CLIENT");

        selectHotbar(context, STAFF_HOTBAR_SLOT, STAFF_ID);
        context.waitTicks(20);
        int firstStaffMessage = RECEIVED_GAME_MESSAGES.size();
        context.getInput().pressKey(options -> options.keySwapOffhand);
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D46_STAFF_F_SENT_CLIENT");
        context.waitFor(client -> staffOwnershipMessageSince(firstStaffMessage), 20 * 10);
        context.waitFor(
                client ->
                        client.player != null
                                && hasProjection(client.player.getMainHandItem(), STAFF_ID)
                                && hasProjection(client.player.getOffhandItem(), SHIELD_ID),
                20 * 10);
        ItemAuthority afterStaffF =
                captureShield(
                        context,
                        "PHYSICAL_AUTHORITY_SHIELD_D46_STATUS_AFTER_STAFF_F_CLIENT");
        requireSameAuthority(worn, afterStaffF, "Staff F changed authoritative shield OFF_HAND state");
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D46_STAFF_OWNS_F_CLIENT");
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D46_COMPLETE_CLIENT");
        disconnectToTitle(context);
    }

    private static void waitForServerHandshake(ClientGameTestContext context) {
        context.waitFor(
                client -> client.level != null && client.player != null, CONNECTION_TIMEOUT_TICKS);
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
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D46_HANDSHAKE_CLIENT");
    }

    private static void fillGameplayHotbar(ClientGameTestContext context) {
        for (int slot = 0; slot <= SWORD_HOTBAR_SLOT; slot++) {
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
        System.out.println("PHYSICAL_AUTHORITY_SHIELD_D46_FILLER_READY_CLIENT");
    }

    private static void grantItem(
            ClientGameTestContext context, String definitionId, int expectedSlot) {
        sendCommand(context, "/mmo dev");
        context.waitFor(client -> menuContains(client.gui.screen(), DEV_MODULE_NAME), 20 * 10);
        clickMenuEntry(context, DEV_MODULE_NAME);
        context.waitFor(client -> menuContains(client.gui.screen(), definitionId), 20 * 10);
        int firstNewMessage = RECEIVED_GAME_MESSAGES.size();
        boolean confirmed = false;
        for (int attempt = 0; attempt < 3; attempt++) {
            clickMenuEntry(context, definitionId);
            context.waitTicks(40);
            confirmed =
                    grantSucceededSince(firstNewMessage)
                            || context.computeOnClient(
                                    client ->
                                            client.player != null
                                                    && hasProjection(
                                                            client.player
                                                                    .getInventory()
                                                                    .getItem(expectedSlot),
                                                            definitionId));
            if (confirmed) {
                break;
            }
        }
        if (!confirmed) {
            throw new AssertionError(
                    "D4-D6 persisted grant was not confirmed: definition="
                            + definitionId
                            + " slot="
                            + expectedSlot);
        }
        context.waitFor(
                client ->
                        client.player != null
                                && hasProjection(
                                        client.player.getInventory().getItem(expectedSlot),
                                        definitionId),
                20 * 10);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(null);
    }

    private static boolean grantSucceededSince(int firstMessage) {
        return messageEqualsSince(firstMessage, GRANT_SUCCESS_MESSAGE);
    }

    private static void freeHotbarSlot(ClientGameTestContext context, int slot) {
        sendCommand(context, "/item replace entity @s hotbar." + slot + " with minecraft:air");
        context.waitFor(
                client -> client.player != null && client.player.getInventory().getItem(slot).isEmpty(),
                20 * 15);
    }

    private static void selectHotbar(
            ClientGameTestContext context, int slot, String definitionId) {
        context.getInput().pressKey(options -> options.keyHotbarSlots[slot]);
        context.waitFor(
                client ->
                        client.player != null
                                && hasProjection(client.player.getMainHandItem(), definitionId),
                20 * 10);
        context.waitTicks(2);
    }

    private static void waitForShieldOffhand(ClientGameTestContext context) {
        context.waitFor(
                client ->
                        client.player != null
                                && hasProjection(client.player.getOffhandItem(), SHIELD_ID),
                20 * 15);
    }

    private static ItemAuthority captureShield(ClientGameTestContext context, String marker) {
        ItemAuthority value = probeShield(context);
        System.out.println(marker);
        return value;
    }

    private static ItemAuthority probeShield(ClientGameTestContext context) {
        for (int attempt = 0; attempt < 12; attempt++) {
            int firstNewMessage = RECEIVED_GAME_MESSAGES.size();
            sendCommand(context, "/mmo physical status");
            context.waitTicks(10);
            String status = shieldStatusSince(firstNewMessage);
            if (status != null) {
                return parseShieldStatus(status);
            }
            context.waitTicks(10);
            status = shieldStatusSince(firstNewMessage);
            if (status != null) {
                return parseShieldStatus(status);
            }
        }
        throw new AssertionError("Timed out waiting for Training Shield authority status");
    }

    private static ItemAuthority waitForShieldTransition(
            ClientGameTestContext context,
            ItemAuthority before,
            java.util.function.Predicate<ItemAuthority> expected,
            String marker) {
        for (int attempt = 0; attempt < 16; attempt++) {
            ItemAuthority snapshot = probeShield(context);
            if (snapshot.equals(before)) {
                context.waitTicks(10);
                continue;
            }
            if (expected.test(snapshot)) {
                System.out.println(marker);
                return snapshot;
            }
            throw new AssertionError(
                    "D4-D6 shield authority changed unexpectedly: before="
                            + before
                            + " observed="
                            + snapshot);
        }
        throw new AssertionError("Timed out waiting for D4-D6 shield authority transition");
    }

    private static String shieldStatusSince(int firstMessage) {
        for (int index = RECEIVED_GAME_MESSAGES.size() - 1; index >= firstMessage; index--) {
            String message = RECEIVED_GAME_MESSAGES.get(index);
            if (message.startsWith("ITEM uuid=")
                    && message.contains(" def=" + SHIELD_ID + " ")) {
                return message;
            }
        }
        return null;
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

    private static boolean isLocationMove(
            ItemAuthority before, ItemAuthority after, String location) {
        return before.uuid().equals(after.uuid())
                && before.contentVersion().equals(after.contentVersion())
                && before.currentDurability() == after.currentDurability()
                && before.maximumDurability() == after.maximumDurability()
                && after.version() == before.version() + 1
                && !before.transactionId().equals(after.transactionId())
                && location.equals(after.location());
    }

    private static boolean isOnePointWear(ItemAuthority before, ItemAuthority after) {
        return before.uuid().equals(after.uuid())
                && before.location().equals(after.location())
                && before.contentVersion().equals(after.contentVersion())
                && before.maximumDurability() == after.maximumDurability()
                && before.currentDurability() == 180
                && after.currentDurability() == 179
                && after.version() == before.version() + 1
                && !before.transactionId().equals(after.transactionId());
    }

    private static void requireShield(
            ItemAuthority value, String location, int current, int maximum, String label) {
        if (!location.equals(value.location())
                || value.currentDurability() != current
                || value.maximumDurability() != maximum) {
            throw new AssertionError("D4-D6 " + label + " shield is invalid: " + value);
        }
    }

    private static void requireSameAuthority(
            ItemAuthority expected, ItemAuthority actual, String detail) {
        if (!expected.equals(actual)) {
            throw new AssertionError(detail + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static boolean hitOutcomeSince(int firstMessage) {
        for (int index = firstMessage; index < RECEIVED_GAME_MESSAGES.size(); index++) {
            if (RECEIVED_GAME_MESSAGES.get(index).startsWith("HIT ")) {
                return true;
            }
        }
        return false;
    }

    private static boolean guardedOutcomeSince(int firstMessage) {
        for (int index = firstMessage; index < RECEIVED_GAME_MESSAGES.size(); index++) {
            String message = RECEIVED_GAME_MESSAGES.get(index);
            if (message.startsWith("PERFECT_GUARD")
                    || message.startsWith("GUARDED ")
                    || message.startsWith("GUARD_BREAK ")) {
                return true;
            }
        }
        return false;
    }

    private static boolean staffOwnershipMessageSince(int firstMessage) {
        for (int index = firstMessage; index < RECEIVED_GAME_MESSAGES.size(); index++) {
            String message = RECEIVED_GAME_MESSAGES.get(index);
            if (message.startsWith("SELECTED ")
                    || STAFF_EMPTY_MESSAGE.equals(message)
                    || "SPELL SELECTION LOCKED".equals(message)) {
                return true;
            }
        }
        return false;
    }

    private static boolean messageEqualsSince(int firstMessage, String expected) {
        for (int index = firstMessage; index < RECEIVED_GAME_MESSAGES.size(); index++) {
            if (expected.equals(RECEIVED_GAME_MESSAGES.get(index))) {
                return true;
            }
        }
        return false;
    }

    private static void clickMenuEntry(ClientGameTestContext context, String namePrefix) {
        setDevMenuCursor(context, namePrefix);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void setDevMenuCursor(ClientGameTestContext context, String namePrefix) {
        double[] target =
                context.computeOnClient(
                        client -> {
                            if (!(client.gui.screen()
                                    instanceof AbstractContainerScreen<?> screen)) {
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

    private static Slot findMenuEntry(
            AbstractContainerScreen<?> screen, String namePrefix) {
        return screen.getMenu().slots.stream()
                .filter(
                        slot ->
                                slot.hasItem()
                                        && slot.getItem()
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
                        slot ->
                                slot.hasItem()
                                        && slot.getItem()
                                                .getHoverName()
                                                .getString()
                                                .startsWith(namePrefix));
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

    private static void connect(ClientGameTestContext context, String address) {
        context.runOnClient(
                client -> {
                    ServerData server =
                            new ServerData(
                                    "Branz physical shield D4-D6 acceptance",
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
        sendCommand(context, "/kill @e[tag=" + SOURCE_TAG + "]");
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitFor(client -> client.gui.screen() != null, 20 * 5);
        context.clickScreenButton("menu.disconnect");
        context.waitFor(client -> client.level == null && client.player == null, 20 * 15);
        context.setScreen(TitleScreen::new);
        context.waitForScreen(TitleScreen.class);
    }

    private static String inventoryLocation(int slot) {
        return "CHARACTER_INVENTORY/slot:" + slot;
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
