package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.item.LootService;
import com.branz.mmorpg.api.item.LootDefinition;
import com.branz.mmorpg.api.mob.MobAiState;
import com.branz.mmorpg.api.mob.MobDecision;
import com.branz.mmorpg.api.mob.MobDefinition;
import com.branz.mmorpg.api.mob.MobRepository;
import com.branz.mmorpg.api.mob.MobRuntimeSnapshot;
import com.branz.mmorpg.api.mob.MobTargetCandidate;
import com.branz.mmorpg.api.mob.SpatialPosition;
import com.branz.mmorpg.api.runtime.Scheduler;
import com.branz.mmorpg.api.social.PartyService;
import com.branz.mmorpg.core.mob.MobAiEngine;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Paper presentation/navigation adapter for durable C9 mob state. */
public final class PaperMobRuntime implements Listener {
    private final JavaPlugin plugin;
    private final MobRepository repository;
    private final ContentService content;
    private final Scheduler scheduler;
    private final LootService loot;
    private final PartyService parties;
    private final MobAiEngine engine = new MobAiEngine();
    private final NamespacedKey instanceKey;
    private final NamespacedKey definitionKey;
    private final Map<UUID, MobRuntimeSnapshot> states = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> entities = new ConcurrentHashMap<>();
    private final Map<UUID, Map<UUID, Double>> contributions = new ConcurrentHashMap<>();
    private final Map<UUID, Set<ContentId>> abilityOverrides = new ConcurrentHashMap<>();
    private final Set<UUID> rewardSuppressed = ConcurrentHashMap.newKeySet();

    public PaperMobRuntime(JavaPlugin plugin, MobRepository repository,
                           ContentService content, Scheduler scheduler, LootService loot,
                           PartyService parties) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.repository = java.util.Objects.requireNonNull(repository, "repository");
        this.content = java.util.Objects.requireNonNull(content, "content");
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        this.loot = java.util.Objects.requireNonNull(loot, "loot");
        this.parties = java.util.Objects.requireNonNull(parties, "parties");
        instanceKey = new NamespacedKey(plugin, "mob_instance");
        definitionKey = new NamespacedKey(plugin, "mob_definition");
        bindExistingEntities();
        scheduler.async(repository::list).whenComplete((loaded, failure) ->
                scheduler.sync(() -> {
                    if (failure != null) {
                        plugin.getLogger().warning("Mob recovery failed: " + failure.getMessage());
                        return;
                    }
                    loaded.forEach(state -> {
                        states.put(state.instanceId(), state);
                        if (state.state() != MobAiState.DEAD
                                && !entities.containsKey(state.instanceId())) spawnEntity(state);
                    });
                }));
    }

    public void tick() {
        Instant now = Instant.now();
        for (var entry : states.entrySet()) {
            MobRuntimeSnapshot state = entry.getValue();
            Entity raw = entity(state.instanceId()).orElse(null);
            if (!(raw instanceof LivingEntity living) || living.isDead()) continue;
            MobDefinition definition = content.snapshot().mobs().get(state.definitionId());
            if (definition == null) {
                living.customName(Component.text("BROKEN: " + state.definitionId()));
                living.setAI(false);
                continue;
            }
            state = engine.withPosition(state, position(living.getLocation()));
            state = engine.withHealth(state, Math.min(state.maximumHealth(), living.getHealth()));
            MobDecision decision = engine.decide(
                    withAbilityOverride(definition, state.instanceId()), state,
                    targets(living, definition.aggroRange()), now);
            states.put(state.instanceId(), decision.snapshot());
            apply(living, decision);
            if (decision.action() != MobDecision.Action.NONE) {
                MobRuntimeSnapshot persisted = decision.snapshot();
                scheduler.async(() -> repository.save(persisted)).exceptionally(failure -> {
                    plugin.getLogger().warning("Mob state save failed: " + failure.getMessage());
                    return null;
                });
            }
        }
    }

    public void spawn(ContentId definitionId, int level, Location home,
                      java.util.function.Consumer<String> feedback) {
        spawn(definitionId, level, home, ignored -> { }, feedback);
    }

    public void spawn(ContentId definitionId, int level, Location home,
                      java.util.function.Consumer<MobRuntimeSnapshot> success,
                      java.util.function.Consumer<String> feedback) {
        MobDefinition definition = requireDefinition(definitionId);
        double maximumHealth = scaledMaximumHealth(definition, level);
        MobRuntimeSnapshot state = MobRuntimeSnapshot.spawn(
                UUID.randomUUID(), definitionId, level, position(home),
                maximumHealth, Instant.now());
        scheduler.async(() -> repository.insert(state)).whenComplete((saved, failure) ->
                scheduler.sync(() -> {
                    if (failure != null) feedback.accept("Mob spawn failed: " + failure.getMessage());
                    else {
                        states.put(saved.instanceId(), saved);
                        spawnEntity(saved);
                        success.accept(saved);
                        feedback.accept("Spawned " + definitionId + " as " + saved.instanceId());
                    }
                }));
    }

    public void remove(UUID instanceId, java.util.function.Consumer<String> feedback) {
        remove(instanceId, ignored -> { }, feedback);
    }

    public void remove(UUID instanceId, java.util.function.Consumer<Boolean> success,
                       java.util.function.Consumer<String> feedback) {
        scheduler.async(() -> repository.remove(instanceId)).whenComplete((removed, failure) ->
                scheduler.sync(() -> {
                    if (failure != null) feedback.accept("Mob removal failed: " + failure.getMessage());
                    else {
                        states.remove(instanceId);
                        contributions.remove(instanceId);
                        abilityOverrides.remove(instanceId);
                        rewardSuppressed.remove(instanceId);
                        entity(instanceId).ifPresent(Entity::remove);
                        entities.remove(instanceId);
                        success.accept(removed);
                        feedback.accept(removed ? "Removed " + instanceId : "Unknown mob " + instanceId);
                    }
                }));
    }

    public Collection<MobRuntimeSnapshot> states() {
        return java.util.List.copyOf(states.values());
    }

    public Optional<MobRuntimeSnapshot> state(UUID instanceId) {
        return Optional.ofNullable(states.get(instanceId));
    }

    public void reset(UUID instanceId, java.util.function.Consumer<String> feedback) {
        MobRuntimeSnapshot before = states.get(instanceId);
        if (before == null) {
            feedback.accept("Unknown mob " + instanceId);
            return;
        }
        Instant now = Instant.now();
        MobRuntimeSnapshot reset = new MobRuntimeSnapshot(
                before.instanceId(), before.definitionId(), before.level(),
                before.home(), before.home(), MobAiState.IDLE, Optional.empty(),
                before.maximumHealth(), before.maximumHealth(), now, now, now,
                before.decisionSequence() + 1, before.rewardSequence());
        scheduler.async(() -> repository.save(reset)).whenComplete((saved, failure) ->
                scheduler.sync(() -> {
                    if (failure != null) {
                        feedback.accept("Mob reset failed: " + failure.getMessage());
                        return;
                    }
                    states.put(instanceId, saved);
                    Entity raw = entity(instanceId).orElse(null);
                    if (raw instanceof LivingEntity living) {
                        living.teleport(location(saved.home()));
                        living.setHealth(saved.maximumHealth());
                        living.setInvulnerable(false);
                        living.setAI(true);
                    } else {
                        spawnEntity(saved);
                    }
                    feedback.accept("Mob reset to canonical home state.");
                }));
    }

    public void setAbilityOverride(UUID instanceId, Set<ContentId> abilities) {
        if (abilities.isEmpty()) abilityOverrides.remove(instanceId);
        else abilityOverrides.put(instanceId, Set.copyOf(abilities));
    }

    public void setRewardSuppressed(UUID instanceId, boolean suppressed) {
        if (suppressed) rewardSuppressed.add(instanceId);
        else rewardSuppressed.remove(instanceId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        UUID instanceId = instanceId(event.getEntity()).orElse(null);
        if (instanceId == null) return;
        Player player = event.getDamager() instanceof Player direct ? direct
                : event.getDamager() instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player shooter ? shooter : null;
        if (player != null) {
            contributions.computeIfAbsent(instanceId, ignored -> new ConcurrentHashMap<>())
                    .merge(player.getUniqueId(), event.getFinalDamage(), Double::sum);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        UUID instanceId = instanceId(event.getEntity()).orElse(null);
        if (instanceId == null) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        MobRuntimeSnapshot current = states.get(instanceId);
        if (current == null) return;
        MobDefinition definition = content.snapshot().mobs().get(current.definitionId());
        if (definition == null) return;
        MobDecision death = engine.decide(
                definition, engine.withHealth(current, 0), java.util.List.of(), Instant.now());
        states.put(instanceId, death.snapshot());
        entities.remove(instanceId);
        Map<UUID, Double> damage = contributions.remove(instanceId);
        boolean awardDirectly = !rewardSuppressed.remove(instanceId);
        Location rawDeath = event.getEntity().getLocation();
        WorldPoint deathLocation = new WorldPoint(rawDeath.getWorld().getUID(),
                rawDeath.getX(), rawDeath.getY(), rawDeath.getZ());
        Map<UUID, WorldPoint> onlineLocations = plugin.getServer().getOnlinePlayers().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Player::getUniqueId, player -> {
                            Location location = player.getLocation();
                            return new WorldPoint(location.getWorld().getUID(),
                                    location.getX(), location.getY(), location.getZ());
                        }));
        scheduler.async(() -> {
            repository.save(death.snapshot());
            if (awardDirectly && damage != null) {
                awardLoot(definition, instanceId, death.snapshot().rewardSequence(),
                        damage, deathLocation, onlineLocations);
            }
        }).exceptionally(failure -> {
            plugin.getLogger().warning("Mob death completion failed; retry is safe: "
                    + failure.getMessage());
            return null;
        });
    }

    private void awardLoot(
            MobDefinition mob, UUID instanceId, long rewardSequence,
            Map<UUID, Double> damage, WorldPoint death,
            Map<UUID, WorldPoint> onlineLocations) {
        LootDefinition table = content.snapshot().lootTables().get(mob.lootTableId());
        if (table == null) {
            throw new IllegalStateException("unknown mob loot table " + mob.lootTableId());
        }
        String roll = "mob:" + instanceId + ':' + rewardSequence;
        if (table.ownership() == LootDefinition.Ownership.PERSONAL) {
            damage.forEach((playerId, contribution) -> loot.resolvePersonal(
                    playerId, mob.lootTableId(), roll,
                    contribution >= mob.minimumContribution(), Set.of(), Map.of()));
            return;
        }
        Map<UUID, Set<UUID>> groups = new java.util.HashMap<>();
        for (UUID contributor : damage.keySet()) {
            var party = parties.party(contributor).orElse(null);
            UUID groupId = party == null ? contributor : party.partyId();
            Set<UUID> eligible = groups.computeIfAbsent(
                    groupId, ignored -> new java.util.HashSet<>());
            Collection<UUID> candidates = party == null
                    ? Set.of(contributor) : party.members();
            for (UUID candidate : candidates) {
                WorldPoint position = onlineLocations.get(candidate);
                if (position == null || !position.worldId().equals(death.worldId())) continue;
                double range = party == null ? 64.0 : party.rewardRange();
                if (position.distanceSquared(death) > range * range) continue;
                if (!table.contributionRequired()
                        || damage.getOrDefault(candidate, 0.0) >= mob.minimumContribution()) {
                    eligible.add(candidate);
                }
            }
        }
        groups.forEach((groupId, eligible) -> loot.resolveParty(
                eligible, mob.lootTableId(), roll + ":party:" + groupId,
                Set.of(), Map.of()));
    }

    private record WorldPoint(UUID worldId, double x, double y, double z) {
        double distanceSquared(WorldPoint other) {
            double dx = x - other.x;
            double dy = y - other.y;
            double dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    private void apply(LivingEntity living, MobDecision decision) {
        if (decision.snapshot().state() == MobAiState.RESET) {
            living.setInvulnerable(true);
            living.setHealth(Math.min(living.getMaxHealth(), decision.snapshot().health()));
            if (decision.snapshot().position().equals(decision.snapshot().home())) {
                living.teleport(location(decision.snapshot().home()));
                living.setInvulnerable(false);
            } else if (decision.requestPath() && living instanceof org.bukkit.entity.Mob mob) {
                mob.getPathfinder().moveTo(location(decision.snapshot().home()));
            }
            return;
        }
        living.setInvulnerable(false);
        if (living instanceof org.bukkit.entity.Mob mob) {
            LivingEntity target = decision.targetId().flatMap(this::living).orElse(null);
            mob.setTarget(target);
        }
        if (decision.action() == MobDecision.Action.CAST) {
            decision.targetId().flatMap(this::living).ifPresent(target -> {
                double power = content.snapshot().skills().get(decision.skillId().orElseThrow())
                        .effects().values().stream()
                        .filter(node -> node.type()
                                == com.branz.mmorpg.api.skill.SkillEffectType.DAMAGE)
                        .mapToDouble(node -> node.numbers().getOrDefault("power", 0d)).sum();
                target.damage(Math.max(0.1, power), living);
            });
        }
    }

    private void spawnEntity(MobRuntimeSnapshot state) {
        MobDefinition definition = requireDefinition(state.definitionId());
        NamespacedKey key = NamespacedKey.fromString(definition.presentation().entityType());
        EntityType type = key == null ? null : Registry.ENTITY_TYPE.get(key);
        if (type == null || !type.isAlive()) {
            plugin.getLogger().severe("Unsupported mob entity type "
                    + definition.presentation().entityType());
            return;
        }
        Entity raw = location(state.position()).getWorld().spawnEntity(location(state.position()), type);
        if (!(raw instanceof LivingEntity living)) {
            raw.remove();
            return;
        }
        living.getPersistentDataContainer().set(
                instanceKey, PersistentDataType.STRING, state.instanceId().toString());
        living.getPersistentDataContainer().set(
                definitionKey, PersistentDataType.STRING, state.definitionId().toString());
        living.customName(Component.text(definition.displayName()));
        living.setCustomNameVisible(true);
        living.setRemoveWhenFarAway(false);
        living.setMaxHealth(state.maximumHealth());
        living.setHealth(Math.min(state.health(), state.maximumHealth()));
        entities.put(state.instanceId(), living.getUniqueId());
        // ModelEngine is intentionally optional: the vanilla entity remains authoritative.
        if (definition.presentation().modelId().isPresent()
                && plugin.getServer().getPluginManager().getPlugin("ModelEngine") == null) {
            plugin.getLogger().fine("ModelEngine absent; using vanilla presentation for "
                    + definition.id());
        }
    }

    private void bindExistingEntities() {
        plugin.getServer().getWorlds().forEach(world -> world.getEntities().forEach(entity ->
                instanceId(entity).ifPresent(id -> entities.putIfAbsent(id, entity.getUniqueId()))));
    }

    private Collection<MobTargetCandidate> targets(LivingEntity mob, double range) {
        ArrayList<MobTargetCandidate> result = new ArrayList<>();
        for (Entity nearby : mob.getNearbyEntities(range, range, range)) {
            if (nearby instanceof Player player && player.isOnline()
                    && !player.isDead() && player.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
                double threat = contributions.getOrDefault(instanceId(mob).orElseThrow(), Map.of())
                        .getOrDefault(player.getUniqueId(), 0d) + 1;
                result.add(new MobTargetCandidate(player.getUniqueId(),
                        position(player.getLocation()), true, true, threat, Set.of("player")));
            }
        }
        return result;
    }

    private Optional<Entity> entity(UUID instanceId) {
        UUID entityId = entities.get(instanceId);
        return entityId == null ? Optional.empty()
                : Optional.ofNullable(plugin.getServer().getEntity(entityId));
    }

    private Optional<LivingEntity> living(UUID entityId) {
        Entity entity = plugin.getServer().getEntity(entityId);
        return entity instanceof LivingEntity living ? Optional.of(living) : Optional.empty();
    }

    public Optional<UUID> instanceId(Entity entity) {
        String value = entity.getPersistentDataContainer()
                .get(instanceKey, PersistentDataType.STRING);
        if (value == null) return Optional.empty();
        try {
            return Optional.of(UUID.fromString(value));
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    private MobDefinition requireDefinition(ContentId id) {
        MobDefinition result = content.snapshot().mobs().get(id);
        if (result == null) throw new IllegalArgumentException("unknown mob " + id);
        return result;
    }

    private MobDefinition withAbilityOverride(MobDefinition definition, UUID instanceId) {
        Set<ContentId> allowed = abilityOverrides.get(instanceId);
        if (allowed == null) return definition;
        var abilities = definition.abilities().stream()
                .filter(ability -> allowed.contains(ability.skillId())).toList();
        if (abilities.isEmpty()) return definition;
        return new MobDefinition(definition.id(), definition.displayName(),
                definition.baseStats(), definition.scaling(), definition.faction(),
                definition.targetPolicy(), definition.navigation(), abilities,
                definition.aggroRange(), definition.leashRange(), definition.resetMillis(),
                definition.homeRegionId(), definition.statusImmunities(),
                definition.statusResistances(), definition.lootTableId(),
                definition.minimumContribution(), definition.presentation());
    }

    private static double scaledMaximumHealth(MobDefinition definition, int level) {
        double base = definition.baseStats().getOrDefault("max_health", 20d);
        double multiplier = Math.min(definition.scaling().maximumMultiplier(),
                1 + definition.scaling().healthPerLevel() * Math.max(0, level - 1));
        return Math.max(1, base * multiplier);
    }

    private SpatialPosition position(Location location) {
        return new SpatialPosition(location.getWorld().getUID(),
                location.getX(), location.getY(), location.getZ());
    }

    private Location location(SpatialPosition position) {
        var world = plugin.getServer().getWorld(position.worldId());
        if (world == null) throw new IllegalStateException("mob world is not loaded");
        return new Location(world, position.x(), position.y(), position.z());
    }
}
