package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.combat.DamageRequest;
import com.branz.mmorpg.api.combat.DamageType;
import com.branz.mmorpg.api.combat.WorldPoint;
import com.branz.mmorpg.api.character.CharacterClassDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.input.CombatInputIntent;
import com.branz.mmorpg.api.input.CombatInputKey;
import com.branz.mmorpg.api.input.CombatInputProfileDefinition;
import com.branz.mmorpg.api.input.InputResolution;
import com.branz.mmorpg.api.input.SkillSlot;
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
import com.branz.mmorpg.core.character.CharacterClassProgressionService;
import com.branz.mmorpg.core.input.CombatComboResolver;
import com.branz.mmorpg.core.input.CombatInputEngine;
import com.branz.mmorpg.core.runtime.SystemGameClock;
import com.branz.mmorpg.core.skill.SkillExecutionEngine;
import com.branz.mmorpg.core.stat.PlayerAttributeService;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/** Paper input, resources, feedback, and effect adapter for the C5 engine. */
public final class PaperSkillRuntime implements Listener {

    private static final ContentId DEFAULT_WEAPON = ContentId.parse("branz:broadsword");
    private static final ContentId DEFAULT_INPUT_PROFILE =
            ContentId.parse("branz:default_action_controls");

    private final JavaPlugin plugin;
    private final PlayerSessionService sessions;
    private final ContentService content;
    private final PaperCombatRuntime combat;
    private final PaperStatusRuntime statuses;
    private final LoadoutService loadouts;
    private final PaperItemRuntime items;
    private final TelemetryService telemetry;
    private final PlayerAttributeService attributes;
    private final CharacterClassProgressionService classProgression;
    private final CombatComboResolver comboResolver = new CombatComboResolver();
    private final CombatInputEngine inputEngine = new CombatInputEngine(comboResolver);
    private final Map<UUID, ProjectileCast> projectiles = new ConcurrentHashMap<>();
    private final SkillExecutionEngine engine;
    private volatile BiConsumer<UUID, ContentId> skillListener =
            (player, skill) -> {};
    private volatile Predicate<Player> inputReserved = player -> false;

    public void skillListener(BiConsumer<UUID, ContentId> listener) {
        skillListener = Objects.requireNonNull(listener, "listener");
    }

    public void inputReserved(Predicate<Player> predicate) {
        inputReserved = Objects.requireNonNull(predicate, "predicate");
    }

    public PaperSkillRuntime(JavaPlugin plugin, PlayerSessionService sessions,
                             ContentService content, PaperCombatRuntime combat,
                             PaperStatusRuntime statuses, LoadoutService loadouts,
                             PaperItemRuntime items, TelemetryService telemetry,
                             PlayerAttributeService attributes,
                             CharacterClassProgressionService classProgression) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.content = Objects.requireNonNull(content, "content");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.statuses = Objects.requireNonNull(statuses, "statuses");
        this.loadouts = Objects.requireNonNull(loadouts, "loadouts");
        this.items = Objects.requireNonNull(items, "items");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.attributes = Objects.requireNonNull(attributes, "attributes");
        this.classProgression = Objects.requireNonNull(classProgression, "classProgression");
        this.engine = new SkillExecutionEngine(new SystemGameClock(), this::execute);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (inputReserved.test(player) || !playable(player)) {
            return;
        }
        event.setCancelled(true);
        routeInput(player, player.isSneaking() ? CombatInputKey.SHIFT_F : CombatInputKey.F, null);
    }

    /** RMB is owned only in air so block/container interactions retain priority. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || event.getAction() != Action.RIGHT_CLICK_AIR
                || inputReserved.test(event.getPlayer())
                || !playable(event.getPlayer())) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        routeInput(player, player.isSneaking()
                ? CombatInputKey.SHIFT_RMB : CombatInputKey.RMB, null);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // PlayerSessionListener activates the class-derived stat block after the
        // asynchronous profile load has completed.
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        comboResolver.reset(playerId);
        loadouts.forget(playerId);
        engine.activeCast(playerId).ifPresent(cast -> {
            engine.interrupt(cast.castId(), "logout");
            combat.engine().endCast(cast.castId());
            engine.forget(cast.castId());
        });
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
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            boolean inCombat = combat.engine().combatState()
                    .inCombat(player.getUniqueId(), java.time.Instant.now());
            attributes.find(player.getUniqueId()).ifPresent(ignored ->
                    attributes.tick(player.getUniqueId(), 1L, inCombat));
        }
    }

    public SkillExecutionEngine engine() {
        return engine;
    }

    public ResourceView resources(UUID playerId) {
        return attributes.find(playerId).map(block -> {
            var pools = block.resources();
            var mana = pools.get(ResourceType.MANA);
            var stamina = pools.get(ResourceType.STAMINA);
            var primary = pools.get(block.primaryResource());
            return new ResourceView(
                    mana == null ? 0 : mana.current(), mana == null ? 0 : mana.maximum(),
                    stamina == null ? 0 : stamina.current(), stamina == null ? 0 : stamina.maximum(),
                    block.primaryResource(), primary.current(), primary.maximum());
        }).orElseGet(() -> new ResourceView(0, 0, 0, 0,
                ResourceType.HEALTH, 0, 0));
    }

    /** Routes vanilla melee intent through the same authoritative skill state machine. */
    public void basicAttack(Player player, LivingEntity target) {
        if (inputReserved.test(player) || !playable(player)) return;
        routeInput(player, player.isSneaking()
                ? CombatInputKey.SHIFT_LMB : CombatInputKey.LMB, target);
    }

    private void routeInput(Player player, CombatInputKey key, LivingEntity suppliedTarget) {
        var session = sessions.requirePlayable(player.getUniqueId());
        var snapshot = content.snapshot();
        CombatInputProfileDefinition profile =
                snapshot.combatInputProfiles().get(DEFAULT_INPUT_PROFILE);
        if (profile == null) {
            player.sendActionBar(Component.text("Combat controls are unavailable."));
            return;
        }
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
        var location = player.getLocation();
        long loadoutRevision = loadouts.revision(player.getUniqueId());
        CombatInputIntent intent = new CombatInputIntent(UUID.randomUUID(),
                player.getUniqueId(), session.token(), key,
                Math.max(0L, player.getTicksLived()), System.nanoTime(),
                java.util.Optional.empty(),
                java.util.Optional.ofNullable(suppliedTarget).map(Entity::getUniqueId),
                new WorldPoint(location.getWorld().getUID(), location.getX(),
                        location.getY(), location.getZ()),
                profile.revision(), snapshot.revision(), loadoutRevision);
        InputResolution resolution = inputEngine.accept(intent, profile,
                snapshot.combatCombos().values(), new CombatInputEngine.Context(
                        session.token(), session.playable(), snapshot.revision(),
                        loadoutRevision, weapon.tags(), null));
        if (resolution.outcome() == InputResolution.Outcome.REJECTED) {
            telemetry.increment("input.rejected");
            player.sendActionBar(Component.text("Cannot use input: " + resolution.rejection()));
            return;
        }
        if (resolution.outcome() == InputResolution.Outcome.COMBO_ADVANCED) return;
        ContentId classId = session.profile().classId().orElseThrow();
        var activeWeapon = weapon;
        ContentId skillId = resolution.skillId().orElseGet(() -> resolution.slot()
                .map(slot -> skillForSlot(classId, activeWeapon, slot)).orElse(null));
        if (skillId == null) {
            player.sendActionBar(Component.text("No skill is bound to that input."));
            return;
        }
        if (classSkill(classId, skillId)
                && !classProgression.skillUnlocked(player.getUniqueId(), skillId)) {
            player.sendActionBar(Component.text(
                    "Unlock this class skill in the Skill Tree first."));
            return;
        }
        SkillDefinition definition = snapshot.skills().get(skillId);
        if (definition == null) {
            player.sendActionBar(Component.text("Skill content is unavailable."));
            return;
        }
        LivingEntity target = suppliedTarget;
        if (target == null) {
            Entity selected = player.getTargetEntity(
                    Math.max(1, (int) Math.ceil(definition.range())));
            target = selected instanceof LivingEntity living ? living : null;
        }
        SkillEffectNode directDamage = firstDirectDamage(
                definition, definition.effects().get(definition.rootEffect()));
        if (directDamage != null) {
            if (target == null) {
                player.sendActionBar(Component.text("No valid target."));
                return;
            }
            DamageType damageType = DamageType.valueOf(directDamage.values()
                    .getOrDefault("type", "physical").toUpperCase(Locale.ROOT));
            double power = directDamage.numbers().getOrDefault("power", 0.0);
            var rejection = combat.engine().eligibility(new DamageRequest(UUID.randomUUID(),
                    player.getUniqueId(), target.getUniqueId(), damageType, power,
                    definition.range(), definition.requiresLineOfSight(), 1));
            if (rejection != null) {
                player.sendActionBar(Component.text("Cannot cast: "
                        + rejection.name().toLowerCase(Locale.ROOT).replace('_', ' ')));
                return;
            }
        }
        var result = engine.begin(definition, caster(player),
                target == null ? null : target.getUniqueId(), snapshot.revision());
        if (result.started()) {
            skillListener.accept(player.getUniqueId(), definition.id());
        }
        telemetry.increment("skill.usage");
        if (!result.started()) {
            telemetry.increment("skill.rejected." + result.rejection().name()
                    .toLowerCase(Locale.ROOT));
            player.sendActionBar(Component.text("Cannot cast: "
                    + result.rejection().name().toLowerCase(Locale.ROOT).replace('_', ' ')));
        } else {
            player.sendActionBar(Component.text(definition.displayName() + " - casting"));
        }
    }

    private ContentId skillForSlot(ContentId classId,
                                   com.branz.mmorpg.api.item.WeaponDefinition weapon,
                                   SkillSlot slot) {
        CharacterClassDefinition definition = content.snapshot().characterClasses().get(classId);
        if (definition == null) return null;
        return switch (slot) {
            case BASIC_ATTACK -> weapon.basicAttackSkillId();
            case WEAPON_SKILL_1 -> weapon.activeSkillIds().isEmpty()
                    ? null : weapon.activeSkillIds().get(0);
            case WEAPON_SKILL_2 -> weapon.activeSkillIds().size() < 2
                    ? null : weapon.activeSkillIds().get(1);
            case CLASS_SKILL_1 -> definition.classSkillIds().isEmpty()
                    ? null : definition.classSkillIds().get(0);
            case CLASS_SKILL_2 -> definition.classSkillIds().size() < 2
                    ? null : definition.classSkillIds().get(1);
            case ULTIMATE -> definition.ultimateSkillId();
        };
    }

    private boolean classSkill(ContentId classId, ContentId skillId) {
        CharacterClassDefinition definition = content.snapshot().characterClasses().get(classId);
        return definition != null && (definition.classSkillIds().contains(skillId)
                || definition.ultimateSkillId().equals(skillId));
    }

    private static SkillEffectNode firstDirectDamage(
            SkillDefinition definition, SkillEffectNode node) {
        if (node == null || node.type() == com.branz.mmorpg.api.skill.SkillEffectType.SPAWN_PROJECTILE) {
            return null;
        }
        if (node.type() == com.branz.mmorpg.api.skill.SkillEffectType.DAMAGE) return node;
        for (String childId : node.children()) {
            SkillEffectNode found = firstDirectDamage(
                    definition, definition.effects().get(childId));
            if (found != null) return found;
        }
        return null;
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
                               double stamina, double maximumStamina,
                               ResourceType primaryResource, double primary,
                               double maximumPrimary) {
    }

    private SkillCaster caster(Player player) {
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
            @Override public double cooldownRecovery() {
                return attributes.attributes(player.getUniqueId())
                        .get(AttributeType.COOLDOWN_RECOVERY);
            }
            @Override public boolean spend(Map<ResourceType, Double> costs) {
                double mana = costs.getOrDefault(ResourceType.MANA, 0.0);
                double stamina = costs.getOrDefault(ResourceType.STAMINA, 0.0);
                double health = costs.getOrDefault(ResourceType.HEALTH, 0.0);
                if (player.getHealth() <= health) {
                    return false;
                }
                boolean spent = attributes.spend(player.getUniqueId(), costs, "skill_cost");
                if (spent && health > 0) player.setHealth(player.getHealth() - health);
                return spent;
            }
            @Override public void refund(Map<ResourceType, Double> costs, double fraction) {
                costs.forEach((resource, amount) -> attributes.add(player.getUniqueId(), resource,
                        amount * fraction, "skill_refund"));
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
                LivingEntity recipient = target == null ? caster : target;
                String status = node.values().get("status");
                if (status != null) {
                    statuses.apply(recipient.getUniqueId(), ContentId.parse(status),
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
        if (sessions.session(player.getUniqueId()).map(PlayerSession::playable).orElse(false)
                && sessions.requirePlayable(player.getUniqueId()).profile().classId().isPresent()) {
            try {
                attributes.require(player.getUniqueId());
                return true;
            } catch (RuntimeException unavailable) {
                return false;
            }
        }
        return false;
    }

    private record ProjectileCast(
            SkillCastSnapshot cast, SkillDefinition definition,
            SkillEffectNode node, UUID casterId) {
    }
}
