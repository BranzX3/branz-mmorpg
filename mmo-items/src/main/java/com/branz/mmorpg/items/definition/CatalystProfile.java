package com.branz.mmorpg.items.definition;

import java.util.Objects;
import java.util.Set;

public record CatalystProfile(
        Set<String> tags, double channelStability, int durabilityCostPerCommit) {
    public CatalystProfile {
        tags = Set.copyOf(Objects.requireNonNull(tags, "tags"));
        if (tags.isEmpty()
                || tags.stream().anyMatch(tag -> tag == null || tag.isBlank())
                || !Double.isFinite(channelStability)
                || channelStability < 0
                || channelStability > 1
                || durabilityCostPerCommit < 1) {
            throw new IllegalArgumentException("invalid catalyst profile");
        }
    }
}
