package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.combat.DamageRequest;
import com.branz.mmorpg.api.combat.DamageType;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.player.PlayerSession;
import com.branz.mmorpg.api.skill.ResourceType;
import com.branz.mmorpg.api.skill.SkillCastSnapshot;
import com.branz.mmorpg.api.skill.SkillCaster;
import com.branz.mmorpg.api.skill.SkillDefinition;
import com.branz.mmorpg.api.skill.SkillEffectNode;
import com.branz.mmorpg.api.skill.SkillState;
import com.branz.mmorpg.api.stat.AttributeType;
import com.branz.mmorpg.api.item.LoadoutService;
import com.branz.mmorpg.api.telemetry.TelemetryService;
import com.branz.mmorpg.core.player.PlayerSessionService;
import com.branz.mmorpg.core.runtime.SystemGameClock;
import com.branz.mmorpg.core.skill.SkillExecutionEngine;
import com.branz.mmorpg.core.stat.ResourcePool;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/** Paper input, resources, feedback, and effect adapter for the C5 engine. */
public final class PaperSkillRuntime implements Listener {

    private static final ContentId DEFAULT_WEAPON = ContentId.parse("branz:broadsword");

    private final JavaPlugin plugin;
    private final PlayerSessionService sessions;
    private final ContentService content;
    private final PaperCombatRuntime combat;
    private final PaperStatusRuntime statuses;
    private final LoadoutService loadouts;
    private final PaperItemRuntime items;
    private final TelemetryService telemetry;
    private final Map<UUID, PlayerResources> resources = new ConcurrentHashMap<>();
    private final Map<UUID, ProjectileCast> projectiles = new ConcurrentHashMap<>();
    private final SkillExecutionEngine engine;
    private volatile BiConsumer<UUID, ContentId> skillListener =
            (player, skill) -> {};

    public void skillListener(BiConsumer<UUID, ContentId> listener) {
        skillListener = Objects.requireNonNull(listener, "listener");
    }

    public PaperSkillRuntime(JavaPlugin plugin, PlayerSessionService sessions,
                             ContentService content, PaperCombatRuntime combat,
                             PaperStatusRuntime statuses, LoadoutService loadouts,
                             PaperItemRuntime items, TelemetryService telemetry) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.content = Objects.requireNonNull(content, "content");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.statuses = Objects.requireNonNull(statuses, "statuses");
        this.loadouts = Objects.requireNonNull(loadouts, "loadouts");
        this.items = Objects.requireNonNull(items, "items");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.engine = new SkillExecutionEngine(new SystemGameClock(), this::execute);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!playable(player)) {
            return;
        }
        event.setCancelled(true);
        var weapon = items.activeWeapon(player.getUniqueId())
                .or(() -> loadouts.current(player.getUniqueId())).orElse(null);
        if (weapon == null) {
            LoadoutService.EquipResult defaultEquip =
                    loadouts.equip(player.getUniqueId(), DEFAULT_WEAPON);
            if (!defaultEquip.equipped()) {
                player.sendActionBar(Component.text("Cannot equip default weapon: "
                        + defaultEquip.rejection()));
                return;
            }
            weapon = defaultEquip.weapon();
        }
        if (weapon == null || weapon.activeSkillIds().isEmpty()) {
            player.sendActionBar(Component.text("No active weapon loadout."));
            return;
        }
        SkillDefinition definition = content.snapshot().skills().get(weapon.activeSkillIds().get(0));
        if (definition == null) {
            player.sendActionBar(Component.text("Skill content is unavailable."));
            return;
        }
        Entity selected = player.getTargetEntity(Math.max(1, (int) Math.ceil(definition.range())));
        UUID targetId = selected instanceof LivingEntity ? selected.getUniqueId() : null;
        if (targetId == null) {
            player.sendActionBar(Component.text("No valid target."));
            return;
        }
        var result = engine.begin(definition, caster(player), targetId,
                content.snapshot().revision());
        if (result.started()) {
            skillListener.accept(player.getUniqueId(), definition.id());
        }
        telemetry.increment("skill.usage");
        if (!result.started()) {
            player.sendActionBar(Component.text("Cannot cast: "
                    + result.rejection().name().toLowerCase(Locale.ROOT).replace('_', ' ')));
        } else {
            player.sendActionBar(Component.text(definition.displayName() + " — casting"));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        resources.put(event.getPlayer().getUniqueId(), new PlayerResources());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        engine.activeCast(playerId).ifPresent(cast -> {
            engine.interrupt(cast.castId(), "logout");
            combat.engine().endCast(cast.castId());
            engine.forget(cast.castId());
        });
        resources.remove(playerId);
    }

    /** Called once per Paper tick. */
    public void tick() {
        projectiles.entrySet().removeIf(entry -> {
            Entity entity = plugin.getServer().getEntity(entry.getKey());
            if (entity != null && entity.isValid()) return false;
            combat.engine().endCast(entry.getValue().cast().castId());
            return true;
        });
        for (SkillCastSnapshot cast : engine.advanceAll()) {
            if (cast.state() == SkillState.COMPLETE || cast.state() == SkillState.INTERRUPTED) {
                combat.engine().endCast(cast.castId());
                engine.forget(cast.castId());
            }
        }
        for (Map.Entry<UUID, PlayerResources> entry : resources.entrySet()) {
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            boolean inCombat = combat.engine().combatState()
                    .inCombat(player.getUniqueId(), java.time.Instant.now());
            entry.getValue().mana.regenerate(5.0, 1L, inCombat, 0.25);
            entry.getValue().stamina.regenerate(10.0, 1L, inCombat, 0.50);
        }
    }

    public SkillExecutionEngine engine() {
        return engine;
    }

    public ResourceView resources(UUID playerId) {
        PlayerResources value = resources.computeIfAbsent(
                playerId, ignored -> new PlayerResources());
        return new ResourceView(value.mana.current(), value.mana.maximum(),
                value.stamina.current(), value.stamina.maximum());
    }

    /** Routes vanilla melee intent through the same authoritative skill state machine. */
    public void basicAttack(Player player, LivingEntity target) {
        if (!playable(player)) return;
        var weapon = items.activeWeapon(player.getUniqueId())
                .or(() -> loadouts.current(player.getUniqueId())).orElse(null);
        if (weapon == null) {
            LoadoutService.EquipResult equipped =
                    loadouts.equip(player.getUniqueId(), DEFAULT_WEAPON);
            weapon = equipped.equipped() ? equipped.weapon() : null;
        }
        if (weapon == null) {
            player.sendActionBar(Component.text("No usable weapon."));
            return;
        }
        SkillDefinition definition =
                content.snapshot().skills().get(weapon.basicAttackSkillId());
        if (definition == null) {
            player.sendActionBar(Component.text("Basic attack content is unavailable."));
            return;
        }
        var result = engine.begin(definition, caster(player), target.getUniqueId(),
                content.snapshot().revision());
        if (result.started()) {
            skillListener.accept(player.getUniqueId(), definition.id());
        }
        telemetry.increment("skill.usage");
        if (!result.started()) {
            telemetry.increment("skill.rejected." + result.rejection().name()
                    .toLowerCase(Locale.ROOT));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        ProjectileCast launched = projectiles.remove(event.getEntity().getUniqueId());
        if (launched == null) return;
        try {
            event.getEntity().remove();
            if (!(event.getHitEntity() instanceof LivingEntity target)
                    || target.getUniqueId().equals(launched.casterId())) return;
            Player caster = plugin.getServer().getPlayer(launched.casterId());
            if (caster == null) return;
            String child = launched.node().values().get("on-hit");
            if (child != null) {
                SkillEffectNode effect = launched.definition().effects().get(child);
                if (effect != null) {
                    executeNode(launched.cast(), launched.definition(), effect, caster, target);
                    return;
                }
            }
            double power = launched.node().numbers().getOrDefault("power", 0.0);
            if (power <= 0) return;
            DamageType type = DamageType.valueOf(launched.node().values()
                    .getOrDefault("type", "physical").toUpperCase(Locale.ROOT));
            combat.engine().damage(new DamageRequest(launched.cast().castId(),
                    launched.casterId(), target.getUniqueId(), type, power,
                    launched.definition().range(),
                    launched.definition().requiresLineOfSight(), 1));
            telemetry.increment("skill.hit");
        } finally {
            combat.engine().endCast(launched.cast().castId());
        }
    }

    public record ResourceView(double mana, double maximumMana,
                               double stamina, double maximumStamina) {
    }

    private SkillCaster caster(Player player) {
        PlayerResources pools = resources.computeIfAbsent(player.getUniqueId(),
                ignored -> new PlayerResources());
        return new SkillCaster() {
            @Override public UUID id() { return player.getUniqueId(); }
            @Override public boolean alive() { return !player.isDead() && player.getHealth() > 0; }
            @Override public boolean silenced() {
                return statuses.has(player.getUniqueId(),
                        com.branz.mmorpg.core.status.BuiltInStatuses.SILENCE);
            }
            @Override public boolean stunned() {
                return statuses.has(player.getUniqueId(),
                        com.branz.mmorpg.core.status.BuiltInStatuses.STUN);
            }
            @Override public double cooldownRecovery() { return 0.0; }
            @Override public boolean spend(Map<ResourceType, Double> costs) {
                double mana = costs.getOrDefault(ResourceType.MANA, 0.0);
                double stamina = costs.getOrDefault(ResourceType.STAMINA, 0.0);
                double health = costs.getOrDefault(ResourceType.HEALTH, 0.0);
                if (pools.mana.current() < mana || pools.stamina.current() < stamina
                        || player.getHealth() <= health) {
                    return false;
                }
                pools.mana.spend(mana);
                pools.stamina.spend(stamina);
                player.setHealth(player.getHealth() - health);
                return true;
            }
            @Override public void refund(Map<ResourceType, Double> costs, double fraction) {
                pools.mana.add(costs.getOrDefault(ResourceType.MANA, 0.0) * fraction);
                pools.stamina.add(costs.getOrDefault(ResourceType.STAMINA, 0.0) * fraction);
                player.setHealth(Math.min(player.getMaxHealth(), player.getHealth()
                        + costs.getOrDefault(ResourceType.HEALTH, 0.0) * fraction));
            }
        };
    }

    private void execute(SkillCastSnapshot cast, SkillDefinition definition, SkillEffectNode root) {
        Player caster = plugin.getServer().getPlayer(cast.casterId());
        Entity targetEntity = cast.targetId() == null ? null : plugin.getServer().getEntity(cast.targetId());
        LivingEntity target = targetEntity instanceof LivingEntity living ? living : null;
        if (caster == null) {
            return;
        }
        executeNode(cast, definition, root, caster, target);
    }

    private void executeNode(SkillCastSnapshot cast, SkillDefinition definition,
                             SkillEffectNode node, Player caster, LivingEntity target) {
        switch (node.type()) {
            case SEQUENCE, PARALLEL, CONDITIONAL, AREA_QUERY ->
                    children(definition, node).forEach(child ->
                            executeNode(cast, definition, child, caster, target));
            case DAMAGE -> {
                if (target == null) return;
                DamageType type = DamageType.valueOf(node.values()
                        .getOrDefault("type", "physical").toUpperCase(Locale.ROOT));
                double power = node.numbers().getOrDefault("power", 0.0);
                combat.engine().damage(new DamageRequest(cast.castId(), caster.getUniqueId(),
                        target.getUniqueId(), type, power, definition.range(),
                        definition.requiresLineOfSight(), 1));
                telemetry.increment("skill.hit");
            }
            case HEAL -> {
                LivingEntity recipient = target == null ? caster : target;
                double amount = Math.max(0.0, node.numbers().getOrDefault("amount", 0.0));
                recipient.setHealth(Math.min(recipient.getMaxHealth(), recipient.getHealth() + amount));
            }
            case DASH -> caster.setVelocity(caster.getLocation().getDirection().normalize()
                    .multiply(node.numbers().getOrDefault("strength", 1.0)));
            case KNOCKBACK -> {
                if (target == null) return;
                Vector direction = target.getLocation().toVector()
                        .subtract(caster.getLocation().toVector()).normalize();
                target.setVelocity(direction.multiply(node.numbers().getOrDefault("strength", 1.0))
                        .setY(node.numbers().getOrDefault("vertical", 0.25)));
            }
            case APPLY_STATUS -> {
                if (target == null) return;
                String status = node.values().get("status");
                if (status != null) {
                    statuses.apply(target.getUniqueId(), ContentId.parse(status),
                            caster.getUniqueId());
                }
            }
            case REMOVE_STATUS -> {
                LivingEntity recipient = target == null ? caster : target;
                String status = node.values().get("status");
                if (status != null) {
                    statuses.remove(recipient.getUniqueId(), ContentId.parse(status));
                }
            }
            case SPAWN_PROJECTILE -> {
                Vector direction = target == null
                        ? caster.getEyeLocation().getDirection()
                        : target.getEyeLocation().toVector()
                        .subtract(caster.getEyeLocation().toVector()).normalize();
                double speed = Math.max(0.1,
                        node.numbers().getOrDefault("speed", 1.5));
                Projectile projectile = caster.launchProjectile(
                        Snowball.class, direction.multiply(speed));
                projectile.setShooter(caster);
                projectiles.put(projectile.getUniqueId(),
                        new ProjectileCast(cast, definition, node, caster.getUniqueId()));
            }
        }
    }

    private static List<SkillEffectNode> children(SkillDefinition definition, SkillEffectNode node) {
        return node.children().stream().map(definition.effects()::get).toList();
    }

    private boolean playable(Player player) {
        return sessions.session(player.getUniqueId()).map(PlayerSession::playable).orElse(false);
    }

    private static final class PlayerResources {
        private final ResourcePool mana = new ResourcePool(AttributeType.MAX_MANA, 50.0);
        private final ResourcePool stamina = new ResourcePool(AttributeType.MAX_STAMINA, 100.0);
    }

    private record ProjectileCast(
            SkillCastSnapshot cast, SkillDefinition definition,
            SkillEffectNode node, UUID casterId) {
    }
}
