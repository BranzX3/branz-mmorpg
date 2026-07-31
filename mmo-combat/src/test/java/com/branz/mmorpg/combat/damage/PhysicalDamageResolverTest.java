package com.branz.mmorpg.combat.damage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PhysicalDamageResolverTest {
    private final PhysicalDamageResolver resolver = new PhysicalDamageResolver();

    @Test
    void resolvesCanonicalArmorResistanceAndAdvantageBreakdown() {
        PhysicalDamageBreakdown result =
                resolver.resolve(
                        request(
                                100,
                                0.8,
                                100,
                                0,
                                0,
                                0.2,
                                Set.of(
                                        ConditionalAdvantage.POSTURE_BREAK,
                                        ConditionalAdvantage.WEAK_POINT)));

        assertEquals(80, result.rawDamage());
        assertEquals(100, result.effectiveArmor());
        assertEquals(0.5, result.armorMitigation());
        assertEquals(0.8, result.resistanceMultiplier());
        assertEquals(1.475, result.advantageMultiplier(), 0.0000001);
        assertEquals(47.2, result.finalDamage(), 0.0000001);
    }

    @Test
    void clampsPenetrationArmorMitigationResistanceAndAdvantage() {
        PhysicalDamageBreakdown cappedPenetration =
                resolver.resolve(request(100, 1, 1000, 1, 0, 0, Set.of()));
        PhysicalDamageBreakdown sixtyPercentPenetration =
                resolver.resolve(request(100, 1, 1000, 0.6, 0, 0, Set.of()));
        assertEquals(sixtyPercentPenetration.effectiveArmor(), cappedPenetration.effectiveArmor());
        assertEquals(0.70, cappedPenetration.armorMitigation());

        assertEquals(
                1.3,
                resolver.resolve(request(100, 1, 0, 0, 0, -1, Set.of())).resistanceMultiplier());
        assertEquals(
                0.4,
                resolver.resolve(request(100, 1, 0, 0, 0, 1, Set.of())).resistanceMultiplier());
    }

    @Test
    void randomizedFormulaIsDeterministicAndMonotonicAcrossArmorAndPenetration() {
        Random random = new Random(0xDA6A6EL);
        for (int index = 0; index < 10_000; index++) {
            double power = 1 + random.nextDouble(500);
            double coefficient = 0.1 + random.nextDouble(3);
            double armor = random.nextDouble(1000);
            double penetration = random.nextDouble(0.6);
            PhysicalDamageBreakdown baseline =
                    resolver.resolve(
                            request(power, coefficient, armor, penetration, 0, 0, Set.of()));
            PhysicalDamageBreakdown repeated =
                    resolver.resolve(
                            request(power, coefficient, armor, penetration, 0, 0, Set.of()));
            PhysicalDamageBreakdown moreArmor =
                    resolver.resolve(
                            request(power, coefficient, armor + 1, penetration, 0, 0, Set.of()));
            PhysicalDamageBreakdown morePenetration =
                    resolver.resolve(
                            request(
                                    power,
                                    coefficient,
                                    armor,
                                    Math.min(0.6, penetration + 0.01),
                                    0,
                                    0,
                                    Set.of()));

            assertEquals(baseline, repeated);
            assertTrue(moreArmor.finalDamage() <= baseline.finalDamage() + 0.0000001);
            assertTrue(morePenetration.finalDamage() + 0.0000001 >= baseline.finalDamage());
        }
    }

    private static PhysicalDamageRequest request(
            double power,
            double coefficient,
            double armor,
            double penetrationPercent,
            double flatPenetration,
            double resistance,
            Set<ConditionalAdvantage> advantages) {
        return new PhysicalDamageRequest(
                power,
                coefficient,
                0,
                armor,
                penetrationPercent,
                flatPenetration,
                resistance,
                advantages,
                1);
    }
}
