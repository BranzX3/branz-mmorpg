package com.branz.mmorpg.content;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

record RawCombatInputProfileDefinition(
        String type,
        String id,
        long revision,
        Map<String, String> bindings,
        @JsonProperty("combo-window-millis") long comboWindowMillis,
        @JsonProperty("input-buffer-millis") long inputBufferMillis) {
}
