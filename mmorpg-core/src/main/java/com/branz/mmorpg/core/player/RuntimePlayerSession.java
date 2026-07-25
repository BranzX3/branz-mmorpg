package com.branz.mmorpg.core.player;

import com.branz.mmorpg.api.error.ErrorCode;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.lifeskill.LifeSkillProfile;
import com.branz.mmorpg.api.player.PlayerProfile;
import com.branz.mmorpg.api.player.PlayerSession;
import com.branz.mmorpg.api.player.SessionState;
import com.branz.mmorpg.api.player.SessionToken;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mutable session object behind the immutable {@link PlayerSession} view.
 *
 * <p>The profile itself is never mutated in place: an update swaps in a new
 * immutable snapshot. That is what lets another thread read
 * {@link #profile()} while gameplay code is editing it, without a lock and
 * without observing a half-applied change.
 *
 * <p>Dirty components exist so a periodic save writes only what changed. Saving
 * everything on a timer is what turns a few hundred players into a database
 * problem.
 */
public final class RuntimePlayerSession implements PlayerSession {

    /** Parts of a session that can independently need saving. */
    public enum DirtyComponent {
        PROFILE,
        LIFE_SKILL
    }

    private final SessionToken token;
    private final long contentRevision;
    private final AtomicReference<SessionState> state = new AtomicReference<>(SessionState.ABSENT);
    private final AtomicReference<PlayerProfile> profile = new AtomicReference<>();
    private final AtomicReference<LifeSkillProfile> lifeSkills = new AtomicReference<>();
    private final Set<DirtyComponent> dirty = EnumSet.noneOf(DirtyComponent.class);
    private volatile String loadFailure;

    public RuntimePlayerSession(SessionToken token, long contentRevision) {
        this.token = Objects.requireNonNull(token, "token");
        this.contentRevision = contentRevision;
    }

    @Override
    public UUID playerId() {
        return token.playerId();
    }

    @Override
    public SessionToken token() {
        return token;
    }

    @Override
    public SessionState state() {
        return state.get();
    }

    @Override
    public PlayerProfile profile() {
        PlayerProfile current = profile.get();
        if (current == null) {
            throw new MMOException(ErrorCode.PROFILE_LOAD_FAILED,
                    "profile for " + token + " is not loaded"
                            + (loadFailure == null ? "" : ": " + loadFailure));
        }
        return current;
    }

    @Override
    public LifeSkillProfile lifeSkills() {
        LifeSkillProfile current = lifeSkills.get();
        if (current == null) {
            throw new MMOException(ErrorCode.PROFILE_LOAD_FAILED,
                    "life skills for " + token + " are not loaded");
        }
        return current;
    }

    @Override
    public long contentRevision() {
        return contentRevision;
    }

    void beginLoading() {
        transition(SessionState.LOADING);
    }

    void loaded(PlayerProfile loadedProfile, LifeSkillProfile loadedLifeSkills) {
        profile.set(Objects.requireNonNull(loadedProfile, "loadedProfile"));
        lifeSkills.set(Objects.requireNonNull(loadedLifeSkills, "loadedLifeSkills"));
        transition(SessionState.ACTIVE);
    }

    void loadFailed(String reason) {
        loadFailure = reason;
        transition(SessionState.LOAD_FAILED);
    }

    void beginSaving() {
        transition(SessionState.SAVING);
    }

    void savedAndResumed() {
        transition(SessionState.ACTIVE);
    }

    void saveRetryPending() {
        transition(SessionState.SAVE_RETRY_PENDING);
    }

    void closed() {
        transition(SessionState.CLOSED);
    }

    /**
     * Marks this session as superseded by a newer login. Always permitted from a
     * live state, and deliberately silent when the session is already terminal.
     */
    void conflicted() {
        state.getAndUpdate(current -> current.canTransitionTo(SessionState.CONFLICTED)
                ? SessionState.CONFLICTED
                : current);
    }

    /** Replaces the profile snapshot and marks it for the next save. */
    public void updateProfile(java.util.function.UnaryOperator<PlayerProfile> update) {
        Objects.requireNonNull(update, "update");
        requirePlayable();
        profile.updateAndGet(current -> Objects.requireNonNull(
                update.apply(current), "updated profile must not be null"));
        markDirty(DirtyComponent.PROFILE);
    }

    /** Replaces the Life Skill snapshot and marks it for the next save. */
    public void updateLifeSkills(java.util.function.UnaryOperator<LifeSkillProfile> update) {
        Objects.requireNonNull(update, "update");
        requirePlayable();
        lifeSkills.updateAndGet(current -> Objects.requireNonNull(
                update.apply(current), "updated life skills must not be null"));
        markDirty(DirtyComponent.LIFE_SKILL);
    }

    public void markDirty(DirtyComponent component) {
        synchronized (dirty) {
            dirty.add(component);
        }
    }

    public Set<DirtyComponent> dirtyComponents() {
        synchronized (dirty) {
            return EnumSet.copyOf(dirty.isEmpty() ? EnumSet.noneOf(DirtyComponent.class) : dirty);
        }
    }

    public boolean hasUnsavedChanges() {
        synchronized (dirty) {
            return !dirty.isEmpty();
        }
    }

    /**
     * Clears the components captured before a save began. Components dirtied
     * <em>during</em> the save survive, so a change made mid-flush is written by
     * the next save rather than lost.
     */
    void clearDirty(Set<DirtyComponent> saved) {
        synchronized (dirty) {
            dirty.removeAll(saved);
        }
    }

    private void requirePlayable() {
        SessionState current = state.get();
        if (!current.playable()) {
            throw new MMOException(ErrorCode.SERVICE_UNAVAILABLE,
                    "session " + token + " is " + current + " and refuses mutation");
        }
    }

    private void transition(SessionState target) {
        SessionState previous = state.getAndUpdate(
                current -> current.canTransitionTo(target) ? target : current);
        if (!previous.canTransitionTo(target)) {
            throw new MMOException(ErrorCode.SERVICE_LIFECYCLE,
                    "session " + token + " cannot go from " + previous + " to " + target);
        }
    }
}
