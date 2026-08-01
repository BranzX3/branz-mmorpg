package com.branz.mmorpg.magic.effect;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.magic.definition.ArcaneSchool;
import com.branz.mmorpg.magic.definition.SpellCastType;
import com.branz.mmorpg.magic.definition.SpellDefinition;
import com.branz.mmorpg.magic.definition.SpellDeliveryType;
import com.branz.mmorpg.magic.definition.SpellTargetType;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SpellEffectEngineTest {
    @Test
    void zonePulsesOnScheduleAndExpiresAtBoundedLifetime() {
        ZoneEngine engine = new ZoneEngine();
        ZoneRuntime runtime = engine.start(zoneSpell(), 100);

        ZoneTickResolution first = engine.advance(runtime, 100);
        assertTrue(first.pulseEmitted());
        assertEquals(1, first.runtime().pulsesEmitted());
        assertFalse(engine.advance(first.runtime(), 109).pulseEmitted());
        ZoneTickResolution second = engine.advance(first.runtime(), 110);
        assertTrue(second.pulseEmitted());
        assertTrue(engine.advance(second.runtime(), 140).runtime().expired());
    }

    @Test
    void RunicImbuementConsumesExactChargesAndExpires() {
        RunicImbuementEngine engine = new RunicImbuementEngine();
        RunicImbuementRuntime runtime = engine.start(imbueSpell(), 10);

        ImbuementHitResolution first = engine.consume(runtime, 11);
        assertTrue(first.applied());
        assertEquals(1, first.remainingRuntime().orElseThrow().remainingCharges());
        ImbuementHitResolution second = engine.consume(first.remainingRuntime().orElseThrow(), 12);
        assertTrue(second.applied());
        assertTrue(second.remainingRuntime().isEmpty());
        assertFalse(engine.consume(runtime, 31).applied());
    }

    private static SpellDefinition zoneSpell() {
        return spell(
                SpellCastType.WINDUP,
                SpellDeliveryType.ZONE,
                Optional.of(new SpellDefinition.Zone(12, 3, 40, 10, 6)),
                Optional.empty());
    }

    private static SpellDefinition imbueSpell() {
        return spell(
                SpellCastType.INSTANT,
                SpellDeliveryType.IMBUE,
                Optional.empty(),
                Optional.of(new SpellDefinition.Imbuement(20, 2, 0.35)));
    }

    private static SpellDefinition spell(
            SpellCastType castType,
            SpellDeliveryType delivery,
            Optional<SpellDefinition.Zone> zone,
            Optional<SpellDefinition.Imbuement> imbuement) {
        return new SpellDefinition(
                DefinitionId.of("spell.test.effect"),
                DefinitionId.of("magic.test"),
                castType,
                delivery == SpellDeliveryType.ZONE
                        ? SpellTargetType.GROUND_AREA
                        : SpellTargetType.SELF,
                delivery,
                new SpellDefinition.Requirements(Set.of("STAFF"), 1),
                5,
                new SpellDefinition.Phases(castType == SpellCastType.WINDUP ? 2 : 0, 0, 0, 4),
                new SpellDefinition.Interruption(false, false, true, true, true, true),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                zone,
                imbuement,
                new SpellDefinition.Output(ArcaneSchool.FIRE, 0.4, 3, 2),
                "TEST_EFFECT",
                new SpellDefinition.CombatProfiles(1, 0.65));
    }
}
