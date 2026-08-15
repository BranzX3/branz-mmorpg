package com.branz.mmorpg.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

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
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SameServerLeaseRetryBudgetTest {
    private static final Duration TTL = Duration.ofSeconds(30);

    @Test
    void defaultBudgetCoversMoreThanOneSecondOfSameServerCloseLag() {
        CharacterId characterId = new CharacterId(java.util.UUID.randomUUID());
        ServerInstanceId local = new ServerInstanceId("local-server");
        SessionId staleSession = new SessionId(java.util.UUID.randomUUID());
        SessionId nextSession = new SessionId(java.util.UUID.randomUUID());
        CharacterLease stale = lease(characterId, local, staleSession, 1);
        CharacterLease acquired = lease(characterId, local, nextSession, 2);
        DelayedAcquireRepository delegate = new DelayedAcquireRepository(stale, acquired, 12);
        SameServerLeaseRetryRepository repository =
                new SameServerLeaseRetryRepository(
                        delegate,
                        SameServerLeaseRetryRepository.DEFAULT_MAX_RETRIES,
                        Duration.ZERO);

        Result<LeaseAcquireOutcome, LeaseErrorCode> result =
                repository.acquire(characterId, local, nextSession, TTL);

        assertInstanceOf(
                LeaseAcquireOutcome.Acquired.class,
                ((Result.Success<LeaseAcquireOutcome, LeaseErrorCode>) result).value());
        assertEquals(13, delegate.acquireCalls);
    }

    @Test
    void defaultBudgetRemainsBounded() {
        assertEquals(50, SameServerLeaseRetryRepository.DEFAULT_MAX_RETRIES);
        assertEquals(Duration.ofMillis(100), SameServerLeaseRetryRepository.DEFAULT_RETRY_DELAY);
    }

    private static CharacterLease lease(
            CharacterId characterId,
            ServerInstanceId serverInstanceId,
            SessionId sessionId,
            long version) {
        Instant now = Instant.parse("2026-08-15T00:00:00Z");
        return new CharacterLease(
                characterId, serverInstanceId, sessionId, version, now, now, now.plus(TTL));
    }

    private static final class DelayedAcquireRepository implements CharacterLeaseRepository {
        private final CharacterLease stale;
        private final CharacterLease acquired;
        private final int conflictsBeforeAcquire;
        private int acquireCalls;

        private DelayedAcquireRepository(
                CharacterLease stale, CharacterLease acquired, int conflictsBeforeAcquire) {
            this.stale = stale;
            this.acquired = acquired;
            this.conflictsBeforeAcquire = conflictsBeforeAcquire;
        }

        @Override
        public Result<LeaseAcquireOutcome, LeaseErrorCode> acquire(
                CharacterId characterId,
                ServerInstanceId serverInstanceId,
                SessionId sessionId,
                Duration timeToLive) {
            acquireCalls++;
            return Result.success(
                    acquireCalls <= conflictsBeforeAcquire
                            ? new LeaseAcquireOutcome.Conflict(stale)
                            : new LeaseAcquireOutcome.Acquired(acquired));
        }

        @Override
        public Result<CharacterLease, LeaseErrorCode> recoverExpired(
                CharacterId characterId,
                long expectedVersion,
                ServerInstanceId serverInstanceId,
                SessionId sessionId,
                Duration timeToLive) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<CharacterLease, LeaseErrorCode> heartbeat(
                CharacterId characterId,
                ServerInstanceId serverInstanceId,
                SessionId sessionId,
                long expectedVersion,
                Duration timeToLive) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<LeaseReleaseOutcome, LeaseErrorCode> release(
                CharacterId characterId,
                ServerInstanceId serverInstanceId,
                SessionId sessionId,
                long expectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Result<Optional<CharacterLease>, LeaseErrorCode> find(CharacterId characterId) {
            throw new UnsupportedOperationException();
        }
    }
}
