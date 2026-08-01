# ADR 0023: Live Expedition Flask hotbar input

- Status: Accepted
- Date: 2026-08-01
- Owners: Combat, Items, Persistence and Bootstrap

## Context

The durable Expedition Flask is character state rather than an inventory lot, but players require
one hotbar representation and a deterministic way to select Healing, Mana or Stamina. Flask use must
share combat action ownership, persist the spent charge before applying restoration and preserve the
commit-tick-wins rule across asynchronous PostgreSQL acknowledgement.

## Decision

Player Session readiness materializes exactly one character-bound Flask representation in gameplay
hotbar slots 1-8. It is regenerated from database truth, cannot enter containers/off-hand/drop paths
and is removed before logout persistence. The representation stores only owner and selected-dose
presentation; allocation and charges remain exclusively authoritative in the Player Session.

Sneak + right-click cycles authored allocations in stable Healing, Mana, Stamina order. Normal
right-click begins the selected dose. This input is owned only while the Flask representation is in
main hand, so weapon secondary input remains unchanged.

Use follows the authored 28-tick windup, commit at offset 18 and 20-tick recovery. Combat must be
sheathed and idle before start. The adapter reduces current walk speed to 60%, blocks sprinting and
owns combat action state until the use terminates. Jump, sprint, hotbar selection, forced teleport,
death or applied crowd control interrupt the action. Interruption before commit changes nothing;
the exact commit tick wins and starts one asynchronous expedition-state transaction. Restoration is
applied only after PostgreSQL acknowledges and Player Session reloads the decremented charge.

Recovery starts at database acknowledgement rather than expiring while waiting for I/O. An
interruption during commit or recovery cannot refund the charge. On normal completion the previously
selected combat slot is restored unless the player deliberately selected another slot.

## Consequences

- client inventory state can never mint or consume a Flask charge;
- slow or failed database writes cannot grant restoration without durable consumption;
- a lost callback may spend the charge but cannot heal an offline/dead actor; reconnect reloads truth;
- one character can have at most one live Flask use and one representation;
- ordinary consumable lots remain a separate later adapter with their own lot-consumption commit.

## Failure and recovery

No charge, a non-idle combat state or missing hotbar space rejects cleanly. A stale/busy/database
failure ends the live action without restoration and leaves the reloaded durable state authoritative.
Disconnect removes the representation and any process-local action; reconnect reconstructs one item
from the Player Session. Exact operation retry is handled by the expedition-state journal.

## Migration impact

None. The representation is transient Paper state and the charge uses the existing V0008 expedition
document. No content schema or configuration value changes.
