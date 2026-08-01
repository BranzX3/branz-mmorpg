package com.branz.mmorpg.lifeskills.node;

import com.branz.mmorpg.api.identity.DefinitionId;
import com.branz.mmorpg.lifeskills.progression.LifeskillDiscipline;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;

/** Authored rules for one resource-node definition. */
public record ResourceNodeDefinition(
        DefinitionId id,
        LifeskillDiscipline discipline,
        ResourceNodeType type,
        ResourceNodeSharing sharing,
        int maximumCharges,
        long workDurationTicks,
        Duration reservationTimeout,
        Duration recoveryDuration,
        int durabilityCost,
        Set<String> requiredToolTags) {
    public ResourceNodeDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(discipline, "discipline");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sharing, "sharing");
        Objects.requireNonNull(reservationTimeout, "reservationTimeout");
        Objects.requireNonNull(recoveryDuration, "recoveryDuration");
        requiredToolTags = Set.copyOf(Objects.requireNonNull(requiredToolTags, "requiredToolTags"));
        if (!id.value().startsWith("node.")) {
            throw new IllegalArgumentException("resource node definition ID must start with node.");
        }
        if (maximumCharges < 1
                || workDurationTicks < 1
                || reservationTimeout.isZero()
                || reservationTimeout.isNegative()
                || recoveryDuration.isZero()
                || recoveryDuration.isNegative()
                || durabilityCost < 1) {
            throw new IllegalArgumentException(
                    "invalid resource node timing, charges or durability");
        }
        if (type == ResourceNodeType.COMMON && sharing != ResourceNodeSharing.PERSONAL) {
            throw new IllegalArgumentException("common nodes must use personal extraction state");
        }
        if ((type == ResourceNodeType.RICH || type == ResourceNodeType.RARE)
                && sharing != ResourceNodeSharing.SHARED) {
            throw new IllegalArgumentException(
                    "rich and rare nodes must use shared extraction state");
        }
        if (requiredToolTags.stream().anyMatch(tag -> tag == null || tag.isBlank())) {
            throw new IllegalArgumentException("required tool tags must not be blank");
        }
    }
}
