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
import org.lwjgl.glfw.GLFW;

/** Section B5: drive one real weapon to zero, then prove the next real PRIMARY is rejected. */
final class PhysicalBrokenWeaponClientGameTest {
    private static final int SERVER_HANDSHAKE_LEVEL = 7;
    private static final int PRIMARY_STAGE_HANDSHAKE_LEVEL = 8;
    private static final int BROKEN_TARGET_READY_LEVEL = 11;
    private static final int TRAINING_BLADE_MAXIMUM = 100;
    private static final float HIT_AIM_MAX_ABS_PITCH = 5.0F;
    private static final String BROKEN_MESSAGE = "Combat not ready: equipped weapon is broken.";
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
                                && !client.player.getMainHandItem().isEmpty(),
                20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_PRIMARY_PROJECTION_READY_CLIENT");

        context.waitFor(
                client ->
                        client.player != null
                                && client.player.experienceLevel == BROKEN_TARGET_READY_LEVEL,
                20 * 30);
        context.waitFor(
                client ->
                        client.player != null
                                && !client.player.getMainHandItem().isEmpty()
                                && Math.abs(client.player.getXRot()) <= HIT_AIM_MAX_ABS_PITCH,
                20 * 5);
        System.out.println("PHYSICAL_AUTHORITY_PRIMARY_BROKEN_TARGET_READY_CLIENT");

        String beforeLine =
                sendStatusAndCaptureTrainingBlade(
                        context, "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_STATUS_BEFORE_CLIENT");
        ItemAuthority before = parseTrainingBladeStatus(beforeLine);
        if (before.currentDurability() != TRAINING_BLADE_MAXIMUM
                || before.maximumDurability() != TRAINING_BLADE_MAXIMUM) {
            throw new AssertionError(
                    "B5 requires a fresh 100/100 Training Blade before accelerated wear: " + before);
        }

        int firstBrokenStateMessage = RECEIVED_GAME_MESSAGES.size();
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        System.out.println("PHYSICAL_AUTHORITY_PRIMARY_MOUSE_SENT_CLIENT");
        System.out.println("PHYSICAL_AUTHORITY_PRIMARY_BROKEN_FIRST_MOUSE_SENT_CLIENT");
        context.waitTicks(80);
        String zeroLine =
                sendStatusAndCaptureTrainingBlade(
                        context, "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_STATUS_ZERO_CLIENT");
        ItemAuthority zero = parseTrainingBladeStatus(zeroLine);
        if (!before.uuid().equals(zero.uuid())
                || !before.location().equals(zero.location())
                || !before.contentVersion().equals(zero.contentVersion())
                || zero.maximumDurability() != TRAINING_BLADE_MAXIMUM
                || zero.currentDurability() != 0
                || zero.version() != before.version() + 1
                || before.transactionId().equals(zero.transactionId())) {
            throw new AssertionError(
                    "Training Blade did not reach authoritative broken state in exactly one B5 wear commit: before="
                            + before
                            + " zero="
                            + zero);
        }
        System.out.println("PHYSICAL_AUTHORITY_PRIMARY_BROKEN_ZERO_CLIENT");

        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        System.out.println("PHYSICAL_AUTHORITY_PRIMARY_BROKEN_RETRY_MOUSE_SENT_CLIENT");
        context.waitFor(client -> brokenMessageSince(firstBrokenStateMessage), 20 * 5);
        System.out.println("PHYSICAL_AUTHORITY_PRIMARY_BROKEN_REJECTION_MESSAGE_CLIENT");
        context.waitTicks(40);

        String afterRejectLine =
                sendStatusAndCaptureTrainingBlade(
                        context, "PHYSICAL_AUTHORITY_PRIMARY_BROKEN_STATUS_AFTER_REJECT_CLIENT");
        if (!zeroLine.equals(afterRejectLine)) {
            throw new AssertionError(
                    "Broken PRIMARY rejection changed authoritative weapon state: zero="
                            + zeroLine
                            + " afterReject="
                            + afterRejectLine);
        }
        System.out.println("PHYSICAL_AUTHORITY_PRIMARY_BROKEN_REJECTED_STABLE_CLIENT");
        System.out.println("PHYSICAL_AUTHORITY_STATUS_COMMAND_SENT_CLIENT");

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

    private static boolean brokenMessageSince(int firstMessage) {
        for (int index = RECEIVED_GAME_MESSAGES.size() - 1; index >= firstMessage; index--) {
            if (BROKEN_MESSAGE.equals(RECEIVED_GAME_MESSAGES.get(index))) {
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
                                    "Branz physical broken weapon acceptance",
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
