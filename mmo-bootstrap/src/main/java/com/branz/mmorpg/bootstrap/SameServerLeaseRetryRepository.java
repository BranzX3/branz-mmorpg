package com.branz.mmorpg.bootstrap;

import com.branz.mmorpg.api.identity.CharacterId;
import com.branz.mmorpg.api.identity.SessionId;
import com.branz.mmorpg.api.result.Result;
import com.branz.mmorpg.persistence.lease.CharacterLease;
import com.branz.mmorpg.persistence.lease.CharacterLeaseRepository;
import com.branz.mmorpg.persistence.lease.LeaseAcquireOutcome;
import com.branz.mmorpg.persistence.lease.LeaseErrorCode;
import com.branz.mmorpg.persistence.lease.LeaseReleaseOutcome;
import com.branz.mmorpg.persistence.lease.ServerInstanceId;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Retries only the transient acquire race where the conflicting lease belongs to the same server
 * instance that is trying to acquire it. A lease owned by any other server is returned immediately
 * so cross-server ownership remains fail-closed.
 */
final class SameServerLeaseRetryRepository implements CharacterLeaseRepository {
    static final int DEFAULT_MAX_RETRIES = 50;
    static final Duration DEFAULT_RETRY_DELAY = Duration.ofMillis(100);

    private final CharacterLeaseRepository delegate;
    private final int maxRetries;
    private final Duration retryDelay;

    SameServerLeaseRetryRepository(CharacterLeaseRepository delegate) {
        this(delegate, DEFAULT_MAX_RETRIES, DEFAULT_RETRY_DELAY);
    }

    SameServerLeaseRetryRepository(
            CharacterLeaseRepository delegate, int maxRetries, Duration retryDelay) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must not be negative");
        }
        this.maxRetries = maxRetries;
        this.retryDelay = Objects.requireNonNull(retryDelay, "retryDelay");
        if (retryDelay.isNegative()) {
            throw new IllegalArgumentException("retryDelay must not be negative");
        }
    }

    @Override
    public Result<LeaseAcquireOutcome, LeaseErrorCode> acquire(
            CharacterId characterId,
            ServerInstanceId serverInstanceId,
            SessionId sessionId,
            Duration timeToLive) {
        int retries = 0;
        while (true) {
            Result<LeaseAcquireOutcome, LeaseErrorCode> result =
                    delegate.acquire(characterId, serverInstanceId, sessionId, timeToLive);
            if (!(result instanceof Result.Success<LeaseAcquireOutcome, LeaseErrorCode> success)) {
                return result;
            }
            LeaseAcquireOutcome outcome = success.value();
            if (!(outcome instanceof LeaseAcquireOutcome.Conflict conflict)
                    || !conflict.lease().serverInstanceId().equals(serverInstanceId)
                    || retries >= maxRetries) {
                return result;
            }
            retries++;
            if (retryDelay.isZero()) {
                continue;
            }
            try {
                Thread.sleep(retryDelay.toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return Result.failure(
                        LeaseErrorCode.LEASE_DATABASE_UNAVAILABLE,
                        "Lease acquire retry was interrupted.");
            }
        }
    }

    @Override
    public Result<CharacterLease, LeaseErrorCode> recoverExpired(
            CharacterId characterId,
            long expectedVersion,
            ServerInstanceId serverInstanceId,
            SessionId sessionId,
            Duration timeToLive) {
        return delegate.recoverExpired(
                characterId, expectedVersion, serverInstanceId, sessionId, timeToLive);
    }

    @Override
    public Result<CharacterLease, LeaseErrorCode> heartbeat(
            CharacterId characterId,
            ServerInstanceId serverInstanceId,
            SessionId sessionId,
            long expectedVersion,
            Duration timeToLive) {
        return delegate.heartbeat(
                characterId, serverInstanceId, sessionId, expectedVersion, timeToLive);
    }

    @Override
    public Result<LeaseReleaseOutcome, LeaseErrorCode> release(
            CharacterId characterId,
            ServerInstanceId serverInstanceId,
            SessionId sessionId,
            long expectedVersion) {
        return delegate.release(characterId, serverInstanceId, sessionId, expectedVersion);
    }

    @Override
    public Result<Optional<CharacterLease>, LeaseErrorCode> find(CharacterId characterId) {
        return delegate.find(characterId);
    }
}
