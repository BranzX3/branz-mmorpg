package com.branz.mmorpg.core.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.error.MMOException;
import com.branz.mmorpg.api.stat.AttributeModifier;
import com.branz.mmorpg.api.stat.AttributeType;
import com.branz.mmorpg.api.stat.ModifierOperation;
import com.branz.mmorpg.api.stat.ModifierSource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AttributeResolverTest {

    private static final ModifierSource SWORD =
            ModifierSource.of(ModifierSource.SourceType.EQUIPMENT, "sword-1");

    @Test
    void appliesFlatThenAdditivePercentThenMultiplier() {
        double resolved = AttributeResolver.resolve(AttributeType.PHYSICAL_POWER, 100.0, List.of(
                AttributeModifier.flat("flat", AttributeType.PHYSICAL_POWER, 50.0, SWORD),
                AttributeModifier.percent("pct", AttributeType.PHYSICAL_POWER, 0.20, SWORD),
                new AttributeModifier("mult", AttributeType.PHYSICAL_POWER, ModifierOperation.MULTIPLY,
                        1.10, SWORD, "", 0, Optional.empty())));

        // (100 + 50) * 1.20 * 1.10
        assertEquals(198.0, resolved, 1e-9);
    }

    @Test
    void additivePercentagesSumRatherThanCompound() {
        List<AttributeModifier> five = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            five.add(AttributeModifier.percent("p" + i, AttributeType.PHYSICAL_POWER, 0.10, SWORD));
        }

        // +50%, not the +61% that compounding would give
        assertEquals(150.0, AttributeResolver.resolve(AttributeType.PHYSICAL_POWER, 100.0, five), 1e-9);
    }

    @Test
    void resultIsIndependentOfModifierOrder() {
        List<AttributeModifier> modifiers = new ArrayList<>(List.of(
                AttributeModifier.flat("a", AttributeType.MAX_HEALTH, 40.0, SWORD),
                AttributeModifier.percent("b", AttributeType.MAX_HEALTH, 0.25, SWORD),
                new AttributeModifier("c", AttributeType.MAX_HEALTH, ModifierOperation.MULTIPLY,
                        1.5, SWORD, "", 0, Optional.empty())));
        double expected = AttributeResolver.resolve(AttributeType.MAX_HEALTH, 100.0, modifiers);

        for (int i = 0; i < 20; i++) {
            Collections.shuffle(modifiers);
            assertEquals(expected,
                    AttributeResolver.resolve(AttributeType.MAX_HEALTH, 100.0, modifiers), 1e-9);
        }
    }

    @Test
    void onlyTheStrongestModifierInAStackingGroupApplies() {
        List<AttributeModifier> haste = List.of(
                AttributeModifier.percent("weak", AttributeType.ATTACK_SPEED, 0.05, SWORD)
                        .inGroup("haste", 1),
                AttributeModifier.percent("strong", AttributeType.ATTACK_SPEED, 0.20, SWORD)
                        .inGroup("haste", 9),
                AttributeModifier.percent("other", AttributeType.ATTACK_SPEED, 0.10, SWORD));

        // 1.0 * (1 + 0.20 + 0.10): the group contributes once, the ungrouped one always
        assertEquals(1.30, AttributeResolver.resolve(AttributeType.ATTACK_SPEED, 1.0, haste), 1e-9);
        assertEquals(2, AttributeResolver.applicable(AttributeType.ATTACK_SPEED, haste).size());
    }

    @Test
    void groupTiesBreakOnIdNotInsertionOrder() {
        List<AttributeModifier> tied = new ArrayList<>(List.of(
                AttributeModifier.percent("zzz", AttributeType.ATTACK_SPEED, 0.30, SWORD)
                        .inGroup("haste", 5),
                AttributeModifier.percent("aaa", AttributeType.ATTACK_SPEED, 0.10, SWORD)
                        .inGroup("haste", 5)));

        for (int i = 0; i < 10; i++) {
            Collections.shuffle(tied);
            List<AttributeModifier> applied = AttributeResolver.applicable(AttributeType.ATTACK_SPEED, tied);
            assertEquals(1, applied.size());
            assertEquals("aaa", applied.get(0).id(), "ties resolve on ID, deterministically");
        }
    }

    @Test
    void ignoresModifiersForOtherAttributes() {
        double resolved = AttributeResolver.resolve(AttributeType.DEFENSE, 10.0, List.of(
                AttributeModifier.flat("wrong", AttributeType.PHYSICAL_POWER, 500.0, SWORD)));

        assertEquals(10.0, resolved, 1e-9);
    }

    @Test
    void clampsToTheDocumentedCaps() {
        assertEquals(0.60, AttributeResolver.resolve(AttributeType.CRITICAL_CHANCE, 0.05, List.of(
                AttributeModifier.flat("crit", AttributeType.CRITICAL_CHANCE, 5.0, SWORD))), 1e-9);
        assertEquals(0.35, AttributeResolver.resolve(AttributeType.COOLDOWN_RECOVERY, 0.0, List.of(
                AttributeModifier.flat("cdr", AttributeType.COOLDOWN_RECOVERY, 0.9, SWORD))), 1e-9);
        assertEquals(1.30, AttributeResolver.resolve(AttributeType.MOVEMENT_SPEED, 1.0, List.of(
                AttributeModifier.percent("spd", AttributeType.MOVEMENT_SPEED, 2.0, SWORD))), 1e-9);
        assertEquals(0.60, AttributeResolver.resolve(AttributeType.CROWD_CONTROL_RESISTANCE, 0.0, List.of(
                AttributeModifier.flat("ccr", AttributeType.CROWD_CONTROL_RESISTANCE, 1.0, SWORD))), 1e-9);
    }

    @Test
    void clampsBelowTheMinimumToo() {
        assertEquals(0.0, AttributeResolver.resolve(AttributeType.DEFENSE, 10.0, List.of(
                AttributeModifier.flat("debuff", AttributeType.DEFENSE, -999.0, SWORD))), 1e-9);
        assertEquals(1.0, AttributeResolver.resolve(AttributeType.MAX_HEALTH, 100.0, List.of(
                AttributeModifier.flat("drain", AttributeType.MAX_HEALTH, -1_000.0, SWORD))), 1e-9);
    }

    @Test
    void rejectsNonFiniteModifierValues() {
        assertThrows(MMOException.class,
                () -> AttributeModifier.flat("nan", AttributeType.PHYSICAL_POWER, Double.NaN, SWORD));
        assertThrows(MMOException.class,
                () -> AttributeModifier.flat("inf", AttributeType.PHYSICAL_POWER,
                        Double.POSITIVE_INFINITY, SWORD));
        assertThrows(IllegalArgumentException.class,
                () -> AttributeResolver.resolve(AttributeType.PHYSICAL_POWER, Double.NaN, List.of()));
    }

    @Test
    void rejectsOverflowInsteadOfLeakingInfinityOrSilentlyClamping() {
        MMOException failure = assertThrows(MMOException.class,
                () -> AttributeResolver.resolve(AttributeType.PHYSICAL_POWER, Double.MAX_VALUE, List.of(
                        new AttributeModifier("huge", AttributeType.PHYSICAL_POWER,
                                ModifierOperation.MULTIPLY, Double.MAX_VALUE,
                                SWORD, "", 0, Optional.empty()))));

        assertEquals(com.branz.mmorpg.api.error.ErrorCode.INVALID_ARGUMENT, failure.code());
    }

    @Test
    void everyPercentageAttributeDeclaresACap() {
        for (AttributeType attribute : AttributeType.values()) {
            assertTrue(Double.isFinite(attribute.maximum()), attribute + " must declare a finite cap");
            assertTrue(attribute.maximum() >= attribute.minimum(), attribute + " range is inverted");
            assertEquals(attribute.defaultValue(), attribute.clamp(attribute.defaultValue()), 1e-9,
                    attribute + " default must sit inside its own range");
        }
    }

    @Test
    void randomizedFiniteInputsStayDeterministicAndInsideDeclaredBounds() {
        java.util.Random random = new java.util.Random(0xC2A77L);
        for (int sample = 0; sample < 500; sample++) {
            AttributeType attribute = AttributeType.values()[
                    random.nextInt(AttributeType.values().length)];
            double base = random.nextDouble(1_000.0);
            List<AttributeModifier> modifiers = new ArrayList<>();
            for (int index = 0; index < 8; index++) {
                ModifierOperation operation = ModifierOperation.values()[random.nextInt(3)];
                double value = switch (operation) {
                    case ADD_FLAT -> random.nextDouble(-50.0, 50.0);
                    case ADD_PERCENT -> random.nextDouble(-0.25, 0.50);
                    case MULTIPLY -> random.nextDouble(0.50, 1.50);
                };
                modifiers.add(new AttributeModifier("m" + index, attribute, operation,
                        value, SWORD, "", 0, Optional.empty()));
            }
            double expected = AttributeResolver.resolve(attribute, base, modifiers);
            Collections.shuffle(modifiers, random);
            double shuffled = AttributeResolver.resolve(attribute, base, modifiers);

            assertEquals(expected, shuffled, 1e-9);
            assertTrue(shuffled >= attribute.minimum());
            assertTrue(shuffled <= attribute.maximum());
            assertTrue(Double.isFinite(shuffled));
        }
    }
}
