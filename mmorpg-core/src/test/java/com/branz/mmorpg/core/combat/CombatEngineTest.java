package com.branz.mmorpg.core.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.combat.CombatPolicy;
import com.branz.mmorpg.api.combat.Combatant;
import com.branz.mmorpg.api.combat.DamageRequest;
import com.branz.mmorpg.api.combat.DamageResult;
import com.branz.mmorpg.api.combat.DamageType;
import com.branz.mmorpg.api.combat.RejectionReason;
import com.branz.mmorpg.api.stat.AttributeType;
import com.branz.mmorpg.core.event.SimpleEventBus;
import com.branz.mmorpg.core.fixture.FixedGameClock;
import com.branz.mmorpg.core.fixture.ScriptedRandomSource;
import com.branz.mmorpg.core.fixture.TestCombatant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CombatEngineTest {

    private final Map<UUID, Combatant> world = new HashMap<>();
    private final List<CombatEvents.DamageDealt> damageEvents = new ArrayList<>();
    private final List<CombatEvents.CombatantDied> deaths = new ArrayList<>();
    private final List<CombatEvents.CombatStateChanged> stateChanges = new ArrayList<>();

    private FixedGameClock clock;
    private SimpleEventBus bus;

    @BeforeEach
    void setUp() {
        clock = FixedGameClock.at("2026-07-25T12:00:00Z");
        bus = new SimpleEventBus();
        bus.subscribe(CombatEvents.DamageDealt.class, damageEvents::add);
        bus.subscribe(CombatEvents.CombatantDied.class, deaths::add);
        bus.subscribe(CombatEvents.CombatStateChanged.class, stateChanges::add);
    }

    private CombatEngine engine(CombatPolicy policy, double... rolls) {
        return new CombatEngine(policy, clock,
                rolls.length == 0 ? ScriptedRandomSource.of(new double[]{0.99, 0.99, 0.99, 0.99, 0.99})
                        : ScriptedRandomSource.of(rolls),
                bus, world::get, CombatEngine.LineOfSight.always());
    }

    private TestCombatant register(TestCombatant combatant) {
        world.put(combatant.id(), combatant);
        return combatant;
    }

    @Test
    void damageIsMitigatedAndAppliedToHealth() {
        TestCombatant attacker = register(TestCombatant.mob().with(AttributeType.PHYSICAL_POWER, 0.0));
        TestCombatant target = register(TestCombatant.player()
                .health(100.0).with(AttributeType.DEFENSE, 100.0));

        DamageResult result = engine(CombatPolicy.defaults()).damage(
                DamageRequest.melee(UUID.randomUUID(), attacker.id(), target.id(),
                        DamageType.PHYSICAL, 50.0, 10.0));

        assertTrue(result.landed());
        assertEquals(25.0, result.applied(), 1e-9, "50 power, 50% mitigation");
        assertEquals(75.0, target.currentHealth(), 1e-9);
        assertEquals(1, damageEvents.size());
    }

    @Test
    void shieldsAbsorbBeforeHealth() {
        TestCombatant attacker = register(TestCombatant.mob().with(AttributeType.PHYSICAL_POWER, 0.0));
        TestCombatant target = register(TestCombatant.player().health(100.0).shield(30.0));

        DamageResult result = engine(CombatPolicy.defaults()).damage(
                DamageRequest.melee(UUID.randomUUID(), attacker.id(), target.id(),
                        DamageType.PHYSICAL, 50.0, 10.0));

        assertEquals(30.0, result.absorbed(), 1e-9);
        assertEquals(20.0, result.applied(), 1e-9);
        assertEquals(80.0, target.currentHealth(), 1e-9);
        assertEquals(0.0, target.shieldRemaining(), 1e-9);
    }

    @Test
    void oneCastCannotHitTheSameTargetTwice() {
        TestCombatant attacker = register(TestCombatant.mob().with(AttributeType.PHYSICAL_POWER, 0.0));
        TestCombatant target = register(TestCombatant.player().health(100.0));
        CombatEngine engine = engine(CombatPolicy.defaults());
        UUID castId = UUID.randomUUID();

        assertTrue(engine.damage(DamageRequest.melee(castId, attacker.id(), target.id(),
                DamageType.PHYSICAL, 10.0, 10.0)).landed());
        DamageResult second = engine.damage(DamageRequest.melee(castId, attacker.id(), target.id(),
                DamageType.PHYSICAL, 10.0, 10.0));

        assertEquals(RejectionReason.DUPLICATE_HIT, second.rejection());
        assertEquals(90.0, target.currentHealth(), 1e-9, "the second hit changed nothing");

        // a different cast is a different hit
        assertTrue(engine.damage(DamageRequest.melee(UUID.randomUUID(), attacker.id(), target.id(),
                DamageType.PHYSICAL, 10.0, 10.0)).landed());
    }

    @Test
    void multiHitCastsRespectTheirDeclaredLimit() {
        TestCombatant attacker = register(TestCombatant.mob().with(AttributeType.PHYSICAL_POWER, 0.0));
        TestCombatant target = register(TestCombatant.player().health(100.0));
        CombatEngine engine = engine(CombatPolicy.defaults());
        UUID castId = UUID.randomUUID();
        DamageRequest triple = new DamageRequest(castId, attacker.id(), target.id(),
                DamageType.PHYSICAL, 10.0, 10.0, false, 3);

        assertTrue(engine.damage(triple).landed());
        assertTrue(engine.damage(triple).landed());
        assertTrue(engine.damage(triple).landed());
        assertEquals(RejectionReason.DUPLICATE_HIT, engine.damage(triple).rejection());
    }

    @Test
    void aRejectedAttemptDoesNotConsumeTheCastsHit() {
        TestCombatant attacker = register(TestCombatant.mob().with(AttributeType.PHYSICAL_POWER, 0.0));
        TestCombatant target = register(TestCombatant.player().health(100.0).at(500, 0, 0));
        CombatEngine engine = engine(CombatPolicy.defaults());
        UUID castId = UUID.randomUUID();

        assertEquals(RejectionReason.OUT_OF_RANGE,
                engine.damage(DamageRequest.melee(castId, attacker.id(), target.id(),
                        DamageType.PHYSICAL, 10.0, 5.0)).rejection());

        target.at(1, 0, 0);
        assertTrue(engine.damage(DamageRequest.melee(castId, attacker.id(), target.id(),
                DamageType.PHYSICAL, 10.0, 5.0)).landed(),
                "an out-of-range miss must not burn the cast's hit");
    }

    @Test
    void safeZoneIsCheckedBeforeAnyMutation() {
        TestCombatant attacker = register(TestCombatant.mob());
        TestCombatant target = register(TestCombatant.player().health(100.0).safeZone(true));

        DamageResult result = engine(CombatPolicy.defaults()).damage(
                DamageRequest.melee(UUID.randomUUID(), attacker.id(), target.id(),
                        DamageType.PHYSICAL, 50.0, 10.0));

        assertEquals(RejectionReason.SAFE_ZONE, result.rejection());
        assertEquals(100.0, target.currentHealth(), 1e-9);
        assertTrue(damageEvents.isEmpty(), "a rejected attempt publishes nothing");
    }

    @Test
    void attackingFromASafeZoneIsAlsoRefused() {
        TestCombatant attacker = register(TestCombatant.player().safeZone(true));
        TestCombatant target = register(TestCombatant.mob().health(100.0));

        assertEquals(RejectionReason.SAFE_ZONE, engine(CombatPolicy.defaults()).damage(
                DamageRequest.melee(UUID.randomUUID(), attacker.id(), target.id(),
                        DamageType.PHYSICAL, 50.0, 10.0)).rejection());
    }

    @Test
    void pvpIsRefusedWhenDisabledAndAllowedWhenEnabled() {
        TestCombatant attacker = register(TestCombatant.player().allegiance("red")
                .with(AttributeType.PHYSICAL_POWER, 0.0));
        TestCombatant target = register(TestCombatant.player().allegiance("blue").health(100.0));

        assertEquals(RejectionReason.PVP_DISABLED, engine(CombatPolicy.defaults()).damage(
                DamageRequest.melee(UUID.randomUUID(), attacker.id(), target.id(),
                        DamageType.PHYSICAL, 50.0, 10.0)).rejection());

        DamageResult enabled = engine(CombatPolicy.defaults().withPvp(true)).damage(
                DamageRequest.melee(UUID.randomUUID(), attacker.id(), target.id(),
                        DamageType.PHYSICAL, 50.0, 10.0));

        assertTrue(enabled.landed());
        assertEquals(25.0, enabled.applied(), 1e-9, "the PvP coefficient halves it");
    }

    @Test
    void friendlyFireIsPolicyDriven() {
        TestCombatant attacker = register(TestCombatant.player().allegiance("party-1"));
        TestCombatant ally = register(TestCombatant.player().allegiance("party-1").health(100.0));

        assertEquals(RejectionReason.FRIENDLY_FIRE_DISABLED,
                engine(CombatPolicy.defaults().withPvp(true)).damage(
                        DamageRequest.melee(UUID.randomUUID(), attacker.id(), ally.id(),
                                DamageType.PHYSICAL, 50.0, 10.0)).rejection());

        assertTrue(engine(CombatPolicy.defaults().withPvp(true).withFriendlyFire(true)).damage(
                DamageRequest.melee(UUID.randomUUID(), attacker.id(), ally.id(),
                        DamageType.PHYSICAL, 50.0, 10.0)).landed());
    }

    @Test
    void invulnerableDeadAndCrossWorldTargetsAreRefused() {
        TestCombatant attacker = register(TestCombatant.mob());
        TestCombatant invulnerable = register(TestCombatant.player().health(100).invulnerable(true));
        TestCombatant dead = register(TestCombatant.player().health(0.0));
        TestCombatant elsewhere = register(TestCombatant.player().health(100)
                .inWorld(UUID.randomUUID()));
        CombatEngine engine = engine(CombatPolicy.defaults());

        assertEquals(RejectionReason.TARGET_INVULNERABLE, engine.damage(
                DamageRequest.melee(UUID.randomUUID(), attacker.id(), invulnerable.id(),
                        DamageType.PHYSICAL, 10.0, 10.0)).rejection());
        assertEquals(RejectionReason.TARGET_DEAD, engine.damage(
                DamageRequest.melee(UUID.randomUUID(), attacker.id(), dead.id(),
                        DamageType.PHYSICAL, 10.0, 10.0)).rejection());
        assertEquals(RejectionReason.DIFFERENT_WORLD, engine.damage(
                DamageRequest.melee(UUID.randomUUID(), attacker.id(), elsewhere.id(),
                        DamageType.PHYSICAL, 10.0, 0.0)).rejection());
    }

    @Test
    void lineOfSightIsEnforcedWhenRequired() {
        TestCombatant attacker = register(TestCombatant.mob());
        TestCombatant target = register(TestCombatant.player().health(100.0));
        CombatEngine blocked = new CombatEngine(CombatPolicy.defaults(), clock,
                ScriptedRandomSource.of(0.99), bus, world::get, (from, to) -> false);

        assertEquals(RejectionReason.NO_LINE_OF_SIGHT, blocked.damage(
                DamageRequest.melee(UUID.randomUUID(), attacker.id(), target.id(),
                        DamageType.PHYSICAL, 10.0, 10.0)).rejection());

        assertTrue(blocked.damage(new DamageRequest(UUID.randomUUID(), attacker.id(), target.id(),
                DamageType.PHYSICAL, 10.0, 10.0, false, 1)).landed(),
                "an effect that does not require sight still lands");
    }

    @Test
    void unknownCombatantsAndInvalidPowerAreRefused() {
        TestCombatant target = register(TestCombatant.player().health(100.0));
        CombatEngine engine = engine(CombatPolicy.defaults());

        assertEquals(RejectionReason.TARGET_UNAVAILABLE, engine.damage(
                DamageRequest.melee(UUID.randomUUID(), null, UUID.randomUUID(),
                        DamageType.PHYSICAL, 10.0, 10.0)).rejection());
        assertEquals(RejectionReason.ATTACKER_UNAVAILABLE, engine.damage(
                DamageRequest.melee(UUID.randomUUID(), UUID.randomUUID(), target.id(),
                        DamageType.PHYSICAL, 10.0, 10.0)).rejection());
        assertEquals(RejectionReason.INVALID_POWER, engine.damage(
                DamageRequest.melee(UUID.randomUUID(), null, target.id(),
                        DamageType.PHYSICAL, Double.NaN, 10.0)).rejection());
        assertEquals(RejectionReason.INVALID_POWER, engine.damage(
                DamageRequest.melee(UUID.randomUUID(), null, target.id(),
                        DamageType.PHYSICAL, -5.0, 10.0)).rejection());
    }

    @Test
    void environmentalDamageNeedsNoAttacker() {
        TestCombatant target = register(TestCombatant.player().health(100.0)
                .with(AttributeType.DEFENSE, 500.0));

        DamageResult result = engine(CombatPolicy.defaults()).damage(
                DamageRequest.environmental(UUID.randomUUID(), target.id(), 30.0));

        assertTrue(result.landed());
        assertEquals(30.0, result.applied(), 1e-9, "environmental damage ignores defense");
        assertNull(damageEvents.get(0).attackerId());
    }

    @Test
    void deathIsReportedOnceWithOverkillAndClearsCombatState() {
        TestCombatant attacker = register(TestCombatant.mob().with(AttributeType.PHYSICAL_POWER, 0.0));
        TestCombatant target = register(TestCombatant.player().health(10.0));
        CombatEngine engine = engine(CombatPolicy.defaults());

        DamageResult killing = engine.damage(DamageRequest.melee(UUID.randomUUID(), attacker.id(),
                target.id(), DamageType.TRUE, 30.0, 10.0));

        assertTrue(killing.lethal());
        assertEquals(10.0, killing.applied(), 1e-9);
        assertEquals(1, deaths.size());
        assertEquals(20.0, deaths.get(0).overkill(), 1e-9);
        assertEquals(attacker.id(), deaths.get(0).killerId());
        assertFalse(engine.combatState().inCombat(target.id(), clock.now()));
    }

    @Test
    void simultaneousLethalDamageProducesOneDeath() {
        TestCombatant first = register(TestCombatant.mob().with(AttributeType.PHYSICAL_POWER, 0.0));
        TestCombatant second = register(TestCombatant.mob().with(AttributeType.PHYSICAL_POWER, 0.0));
        TestCombatant target = register(TestCombatant.player().health(10.0));
        CombatEngine engine = engine(CombatPolicy.defaults());

        DamageResult killing = engine.damage(DamageRequest.melee(UUID.randomUUID(), first.id(),
                target.id(), DamageType.TRUE, 50.0, 10.0));
        DamageResult afterDeath = engine.damage(DamageRequest.melee(UUID.randomUUID(), second.id(),
                target.id(), DamageType.TRUE, 50.0, 10.0));

        assertTrue(killing.lethal());
        assertEquals(RejectionReason.TARGET_DEAD, afterDeath.rejection());
        assertEquals(1, deaths.size(), "only the blow that landed kills");
        assertEquals(first.id(), deaths.get(0).killerId());
    }

    @Test
    void combatStateEntersOnDamageAndExpiresOnInactivity() {
        TestCombatant attacker = register(TestCombatant.mob().with(AttributeType.PHYSICAL_POWER, 0.0));
        TestCombatant target = register(TestCombatant.player().health(1000.0));
        CombatEngine engine = engine(CombatPolicy.defaults());

        engine.damage(DamageRequest.melee(UUID.randomUUID(), attacker.id(), target.id(),
                DamageType.PHYSICAL, 10.0, 10.0));

        assertTrue(engine.combatState().inCombat(target.id(), clock.now()));
        assertEquals(2, stateChanges.size(), "attacker and target both entered combat");

        clock.advance(Duration.ofSeconds(7));
        assertFalse(engine.combatState().inCombat(target.id(), clock.now()));
        assertEquals(2, engine.sweepCombatState().size());
        assertEquals(0, engine.combatState().tracked());
        assertEquals(4, stateChanges.size(), "attacker and target both emitted leave events");
        assertFalse(stateChanges.get(2).inCombat());
        assertFalse(stateChanges.get(3).inCombat());
    }

    @Test
    void disconnectForgetsCombatStateAndEndingACastReleasesItsDedupRecord() {
        TestCombatant attacker = register(TestCombatant.mob().with(AttributeType.PHYSICAL_POWER, 0.0));
        TestCombatant target = register(TestCombatant.player().health(100.0));
        CombatEngine engine = engine(CombatPolicy.defaults());
        UUID castId = UUID.randomUUID();
        engine.damage(DamageRequest.melee(castId, attacker.id(), target.id(),
                DamageType.PHYSICAL, 10.0, 10.0));

        engine.forget(target.id());
        assertFalse(engine.combatState().inCombat(target.id(), clock.now()));

        assertEquals(1, engine.activeCasts());
        engine.endCast(castId);
        assertEquals(0, engine.activeCasts(), "cast records must not leak for the server's lifetime");
    }

    @Test
    void aThrowingSubscriberNeitherStopsOthersNorFailsTheHit() {
        bus.subscribe(CombatEvents.DamageDealt.class, event -> {
            throw new IllegalStateException("consumer is broken");
        });
        List<CombatEvents.DamageDealt> late = new ArrayList<>();
        bus.subscribe(CombatEvents.DamageDealt.class, late::add);
        TestCombatant attacker = register(TestCombatant.mob().with(AttributeType.PHYSICAL_POWER, 0.0));
        TestCombatant target = register(TestCombatant.player().health(100.0));

        DamageResult result = engine(CombatPolicy.defaults()).damage(
                DamageRequest.melee(UUID.randomUUID(), attacker.id(), target.id(),
                        DamageType.PHYSICAL, 10.0, 10.0));

        assertTrue(result.landed());
        assertEquals(90.0, target.currentHealth(), 1e-9);
        assertEquals(1, late.size(), "the remaining subscribers still receive it");
    }
}
