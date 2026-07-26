package com.branz.mmorpg.core.player;

import com.branz.mmorpg.api.player.PlayerProfile;
import com.branz.mmorpg.api.player.PlayerProfileStore;
import com.branz.mmorpg.api.player.PlayerSessionService;
import com.branz.mmorpg.api.player.PlayerSessionSnapshot;
import com.branz.mmorpg.api.player.PlayerSessionState;
import com.branz.mmorpg.api.player.PlayerSessionToken;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicLong;

public final class PlayerSessionManager implements PlayerSessionService {
    private final PlayerProfileStore store;
    private final Clock clock;
    private final AtomicLong tokens = new AtomicLong();
    private final Map<UUID, PlayerSessionSnapshot> sessions = new HashMap<>();

    public PlayerSessionManager(PlayerProfileStore store) {
        this(store, Clock.systemUTC());
    }

    public PlayerSessionManager(PlayerProfileStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public CompletionStage<PlayerSessionSnapshot> open(
            UUID playerId, String lastKnownName, long contentRevision) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(lastKnownName, "lastKnownName");
        if (lastKnownName.isBlank()) {
            throw new IllegalArgumentException("Player name must not be blank");
        }

        PlayerSessionSnapshot loading;
        synchronized (sessions) {
            PlayerSessionSnapshot existing = sessions.get(playerId);
            if (existing != null && existing.state() != PlayerSessionState.CLOSED) {
                PlayerSessionToken rejectedToken = nextToken();
                return CompletableFuture.completedFuture(new PlayerSessionSnapshot(
                        playerId,
                        rejectedToken,
                        PlayerSessionState.CONFLICTED,
                        Optional.empty(),
                        contentRevision,
                        "an existing session is " + existing.state()));
            }
            loading = new PlayerSessionSnapshot(
                    playerId,
                    nextToken(),
                    PlayerSessionState.LOADING,
                    Optional.empty(),
                    contentRevision,
                    "loading profile");
            sessions.put(playerId, loading);
        }

        return store.loadOrCreate(playerId, lastKnownName, clock.instant())
                .handle((profile, failure) -> completeLoad(loading, profile, failure));
    }

    @Override
    public CompletionStage<PlayerSessionSnapshot> close(UUID playerId, PlayerSessionToken token) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(token, "token");

        PlayerSessionSnapshot saving;
        synchronized (sessions) {
            PlayerSessionSnapshot current = sessions.get(playerId);
            if (current == null || !current.token().equals(token)) {
                return CompletableFuture.completedFuture(closed(playerId, token, 0, "stale close ignored"));
            }
            if (current.state() == PlayerSessionState.LOADING
                    || current.state() == PlayerSessionState.LOAD_FAILED
                    || current.profile().isEmpty()) {
                sessions.remove(playerId);
                return CompletableFuture.completedFuture(
                        closed(playerId, token, current.contentRevision(), "closed without save"));
            }
            if (current.state() != PlayerSessionState.ACTIVE
                    && current.state() != PlayerSessionState.SAVE_RETRY_PENDING) {
                return CompletableFuture.completedFuture(current);
            }
            PlayerProfile touched = current.profile().orElseThrow().seenAs(
                    current.profile().orElseThrow().lastKnownName(), clock.instant());
            saving = new PlayerSessionSnapshot(
                    playerId,
                    token,
                    PlayerSessionState.SAVING,
                    Optional.of(touched),
                    current.contentRevision(),
                    "saving profile");
            sessions.put(playerId, saving);
        }

        return store.save(saving.profile().orElseThrow())
                .handle((profile, failure) -> completeSave(saving, profile, failure));
    }

    @Override
    public Optional<PlayerSessionSnapshot> snapshot(UUID playerId) {
        synchronized (sessions) {
            return Optional.ofNullable(sessions.get(playerId));
        }
    }

    @Override
    public int activeSessionCount() {
        synchronized (sessions) {
            return (int) sessions.values().stream()
                    .filter(session -> session.state() == PlayerSessionState.ACTIVE)
                    .count();
        }
    }

    private PlayerSessionSnapshot completeLoad(
            PlayerSessionSnapshot loading, PlayerProfile profile, Throwable failure) {
        synchronized (sessions) {
            PlayerSessionSnapshot current = sessions.get(loading.playerId());
            if (current == null || !current.token().equals(loading.token())) {
                return closed(
                        loading.playerId(),
                        loading.token(),
                        loading.contentRevision(),
                        "late load ignored");
            }
            PlayerSessionSnapshot completed;
            if (failure == null) {
                completed = new PlayerSessionSnapshot(
                        loading.playerId(),
                        loading.token(),
                        PlayerSessionState.ACTIVE,
                        Optional.of(Objects.requireNonNull(profile, "profile")),
                        loading.contentRevision(),
                        "active");
            } else {
                completed = new PlayerSessionSnapshot(
                        loading.playerId(),
                        loading.token(),
                        PlayerSessionState.LOAD_FAILED,
                        Optional.empty(),
                        loading.contentRevision(),
                        failureMessage(failure));
            }
            sessions.put(loading.playerId(), completed);
            return completed;
        }
    }

    private PlayerSessionSnapshot completeSave(
            PlayerSessionSnapshot saving, PlayerProfile profile, Throwable failure) {
        synchronized (sessions) {
            PlayerSessionSnapshot current = sessions.get(saving.playerId());
            if (current == null || !current.token().equals(saving.token())) {
                return closed(
                        saving.playerId(),
                        saving.token(),
                        saving.contentRevision(),
                        "late save ignored");
            }
            if (failure != null) {
                PlayerSessionSnapshot pending = new PlayerSessionSnapshot(
                        saving.playerId(),
                        saving.token(),
                        PlayerSessionState.SAVE_RETRY_PENDING,
                        saving.profile(),
                        saving.contentRevision(),
                        failureMessage(failure));
                sessions.put(saving.playerId(), pending);
                return pending;
            }
            sessions.remove(saving.playerId());
            return closed(
                    saving.playerId(),
                    saving.token(),
                    saving.contentRevision(),
                    "saved revision " + Objects.requireNonNull(profile, "profile").revision());
        }
    }

    private PlayerSessionToken nextToken() {
        return new PlayerSessionToken(tokens.incrementAndGet());
    }

    private static PlayerSessionSnapshot closed(
            UUID playerId, PlayerSessionToken token, long contentRevision, String detail) {
        return new PlayerSessionSnapshot(
                playerId,
                token,
                PlayerSessionState.CLOSED,
                Optional.empty(),
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
}
