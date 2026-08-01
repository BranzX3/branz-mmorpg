package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.worldloop.reward.RewardIneligibilityReason;
import com.branz.mmorpg.worldloop.reward.RolledPersonalReward;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

record PersonalRewardReconciliation(
        UUID batchId,
        Map<CharacterId, RolledPersonalReward> delivered,
        Map<CharacterId, RewardIneligibilityReason> rejected) {
    PersonalRewardReconciliation {
        Objects.requireNonNull(batchId, "batchId");
        delivered = Map.copyOf(Objects.requireNonNull(delivered, "delivered"));
        rejected = Map.copyOf(Objects.requireNonNull(rejected, "rejected"));
    }
}
