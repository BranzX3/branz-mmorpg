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
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

/** Section F: Chronicle must preserve native physical authority while virtual equipment still commits. */
final class PhysicalChronicleBoundaryClientGameTest {
    private static final int SERVER_HANDSHAKE_LEVEL = 7;
    private static final int SWORD_HOTBAR_SLOT = 7;
    private static final int SHIELD_HOTBAR_SLOT = 6;
    private static final int QUIVER_HOTBAR_SLOT = 5;
    private static final int CHRONICLE_HOTBAR_SLOT = 8;
    private static final int CONNECTION_TIMEOUT_TICKS = 20 * 60;
    private static final int CONTAINER_IMAGE_WIDTH = 176;
    private static final int CONTAINER_IMAGE_HEIGHT = 222;
    private static final double SLOT_HITBOX_SIZE = 16.0;
    private static final double SLOT_CENTER_OFFSET = SLOT_HITBOX_SIZE / 2.0;

    private static final String SWORD_ID = "weapon.training_sword";
    private static final String SHIELD_ID = "equipment.training_shield";
    private static final String QUIVER_ID = "equipment.training_quiver";
    private static final String DEV_MODULE_NAME = "Persisted Test Item";
    private static final String EQUIPMENT_PAGE = "Character & Equipment";
    private static final String CONFIRM_SCENE = "Confirm Scene transaction";
    private static final String EQUIPMENT_COMMITTED = "Equipment committed.";

    private static final List<String> RECEIVED_GAME_MESSAGES = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean GAME_MESSAGE_LISTENER_REGISTERED = new AtomicBoolean();

    void runTest(ClientGameTestContext context) {
        registerGameMessageCapture();
        RECEIVED_GAME_MESSAGES.clear();
        String address = System.getProperty("branz.acceptance.server", "localhost:25565");

        connect(context, address);
        waitForServerHandshake(context);
        stageAcceptanceItems(context);

        PhysicalAuthority baseline =
                capturePhysicalAuthority(
                        context, "PHYSICAL_AUTHORITY_CHRONICLE_F_STATUS_BEFORE_CLIENT");
        String quiverBefore =
                captureStatusLine(
                        context,
                        QUIVER_ID,
                        "PHYSICAL_AUTHORITY_CHRONICLE_F_QUIVER_BEFORE_CLIENT");
        requireLocation(
                quiverBefore,
                "CHARACTER_INVENTORY/slot:" + QUIVER_HOTBAR_SLOT,
                "staged quiver");

        openChronicle(context);
        clickChronicleMenuEntry(context, EQUIPMENT_PAGE);
        context.waitFor(
                client ->
                        client.player != null
                                && chronicleMenuContains(
                                        client.gui.screen(),
                                        client.player.getInventory(),
                                        QUIVER_ID),
                20 * 10);
        context.waitTicks(5);
        boolean nativeEntryExposed =
                context.computeOnClient(
                        client -> {
                            if (client.player == null) {
                                throw new AssertionError(
                                        "Player must be present while checking Chronicle entries");
                            }
                            Object playerInventory = client.player.getInventory();
                            return chronicleMenuContains(
                                            client.gui.screen(), playerInventory, SWORD_ID)
                                    || chronicleMenuContains(
                                            client.gui.screen(), playerInventory, SHIELD_ID);
                        });
        if (nativeEntryExposed) {
            throw new AssertionError(
                    "Chronicle exposed a native physical sword/shield entry in its owned menu slots");
        }
        System.out.println(
                "PHYSICAL_AUTHORITY_CHRONICLE_F_NATIVE_REJECTED_CLIENT mode=not-exposed");

        closeContainer(context);
        PhysicalAuthority afterNativeBoundary =
                capturePhysicalAuthority(
                        context, "PHYSICAL_AUTHORITY_CHRONICLE_F_STATUS_AFTER_NATIVE_REJECT_CLIENT");
        requireSamePhysical(
                baseline,
                afterNativeBoundary,
                "Chronicle native-slot UI boundary changed physical authority");

        openChronicle(context);
        clickChronicleMenuEntry(context, EQUIPMENT_PAGE);
        context.waitFor(
                client ->
                        client.player != null
                                && chronicleMenuContains(
                                        client.gui.screen(),
                                        client.player.getInventory(),
                                        QUIVER_ID),
                20 * 10);
        clickChronicleMenuEntry(context, QUIVER_ID);
        context.waitFor(
                client ->
                        client.player != null
                                && chronicleMenuContains(
                                        client.gui.screen(),
                                        client.player.getInventory(),
                                        CONFIRM_SCENE),
                20 * 10);

        int commitStart = RECEIVED_GAME_MESSAGES.size();
        clickChronicleMenuEntry(context, CONFIRM_SCENE);
        context.waitFor(client -> messageEqualsSince(commitStart, EQUIPMENT_COMMITTED), 20 * 15);
        System.out.println("PHYSICAL_AUTHORITY_CHRONICLE_F_VIRTUAL_COMMIT_CLIENT");

        closeContainer(context);
        String quiverCommitted =
                captureStatusLine(
                        context,
                        QUIVER_ID,
                        "PHYSICAL_AUTHORITY_CHRONICLE_F_QUIVER_COMMITTED_CLIENT");
        requireLocation(quiverCommitted, "VIRTUAL_EQUIPPED/QUIVER", "committed quiver");
        if (quiverBefore.equals(quiverCommitted)) {
            throw new AssertionError("Virtual QUIVER Chronicle transaction did not change authority");
        }

        PhysicalAuthority afterVirtualCommit =
                capturePhysicalAuthority(
                        context, "PHYSICAL_AUTHORITY_CHRONICLE_F_STATUS_AFTER_VIRTUAL_COMMIT_CLIENT");
        requireSamePhysical(
                baseline,
                afterVirtualCommit,
                "Supported virtual Chronicle commit changed physical sword/shield authority");

        openChronicle(context);
        System.out.println("PHYSICAL_AUTHORITY_CHRONICLE_F_REOPENED_CLIENT");
        closeContainer(context);
        PhysicalAuthority afterReopen =
                capturePhysicalAuthority(
                        context, "PHYSICAL_AUTHORITY_CHRONICLE_F_STATUS_AFTER_REOPEN_CLIENT");
        requireSamePhysical(
                baseline,
                afterReopen,
                "Closing and reopening Chronicle changed physical authority");

        reconnect(context, address);
        waitForServerHandshake(context);
        PhysicalAuthority afterReconnect =
                capturePhysicalAuthority(
                        context, "PHYSICAL_AUTHORITY_CHRONICLE_F_STATUS_RECONNECT_CLIENT");
        requireSamePhysical(
                baseline,
                afterReconnect,
                "Reconnect did not reconstruct byte-stable physical authority");
        String quiverReconnect =
                captureStatusLine(
                        context,
                        QUIVER_ID,
                        "PHYSICAL_AUTHORITY_CHRONICLE_F_QUIVER_RECONNECT_CLIENT");
        requireLocation(quiverReconnect, "VIRTUAL_EQUIPPED/QUIVER", "reconnected quiver");

        System.out.println("PHYSICAL_AUTHORITY_CHRONICLE_F_COMPLETE_CLIENT");
        disconnectToTitle(context);
    }

    private static void stageAcceptanceItems(ClientGameTestContext context) {
        fillGameplayHotbar(context);
        freeHotbarSlot(context, SWORD_HOTBAR_SLOT);
        grantItem(context, SWORD_ID, SWORD_HOTBAR_SLOT);
        freeHotbarSlot(context, SHIELD_HOTBAR_SLOT);
        grantItem(context, SHIELD_ID, SHIELD_HOTBAR_SLOT);
        freeHotbarSlot(context, QUIVER_HOTBAR_SLOT);
        grantItem(context, QUIVER_ID, QUIVER_HOTBAR_SLOT);
        context.waitFor(
                client ->
                        client.player != null
                                && hasProjection(
                                        client.player.getInventory().getItem(SWORD_HOTBAR_SLOT),
                                        SWORD_ID)
                                && hasProjection(
                                        client.player.getInventory().getItem(SHIELD_HOTBAR_SLOT),
                                        SHIELD_ID)
                                && hasProjection(
                                        client.player.getInventory().getItem(QUIVER_HOTBAR_SLOT),
                                        QUIVER_ID)
                                && client.player
                                        .getInventory()
                                        .getItem(CHRONICLE_HOTBAR_SLOT)
                                        .is(Items.WRITTEN_BOOK),
                20 * 15);
        System.out.println("PHYSICAL_AUTHORITY_CHRONICLE_F_ITEMS_READY_CLIENT");
    }

    private static void fillGameplayHotbar(ClientGameTestContext context) {
        for (int slot = 0; slot < CHRONICLE_HOTBAR_SLOT; slot++) {
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
    }

    private static void freeHotbarSlot(ClientGameTestContext context, int slot) {
        sendCommand(context, "/item replace entity @s hotbar." + slot + " with minecraft:air");
        context.waitFor(
                client -> client.player != null && client.player.getInventory().getItem(slot).isEmpty(),
                20 * 15);
    }

    private static void grantItem(
            ClientGameTestContext context, String definitionId, int expectedSlot) {
        sendCommand(context, "/mmo dev");
        context.waitFor(client -> menuContains(client.gui.screen(), DEV_MODULE_NAME), 20 * 10);
        clickMenuEntry(context, DEV_MODULE_NAME);
        context.waitFor(client -> menuContains(client.gui.screen(), definitionId), 20 * 10);
        clickMenuEntry(context, definitionId);
        context.waitFor(
                client ->
                        client.player != null
                                && hasProjection(
                                        client.player.getInventory().getItem(expectedSlot),
                                        definitionId),
                20 * 15);
        context.waitTicks(2);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(null);
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
                client ->
                        client.player != null
                                && chronicleMenuContains(
                                        client.gui.screen(),
                                        client.player.getInventory(),
                                        EQUIPMENT_PAGE),
                20 * 10);
        System.out.println("PHYSICAL_AUTHORITY_CHRONICLE_F_OPENED_CLIENT");
    }

    private static void closeContainer(ClientGameTestContext context) {
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(null);
        context.waitTicks(5);
    }

    private static PhysicalAuthority capturePhysicalAuthority(
            ClientGameTestContext context, String marker) {
        String sword = probeStatusLine(context, SWORD_ID);
        String shield = probeStatusLine(context, SHIELD_ID);
        System.out.println(marker);
        return new PhysicalAuthority(sword, shield);
    }

    private static String captureStatusLine(
            ClientGameTestContext context, String definitionId, String marker) {
        String status = probeStatusLine(context, definitionId);
        System.out.println(marker);
        return status;
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
        throw new AssertionError("Timed out waiting for physical authority status: " + definitionId);
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

    private static void requireLocation(String status, String location, String label) {
        if (!status.contains(" loc=" + location + " ")) {
            throw new AssertionError(label + " has unexpected authority line: " + status);
        }
    }

    private static void requireSamePhysical(
            PhysicalAuthority expected, PhysicalAuthority actual, String detail) {
        if (!expected.equals(actual)) {
            throw new AssertionError(detail + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static boolean messageEqualsSince(int firstMessage, String expected) {
        for (int index = RECEIVED_GAME_MESSAGES.size() - 1; index >= firstMessage; index--) {
            if (expected.equals(RECEIVED_GAME_MESSAGES.get(index))) {
                return true;
            }
        }
        return false;
    }

    private static boolean menuContains(Object candidate, String namePrefix) {
        if (!(candidate instanceof AbstractContainerScreen<?> screen)) {
            return false;
        }
        return screen.getMenu().slots.stream().anyMatch(slot -> menuEntryMatches(slot, namePrefix));
    }

    private static boolean chronicleMenuContains(
            Object candidate, Object playerInventory, String namePrefix) {
        if (!(candidate instanceof AbstractContainerScreen<?> screen) || playerInventory == null) {
            return false;
        }
        return screen.getMenu().slots.stream()
                .anyMatch(
                        slot ->
                                slot.container != playerInventory
                                        && menuEntryMatches(slot, namePrefix));
    }

    private static boolean menuEntryMatches(Slot slot, String namePrefix) {
        return slot.hasItem()
                && slot.getItem().getHoverName().getString().startsWith(namePrefix);
    }

    private static void clickMenuEntry(ClientGameTestContext context, String namePrefix) {
        setMenuCursor(context, namePrefix);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void clickChronicleMenuEntry(
            ClientGameTestContext context, String namePrefix) {
        setChronicleMenuCursor(context, namePrefix);
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
    }

    private static void setMenuCursor(ClientGameTestContext context, String namePrefix) {
        double[] target =
                context.computeOnClient(
                        client -> {
                            if (!(client.gui.screen()
                                    instanceof AbstractContainerScreen<?> screen)) {
                                throw new AssertionError("Expected an open container for " + namePrefix);
                            }
                            Slot slot = findMenuEntry(screen, namePrefix);
                            return cursorTarget(client, slot);
                        });
        context.getInput().setCursorPos(target[0], target[1]);
        assertCursorInsideSlot(context, target, namePrefix);
    }

    private static void setChronicleMenuCursor(
            ClientGameTestContext context, String namePrefix) {
        double[] target =
                context.computeOnClient(
                        client -> {
                            if (!(client.gui.screen()
                                    instanceof AbstractContainerScreen<?> screen)
                                    || client.player == null) {
                                throw new AssertionError(
                                        "Expected an open Chronicle container for " + namePrefix);
                            }
                            Slot slot =
                                    findChronicleMenuEntry(
                                            screen, client.player.getInventory(), namePrefix);
                            return cursorTarget(client, slot);
                        });
        context.getInput().setCursorPos(target[0], target[1]);
        assertCursorInsideSlot(context, target, namePrefix);
    }

    private static double[] cursorTarget(
            net.minecraft.client.Minecraft client, Slot slot) {
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
            guiX,
            guiY,
            slot.x,
            slot.y,
            left,
            top
        };
    }

    private static Slot findMenuEntry(AbstractContainerScreen<?> screen, String namePrefix) {
        return screen.getMenu().slots.stream()
                .filter(slot -> menuEntryMatches(slot, namePrefix))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Menu entry not found: " + namePrefix));
    }

    private static Slot findChronicleMenuEntry(
            AbstractContainerScreen<?> screen, Object playerInventory, String namePrefix) {
        return screen.getMenu().slots.stream()
                .filter(
                        slot ->
                                slot.container != playerInventory
                                        && menuEntryMatches(slot, namePrefix))
                .findFirst()
                .orElseThrow(
                        () ->
                                new AssertionError(
                                        "Chronicle-owned menu entry not found: " + namePrefix));
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
        double slotLeft = target[6] + target[4];
        double slotTop = target[7] + target[5];
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
        System.out.println("PHYSICAL_AUTHORITY_CHRONICLE_F_HANDSHAKE_CLIENT");
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
                                    "Branz Chronicle boundary acceptance",
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

    private record PhysicalAuthority(String sword, String shield) {}
}
