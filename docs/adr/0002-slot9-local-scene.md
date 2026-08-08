# ADR 0002 — Slot 9 Chronicle and Local Scene

## Decision

Hotbar slot 9 is permanently reserved for the Chronicle. Right-click requests a vulnerable Local
Character Scene with an owner-only world Preview Actor; daily use does not teleport. Inventory UI is
a control overlay and is not the Scene renderer.

## Rationale

It provides a discoverable vanilla-client entry and an immersive character view while avoiding
teleport-based studio transitions for a frequently used Scene.

## Consequences

Gameplay has eight hotbar slots. Slot protection, validated actor placement, viewpoint restoration
and idempotent recovery are mandatory. Normal movement input is locked without closing the Scene.
Damage, forced movement, teleport, world change, death, disconnect and actor/session invalidation
close it. The Scene may open outside Rest Context; rest-locked state is validated at Confirm.
