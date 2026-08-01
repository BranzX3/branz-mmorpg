# ADR 0042: Live durable boss personal rewards

- Status: Accepted
- Date: 2026-08-01
- Owners: World Loop, Bootstrap and Persistence

## Context

ADR 0041 makes a frozen grant deterministic, but the live boss lab still completed with one empty
grant UUID. Participation evidence existed only as a pure input contract, and no adapter guaranteed
that a restart before victory, between roll and value creation or after value creation would resume
without losing eligibility or duplicating loot.

## Decision

Boss runtime schema V2 checkpoints per-participant category totals, join/activity ticks and
membership validity alongside the attempt. Every contribution mutation is serialized through the
existing V0009 persist-before-effect queue. Attempt reset clears those totals and establishes a new
attempt clock. Active restart recovery preserves totals while rebasing join/activity ticks to the
new Paper clock; schema V1 records decode with zero contribution evidence.

Victory persists a stable completion tick before reward work starts. The live adapter then freezes
eligibility and creates one V0011 `FROZEN` row per eligible participant. For every row it persists
the deterministic `ROLLED` outcome, grants its stable lot UUID to that character's Pending Rewards
through the value transaction journal, and finally persists the delivery receipt as `DELIVERED`.
Only after every eligible row is delivered does V0009 advance the boss to `COMPLETED`.

Freeze, roll, value delivery, delivery receipt and encounter completion each use deterministic
operation IDs. Restart or manual reconciliation therefore replays the exact prior transaction. An
ineligible participant receives a stable category/cutoff/membership/activity rejection instead of
an empty or shared grant. Recovery fails closed when the durable victory or grant is pinned to a
different content version, rather than rolling from a newly loaded table.

## Consequences

- meaningful damage, guard/control, support or objective play can qualify independently;
- evidence and the victory cutoff survive restart before reward freeze;
- every eligible participant owns an independent durable grant and lot;
- offline-safe delivery lands in Pending Rewards and is never ground-only;
- the environment-gated lab exposes contribution commands until a concrete boss provider emits the
  same category evidence from real encounter actions.

## Failure and recovery

Database failure leaves the boss in `VICTORY_PENDING` and retries after one second. A crash after
`ROLLED` resumes the stored outcome. A crash after the value grant replays the same lot transaction,
then writes the missing delivery receipt. A crash after all deliveries but before boss completion
replays every terminal grant and completes V0009 without creating new value.

## Migration impact

No SQL migration. V0009 JSON advances to schema V2 with backward-compatible V1 decoding. Durable
personal grant/value state continues to use V0011 and the existing transaction journal tables.
