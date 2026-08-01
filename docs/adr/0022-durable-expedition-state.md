# ADR 0022: Durable expedition state

- Status: Accepted
- Date: 2026-08-01
- Owners: Combat, Items, Persistence and Bootstrap

## Context

The Expedition Flask, consumable category and ailment kernels define character-owned value that
must survive reconnect and server restart. Their runtime timelines use monotonic server ticks, which
cannot be restored as absolute deadlines in a new process. Storing each component independently
would also permit partial state such as a consumed Flask charge without its corresponding status
change.

## Decision

V0008 stores one canonical JSON document per character. It includes Flask allocation/charges,
category effects with remaining ticks and ailments with buildup, remaining decay delay, remaining
active duration and tier. The JSON declares its own schema version. Offline wall-clock time does not
advance these remaining durations in V1.

Writes use the existing transaction journal and bind operation UUID, character/session, expected
state version, replacement payload and content version. The repository applies optimistic versioning
and an audit row atomically. Player Session loads/decodes this state and reloads the complete
snapshot after every successful commit before exposing it to Paper.

## Consequences

- Flask, active category effects and ailments share one atomic persistence boundary;
- process-local absolute ticks never leak into durable data;
- later live adapters can translate between remaining ticks and their current monotonic clock;
- offline time does not expire effects, which is deterministic and avoids wall-clock manipulation;
- a future design that advances effects offline requires an explicit migration and trusted-time
  policy rather than an incidental codec change.

## Failure and recovery

Invalid schema/state rejects Player Session loading. SQL or stale-version failure leaves the prior
live snapshot unchanged. Exact transaction retry replays without another version increment;
changed reuse rejects. A committed write whose callback is lost is recovered by transaction retry
or normal reconnect from PostgreSQL truth.

## Migration impact

Forward-only V0008 adds `character_expedition_state` plus an updated-time index. It references the
existing transaction journal and does not rewrite existing characters; absence maps to the empty
balanced-allocation state until the first durable commit.
