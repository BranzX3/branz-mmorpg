# ADR 0035: Party membership kernel

- Status: Accepted
- Date: 2026-08-01
- Owners: Social and API

## Context

Boss encounters currently accept an explicit player list, but V1 requires a reusable party authority
for invitations, leadership, reconnect grace and ready checks. These rules must be deterministic
before Paper commands, persistence, HUD and encounter binding are attached.

## Decision

A stable UUID `PartyId` identifies one party. Its immutable runtime contains up to five members, one
leader, pending invitations, an optional ready check, stable join order and processed operation IDs.
The last member leaving produces an explicit disbanded terminal runtime rather than deleting replay
history inside the kernel.

Only the leader invites, transfers leadership and kicks. Invite targets accept or decline before a
1,200-tick expiry; capacity is checked at acceptance. A member disconnect receives 6,000 ticks
(five minutes) of grace. Reconnect preserves membership, while grace expiry removes the member and
transfers leadership to the earliest remaining join order with UUID as deterministic tie-breaker.

A leader starts one 600-tick ready check and counts as ready. Members answer ready/not-ready; the
check emits a result only when all current members answer or the deadline returns false. Membership
change or disconnect cancels an unfinished check so stale responses cannot bind a changed party.

Every state-changing command accepts an operation UUID and records its command kind. Exact replay is
a no-op; cross-command reuse fails closed.

## Consequences

- party membership and leadership no longer need to be inferred from a boss encounter;
- invitation capacity races resolve at accept time;
- disconnect does not immediately dissolve or reassign a party;
- live adapters can project transition outputs without embedding business rules.

## Failure and recovery

Non-leader authority, missing/expired invitations, full membership, invalid members and ready-check
errors mutate nothing. A disbanded runtime rejects all new operations while retaining replay
identity. Persistence and live command recovery remain later slices.

## Migration impact

None for the pure kernel. Durable party state will require a forward-only migration.
