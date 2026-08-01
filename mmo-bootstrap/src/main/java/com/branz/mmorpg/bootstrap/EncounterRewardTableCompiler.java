package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.content.definition.ContentDefinition;
import com.branz.mmorpg.content.schema.DefinitionType;
import com.branz.mmorpg.content.snapshot.ContentSnapshot;
import com.branz.mmorpg.items.definition.ItemClass;
import com.branz.mmorpg.items.definition.ItemDefinition;
import com.branz.mmorpg.items.definition.ItemEngine;
import com.branz.mmorpg.worldloop.reward.EncounterRewardTable;
import com.branz.mmorpg.worldloop.reward.RewardEligibilityProfile;
import com.branz.mmorpg.worldloop.reward.RewardTableEntry;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Compiles validated encounter content into immutable personal reward tables. */
final class EncounterRewardTableCompiler {
    private EncounterRewardTableCompiler() {}

    static Map<DefinitionId, EncounterRewardTable> compile(
            ContentSnapshot snapshot, ItemEngine items) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(items, "items");
        LinkedHashMap<DefinitionId, EncounterRewardTable> tables = new LinkedHashMap<>();
        for (ContentDefinition source : snapshot.definitions().byType(DefinitionType.ENCOUNTER)) {
            JsonNode body = source.body();
            JsonNode eligibility = body.path("eligibility");
            RewardEligibilityProfile profile =
                    new RewardEligibilityProfile(
                            requiredLong(eligibility, "damage_and_posture_floor"),
                            requiredLong(eligibility, "guard_and_control_floor"),
                            requiredLong(eligibility, "healing_and_support_floor"),
                            requiredLong(eligibility, "objective_action_floor"),
                            requiredLong(eligibility, "maximum_idle_ticks"));
            ArrayList<RewardTableEntry> entries = new ArrayList<>();
            for (JsonNode entry : body.path("reward_pool").path("entries")) {
                DefinitionId itemId = DefinitionId.of(entry.path("item").asText(""));
                ItemDefinition item =
                        items.find(itemId)
                                .orElseThrow(
                                        () ->
                                                new IllegalArgumentException(
                                                        source.id()
                                                                + " reward item is unavailable: "
                                                                + itemId));
                if (item.itemClass() != ItemClass.STACKABLE_LOT) {
                    throw new IllegalArgumentException(
                            source.id() + " reward item must be STACKABLE_LOT: " + itemId);
                }
                entries.add(
                        new RewardTableEntry(
                                itemId,
                                requiredLong(entry, "weight"),
                                requiredLong(entry, "min_quantity"),
                                requiredLong(entry, "max_quantity")));
            }
            EncounterRewardTable table =
                    new EncounterRewardTable(
                            source.id(),
                            profile,
                            eligibility.path("late_join_hp_ratio").doubleValue(),
                            entries);
            tables.put(source.id(), table);
        }
        return Map.copyOf(tables);
    }

    private static long requiredLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.canConvertToLong()) {
            throw new IllegalArgumentException("reward field must be an integer: " + field);
        }
        return value.longValue();
    }
}
