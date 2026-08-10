package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.ItemEngine;
import com.branz.mmorpg.items.projection.ObservedProjection;
import com.branz.mmorpg.persistence.transaction.ItemLocationRecord;
import java.util.Objects;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Resolves a selected Bukkit hotbar stack back to exact authoritative MMO item truth. */
final class BukkitPhysicalItemResolver {
    private final BukkitItemProjectionCodec codec;
    private final ItemEngine items;

    BukkitPhysicalItemResolver(BukkitItemProjectionCodec codec, ItemEngine items) {
        this.codec = Objects.requireNonNull(codec, "codec");
        this.items = Objects.requireNonNull(items, "items");
    }

    Result<ResolvedPhysicalItem, PhysicalItemResolutionErrorCode> resolveSelected(
            Player player, LoadedCharacterSession session) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(session, "session");
        int slot = player.getInventory().getHeldItemSlot();
        if (slot < 0 || slot >= ChronicleService.HOTBAR_SLOT) {
            return Result.failure(
                    PhysicalItemResolutionErrorCode.PHYSICAL_ITEM_SLOT_NOT_GAMEPLAY,
                    "Selected slot is not a gameplay hotbar slot 1-8.");
        }
        ItemStack stack = player.getInventory().getItem(slot);
        if (!codec.hasProjectionMarker(stack)) {
            return Result.failure(
                    PhysicalItemResolutionErrorCode.PHYSICAL_ITEM_NOT_MMO_PROJECTION,
                    "Selected stack is not an MMO item projection.");
        }
        ObservedProjection observed = codec.decode(stack, slot).orElse(null);
        if (observed == null) {
            return Result.failure(
                    PhysicalItemResolutionErrorCode.PHYSICAL_ITEM_PROJECTION_INVALID,
                    "Selected MMO projection is malformed or cannot be verified.");
        }
        Result<ItemLocationRecord, PhysicalItemResolutionErrorCode> authoritative =
                PhysicalInventoryAuthority.resolveUniqueItem(
                        session.characterId(), slot, observed, session.snapshot());
        if (authoritative
                instanceof
                Result.Failure<ItemLocationRecord, PhysicalItemResolutionErrorCode> failure) {
            return Result.failure(failure.error(), failure.detail());
        }
        ItemLocationRecord record =
                ((Result.Success<ItemLocationRecord, PhysicalItemResolutionErrorCode>)
                                authoritative)
                        .value();
        ItemDefinition definition = items.find(record.definitionId()).orElse(null);
        if (definition == null) {
            return Result.failure(
                    PhysicalItemResolutionErrorCode.PHYSICAL_ITEM_DEFINITION_MISSING,
                    "Selected item definition is absent from the active content snapshot.");
        }
        return Result.success(new ResolvedPhysicalItem(slot, observed, record, definition));
    }
}
