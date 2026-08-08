package com.branz.mmorpg.bootstrap;

import java.util.Objects;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;

/** Bukkit presentation adapter for one authoritative MMO melee hit. */
final class MeleeHitFeedbackRenderer {
    private MeleeHitFeedbackRenderer() {}

    static void render(
            LivingEntity entity, MeleeHitFeedbackPolicy.MeleeHitFeedbackSpec feedback) {
        Objects.requireNonNull(entity, "entity");
        Objects.requireNonNull(feedback, "feedback");
        entity.playHurtAnimation(0.0f);
        Location location = entity.getLocation().add(0.0, entity.getHeight() * 0.6, 0.0);
        entity.getWorld()
                .spawnParticle(
                        Particle.DAMAGE_INDICATOR,
                        location,
                        feedback.particleCount(),
                        0.18,
                        0.18,
                        0.18,
                        0.02);
        entity.getWorld()
                .playSound(
                        location,
                        Sound.ENTITY_PLAYER_ATTACK_STRONG,
                        feedback.volume(),
                        feedback.pitch());
    }
}
