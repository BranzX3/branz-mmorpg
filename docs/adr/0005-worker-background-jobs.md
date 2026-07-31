# ADR 0005 — Bounded Background Workers

## Decision

Workers are timestamped database jobs with reserved costs and a 24-hour queue cap. They are not always-loaded NPC simulations and cannot produce rare encounter resources.

## Rationale

Provides BDO-like economic continuity while preventing chunk load, duplication and exponential offline empires.
