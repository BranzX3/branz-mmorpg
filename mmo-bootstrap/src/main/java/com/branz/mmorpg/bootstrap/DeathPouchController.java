package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.persistence.transaction.CarriedWalletBalance;
import com.branz.mmorpg.persistence.transaction.CarriedWalletOperationKind;
import com.branz.mmorpg.persistence.transaction.CarriedWalletService;
import com.branz.mmorpg.persistence.transaction.DeathPouchRecord;
import com.branz.mmorpg.persistence.transaction.DeathPouchRepository;
import com.branz.mmorpg.persistence.transaction.TransactionErrorCode;
import com.branz.mmorpg.worldloop.death.DeathPouchContext;
import com.branz.mmorpg.worldloop.death.DeathPouchDecision;
import com.branz.mmorpg.worldloop.death.DeathPouchDecisionReason;
import com.branz.mmorpg.worldloop.death.DeathPouchDraft;
import com.branz.mmorpg.worldloop.death.DeathPouchEngine;
import com.branz.mmorpg.worldloop.death.DeathPouchLocation;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** Live owner-only Death Pouch adapter over the V0012 saga and V0013 carried wallet. */
final class DeathPouchController implements Listener {
    private static final double RECOVERY_DISTANCE_SQUARED = 16.0;

    private final JavaPlugin plugin;
    private final CharacterSessionController characterSessions;
    private final BossEncounterController bossEncounters;
    private final DurableDeathPouchStore store;
    private final CarriedWalletService wallet;
    private final DeathPouchSagaService saga;
    private final Clock clock;
    private final DeathPouchEngine engine = new DeathPouchEngine();
    private final Map<CharacterId, List<DeathPouchRecord>> activeByOwner = new HashMap<>();
    private final Set<CharacterId> creationInFlight = ConcurrentHashMap.newKeySet();
    private final Set<UUID> pouchInFlight = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean scanInFlight = new AtomicBoolean();
    private int particleTaskId = -1;
    private int reconciliationTaskId = -1;
    private boolean recoveryReady;

    DeathPouchController(
            JavaPlugin plugin,
            CharacterSessionController characterSessions,
            BossEncounterController bossEncounters,
            DeathPouchRepository pouches,
            CarriedWalletService wallet,
            String contentVersion) {
        this(
                plugin,
                characterSessions,
                bossEncounters,
                pouches,
                wallet,
                contentVersion,
                Clock.systemUTC());
    }

    DeathPouchController(
            JavaPlugin plugin,
            CharacterSessionController characterSessions,
            BossEncounterController bossEncounters,
            DeathPouchRepository pouches,
            CarriedWalletService wallet,
            String contentVersion,
            Clock clock) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.characterSessions = Objects.requireNonNull(characterSessions, "characterSessions");
        this.bossEncounters = Objects.requireNonNull(bossEncounters, "bossEncounters");
        store = new DurableDeathPouchStore(pouches, contentVersion);
        this.wallet = Objects.requireNonNull(wallet, "wallet");
        saga = new DeathPouchSagaService(store, wallet, contentVersion);
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    void start() {
        particleTaskId =
                plugin.getServer()
                        .getScheduler()
                        .scheduleSyncRepeatingTask(plugin, this::renderOwnerPouches, 10L, 10L);
        reconciliationTaskId =
                plugin.getServer()
                        .getScheduler()
                        .scheduleSyncRepeatingTask(
                                plugin, () -> reconcileDurable(false), 200L, 200L);
        reconcileDurable(true);
    }

    void shutdown() {
        if (particleTaskId >= 0) {
            plugin.getServer().getScheduler().cancelTask(particleTaskId);
            particleTaskId = -1;
        }
        if (reconciliationTaskId >= 0) {
            plugin.getServer().getScheduler().cancelTask(reconciliationTaskId);
            reconciliationTaskId = -1;
        }
        activeByOwner.clear();
        creationInFlight.clear();
        pouchInFlight.clear();
        recoveryReady = false;
    }

    void onCharacterReady(Player player) {
        refreshOwner(characterId(player));
    }

    void handleCommand(Player player, String[] args, boolean devToolsAllowed) {
        if (!characterSessions.ready(player)) {
            player.sendMessage(
                    Component.text("Character session is not ready.", NamedTextColor.RED));
            return;
        }
        if (args.length < 2) {
            usage(player);
            return;
        }
        switch (args[1].toLowerCase(java.util.Locale.ROOT)) {
            case "wallet" -> showWallet(player);
            case "status" -> showStatus(player);
            case "recover" -> recover(player, args);
            case "fund" -> {
                if (devToolsAllowed) {
                    fund(player, args);
                } else {
                    devDenied(player);
                }
            }
            case "simulate" -> {
                if (devToolsAllowed) {
                    createAt(player, false);
                } else {
                    devDenied(player);
                }
            }
            default -> usage(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!characterSessions.ready(player) || !recoveryReady) {
            return;
        }
        if (bossEncounters.suppressesDeathPouch(player)) {
            return;
        }
        createAt(player, player.getKiller() != null);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        activeByOwner.remove(characterId(event.getPlayer()));
    }

    private void createAt(Player player, boolean pvpSuppressed) {
        CharacterId owner = characterId(player);
        if (!recoveryReady) {
            player.sendMessage(
                    Component.text(
                            "Death Pouch recovery is still loading.", NamedTextColor.YELLOW));
            return;
        }
        if (!creationInFlight.add(owner)) {
            player.sendMessage(
                    Component.text(
                            "A Death Pouch operation is already pending.", NamedTextColor.YELLOW));
            return;
        }
        UUID deathId = UUID.randomUUID();
        Location location = player.getLocation().clone();
        Instant createdAt = clock.instant();
        DeathPouchContext context =
                pvpSuppressed ? DeathPouchContext.DUEL : DeathPouchContext.OPEN_WORLD_PVE;
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<CarriedWalletBalance, TransactionErrorCode> balance =
                                    wallet.balance(owner);
                            CreationOutcome outcome;
                            if (balance
                                    instanceof
                                    Result.Failure<CarriedWalletBalance, TransactionErrorCode>
                                            failure) {
                                outcome = CreationOutcome.failure(detail(failure));
                            } else {
                                long carried =
                                        ((Result.Success<
                                                                CarriedWalletBalance,
                                                                TransactionErrorCode>)
                                                        balance)
                                                .value()
                                                .balance();
                                DeathPouchDecision decision =
                                        engine.plan(
                                                deathId,
                                                owner,
                                                context,
                                                carried,
                                                new DeathPouchLocation(
                                                        location.getWorld().getKey().toString(),
                                                        location.getX(),
                                                        location.getY(),
                                                        location.getZ()),
                                                createdAt);
                                outcome = activateDecision(decision);
                            }
                            CreationOutcome completed = outcome;
                            runSyncIfEnabled(() -> completeCreation(player, owner, completed));
                        });
    }

    private CreationOutcome activateDecision(DeathPouchDecision decision) {
        if (decision.draft().isEmpty()) {
            return CreationOutcome.suppressed(decision.reason());
        }
        DeathPouchDraft draft = decision.draft().orElseThrow();
        Result<DeathPouchRecord, TransactionErrorCode> active = saga.activate(draft);
        if (active instanceof Result.Failure<DeathPouchRecord, TransactionErrorCode> failure) {
            return CreationOutcome.failure(detail(failure));
        }
        return CreationOutcome.created(success(active));
    }

    private void completeCreation(Player player, CharacterId owner, CreationOutcome outcome) {
        creationInFlight.remove(owner);
        if (outcome.record() != null) {
            upsertActive(outcome.record());
            if (player.isOnline()) {
                player.sendMessage(
                        Component.text(
                                "Death Pouch created: "
                                        + outcome.record().amount()
                                        + " carried currency. Return to the death site within 7 days.",
                                NamedTextColor.GOLD));
            }
            return;
        }
        if (outcome.reason() == DeathPouchDecisionReason.CARRIED_WALLET_TOO_SMALL) {
            if (player.isOnline()) {
                player.sendMessage(
                        Component.text("No carried currency was lost.", NamedTextColor.GRAY));
            }
        } else if (outcome.error() != null && player.isOnline()) {
            player.sendMessage(
                    Component.text(
                            "Death Pouch is pending reconciliation: " + outcome.error(),
                            NamedTextColor.RED));
        }
    }

    private void showWallet(Player player) {
        CharacterId owner = characterId(player);
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<CarriedWalletBalance, TransactionErrorCode> result =
                                    wallet.balance(owner);
                            runSyncIfEnabled(
                                    () -> {
                                        if (result
                                                instanceof
                                                Result.Failure<
                                                                CarriedWalletBalance,
                                                                TransactionErrorCode>
                                                        failure) {
                                            player.sendMessage(
                                                    Component.text(
                                                            detail(failure), NamedTextColor.RED));
                                        } else if (player.isOnline()) {
                                            CarriedWalletBalance balance = success(result);
                                            player.sendMessage(
                                                    Component.text(
                                                            "Carried wallet: "
                                                                    + balance.balance()
                                                                    + " (v"
                                                                    + balance.version()
                                                                    + ")",
                                                            NamedTextColor.GOLD));
                                        }
                                    });
                        });
    }

    private void fund(Player player, String[] args) {
        if (args.length != 3) {
            player.sendMessage(
                    Component.text("Usage: /mmo pouch fund <amount>", NamedTextColor.YELLOW));
            return;
        }
        long amount;
        try {
            amount = Long.parseLong(args[2]);
            if (amount < 1) {
                throw new NumberFormatException("not positive");
            }
        } catch (NumberFormatException exception) {
            player.sendMessage(
                    Component.text("Amount must be a positive integer.", NamedTextColor.RED));
            return;
        }
        CharacterId owner = characterId(player);
        UUID operationId = UUID.randomUUID();
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<CarriedWalletBalance, TransactionErrorCode> result =
                                    saga.adjust(
                                            operationId,
                                            owner,
                                            CarriedWalletOperationKind.CREDIT,
                                            amount);
                            runSyncIfEnabled(
                                    () -> {
                                        if (!player.isOnline()) {
                                            return;
                                        }
                                        if (result
                                                instanceof
                                                Result.Failure<
                                                                CarriedWalletBalance,
                                                                TransactionErrorCode>
                                                        failure) {
                                            player.sendMessage(
                                                    Component.text(
                                                            detail(failure), NamedTextColor.RED));
                                        } else {
                                            player.sendMessage(
                                                    Component.text(
                                                            "Funded carried wallet; balance="
                                                                    + success(result).balance(),
                                                            NamedTextColor.GREEN));
                                        }
                                    });
                        });
    }

    private void showStatus(Player player) {
        List<DeathPouchRecord> records = activeByOwner.getOrDefault(characterId(player), List.of());
        if (records.isEmpty()) {
            player.sendMessage(Component.text("No active Death Pouches.", NamedTextColor.GRAY));
            return;
        }
        player.sendMessage(Component.text("Active Death Pouches:", NamedTextColor.GOLD));
        for (DeathPouchRecord record : records) {
            player.sendMessage(
                    Component.text(
                            record.pouchId()
                                    + " | amount="
                                    + record.amount()
                                    + " | expires="
                                    + record.expiresAt(),
                            NamedTextColor.YELLOW));
        }
    }

    private void recover(Player player, String[] args) {
        CharacterId owner = characterId(player);
        List<DeathPouchRecord> records = activeByOwner.getOrDefault(owner, List.of());
        DeathPouchRecord target;
        if (args.length == 2 && records.size() == 1) {
            target = records.getFirst();
        } else if (args.length == 3) {
            UUID pouchId;
            try {
                pouchId = UUID.fromString(args[2]);
            } catch (IllegalArgumentException exception) {
                player.sendMessage(Component.text("Pouch ID must be a UUID.", NamedTextColor.RED));
                return;
            }
            target =
                    records.stream()
                            .filter(record -> record.pouchId().equals(pouchId))
                            .findFirst()
                            .orElse(null);
        } else {
            player.sendMessage(
                    Component.text(
                            "Usage: /mmo pouch recover [pouch-uuid]", NamedTextColor.YELLOW));
            return;
        }
        if (target == null) {
            player.sendMessage(
                    Component.text("That active pouch is not yours.", NamedTextColor.RED));
            return;
        }
        if (!near(player, target)) {
            player.sendMessage(
                    Component.text("Move closer to the visible Death Pouch.", NamedTextColor.RED));
            return;
        }
        if (!pouchInFlight.add(target.pouchId())) {
            player.sendMessage(
                    Component.text(
                            "That pouch is already being recovered.", NamedTextColor.YELLOW));
            return;
        }
        DeathPouchRecord selected = target;
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<DeathPouchRecord, TransactionErrorCode> result =
                                    saga.recover(selected);
                            runSyncIfEnabled(() -> completeRecovery(player, selected, result));
                        });
    }

    private void completeRecovery(
            Player player,
            DeathPouchRecord selected,
            Result<DeathPouchRecord, TransactionErrorCode> result) {
        pouchInFlight.remove(selected.pouchId());
        if (result instanceof Result.Failure<DeathPouchRecord, TransactionErrorCode> failure) {
            removeActive(selected.ownerCharacterId(), selected.pouchId());
            if (player.isOnline()) {
                player.sendMessage(
                        Component.text(
                                "Recovery is durably pending reconciliation: " + detail(failure),
                                NamedTextColor.RED));
            }
            return;
        }
        removeActive(selected.ownerCharacterId(), selected.pouchId());
        if (player.isOnline()) {
            player.sendMessage(
                    Component.text(
                            "Recovered " + selected.amount() + " carried currency.",
                            NamedTextColor.GREEN));
        }
    }

    private void reconcileDurable(boolean startup) {
        if (!scanInFlight.compareAndSet(false, true)) {
            return;
        }
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Instant now = clock.instant();
                            Set<CharacterId> changedOwners = new HashSet<>();
                            String failure = reconcileRecoverable(now, changedOwners);
                            if (failure == null) {
                                failure = reconcileExpiry(now, changedOwners);
                            }
                            String scanFailure = failure;
                            scanInFlight.set(false);
                            runSyncIfEnabled(
                                    () -> {
                                        boolean becameReady = scanFailure == null && !recoveryReady;
                                        if (becameReady) {
                                            recoveryReady = true;
                                        }
                                        for (CharacterId owner : changedOwners) {
                                            Player ownerPlayer =
                                                    plugin.getServer().getPlayer(owner.value());
                                            if (ownerPlayer != null
                                                    && ownerPlayer.isOnline()
                                                    && characterSessions.ready(ownerPlayer)) {
                                                refreshOwner(owner);
                                            } else {
                                                activeByOwner.remove(owner);
                                            }
                                        }
                                        if (scanFailure != null) {
                                            plugin.getLogger()
                                                    .warning(
                                                            "Death Pouch reconciliation failed: "
                                                                    + scanFailure);
                                        } else if (startup || becameReady) {
                                            plugin.getLogger()
                                                    .info("Death Pouch reconciliation is ready.");
                                            plugin.getServer()
                                                    .getOnlinePlayers()
                                                    .forEach(this::onCharacterReady);
                                        }
                                    });
                        });
    }

    private String reconcileRecoverable(Instant now, Set<CharacterId> changedOwners) {
        Result<List<DeathPouchRecord>, TransactionErrorCode> result = store.recoverable();
        if (result
                instanceof Result.Failure<List<DeathPouchRecord>, TransactionErrorCode> failure) {
            return detail(failure);
        }
        for (DeathPouchRecord record : success(result)) {
            if (!pouchInFlight.add(record.pouchId())) {
                continue;
            }
            Result<DeathPouchRecord, TransactionErrorCode> reconciled = saga.resume(record, now);
            pouchInFlight.remove(record.pouchId());
            if (reconciled
                    instanceof Result.Failure<DeathPouchRecord, TransactionErrorCode> failure) {
                plugin.getLogger()
                        .warning(
                                "Death Pouch "
                                        + record.pouchId()
                                        + " remains recoverable: "
                                        + detail(failure));
                continue;
            }
            changedOwners.add(record.ownerCharacterId());
        }
        return null;
    }

    private String reconcileExpiry(Instant now, Set<CharacterId> changedOwners) {
        Result<List<DeathPouchRecord>, TransactionErrorCode> result = store.expirable(now);
        if (result
                instanceof Result.Failure<List<DeathPouchRecord>, TransactionErrorCode> failure) {
            return detail(failure);
        }
        for (DeathPouchRecord record : success(result)) {
            if (!pouchInFlight.add(record.pouchId())) {
                continue;
            }
            Result<DeathPouchRecord, TransactionErrorCode> expired = saga.expire(record, now);
            pouchInFlight.remove(record.pouchId());
            if (expired instanceof Result.Failure<DeathPouchRecord, TransactionErrorCode> failure) {
                plugin.getLogger()
                        .warning(
                                "Death Pouch "
                                        + record.pouchId()
                                        + " could not expire: "
                                        + detail(failure));
                continue;
            }
            changedOwners.add(record.ownerCharacterId());
        }
        return null;
    }

    private void refreshOwner(CharacterId owner) {
        plugin.getServer()
                .getScheduler()
                .runTaskAsynchronously(
                        plugin,
                        () -> {
                            Result<List<DeathPouchRecord>, TransactionErrorCode> result =
                                    store.active(owner);
                            runSyncIfEnabled(
                                    () -> {
                                        if (result
                                                instanceof
                                                Result.Success<
                                                                List<DeathPouchRecord>,
                                                                TransactionErrorCode>
                                                        success) {
                                            activeByOwner.put(owner, success.value());
                                        } else {
                                            plugin.getLogger()
                                                    .warning(
                                                            "Could not load active Death Pouches for "
                                                                    + owner.value()
                                                                    + ": "
                                                                    + detail(
                                                                            (Result.Failure<
                                                                                            List<
                                                                                                    DeathPouchRecord>,
                                                                                            TransactionErrorCode>)
                                                                                    result));
                                        }
                                    });
                        });
    }

    private void renderOwnerPouches() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            for (DeathPouchRecord record :
                    activeByOwner.getOrDefault(characterId(player), List.of())) {
                if (!player.getWorld().getKey().toString().equals(record.worldKey())) {
                    continue;
                }
                player.spawnParticle(
                        Particle.SOUL_FIRE_FLAME,
                        record.locationX(),
                        record.locationY() + 0.45,
                        record.locationZ(),
                        3,
                        0.22,
                        0.3,
                        0.22,
                        0.005);
            }
        }
    }

    private void upsertActive(DeathPouchRecord record) {
        ArrayList<DeathPouchRecord> records =
                new ArrayList<>(activeByOwner.getOrDefault(record.ownerCharacterId(), List.of()));
        records.removeIf(existing -> existing.pouchId().equals(record.pouchId()));
        records.add(record);
        records.sort(
                java.util.Comparator.comparing(DeathPouchRecord::createdAt)
                        .thenComparing(DeathPouchRecord::pouchId));
        activeByOwner.put(record.ownerCharacterId(), List.copyOf(records));
    }

    private void removeActive(CharacterId owner, UUID pouchId) {
        List<DeathPouchRecord> current = activeByOwner.get(owner);
        if (current == null) {
            return;
        }
        activeByOwner.put(
                owner, current.stream().filter(row -> !row.pouchId().equals(pouchId)).toList());
    }

    private static boolean near(Player player, DeathPouchRecord record) {
        if (!player.getWorld().getKey().toString().equals(record.worldKey())) {
            return false;
        }
        Location location = player.getLocation();
        double dx = location.getX() - record.locationX();
        double dy = location.getY() - record.locationY();
        double dz = location.getZ() - record.locationZ();
        return dx * dx + dy * dy + dz * dz <= RECOVERY_DISTANCE_SQUARED;
    }

    private void runSyncIfEnabled(Runnable action) {
        if (!plugin.isEnabled()) {
            return;
        }
        try {
            plugin.getServer().getScheduler().runTask(plugin, action);
        } catch (org.bukkit.plugin.IllegalPluginAccessException exception) {
            if (plugin.isEnabled()) {
                throw exception;
            }
        }
    }

    private static CharacterId characterId(Player player) {
        return new CharacterId(player.getUniqueId());
    }

    private static <T> T success(Result<T, TransactionErrorCode> result) {
        return ((Result.Success<T, TransactionErrorCode>) result).value();
    }

    private static String detail(Result.Failure<?, TransactionErrorCode> failure) {
        return failure.error().code() + ": " + failure.detail();
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static void usage(Player player) {
        player.sendMessage(
                Component.text(
                        "Usage: /mmo pouch <wallet|status|recover [uuid]|fund <amount>|simulate>",
                        NamedTextColor.YELLOW));
    }

    private static void devDenied(Player player) {
        player.sendMessage(
                Component.text(
                        "Death Pouch development commands are disabled for this account/environment.",
                        NamedTextColor.RED));
    }

    private record CreationOutcome(
            DeathPouchRecord record, DeathPouchDecisionReason reason, String error) {
        static CreationOutcome created(DeathPouchRecord record) {
            return new CreationOutcome(Objects.requireNonNull(record, "record"), null, null);
        }

        static CreationOutcome suppressed(DeathPouchDecisionReason reason) {
            return new CreationOutcome(null, Objects.requireNonNull(reason, "reason"), null);
        }

        static CreationOutcome failure(String error) {
            return new CreationOutcome(null, null, requireText(error, "error"));
        }
    }
}
