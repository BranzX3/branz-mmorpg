# ADR 0030: Party-PvE downed and revive kernel

- Status: Accepted
- Date: 2026-08-01
- Owners: Social, Combat and World Loop

## Context

The current party specification and cross-system acceptance matrix require one revive per character
per encounter. This supersedes the earlier no-downed V1 note in document 14. The behavior must be a
deterministic encounter rule before Paper damage interception, animation and party UI are attached.

## Decision

One immutable downed runtime is keyed by encounter UUID and locks one to five participants. A solo
runtime goes directly to death. In party PvE, the first non-Execute lethal event moves an active
participant to `DOWNED` for 300 ticks. Execute, lethal damage while already downed, or lethal damage
after the encounter revive was consumed goes directly to `DEAD`.

An active ally may own one 80-tick revive channel to one downed target. The target may likewise have
only one channel. Movement or damage is represented by an explicit interruption transition;
interruption removes the channel without consuming the target's revive. At advance time, downed
expiry wins before channel completion. A successful commit returns the target to `ACTIVE`, emits a
25% health ratio, consumes its only encounter revive and grants 60 ticks of protection. Hostile
action ends that protection immediately.

Every state-changing input carries an operation UUID recorded with its kind. Exact replay emits no
second downed/death/heal effect, and using the same UUID for another input kind fails closed.

## Consequences

- the second defeat after a successful revive is always real death;
- starting or interrupting a revive never spends the one-revive allowance;
- simultaneous live signals can be serialized around immutable state;
- health application and visual presentation remain adapter effects, not hidden kernel mutation.

## Failure and recovery

Non-participants, invalid life states, expired targets, busy channels and reused operation IDs change
nothing. Encounter recovery can persist the runtime and replay its operation boundary without
duplicating a revive or death effect. Durable storage and live Paper interception are subsequent
Milestone 7 slices.

## Migration impact

None for this kernel slice.
