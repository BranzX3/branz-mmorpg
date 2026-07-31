# ADR 0006 — Projectile and Bow Content Contract

## Decision

`PROJECTILE` move hitboxes carry explicit speed, per-tick gravity/drag, collision radius,
lifetime, pierce count and ammo-category reference. `BOW` weapon profiles carry explicit
minimum/full draw timing, free full-draw hold, strained stamina drain and bounded velocity,
posture and penetration scaling. The server tick runtime owns charge, collision and damage;
client presentation never supplies shot force or hit results.

## Rationale

The generic ARC range/angle/height fields cannot safely describe projectile physics, while Bow
charge behavior must remain designer-authored and deterministic across runtime, schema tooling and
replay tests. Explicit units prevent later weapon families and spells from inventing a second
projectile interpretation.

## Compatibility and migration

The schema change is additive for existing non-Bow items and non-projectile moves. Existing ARC
content compiles unchanged. New `BOW` and `PROJECTILE` definitions fail closed unless their complete
shape-specific contract is present. New stable IDs `weapon.training_bow`,
`ammo.training_arrow` and `move.training_bow.quick_shot` do not replace an existing identity.

No SQL migration is required: Bow draw and projectile state are transient, and the training ammo
category is not consumed from a durable lot in this slice. Durable quiver/ammo consumption will
require an idempotent value transaction in its owning implementation. Rollback must revert the
example content bundle and generated schemas together with the runtime code; an older runtime must
not activate the new Bow/projectile definitions.

## Consequences

Projectile tasks are bounded per caster, cancel on session/world teardown and use immutable content
context for their lifetime. Future Crossbow and spell delivery may reuse the projectile engine but
must provide their own ammo/commit and payload policies.
