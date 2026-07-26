package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;

record RawProfessionDefinition(
        String type,
        String id,
        @JsonProperty("display_name") String displayName,
        @JsonProperty("maximum_level") int maximumLevel,
        @JsonProperty("curve_base") double curveBase,
        @JsonProperty("curve_exponent") double curveExponent) {
}
