# ADR 0034: Persist-before-effect downed recovery

- Status: Accepted
- Date: 2026-08-01
- Owners: Bootstrap, Combat, Social and Persistence

## Context

The live adapter previously changed combat/death state immediately and held its kernel runtime only
in memory. V0010 plus the canonical codec now make durable ordering possible, but concurrent lethal,
revive, interruption and clock signals must not overwrite each other or publish an uncommitted
effect.

## Decision

Each encounter attempt owns one main-thread FIFO mutation queue. A queued input is recalculated from
the latest acknowledged runtime, then create/compare-and-set is executed asynchronously with one
stable operation UUID, transition tick and payload across retries. Only the main-thread success
callback installs state and applies death, heal, channel UI or protection effects.

For party lethal damage, combat enters a hard-locked pending sentinel instead of firing Bukkit death.
After V0010 commits, a first defeat remains DOWNED; Execute, expiry or a non-revivable defeat becomes
real death and continues through the existing durable V0009 boss path. Database failure retains the
queue head and sentinel, publishes no transition effect and retries with the identical request.

While downed, channel or protection time is active, V0010 checkpoints every 20 server ticks when no
semantic mutation is pending. Startup waits for V0009 boss recovery, loads V0010, validates attempt
and participant identity, rebases deadlines and restores online participant state. Rows without a
matching active boss attempt are marked non-recoverable.

## Consequences

- simultaneous inputs cannot lose a committed revive allowance or revive twice;
- callback loss can replay the exact transaction without an idempotency conflict;
- restart preserves remaining online time and participant life state;
- PostgreSQL latency holds a lethal player safely action-locked rather than exposing an uncommitted
  death/downed result.

## Failure and recovery

Invalid kernel input discards only that queue item. A failed durable write remains at the queue head
and retries after 20 ticks. Invalid recovery JSON blocks downed recovery; stale attempt/participant
rows are closed without installation. An unresolved write at plugin shutdown is recovered from its
committed journal/row or retried from the last acknowledged state after restart.

## Migration impact

None beyond V0010. This ADR activates the repository and supersedes the memory-only limitation in
ADR 0031.
