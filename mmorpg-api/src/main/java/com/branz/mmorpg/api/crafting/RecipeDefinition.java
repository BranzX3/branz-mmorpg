package com.branz.mmorpg.api.crafting;

import com.branz.mmorpg.api.content.ContentDefinition;
import com.branz.mmorpg.api.content.ContentId;
import com.branz.mmorpg.api.content.ContentType;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record RecipeDefinition(
        ContentId id,
        String displayName,
        Map<ContentId, Long> inputs,
        Map<ContentId, Long> optionalCatalysts,
        long coinFee,
        String stationTag,
        Optional<ContentId> professionId,
        int requiredProfessionLevel,
        long durationMillis,
        Output output,
        long professionXp,
        int trivialAfterLevel) implements ContentDefinition {

    public RecipeDefinition {
        Objects.requireNonNull(id, "id");
        displayName = Objects.requireNonNull(displayName, "displayName").trim();
        inputs = positiveCopy(inputs, "inputs");
        optionalCatalysts = positiveCopy(optionalCatalysts, "optionalCatalysts");
        stationTag = Objects.requireNonNull(stationTag, "stationTag").trim();
        Objects.requireNonNull(professionId, "professionId");
        Objects.requireNonNull(output, "output");
        if (displayName.isEmpty() || inputs.isEmpty() || coinFee < 0 || stationTag.isEmpty()
                || requiredProfessionLevel < 1 || durationMillis < 0 || professionXp < 0
                || trivialAfterLevel < requiredProfessionLevel
                || professionId.isEmpty() && requiredProfessionLevel > 1) {
            throw new IllegalArgumentException("invalid recipe " + id);
        }
    }

    @Override public ContentType type() { return ContentType.RECIPE; }

    private static Map<ContentId, Long> positiveCopy(
            Map<ContentId, Long> values, String field) {
        Objects.requireNonNull(values, field);
        values.forEach((id, amount) -> {
            if (id == null || amount == null || amount <= 0) {
                throw new IllegalArgumentException(field + " must be positive");
            }
        });
        return Map.copyOf(values);
    }

    public record Output(
            ContentId itemId,
            long quantity,
            Binding binding,
            String qualityPolicy) {
        public enum Binding { UNBOUND, BIND_ON_CREATE }

        public Output {
            Objects.requireNonNull(itemId, "itemId");
            Objects.requireNonNull(binding, "binding");
            qualityPolicy = Objects.requireNonNull(qualityPolicy, "qualityPolicy").trim();
            if (quantity < 1 || qualityPolicy.isEmpty()) {
                throw new IllegalArgumentException("invalid recipe output");
            }
        }
    }
}
