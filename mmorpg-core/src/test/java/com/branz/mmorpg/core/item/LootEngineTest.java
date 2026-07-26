package com.branz.mmorpg.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.item.LootDefinition;
import com.branz.mmorpg.api.item.LootEntry;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LootEngineTest {
    private static final ContentId ORE = ContentId.parse("branz:aether_ore");
    private final LootEngine engine = new LootEngine();

    @Test
    void sameDurableSeedAlwaysProducesSameRoll() {
        LootDefinition table = table();
        var first = engine.resolve(table, 42, true, Set.of(), Map.of());
        var retry = engine.resolve(table, 42, true, Set.of(), Map.of());

        assertEquals(first, retry);
        assertTrue(first.stream().anyMatch(award -> award.entryId().equals("guaranteed")));
    }

    @Test
    void contributionGateFailsClosedAndPityForcesEligibleEntry() {
        LootDefinition table = table();
        assertTrue(engine.resolve(table, 42, false, Set.of(), Map.of()).isEmpty());

        var pity = engine.resolve(table, 42, true, Set.of(), Map.of("rare", 5));
        assertTrue(pity.stream().anyMatch(award -> award.entryId().equals("rare")));
    }

    private static LootDefinition table() {
        return new LootDefinition(ContentId.parse("branz:test_loot"), "Test",
                LootDefinition.Ownership.PERSONAL, 1, true, List.of(
                new LootEntry("guaranteed", ORE, 0, true, 1, 1, Set.of(), 0, 1),
                new LootEntry("common", ORE, 90, false, 1, 2, Set.of(), 0, 2),
                new LootEntry("rare", ContentId.parse("branz:broadsword"),
                        10, false, 1, 1, Set.of(), 5, 1)));
    }
}
