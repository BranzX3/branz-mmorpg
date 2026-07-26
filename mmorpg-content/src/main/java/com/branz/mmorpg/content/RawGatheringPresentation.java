package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;

record RawGatheringPresentation(
        @JsonProperty("available_block") String availableBlock,
        @JsonProperty("depleted_block") String depletedBlock,
        String hologram) {
}
