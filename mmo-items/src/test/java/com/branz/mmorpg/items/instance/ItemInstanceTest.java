package com.branz.mmorpg.items.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.api.identity.ItemId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ItemInstanceTest {
    @Test
    void relocationRequiresExpectedVersionAndProducesANewInstance() {
        ItemInstance original = instance(OptionalInt.of(40), OptionalInt.of(50));

        ItemInstance relocated = original.relocated(ItemLocation.inventory(10), 3);

        assertEquals(3, original.version());
        assertEquals(4, relocated.version());
        assertEquals(ItemLocation.inventory(10), relocated.location());
        assertThrows(
                IllegalArgumentException.class,
                () -> original.relocated(ItemLocation.inventory(11), 2));
    }

    @Test
    void durabilityMustBeAnAtomicValidPair() {
        assertThrows(
                IllegalArgumentException.class,
                () -> instance(OptionalInt.of(1), OptionalInt.empty()));
        assertThrows(
                IllegalArgumentException.class,
                () -> instance(OptionalInt.of(51), OptionalInt.of(50)));
    }

    private static ItemInstance instance(OptionalInt currentDurability, OptionalInt maxDurability) {
        return new ItemInstance(
                new ItemId(UUID.randomUUID()),
                DefinitionId.of("weapon.test.blade"),
                1,
                new CharacterId(UUID.randomUUID()),
                ItemLocation.inventory(2),
                Map.of("quality", BigDecimal.valueOf(0.25)),
                0,
                Optional.empty(),
                currentDurability,
                maxDurability,
                Optional.empty(),
                Optional.empty(),
                Instant.parse("2026-01-01T00:00:00Z"),
                3);
    }
}
