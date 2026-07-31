# Moves, Hitboxes and Targeting

## Move definition

Every move declares:

```text
input branch
windup / active / recovery ticks
chain windows
cancel windows
movement curve
facing policy and turn rate
hitbox sequence
resource costs and commit tick
health, posture, guard and status outputs
interrupt resistance
VFX/SFX/animation archetype
PvE/PvP profile overrides
```

Combat logic uses integer server ticks. Presentation receives normalized phase progress.

## Commit rules

- Costs reserve at action start and commit at the definition's commit tick.
- Cancellation before commit refunds reserved resources unless explicitly marked as setup cost.
- Hits cannot occur before the active phase.
- A move may hit a target once unless it declares a re-hit interval.

## Hitbox shapes

V1 supports only:

- `ARC`
- `CAPSULE`
- `BOX`
- `SPHERE`
- `RAY`
- `PROJECTILE`

Hitboxes are defined in attacker-local space and swept between the previous and current transform to reduce tunnelling. Block collision and line-of-sight policy are declared per hitbox.

## Target ordering

Candidate order is deterministic:

1. Weak-point collider intersected.
2. Distance from hitbox origin.
3. Angular difference from facing.
4. Entity UUID stable ordering.

A move declares maximum targets. Party and safe-zone filters run before damage.

## Soft facing assist

V1 has no hard lock-on. Melee moves may turn toward a valid target within a small cone during windup:

- normal attack: up to 15 degrees;
- committed heavy attack: up to 8 degrees;
- ranged and weak-point shots: no aim correction.

Assist never moves the crosshair or guarantees a hit.

## Projectiles

Projectile definitions include speed, gravity, lifetime, collision radius, pierce count, owner, content snapshot and payload. Server simulation is authoritative. Client-visible arrows may be carrier entities, but reward and hit logic use MMO projectile IDs.

## Weak points

Bosses and selected elites expose provider-defined weak-point colliders with health/posture modifiers. Weak points must have readable visual language and cannot be required for basic progression accessibility.

## Debugging

Authorized staff can render hitboxes, swept paths, target order, active phase, facing cone and rejection reasons owner-only. Debug rendering is disabled by default in production.
