package com.branz.mmorpg.core.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.MaterialDefinition;
import com.branz.mmorpg.api.item.InventorySnapshot;
import com.branz.mmorpg.api.item.ItemCategory;
import com.branz.mmorpg.api.item.ItemInstance;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InventoryEngineTest {
    private static final UUID PLAYER =
            UUID.fromString("9a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f8");
    private static final ContentId ORE = ContentId.parse("branz:aether_ore");
    private static final Instant NOW = Instant.parse("2026-07-26T00:00:00Z");
    private final InventoryEngine engine = new InventoryEngine();

    @Test
    void fungibleGrantFillsStacksAndRoutesOverflowToPendingClaim() {
        MaterialDefinition ore = new MaterialDefinition(
                ORE, "Aether Ore", "ore", "common", true, 10);
        InventorySnapshot empty = InventorySnapshot.empty(PLAYER, 2, NOW);

        var result = engine.grantMaterial(empty, ore, 25, ignored -> 10, NOW);

        assertEquals(20, result.delivered());
        assertEquals(5, result.overflowed());
        assertEquals(20, result.snapshot().materials().get(ORE));
        assertEquals(5, result.snapshot().pendingMaterials().get(ORE));
    }

    @Test
    void uniqueItemNeverBecomesAnUntrackedWorldDropWhenFull() {
        InventorySnapshot full = new InventorySnapshot(PLAYER, 1,
                Map.of(ORE, 10L), Map.of(), Map.of(), Map.of(), Map.of(), NOW);
        ItemInstance weapon = item();

        var result = engine.grantUnique(full, weapon, ignored -> 10, NOW);

        assertEquals(0, result.delivered());
        assertEquals(1, result.overflowed());
        assertEquals(weapon, result.snapshot().pendingItems().get(weapon.instanceId()));
    }

    @Test
    void duplicateUniqueIdentityIsRejected() {
        ItemInstance weapon = item();
        InventorySnapshot owned = new InventorySnapshot(PLAYER, 10, Map.of(),
                Map.of(weapon.instanceId(), weapon), Map.of(), Map.of(), Map.of(), NOW);

        assertThrows(IllegalArgumentException.class,
                () -> engine.grantUnique(owned, weapon, ignored -> 10, NOW));
    }

    @Test
    void pendingMaterialClaimMovesOwnershipWithoutMinting() {
        MaterialDefinition ore = new MaterialDefinition(
                ORE, "Aether Ore", "ore", "common", true, 10);
        InventorySnapshot pending = new InventorySnapshot(PLAYER, 2, Map.of(), Map.of(),
                Map.of(), Map.of(ORE, 25L), Map.of(), NOW);

        var result = engine.claimMaterial(pending, ore, 25, ignored -> 10, NOW);

        assertEquals(20, result.delivered());
        assertEquals(20, result.snapshot().materials().get(ORE));
        assertEquals(5, result.snapshot().pendingMaterials().get(ORE));
    }

    @Test
    void pendingUniqueClaimFailsWithoutConsumingTheItemWhenFull() {
        ItemInstance weapon = item();
        InventorySnapshot full = new InventorySnapshot(PLAYER, 1, Map.of(ORE, 10L),
                Map.of(), Map.of(), Map.of(), Map.of(weapon.instanceId(), weapon), NOW);

        assertThrows(IllegalStateException.class,
                () -> engine.claimUnique(full, weapon.instanceId(), ignored -> 10, NOW));
        assertEquals(weapon, full.pendingItems().get(weapon.instanceId()));
    }

    private static ItemInstance item() {
        return new ItemInstance(
                UUID.fromString("8a1f2b3c-4d5e-6f70-8192-a3b4c5d6e7f1"),
                ContentId.parse("branz:broadsword"), ItemCategory.WEAPON, 42,
                Optional.of(PLAYER), 100, "loot:encounter-42", 1, NOW);
    }
}
