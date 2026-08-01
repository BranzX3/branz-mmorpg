# ADR 0024: Atomic Rest Flask preparation

- Status: Accepted
- Date: 2026-08-01
- Owners: Combat, Persistence, Scenes and Bootstrap

## Context

Rest preparation reallocates the character-owned Expedition Flask and refills missing charges from
owned `material.infusion_stock` lots. Consuming stock and replacing the versioned expedition-state
document in separate transactions could lose stock, mint charges or expose a partial result after a
stale write, disconnect or retry.

## Decision

The Chronicle exposes Expedition Flask preparation only in Rest Context: the Player Session must be
in `EXPLORATION` near the local sanctuary spawn. The preview reallocates all five capacity slots
among Healing, Mana and Stamina without mutating durable state.

Confirm creates one immutable journal request. Inside one database transaction it compare-and-set
consumes the exact character-inventory Infusion Stock lots, compare-and-set replaces the expedition
state document and clears the old prepared boss-checkpoint snapshot. Lots are locked in stable UUID
order. Any missing, moved, stale or wrong-definition lot, or a stale expedition-state version, rolls
back both sides.

The server publishes the new Player Session snapshot only after commit and reload. If no stock is
owned and the current Flask has fewer than two total charges, the server may request the existing
Mercy rule; it grants at most two charges and consumes no stock. The service derives eligibility
again rather than trusting the Scene presentation.

## Consequences

- a refill cannot become visible without its exact stock consumption;
- an allocation change invalidates the old boss snapshot and requires a later checkpoint capture;
- Scene inventory state never owns stock or Flask value;
- normal consumable lots remain a separate live-use adapter.

## Failure and recovery

Leaving Rest Context, another mutation in flight, invalid allocation, stale version or database
failure rejects without partial mutation. Exact journal retry returns the committed transaction and
does not consume stock twice. Reconnect and restart rebuild the Flask and remaining stock solely
from persisted state.

## Migration impact

None. This composes the existing transaction journal, value-lot rows and V0008 expedition-state
document in one JDBC transaction.
