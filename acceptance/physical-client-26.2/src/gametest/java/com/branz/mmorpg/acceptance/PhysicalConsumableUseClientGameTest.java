package com.branz.mmorpg.acceptance;

import java.util.List;
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

/** Section C3: physically select and use one authoritative consumable from the gameplay hotbar. */
final class PhysicalConsumableUseClientGameTest {
    private static final int SERVER_HANDSHAKE_LEVEL = 7;
    private static final int SOURCE_STORAGE_SLOT = 9;
    private static final int TARGET_HOTBAR_SLOT = 7;
    private static final int CHRONICLE_HOTBAR_SLOT = 8;
    private static final int EXPECTED_QUANTITY = 64;
    private static final int CONNECTION_TIMEOUT_TICKS = 20 * 60;
    private static final int PREPARATION_CLICK_ATTEMPTS = 4;
    private static final int PREPARATION_RETRY_STABLE_TICKS = 6;
    private static final int PREPARATION_TRANSITION_TIMEOUT_TICKS = 20 * 5;
    private static final int INVENTORY_IMAGE_WIDTH = 176;
    private static final int INVENTORY_IMAGE_HEIGHT = 166;
    private static final int DEV_CONTAINER_IMAGE_WIDTH = 176;
    private static final int DEV_CONTAINER_IMAGE_HEIGHT = 222;
    private static final double SLOT_HITBOX_SIZE = 16.0;
    private static final double SLOT_CENTER_OFFSET = SLOT_HITBOX_SIZE / 2.0;
    private static final String DEFINITION_ID = "consumable.training_body_tonic";
    private static final String DEV_MODULE_NAME = "Persisted Test Item";
    private static final String GRANT_LABEL_PREFIX = DEFINITION_ID + " (Shift = lot x64)";
    private static final String WINDUP_PREFIX = "CONSUMABLE BODY_TONIC WINDUP ";
    private static final String COMMITTED_PREFIX = "CONSUMABLE BODY_TONIC COMMITTED | effect=";
    private static final String RECOVERY_COMPLETE = "CONSUMABLE RECOVERY COMPLETE";
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
        context.waitTicks(20);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(null);

        LotAuthority staged =
                parseTonicStatus(
                        sendStatusAndCaptureTonic(
                                context, "PHYSICAL_AUTHORITY_CONSUMABLE_USE_STATUS_STAGED_CLIENT"));
        if (!"CHARACTER_INVENTORY/slot:9".equals(staged.location())
                || staged.quantity() != EXPECTED_QUANTITY) {
            throw new AssertionError("C3 preparation did not stage one x64 tonic lot: " + staged);
        }
        System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_USE_STAGE_READY_CLIENT");

        freeTargetHotbarSlot(context);
        moveTonicPhysicallyToHotbar(context);
        LotAuthority beforeUse =
                waitForMovedAuthority(
                        context,
                        staged,
                        "PHYSICAL_AUTHORITY_CONSUMABLE_USE_STATUS_BEFORE_CLIENT");
        System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_USE_HOTBAR_READY_CLIENT");

        context.getInput().pressKey(GLFW.GLFW_KEY_8);
        context.waitFor(
                client ->
                        client.player != null
                                && hasTonicProjection(client.player.getMainHandItem()),
                20 * 5);
        System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_USE_SELECTED_CLIENT");

        LotAuthority selectedAuthority =
                parseTonicStatus(
                        sendStatusAndCaptureTonic(
                                context,
                                "PHYSICAL_AUTHORITY_CONSUMABLE_USE_STATUS_SELECTED_CLIENT"));
        if (!beforeUse.equals(selectedAuthority)) {
            throw new AssertionError(
                    "Selecting the tonic changed authority before use: before="
                            + beforeUse
                            + " selected="
                            + selectedAuthority);
        }

        int firstUseMessage = RECEIVED_GAME_MESSAGES.size();
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_USE_MOUSE_SENT_CLIENT");
        waitForGameMessage(context, firstUseMessage, WINDUP_PREFIX, 20 * 5);
        System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_USE_WINDUP_CLIENT");
        waitForGameMessage(context, firstUseMessage, COMMITTED_PREFIX, 20 * 10);
        System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_USE_COMMITTED_CLIENT");

        LotAuthority consumed =
                waitForConsumedAuthority(
                        context,
                        beforeUse,
                        "PHYSICAL_AUTHORITY_CONSUMABLE_USE_STATUS_AFTER_CLIENT");
        System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_USE_DECREMENTED_ONCE_CLIENT");

        waitForGameMessage(context, firstUseMessage, RECOVERY_COMPLETE, 20 * 10);
        System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_USE_RECOVERY_COMPLETE_CLIENT");
        assertAuthorityStable(context, consumed);
        System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_USE_STABLE_CLIENT");

        context.waitFor(
                client ->
                        client.player != null
                                && hasTonicProjection(
                                        client.player
                                                .getInventory()
                                                .getItem(TARGET_HOTBAR_SLOT)),
                20 * 5);
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
        System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_USE_HANDSHAKE_CLIENT");
    }

    private static void prepareMainInventoryGrantSlot(ClientGameTestContext context) {
        context.waitFor(
                client ->
                        client.player != null
                                && client.player
                                        .getInventory()
                                        .getItem(CHRONICLE_HOTBAR_SLOT)
                                        .is(Items.WRITTEN_BOOK),
                20 * 30);
        for (int slot = 0; slot <= TARGET_HOTBAR_SLOT; slot++) {
            sendCommand(
                    context,
                    "/item replace entity @s hotbar."
                            + slot
                            + " with minecraft:stone");
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

    private static void moveTonicPhysicallyToHotbar(ClientGameTestContext context) {
        context.getInput().pressKey(options -> options.keyInventory);
        context.waitForScreen(InventoryScreen.class);
        context.waitFor(
                client -> {
                    if (!(client.gui.screen() instanceof InventoryScreen screen)
                            || client.player == null) {
                        return false;
                    }
                    return hasTonicProjection(
                            findPlayerSlot(
                                            screen,
                                            client.player.getInventory(),
                                            SOURCE_STORAGE_SLOT)
                                    .getItem());
                },
                20 * 10);
        pickupTonicWithRetry(context);
        placeTonicWithRetry(context);
        context.getInput().pressKey(GLFW.GLFW_KEY_ESCAPE);
        context.waitForScreen(null);
        System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_USE_PHYSICAL_MOVE_CLIENT");
    }

    private static void pickupTonicWithRetry(ClientGameTestContext context) {
        for (int attempt = 1; attempt <= PREPARATION_CLICK_ATTEMPTS; attempt++) {
            int state = inventoryMoveState(context);
            if (state == 1) {
                System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_USE_PICKUP_OBSERVED_CLIENT");
                return;
            }
            if (state != 0) {
                throw new AssertionError(
                        "C3 pickup entered an unexpected physical inventory state before retry: "
                                + inventoryMoveDescription(context));
            }
            setInventoryCursor(context, SOURCE_STORAGE_SLOT);
            context.waitTicks(1);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            state = waitForPreparationTransition(context, 1, 0);
            if (state == 1) {
                System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_USE_PICKUP_OBSERVED_CLIENT");
                return;
            }
            if (state != 0) {
                throw new AssertionError(
                        "C3 pickup did not settle into a safe state: "
                                + inventoryMoveDescription(context));
            }
            System.out.println(
                    "PHYSICAL_AUTHORITY_CONSUMABLE_USE_PICKUP_RETRY_CLIENT attempt=" + attempt);
        }
        throw new AssertionError(
                "C3 physical pickup did not register after "
                        + PREPARATION_CLICK_ATTEMPTS
                        + " state-aware attempts");
    }

    private static void placeTonicWithRetry(ClientGameTestContext context) {
        for (int attempt = 1; attempt <= PREPARATION_CLICK_ATTEMPTS; attempt++) {
            int state = inventoryMoveState(context);
            if (state == 2) {
                System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_USE_PLACE_OBSERVED_CLIENT");
                return;
            }
            if (state != 1) {
                throw new AssertionError(
                        "C3 placement entered an unexpected physical inventory state before retry: "
                                + inventoryMoveDescription(context));
            }
            setInventoryCursor(context, TARGET_HOTBAR_SLOT);
            context.waitTicks(1);
            context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
            state = waitForPreparationTransition(context, 2, 1);
            if (state == 2) {
                System.out.println("PHYSICAL_AUTHORITY_CONSUMABLE_USE_PLACE_OBSERVED_CLIENT");
                return;
            }
            if (state != 1) {
                throw new AssertionError(
                        "C3 placement did not settle into a safe state: "
                                + inventoryMoveDescription(context));
            }
            System.out.println(
                    "PHYSICAL_AUTHORITY_CONSUMABLE_USE_PLACE_RETRY_CLIENT attempt=" + attempt);
        }
        throw new AssertionError(
                "C3 physical placement did not register after "
                        + PREPARATION_CLICK_ATTEMPTS
                        + " state-aware attempts");
    }

    private static int waitForPreparationTransition(
            ClientGameTestContext context, int desiredState, int retryState) {
        int retryStableTicks = 0;
        int state = inventoryMoveState(context);
        for (int tick = 0; tick < PREPARATION_TRANSITION_TIMEOUT_TICKS; tick++) {
            if (state == desiredState) {
                return state;
            }
            if (state == retryState) {
                retryStableTicks++;
                if (retryStableTicks >= PREPARATION_RETRY_STABLE_TICKS) {
                    return state;
                }
            } else {
                retryStableTicks = 0;
            }
            context.waitTicks(1);
            state = inventoryMoveState(context);
        }
        return state;
    }

    private static int inventoryMoveState(ClientGameTestContext context) {
        return context.computeOnClient(
                client -> {
                    if (!(client.gui.screen() instanceof InventoryScreen screen)
                            || client.player == null) {
                        return -1;
                    }
                    Slot source =
                            findPlayerSlot(
                                    screen,
                                    client.player.getInventory(),
                                    SOURCE_STORAGE_SLOT);
                    Slot target =
                            findPlayerSlot(
                                    screen,
                                    client.player.getInventory(),
                                    TARGET_HOTBAR_SLOT);
                    boolean sourceTonic = hasTonicProjection(source.getItem());
                    boolean targetTonic = hasTonicProjection(target.getItem());
                    boolean carriedTonic = hasTonicProjection(screen.getMenu().getCarried());
                    boolean sourceEmpty = source.getItem().isEmpty();
                    boolean targetEmpty = target.getItem().isEmpty();
                    boolean carriedEmpty = screen.getMenu().getCarried().isEmpty();
                    if (sourceTonic && targetEmpty && carriedEmpty) {
                        return 0;
                    }
                    if (sourceEmpty && targetEmpty && carriedTonic) {
                        return 1;
                    }
                    if (sourceEmpty && targetTonic && carriedEmpty) {
                        return 2;
                    }
                    return -2;
                });
    }

    private static String inventoryMoveDescription(ClientGameTestContext context) {
        return context.computeOnClient(
                client -> {
                    if (!(client.gui.screen() instanceof InventoryScreen screen)
                            || client.player == null) {
                        return "screen="
                                + (client.gui.screen() == null
                                        ? "null"
                                        : client.gui.screen().getClass().getSimpleName())
                                + " player="
                                + (client.player != null);
                    }
                    Slot source =
                            findPlayerSlot(
                                    screen,
                                    client.player.getInventory(),
                                    SOURCE_STORAGE_SLOT);
                    Slot target =
                            findPlayerSlot(
                                    screen,
                                    client.player.getInventory(),
                                    TARGET_HOTBAR_SLOT);
                    return "source="
                            + stackDescription(source.getItem())
                            + " target="
                            + stackDescription(target.getItem())
                            + " carried="
                            + stackDescription(screen.getMenu().getCarried());
                });
    }

    private static String stackDescription(net.minecraft.world.item.ItemStack stack) {
        if (stack.isEmpty()) {
            return "empty";
        }
        return stack.getHoverName().getString() + "x" + stack.getCount();
    }

    private static LotAuthority waitForMovedAuthority(
            ClientGameTestContext context, LotAuthority staged, String marker) {
        for (int attempt = 0; attempt < 12; attempt++) {
            LotAuthority candidate = pollTonicStatus(context);
            if (isMoved(staged, candidate)) {
                System.out.println(marker);
                return candidate;
            }
            if (!candidate.equals(staged)) {
                throw new AssertionError(
                        "Unexpected authority while preparing C3 hotbar lot: staged="
                                + staged
                                + " observed="
                                + candidate);
            }
        }
        throw new AssertionError("Timed out waiting for C3 hotbar authority move from " + staged);
    }

    private static LotAuthority waitForConsumedAuthority(
            ClientGameTestContext context, LotAuthority beforeUse, String marker) {
        for (int attempt = 0; attempt < 16; attempt++) {
            LotAuthority candidate = pollTonicStatus(context);
            if (isConsumedOnce(beforeUse, candidate)) {
                System.out.println(marker);
                return candidate;
            }
            if (!candidate.equals(beforeUse)) {
                throw new AssertionError(
                        "Unexpected authority while waiting for C3 consumption: before="
                                + beforeUse
                                + " observed="
                                + candidate);
            }
        }
        throw new AssertionError("Timed out waiting for one authoritative tonic consumption");
    }

    private static void assertAuthorityStable(
            ClientGameTestContext context, LotAuthority expected) {
        for (int probe = 0; probe < 3; probe++) {
            LotAuthority observed = pollTonicStatus(context);
            if (!expected.equals(observed)) {
                throw new AssertionError(
                        "C3 authority changed again after the single use commit: expected="
                                + expected
                                + " observed="
                                + observed);
            }
        }
    }

    private static LotAuthority pollTonicStatus(ClientGameTestContext context) {
        int firstNewMessage = RECEIVED_GAME_MESSAGES.size();
        sendCommand(context, "/mmo physical status");
        context.waitTicks(10);
        String status = tonicStatusSince(firstNewMessage);
        if (status == null) {
            context.waitTicks(10);
            status = tonicStatusSince(firstNewMessage);
        }
        if (status == null) {
            throw new AssertionError("Authoritative tonic status did not answer a C3 probe");
        }
        return parseTonicStatus(status);
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

    private static boolean isMoved(LotAuthority staged, LotAuthority moved) {
        return staged.uuid().equals(moved.uuid())
                && staged.contentVersion().equals(moved.contentVersion())
                && moved.quantity() == staged.quantity()
                && moved.version() == staged.version() + 1
                && !staged.transactionId().equals(moved.transactionId())
                && "CHARACTER_INVENTORY/slot:7".equals(moved.location());
    }

    private static boolean isConsumedOnce(LotAuthority before, LotAuthority after) {
        return before.uuid().equals(after.uuid())
                && before.contentVersion().equals(after.contentVersion())
                && before.location().equals(after.location())
                && after.quantity() == before.quantity() - 1
                && after.version() == before.version() + 1
                && !before.transactionId().equals(after.transactionId());
    }

    private static void waitForGameMessage(
            ClientGameTestContext context, int firstMessage, String prefix, int timeoutTicks) {
        context.waitFor(
                client -> {
                    for (int index = firstMessage; index < RECEIVED_GAME_MESSAGES.size(); index++) {
                        if (RECEIVED_GAME_MESSAGES.get(index).startsWith(prefix)) {
                            return true;
                        }
                    }
                    return false;
                },
                timeoutTicks);
    }

    private static void openDevHub(ClientGameTestContext context) {
        sendCommand(context, "/mmo dev");
        context.waitFor(client -> menuContains(client.gui.screen(), DEV_MODULE_NAME), 20 * 10);
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
                        slot ->
                                slot.container == inventory
                                        && slot.getContainerSlot() == storageSlot)
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

    private static boolean hasTonicProjection(net.minecraft.world.item.ItemStack stack) {
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

    private static void connect(ClientGameTestContext context, String address) {
        context.runOnClient(
                client -> {
                    ServerData server =
                            new ServerData(
                                    "Branz physical consumable use acceptance",
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

    private record LotAuthority(
            String uuid,
            String location,
            int version,
            int quantity,
            String transactionId,
            String contentVersion) {}
}
