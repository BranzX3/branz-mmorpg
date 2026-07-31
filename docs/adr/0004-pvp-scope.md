# ADR-0004: PvP limited to duel and arena

Status: Accepted

## Decision

V1 implements consensual duel and arena profiles only. Open-world PvP, criminal flags and territory conflict are deferred.

## Consequences

- PvP has a separate damage/healing/CC profile.
- PvP causes no durability loss or Death Pouch.
- Core APIs retain profile hooks for future modes without implementing them now.
