package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.persistence.transaction.DownedEncounterStateRepository;
import com.branz.mmorpg.persistence.transaction.TransactionErrorCode;
import com.branz.mmorpg.social.downed.DownedEncounterEngine;
import com.branz.mmorpg.social.downed.DownedEncounterRuntime;
import com.branz.mmorpg.social.downed.DownedErrorCode;
import com.branz.mmorpg.social.downed.DownedParticipant;
import com.branz.mmorpg.social.downed.DownedTransition;
import com.branz.mmorpg.social.downed.EncounterLifeState;
import com.branz.mmorpg.social.downed.ReviveChannel;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Persist-before-effect party-PvE downed adapter for durable boss attempts. */
final class DownedController implements Listener {
    private static final double MOVEMENT_EPSILON_SQUARED = 1.0e-6;

    private final JavaPlugin plugin;
    private final CombatSessionController combatSessions;
    private final BossEncounterController bossEncounters;
    private final DownedEncounterEngine engine = new DownedEncounterEngine();
    private final DurableDownedEncounterStore durableStore;
    private final Map<AttemptKey, StoredDownedEncounter> active = new HashMap<>();
    private final Map<EncounterId, StoredDownedEncounter> latestByEncounter = new HashMap<>();
    private final Map<AttemptKey, ArrayDeque<PendingMutation>> mutationQueues = new HashMap<>();
    private final Set<AttemptKey> mutationInFlight = new java.util.HashSet<>();
    private final Map<CharacterId, Location> reviveOrigins = new HashMap<>();
    private int tickTaskId = -1;
    private boolean recoveryReady;

    DownedController(
            JavaPlugin plugin,
            CombatSessionController combatSessions,
            BossEncounterController bossEncounters,
            DownedEncounterStateRepository repository,
            String contentVersion) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.combatSessions = Objects.requireNonNull(combatSessions, "combatSessions");
        this.bossEncounters = Objects.requireNonNull(bossEncounters, "bossEncounters");
        durableStore = new DurableDownedEncounterStore(repository, contentVersion);
    }

    void start() {
        tickTaskId =
                plugin.getServer()
                        .getScheduler()
                        .scheduleSyncRepeatingTask(plugin, this::advance, 1L, 1L);
        awaitBossRecovery();
    }

    void shutdown() {
        if (tickTaskId >= 0) {
            plugin.getServer().getScheduler().cancelTask(tickTaskId);
            tickTaskId = -1;
        }
        recoveryReady = false;
        reviveOrigins.clear();
        mutationInFlight.clear();
        mutationQueues.clear();
        latestByEncounter.clear();
        active.clear();
    }

    void onCharacterReady(Player player) {
        if (!recoveryReady) {
            return;
        }
        RuntimeView view = view(player).orElse(null);
        if (view == null) {
            return;
        }
        restoreParticipant(player, view.runtime().participants().get(characterId(player)));
    }

    LethalDamageDisposition interceptLethal(Player player) {
        PartyEncounterContext context = bossEncounters.partyEncounter(player).orElse(null);
        if (context == null) {
            return LethalDamageDisposition.DEATH;
        }
        AttemptKey key = AttemptKey.from(context);
        UUID operationId = UUID.randomUUID();
        long tick = currentTick();
        queueMutation(
                context,
                new PendingMutation(
                        operationId,
                        runtime ->
                                engine.lethalDamage(
                                        runtime, characterId(player), false, operationId, tick),
                        (before, transition, currentContext) ->
                                applyLethal(player, transition, currentContext, false),
                        () -> combatSessions.killPlayer(player),
                        false,
                        tick,
                        "lethal damage"));
        drainMutations(key);
        return LethalDamageDisposition.PENDING_COMMIT;
    }

    boolean protectedFromDamage(Player player) {
        RuntimeView view = view(player).orElse(null);
        if (view == null) {
            return false;
        }
        DownedParticipant participant = view.runtime().participants().get(characterId(player));
        return participant != null && participant.protectedAt(currentTick());
    }

    void observeHostileAction(Player player, String reason) {
        interruptOwnedChannel(player, reason);
        PartyEncounterContext context = bossEncounters.partyEncounter(player).orElse(null);
        if (context == null || !recoveryReady) {
            return;
        }
        UUID operationId = UUID.randomUUID();
        long tick = currentTick();
        queueMutation(
                context,
                new PendingMutation(
                        operationId,
                        runtime ->
                                engine.hostileAction(
                                        runtime, characterId(player), operationId, tick),
                        (before, transition, ignored) -> {
                            if (transition.changed()) {
                                player.sendActionBar(
                                        Component.text(
                                                "REVIVE PROTECTION ENDED", NamedTextColor.YELLOW));
                            }
                        },
                        () -> {},
                        false,
                        tick,
                        "hostile action"));
        drainMutations(AttemptKey.from(context));
    }

    void handleCommand(Player actor, String[] args) {
        if (!recoveryReady) {
            actor.sendMessage(
                    Component.text(
                            "Downed-state recovery is still loading.", NamedTextColor.YELLOW));
            return;
        }
        if (args.length < 2) {
            usage(actor);
            return;
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "status" -> showStatus(actor);
            case "down" -> forceDown(actor, args, false);
            case "execute" -> forceDown(actor, args, true);
            case "revive" -> beginRevive(actor, args);
            case "interrupt" -> interruptOwnedChannel(actor, "LAB_COMMAND");
            case "hostile" -> observeHostileAction(actor, "LAB_COMMAND");
            default -> usage(actor);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getFinalDamage() > 0) {
            interruptOwnedChannel(player, "DAMAGE");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (combatSessions.isDowned(player) && positionChanged(event.getFrom(), event.getTo())) {
            Location locked = event.getFrom().clone();
            locked.setYaw(event.getTo().getYaw());
            locked.setPitch(event.getTo().getPitch());
            event.setTo(locked);
            return;
        }
        Location origin = reviveOrigins.get(characterId(player));
        if (origin != null && positionChanged(origin, event.getTo())) {
            interruptOwnedChannel(player, "MOVEMENT");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        interruptOwnedChannel(event.getPlayer(), "DISCONNECT");
    }

    private void awaitBossRecovery() {
        if (!plugin.isEnabled()) {
            return;
        }
        if (!bossEncounters.recoveryReady()) {
            plugin.getServer().getScheduler().runTaskLater(plugin, this::awaitBossRecovery, 1L);
            return;
        }
        long tick = currentTick();
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<List<StoredDownedEncounter>, TransactionErrorCode> recovered =
                                    durableStore.recoverable(tick);
                            runSyncIfEnabled(() -> completeRecovery(recovered));
                        });
    }

    private void completeRecovery(
            Result<List<StoredDownedEncounter>, TransactionErrorCode> recovered) {
        if (recovered
                instanceof
                Result.Failure<List<StoredDownedEncounter>, TransactionErrorCode> failure) {
            plugin.getLogger()
                    .severe(
                            "Downed-state recovery failed: "
                                    + failure.error().code()
                                    + " "
                                    + failure.detail());
            return;
        }
        List<StoredDownedEncounter> records =
                ((Result.Success<List<StoredDownedEncounter>, TransactionErrorCode>) recovered)
                        .value();
        for (StoredDownedEncounter stored : records) {
            PartyEncounterContext context =
                    bossEncounters.partyEncounter(stored.runtime().encounterId()).orElse(null);
            if (context == null
                    || context.attempt() != stored.record().attempt()
                    || !context.participants().equals(stored.runtime().participants().keySet())) {
                closeStale(stored);
                continue;
            }
            install(AttemptKey.from(context), stored);
        }
        recoveryReady = true;
        plugin.getServer().getOnlinePlayers().forEach(this::onCharacterReady);
        mutationQueues.keySet().forEach(this::drainMutations);
        plugin.getLogger().info("Recovered " + active.size() + " downed encounter(s) from V0010.");
    }

    private void closeStale(StoredDownedEncounter stored) {
        long tick = currentTick();
        UUID operationId = UUID.randomUUID();
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () ->
                                durableStore.replace(
                                        stored,
                                        stored.runtime(),
                                        stored.record().attempt(),
                                        false,
                                        tick,
                                        operationId));
    }

    private void forceDown(Player actor, String[] args, boolean execute) {
        Player target = target(actor, args);
        if (target == null) {
            return;
        }
        PartyEncounterContext context = bossEncounters.partyEncounter(target).orElse(null);
        if (context == null) {
            actor.sendMessage(
                    Component.text(
                            "Target is not in an active multi-player boss encounter.",
                            NamedTextColor.RED));
            return;
        }
        if (!execute) {
            if (!combatSessions.forceLethalDamage(target)) {
                actor.sendMessage(
                        Component.text("Target has no live combat session.", NamedTextColor.RED));
            }
            return;
        }
        if (!combatSessions.holdLethalDamage(target)) {
            actor.sendMessage(Component.text("Target cannot be executed now.", NamedTextColor.RED));
            return;
        }
        UUID operationId = UUID.randomUUID();
        long tick = currentTick();
        queueMutation(
                context,
                new PendingMutation(
                        operationId,
                        runtime ->
                                engine.lethalDamage(
                                        runtime, characterId(target), true, operationId, tick),
                        (before, transition, currentContext) ->
                                applyLethal(target, transition, currentContext, true),
                        () -> combatSessions.killPlayer(target),
                        false,
                        tick,
                        "Execute"));
        drainMutations(AttemptKey.from(context));
    }

    private void beginRevive(Player actor, String[] args) {
        if (args.length < 3) {
            usage(actor);
            return;
        }
        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null || !target.isOnline()) {
            actor.sendMessage(Component.text("Revive target is not online.", NamedTextColor.RED));
            return;
        }
        PartyEncounterContext actorContext = bossEncounters.partyEncounter(actor).orElse(null);
        PartyEncounterContext targetContext = bossEncounters.partyEncounter(target).orElse(null);
        if (actorContext == null
                || targetContext == null
                || !AttemptKey.from(actorContext).equals(AttemptKey.from(targetContext))) {
            actor.sendMessage(
                    Component.text(
                            "Reviver and target must share one active party encounter.",
                            NamedTextColor.RED));
            return;
        }
        UUID operationId = UUID.randomUUID();
        UUID channelId = UUID.randomUUID();
        long tick = currentTick();
        queueMutation(
                actorContext,
                new PendingMutation(
                        operationId,
                        runtime ->
                                engine.beginRevive(
                                        runtime,
                                        characterId(actor),
                                        characterId(target),
                                        channelId,
                                        operationId,
                                        tick),
                        (before, transition, currentContext) -> {
                            reviveOrigins.put(characterId(actor), actor.getLocation().clone());
                            broadcast(
                                    currentContext,
                                    actor.getName() + " is reviving " + target.getName() + " (4s).",
                                    NamedTextColor.AQUA);
                        },
                        () -> {},
                        false,
                        tick,
                        "revive begun"));
        drainMutations(AttemptKey.from(actorContext));
    }

    private void interruptOwnedChannel(Player reviver, String reason) {
        if (!recoveryReady) {
            return;
        }
        CharacterId reviverId = characterId(reviver);
        for (Map.Entry<AttemptKey, StoredDownedEncounter> entry : active.entrySet()) {
            ReviveChannel channel =
                    entry.getValue().runtime().reviveChannelsByTarget().values().stream()
                            .filter(candidate -> candidate.reviverId().equals(reviverId))
                            .findFirst()
                            .orElse(null);
            if (channel == null) {
                continue;
            }
            PartyEncounterContext context =
                    bossEncounters.partyEncounter(entry.getKey().encounterId()).orElse(null);
            if (context == null || context.attempt() != entry.getKey().attempt()) {
                return;
            }
            UUID operationId = UUID.randomUUID();
            long tick = currentTick();
            queueMutation(
                    context,
                    new PendingMutation(
                            operationId,
                            runtime ->
                                    engine.interruptRevive(
                                            runtime, channel.targetId(), operationId),
                            (before, transition, ignored) -> {
                                reviveOrigins.remove(reviverId);
                                reviver.sendMessage(
                                        Component.text(
                                                "Revive interrupted: " + reason,
                                                NamedTextColor.RED));
                            },
                            () -> {},
                            false,
                            tick,
                            "revive interrupted"));
            drainMutations(entry.getKey());
            return;
        }
    }

    private void advance() {
        if (!recoveryReady) {
            return;
        }
        long tick = currentTick();
        for (Map.Entry<AttemptKey, StoredDownedEncounter> entry : List.copyOf(active.entrySet())) {
            AttemptKey key = entry.getKey();
            if (hasPendingMutation(key)) {
                continue;
            }
            DownedEncounterRuntime runtime = entry.getValue().runtime();
            UUID operationId = UUID.randomUUID();
            Result<DownedTransition, DownedErrorCode> preview =
                    engine.advance(runtime, operationId, tick);
            if (preview instanceof Result.Success<DownedTransition, DownedErrorCode> success
                    && success.value().changed()) {
                PartyEncounterContext context =
                        bossEncounters.partyEncounter(key.encounterId()).orElse(null);
                if (context != null && context.attempt() == key.attempt()) {
                    queueMutation(
                            context,
                            new PendingMutation(
                                    operationId,
                                    current -> engine.advance(current, operationId, tick),
                                    this::applyClockEffects,
                                    () -> {},
                                    false,
                                    tick,
                                    "downed clock advanced"));
                    drainMutations(key);
                }
            } else if (tick % 20 == 0 && hasTimedState(runtime)) {
                queueCheckpoint(key, runtime, tick);
            }
        }
        if (tick % 20 == 0) {
            showDownedCountdowns(tick);
        }
    }

    private void queueCheckpoint(AttemptKey key, DownedEncounterRuntime runtime, long tick) {
        PartyEncounterContext context =
                bossEncounters.partyEncounter(key.encounterId()).orElse(null);
        if (context == null || context.attempt() != key.attempt() || hasPendingMutation(key)) {
            return;
        }
        queueMutation(
                context,
                new PendingMutation(
                        UUID.randomUUID(),
                        ignored ->
                                Result.success(
                                        new DownedTransition(
                                                runtime, Set.of(), Set.of(), Map.of(), false)),
                        (before, transition, ignored) -> {},
                        () -> {},
                        true,
                        tick,
                        "timer checkpoint at " + tick));
        drainMutations(key);
    }

    private void queueMutation(PartyEncounterContext context, PendingMutation pending) {
        AttemptKey key = AttemptKey.from(context);
        mutationQueues.computeIfAbsent(key, ignored -> new ArrayDeque<>()).addLast(pending);
    }

    private void drainMutations(AttemptKey key) {
        if (!recoveryReady || !mutationInFlight.add(key)) {
            return;
        }
        ArrayDeque<PendingMutation> queue = mutationQueues.get(key);
        if (queue == null || queue.isEmpty()) {
            mutationInFlight.remove(key);
            return;
        }
        PartyEncounterContext context =
                bossEncounters.partyEncounter(key.encounterId()).orElse(null);
        PendingMutation pending = queue.getFirst();
        if (context == null || context.attempt() != key.attempt()) {
            mutationInFlight.remove(key);
            queue.removeFirst();
            pending.discardedEffect().run();
            drainMutations(key);
            return;
        }
        StoredDownedEncounter currentStored = active.get(key);
        DownedEncounterRuntime current =
                currentStored == null ? newRuntime(context) : currentStored.runtime();
        Result<DownedTransition, DownedErrorCode> result = pending.mutation().apply(current);
        if (result instanceof Result.Failure<DownedTransition, DownedErrorCode> failure) {
            mutationInFlight.remove(key);
            queue.removeFirst();
            pending.discardedEffect().run();
            firstOnline(context)
                    .ifPresent(
                            player ->
                                    player.sendMessage(
                                            Component.text(
                                                    failure.error().code()
                                                            + ": "
                                                            + failure.detail(),
                                                    NamedTextColor.RED)));
            drainMutations(key);
            return;
        }
        DownedTransition transition =
                ((Result.Success<DownedTransition, DownedErrorCode>) result).value();
        if (!transition.changed() && !pending.forcePersist()) {
            mutationInFlight.remove(key);
            queue.removeFirst();
            drainMutations(key);
            return;
        }
        StoredDownedEncounter expected = latestByEncounter.get(key.encounterId());
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<StoredDownedEncounter, TransactionErrorCode> committed =
                                    expected == null
                                            ? durableStore.create(
                                                    transition.runtime(),
                                                    key.attempt(),
                                                    true,
                                                    pending.recordedAtTick(),
                                                    pending.operationId())
                                            : durableStore.replace(
                                                    expected,
                                                    transition.runtime(),
                                                    key.attempt(),
                                                    true,
                                                    pending.recordedAtTick(),
                                                    pending.operationId());
                            runSyncIfEnabled(
                                    () ->
                                            completeMutation(
                                                    key,
                                                    context,
                                                    pending,
                                                    current,
                                                    transition,
                                                    committed));
                        });
    }

    private void completeMutation(
            AttemptKey key,
            PartyEncounterContext context,
            PendingMutation pending,
            DownedEncounterRuntime before,
            DownedTransition transition,
            Result<StoredDownedEncounter, TransactionErrorCode> committed) {
        mutationInFlight.remove(key);
        ArrayDeque<PendingMutation> queue = mutationQueues.get(key);
        if (queue == null || queue.isEmpty() || queue.getFirst() != pending) {
            return;
        }
        if (committed
                instanceof Result.Failure<StoredDownedEncounter, TransactionErrorCode> failure) {
            broadcast(
                    context,
                    "Downed-state commit retry pending: "
                            + failure.error().code()
                            + " "
                            + failure.detail(),
                    NamedTextColor.RED);
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> drainMutations(key), 20L);
            return;
        }
        queue.removeFirst();
        StoredDownedEncounter stored =
                ((Result.Success<StoredDownedEncounter, TransactionErrorCode>) committed).value();
        install(key, stored);
        clearFinishedChannels(before, stored.runtime());
        pending.effect().apply(before, transition, context);
        drainMutations(key);
    }

    private void install(AttemptKey key, StoredDownedEncounter stored) {
        active.keySet()
                .removeIf(
                        candidate ->
                                candidate.encounterId().equals(key.encounterId())
                                        && candidate.attempt() != key.attempt());
        active.put(key, stored);
        latestByEncounter.put(key.encounterId(), stored);
    }

    private void applyLethal(
            Player player,
            DownedTransition transition,
            PartyEncounterContext context,
            boolean execute) {
        CharacterId playerId = characterId(player);
        if (transition.newlyDowned().contains(playerId)) {
            broadcast(
                    context,
                    player.getName() + " is DOWNED for 15s. An active ally can revive them.",
                    NamedTextColor.YELLOW);
        } else if (transition.newlyDead().contains(playerId)) {
            broadcast(
                    context,
                    player.getName()
                            + (execute
                                    ? " was EXECUTED and cannot be revived."
                                    : " was defeated; no revive remains."),
                    NamedTextColor.RED);
            combatSessions.killPlayer(player);
        }
    }

    private void applyClockEffects(
            DownedEncounterRuntime before,
            DownedTransition transition,
            PartyEncounterContext context) {
        transition.newlyDead().forEach(this::killOnline);
        transition.revivedHealthRatios().forEach(this::reviveOnline);
    }

    private void killOnline(CharacterId characterId) {
        Player player = plugin.getServer().getPlayer(characterId.value());
        if (player != null && player.isOnline()) {
            player.sendMessage(Component.text("Downed timer expired.", NamedTextColor.RED));
            combatSessions.killPlayer(player);
        }
    }

    private void reviveOnline(CharacterId characterId, double healthRatio) {
        Player player = plugin.getServer().getPlayer(characterId.value());
        if (player != null && player.isOnline()) {
            combatSessions.reviveFromDowned(player, healthRatio);
            player.sendMessage(
                    Component.text(
                            "REVIVED at 25% health with 3s protection.", NamedTextColor.GREEN));
        }
    }

    private void restoreParticipant(Player player, DownedParticipant participant) {
        if (participant == null) {
            return;
        }
        if (participant.lifeState() == EncounterLifeState.DOWNED) {
            combatSessions.holdLethalDamage(player);
            player.sendMessage(
                    Component.text("Recovered durable DOWNED state.", NamedTextColor.YELLOW));
        } else if (participant.lifeState() == EncounterLifeState.DEAD) {
            combatSessions.killPlayer(player);
        } else if (participant.reviveConsumed() && participant.protectedAt(currentTick())) {
            combatSessions.restoreRevived(player, DownedEncounterEngine.REVIVED_HEALTH_RATIO);
        }
    }

    private void showDownedCountdowns(long tick) {
        active.values()
                .forEach(
                        stored ->
                                stored.runtime().participants().values().stream()
                                        .filter(
                                                participant ->
                                                        participant.lifeState()
                                                                == EncounterLifeState.DOWNED)
                                        .forEach(
                                                participant -> {
                                                    Player player =
                                                            plugin.getServer()
                                                                    .getPlayer(
                                                                            participant
                                                                                    .characterId()
                                                                                    .value());
                                                    if (player != null && player.isOnline()) {
                                                        long seconds =
                                                                Math.max(
                                                                        0,
                                                                        (participant
                                                                                                .downedDeadlineTick()
                                                                                        - tick
                                                                                        + 19)
                                                                                / 20);
                                                        player.sendActionBar(
                                                                Component.text(
                                                                        "DOWNED " + seconds + "s",
                                                                        NamedTextColor.RED));
                                                    }
                                                }));
    }

    private void showStatus(Player actor) {
        RuntimeView view = view(actor).orElse(null);
        if (view == null) {
            actor.sendMessage(
                    Component.text("No active party downed runtime.", NamedTextColor.YELLOW));
            return;
        }
        DownedParticipant participant = view.runtime().participants().get(characterId(actor));
        actor.sendMessage(
                Component.text(
                        "Downed runtime | encounter="
                                + view.key().encounterId().value()
                                + " | attempt="
                                + view.key().attempt()
                                + " | version="
                                + view.stored().record().version()
                                + " | state="
                                + participant.lifeState()
                                + " | reviveConsumed="
                                + participant.reviveConsumed()
                                + " | channels="
                                + view.runtime().reviveChannelsByTarget().size()
                                + " | queued="
                                + mutationQueues
                                        .getOrDefault(view.key(), new ArrayDeque<>())
                                        .size(),
                        NamedTextColor.AQUA));
    }

    private Optional<RuntimeView> view(Player player) {
        PartyEncounterContext context = bossEncounters.partyEncounter(player).orElse(null);
        if (context == null) {
            return Optional.empty();
        }
        AttemptKey key = AttemptKey.from(context);
        StoredDownedEncounter stored = active.get(key);
        return stored == null
                ? Optional.empty()
                : Optional.of(new RuntimeView(key, stored.runtime(), stored));
    }

    private DownedEncounterRuntime newRuntime(PartyEncounterContext context) {
        Result<DownedEncounterRuntime, DownedErrorCode> started =
                engine.start(context.encounterId(), context.participants());
        return ((Result.Success<DownedEncounterRuntime, DownedErrorCode>) started).value();
    }

    private boolean hasPendingMutation(AttemptKey key) {
        ArrayDeque<PendingMutation> queue = mutationQueues.get(key);
        return mutationInFlight.contains(key) || queue != null && !queue.isEmpty();
    }

    private static boolean hasTimedState(DownedEncounterRuntime runtime) {
        return !runtime.reviveChannelsByTarget().isEmpty()
                || runtime.participants().values().stream()
                        .anyMatch(
                                participant ->
                                        participant.lifeState() == EncounterLifeState.DOWNED
                                                || participant.protectionUntilTick() >= 0);
    }

    private void clearFinishedChannels(
            DownedEncounterRuntime before, DownedEncounterRuntime after) {
        before.reviveChannelsByTarget().values().stream()
                .filter(channel -> !after.reviveChannelsByTarget().containsKey(channel.targetId()))
                .forEach(channel -> reviveOrigins.remove(channel.reviverId()));
    }

    private void broadcast(PartyEncounterContext context, String message, NamedTextColor color) {
        context.participants().stream()
                .map(characterId -> plugin.getServer().getPlayer(characterId.value()))
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .forEach(player -> player.sendMessage(Component.text(message, color)));
    }

    private Optional<Player> firstOnline(PartyEncounterContext context) {
        return context.participants().stream()
                .map(characterId -> plugin.getServer().getPlayer(characterId.value()))
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .findFirst();
    }

    private Player target(Player actor, String[] args) {
        if (args.length < 3) {
            return actor;
        }
        Player target = plugin.getServer().getPlayerExact(args[2]);
        if (target == null || !target.isOnline()) {
            actor.sendMessage(Component.text("Target is not online.", NamedTextColor.RED));
            return null;
        }
        return target;
    }

    private void runSyncIfEnabled(Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        try {
            plugin.getServer().getScheduler().runTask(plugin, task);
        } catch (org.bukkit.plugin.IllegalPluginAccessException exception) {
            if (plugin.isEnabled()) {
                throw exception;
            }
        }
    }

    private long currentTick() {
        return plugin.getServer().getCurrentTick();
    }

    private static boolean positionChanged(Location from, Location to) {
        return from.getWorld() != to.getWorld()
                || from.toVector().distanceSquared(to.toVector()) > MOVEMENT_EPSILON_SQUARED;
    }

    private static CharacterId characterId(Player player) {
        return new CharacterId(player.getUniqueId());
    }

    private static void usage(Player player) {
        player.sendMessage(
                "Usage: /mmo downed <status|down [player]|execute [player]|revive <player>|interrupt|hostile>");
    }

    private record AttemptKey(EncounterId encounterId, int attempt) {
        private AttemptKey {
            Objects.requireNonNull(encounterId, "encounterId");
            if (attempt < 1) {
                throw new IllegalArgumentException("attempt must be positive");
            }
        }

        private static AttemptKey from(PartyEncounterContext context) {
            return new AttemptKey(context.encounterId(), context.attempt());
        }
    }

    private record RuntimeView(
            AttemptKey key, DownedEncounterRuntime runtime, StoredDownedEncounter stored) {
        private RuntimeView {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(runtime, "runtime");
            Objects.requireNonNull(stored, "stored");
        }
    }

    private record PendingMutation(
            UUID operationId,
            Function<DownedEncounterRuntime, Result<DownedTransition, DownedErrorCode>> mutation,
            MutationEffect effect,
            Runnable discardedEffect,
            boolean forcePersist,
            long recordedAtTick,
            String description) {
        private PendingMutation {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(mutation, "mutation");
            Objects.requireNonNull(effect, "effect");
            Objects.requireNonNull(discardedEffect, "discardedEffect");
            Objects.requireNonNull(description, "description");
            if (recordedAtTick < 0) {
                throw new IllegalArgumentException("recordedAtTick must not be negative");
            }
        }
    }

    @FunctionalInterface
    private interface MutationEffect {
        void apply(
                DownedEncounterRuntime before,
                DownedTransition transition,
                PartyEncounterContext context);
    }
}
