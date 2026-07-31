package com.branz.mmorpg.persistence.transaction;

import java.util.Objects;
import java.util.Optional;

public record ValueLocation(ValueLocationType type, Optional<String> reference) {
    public ValueLocation {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(reference, "reference");
        reference =
                reference.map(
                        value -> {
                            if (value.isBlank()) {
                                throw new IllegalArgumentException(
                                        "location reference must not be blank");
                            }
                            return value;
                        });
    }

    public static ValueLocation inventory(String slotReference) {
        return new ValueLocation(
                ValueLocationType.CHARACTER_INVENTORY,
                Optional.of(Objects.requireNonNull(slotReference, "slotReference")));
    }

    public static ValueLocation pendingRewards(String grantReference) {
        return new ValueLocation(
                ValueLocationType.PENDING_REWARDS,
                Optional.of(Objects.requireNonNull(grantReference, "grantReference")));
    }

    public static ValueLocation nativeEquipped(String slotReference) {
        return new ValueLocation(
                ValueLocationType.NATIVE_EQUIPPED,
                Optional.of(Objects.requireNonNull(slotReference, "slotReference")));
    }

    public static ValueLocation virtualEquipped(String slotReference) {
        return new ValueLocation(
                ValueLocationType.VIRTUAL_EQUIPPED,
                Optional.of(Objects.requireNonNull(slotReference, "slotReference")));
    }

    public static ValueLocation overflowClaim(String claimReference) {
        return new ValueLocation(
                ValueLocationType.OVERFLOW_CLAIM,
                Optional.of(Objects.requireNonNull(claimReference, "claimReference")));
    }

    public static ValueLocation quarantine(String caseReference) {
        return new ValueLocation(
                ValueLocationType.QUARANTINE,
                Optional.of(Objects.requireNonNull(caseReference, "caseReference")));
    }

    public static ValueLocation destroyed(String transactionReference) {
        return new ValueLocation(
                ValueLocationType.DESTROYED,
                Optional.of(Objects.requireNonNull(transactionReference, "transactionReference")));
    }
}
