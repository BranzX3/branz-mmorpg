package com.branz.mmorpg.persistence.lease;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.SessionId;
import com.branz.mmorpg.api.result.Result;
import java.time.Duration;
import java.util.Optional;

public interface CharacterLeaseRepository {
    Result<LeaseAcquireOutcome, LeaseErrorCode> acquire(
            CharacterId characterId,
            ServerInstanceId serverInstanceId,
            SessionId sessionId,
            Duration timeToLive);

    Result<CharacterLease, LeaseErrorCode> recoverExpired(
            CharacterId characterId,
            long expectedVersion,
            ServerInstanceId serverInstanceId,
            SessionId sessionId,
            Duration timeToLive);

    Result<CharacterLease, LeaseErrorCode> heartbeat(
            CharacterId characterId,
            ServerInstanceId serverInstanceId,
            SessionId sessionId,
            long expectedVersion,
            Duration timeToLive);

    Result<LeaseReleaseOutcome, LeaseErrorCode> release(
            CharacterId characterId,
            ServerInstanceId serverInstanceId,
            SessionId sessionId,
            long expectedVersion);

    Result<Optional<CharacterLease>, LeaseErrorCode> find(CharacterId characterId);
}
