package com.branz.mmorpg.core.skill;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.runtime.GameClock;
import com.branz.mmorpg.api.skill.SkillCastSnapshot;
import com.branz.mmorpg.api.skill.SkillCaster;
import com.branz.mmorpg.api.skill.SkillDefinition;
import com.branz.mmorpg.api.skill.SkillEffectNode;
import com.branz.mmorpg.api.skill.SkillState;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Authoritative pure-Java state machine for skill casts and cooldown groups. */
public final class SkillExecutionEngine {

    private final GameClock clock;
    private final EffectExecutor effects;
    private final Map<UUID, RuntimeCast> casts = new HashMap<>();
    private final Map<UUID, UUID> activeByCaster = new HashMap<>();
    private final Map<CooldownKey, Long> cooldownReadyAt = new HashMap<>();

    public SkillExecutionEngine(GameClock clock, EffectExecutor effects) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.effects = Objects.requireNonNull(effects, "effects");
    }

    public BeginResult begin(SkillDefinition definition, SkillCaster caster, long contentRevision) {
        return begin(definition, caster, null, contentRevision);
    }

    public BeginResult begin(SkillDefinition definition, SkillCaster caster,
                             UUID targetId, long contentRevision) {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(caster, "caster");
        long now = clock.monotonicNanos();
        if (!caster.alive()) {
            return BeginResult.rejected(Rejection.CASTER_DEAD);
        }
        if (caster.stunned()) {
            return BeginResult.rejected(Rejection.STUNNED);
        }
        if (caster.silenced()) {
            return BeginResult.rejected(Rejection.SILENCED);
        }
        if (activeByCaster.containsKey(caster.id())) {
            return BeginResult.rejected(Rejection.ALREADY_CASTING);
        }
        CooldownKey cooldownKey = new CooldownKey(caster.id(), definition.cooldownGroup());
        if (cooldownReadyAt.getOrDefault(cooldownKey, 0L) > now) {
            return BeginResult.rejected(Rejection.COOLDOWN);
        }
        if (!caster.spend(definition.costs())) {
            return BeginResult.rejected(Rejection.INSUFFICIENT_RESOURCE);
        }

        SkillState initial = definition.castMillis() > 0 ? SkillState.CASTING : SkillState.ACTIVE;
        RuntimeCast cast = new RuntimeCast(UUID.randomUUID(), caster, targetId, definition,
                contentRevision, initial, now);
        casts.put(cast.id, cast);
        activeByCaster.put(caster.id(), cast.id);
        if (initial == SkillState.ACTIVE) {
            deliver(cast);
        }
        return BeginResult.started(cast.snapshot());
    }

    /** Advances a cast through every phase whose duration has elapsed. */
    public SkillCastSnapshot advance(UUID castId) {
        RuntimeCast cast = requireCast(castId);
        long now = clock.monotonicNanos();
        boolean moved;
        do {
            moved = switch (cast.state) {
                case CASTING -> elapsed(cast, now, cast.definition.castMillis())
                        && transition(cast, SkillState.ACTIVE, now);
                case ACTIVE -> elapsed(cast, now, cast.definition.activeMillis())
                        && transition(cast, SkillState.RECOVERY, now);
                case RECOVERY -> elapsed(cast, now, cast.definition.recoveryMillis())
                        && enterCooldown(cast, now);
                case COOLDOWN -> cooldownExpired(cast, now) && complete(cast, now);
                case COMPLETE, INTERRUPTED -> false;
            };
        } while (moved);
        return cast.snapshot();
    }

    public List<SkillCastSnapshot> advanceAll() {
        List<SkillCastSnapshot> updated = new ArrayList<>();
        for (UUID castId : List.copyOf(casts.keySet())) {
            updated.add(advance(castId));
        }
        return List.copyOf(updated);
    }

    public SkillCastSnapshot interrupt(UUID castId, String reason) {
        RuntimeCast cast = requireCast(castId);
        if (cast.state != SkillState.CASTING && cast.state != SkillState.ACTIVE) {
            return cast.snapshot();
        }
        cast.caster.refund(cast.definition.costs(), cast.definition.interruptRefundFraction());
        cast.state = SkillState.INTERRUPTED;
        cast.phaseStarted = clock.monotonicNanos();
        cast.interruptionReason = reason == null ? "interrupted" : reason;
        activeByCaster.remove(cast.caster.id(), cast.id);
        startCooldown(cast, cast.phaseStarted);
        return cast.snapshot();
    }

    public int cancelCaster(UUID casterId, String reason) {
        UUID castId = activeByCaster.get(casterId);
        if (castId == null) {
            return 0;
        }
        interrupt(castId, reason);
        return 1;
    }

    public Optional<SkillCastSnapshot> cast(UUID castId) {
        RuntimeCast cast = casts.get(castId);
        return cast == null ? Optional.empty() : Optional.of(cast.snapshot());
    }

    public Optional<SkillCastSnapshot> activeCast(UUID casterId) {
        UUID castId = activeByCaster.get(casterId);
        return castId == null ? Optional.empty() : cast(castId);
    }

    public long cooldownRemainingMillis(UUID casterId, String group) {
        long remaining = cooldownReadyAt.getOrDefault(new CooldownKey(casterId, group), 0L)
                - clock.monotonicNanos();
        return Math.max(0L, remaining / 1_000_000L);
    }

    public void forget(UUID castId) {
        RuntimeCast removed = casts.remove(castId);
        if (removed != null) {
            activeByCaster.remove(removed.caster.id(), castId);
        }
    }

    private boolean transition(RuntimeCast cast, SkillState next, long now) {
        cast.state = next;
        cast.phaseStarted = now;
        if (next == SkillState.ACTIVE) {
            deliver(cast);
        }
        return true;
    }

    private void deliver(RuntimeCast cast) {
        if (cast.effectDelivered) {
            return;
        }
        cast.effectDelivered = true;
        effects.execute(cast.snapshot(), cast.definition,
                cast.definition.effects().get(cast.definition.rootEffect()));
    }

    private boolean enterCooldown(RuntimeCast cast, long now) {
        cast.state = SkillState.COOLDOWN;
        cast.phaseStarted = now;
        activeByCaster.remove(cast.caster.id(), cast.id);
        startCooldown(cast, now);
        return true;
    }

    private void startCooldown(RuntimeCast cast, long now) {
        double recovery = Math.max(0.0, Math.min(0.35, cast.caster.cooldownRecovery()));
        long duration = Math.max(1L,
                Math.round(cast.definition.cooldownMillis() * (1.0 - recovery)));
        cast.cooldownReady = now + duration * 1_000_000L;
        cooldownReadyAt.put(new CooldownKey(cast.caster.id(),
                cast.definition.cooldownGroup()), cast.cooldownReady);
    }

    private boolean cooldownExpired(RuntimeCast cast, long now) {
        return now >= cast.cooldownReady;
    }

    private boolean complete(RuntimeCast cast, long now) {
        cast.state = SkillState.COMPLETE;
        cast.phaseStarted = now;
        return true;
    }

    private static boolean elapsed(RuntimeCast cast, long now, long millis) {
        return now - cast.phaseStarted >= millis * 1_000_000L;
    }

    private RuntimeCast requireCast(UUID castId) {
        RuntimeCast cast = casts.get(Objects.requireNonNull(castId, "castId"));
        if (cast == null) {
            throw new IllegalArgumentException("unknown cast " + castId);
        }
        return cast;
    }

    public enum Rejection {
        CASTER_DEAD,
        STUNNED,
        SILENCED,
        ALREADY_CASTING,
        COOLDOWN,
        INSUFFICIENT_RESOURCE
    }

    public record BeginResult(SkillCastSnapshot cast, Rejection rejection) {
        static BeginResult started(SkillCastSnapshot cast) {
            return new BeginResult(cast, null);
        }

        static BeginResult rejected(Rejection rejection) {
            return new BeginResult(null, rejection);
        }

        public boolean started() {
            return cast != null;
        }
    }

    @FunctionalInterface
    public interface EffectExecutor {
        void execute(SkillCastSnapshot cast, SkillDefinition definition, SkillEffectNode root);
    }

    private static final class RuntimeCast {
        private final UUID id;
        private final SkillCaster caster;
        private final UUID targetId;
        private final SkillDefinition definition;
        private final long contentRevision;
        private SkillState state;
        private long phaseStarted;
        private long cooldownReady;
        private boolean effectDelivered;
        private String interruptionReason;

        private RuntimeCast(UUID id, SkillCaster caster, UUID targetId, SkillDefinition definition,
                            long contentRevision, SkillState state, long phaseStarted) {
            this.id = id;
            this.caster = caster;
            this.targetId = targetId;
            this.definition = definition;
            this.contentRevision = contentRevision;
            this.state = state;
            this.phaseStarted = phaseStarted;
        }

        private SkillCastSnapshot snapshot() {
            return new SkillCastSnapshot(id, caster.id(), targetId, definition.id(), contentRevision,
                    state, phaseStarted, interruptionReason);
        }
    }

    private record CooldownKey(UUID casterId, String group) {
    }
}
