package com.branz.mmorpg.core.item;

import com.branz.mmorpg.api.item.LootAward;
import com.branz.mmorpg.api.item.LootDefinition;
import com.branz.mmorpg.api.item.LootEntry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;

/** Deterministic one-shot loot resolution from a durable roll seed. */
public final class LootEngine {

    public List<LootAward> resolve(LootDefinition table, long seed, boolean eligible,
                                   Set<String> conditions, Map<String, Integer> pityMisses) {
        if (table.contributionRequired() && !eligible) return List.of();
        SplittableRandom random = new SplittableRandom(seed);
        List<LootEntry> candidates = table.entries().stream()
                .filter(entry -> conditions.containsAll(entry.requiredConditions()))
                .toList();
        List<LootAward> awards = new ArrayList<>();
        for (LootEntry entry : candidates) {
            if (entry.guaranteed()
                    || entry.pityAfter() > 0
                    && pityMisses.getOrDefault(entry.entryId(), 0) >= entry.pityAfter()) {
                awards.add(award(entry, random));
            }
        }
        List<LootEntry> weighted = candidates.stream().filter(entry -> !entry.guaranteed()).toList();
        double totalWeight = weighted.stream().mapToDouble(LootEntry::weight).sum();
        for (int roll = 0; roll < table.weightedRolls() && totalWeight > 0; roll++) {
            double selected = random.nextDouble(totalWeight);
            double cursor = 0;
            for (LootEntry entry : weighted) {
                cursor += entry.weight();
                if (selected < cursor) {
                    awards.add(award(entry, random));
                    break;
                }
            }
        }
        return mergeAndCap(awards, candidates);
    }

    private static LootAward award(LootEntry entry, SplittableRandom random) {
        long bound = Math.addExact(Math.subtractExact(
                entry.maximumQuantity(), entry.minimumQuantity()), 1);
        long quantity = entry.minimumQuantity() + random.nextLong(bound);
        return new LootAward(entry.itemId(), quantity, entry.entryId());
    }

    private static List<LootAward> mergeAndCap(
            List<LootAward> awards, List<LootEntry> definitions) {
        Map<String, LootEntry> byId = definitions.stream().collect(
                java.util.stream.Collectors.toMap(LootEntry::entryId, entry -> entry));
        Map<String, LootAward> merged = new java.util.LinkedHashMap<>();
        for (LootAward award : awards) {
            LootEntry definition = byId.get(award.entryId());
            merged.merge(award.entryId(), award, (left, right) ->
                    new LootAward(left.itemId(),
                            Math.min(definition.perRollCap(),
                                    Math.addExact(left.quantity(), right.quantity())),
                            left.entryId()));
        }
        return List.copyOf(merged.values());
    }
}
