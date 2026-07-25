package com.branz.mmorpg.core.combat;

import com.branz.mmorpg.api.combat.CombatPolicy;
import com.branz.mmorpg.api.combat.Combatant;
import com.branz.mmorpg.api.combat.DamageRequest;
import com.branz.mmorpg.api.combat.DamageResult;
import com.branz.mmorpg.api.combat.RejectionReason;
import com.branz.mmorpg.api.event.EventBus;
import com.branz.mmorpg.api.runtime.GameClock;
import com.branz.mmorpg.api.runtime.RandomSource;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

/**
 * The authoritative damage pipeline.
 *
 * <pre>
 * intent -> eligibility -> target validation -> hit dedup -> power
 *        -> critical -> mitigation -> shields -> health -> events
 * </pre>
 *
 * <p>Every stage runs server-side on server-owned numbers. Nothing here reads a
 * client-supplied value, and the order is fixed: validation precedes any
 * mutation, so a rejected attempt cannot have already changed the world.
 *
 * <p>Platform-independent, so the whole engine runs in plain JUnit.
 */
public final class CombatEngine {

    private final CombatPolicy policy;
    private final GameClock clock;
    private final RandomSource random;
    private final EventBus events;
    private final Function<UUID, Combatant> combatants;
    private final LineOfSight lineOfSight;
    private final CombatStateTracker combatState;

    /** Hits already delivered per cast, so one cast cannot hit a target twice. */
    private final Map<UUID, Map<UUID, Integer>> hitsByCast = new HashMap<>();

    public CombatEngine(CombatPolicy policy,
                        GameClock clock,
                        RandomSource random,
                        EventBus events,
                        Function<UUID, Combatant> combatants,
                        LineOfSight lineOfSight) {
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.random = Objects.requireNonNull(random, "random");
        this.events = Objects.requireNonNull(events, "events");
        this.combatants = Objects.requireNonNull(combatants, "combatants");
        this.lineOfSight = Objects.requireNonNull(lineOfSight, "lineOfSight");
        this.combatState = new CombatStateTracker(policy);
    }

    public CombatStateTracker combatState() {
        return combatState;
    }

    /** Resolves one damage attempt end to end. */
    public DamageResult damage(DamageRequest request) {
        Objects.requireNonNull(request, "request");
        Instant now = clock.now();

        if (!request.validPower()) {
            return DamageResult.rejected(RejectionReason.INVALID_POWER);
        }

        Combatant target = combatants.apply(request.targetId());
        if (target == null) {
            return DamageResult.rejected(RejectionReason.TARGET_UNAVAILABLE);
        }
        Combatant attacker = request.environmental() ? null : combatants.apply(request.attackerId());
        if (!request.environmental() && attacker == null) {
            return DamageResult.rejected(RejectionReason.ATTACKER_UNAVAILABLE);
        }

        RejectionReason rejection = validate(request, attacker, target);
        if (rejection != null) {
            return DamageResult.rejected(rejection);
        }

        // Dedup is claimed only after validation passes, so a rejected attempt
        // does not burn the cast's one permitted hit on this target.
        if (!claimHit(request)) {
            return DamageResult.rejected(RejectionReason.DUPLICATE_HIT);
        }

        var attackerStats = attacker == null ? null : attacker.attributes();
        var targetStats = target.attributes();

        double raw = DamageCalculator.offensivePower(request.basePower(), request.type(), attackerStats);
        boolean critical = DamageCalculator.rollCritical(request.type(), attackerStats, random);
        double afterCrit = DamageCalculator.applyCritical(raw, critical, attackerStats);
        afterCrit *= DamageCalculator.pvpScale(
                attacker != null && attacker.playerControlled(), target.playerControlled(), policy);

        double afterMitigation = DamageCalculator.mitigate(afterCrit, request.type(), targetStats, policy);
        double mitigated = Math.max(0.0, afterCrit - afterMitigation);

        double absorbed = target.absorb(afterMitigation);
        double toHealth = Math.max(0.0, afterMitigation - absorbed);
        double healthBefore = target.currentHealth();
        double applied = target.applyHealthLoss(toHealth);
        boolean lethal = healthBefore > 0.0 && target.currentHealth() <= 0.0;

        DamageResult result = new DamageResult(null, raw, critical, afterCrit,
                mitigated, absorbed, applied, lethal);

        publishDamage(request, result, now);
        if (attacker != null) {
            enterCombat(attacker.id(), now);
        }
        enterCombat(target.id(), now);
        if (lethal) {
            onDeath(request, target, toHealth - applied, now);
        }
        return result;
    }

    /**
     * Ends a cast, releasing its dedup record.
     *
     * <p>Without this the map grows for the lifetime of the server, so every cast
     * must be closed when its effects are finished.
     */
    public void endCast(UUID castId) {
        hitsByCast.remove(castId);
    }

    public int activeCasts() {
        return hitsByCast.size();
    }

    /** Removes all combat state for a combatant. Called on logout and on death. */
    public void forget(UUID combatantId) {
        combatState.clear(combatantId);
    }

    private RejectionReason validate(DamageRequest request, Combatant attacker, Combatant target) {
        if (!target.alive()) {
            return RejectionReason.TARGET_DEAD;
        }
        if (target.invulnerable()) {
            return RejectionReason.TARGET_INVULNERABLE;
        }
        // Safe zone is checked before any mutation, never after.
        if (target.inSafeZone() || (attacker != null && attacker.inSafeZone())) {
            return RejectionReason.SAFE_ZONE;
        }
        if (attacker == null) {
            return null;
        }
        if (!attacker.alive()) {
            return RejectionReason.ATTACKER_UNAVAILABLE;
        }
        if (attacker.playerControlled() && target.playerControlled() && !policy.pvpEnabled()) {
            return RejectionReason.PVP_DISABLED;
        }
        if (!attacker.id().equals(target.id()) && attacker.allyOf(target) && !policy.friendlyFire()) {
            return RejectionReason.FRIENDLY_FIRE_DISABLED;
        }
        if (!attacker.position().sameWorld(target.position())) {
            return RejectionReason.DIFFERENT_WORLD;
        }
        if (request.range() > 0.0 && !attacker.position().within(target.position(), request.range())) {
            return RejectionReason.OUT_OF_RANGE;
        }
        if (request.requiresLineOfSight()
                && !lineOfSight.clear(attacker.position(), target.position())) {
            return RejectionReason.NO_LINE_OF_SIGHT;
        }
        return null;
    }

    private boolean claimHit(DamageRequest request) {
        Map<UUID, Integer> perTarget =
                hitsByCast.computeIfAbsent(request.castId(), key -> new HashMap<>());
        int already = perTarget.getOrDefault(request.targetId(), 0);
        if (already >= request.maxHitsPerTarget()) {
            return false;
        }
        perTarget.put(request.targetId(), already + 1);
        return true;
    }

    private void enterCombat(UUID combatantId, Instant now) {
        if (combatState.touch(combatantId, now)) {
            events.publish(new CombatEvents.CombatStateChanged(
                    UUID.randomUUID(), now, combatantId, true));
        }
    }

    private void publishDamage(DamageRequest request, DamageResult result, Instant now) {
        events.publish(new CombatEvents.DamageDealt(UUID.randomUUID(), now, request.castId(),
                request.attackerId(), request.targetId(), request.type(), result));
    }

    private void onDeath(DamageRequest request, Combatant target, double overkill, Instant now) {
        // Combat state is cleared before the event so a consumer that inspects
        // the victim sees them out of combat, not fighting while dead.
        combatState.clear(target.id());
        events.publish(new CombatEvents.CombatantDied(UUID.randomUUID(), now, target.id(),
                request.attackerId(), request.type(), Math.max(0.0, overkill)));
    }

    /** Line-of-sight port. The Paper adapter ray-traces; tests answer directly. */
    @FunctionalInterface
    public interface LineOfSight {

        boolean clear(com.branz.mmorpg.api.combat.WorldPoint from,
                      com.branz.mmorpg.api.combat.WorldPoint to);

        /** Always clear. For tests and for effects that do not require sight. */
        static LineOfSight always() {
            return (from, to) -> true;
        }
    }
}
