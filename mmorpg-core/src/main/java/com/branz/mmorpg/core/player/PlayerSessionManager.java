package com.branz.mmorpg.core.player;

import com.branz.mmorpg.api.player.PlayerProfile;
import com.branz.mmorpg.api.player.PlayerProfileComponent;
import com.branz.mmorpg.api.player.PlayerProfileRecoveryRecord;
import com.branz.mmorpg.api.player.PlayerProfileRecoveryStore;
import com.branz.mmorpg.api.player.PlayerProfileStore;
import com.branz.mmorpg.api.player.PlayerSessionService;
import com.branz.mmorpg.api.player.PlayerSessionSnapshot;
import com.branz.mmorpg.api.player.PlayerSessionState;
import com.branz.mmorpg.api.player.PlayerSessionToken;
import java.time.Clock;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.UnaryOperator;

public final class PlayerSessionManager implements PlayerSessionService {
    private final PlayerProfileStore store;
    private final Clock clock;
    private final PlayerSessionSavePolicy savePolicy;
    private final PlayerProfileRecoveryStore recoveryStore;
    private final AtomicLong tokens = new AtomicLong();
    private final Map<UUID, SessionEntry> sessions = new HashMap<>();

    public PlayerSessionManager(PlayerProfileStore store) {
        this(store, Clock.systemUTC(), PlayerSessionSavePolicy.DEFAULT, PlayerProfileRecoveryStore.none());
    }

    public PlayerSessionManager(PlayerProfileStore store, Clock clock) {
        this(store, clock, PlayerSessionSavePolicy.DEFAULT, PlayerProfileRecoveryStore.none());
    }

    public PlayerSessionManager(PlayerProfileStore store, PlayerProfileRecoveryStore recoveryStore) {
        this(store, Clock.systemUTC(), PlayerSessionSavePolicy.DEFAULT, recoveryStore);
    }

    public PlayerSessionManager(
            PlayerProfileStore store,
            PlayerSessionSavePolicy savePolicy,
            PlayerProfileRecoveryStore recoveryStore) {
        this(store, Clock.systemUTC(), savePolicy, recoveryStore);
    }

    public PlayerSessionManager(
            PlayerProfileStore store, Clock clock, PlayerSessionSavePolicy savePolicy) {
        this(store, clock, savePolicy, PlayerProfileRecoveryStore.none());
    }

    public PlayerSessionManager(
            PlayerProfileStore store,
            Clock clock,
            PlayerSessionSavePolicy savePolicy,
            PlayerProfileRecoveryStore recoveryStore) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.savePolicy = Objects.requireNonNull(savePolicy, "savePolicy");
        this.recoveryStore = Objects.requireNonNull(recoveryStore, "recoveryStore");
    }

    @Override
    public CompletionStage<PlayerSessionSnapshot> open(
            UUID playerId, String lastKnownName, long contentRevision) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(lastKnownName, "lastKnownName");
        if (lastKnownName.isBlank()) {
            throw new IllegalArgumentException("Player name must not be blank");
        }
        if (contentRevision < 0) {
            throw new IllegalArgumentException("Content revision must not be negative");
        }

        SessionEntry loading;
        synchronized (sessions) {
            SessionEntry existing = sessions.get(playerId);
            if (existing != null) {
                return CompletableFuture.completedFuture(snapshot(
                        playerId,
                        nextToken(),
                        PlayerSessionState.CONFLICTED,
                        null,
                        Set.of(),
                        contentRevision,
                        "an existing session is " + existing.state));
            }
            loading = new SessionEntry(playerId, nextToken(), contentRevision);
            sessions.put(playerId, loading);
        }

        return loadWithRecovery(playerId, lastKnownName, clock.instant())
                .handle((profile, failure) -> completeLoad(loading, profile, failure));
    }

    @Override
    public PlayerSessionSnapshot updateProfile(
            UUID playerId,
            PlayerSessionToken token,
            Set<PlayerProfileComponent> components,
            UnaryOperator<PlayerProfile> update) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(token, "token");
        components = Set.copyOf(components);
        if (components.isEmpty()) {
            throw new IllegalArgumentException("At least one dirty component is required");
        }
        Objects.requireNonNull(update, "update");

        synchronized (sessions) {
            SessionEntry entry = requireCurrent(playerId, token);
            if (entry.state != PlayerSessionState.ACTIVE) {
                throw new IllegalStateException("Profile mutation requires an ACTIVE session, was " + entry.state);
            }
            PlayerProfile previous = Objects.requireNonNull(entry.profile, "profile");
            PlayerProfile updated = Objects.requireNonNull(update.apply(previous), "updated profile");
            validateMutation(previous, updated, components);
            if (!updated.equals(previous)) {
                entry.profile = updated;
                entry.dirtyComponents.addAll(components);
            }
            return entry.snapshot();
        }
    }

    @Override
    public CompletionStage<PlayerSessionSnapshot> save(UUID playerId, PlayerSessionToken token) {
        return saveInternal(playerId, token, false);
    }

    @Override
    public CompletionStage<PlayerSessionSnapshot> close(UUID playerId, PlayerSessionToken token) {
        return saveInternal(playerId, token, true);
    }

    @Override
    public Optional<PlayerSessionSnapshot> snapshot(UUID playerId) {
        synchronized (sessions) {
            SessionEntry entry = sessions.get(playerId);
            return entry == null ? Optional.empty() : Optional.of(entry.snapshot());
        }
    }

    @Override
    public int activeSessionCount() {
        synchronized (sessions) {
            return (int) sessions.values().stream()
                    .filter(session -> session.state == PlayerSessionState.ACTIVE)
                    .count();
        }
    }

    @Override
    public int dirtySessionCount() {
        synchronized (sessions) {
            return (int) sessions.values().stream()
                    .filter(session -> !session.dirtyComponents.isEmpty())
                    .count();
        }
    }

    private CompletionStage<PlayerSessionSnapshot> saveInternal(
            UUID playerId, PlayerSessionToken token, boolean closing) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(token, "token");

        SessionEntry entry;
        CompletableFuture<PlayerSessionSnapshot> result;
        synchronized (sessions) {
            entry = sessions.get(playerId);
            if (entry == null || !entry.token.equals(token)) {
                return CompletableFuture.completedFuture(closed(playerId, token, 0, "stale close ignored"));
            }
            if (closing) {
                entry.closeRequested = true;
            }
            if (entry.state == PlayerSessionState.LOADING
                    || entry.state == PlayerSessionState.LOAD_FAILED
                    || entry.profile == null) {
                if (closing) {
                    sessions.remove(playerId);
                    return CompletableFuture.completedFuture(closed(
                            playerId, token, entry.contentRevision, "closed without save"));
                }
                return CompletableFuture.completedFuture(entry.snapshot());
            }
            if (entry.state == PlayerSessionState.SAVING) {
                return Objects.requireNonNull(entry.inFlightSave, "inFlightSave");
            }
            if (entry.state != PlayerSessionState.ACTIVE
                    && entry.state != PlayerSessionState.SAVE_RETRY_PENDING) {
                return CompletableFuture.completedFuture(entry.snapshot());
            }

            if (closing) {
                entry.profile = entry.profile.seenAs(entry.profile.lastKnownName(), clock.instant());
                entry.dirtyComponents.add(PlayerProfileComponent.IDENTITY);
            }
            if (entry.dirtyComponents.isEmpty()) {
                if (closing) {
                    sessions.remove(playerId);
                    return CompletableFuture.completedFuture(closed(
                            playerId, token, entry.contentRevision, "closed; profile was clean"));
                }
                return CompletableFuture.completedFuture(entry.snapshot());
            }

            entry.state = PlayerSessionState.SAVING;
            entry.detail = "saving profile (attempt 1/" + savePolicy.maximumAttempts() + ')';
            result = new CompletableFuture<>();
            entry.inFlightSave = result;
        }

        attemptSave(entry, entry.profile, 1, result);
        return result;
    }

    private void attemptSave(
            SessionEntry entry,
            PlayerProfile profile,
            int attempt,
            CompletableFuture<PlayerSessionSnapshot> result) {
        CompletionStage<PlayerProfile> save;
        try {
            save = Objects.requireNonNull(store.save(profile), "save stage");
        } catch (RuntimeException failure) {
            handleSaveAttempt(entry, profile, attempt, null, failure, result);
            return;
        }
        save.whenComplete((saved, failure) ->
                handleSaveAttempt(entry, profile, attempt, saved, failure, result));
    }

    private void handleSaveAttempt(
            SessionEntry entry,
            PlayerProfile profile,
            int attempt,
            PlayerProfile saved,
            Throwable failure,
            CompletableFuture<PlayerSessionSnapshot> result) {
        if (failure != null && attempt < savePolicy.maximumAttempts()) {
            synchronized (sessions) {
                SessionEntry current = sessions.get(entry.playerId);
                if (current != entry || current.state != PlayerSessionState.SAVING) {
                    result.complete(closed(
                            entry.playerId, entry.token, entry.contentRevision, "late save ignored"));
                    return;
                }
                current.detail = "saving profile (attempt " + (attempt + 1) + '/'
                        + savePolicy.maximumAttempts() + ')';
            }
            attemptSave(entry, profile, attempt + 1, result);
            return;
        }

        if (failure != null) {
            recordRecovery(entry, profile, attempt, failure, result);
            return;
        }

        CompletionStage<Void> cleanup;
        try {
            cleanup = Objects.requireNonNull(recoveryStore.delete(entry.playerId), "recovery delete stage");
        } catch (RuntimeException cleanupFailure) {
            cleanup = CompletableFuture.failedFuture(cleanupFailure);
        }
        PlayerProfile savedProfile = Objects.requireNonNull(saved, "saved profile");
        cleanup.whenComplete((ignored, cleanupFailure) ->
                completeSuccessfulSave(entry, savedProfile, cleanupFailure, result));
    }

    private void recordRecovery(
            SessionEntry entry,
            PlayerProfile profile,
            int attempt,
            Throwable saveFailure,
            CompletableFuture<PlayerSessionSnapshot> result) {
        String saveDetail = "save failed after " + attempt + " attempt(s): " + failureMessage(saveFailure);
        synchronized (sessions) {
            SessionEntry current = sessions.get(entry.playerId);
            if (current != entry || current.state != PlayerSessionState.SAVING) {
                result.complete(closed(
                        entry.playerId, entry.token, entry.contentRevision, "late save ignored"));
                return;
            }
            current.detail = saveDetail + "; writing recovery journal";
        }

        CompletionStage<Void> recovery;
        try {
            recovery = Objects.requireNonNull(
                    recoveryStore.write(new PlayerProfileRecoveryRecord(
                            profile, clock.instant(), failureMessage(saveFailure))),
                    "recovery write stage");
        } catch (RuntimeException recoveryFailure) {
            recovery = CompletableFuture.failedFuture(recoveryFailure);
        }
        recovery.whenComplete((ignored, recoveryFailure) -> {
            PlayerSessionSnapshot completed;
            synchronized (sessions) {
                SessionEntry current = sessions.get(entry.playerId);
                if (current != entry || !current.token.equals(entry.token)) {
                    completed = closed(
                            entry.playerId, entry.token, entry.contentRevision, "late recovery write ignored");
                } else {
                    current.state = PlayerSessionState.SAVE_RETRY_PENDING;
                    current.detail = recoveryFailure == null
                            ? saveDetail + "; durable recovery recorded"
                            : saveDetail + "; RECOVERY WRITE FAILED: " + failureMessage(recoveryFailure);
                    current.inFlightSave = null;
                    completed = current.snapshot();
                }
            }
            result.complete(completed);
        });
    }

    private void completeSuccessfulSave(
            SessionEntry entry,
            PlayerProfile saved,
            Throwable cleanupFailure,
            CompletableFuture<PlayerSessionSnapshot> result) {
        PlayerSessionSnapshot completed;
        synchronized (sessions) {
            SessionEntry current = sessions.get(entry.playerId);
            if (current != entry || !current.token.equals(entry.token)) {
                completed = closed(
                        entry.playerId, entry.token, entry.contentRevision, "late save ignored");
            } else {
                current.profile = saved;
                current.dirtyComponents.clear();
                current.inFlightSave = null;
                String cleanupDetail = cleanupFailure == null
                        ? ""
                        : "; recovery cleanup deferred: " + failureMessage(cleanupFailure);
                if (current.closeRequested) {
                    sessions.remove(current.playerId);
                    completed = closed(
                            current.playerId,
                            current.token,
                            current.contentRevision,
                            "saved revision " + current.profile.revision() + cleanupDetail);
                } else {
                    current.state = PlayerSessionState.ACTIVE;
                    current.detail = "active; saved revision " + current.profile.revision() + cleanupDetail;
                    completed = current.snapshot();
                }
            }
        }
        result.complete(completed);
    }

    private CompletionStage<PlayerProfile> loadWithRecovery(
            UUID playerId, String lastKnownName, java.time.Instant now) {
        return recoveryStore.load(playerId).thenCompose(recovery ->
                store.loadOrCreate(playerId, lastKnownName, now).thenCompose(databaseProfile -> {
                    if (recovery.isEmpty()) {
                        return CompletableFuture.completedFuture(databaseProfile);
                    }
                    PlayerProfile recovered = recovery.orElseThrow().profile();
                    if (!recovered.playerId().equals(playerId)) {
                        return CompletableFuture.failedFuture(
                                new IllegalStateException("Recovery record belongs to another player"));
                    }
                    if (databaseProfile.revision() == recovered.revision()) {
                        PlayerProfile replay = recovered.seenAs(lastKnownName, now);
                        return store.save(replay).thenCompose(savedProfile ->
                                recoveryStore.delete(playerId).thenApply(ignored -> savedProfile));
                    }
                    if (databaseProfile.revision() > recovered.revision()
                            && sameDomainState(databaseProfile, recovered)) {
                        return recoveryStore.delete(playerId).thenApply(ignored -> databaseProfile);
                    }
                    return CompletableFuture.failedFuture(new IllegalStateException(
                            "Recovery conflict at database revision " + databaseProfile.revision()
                                    + " and journal revision " + recovered.revision()));
                }));
    }

    private static boolean sameDomainState(PlayerProfile first, PlayerProfile second) {
        return first.schemaVersion() == second.schemaVersion()
                && first.classId().equals(second.classId())
                && first.selectedLoadoutId().equals(second.selectedLoadoutId())
                && first.respawnPointId().equals(second.respawnPointId())
                && first.settings().equals(second.settings());
    }

    private PlayerSessionSnapshot completeLoad(
            SessionEntry loading, PlayerProfile profile, Throwable failure) {
        synchronized (sessions) {
            SessionEntry current = sessions.get(loading.playerId);
            if (current != loading || !current.token.equals(loading.token)) {
                return closed(
                        loading.playerId, loading.token, loading.contentRevision, "late load ignored");
            }
            if (failure == null) {
                current.profile = Objects.requireNonNull(profile, "profile");
                current.state = PlayerSessionState.ACTIVE;
                current.detail = "active";
            } else {
                current.state = PlayerSessionState.LOAD_FAILED;
                current.detail = failureMessage(failure);
            }
            return current.snapshot();
        }
    }

    private SessionEntry requireCurrent(UUID playerId, PlayerSessionToken token) {
        SessionEntry entry = sessions.get(playerId);
        if (entry == null || !entry.token.equals(token)) {
            throw new IllegalStateException("No current session for token " + token.value());
        }
        return entry;
    }

    private static void validateMutation(
            PlayerProfile previous,
            PlayerProfile updated,
            Set<PlayerProfileComponent> declaredComponents) {
        if (!updated.playerId().equals(previous.playerId())) {
            throw new IllegalArgumentException("A profile mutation cannot change player UUID");
        }
        if (!updated.createdAt().equals(previous.createdAt())) {
            throw new IllegalArgumentException("A profile mutation cannot change creation time");
        }
        if (updated.revision() != previous.revision()) {
            throw new IllegalArgumentException("A profile mutation cannot change persistence revision");
        }
        requireDeclared(
                previous.schemaVersion() != updated.schemaVersion()
                        || !previous.lastKnownName().equals(updated.lastKnownName())
                        || !previous.lastSeenAt().equals(updated.lastSeenAt()),
                PlayerProfileComponent.IDENTITY,
                declaredComponents);
        requireDeclared(
                !previous.classId().equals(updated.classId()),
                PlayerProfileComponent.CHARACTER_CLASS,
                declaredComponents);
        requireDeclared(
                !previous.selectedLoadoutId().equals(updated.selectedLoadoutId()),
                PlayerProfileComponent.LOADOUT,
                declaredComponents);
        requireDeclared(
                !previous.respawnPointId().equals(updated.respawnPointId()),
                PlayerProfileComponent.RESPAWN,
                declaredComponents);
        requireDeclared(
                !previous.settings().equals(updated.settings()),
                PlayerProfileComponent.SETTINGS,
                declaredComponents);
    }

    private static void requireDeclared(
            boolean changed,
            PlayerProfileComponent required,
            Set<PlayerProfileComponent> declaredComponents) {
        if (changed && !declaredComponents.contains(required)) {
            throw new IllegalArgumentException(
                    "Profile mutation changed undeclared component " + required);
        }
    }

    private PlayerSessionToken nextToken() {
        return new PlayerSessionToken(tokens.incrementAndGet());
    }

    private static PlayerSessionSnapshot snapshot(
            UUID playerId,
            PlayerSessionToken token,
            PlayerSessionState state,
            PlayerProfile profile,
            Set<PlayerProfileComponent> dirtyComponents,
            long contentRevision,
            String detail) {
        return new PlayerSessionSnapshot(
                playerId,
                token,
                state,
                Optional.ofNullable(profile),
                dirtyComponents,
                contentRevision,
                detail);
    }

    private static PlayerSessionSnapshot closed(
            UUID playerId, PlayerSessionToken token, long contentRevision, String detail) {
        return snapshot(
                playerId,
                token,
                PlayerSessionState.CLOSED,
                null,
                Set.of(),
                contentRevision,
                detail);
    }

    private static String failureMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message;
    }

    private static final class SessionEntry {
        private final UUID playerId;
        private final PlayerSessionToken token;
        private final long contentRevision;
        private final EnumSet<PlayerProfileComponent> dirtyComponents =
                EnumSet.noneOf(PlayerProfileComponent.class);
        private PlayerSessionState state = PlayerSessionState.LOADING;
        private PlayerProfile profile;
        private String detail = "loading profile";
        private boolean closeRequested;
        private CompletableFuture<PlayerSessionSnapshot> inFlightSave;

        private SessionEntry(UUID playerId, PlayerSessionToken token, long contentRevision) {
            this.playerId = playerId;
            this.token = token;
            this.contentRevision = contentRevision;
        }

        private PlayerSessionSnapshot snapshot() {
            return PlayerSessionManager.snapshot(
                    playerId, token, state, profile, dirtyComponents, contentRevision, detail);
        }
    }
}
