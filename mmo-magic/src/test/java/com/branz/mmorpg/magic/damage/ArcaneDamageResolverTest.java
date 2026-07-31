package com.branz.mmorpg.magic.damage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.branz.mmorpg.combat.damage.ConditionalAdvantage;
import com.branz.mmorpg.magic.definition.ArcaneSchool;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ArcaneDamageResolverTest {
    private final ArcaneDamageResolver resolver = new ArcaneDamageResolver();

    @Test
    void resolvesArcaneResistanceWithoutArmorAndUsesDeterministicAdvantage() {
        ArcaneDamageBreakdown result =
                resolver.resolve(
                        new ArcaneDamageRequest(
                                ArcaneSchool.FIRE,
                                100,
                                0.9,
                                0.2,
                                Set.of(ConditionalAdvantage.POSTURE_BREAK),
                                1));

        assertEquals(90, result.rawDamage());
        assertEquals(0.8, result.resistanceMultiplier());
        assertEquals(1.35, result.advantageMultiplier());
        assertEquals(97.2, result.finalDamage(), 0.000001);
    }

    @Test
    void clampsAuthoredResistanceBounds() {
        ArcaneDamageBreakdown vulnerable =
                resolver.resolve(
                        new ArcaneDamageRequest(ArcaneSchool.FIRE, 100, 0.9, -5, Set.of(), 1));
        ArcaneDamageBreakdown resistant =
                resolver.resolve(
                        new ArcaneDamageRequest(ArcaneSchool.FIRE, 100, 0.9, 5, Set.of(), 1));

        assertEquals(117, vulnerable.finalDamage(), 0.000001);
        assertEquals(36, resistant.finalDamage(), 0.000001);
    }
}
