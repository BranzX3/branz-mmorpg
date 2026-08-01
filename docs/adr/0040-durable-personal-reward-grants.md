# ADR 0040: Durable personal reward grant ledger

- Status: Accepted
- Date: 2026-08-01
- Owners: Persistence and World Loop

## Context

ADR 0039 freezes deterministic personal grant identities and roll seeds, but a live victory cannot
roll or deliver value safely from process memory. The server must know after restart whether each
eligible character is only frozen, already rolled or fully delivered, and exact retries must not
duplicate a row, roll or audit event.

## Decision

Forward migration V0011 creates `personal_reward_grant` with one row per grant UUID and a unique
encounter/attempt/character constraint. Immutable identity columns carry encounter, attempt,
character and roll seed. Mutable state is `FROZEN`, `ROLLED` or `DELIVERED`, with a JSON state
payload, content version, optimistic version, last transaction and timestamps.

Every create/replace uses the shared system transaction journal. New grants must start `FROZEN`.
Optimistic replacement preserves all identity columns and permits only `FROZEN → FROZEN/ROLLED`,
`ROLLED → ROLLED/DELIVERED` and `DELIVERED → DELIVERED`. The payload may therefore checkpoint
frozen evidence, the deterministic rolled outcome and final delivery receipts without inventing
another identity.

`findPending` returns all non-delivered grants in stable updated-time/grant order. Each committed
version writes a `REWARD_GRANT` audit record. Exact transaction replay returns the committed record;
changed reuse, stale versions, duplicate character grants and backward transitions fail closed.

## Consequences

- restart recovery can distinguish an unrolled grant from a rolled-but-undelivered grant;
- deterministic roll output can be persisted before any inventory/wallet effect;
- delivery can use existing value transaction/Pending Rewards primitives and record their receipts;
- boss completion must wait until every eligible personal grant reaches a reconciled terminal state;
- payload schema/codec and live delivery remain the next adapter layer.

## Failure and recovery

Database failure rolls back journal, grant and audit together. Stale identity/version or invalid
state transition commits nothing. A committed `ROLLED` record is recoverable without rerolling; a
committed `DELIVERED` record is excluded from pending recovery and may only accept idempotent receipt
checkpoint updates.

## Migration impact

Forward-only V0011 adds the grant enum, table, unique participant constraint and partial pending
index. No existing data is rewritten.
