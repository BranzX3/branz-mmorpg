package com.branz.mmorpg.core.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.skill.ResourceType;
import com.branz.mmorpg.api.skill.SkillCaster;
import com.branz.mmorpg.api.skill.SkillDefinition;
import com.branz.mmorpg.api.skill.SkillEffectNode;
import com.branz.mmorpg.api.skill.SkillEffectType;
import com.branz.mmorpg.api.skill.SkillState;
import com.branz.mmorpg.core.fixture.FixedGameClock;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SkillExecutionEngineTest {

    private final FixedGameClock clock = FixedGameClock.at("2026-07-26T12:00:00Z");
    private final AtomicInteger effects = new AtomicInteger();
    private final SkillExecutionEngine engine =
            new SkillExecutionEngine(clock, (cast, definition, root) -> effects.incrementAndGet());

    @Test
    void castRunsEveryPhaseAndExecutesItsGraphOnce() {
        FakeCaster caster = new FakeCaster(100, 100);
        SkillDefinition skill = skill(100, 200, 300, 1_000);

        var begun = engine.begin(skill, caster, 42L);

        assertTrue(begun.started());
        assertEquals(SkillState.CASTING, begun.cast().state());
        assertEquals(90.0, caster.mana, 1e-9);
        clock.advance(Duration.ofMillis(100));
        assertEquals(SkillState.ACTIVE, engine.advance(begun.cast().castId()).state());
        assertEquals(1, effects.get());
        clock.advance(Duration.ofMillis(200));
        assertEquals(SkillState.RECOVERY, engine.advance(begun.cast().castId()).state());
        clock.advance(Duration.ofMillis(300));
        assertEquals(SkillState.COOLDOWN, engine.advance(begun.cast().castId()).state());
        assertEquals(1_000L, engine.cooldownRemainingMillis(caster.id(), "weapon-primary"));
        clock.advance(Duration.ofSeconds(1));
        assertEquals(SkillState.COMPLETE, engine.advance(begun.cast().castId()).state());
        assertEquals(1, effects.get());
    }

    @Test
    void spamAndSharedCooldownAreRejectedWithoutSpending() {
        FakeCaster caster = new FakeCaster(100, 100);
        SkillDefinition skill = skill(100, 0, 0, 1_000);
        var first = engine.begin(skill, caster, 1L);

        assertEquals(SkillExecutionEngine.Rejection.ALREADY_CASTING,
                engine.begin(skill, caster, 1L).rejection());
        assertEquals(90.0, caster.mana, 1e-9);
        clock.advance(Duration.ofMillis(100));
        engine.advance(first.cast().castId());
        assertEquals(SkillExecutionEngine.Rejection.COOLDOWN,
                engine.begin(skill, caster, 1L).rejection());
        assertEquals(90.0, caster.mana, 1e-9);
    }

    @Test
    void failedEligibilityAndResourcesConsumeNothing() {
        FakeCaster caster = new FakeCaster(5, 100);
        SkillDefinition skill = skill(100, 0, 0, 1_000);

        assertEquals(SkillExecutionEngine.Rejection.INSUFFICIENT_RESOURCE,
                engine.begin(skill, caster, 1L).rejection());
        assertEquals(5.0, caster.mana, 1e-9);

        caster.mana = 100;
        caster.silenced = true;
        assertEquals(SkillExecutionEngine.Rejection.SILENCED,
                engine.begin(skill, caster, 1L).rejection());
        assertEquals(100.0, caster.mana, 1e-9);
    }

    @Test
    void interruptionRefundsConfiguredFractionAndStartsCooldown() {
        FakeCaster caster = new FakeCaster(100, 100);
        SkillDefinition skill = skill(1_000, 0, 0, 1_000);
        var cast = engine.begin(skill, caster, 7L).cast();

        var interrupted = engine.interrupt(cast.castId(), "stunned");

        assertEquals(SkillState.INTERRUPTED, interrupted.state());
        assertEquals("stunned", interrupted.interruptionReason());
        assertEquals(95.0, caster.mana, 1e-9, "half of the 10 Mana cost was refunded");
        assertTrue(engine.cooldownRemainingMillis(caster.id(), "weapon-primary") > 0);
    }

    @Test
    void cooldownRecoveryIsCappedAtThirtyFivePercent() {
        FakeCaster caster = new FakeCaster(100, 100);
        caster.cooldownRecovery = 0.99;
        SkillDefinition skill = skill(0, 0, 0, 1_000);
        var cast = engine.begin(skill, caster, 1L).cast();
        engine.advance(cast.castId());

        assertEquals(650L, engine.cooldownRemainingMillis(caster.id(), "weapon-primary"));
    }

    @Test
    void effectGraphRejectsCyclesAndBrokenReferences() {
        Map<String, SkillEffectNode> cycle = Map.of(
                "a", new SkillEffectNode("a", SkillEffectType.SEQUENCE, Map.of(), Map.of(), List.of("b")),
                "b", new SkillEffectNode("b", SkillEffectType.SEQUENCE, Map.of(), Map.of(), List.of("a")));
        assertThrows(MMOException.class, () -> definition(cycle, "a", 100, 0, 0, 1_000));

        Map<String, SkillEffectNode> broken = Map.of(
                "a", new SkillEffectNode("a", SkillEffectType.SEQUENCE, Map.of(), Map.of(),
                        List.of("missing")));
        assertThrows(MMOException.class, () -> definition(broken, "a", 100, 0, 0, 1_000));
    }

    private static SkillDefinition skill(long cast, long active, long recovery, long cooldown) {
        SkillEffectNode damage = new SkillEffectNode("damage", SkillEffectType.DAMAGE,
                Map.of("power", 25.0), Map.of("type", "physical"), List.of());
        return definition(Map.of("damage", damage), "damage", cast, active, recovery, cooldown);
    }

    private static SkillDefinition definition(Map<String, SkillEffectNode> effects, String root,
                                              long cast, long active, long recovery, long cooldown) {
        return new SkillDefinition(ContentId.parse("branz:test_skill"), "Test", "weapon-1",
                Set.of("weapon"), cast, active, recovery, cooldown, "weapon-primary",
                Map.of(ResourceType.MANA, 10.0), 0.5, 8.0, true, effects, root);
    }

    private static final class FakeCaster implements SkillCaster {
        private final UUID id = UUID.randomUUID();
        private double mana;
        private double stamina;
        private boolean alive = true;
        private boolean silenced;
        private boolean stunned;
        private double cooldownRecovery;

        private FakeCaster(double mana, double stamina) {
            this.mana = mana;
            this.stamina = stamina;
        }

        @Override
        public UUID id() {
            return id;
        }

        @Override
        public boolean alive() {
            return alive;
        }

        @Override
        public boolean silenced() {
            return silenced;
        }

        @Override
        public boolean stunned() {
            return stunned;
        }

        @Override
        public double cooldownRecovery() {
            return cooldownRecovery;
        }

        @Override
        public boolean spend(Map<ResourceType, Double> costs) {
            double manaCost = costs.getOrDefault(ResourceType.MANA, 0.0);
            double staminaCost = costs.getOrDefault(ResourceType.STAMINA, 0.0);
            if (mana < manaCost || stamina < staminaCost) {
                return false;
            }
            mana -= manaCost;
            stamina -= staminaCost;
            return true;
        }

        @Override
        public void refund(Map<ResourceType, Double> costs, double fraction) {
            mana += costs.getOrDefault(ResourceType.MANA, 0.0) * fraction;
            stamina += costs.getOrDefault(ResourceType.STAMINA, 0.0) * fraction;
        }
    }
}
