# ADR 0025: Live consumable lot use

- Status: Accepted
- Date: 2026-08-01
- Owners: Items, Combat, Persistence and Bootstrap

## Context

Authored Body Tonic, Elemental Ward, Weapon Coating, Utility Preparation and Meal definitions already
provide use timelines and category effects, while their inventory representations are signed views
of versioned database lots. A live use must not let Bukkit remove an item before commit, apply an
effect before durable consumption or replace a rare active effect without explicit consent.

## Decision

Right-clicking a valid signed consumable projection in a gameplay hotbar slot begins its authored
windup while sharing the existing consumable combat-action owner with the Expedition Flask. The
weapon must be sheathed and the action idle. Jump, sprint, hotbar/inventory change, forced teleport,
death or applied crowd control interrupts; reaching the exact commit tick wins.

At commit, one database transaction compare-and-set consumes exactly one owned character-inventory
lot unit and compare-and-set replaces the category-scoped effect in the expedition-state document.
The journal and audit row commit in the same transaction. The live action waits in `COMMITTING` and
starts recovery only after Player Session reload. Vanilla consumption is cancelled throughout.

Moving an unchanged signed projection from its authoritative inventory slot to the hotbar is
accepted by verifying its signature against that original slot; the lot ID, definition, quantity and
version are still revalidated by PostgreSQL. Reconciliation after commit restores any remaining
stack to its authoritative slot. Replacing an active rare category effect requires sneak +
right-click and is independently enforced by the service kernel. Meals additionally require
`EXPLORATION`.

Active relative durations are reconstructed against the new server tick on login. Every 100 ticks
the adapter journals the remaining durations and removes expired effects. Offline wall-clock time
does not advance them; an unclean process loss can replay at most one checkpoint interval.

## Consequences

- no effect becomes active before its exact lot unit is durably consumed;
- Flask and normal consumables cannot own action state simultaneously;
- one active effect per category remains authoritative and rare replacement is explicit;
- physical inventory movement cannot forge lot identity or bypass optimistic versions.

## Failure and recovery

Invalid signature/content, empty/moved/stale lot, busy character mutation, stale expedition state or
database failure ends the action without applying an effect. Pre-commit interruption consumes
nothing; post-commit interruption never refunds. Exact journal replay skips both mutations.
Reconnect reloads remaining lot quantity and bounded-checkpoint effect durations from database
truth.

## Migration impact

None. This composes existing value lots, V0008 expedition state, signed projections and the shared
transaction journal.
