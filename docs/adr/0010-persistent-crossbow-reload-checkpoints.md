# ADR 0010 — Persistent Crossbow Reload Checkpoints

## Decision

Each unique Crossbow item owns one durable reload checkpoint in its item payload: `UNLOADED`,
`BOLT_PLACED` or `LOADED`. `COCKING`, `LOCKING` and `FIRED` are transient server-tick phases. The
runtime sequence is `UNLOADED -> COCKING -> BOLT_PLACED -> LOCKING -> LOADED -> FIRED`, followed by
settling to `UNLOADED` after the fire transaction commits. Slot changes, reconnects and server
restarts reconstruct the last durable checkpoint from the same item UUID.

At the authored `BOLT_PLACED` tick, one transaction compare-and-set updates the equipped main-hand
Crossbow payload and consumes one exact selected `BOLT` lot from the equipped Quiver UUID. The
transaction verifies item/lot version, owner and location plus the expected ammo definition. The
Bolt becomes bound to the Crossbow at this boundary and is not consumed a second time when fired.

After the authored locking duration, a journaled item-payload compare-and-set advances the same item
from `BOLT_PLACED` to `LOADED`. Firing first compare-and-sets `LOADED` to `UNLOADED`; only after that
commit may Paper create the authoritative projectile. The projectile retains the bound ammo
definition and uses the shared projectile engine with Crossbow-authored power, move outputs and
physics.

## Rationale

The item UUID is the only identity that can preserve a loaded weapon across equipment changes and
sessions without character-global shadow state. Binding payload and lot quantity in one database
transaction prevents both invalid outcomes: a loaded checkpoint without a spent Bolt, and a spent
Bolt without a recoverable checkpoint. Clearing `LOADED` before launch prevents duplicate shots
after retry or reconnect.

Interrupting `COCKING` returns to `UNLOADED`; interrupting `LOCKING` returns to `BOLT_PLACED`; a
completed `LOADED` checkpoint remains loaded. Database snapshot refresh is authoritative when an
asynchronous completion races weapon swap, death, teleport or disconnect.

## Compatibility and migration

No SQL migration is required. Crossbow state is additive JSON in the existing item payload, and
legacy payloads decode as `UNLOADED`. The Item schema adds optional
`weapon_profile.crossbow.bolt_placement_ticks` and `locking_ticks`; those fields are required only
for the `CROSSBOW` family. The example snapshot adds one Crossbow, Bolt, Bolt Quiver and projectile
move.

Older runtimes may preserve the JSON but do not understand bound ammunition and must not host active
sessions after a Crossbow reaches `BOLT_PLACED` or `LOADED`. Rollback therefore requires stopping
sessions and either unloading loaded Crossbows with a compatible recovery tool or retaining this
runtime until their state resolves; operators must never duplicate or manually delete the bound
value.

## Failure and recovery

Item checkpoint update, Bolt quantity mutation, audit append and transaction-journal commit share one
PostgreSQL transaction. A crash between item update and lot consumption rolls both back. Retrying the
same idempotency key replays one terminal result. Stale versions, changed equipment/location,
incompatible or empty Quivers and concurrent value mutations reject without a projectile or local
checkpoint fabrication.

A durable fire commit may outlive the live Paper session; in that case the Crossbow remains
`UNLOADED` and no duplicate projectile is reconstructed. Encounter-end recoverable ammunition and
Pending Rewards overflow remain Milestone 7 authority because they require a terminal encounter
outcome, not a weapon-local guess.
