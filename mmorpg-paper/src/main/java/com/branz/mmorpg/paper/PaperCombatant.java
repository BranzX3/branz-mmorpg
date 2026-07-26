package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.combat.Combatant;
import com.branz.mmorpg.api.combat.WorldPoint;
import com.branz.mmorpg.api.stat.AttributeSnapshot;
import com.branz.mmorpg.api.stat.AttributeType;
import java.util.EnumMap;
import java.util.UUID;
import com.branz.mmorpg.core.stat.PlayerAttributeService;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

/** Live Paper view consumed by the platform-independent combat engine. */
final class PaperCombatant implements Combatant {

    private final LivingEntity entity;
    private final PlayerAttributeService playerAttributes;

    PaperCombatant(LivingEntity entity, PlayerAttributeService playerAttributes) {
        this.entity = entity;
        this.playerAttributes = playerAttributes;
    }

    @Override
    public UUID id() {
        return entity.getUniqueId();
    }

    @Override
    public AttributeSnapshot attributes() {
        if (entity instanceof Player player) {
            return playerAttributes.attributes(player.getUniqueId());
        }
        EnumMap<AttributeType, Double> values = new EnumMap<>(AttributeType.class);
        for (AttributeType attribute : AttributeType.values()) {
            values.put(attribute, attribute.defaultValue());
        }
        values.put(AttributeType.MAX_HEALTH, entity.getMaxHealth());
        return new AttributeSnapshot(values);
    }

    @Override
    public double currentHealth() {
        return entity.getHealth();
    }

    @Override
    public double applyHealthLoss(double amount) {
        double before = entity.getHealth();
        double after = Math.max(0.0, before - Math.max(0.0, amount));
        entity.setHealth(after);
        return before - after;
    }

    @Override
    public double absorb(double amount) {
        double available = Math.max(0.0, entity.getAbsorptionAmount());
        double absorbed = Math.min(available, Math.max(0.0, amount));
        entity.setAbsorptionAmount(available - absorbed);
        return absorbed;
    }

    @Override
    public WorldPoint position() {
        var location = entity.getLocation();
        return new WorldPoint(entity.getWorld().getUID(),
                location.getX(), location.getY(), location.getZ());
    }

    @Override
    public boolean alive() {
        return !entity.isDead() && entity.getHealth() > 0.0;
    }

    @Override
    public boolean invulnerable() {
        return entity.isInvulnerable();
    }

    @Override
    public boolean inSafeZone() {
        // Region support arrives in C8. Until then no location is silently
        // treated as safe; invulnerability remains a separate explicit gate.
        return false;
    }

    @Override
    public boolean playerControlled() {
        return entity instanceof Player;
    }

    @Override
    public String allegiance() {
        if (!(entity instanceof Player player)) {
            return null;
        }
        Team team = player.getScoreboard().getEntryTeam(player.getName());
        return team == null ? "player:" + player.getUniqueId() : "team:" + team.getName();
    }
}
