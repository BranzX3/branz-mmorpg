# ADR 0027: Live boss-wipe Flask bridge

- Status: Accepted
- Date: 2026-08-01
- Owners: World Loop, Combat and Bootstrap

## Context

ADR 0026 defines the authoritative boss lifecycle and ADR 0022 provides a durable prepared-Flask
snapshot. The local Paper runtime needs a testable bridge between those boundaries before encounter
storage, authored boss spawning and rewards are added. Death alone cannot restore a Flask because a
party survivor or a participant still inside reconnect/boundary grace keeps the attempt active.

## Decision

An environment-gated Boss Encounter Lab owns one in-memory lifecycle per encounter UUID and locks
one to five ready online character IDs. Starting the lab captures each participant's current Flask
into the same encounter/checkpoint UUID through the existing asynchronous Player Session mutation
gate. A partial preparation can be retried with the same encounter UUID; already matching durable
captures are reused.

Paper death events mark only that participant defeated. Quit enters the 1,200-tick reconnect grace,
ready-session rejoin restores active membership and a repeating server-tick check expires grace.
Boundary exit/re-entry is exposed explicitly by the lab until authored arena regions own that
signal. Only the kernel's `WIPE_PENDING` transition begins reset.

Reset derives stable per-encounter, per-attempt operation UUIDs and restores the prepared Flask for
each participant through `commitExpeditionState`. The replacement preserves normal consumable
effects, ailments and every other character value. Offline participants remain pending until their
session is ready; transient mutation failures retry. The next attempt begins only after every Flask
acknowledgement, and the live Flask projection is then reconciled from Player Session truth.

Victory freezes the encounter before any restore and exposes a separate empty reward-reconciliation
fixture. Actual reward grants remain owned by the personal-reward slice.

## Consequences

- `/mmo encounter` and the unlocked Boss Encounter Lab tile can exercise live solo/party wipe,
  reconnect, retry and victory behavior;
- real death and quit events feed the same deterministic kernel used by tests;
- no normal consumable, ammo, durability or ailment state is restored by a boss retry;
- multi-player reset waits safely for offline participants instead of silently skipping them.

## Failure and recovery

An unavailable Player Session, stale mutation or database failure cannot advance reset. Retriable
failures remain pending and use the same derived restore operation. Missing or mismatched Flask
snapshots block visibly. Encounter runtime and reset progress were process-local in this slice; ADR
0029 supersedes that limitation with V0009 persist-before-effect recovery. Already committed
character Flask values remain durable in either design.

## Migration impact

None. This slice reuses V0008 character expedition state and its transaction journal.
