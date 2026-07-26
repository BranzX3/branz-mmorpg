package com.branz.mmorpg.paper;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentService;
import com.branz.mmorpg.api.encounter.ContributionType;
import com.branz.mmorpg.api.encounter.EncounterDefinition;
import com.branz.mmorpg.api.encounter.EncounterService;
import com.branz.mmorpg.api.encounter.EncounterSnapshot;
import com.branz.mmorpg.api.encounter.EncounterState;
import com.branz.mmorpg.api.runtime.Scheduler;
import java.time.Duration;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Paper arena, reconnect, contribution, reward, and cleanup adapter for C10. */
public final class PaperEncounterRuntime implements Listener {
    private final JavaPlugin plugin;
    private final EncounterService service;
    private final ContentService content;
    private final Scheduler scheduler;
    private final PaperMobRuntime mobs;
    private final Map<UUID, EncounterSnapshot> states = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> actorEncounter = new ConcurrentHashMap<>();
    private final Map<UUID, CompletableFuture<?>> serial = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private final Set<UUID> completionNotified = ConcurrentHashMap.newKeySet();
    private volatile Consumer<EncounterSnapshot> completionListener = ignored -> { };

    public PaperEncounterRuntime(JavaPlugin plugin, EncounterService service,
                                 ContentService content, Scheduler scheduler,
                                 PaperMobRuntime mobs) {
        this.plugin = java.util.Objects.requireNonNull(plugin, "plugin");
        this.service = java.util.Objects.requireNonNull(service, "service");
        this.content = java.util.Objects.requireNonNull(content, "content");
        this.scheduler = java.util.Objects.requireNonNull(scheduler, "scheduler");
        this.mobs = java.util.Objects.requireNonNull(mobs, "mobs");
        recover();
    }

    public void start(ContentId definitionId, Set<UUID> participants, Location arena,
                      Consumer<String> feedback) {
        EncounterDefinition definition = definition(definitionId);
        scheduler.async(() -> service.create(definitionId, participants))
                .whenComplete((created, failure) -> scheduler.sync(() -> {
                    if (failure != null) {
                        feedback.accept("Encounter creation failed: " + root(failure));
                        return;
                    }
                    states.put(created.instanceId(), created);
                    feedback.accept("Preparing " + definition.displayName()
                            + " (" + created.instanceId() + ")");
                    scheduler.syncLater(() -> spawnBoss(created.instanceId(), definition,
                                    arena.clone(), feedback),
                            Duration.ofMillis(definition.preparationMillis()));
                }));
    }

    public CompletableFuture<Void> startDurable(
            UUID instanceId, ContentId definitionId,
            Set<UUID> participants, Location arena) {
        EncounterDefinition definition = definition(definitionId);
        return scheduler.async(() ->
                        service.create(instanceId, definitionId, participants))
                .thenCompose(created -> scheduler.sync(() -> {
                    states.put(created.instanceId(), created);
                    if (created.state() == EncounterState.PREPARING) {
                        scheduler.syncLater(() -> spawnBoss(created.instanceId(), definition,
                                        arena.clone(), ignored -> {}),
                                Duration.ofMillis(definition.preparationMillis()));
                    }
                }));
    }

    public Collection<EncounterSnapshot> states() {
        return java.util.List.copyOf(states.values());
    }

    public void completionListener(Consumer<EncounterSnapshot> listener) {
        completionListener = java.util.Objects.requireNonNull(listener, "listener");
    }

    public void abandon(UUID encounterId, Consumer<String> feedback) {
        submit(encounterId, () -> service.abandon(encounterId), failed -> {
            states.put(encounterId, failed);
            feedback.accept("Encounter marked failed; cleanup started.");
            cleanup(failed);
        }, feedback);
    }

    public void tick() {
        for (EncounterSnapshot state : states.values()) {
            if (state.state() == EncounterState.ACTIVE
                    && state.connectedParticipants().isEmpty()) {
                submit(state.instanceId(), () -> service.checkWipe(state.instanceId()), checked -> {
                    states.put(checked.instanceId(), checked);
                    if (checked.state() == EncounterState.FAILED) cleanup(checked);
                }, message -> plugin.getLogger().warning(message));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        UUID actorId = mobs.instanceId(event.getEntity()).orElse(null);
        UUID encounterId = actorId == null ? null : actorEncounter.get(actorId);
        if (encounterId == null) return;
        Player player = attacker(event.getDamager());
        if (player != null) {
            submit(encounterId, () -> service.contribute(encounterId,
                    player.getUniqueId(), ContributionType.DAMAGE, event.getFinalDamage()),
                    state -> states.put(encounterId, state),
                    message -> plugin.getLogger().warning(message));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnyDamage(EntityDamageEvent event) {
        if (event.isCancelled()) return;
        UUID actorId = mobs.instanceId(event.getEntity()).orElse(null);
        UUID encounterId = actorId == null ? null : actorEncounter.get(actorId);
        if (encounterId == null) return;
        if (event.getEntity() instanceof org.bukkit.entity.LivingEntity living) {
            double remaining = Math.max(0, living.getHealth() - event.getFinalDamage());
            double fraction = remaining / living.getMaxHealth();
            BossBar bar = bossBars.get(encounterId);
            if (bar != null) bar.progress((float) Math.max(0, Math.min(1, fraction)));
            submit(encounterId, () -> service.bossHealth(encounterId, fraction),
                    this::phaseOrComplete,
                    message -> plugin.getLogger().warning(message));
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        forParticipant(event.getPlayer().getUniqueId(),
                id -> submit(id, () -> service.disconnect(id, event.getPlayer().getUniqueId()),
                        state -> states.put(id, state),
                        message -> plugin.getLogger().warning(message)));
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        forParticipant(event.getPlayer().getUniqueId(),
                id -> submit(id, () -> service.connect(id, event.getPlayer().getUniqueId()),
                        state -> states.put(id, state),
                        message -> plugin.getLogger().warning(message)));
    }

    private void spawnBoss(UUID encounterId, EncounterDefinition definition,
                           Location arena, Consumer<String> feedback) {
        EncounterSnapshot current = states.get(encounterId);
        if (current == null || current.state() != EncounterState.PREPARING) return;
        Chunk chunk = arena.getChunk();
        chunk.setForceLoaded(true);
        String chunkKey = chunkKey(chunk);
        mobs.spawn(definition.bossMobId(), 1, arena, boss -> {
            mobs.setRewardSuppressed(boss.instanceId(), true);
            submit(encounterId,
                    () -> service.activate(encounterId,
                            Set.of(boss.instanceId()), Set.of(chunkKey)),
                    active -> {
                        states.put(encounterId, active);
                        actorEncounter.put(boss.instanceId(), encounterId);
                        applyPhase(active);
                        showBossBar(active);
                        feedback.accept(definition.displayName() + " is active.");
                    }, feedback);
        }, failure -> {
            feedback.accept(failure);
            chunk.setForceLoaded(false);
            abandon(encounterId, feedback);
        });
    }

    private void phaseOrComplete(EncounterSnapshot state) {
        states.put(state.instanceId(), state);
        applyPhase(state);
        if (state.state() == EncounterState.SUCCESS) {
            if (completionNotified.add(state.instanceId())) completionListener.accept(state);
            submit(state.instanceId(), () -> service.deliverRewards(state.instanceId()),
                    rewarded -> cleanup(rewarded),
                    message -> plugin.getLogger().warning(
                            "Encounter reward retry required: " + message));
        }
    }

    private void applyPhase(EncounterSnapshot state) {
        EncounterDefinition definition = definition(state.definitionId());
        int phase = Math.min(state.phaseIndex(), definition.phases().size() - 1);
        state.actorIds().forEach(actor -> {
            actorEncounter.put(actor, state.instanceId());
            mobs.setRewardSuppressed(actor, true);
            mobs.setAbilityOverride(actor, definition.phases().get(phase).abilityIds());
        });
        BossBar bar = bossBars.get(state.instanceId());
        if (bar != null) {
            String phaseName = definition.phases().get(phase).id().replace('_', ' ');
            bar.name(Component.text(definition.displayName() + " — " + phaseName));
            state.connectedParticipants().forEach(playerId -> {
                Player player = plugin.getServer().getPlayer(playerId);
                if (player != null) {
                    player.playSound(player.getLocation(),
                            org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.2f);
                }
            });
        }
    }

    private void cleanup(EncounterSnapshot terminal) {
        submit(terminal.instanceId(), () -> service.beginCleanup(terminal.instanceId()),
                cleaning -> scheduler.sync(() -> performCleanup(cleaning)),
                message -> plugin.getLogger().warning(message));
    }

    private void performCleanup(EncounterSnapshot cleaning) {
        hideBossBar(cleaning);
        Set<String> chunks = cleaning.forcedChunkKeys();
        chunks.forEach(this::releaseChunk);
        if (cleaning.actorIds().isEmpty()) {
            acknowledge(cleaning, Set.of(), chunks);
            return;
        }
        Set<UUID> actors = cleaning.actorIds();
        AtomicInteger remaining = new AtomicInteger(actors.size());
        actors.forEach(actor -> mobs.remove(actor, removed -> {
            actorEncounter.remove(actor);
            if (remaining.decrementAndGet() == 0) acknowledge(cleaning, actors, chunks);
        }, message -> {
            plugin.getLogger().warning(message);
            if (remaining.decrementAndGet() == 0) acknowledge(cleaning, actors, chunks);
        }));
    }

    private void showBossBar(EncounterSnapshot state) {
        EncounterDefinition definition = definition(state.definitionId());
        BossBar bar = BossBar.bossBar(Component.text(definition.displayName()), 1,
                BossBar.Color.RED, BossBar.Overlay.PROGRESS);
        bossBars.put(state.instanceId(), bar);
        state.participantSnapshot().forEach(playerId -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) player.showBossBar(bar);
        });
    }

    private void hideBossBar(EncounterSnapshot state) {
        BossBar bar = bossBars.remove(state.instanceId());
        if (bar == null) return;
        state.participantSnapshot().forEach(playerId -> {
            Player player = plugin.getServer().getPlayer(playerId);
            if (player != null) player.hideBossBar(bar);
        });
    }

    private void acknowledge(
            EncounterSnapshot cleaning, Set<UUID> actors, Set<String> chunks) {
        submit(cleaning.instanceId(), () -> service.acknowledgeCleanup(
                        cleaning.instanceId(), actors, chunks),
                closed -> states.put(closed.instanceId(), closed),
                message -> plugin.getLogger().warning(message));
    }

    private void recover() {
        scheduler.async(service::recoverable).whenComplete((recovered, failure) ->
                scheduler.sync(() -> {
                    if (failure != null) {
                        plugin.getLogger().warning("Encounter recovery failed: " + root(failure));
                        return;
                    }
                    recovered.forEach(state -> {
                        states.put(state.instanceId(), state);
                        state.forcedChunkKeys().forEach(this::forceChunk);
                        if (state.state() == EncounterState.ACTIVE) applyPhase(state);
                        else if (state.state() == EncounterState.SUCCESS) phaseOrComplete(state);
                        else if (state.state() == EncounterState.FAILED
                                || state.state() == EncounterState.CLEANING) cleanup(state);
                        else abandon(state.instanceId(),
                                message -> plugin.getLogger().warning(message));
                    });
                }));
    }

    private <T> void submit(UUID encounterId, Supplier<T> work,
                            Consumer<T> success, Consumer<String> failure) {
        serial.compute(encounterId, (ignored, previous) -> {
            CompletableFuture<?> head = previous == null
                    ? CompletableFuture.completedFuture(null) : previous;
            CompletableFuture<T> next = head.handle((value, error) -> null)
                    .thenCompose(value -> scheduler.async(work));
            next.whenComplete((result, error) -> scheduler.sync(() -> {
                if (error != null) failure.accept(root(error));
                else success.accept(result);
            }));
            return next;
        });
    }

    private void forParticipant(UUID playerId, Consumer<UUID> action) {
        states.values().stream()
                .filter(state -> state.state() == EncounterState.ACTIVE)
                .filter(state -> state.participantSnapshot().contains(playerId))
                .map(EncounterSnapshot::instanceId).forEach(action);
    }

    private EncounterDefinition definition(ContentId id) {
        EncounterDefinition result = content.snapshot().encounters().get(id);
        if (result == null) throw new IllegalArgumentException("unknown encounter " + id);
        return result;
    }

    private Player attacker(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player player) return player;
        return null;
    }

    private String chunkKey(Chunk chunk) {
        return chunk.getWorld().getUID() + ":" + chunk.getX() + ":" + chunk.getZ();
    }

    private void forceChunk(String key) {
        ChunkRef ref = parseChunk(key);
        World world = plugin.getServer().getWorld(ref.world());
        if (world != null) world.getChunkAt(ref.x(), ref.z()).setForceLoaded(true);
    }

    private void releaseChunk(String key) {
        ChunkRef ref = parseChunk(key);
        World world = plugin.getServer().getWorld(ref.world());
        if (world != null) world.getChunkAt(ref.x(), ref.z()).setForceLoaded(false);
    }

    private static ChunkRef parseChunk(String key) {
        String[] parts = key.split(":");
        if (parts.length != 3) throw new IllegalArgumentException("invalid chunk key " + key);
        return new ChunkRef(UUID.fromString(parts[0]),
                Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    private static String root(Throwable failure) {
        Throwable value = failure;
        while (value.getCause() != null) value = value.getCause();
        return value.getMessage();
    }

    private record ChunkRef(UUID world, int x, int z) {
    }
}
