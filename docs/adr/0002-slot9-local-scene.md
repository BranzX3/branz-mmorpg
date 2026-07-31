# ADR 0002 — Slot 9 Chronicle and Local Scene

## Decision

Hotbar slot 9 is permanently reserved for the Chronicle. Right-click opens a vulnerable Local Scene Hub with an owner-only Preview Actor; daily use does not teleport.

## Rationale

It provides a discoverable vanilla-client menu entry and an immersive character view while avoiding fragile camera/spectator transitions for a frequently used UI.

## Consequences

Gameplay has eight hotbar slots. Slot protection and compact preview fallback are mandatory. Damage closes Scene.
