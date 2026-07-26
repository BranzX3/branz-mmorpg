package com.branz.mmorpg.core.stat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.branz.mmorpg.api.stat.AttributeModifier;
import com.branz.mmorpg.api.stat.AttributeType;
import com.branz.mmorpg.api.stat.ModifierSource;
import com.branz.mmorpg.core.fixture.FixedGameClock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class AttributeContainerTest {

    private static final ModifierSource SWORD =
            ModifierSource.of(ModifierSource.SourceType.EQUIPMENT, "sword-instance-1");
    private static final ModifierSource BUFF =
            ModifierSource.of(ModifierSource.SourceType.STATUS, "branz:might");

    @Test
    void equipmentSwapCannotDuplicateAModifier() {
        AttributeContainer container = new AttributeContainer();
        AttributeModifier swordPower =
                AttributeModifier.flat("sword-instance-1:power", AttributeType.PHYSICAL_POWER, 25.0, SWORD);

        assertTrue(container.add(swordPower));
        assertFalse(container.add(swordPower), "re-adding the identical modifier changes nothing");
        container.add(swordPower);

        assertEquals(1, container.size());
        assertEquals(35.0, container.value(AttributeType.PHYSICAL_POWER), 1e-9);
    }

    @Test
    void sameIdReplacesRatherThanStacks() {
        AttributeContainer container = new AttributeContainer();
        container.add(AttributeModifier.flat("power", AttributeType.PHYSICAL_POWER, 25.0, SWORD));
        container.add(AttributeModifier.flat("power", AttributeType.PHYSICAL_POWER, 5.0, SWORD));

        assertEquals(1, container.size());
        assertEquals(15.0, container.value(AttributeType.PHYSICAL_POWER), 1e-9);
    }

    @Test
    void removingASourceDropsEverythingItGranted() {
        AttributeContainer container = new AttributeContainer();
        container.add(AttributeModifier.flat("s1", AttributeType.PHYSICAL_POWER, 10.0, SWORD));
        container.add(AttributeModifier.flat("s2", AttributeType.DEFENSE, 5.0, SWORD));
        container.add(AttributeModifier.flat("b1", AttributeType.PHYSICAL_POWER, 3.0, BUFF));

        assertEquals(2, container.removeSource(SWORD));

        assertEquals(1, container.size());
        assertEquals(13.0, container.value(AttributeType.PHYSICAL_POWER), 1e-9);
        assertEquals(0.0, container.value(AttributeType.DEFENSE), 1e-9);
    }

    @Test
    void expiredModifiersNeverAppearInAResolvedValue() {
        FixedGameClock clock = FixedGameClock.at("2026-07-25T12:00:00Z");
        AttributeContainer container = new AttributeContainer();
        container.add(AttributeModifier.flat("might", AttributeType.PHYSICAL_POWER, 40.0, BUFF)
                .expiringAt(clock.now().plusSeconds(30)));

        assertEquals(50.0, container.snapshot(clock).get(AttributeType.PHYSICAL_POWER), 1e-9);

        clock.advance(Duration.ofSeconds(31));

        assertEquals(10.0, container.snapshot(clock).get(AttributeType.PHYSICAL_POWER), 1e-9);
        assertEquals(0, container.size(), "the lapsed modifier is gone, not merely ignored");
    }

    @Test
    void purgingIsSafeWhenNothingHasExpired() {
        FixedGameClock clock = FixedGameClock.at("2026-07-25T12:00:00Z");
        AttributeContainer container = new AttributeContainer();
        container.add(AttributeModifier.flat("permanent", AttributeType.DEFENSE, 5.0, SWORD));
        container.add(AttributeModifier.flat("timed", AttributeType.DEFENSE, 5.0, BUFF)
                .expiringAt(clock.now().plusSeconds(60)));

        assertEquals(0, container.purgeExpired(clock.now()));
        assertEquals(2, container.size());
        assertEquals(10.0, container.value(AttributeType.DEFENSE), 1e-9);
    }

    @Test
    void snapshotIsStableUntilSomethingChanges() {
        FixedGameClock clock = FixedGameClock.at("2026-07-25T12:00:00Z");
        AttributeContainer container = new AttributeContainer();
        container.base(AttributeType.MAX_HEALTH, 250.0);

        var first = container.snapshot(clock);
        assertEquals(first, container.snapshot(clock));

        container.add(AttributeModifier.flat("hp", AttributeType.MAX_HEALTH, 50.0, SWORD));
        var second = container.snapshot(clock);

        assertEquals(300.0, second.get(AttributeType.MAX_HEALTH), 1e-9);
        assertEquals(java.util.Map.of(AttributeType.MAX_HEALTH, 300.0), second.differenceFrom(first));
    }

    @Test
    void basesFallBackToAttributeDefaults() {
        AttributeContainer container = new AttributeContainer();

        assertEquals(AttributeType.MAX_MANA.defaultValue(), container.base(AttributeType.MAX_MANA), 1e-9);
        assertEquals(AttributeType.CRITICAL_DAMAGE.defaultValue(),
                container.value(AttributeType.CRITICAL_DAMAGE), 1e-9);
    }

    @Test
    void rejectsNegativeResourceMaximumBases() {
        AttributeContainer container = new AttributeContainer();

        assertThrows(IllegalArgumentException.class,
                () -> container.base(AttributeType.MAX_ENERGY, -1.0));
    }
}
