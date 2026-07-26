package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;

record RawMasteryDefinition(
        String type,
        String id,
        @JsonProperty("display-name") String displayName,
        String kind,
        String parent,
        @JsonProperty("maximum-level") int maximumLevel,
        @JsonProperty("curve-base") double curveBase,
        @JsonProperty("curve-exponent") double curveExponent,
        @JsonProperty("maximum-power-bonus") double maximumPowerBonus,
        @JsonProperty("tree-revision") int treeRevision) {
}
