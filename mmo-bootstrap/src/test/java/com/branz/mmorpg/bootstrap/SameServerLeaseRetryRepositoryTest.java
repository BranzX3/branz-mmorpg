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
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SameServerLeaseRetryRepositoryTest {
    private static final Duration TTL = Duration.ofSeconds(30);

    @Test
    void retriesTransientConflictOwnedByRequestedServerInstance() {
        CharacterId characterId = new CharacterId(java.util.UUID.randomUUID());
        ServerInstanceId local = new ServerInstanceId("local-server");
        SessionId staleSession = new SessionId(java.util.UUID.randomUUID());
        SessionId nextSession = new SessionId(java.util.UUID.randomUUID());
        CharacterLease stale = lease(characterId, local, staleSession, 1);
        CharacterLease acquired = lease(characterId, local, nextSession, 2);
        SequencedLeaseRepository delegate =
                new SequencedLeaseRepository(
                        List.of(
                                new LeaseAcquireOutcome.Conflict(stale),
                                new LeaseAcquireOutcome.Acquired(acquired)));
        SameServerLeaseRetryRepository repository =
                new SameServerLeaseRetryRepository(delegate, 3, Duration.ZERO);

        Result<LeaseAcquireOutcome, LeaseErrorCode> result =
                repository.acquire(characterId, local, nextSession, TTL);

        assertInstanceOf(LeaseAcquireOutcome.Acquired.class, success(result));
        assertEquals(2, delegate.acquireCalls);
    }

    @Test
    void returnsRemoteOwnerConflictWithoutRetry() {
        CharacterId characterId = new CharacterId(java.util.UUID.randomUUID());
        ServerInstanceId local = new ServerInstanceId("local-server");
        ServerInstanceId remote = new ServerInstanceId("remote-server");
        SessionId remoteSession = new SessionId(java.util.UUID.randomUUID());
        SessionId requestedSession = new SessionId(java.util.UUID.randomUUID());
        CharacterLease heldElsewhere = lease(characterId, remote, remoteSession, 1);
        SequencedLeaseRepository delegate =
                new SequencedLeaseRepository(
                        List.of(new LeaseAcquireOutcome.Conflict(heldElsewhere)));
        SameServerLeaseRetryRepository repository =
                new SameServerLeaseRetryRepository(delegate, 10, Duration.ZERO);

        Result<LeaseAcquireOutcome, LeaseErrorCode> result =
                repository.acquire(characterId, local, requestedSession, TTL);

        assertInstanceOf(LeaseAcquireOutcome.Conflict.class, success(result));
        assertEquals(1, delegate.acquireCalls);
    }

    @Test
    void stopsAfterConfiguredRetryBoundWhenLocalConflictPersists() {
        CharacterId characterId = new CharacterId(java.util.UUID.randomUUID());
        ServerInstanceId local = new ServerInstanceId("local-server");
        SessionId staleSession = new SessionId(java.util.UUID.randomUUID());
        SessionId requestedSession = new SessionId(java.util.UUID.randomUUID());
        CharacterLease stale = lease(characterId, local, staleSession, 1);
        SequencedLeaseRepository delegate =
                new SequencedLeaseRepository(List.of(new LeaseAcquireOutcome.Conflict(stale)));
        SameServerLeaseRetryRepository repository =
                new SameServerLeaseRetryRepository(delegate, 2, Duration.ZERO);

        Result<LeaseAcquireOutcome, LeaseErrorCode> result =
                repository.acquire(characterId, local, requestedSession, TTL);

        assertInstanceOf(LeaseAcquireOutcome.Conflict.class, success(result));
        assertEquals(3, delegate.acquireCalls);
    }

    private static LeaseAcquireOutcome success(Result<LeaseAcquireOutcome, LeaseErrorCode> result) {
        if (result instanceof Result.Success<LeaseAcquireOutcome, LeaseErrorCode> success) {
            return success.value();
        }
        Result.Failure<LeaseAcquireOutcome, LeaseErrorCode> failure =
                (Result.Failure<LeaseAcquireOutcome, LeaseErrorCode>) result;
        throw new AssertionError(failure.error().code() + ": " + failure.detail());
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

    private static final class SequencedLeaseRepository implements CharacterLeaseRepository {
        private final List<LeaseAcquireOutcome> outcomes;
        private int acquireCalls;

        private SequencedLeaseRepository(List<LeaseAcquireOutcome> outcomes) {
            this.outcomes = List.copyOf(outcomes);
        }

        @Override
        public Result<LeaseAcquireOutcome, LeaseErrorCode> acquire(
                CharacterId characterId,
                ServerInstanceId serverInstanceId,
                SessionId sessionId,
                Duration timeToLive) {
            int index = Math.min(acquireCalls, outcomes.size() - 1);
            acquireCalls++;
            return Result.success(outcomes.get(index));
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
