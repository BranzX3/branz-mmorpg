# ADR 0033: Downed-state codec and clock rebasing

- Status: Accepted
- Date: 2026-08-01
- Owners: Bootstrap and Social

## Context

V0010 stores opaque JSON, while downed deadlines and channel commits use Paper's monotonic server
tick. That clock restarts with the process, so persisted absolute ticks cannot be restored directly.

## Decision

The canonical V1 payload contains encounter identity, all participants, revive channels, processed
operation identities and a `recordedAtTick`. Collections are sorted by stable UUID before encoding.
Decode reconstructs domain records so their existing constructors revalidate participant, life-state
and channel invariants.

Recovery preserves remaining online time rather than counting offline wall-clock time. Every active
deadline is rebased by `newCurrentTick + max(0, oldDeadline - recordedAtTick)`. Channel start is
shifted by the elapsed portion captured at the checkpoint, while its remaining commit duration is
rebased the same way. Expired-at-checkpoint work becomes eligible on the first live advance.

The durable store composes this codec with V0010 create, optimistic replace and stable recovery scan.
It verifies encounter and attempt identity again after the repository returns a committed row.

## Consequences

- restart cannot grant extra revive allowance or discard processed operation identities;
- offline time neither revives nor kills a player;
- periodic live checkpoints can reduce the maximum replayed timer window after a crash;
- unknown schema versions and reconstructed invariant violations fail closed.

## Failure and recovery

Invalid persisted JSON is returned as `TRANSACTION_INVALID_JSON`; no partially decoded runtime is
installed. Deadline arithmetic overflow or mismatched committed attempt likewise blocks recovery.

## Migration impact

None beyond V0010. This ADR defines its payload schema and tick semantics.
