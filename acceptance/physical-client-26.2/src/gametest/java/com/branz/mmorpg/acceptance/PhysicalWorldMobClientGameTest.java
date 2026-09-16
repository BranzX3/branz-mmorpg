package com.branz.mmorpg.acceptance;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.lwjgl.glfw.GLFW;

/** Section E acceptance: canonical Bukkit health for ordinary mobs, hidden health only for tagged dummies. */
public final class PhysicalWorldMobClientGameTest implements FabricClientGameTest {
    private static final int SERVER_HANDSHAKE_LEVEL = 7;
    private static final int PRIMARY_STAGE_HANDSHAKE_LEVEL = 8;
    private static final int WORLD_TARGET_READY_LEVEL = 21;
    private static final int TRAINING_TARGET_READY_LEVEL = 22;
    private static final int MAX_WORLD_HITS = 16;
    private static final double TRAINING_HEALTH = 1_000.0D;
    private static final double EPSILON = 0.0001D;
    private static final Pattern TRAINING_BLADE_STATUS =
            Pattern.compile(
                    "^ITEM uuid=([0-9a-fA-F-]{36}) def=weapon\\.training_blade loc=([^ ]+) ver=(\\d+) durability=(\\d+)/(\\d+) tx=([0-9a-fA-F-]{36}) content=(\\S+)$");
    private static final Pattern HIT_RESOLUTION =
            Pattern.compile(
                    "^HIT targets=(\\d+) damage=([0-9]+(?:\\.[0-9]+)?) health=([0-9]+(?:\\.[0-9]+)?)/([0-9]+(?:\\.[0-9]+)?).*$");
    private static final Pattern DEATHS = Pattern.compile("(?:^| )deaths=(\\d+)(?: |$)");
    private static final List<String> RECEIVED_GAME_MESSAGES = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean GAME_MESSAGE_LISTENER_REGISTERED = new AtomicBoolean();

    @Override
    public void runTest(ClientGameTestContext context) {
        registerGameMessageCapture();
        RECEIVED_GAME_MESSAGES.clear();
        String address = System.getProperty("branz.acceptance.server", "localhost:25565");
        connect(context, address);
        context.waitFor(client -> client.level != null && client.player != null, 20 * 30);
        context.waitFor(
                client -> client.player != null && client.player.experienceLevel == SERVER_HANDSHAKE_LEVEL,
                20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_SERVER_HANDSHAKE_CLIENT");
        context.waitFor(client -> client.gui.screen() == null, 20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_GAMEPLAY_SCREEN_READY_CLIENT");

        context.waitFor(
                client ->
                        client.player != null
                                && client.player.experienceLevel == PRIMARY_STAGE_HANDSHAKE_LEVEL,
                20 * 30);
        context.waitFor(
                client -> client.player != null && !client.player.getMainHandItem().isEmpty(),
                20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_PRIMARY_PROJECTION_READY_CLIENT");

        runWorldMobPhase(context);
        runTrainingDummyPhase(context);
        System.out.println("PHYSICAL_AUTHORITY_E_COMPLETE_CLIENT");
        System.out.println("PHYSICAL_AUTHORITY_STATUS_COMMAND_SENT_CLIENT");

        context.waitFor(client -> client.level == null && client.player == null, 20 * 30);
        context.setScreen(TitleScreen::new);
        context.waitForScreen(TitleScreen.class);
    }

    private static void runWorldMobPhase(ClientGameTestContext context) {
        context.waitFor(
                client -> client.player != null && client.player.experienceLevel == WORLD_TARGET_READY_LEVEL,
                20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_E_WORLD_TARGET_READY_CLIENT");
        ItemAuthority before =
                parseTrainingBladeStatus(
                        sendStatusAndCaptureTrainingBlade(
                                context, "PHYSICAL_AUTHORITY_E_WORLD_STATUS_BEFORE_CLIENT"));

        double previousHealth = Double.NaN;
        double canonicalMax = Double.NaN;
        int acceptedHits = 0;
        boolean lethal = false;
        while (acceptedHits < MAX_WORLD_HITS && !lethal) {
            HitResolution hit = attackAndCapture(context, "PHYSICAL_AUTHORITY_E_WORLD_HIT_CLIENT");
            acceptedHits++;
            if (hit.targets() != 1) {
                throw new AssertionError("Section E world hit must resolve exactly one target: " + hit.raw());
            }
            if (!(hit.maximumHealth() > 0.0D) || hit.maximumHealth() >= TRAINING_HEALTH - EPSILON) {
                throw new AssertionError(
                        "Ordinary cow exposed hidden training health instead of Bukkit max health: "
                                + hit.raw());
            }
            if (Double.isNaN(canonicalMax)) {
                canonicalMax = hit.maximumHealth();
                previousHealth = canonicalMax;
            } else if (Math.abs(canonicalMax - hit.maximumHealth()) > EPSILON) {
                throw new AssertionError(
                        "Ordinary cow max health changed during one canonical lifecycle: " + hit.raw());
            }
            if (!(hit.currentHealth() + EPSILON < previousHealth)
                    || hit.currentHealth() < -EPSILON
                    || hit.currentHealth() > hit.maximumHealth() + EPSILON) {
                throw new AssertionError(
                        "Ordinary cow health did not decrease from canonical Bukkit health: previous="
                                + previousHealth
                                + " resolution="
                                + hit.raw());
            }
            if (hit.deaths() < 0 || hit.deaths() > 1) {
                throw new AssertionError("One world hit reported an invalid death count: " + hit.raw());
            }
            previousHealth = hit.currentHealth();
            lethal = hit.deaths() == 1;
            if (!lethal) {
                context.waitTicks(80);
            }
        }
        if (!lethal) {
            throw new AssertionError(
                    "Ordinary cow did not enter the normal lethal MMO lifecycle within "
                            + MAX_WORLD_HITS
                            + " accepted hits");
        }
        System.out.printf(
                "PHYSICAL_AUTHORITY_E_WORLD_DEATH_CLIENT hits=%d max=%.1f%n",
                acceptedHits, canonicalMax);

        ItemAuthority after =
                parseTrainingBladeStatus(
                        sendStatusAndCaptureTrainingBlade(
                                context, "PHYSICAL_AUTHORITY_E_WORLD_STATUS_AFTER_CLIENT"));
        assertWearProgression(before, after, acceptedHits, "ordinary world cow");
        System.out.println("PHYSICAL_AUTHORITY_E_WORLD_AUTHORITY_MATCHED_HITS_CLIENT");
    }

    private static void runTrainingDummyPhase(ClientGameTestContext context) {
        context.waitFor(
                client ->
                        client.player != null
                                && client.player.experienceLevel == TRAINING_TARGET_READY_LEVEL,
                20 * 30);
        System.out.println("PHYSICAL_AUTHORITY_E_TRAINING_TARGET_READY_CLIENT");
        ItemAuthority before =
                parseTrainingBladeStatus(
                        sendStatusAndCaptureTrainingBlade(
                                context, "PHYSICAL_AUTHORITY_E_TRAINING_STATUS_BEFORE_CLIENT"));
        HitResolution hit =
                attackAndCapture(context, "PHYSICAL_AUTHORITY_E_TRAINING_HIT_CLIENT");
        if (hit.targets() != 1
                || Math.abs(hit.maximumHealth() - TRAINING_HEALTH) > EPSILON
                || !(hit.currentHealth() + EPSILON < TRAINING_HEALTH)
                || hit.currentHealth() < -EPSILON
                || hit.deaths() != 0) {
            throw new AssertionError(
                    "Explicitly tagged training dummy did not retain hidden 1000-health behavior: "
                            + hit.raw());
        }
        ItemAuthority after =
                parseTrainingBladeStatus(
                        sendStatusAndCaptureTrainingBlade(
                                context, "PHYSICAL_AUTHORITY_E_TRAINING_STATUS_AFTER_CLIENT"));
        assertWearProgression(before, after, 1, "tagged training dummy");
        System.out.println("PHYSICAL_AUTHORITY_E_TRAINING_HIDDEN_HEALTH_CLIENT");
        System.out.println("PHYSICAL_AUTHORITY_E_TRAINING_AUTHORITY_WORN_ONCE_CLIENT");
    }

    private static HitResolution attackAndCapture(ClientGameTestContext context, String marker) {
        int firstNewMessage = RECEIVED_GAME_MESSAGES.size();
        context.getInput().pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT);
        System.out.println("PHYSICAL_AUTHORITY_PRIMARY_MOUSE_SENT_CLIENT");
        context.waitFor(client -> hitResolutionSince(firstNewMessage) != null, 20 * 10);
        String resolution = hitResolutionSince(firstNewMessage);
        if (resolution == null) {
            throw new AssertionError("MMO HIT resolution disappeared after capture wait");
        }
        HitResolution parsed = parseHitResolution(resolution);
        System.out.println(marker + " " + resolution);
        return parsed;
    }

    private static HitResolution parseHitResolution(String raw) {
        Matcher match = HIT_RESOLUTION.matcher(raw);
        if (!match.matches()) {
            throw new AssertionError("Unexpected MMO HIT resolution: " + raw);
        }
        Matcher deaths = DEATHS.matcher(raw);
        int deathCount = deaths.find() ? Integer.parseInt(deaths.group(1)) : 0;
        return new HitResolution(
                Integer.parseInt(match.group(1)),
                Double.parseDouble(match.group(2)),
                Double.parseDouble(match.group(3)),
                Double.parseDouble(match.group(4)),
                deathCount,
                raw);
    }

    private static String hitResolutionSince(int firstMessage) {
        for (int index = RECEIVED_GAME_MESSAGES.size() - 1; index >= firstMessage; index--) {
            String message = RECEIVED_GAME_MESSAGES.get(index);
            if (message.startsWith("HIT targets=")) {
                return message;
            }
        }
        return null;
    }

    private static void assertWearProgression(
            ItemAuthority before, ItemAuthority after, int acceptedHits, String phase) {
        if (!before.uuid().equals(after.uuid())
                || !before.location().equals(after.location())
                || !before.contentVersion().equals(after.contentVersion())
                || before.maximumDurability() != after.maximumDurability()
                || after.version() != before.version() + acceptedHits
                || after.currentDurability() != before.currentDurability() - acceptedHits
                || before.transactionId().equals(after.transactionId())) {
            throw new AssertionError(
                    "Authoritative Training Blade wear diverged from "
                            + phase
                            + " accepted hits: before="
                            + before
                            + " after="
                            + after
                            + " hits="
                            + acceptedHits);
        }
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

    private static void registerGameMessageCapture() {
        if (!GAME_MESSAGE_LISTENER_REGISTERED.compareAndSet(false, true)) {
            return;
        }
        ClientReceiveMessageEvents.GAME.register(
                (message, overlay) -> RECEIVED_GAME_MESSAGES.add(message.getString()));
    }

    private static void connect(ClientGameTestContext context, String address) {
        context.runOnClient(
                client -> {
                    ServerData server =
                            new ServerData(
                                    "Branz physical Section E acceptance",
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

    private record HitResolution(
            int targets,
            double damage,
            double currentHealth,
            double maximumHealth,
            int deaths,
            String raw) {}
}
