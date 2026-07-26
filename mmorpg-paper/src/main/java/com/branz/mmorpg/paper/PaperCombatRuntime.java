package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.combat.CombatPolicy;
import com.branz.mmorpg.api.combat.DamageRequest;
import com.branz.mmorpg.api.combat.DamageType;
import com.branz.mmorpg.api.player.PlayerSession;
import com.branz.mmorpg.core.combat.CombatEngine;
import com.branz.mmorpg.core.combat.CombatEvents;
import com.branz.mmorpg.core.event.SimpleEventBus;
import com.branz.mmorpg.core.player.PlayerSessionService;
import com.branz.mmorpg.core.runtime.SeededRandomSource;
import com.branz.mmorpg.core.runtime.SystemGameClock;
import com.branz.mmorpg.core.stat.PlayerAttributeService;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.function.Consumer;
import org.bukkit.entity.Entity;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

/**
 * First playable Paper adapter for C2-C4.
 *
 * <p>Paper supplies an intent and presentation; Core validates and computes the
 * mutation. Vanilla damage is cancelled whenever this runtime owns the hit so
 * health cannot be removed twice.
 */
public final class PaperCombatRuntime implements Listener {

    private final JavaPlugin plugin;
    private final PlayerSessionService sessions;
    private final PlayerAttributeService playerAttributes;
    private final CombatEngine engine;
    private final SimpleEventBus events;
    private BasicAttackHandler basicAttacks;

    @FunctionalInterface
    public interface BasicAttackHandler {
        void attack(Player attacker, LivingEntity target);
    }

    public PaperCombatRuntime(JavaPlugin plugin, PlayerSessionService sessions,
                              PlayerAttributeService playerAttributes, CombatPolicy policy) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.playerAttributes = Objects.requireNonNull(playerAttributes, "playerAttributes");
        events = new SimpleEventBus(failure ->
                plugin.getLogger().log(Level.WARNING, "Combat event subscriber failed", failure));
        this.engine = new CombatEngine(policy, new SystemGameClock(), SeededRandomSource.shared(),
                events, this::combatant, this::clearLineOfSight);
    }

    public CombatEngine engine() {
        return engine;
    }

    public void damageListener(Consumer<CombatEvents.DamageDealt> listener) {
        events.subscribe(CombatEvents.DamageDealt.class,
                Objects.requireNonNull(listener, "listener"));
    }

    public void basicAttackHandler(BasicAttackHandler handler) {
        basicAttacks = Objects.requireNonNull(handler, "handler");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) {
            return;
        }
        LivingEntity attacker = attacker(event.getDamager());
        if (attacker == null || !owns(attacker, target)) {
            return;
        }
        event.setCancelled(true);
        if (!playable(attacker) || !playable(target)) {
            return;
        }
        if (event.getDamager() instanceof Player player && basicAttacks != null) {
            basicAttacks.attack(player, target);
            return;
        }

        UUID castId = UUID.randomUUID();
        try {
            engine.damage(DamageRequest.melee(castId, attacker.getUniqueId(),
                    target.getUniqueId(), DamageType.PHYSICAL,
                    Math.max(0.0, event.getDamage()), meleeRange(attacker, target)));
        } finally {
            engine.endCast(castId);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnvironmentalDamage(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent
                || !(event.getEntity() instanceof Player player)
                || sessions.session(player.getUniqueId()).isEmpty()) {
            return;
        }
        event.setCancelled(true);
        if (!playable(player)) {
            return;
        }
        UUID castId = UUID.randomUUID();
        try {
            engine.damage(DamageRequest.environmental(castId, player.getUniqueId(),
                    Math.max(0.0, event.getDamage())));
        } finally {
            engine.endCast(castId);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        engine.forget(event.getPlayer().getUniqueId());
    }

    public void sweepCombatState() {
        engine.sweepCombatState();
    }

    private PaperCombatant combatant(UUID entityId) {
        Entity entity = plugin.getServer().getEntity(entityId);
        return entity instanceof LivingEntity living
                ? new PaperCombatant(living, playerAttributes) : null;
    }

    private boolean owns(LivingEntity attacker, LivingEntity target) {
        return attacker instanceof Player || target instanceof Player;
    }

    private boolean playable(LivingEntity entity) {
        if (!(entity instanceof Player player)) {
            return true;
        }
        return sessions.session(player.getUniqueId())
                .map(PlayerSession::playable)
                .orElse(false);
    }

    private static LivingEntity attacker(Entity damager) {
        if (damager instanceof LivingEntity living) {
            return living;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            return shooter instanceof LivingEntity living ? living : null;
        }
        return null;
    }

    private static double meleeRange(LivingEntity attacker, LivingEntity target) {
        // Projectile events have already been validated by Paper. A generous
        // value avoids treating their travel distance as melee reach.
        return attacker.getLocation().distanceSquared(target.getLocation()) > 36.0 ? 0.0 : 6.0;
    }

    private boolean clearLineOfSight(com.branz.mmorpg.api.combat.WorldPoint from,
                                     com.branz.mmorpg.api.combat.WorldPoint to) {
        if (!from.sameWorld(to)) return false;
        var world = plugin.getServer().getWorld(from.worldId());
        if (world == null) return false;
        Vector direction = new Vector(to.x() - from.x(), to.y() - from.y(), to.z() - from.z());
        double distance = direction.length();
        if (distance <= 0.25) return true;
        Location origin = new Location(world, from.x(), from.y(), from.z());
        return world.rayTraceBlocks(origin, direction.normalize(), distance - 0.25,
                FluidCollisionMode.NEVER, true) == null;
    }
}
