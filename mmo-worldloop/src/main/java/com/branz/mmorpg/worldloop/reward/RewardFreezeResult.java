package com.branz.mmorpg.worldloop.reward;

import com.branz.mmorpg.api.identity.CharacterId;
import java.util.Map;
import java.util.Objects;

public record RewardFreezeResult(
        Map<CharacterId, PersonalRewardGrant> grants,
        Map<CharacterId, RewardIneligibilityReason> rejected) {
    public RewardFreezeResult {
        grants = Map.copyOf(Objects.requireNonNull(grants, "grants"));
        rejected = Map.copyOf(Objects.requireNonNull(rejected, "rejected"));
        if (grants.keySet().stream().anyMatch(rejected::containsKey)) {
            throw new IllegalArgumentException("reward participant cannot be granted and rejected");
        }
        grants.forEach(
                (characterId, grant) -> {
                    if (!characterId.equals(grant.characterId())) {
                        throw new IllegalArgumentException("grant key must match character");
                    }
                });
    }
}
