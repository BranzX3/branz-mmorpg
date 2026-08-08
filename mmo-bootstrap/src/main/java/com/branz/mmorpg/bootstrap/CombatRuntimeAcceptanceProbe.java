package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.combat.health.CombatHealthEngine;
import com.branz.mmorpg.combat.health.CombatHealthProfile;
import com.branz.mmorpg.combat.health.CombatHealthRuntime;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Zombie;
import org.bukkit.plugin.java.JavaPlugin;

/** Smoke-only Paper acceptance probe for authoritative melee feedback and lethal entity bridging. */
final class CombatRuntimeAcceptanceProbe {
    static final String ENABLE_PROPERTY = "mmo.bootstrap.combat-acceptance-test";
    static final String MARKER_PROPERTY = "mmo.bootstrap.combat-acceptance-marker";
    static final String PASS_MARKER = "COMBAT_RUNTIME_ACCEPTANCE_PASS";

    private CombatRuntimeAcceptanceProbe() {}

    static void schedule(JavaPlugin plugin) {
        if (!Boolean.getBoolean(ENABLE_PROPERTY)) {
            return;
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> run(plugin), 5L);
    }

    private static void run(JavaPlugin plugin) {
        Zombie target = null;
        try {
            World world =
                    plugin.getServer().getWorlds().stream()
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("no loaded Paper world"));
            Location location = world.getSpawnLocation().clone().add(0.5, 1.0, 0.5);
            target = world.spawn(location, Zombie.class);
            target.setAI(false);
            target.setSilent(true);
            target.setRemoveWhenFarAway(false);

            CombatHealthEngine health =
                    new CombatHealthEngine(CombatHealthProfile.trainingEnemy());
            CombatHealthRuntime full =
                    CombatHealthRuntime.full(health.profile(), plugin.getServer().getCurrentTick());
            MeleeTargetDamageCoordinator.MeleeTargetDamageResult damage =
                    MeleeTargetDamageCoordinator.apply(
                            health,
                            full,
                            plugin.getServer().getCurrentTick(),
                            health.profile().maximum() * 2.0);
            if (!damage.lethalNow()
                    || !damage.runtime().dead()
                    || Double.compare(damage.appliedDamage(), health.profile().maximum()) != 0
                    || damage.feedback().isEmpty()) {
                throw new IllegalStateException("authoritative melee result was not lethal");
            }

            MeleeHitFeedbackRenderer.render(target, damage.feedback().orElseThrow());
            target.setHealth(0.0);
            Zombie observed = target;
            plugin.getServer()
                    .getScheduler()
                    .runTaskLater(plugin, () -> verifyAndMark(plugin, observed), 1L);
        } catch (Exception exception) {
            if (target != null && target.isValid()) {
                target.remove();
            }
            plugin.getLogger()
                    .severe(
                            "COMBAT_RUNTIME_ACCEPTANCE_FAIL "
                                    + exception.getClass().getSimpleName()
                                    + ": "
                                    + exception.getMessage());
        }
    }

    private static void verifyAndMark(JavaPlugin plugin, Zombie target) {
        try {
            if (!target.isDead()) {
                throw new IllegalStateException("Bukkit target was not dead after lethal MMO bridge");
            }
            writeMarker();
            plugin.getLogger().info(PASS_MARKER);
        } catch (Exception exception) {
            plugin.getLogger()
                    .severe(
                            "COMBAT_RUNTIME_ACCEPTANCE_FAIL "
                                    + exception.getClass().getSimpleName()
                                    + ": "
                                    + exception.getMessage());
        } finally {
            if (target.isValid()) {
                target.remove();
            }
        }
    }

    private static void writeMarker() throws IOException {
        String rawPath = System.getProperty(MARKER_PROPERTY, "").trim();
        if (rawPath.isEmpty()) {
            throw new IllegalStateException("combat acceptance marker path is missing");
        }
        Path marker = Path.of(rawPath).toAbsolutePath().normalize();
        Path parent = marker.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(
                marker,
                PASS_MARKER + System.lineSeparator(),
                StandardCharsets.UTF_8);
    }
}
