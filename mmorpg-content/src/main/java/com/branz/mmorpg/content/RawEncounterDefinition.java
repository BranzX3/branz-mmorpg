package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

record RawEncounterDefinition(
        String type,
        String id,
        @JsonProperty("display_name") String displayName,
        String mode,
        @JsonProperty("boss_mob") String bossMob,
        List<RawEncounterPhase> phases,
        @JsonProperty("arena_radius") double arenaRadius,
        @JsonProperty("preparation_ms") long preparationMillis,
        @JsonProperty("wipe_grace_ms") long wipeGraceMillis,
        @JsonProperty("enrage_ms") long enrageMillis,
        @JsonProperty("minimum_players") int minimumPlayers,
        @JsonProperty("maximum_players") int maximumPlayers,
        @JsonProperty("minimum_contribution") double minimumContribution,
        @JsonProperty("party_policy") String partyPolicy,
        @JsonProperty("checkpoints_allowed") boolean checkpointsAllowed,
        @JsonProperty("reward_loot_table") String rewardLootTable) {
}
