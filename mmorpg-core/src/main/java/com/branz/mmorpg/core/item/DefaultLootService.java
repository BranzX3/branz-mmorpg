package com.branz.mmorpg.core.item;

import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentSnapshot;
import com.branz.mmorpg.api.item.InventoryMutationCommit;
import com.branz.mmorpg.api.item.InventoryService;
import com.branz.mmorpg.api.item.ItemCategory;
import com.branz.mmorpg.api.item.ItemInstance;
import com.branz.mmorpg.api.item.LootDefinition;
import com.branz.mmorpg.api.item.LootRollResult;
import com.branz.mmorpg.api.item.LootService;
import com.branz.mmorpg.api.operation.OperationId;
import com.branz.mmorpg.api.runtime.GameClock;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Retry-safe personal loot delivery.
 *
 * <p>The durable roll ID determines both RNG and item UUIDs. Each merged loot
 * entry has its own operation ID, so a crash between entries resumes without
 * rerolling or duplicating completed grants.
 */
public final class DefaultLootService implements LootService {
    private final InventoryService inventory;
    private final Supplier<ContentSnapshot> content;
    private final GameClock clock;
    private final LootEngine engine = new LootEngine();

    public DefaultLootService(InventoryService inventory,
                              Supplier<ContentSnapshot> content, GameClock clock) {
        this.inventory = Objects.requireNonNull(inventory, "inventory");
        this.content = Objects.requireNonNull(content, "content");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public LootRollResult resolvePersonal(
            UUID playerId, ContentId lootTableId, String durableRollId,
            boolean contributionEligible, Set<String> conditions,
            Map<String, Integer> pityMisses) {
        if (durableRollId == null || durableRollId.isBlank()) {
            throw new IllegalArgumentException("durableRollId must not be blank");
        }
        LootDefinition table = content.get().lootTables().get(lootTableId);
        if (table == null) throw new IllegalArgumentException("unknown loot table " + lootTableId);
        if (table.ownership() != LootDefinition.Ownership.PERSONAL) {
            throw new IllegalArgumentException("loot table is not personal");
        }
        var awards = engine.resolve(table, seed(durableRollId), contributionEligible,
                Set.copyOf(conditions), Map.copyOf(pityMisses));
        return deliver(playerId, lootTableId, durableRollId,
                contributionEligible, awards);
    }

    @Override
    public Map<UUID, LootRollResult> resolveParty(
            Set<UUID> eligiblePlayers, ContentId lootTableId, String durableRollId,
            Set<String> conditions, Map<String, Integer> pityMisses) {
        if (durableRollId == null || durableRollId.isBlank()) {
            throw new IllegalArgumentException("durableRollId must not be blank");
        }
        LootDefinition table = content.get().lootTables().get(lootTableId);
        if (table == null) throw new IllegalArgumentException("unknown loot table " + lootTableId);
        if (table.ownership() != LootDefinition.Ownership.PARTY) {
            throw new IllegalArgumentException("loot table is not party-owned");
        }
        List<UUID> players = eligiblePlayers.stream().sorted().toList();
        if (players.isEmpty()) return Map.of();
        var awards = engine.resolve(table, seed(durableRollId), true,
                Set.copyOf(conditions), Map.copyOf(pityMisses));
        HashMap<UUID, java.util.ArrayList<com.branz.mmorpg.api.item.LootAward>>
                assigned = new HashMap<>();
        for (var award : awards) {
            int index = Math.floorMod((int) seed(
                    durableRollId + ":owner:" + award.entryId()), players.size());
            assigned.computeIfAbsent(players.get(index), ignored ->
                    new java.util.ArrayList<>()).add(award);
        }
        HashMap<UUID, LootRollResult> results = new HashMap<>();
        players.forEach(player -> results.put(player, deliver(
                player, lootTableId, durableRollId, true,
                assigned.getOrDefault(player, new java.util.ArrayList<>()))));
        return Map.copyOf(results);
    }

    private LootRollResult deliver(
            UUID playerId, ContentId lootTableId, String durableRollId,
            boolean contributionEligible,
            java.util.List<com.branz.mmorpg.api.item.LootAward> awards) {
        boolean applied = false;
        long delivered = 0;
        long overflowed = 0;
        for (var award : awards) {
            var definition = content.get().find(award.itemId()).orElseThrow();
            if (definition instanceof com.branz.mmorpg.api.content.MaterialDefinition) {
                InventoryMutationCommit result = inventory.grantMaterial(
                        playerId, award.itemId(), award.quantity(),
                        operation(playerId, lootTableId, durableRollId, award.entryId()));
                applied |= result.applied();
                delivered = Math.addExact(delivered, result.delivered());
                overflowed = Math.addExact(overflowed, result.overflowed());
            } else if (definition instanceof com.branz.mmorpg.api.item.WeaponDefinition) {
                for (long index = 0; index < award.quantity(); index++) {
                    String entryKey = award.entryId() + '-' + index;
                    ItemInstance item = new ItemInstance(
                            deterministicUuid(durableRollId + ':' + entryKey),
                            award.itemId(), ItemCategory.WEAPON,
                            seed(durableRollId + ":quality:" + entryKey),
                            Optional.of(playerId), 100,
                            "loot:" + durableRollId, 1, clock.now());
                    InventoryMutationCommit result = inventory.grantUnique(
                            playerId, item,
                            operation(playerId, lootTableId, durableRollId, entryKey));
                    applied |= result.applied();
                    delivered = Math.addExact(delivered, result.delivered());
                    overflowed = Math.addExact(overflowed, result.overflowed());
                }
            } else {
                throw new IllegalArgumentException(
                        "unsupported loot definition " + definition.id());
            }
        }
        return new LootRollResult(durableRollId, contributionEligible, applied,
                awards, delivered, overflowed);
    }

    private static OperationId operation(UUID playerId, ContentId tableId,
                                         String rollId, String entryId) {
        return OperationId.of("loot", tableId.toString(), playerId,
                digest(rollId + ':' + entryId).substring(0, 32));
    }

    private static long seed(String value) {
        return ByteBuffer.wrap(hexBytes(digest(value).substring(0, 16))).getLong();
    }

    private static UUID deterministicUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static byte[] hexBytes(String value) {
        return java.util.HexFormat.of().parseHex(value);
    }
}
