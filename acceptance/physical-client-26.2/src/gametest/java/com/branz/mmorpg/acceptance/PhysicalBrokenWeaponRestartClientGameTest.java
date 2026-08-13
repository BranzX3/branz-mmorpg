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
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.world.item.Items;

/** Section B7 phase two: prove the same broken physical weapon reconstructs after Paper restart. */
final class PhysicalBrokenWeaponRestartClientGameTest {
    private static final int SOURCE_HOTBAR_SLOT = 0;
    private static final int CHRONICLE_HOTBAR_SLOT = 8;
    private static final int TRAINING_BLADE_MAXIMUM = 100;
    private static final int CONNECTION_TIMEOUT_TICKS = 20 * 60;
    private static final Pattern TRAINING_BLADE_STATUS =
            Pattern.compile(
                    "^ITEM uuid=([0-9a-fA-F-]{36}) def=weapon\\.training_blade loc=([^ ]+) ver=(\\d+) durability=(\\d+)/(\\d+) tx=([0-9a-fA-F-]{36}) content=(\\S+)$");
    private static final List<String> RECEIVED_GAME_MESSAGES = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean GAME_MESSAGE_LISTENER_REGISTERED = new AtomicBoolean();

    void runTest(ClientGameTestContext context) {
        registerGameMessageCapture();
        RECEIVED_GAME_MESSAGES.clear();
        String address = System.getProperty("branz.acceptance.server", "localhost:25565");
        connect(context, address);
        context.waitFor(
                client -> client.level != null && client.player != null,
                CONNECTION_TIMEOUT_TICKS);
        context.waitFor(client -> client.gui.screen() == null, 20 * 30);
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
                CONNECTION_TIMEOUT_TICKS);
        System.out.println("PHYSICAL_AUTHORITY_PRIMARY_BROKEN_RESTART_PROJECTED_CLIENT");

        String status =
                sendStatusAndCaptureTrainingBlade(
                        context, "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_RESTART_STATUS_CLIENT");
        ItemAuthority authority = parseTrainingBladeStatus(status);
        if (!"CHARACTER_INVENTORY/slot:0".equals(authority.location())
                || authority.currentDurability() != 0
                || authority.maximumDurability() != TRAINING_BLADE_MAXIMUM) {
            throw new AssertionError(
                    "B7 restart did not reconstruct the broken Training Blade in its final physical slot: "
                            + authority);
        }
        System.out.println("PHYSICAL_AUTHORITY_PRIMARY_BROKEN_RESTART_STABLE_CLIENT");
        System.out.println("PHYSICAL_AUTHORITY_STATUS_COMMAND_SENT_CLIENT");

        context.waitFor(client -> client.level == null && client.player == null, 20 * 60);
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
        context.getInput().pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER);
        context.waitFor(client -> client.gui.screen() == null, 20 * 5);
        context.waitFor(client -> trainingBladeStatusSince(firstNewMessage) != null, 20 * 10);
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

    private static ItemAuthority parseTrainingBladeStatus(String status) {
        Matcher match = TRAINING_BLADE_STATUS.matcher(status);
        if (!match.matches()) {
            throw new AssertionError("Unexpected Training Blade authority status: " + status);
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

    private static void connect(ClientGameTestContext context, String address) {
        context.runOnClient(
                client -> {
                    ServerData server =
                            new ServerData(
                                    "Branz broken weapon restart acceptance",
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

    private record ItemAuthority(
            String uuid,
            String location,
            int version,
            int currentDurability,
            int maximumDurability,
            String transactionId,
            String contentVersion) {}
}
