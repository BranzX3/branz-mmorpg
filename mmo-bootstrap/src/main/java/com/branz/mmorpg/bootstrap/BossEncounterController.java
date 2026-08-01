package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.EncounterId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.combat.resource.BossFlaskCheckpointEngine;
import com.branz.mmorpg.combat.resource.FlaskCheckpointErrorCode;
import com.branz.mmorpg.combat.resource.FlaskState;
import com.branz.mmorpg.combat.resource.PreparedFlaskSnapshot;
import com.branz.mmorpg.persistence.transaction.BossEncounterStateRecord;
import com.branz.mmorpg.persistence.transaction.BossEncounterStateRepository;
import com.branz.mmorpg.persistence.transaction.TransactionErrorCode;
import com.branz.mmorpg.worldloop.encounter.BossEncounterEngine;
import com.branz.mmorpg.worldloop.encounter.BossEncounterErrorCode;
import com.branz.mmorpg.worldloop.encounter.BossEncounterPhase;
import com.branz.mmorpg.worldloop.encounter.BossEncounterRuntime;
import com.branz.mmorpg.worldloop.encounter.BossEncounterTransition;
import com.branz.mmorpg.worldloop.encounter.EncounterParticipant;
import com.branz.mmorpg.worldloop.encounter.EncounterParticipantStatus;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Environment-gated live boss encounter lab and confirmed-wipe Flask bridge. */
final class BossEncounterController implements Listener {
    private static final DefinitionId TRAINING_BOSS =
            DefinitionId.of("encounter.boss.training_golem");

    private final JavaPlugin plugin;
    private final CharacterSessionController characterSessions;
    private final FlaskHotbarController flaskHotbar;
    private final String contentVersion;
    private final DurableBossEncounterStore durableStore;
    private final BossEncounterEngine encounters = new BossEncounterEngine();
    private final BossFlaskCheckpointEngine flaskCheckpoints = new BossFlaskCheckpointEngine();
    private final Map<EncounterId, BossEncounterRuntime> active = new HashMap<>();
    private final Map<EncounterId, BossEncounterStateRecord> durableRecords = new HashMap<>();
    private final Map<EncounterId, ArrayDeque<PendingMutation>> mutationQueues = new HashMap<>();
    private final Set<EncounterId> mutationInFlight = new HashSet<>();
    private final Map<CharacterId, EncounterId> encounterByParticipant = new HashMap<>();
    private final Map<CharacterId, EncounterId> recentEncounterByParticipant = new HashMap<>();
    private final Map<EncounterId, ResetProgress> resets = new HashMap<>();
    private final Set<EncounterId> preparing = new HashSet<>();
    private int graceTaskId = -1;
    private boolean recoveryReady;

    BossEncounterController(
            JavaPlugin plugin,
            CharacterSessionController characterSessions,
            FlaskHotbarController flaskHotbar,
            BossEncounterStateRepository encounterStates,
            String contentVersion) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characterSessions = Objects.requireNonNull(characterSessions, "characterSessions");
        this.flaskHotbar = Objects.requireNonNull(flaskHotbar, "flaskHotbar");
        this.contentVersion = Objects.requireNonNull(contentVersion, "contentVersion");
        durableStore = new DurableBossEncounterStore(encounterStates, contentVersion);
    }

    void start() {
        graceTaskId =
                plugin.getServer()
                        .getScheduler()
                        .scheduleSyncRepeatingTask(plugin, this::advanceGrace, 20L, 20L);
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<List<StoredBossEncounter>, TransactionErrorCode> recovered =
                                    durableStore.recoverable();
                            runSyncIfEnabled(() -> completeRecovery(recovered));
                        });
    }

    void shutdown() {
        if (graceTaskId >= 0) {
            plugin.getServer().getScheduler().cancelTask(graceTaskId);
            graceTaskId = -1;
        }
        preparing.clear();
        mutationInFlight.clear();
        mutationQueues.clear();
        durableRecords.clear();
        resets.clear();
        encounterByParticipant.clear();
        recentEncounterByParticipant.clear();
        active.clear();
        recoveryReady = false;
    }

    void handleCommand(Player actor, String[] args) {
        if (!recoveryReady) {
            actor.sendMessage(
                    Component.text("Encounter recovery is still loading.", NamedTextColor.YELLOW));
            return;
        }
        if (args.length < 2) {
            usage(actor);
            return;
        }
        switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
            case "start" -> startEncounter(actor, args);
            case "status" -> showStatus(actor);
            case "defeat" -> changeAvailability(actor, args, AvailabilityCommand.DEFEAT);
            case "boundary" -> changeAvailability(actor, args, AvailabilityCommand.BOUNDARY);
            case "rejoin" -> rejoin(actor);
            case "victory" -> confirmVictory(actor);
            case "rewards" -> reconcileRewards(actor, args);
            default -> usage(actor);
        }
    }

    void onCharacterReady(Player player) {
        CharacterId characterId = characterId(player);
        EncounterId encounterId = encounterByParticipant.get(characterId);
        BossEncounterRuntime runtime = encounterId == null ? null : active.get(encounterId);
        if (runtime == null) {
            return;
        }
        if (runtime.phase() == BossEncounterPhase.RESETTING) {
            attemptRestore(encounterId, characterId);
            return;
        }
        EncounterParticipant participant = runtime.participants().get(characterId);
        if (runtime.phase() == BossEncounterPhase.ACTIVE
                && participant != null
                && (participant.status() == EncounterParticipantStatus.DISCONNECTED_GRACE
                        || participant.status() == EncounterParticipantStatus.OUTSIDE_GRACE)) {
            UUID operationId = UUID.randomUUID();
            mutate(
                    runtime.encounterId(),
                    operationId,
                    current ->
                            encounters.reconnect(
                                    current,
                                    characterId,
                                    operationId,
                                    plugin.getServer().getCurrentTick()),
                    "rejoined the encounter");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        CharacterId characterId = characterId(event.getPlayer());
        BossEncounterRuntime runtime = runtimeFor(characterId);
        if (runtime == null || runtime.phase() != BossEncounterPhase.ACTIVE) {
            return;
        }
        UUID operationId = UUID.randomUUID();
        mutate(
                runtime.encounterId(),
                operationId,
                current ->
                        encounters.disconnect(
                                current,
                                characterId,
                                operationId,
                                plugin.getServer().getCurrentTick()),
                "entered reconnect grace");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        CharacterId characterId = characterId(player);
        BossEncounterRuntime runtime = runtimeFor(characterId);
        if (runtime == null || runtime.phase() != BossEncounterPhase.ACTIVE) {
            return;
        }
        UUID operationId = UUID.randomUUID();
        mutate(
                runtime.encounterId(),
                operationId,
                current ->
                        encounters.defeat(
                                current,
                                characterId,
                                operationId,
                                plugin.getServer().getCurrentTick()),
                "was defeated");
    }

    private void startEncounter(Player actor, String[] args) {
        if (args.length < 3) {
            usage(actor);
            return;
        }
        UUID value;
        try {
            value = UUID.fromString(args[2]);
        } catch (IllegalArgumentException exception) {
            actor.sendMessage(Component.text("Encounter ID must be a UUID.", NamedTextColor.RED));
            return;
        }
        EncounterId encounterId = new EncounterId(value);
        if (active.containsKey(encounterId) || !preparing.add(encounterId)) {
            actor.sendMessage(
                    Component.text(
                            "That encounter is already active or preparing.", NamedTextColor.RED));
            return;
        }
        LinkedHashSet<Player> players = new LinkedHashSet<>();
        if (args.length == 3) {
            players.add(actor);
        } else {
            for (int index = 3; index < args.length; index++) {
                Player participant = plugin.getServer().getPlayerExact(args[index]);
                if (participant == null || !participant.isOnline()) {
                    preparing.remove(encounterId);
                    actor.sendMessage(
                            Component.text(
                                    "Participant is not online: " + args[index],
                                    NamedTextColor.RED));
                    return;
                }
                players.add(participant);
            }
        }
        if (players.isEmpty() || players.size() > BossEncounterEngine.MAX_PARTICIPANTS) {
            preparing.remove(encounterId);
            actor.sendMessage(
                    Component.text("Encounter requires one to five players.", NamedTextColor.RED));
            return;
        }
        for (Player participant : players) {
            CharacterId characterId = characterId(participant);
            if (!characterSessions.ready(participant)
                    || encounterByParticipant.containsKey(characterId)) {
                preparing.remove(encounterId);
                actor.sendMessage(
                        Component.text(
                                participant.getName()
                                        + " is not ready or is already in an encounter.",
                                NamedTextColor.RED));
                return;
            }
        }
        List<Player> ordered = new ArrayList<>(players);
        actor.sendMessage(
                Component.text(
                        "Preparing boss Flask checkpoint for " + ordered.size() + " player(s)...",
                        NamedTextColor.YELLOW));
        captureNext(actor, encounterId, ordered, 0);
    }

    private void captureNext(
            Player actor, EncounterId encounterId, List<Player> participants, int index) {
        if (index == participants.size()) {
            completeStart(actor, encounterId, participants);
            return;
        }
        Player participant = participants.get(index);
        LoadedCharacterSession session = characterSessions.active(participant).orElse(null);
        if (session == null || !characterSessions.ready(participant)) {
            failPreparation(
                    actor, encounterId, participant.getName() + " session became unavailable");
            return;
        }
        PersistentExpeditionState current = session.snapshot().expeditionState();
        PreparedFlaskSnapshot existing = current.preparedFlaskSnapshot().orElse(null);
        if (existing != null && existing.checkpointInstanceId().equals(encounterId.value())) {
            captureNext(actor, encounterId, participants, index + 1);
            return;
        }
        PreparedFlaskSnapshot prepared =
                flaskCheckpoints.capture(encounterId.value(), current.flaskState());
        PersistentExpeditionState desired =
                new PersistentExpeditionState(
                        current.flaskState(),
                        current.consumableEffects(),
                        current.ailments(),
                        java.util.Optional.of(prepared));
        UUID operationId = operation(encounterId, "capture", characterId(participant), 1);
        characterSessions.commitExpeditionState(
                participant,
                desired,
                operationId,
                contentVersion,
                result -> {
                    if (result instanceof Result.Failure<?, ?> failure) {
                        failPreparation(
                                actor,
                                encounterId,
                                participant.getName() + " checkpoint failed: " + failure.detail());
                        return;
                    }
                    captureNext(actor, encounterId, participants, index + 1);
                });
    }

    private void completeStart(Player actor, EncounterId encounterId, List<Player> participants) {
        List<CharacterId> participantIds =
                participants.stream().map(BossEncounterController::characterId).toList();
        boolean occupied = participantIds.stream().anyMatch(encounterByParticipant::containsKey);
        Result<BossEncounterRuntime, BossEncounterErrorCode> started =
                occupied
                        ? Result.failure(
                                BossEncounterErrorCode.INVALID_PARTICIPANTS,
                                "A participant entered another encounter during preparation.")
                        : encounters.start(
                                encounterId,
                                TRAINING_BOSS,
                                encounterId.value(),
                                participantIds,
                                plugin.getServer().getCurrentTick());
        if (started
                instanceof Result.Failure<BossEncounterRuntime, BossEncounterErrorCode> failure) {
            preparing.remove(encounterId);
            actor.sendMessage(
                    Component.text(
                            "Encounter start failed: " + failure.detail(), NamedTextColor.RED));
            return;
        }
        BossEncounterRuntime runtime =
                ((Result.Success<BossEncounterRuntime, BossEncounterErrorCode>) started).value();
        UUID startOperation = operation(encounterId, "state-start", null, 1);
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<StoredBossEncounter, TransactionErrorCode> committed =
                                    durableStore.create(runtime, startOperation);
                            runSyncIfEnabled(
                                    () -> {
                                        preparing.remove(encounterId);
                                        if (committed
                                                instanceof
                                                Result.Failure<
                                                                StoredBossEncounter,
                                                                TransactionErrorCode>
                                                        failure) {
                                            actor.sendMessage(
                                                    Component.text(
                                                            "Encounter state commit failed: "
                                                                    + failure.error().code()
                                                                    + " "
                                                                    + failure.detail(),
                                                            NamedTextColor.RED));
                                            return;
                                        }
                                        StoredBossEncounter stored =
                                                ((Result.Success<
                                                                        StoredBossEncounter,
                                                                        TransactionErrorCode>)
                                                                committed)
                                                        .value();
                                        install(stored);
                                        broadcast(
                                                stored.runtime(),
                                                Component.text(
                                                        "Boss encounter ACTIVE | id="
                                                                + encounterId.value()
                                                                + " | attempt=1 | Flask checkpoint captured and state committed",
                                                        NamedTextColor.GREEN));
                                    });
                        });
    }

    private void failPreparation(Player actor, EncounterId encounterId, String detail) {
        preparing.remove(encounterId);
        actor.sendMessage(
                Component.text(
                        "Encounter preparation stopped: "
                                + detail
                                + ". Retry the same encounter UUID; completed captures are reused.",
                        NamedTextColor.RED));
    }

    private void changeAvailability(Player actor, String[] args, AvailabilityCommand command) {
        Player target = actor;
        if (args.length >= 3) {
            target = plugin.getServer().getPlayerExact(args[2]);
            if (target == null) {
                actor.sendMessage(Component.text("Player is not online.", NamedTextColor.RED));
                return;
            }
        }
        CharacterId targetId = characterId(target);
        BossEncounterRuntime runtime = runtimeFor(targetId);
        if (runtime == null) {
            actor.sendMessage(
                    Component.text("Player is not in an active encounter.", NamedTextColor.RED));
            return;
        }
        UUID operationId = UUID.randomUUID();
        mutate(
                runtime.encounterId(),
                operationId,
                current ->
                        command == AvailabilityCommand.DEFEAT
                                ? encounters.defeat(
                                        current,
                                        targetId,
                                        operationId,
                                        plugin.getServer().getCurrentTick())
                                : encounters.leaveBoundary(
                                        current,
                                        targetId,
                                        operationId,
                                        plugin.getServer().getCurrentTick()),
                command == AvailabilityCommand.DEFEAT
                        ? target.getName() + " was defeated"
                        : target.getName() + " entered boundary grace");
    }

    private void confirmVictory(Player actor) {
        BossEncounterRuntime runtime = runtimeFor(characterId(actor));
        if (runtime == null) {
            actor.sendMessage(Component.text("No active encounter.", NamedTextColor.RED));
            return;
        }
        UUID operationId = UUID.randomUUID();
        mutate(
                runtime.encounterId(),
                operationId,
                current -> encounters.confirmVictory(current, operationId),
                "victory frozen; reward reconciliation pending");
    }

    private void rejoin(Player actor) {
        CharacterId actorId = characterId(actor);
        BossEncounterRuntime runtime = runtimeFor(actorId);
        if (runtime == null) {
            actor.sendMessage(Component.text("No active encounter.", NamedTextColor.RED));
            return;
        }
        UUID operationId = UUID.randomUUID();
        mutate(
                runtime.encounterId(),
                operationId,
                current ->
                        encounters.reconnect(
                                current, actorId, operationId, plugin.getServer().getCurrentTick()),
                actor.getName() + " rejoined the encounter");
    }

    private void reconcileRewards(Player actor, String[] args) {
        BossEncounterRuntime runtime = runtimeFor(characterId(actor));
        if (runtime == null) {
            actor.sendMessage(Component.text("No active encounter.", NamedTextColor.RED));
            return;
        }
        UUID grantId;
        try {
            grantId = args.length >= 3 ? UUID.fromString(args[2]) : UUID.randomUUID();
        } catch (IllegalArgumentException exception) {
            actor.sendMessage(
                    Component.text("Reward grant ID must be a UUID.", NamedTextColor.RED));
            return;
        }
        UUID operationId = UUID.randomUUID();
        mutate(
                runtime.encounterId(),
                operationId,
                current -> encounters.reconcileRewards(current, operationId, grantId),
                "empty lab reward grant committed=" + grantId);
    }

    private void showStatus(Player actor) {
        CharacterId actorId = characterId(actor);
        EncounterId encounterId = encounterByParticipant.get(actorId);
        if (encounterId == null) {
            encounterId = recentEncounterByParticipant.get(actorId);
        }
        BossEncounterRuntime runtime = encounterId == null ? null : active.get(encounterId);
        if (runtime == null) {
            actor.sendMessage(Component.text("No encounter state.", NamedTextColor.GRAY));
            return;
        }
        actor.sendMessage(
                Component.text(
                        "Encounter "
                                + runtime.encounterId().value()
                                + " | phase="
                                + runtime.phase()
                                + " | attempt="
                                + runtime.attempt()
                                + " | checkpoint="
                                + runtime.checkpointInstanceId(),
                        NamedTextColor.GOLD));
        runtime.participants().values().stream()
                .sorted(
                        java.util.Comparator.comparing(
                                participant -> participant.characterId().value()))
                .forEach(
                        participant ->
                                actor.sendMessage(
                                        Component.text(
                                                participant.characterId().value()
                                                        + " | "
                                                        + participant.status()
                                                        + (participant.graceDeadlineTick()
                                                                        == EncounterParticipant
                                                                                .NO_GRACE_DEADLINE
                                                                ? ""
                                                                : " | deadline="
                                                                        + participant
                                                                                .graceDeadlineTick()),
                                                NamedTextColor.AQUA)));
    }

    private void advanceGrace() {
        long tick = plugin.getServer().getCurrentTick();
        for (BossEncounterRuntime runtime : List.copyOf(active.values())) {
            if (runtime.phase() != BossEncounterPhase.ACTIVE) {
                continue;
            }
            UUID operationId = UUID.randomUUID();
            mutate(
                    runtime.encounterId(),
                    operationId,
                    current -> encounters.advanceGrace(current, operationId, tick),
                    "participant grace expired");
        }
    }

    private void mutate(
            EncounterId encounterId,
            UUID operationId,
            Function<BossEncounterRuntime, Result<BossEncounterTransition, BossEncounterErrorCode>>
                    mutation,
            String description) {
        mutate(encounterId, operationId, mutation, description, false);
    }

    private void mutate(
            EncounterId encounterId,
            UUID operationId,
            Function<BossEncounterRuntime, Result<BossEncounterTransition, BossEncounterErrorCode>>
                    mutation,
            String description,
            boolean resumeOnlineParticipants) {
        mutationQueues
                .computeIfAbsent(encounterId, ignored -> new ArrayDeque<>())
                .addLast(
                        new PendingMutation(
                                operationId, mutation, description, resumeOnlineParticipants));
        drainMutations(encounterId);
    }

    private void drainMutations(EncounterId encounterId) {
        ArrayDeque<PendingMutation> queue = mutationQueues.get(encounterId);
        if (queue == null || queue.isEmpty() || !mutationInFlight.add(encounterId)) {
            return;
        }
        BossEncounterRuntime current = active.get(encounterId);
        BossEncounterStateRecord record = durableRecords.get(encounterId);
        PendingMutation pending = queue.getFirst();
        if (current == null || record == null) {
            mutationInFlight.remove(encounterId);
            queue.removeFirst();
            drainMutations(encounterId);
            return;
        }
        Result<BossEncounterTransition, BossEncounterErrorCode> result =
                pending.mutation().apply(current);
        if (result
                instanceof
                Result.Failure<BossEncounterTransition, BossEncounterErrorCode> failure) {
            mutationInFlight.remove(encounterId);
            queue.removeFirst();
            Player participant = firstOnline(current);
            if (participant != null) {
                participant.sendMessage(
                        Component.text(
                                failure.error().code() + ": " + failure.detail(),
                                NamedTextColor.RED));
            }
            drainMutations(encounterId);
            return;
        }
        BossEncounterTransition transition =
                ((Result.Success<BossEncounterTransition, BossEncounterErrorCode>) result).value();
        if (!transition.changed()) {
            mutationInFlight.remove(encounterId);
            queue.removeFirst();
            drainMutations(encounterId);
            return;
        }
        StoredBossEncounter expected = new StoredBossEncounter(current, record);
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<StoredBossEncounter, TransactionErrorCode> committed =
                                    durableStore.replace(
                                            expected, transition.runtime(), pending.operationId());
                            runSyncIfEnabled(
                                    () ->
                                            completeMutation(
                                                    encounterId, pending, transition, committed));
                        });
    }

    private void completeMutation(
            EncounterId encounterId,
            PendingMutation pending,
            BossEncounterTransition transition,
            Result<StoredBossEncounter, TransactionErrorCode> committed) {
        mutationInFlight.remove(encounterId);
        ArrayDeque<PendingMutation> queue = mutationQueues.get(encounterId);
        if (queue == null || queue.isEmpty() || queue.getFirst() != pending) {
            return;
        }
        if (committed
                instanceof Result.Failure<StoredBossEncounter, TransactionErrorCode> failure) {
            broadcast(
                    transition.runtime(),
                    Component.text(
                            "Encounter state retry pending: "
                                    + failure.error().code()
                                    + " "
                                    + failure.detail(),
                            NamedTextColor.RED));
            plugin.getServer()
                    .getScheduler()
                    .runTaskLater(plugin, () -> drainMutations(encounterId), 20L);
            return;
        }
        queue.removeFirst();
        StoredBossEncounter stored =
                ((Result.Success<StoredBossEncounter, TransactionErrorCode>) committed).value();
        active.put(encounterId, stored.runtime());
        durableRecords.put(encounterId, stored.record());
        broadcast(
                stored.runtime(),
                Component.text(
                        "Encounter "
                                + stored.runtime().phase()
                                + " | attempt="
                                + stored.runtime().attempt()
                                + " | "
                                + pending.description(),
                        NamedTextColor.YELLOW));
        afterPersisted(transition);
        if (pending.resumeOnlineParticipants()) {
            stored.runtime().participants().keySet().stream()
                    .map(characterId -> plugin.getServer().getPlayer(characterId.value()))
                    .filter(Objects::nonNull)
                    .filter(Player::isOnline)
                    .filter(characterSessions::ready)
                    .forEach(this::onCharacterReady);
        }
        drainMutations(encounterId);
    }

    private void afterPersisted(BossEncounterTransition transition) {
        BossEncounterRuntime runtime = transition.runtime();
        if (runtime.phase() == BossEncounterPhase.WIPE_PENDING) {
            beginReset(runtime);
        } else if (runtime.phase() == BossEncounterPhase.RESETTING
                && !transition.flaskRestoreParticipants().isEmpty()) {
            startResetEffects(runtime, transition.flaskRestoreParticipants());
        } else if (runtime.phase() == BossEncounterPhase.ACTIVE
                && resets.containsKey(runtime.encounterId())) {
            finishResetEffects(runtime);
        } else if (runtime.phase() == BossEncounterPhase.COMPLETED) {
            runtime.participants().keySet().forEach(encounterByParticipant::remove);
        }
    }

    private void beginReset(BossEncounterRuntime wiped) {
        UUID resetOperation = operation(wiped.encounterId(), "reset", null, wiped.attempt());
        mutate(
                wiped.encounterId(),
                resetOperation,
                current -> encounters.beginReset(current, resetOperation),
                "party wipe committed; Flask restore beginning");
    }

    private void startResetEffects(
            BossEncounterRuntime reset, Set<CharacterId> restoreParticipants) {
        ResetProgress progress =
                new ResetProgress(
                        reset.activeResetOperationId().orElseThrow(),
                        operation(reset.encounterId(), "reset-complete", null, reset.attempt()),
                        new HashSet<>(restoreParticipants),
                        new HashSet<>());
        resets.put(reset.encounterId(), progress);
        broadcast(
                reset,
                Component.text(
                        "Party wipe confirmed; restoring prepared Flask only...",
                        NamedTextColor.YELLOW));
        List.copyOf(progress.pending())
                .forEach(characterId -> attemptRestore(reset.encounterId(), characterId));
    }

    private void attemptRestore(EncounterId encounterId, CharacterId characterId) {
        BossEncounterRuntime runtime = active.get(encounterId);
        ResetProgress progress = resets.get(encounterId);
        if (runtime == null
                || runtime.phase() != BossEncounterPhase.RESETTING
                || progress == null
                || !progress.pending().contains(characterId)
                || !progress.inFlight().add(characterId)) {
            return;
        }
        Player player = plugin.getServer().getPlayer(characterId.value());
        LoadedCharacterSession session =
                player == null ? null : characterSessions.active(player).orElse(null);
        if (player == null
                || !player.isOnline()
                || session == null
                || !characterSessions.ready(player)) {
            progress.inFlight().remove(characterId);
            return;
        }
        PersistentExpeditionState current = session.snapshot().expeditionState();
        PreparedFlaskSnapshot prepared = current.preparedFlaskSnapshot().orElse(null);
        if (prepared == null) {
            restoreBlocked(encounterId, characterId, player, "prepared Flask snapshot is missing");
            return;
        }
        Result<FlaskState, FlaskCheckpointErrorCode> restored =
                flaskCheckpoints.restore(runtime.checkpointInstanceId(), prepared, true);
        if (restored instanceof Result.Failure<FlaskState, FlaskCheckpointErrorCode> failure) {
            restoreBlocked(
                    encounterId,
                    characterId,
                    player,
                    failure.error().code() + " " + failure.detail());
            return;
        }
        FlaskState flask =
                ((Result.Success<FlaskState, FlaskCheckpointErrorCode>) restored).value();
        if (current.flaskState().equals(flask)) {
            restoreSucceeded(encounterId, characterId, player);
            return;
        }
        PersistentExpeditionState desired =
                new PersistentExpeditionState(
                        flask,
                        current.consumableEffects(),
                        current.ailments(),
                        current.preparedFlaskSnapshot());
        UUID restoreOperation = operation(encounterId, "restore", characterId, runtime.attempt());
        characterSessions.commitExpeditionState(
                player,
                desired,
                restoreOperation,
                contentVersion,
                result -> {
                    if (result instanceof Result.Failure<?, ?> failure) {
                        progress.inFlight().remove(characterId);
                        player.sendMessage(
                                Component.text(
                                        "Flask restore retry pending: " + failure.detail(),
                                        NamedTextColor.RED));
                        plugin.getServer()
                                .getScheduler()
                                .runTaskLater(
                                        plugin,
                                        () -> attemptRestore(encounterId, characterId),
                                        20L);
                        return;
                    }
                    restoreSucceeded(encounterId, characterId, player);
                });
    }

    private void restoreBlocked(
            EncounterId encounterId, CharacterId characterId, Player player, String detail) {
        ResetProgress progress = resets.get(encounterId);
        if (progress != null) {
            progress.inFlight().remove(characterId);
        }
        player.sendMessage(
                Component.text("Encounter reset blocked: " + detail, NamedTextColor.RED));
    }

    private void restoreSucceeded(EncounterId encounterId, CharacterId characterId, Player player) {
        ResetProgress progress = resets.get(encounterId);
        BossEncounterRuntime runtime = active.get(encounterId);
        if (progress == null
                || runtime == null
                || runtime.phase() != BossEncounterPhase.RESETTING) {
            return;
        }
        progress.inFlight().remove(characterId);
        progress.pending().remove(characterId);
        player.sendMessage(
                Component.text("Prepared Flask restored exactly once.", NamedTextColor.GREEN));
        flaskHotbar.onCharacterReady(player);
        if (!progress.pending().isEmpty()) {
            return;
        }
        mutate(
                encounterId,
                progress.completionOperation(),
                current ->
                        encounters.completeReset(
                                current, progress.resetOperation(), progress.completionOperation()),
                "all Flask restores acknowledged");
    }

    private void finishResetEffects(BossEncounterRuntime next) {
        resets.remove(next.encounterId());
        for (CharacterId participantId : next.participants().keySet()) {
            Player participant = plugin.getServer().getPlayer(participantId.value());
            if (participant != null && participant.isOnline() && !participant.isDead()) {
                var maximumHealth = participant.getAttribute(Attribute.MAX_HEALTH);
                if (maximumHealth != null) {
                    participant.setHealth(maximumHealth.getValue());
                }
            }
        }
        broadcast(
                next,
                Component.text(
                        "Encounter retry ACTIVE | attempt=" + next.attempt(),
                        NamedTextColor.GREEN));
    }

    private BossEncounterRuntime runtimeFor(CharacterId characterId) {
        EncounterId encounterId = encounterByParticipant.get(characterId);
        return encounterId == null ? null : active.get(encounterId);
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

    private void completeRecovery(
            Result<List<StoredBossEncounter>, TransactionErrorCode> recovered) {
        if (recovered
                instanceof
                Result.Failure<List<StoredBossEncounter>, TransactionErrorCode> failure) {
            plugin.getLogger()
                    .severe(
                            "Boss encounter recovery failed: "
                                    + failure.error().code()
                                    + " "
                                    + failure.detail());
            return;
        }
        List<StoredBossEncounter> records =
                ((Result.Success<List<StoredBossEncounter>, TransactionErrorCode>) recovered)
                        .value();
        for (StoredBossEncounter stored : records) {
            if (stored.runtime().participants().keySet().stream()
                    .anyMatch(encounterByParticipant::containsKey)) {
                plugin.getLogger()
                        .severe(
                                "Skipping conflicting recoverable encounter "
                                        + stored.runtime().encounterId().value());
                continue;
            }
            install(stored);
        }
        recoveryReady = true;
        for (StoredBossEncounter stored : records) {
            BossEncounterRuntime runtime = active.get(stored.runtime().encounterId());
            if (runtime == null) {
                continue;
            }
            switch (runtime.phase()) {
                case ACTIVE -> {
                    UUID operationId = UUID.randomUUID();
                    mutate(
                            runtime.encounterId(),
                            operationId,
                            current ->
                                    encounters.recoverAfterRestart(
                                            current,
                                            operationId,
                                            plugin.getServer().getCurrentTick()),
                            "restart recovery committed; rejoin grace rebased",
                            true);
                }
                case WIPE_PENDING -> beginReset(runtime);
                case RESETTING -> startResetEffects(runtime, runtime.participants().keySet());
                case VICTORY_PENDING ->
                        broadcast(
                                runtime,
                                Component.text(
                                        "Recovered victory; reward reconciliation remains pending.",
                                        NamedTextColor.YELLOW));
                case COMPLETED -> {
                    // Completed rows are excluded by the recovery query.
                }
                default ->
                        throw new IllegalStateException(
                                "Unsupported recovered encounter phase: " + runtime.phase());
            }
        }
        plugin.getLogger().info("Recovered " + active.size() + " boss encounter(s) from V0009.");
    }

    private void install(StoredBossEncounter stored) {
        BossEncounterRuntime runtime = stored.runtime();
        active.put(runtime.encounterId(), runtime);
        durableRecords.put(runtime.encounterId(), stored.record());
        for (CharacterId participantId : runtime.participants().keySet()) {
            encounterByParticipant.put(participantId, runtime.encounterId());
            recentEncounterByParticipant.put(participantId, runtime.encounterId());
        }
    }

    private void broadcast(BossEncounterRuntime runtime, Component message) {
        runtime.participants().keySet().stream()
                .map(characterId -> plugin.getServer().getPlayer(characterId.value()))
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .forEach(player -> player.sendMessage(message));
    }

    private Player firstOnline(BossEncounterRuntime runtime) {
        return runtime.participants().keySet().stream()
                .map(characterId -> plugin.getServer().getPlayer(characterId.value()))
                .filter(Objects::nonNull)
                .filter(Player::isOnline)
                .findFirst()
                .orElse(null);
    }

    private static CharacterId characterId(Player player) {
        return new CharacterId(player.getUniqueId());
    }

    private static UUID operation(
            EncounterId encounterId, String action, CharacterId characterId, int attempt) {
        String key =
                encounterId.value()
                        + ":"
                        + action
                        + ":"
                        + (characterId == null ? "encounter" : characterId.value())
                        + ":"
                        + attempt;
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }

    private static void usage(Player player) {
        player.sendMessage(
                "Usage: /mmo encounter start <encounter-uuid> [player ...] | status | "
                        + "defeat [player] | boundary [player] | rejoin | victory | "
                        + "rewards [grant-uuid]");
    }

    private enum AvailabilityCommand {
        DEFEAT,
        BOUNDARY
    }

    private record ResetProgress(
            UUID resetOperation,
            UUID completionOperation,
            Set<CharacterId> pending,
            Set<CharacterId> inFlight) {
        private ResetProgress {
            Objects.requireNonNull(resetOperation, "resetOperation");
            Objects.requireNonNull(completionOperation, "completionOperation");
            Objects.requireNonNull(pending, "pending");
            Objects.requireNonNull(inFlight, "inFlight");
        }
    }

    private record PendingMutation(
            UUID operationId,
            Function<BossEncounterRuntime, Result<BossEncounterTransition, BossEncounterErrorCode>>
                    mutation,
            String description,
            boolean resumeOnlineParticipants) {
        private PendingMutation {
            Objects.requireNonNull(operationId, "operationId");
            Objects.requireNonNull(mutation, "mutation");
            Objects.requireNonNull(description, "description");
        }
    }
}
