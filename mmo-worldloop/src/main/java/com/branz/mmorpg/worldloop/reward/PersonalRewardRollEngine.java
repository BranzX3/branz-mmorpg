package com.branz.mmorpg.worldloop.reward;

import com.branz.mmorpg.api.identity.LotId;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

/** Weighted deterministic roll; retries consume no mutable RNG state. */
public final class PersonalRewardRollEngine {
    public RolledPersonalReward roll(EncounterRewardTable table, PersonalRewardGrant grant) {
        Objects.requireNonNull(table, "table");
        Objects.requireNonNull(grant, "grant");
        long totalWeight =
                table.entries().stream()
                        .mapToLong(RewardTableEntry::weight)
                        .reduce(0L, Math::addExact);
        long cursor = Math.floorMod(grant.rollSeed(), totalWeight);
        RewardTableEntry selected = null;
        for (RewardTableEntry entry : table.entries()) {
            if (cursor < entry.weight()) {
                selected = entry;
                break;
            }
            cursor -= entry.weight();
        }
        RewardTableEntry entry = Objects.requireNonNull(selected, "selected reward entry");
        long range =
                Math.addExact(
                        Math.subtractExact(entry.maximumQuantity(), entry.minimumQuantity()), 1);
        long quantity =
                Math.addExact(
                        entry.minimumQuantity(),
                        Math.floorMod(Long.rotateLeft(grant.rollSeed(), 23), range));
        UUID lotUuid =
                UUID.nameUUIDFromBytes(
                        ("personal-reward-lot:" + grant.grantId())
                                .getBytes(StandardCharsets.UTF_8));
        return new RolledPersonalReward(entry.itemDefinitionId(), quantity, new LotId(lotUuid));
    }
}
