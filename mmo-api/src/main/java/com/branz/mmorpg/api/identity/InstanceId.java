package com.branz.mmorpg.api.identity;

import java.util.UUID;

/** Marker for globally unique persistent instance identities. */
public sealed interface InstanceId
        permits ItemId, LotId, MountId, WorkerId, TransactionId, EncounterId {
    UUID value();
}
