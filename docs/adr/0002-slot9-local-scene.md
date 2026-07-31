# ADR-0002: Slot 9 Chronicle and Local Scene

Status: Accepted

## Decision

Hotbar slot 9 is a protected system item. RMB opens a local Scene Hub outside combat. The real player is not teleported; an owner-only preview actor is placed in front with compact fallback.

## Consequences

- Gameplay hotbar capacity is eight slots.
- Inventory event protection must be comprehensive.
- Damage/movement/provider failure closes the Scene and discards preview.
- Fixed Scene Pods are not part of the everyday V1 menu.
