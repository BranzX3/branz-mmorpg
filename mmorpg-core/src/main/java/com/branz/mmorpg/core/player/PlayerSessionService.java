package com.branz.mmorpg.core.player;

import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.lifeskill.LifeSkillProfile;
import com.branz.mmorpg.api.lifeskill.LifeSkillQuery;
import com.branz.mmorpg.api.player.DuplicateLoginPolicy;
import com.branz.mmorpg.api.player.PlayerProfile;
import com.branz.mmorpg.api.player.PlayerProfileRepository;
import com.branz.mmorpg.api.player.PlayerSession;
import com.branz.mmorpg.api.player.SessionState;
import com.branz.mmorpg.api.player.SessionToken;
import com.branz.mmorpg.api.runtime.GameClock;
import com.branz.mmorpg.api.runtime.Scheduler;
import com.branz.mmorpg.core.player.RuntimePlayerSession.DirtyComponent;
import com.branz.mmorpg.core.service.AbstractService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Owns the lifecycle of every runtime player session.
 *
 * <p>Invariants this class exists to hold:
 *
 * <ul>
 *   <li>At most one live session per UUID. Login resolves a duplicate through
 *       {@link DuplicateLoginPolicy} rather than letting two sessions write.</li>
 *   <li>A failed load never becomes a blank profile. The session goes
 *       {@link SessionState#LOAD_FAILED} and gameplay stays disabled.</li>
 *   <li>Session tokens increase per player, so a late async callback from a
 *       previous life can be recognised and dropped.</li>
 *   <li>No Bukkit type is stored here, and nothing is retained after logout.</li>
 * </ul>
 */
public final class PlayerSessionService extends AbstractService implements LifeSkillQuery {

    /** How many times a logout save is retried before it becomes a pending record. */
    public static final int SAVE_ATTEMPTS = 3;

    private final PlayerProfileRepository repository;
    private final Scheduler scheduler;
    private final GameClock clock;
    private final LongSupplier contentRevision;
    private final DuplicateLoginPolicy duplicateLoginPolicy;
<<<<<<< HEAD
    private final PendingSessionSaveStore pendingSaveStore;
    private final int saveAttempts;
=======
>>>>>>> parent of 14f4881 (complete mmo task)

    private final Map<UUID, RuntimePlayerSession> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastSequence = new ConcurrentHashMap<>();
    private final Map<UUID, PlayerProfile> pendingSaves = new ConcurrentHashMap<>();

    public PlayerSessionService(PlayerProfileRepository repository,
                                Scheduler scheduler,
                                GameClock clock,
                                LongSupplier contentRevision,
                                DuplicateLoginPolicy duplicateLoginPolicy) {
<<<<<<< HEAD
        this(repository, scheduler, clock, contentRevision, duplicateLoginPolicy,
                new InMemoryPendingSessionSaveStore(), SAVE_ATTEMPTS);
    }

    public PlayerSessionService(PlayerProfileRepository repository,
                                Scheduler scheduler,
                                GameClock clock,
                                LongSupplier contentRevision,
                                DuplicateLoginPolicy duplicateLoginPolicy,
                                PendingSessionSaveStore pendingSaveStore) {
        this(repository, scheduler, clock, contentRevision, duplicateLoginPolicy,
                pendingSaveStore, SAVE_ATTEMPTS);
    }

    public PlayerSessionService(PlayerProfileRepository repository,
                                Scheduler scheduler,
                                GameClock clock,
                                LongSupplier contentRevision,
                                DuplicateLoginPolicy duplicateLoginPolicy,
                                PendingSessionSaveStore pendingSaveStore,
                                int saveAttempts) {
=======
>>>>>>> parent of 14f4881 (complete mmo task)
        super("player-session");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.contentRevision = Objects.requireNonNull(contentRevision, "contentRevision");
        this.duplicateLoginPolicy = Objects.requireNonNull(duplicateLoginPolicy, "duplicateLoginPolicy");
<<<<<<< HEAD
        this.pendingSaveStore = Objects.requireNonNull(pendingSaveStore, "pendingSaveStore");
        if (saveAttempts < 1) {
            throw new IllegalArgumentException("saveAttempts must be at least 1");
        }
        this.saveAttempts = saveAttempts;
=======
>>>>>>> parent of 14f4881 (complete mmo task)
    }

    @Override
    protected void onStart() {
        sessions.clear();
        pendingSaves.clear();
    }

    @Override
    protected void onStop() {
        // Shutdown drains what is dirty; it does not discard it.
        flushAll();
        sessions.clear();
    }

    /**
     * Begins a login. The profile load runs off the tick thread; the returned
     * future completes with an ACTIVE session, or completes exceptionally when
     * the load failed or the login was rejected.
     */
    public CompletableFuture<PlayerSession> login(UUID playerId, String name) {
        Objects.requireNonNull(playerId, "playerId");

        RuntimePlayerSession session;
        try {
            session = openSession(playerId);
        } catch (MMOException rejected) {
            return CompletableFuture.failedFuture(rejected);
        }

        return scheduler.async(() -> {
            PlayerProfile profile = repository.loadOrCreate(playerId, name);
            LifeSkillProfile lifeSkills = repository.loadLifeSkills(playerId);
            return new Loaded(profile, lifeSkills);
        }).handle((loaded, failure) -> {
            if (!isLive(session.token())) {
                // Superseded or logged out while loading. Drop the result rather
                // than resurrect a session the server has already moved past.
                throw new MMOException(ErrorCode.SERVICE_UNAVAILABLE,
                        "session " + session.token() + " was superseded during load");
            }
            if (failure != null) {
                session.loadFailed(rootMessage(failure));
                throw new MMOException(ErrorCode.PROFILE_LOAD_FAILED,
                        "profile load failed for " + playerId, failure);
            }
            PlayerProfile named = loaded.profile()
                    .withName(name)
                    .withLastSeenAt(clock.now());
            session.loaded(named, loaded.lifeSkills());
            session.markDirty(DirtyComponent.PROFILE);
            return (PlayerSession) session;
        });
    }

    /**
     * Saves and closes the session. Completes once the session is CLOSED or has
     * become a pending save record; it never leaves a live session behind.
     */
    public CompletableFuture<Void> logout(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        RuntimePlayerSession session = sessions.remove(playerId);
        if (session == null) {
            return CompletableFuture.completedFuture(null);
        }
        if (session.state() == SessionState.LOAD_FAILED) {
            // Nothing was ever loaded, so there is nothing to write.
            session.closed();
            return CompletableFuture.completedFuture(null);
        }
        if (session.state().terminal()) {
            return CompletableFuture.completedFuture(null);
        }
        return scheduler.async(() -> {
            save(session, saveAttempts);
            return null;
        }).thenApply(ignored -> null);
    }

    /** Whether {@code token} still identifies the live session for its player. */
    public boolean isLive(SessionToken token) {
        if (token == null) {
            return false;
        }
        RuntimePlayerSession current = sessions.get(token.playerId());
        return current != null && current.token().equals(token) && !current.state().terminal();
    }

    public Optional<PlayerSession> session(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    /** The session, or a failure — the call gameplay code should use. */
    public RuntimePlayerSession requirePlayable(UUID playerId) {
        RuntimePlayerSession session = sessions.get(playerId);
        if (session == null || !session.state().playable()) {
            throw new MMOException(ErrorCode.PROFILE_LOAD_FAILED,
                    "no playable session for " + playerId
                            + (session == null ? "" : " (" + session.state() + ")"));
        }
        return session;
    }

    @Override
    public LifeSkillProfile profile(UUID playerId) {
        return requirePlayable(playerId).lifeSkills();
    }

    /** Saves every dirty session. Used by the periodic timer and by shutdown. */
    public int flushAll() {
        int saved = 0;
        for (RuntimePlayerSession session : List.copyOf(sessions.values())) {
            if (session.hasUnsavedChanges() && session.state() == SessionState.ACTIVE) {
                save(session, 1);
                saved++;
            }
        }
        return saved;
    }

    /** Profiles whose logout save exhausted its retries and await recovery. */
    public Map<UUID, PlayerProfile> pendingSaves() {
        return Map.copyOf(pendingSaves);
    }

    /** Retries pending saves. Returns the players that were recovered. */
    public List<UUID> retryPendingSaves() {
        List<UUID> recovered = new ArrayList<>();
        for (Map.Entry<UUID, PlayerProfile> entry : Map.copyOf(pendingSaves).entrySet()) {
            try {
                repository.saveProfile(entry.getValue());
                pendingSaves.remove(entry.getKey());
                recovered.add(entry.getKey());
            } catch (RuntimeException stillFailing) {
                // Left in the map for the next attempt; never dropped.
            }
        }
        return recovered;
    }

    private RuntimePlayerSession openSession(UUID playerId) {
        RuntimePlayerSession opened = sessions.compute(playerId, (id, existing) -> {
            if (existing != null && !existing.state().terminal()) {
                if (duplicateLoginPolicy == DuplicateLoginPolicy.REJECT_NEW) {
                    throw new MMOException(ErrorCode.SERVICE_UNAVAILABLE,
                            "a session for " + id + " is already " + existing.state());
                }
                existing.conflicted();
            }
            long sequence = lastSequence.merge(id, 1L, Long::sum);
            return new RuntimePlayerSession(new SessionToken(id, sequence), contentRevision.getAsLong());
        });
        opened.beginLoading();
        return opened;
    }

    private void save(RuntimePlayerSession session, int attempts) {
        Set<DirtyComponent> captured = session.dirtyComponents();
        boolean resuming = session.state() == SessionState.ACTIVE && sessions.containsKey(session.playerId());
        PlayerProfile profile;
        try {
            profile = session.profile().withLastSeenAt(clock.now());
        } catch (MMOException notLoaded) {
            session.closed();
            return;
        }
        session.beginSaving();
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= Math.max(1, attempts); attempt++) {
            try {
<<<<<<< HEAD
                repository.saveSession(profile, lifeSkills);
                session.acceptPersistedProfileRevision(profile.revision());
=======
                repository.saveProfile(profile);
>>>>>>> parent of 14f4881 (complete mmo task)
                session.clearDirty(captured);
                if (resuming) {
                    session.savedAndResumed();
                } else {
                    session.closed();
                }
                return;
            } catch (RuntimeException failure) {
                lastFailure = failure;
            }
        }
        // Retries exhausted: keep a durable record instead of losing the write.
        pendingSaves.put(session.playerId(), profile);
        session.saveRetryPending();
        if (!resuming) {
            session.closed();
        }
        throw new MMOException(ErrorCode.STORAGE_FAILURE,
                "save failed for " + session.token() + " after " + attempts + " attempt(s); "
                        + "profile retained as a pending save", lastFailure);
    }

    private static String rootMessage(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }

    private record Loaded(PlayerProfile profile, LifeSkillProfile lifeSkills) {
    }
}
