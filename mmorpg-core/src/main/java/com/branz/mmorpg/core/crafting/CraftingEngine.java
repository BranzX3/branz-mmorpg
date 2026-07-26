package com.branz.mmorpg.core.crafting;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.crafting.CraftJob;
import com.branz.mmorpg.api.crafting.RecipeDefinition;
import com.branz.mmorpg.api.item.InventorySnapshot;
import com.branz.mmorpg.api.item.ItemCategory;
import com.branz.mmorpg.api.item.ItemInstance;
import com.branz.mmorpg.core.item.InventoryEngine;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Pure material escrow, refund, and deterministic output rules. */
public final class CraftingEngine {
    private final InventoryEngine inventory = new InventoryEngine();

    public InventorySnapshot consume(
            InventorySnapshot before, Map<ContentId, Long> escrow, Instant now) {
        Map<ContentId, Long> materials = new HashMap<>(before.materials());
        escrow.forEach((id, amount) -> {
            long held = materials.getOrDefault(id, 0L);
            if (held < amount) throw new IllegalStateException("MISSING_INPUT " + id);
            long remaining = held - amount;
            if (remaining == 0) materials.remove(id);
            else materials.put(id, remaining);
        });
        return copy(before, materials, now);
    }

    public InventorySnapshot refund(
            InventorySnapshot before, Map<ContentId, Long> escrow,
            ContentSnapshot content, Instant now) {
        InventorySnapshot current = before;
        for (var entry : escrow.entrySet()) {
            var definition = content.materials().get(entry.getKey());
            if (definition == null) throw new IllegalStateException(
                    "missing escrow material definition " + entry.getKey());
            current = inventory.grantMaterial(current, definition, entry.getValue(),
                    id -> stackSize(content, id), now).snapshot();
        }
        return current;
    }

    public InventorySnapshot deliver(
            InventorySnapshot before, CraftJob job,
            ContentSnapshot content, Instant now) {
        var material = content.materials().get(job.outputItemId());
        if (material != null) {
            return inventory.grantMaterial(before, material, job.outputQuantity(),
                    id -> stackSize(content, id), now).snapshot();
        }
        var weapon = content.weapons().get(job.outputItemId());
        if (weapon == null) throw new IllegalStateException(
                "missing craft output definition " + job.outputItemId());
        InventorySnapshot current = before;
        for (long index = 0; index < job.outputQuantity(); index++) {
            String durable = job.operationId().value() + ':' + index;
            UUID instanceId = UUID.nameUUIDFromBytes(durable.getBytes(StandardCharsets.UTF_8));
            ItemInstance item = new ItemInstance(instanceId, weapon.id(), ItemCategory.WEAPON,
                    durable.hashCode(),
                    job.outputBinding() == RecipeDefinition.Output.Binding.BIND_ON_CREATE
                            ? Optional.of(job.playerId()) : Optional.empty(),
                    100, "craft:" + job.operationId(), 1, now);
            current = inventory.grantUnique(
                    current, item, id -> stackSize(content, id), now).snapshot();
        }
        return current;
    }

    private static int stackSize(ContentSnapshot content, ContentId id) {
        var definition = content.materials().get(id);
        return definition == null ? 0 : definition.maxStackSize();
    }

    private static InventorySnapshot copy(
            InventorySnapshot before, Map<ContentId, Long> materials, Instant now) {
        return new InventorySnapshot(before.playerId(), before.slotCapacity(), materials,
                before.items(), before.equipped(), before.pendingMaterials(),
                before.pendingItems(), now);
    }
}
