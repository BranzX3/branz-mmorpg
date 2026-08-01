# ADR 0026: Idempotent boss encounter lifecycle

- Status: Accepted
- Date: 2026-08-01
- Owners: World Loop, Combat, Persistence and Bootstrap

## Context

The durable Expedition Flask checkpoint is already able to capture and restore a character's
prepared allocation. Milestone 7 needs an authoritative encounter decision before that restore can
be invoked: an individual death is not necessarily a party wipe, reconnect and boundary grace must
not reset an attempt early, and victory must prevent any later retry restore.

## Decision

Boss encounters use an immutable lifecycle in `mmo-worldloop`: `ACTIVE`, `WIPE_PENDING`,
`RESETTING`, `VICTORY_PENDING` and `COMPLETED`. One to five character IDs are locked at start. A
participant can be active, defeated, disconnected under grace or outside the boundary under grace.
Only when every locked participant is defeated does the lifecycle enter `WIPE_PENDING`; expiration
of the 1,200-tick reconnect/boundary grace converts that participant to defeated.

Beginning a reset is the sole transition that emits Flask-restore effects, once for every locked
participant. Completion must cite the same reset operation and starts the next attempt with all
participants active. Victory may win a same-tick race while wipe is still pending, freezes the
encounter in `VICTORY_PENDING`, requests reward reconciliation once and permanently prevents reset.

Every mutating command carries an immutable operation UUID recorded with its command kind. Exact
replay is a no-op with no repeated effects; reusing an operation UUID for another command kind is a
stable error. Reward reconciliation records its stable grant UUID before the encounter becomes
`COMPLETED`.

## Consequences

- a party survivor or participant still inside grace prevents an early wipe;
- a retry emits a bounded, explicit Flask-restore set and restores no other resources;
- victory and reward side effects have replay-safe boundaries;
- persistence and Bukkit adapters can serialize the runtime without embedding live entities.

## Failure and recovery

Invalid phases, non-participants, expired rejoin attempts, mismatched reset completion and reused
operation IDs fail without changing state. A recovered `WIPE_PENDING`, `RESETTING` or
`VICTORY_PENDING` runtime can resume the corresponding effect reconciliation by its recorded
operation boundary. The first live adapter remains responsible for persisting the runtime before
executing Flask restores or reward grants.

## Migration impact

None for this kernel slice. Durable encounter storage and its live-server adapter are subsequent
Milestone 7 work.
