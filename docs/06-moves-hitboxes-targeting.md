# Moves, Hitboxes and Targeting

## Move definition

Every combat action is data-driven. Runtime code implements reusable behaviors; content definitions select and parameterize them.

Required fields:

```yaml
id: move.greatsword.light_1
weapon_tags: [GREATSWORD]
input: PRIMARY
phases:
  windup_ticks: 5
  active_ticks: 4
  recovery_ticks: 9
commit_tick: 4
movement_curve: move_curve.greatsword.step_slash
facing:
  mode: SOFT_LOCK
  max_turn_degrees_per_tick: 12
cost:
  stamina: 8
hitboxes:
  - shape: ARC
    start_tick: 5
    end_tick: 8
    radius: 3.1
    angle_degrees: 105
    vertical_min: -0.6
    vertical_max: 1.8
impact:
  damage_coefficient: 0.82
  posture: 18
  guard_pressure: 14
  poise_damage: 20
windows:
  chain: [{from_tick: 11, to_tick: 17, branch: PRIMARY_2}]
  dodge_cancel: [{from_tick: 13, to_tick: 18}]
```

All time values are integer server ticks. Presentation may interpolate within the tick but cannot change gameplay timing.

## Phase rules

- `windup`: action can be interrupted according to armor and move tags.
- `commit_tick`: costs, ammo and consumables become irrevocable.
- `active`: hitboxes may resolve.
- `recovery`: no new primary action except through an authored chain/cancel window.
- A move may have several active windows and hitboxes.
- A target is hit at most once per `hit_group` unless a re-hit interval is explicitly declared.

## Movement curves

Movement is server-authored as local displacement samples or a parametric curve. It is not client velocity acceptance.

Modes:

- `NONE`
- `STEP`
- `LUNGE`
- `ROOTED`
- `CUSTOM_CURVE`

Movement checks blocks using swept collision. If blocked, displacement is truncated; damage timing continues unless the move says `cancel_on_blocked_motion`.

## Facing modes

- `FREE`: current facing, no assistance.
- `SOFT_LOCK`: turn toward the best target inside a narrow cone and range, capped per tick.
- `SNAP_AT_START`: one capped adjustment when the move starts.
- `LOCKED`: facing cannot change after commit.
- `CROSSHAIR`: projectile/cast direction follows server-known look vector.

V1 has no persistent hard lock-on. Soft assist never selects through solid blocks and never rotates more than 35 degrees from the player's initial view for one action.

## Hitbox primitives

V1 supports only:

- `ARC`
- `CAPSULE`
- `BOX`
- `SPHERE`
- `RAY`
- `PROJECTILE`

All melee hitboxes are expressed in attacker-local coordinates and transformed each active tick. Fast shapes use a sweep from previous to current transform to reduce tunneling.

## Collision filters

A hit candidate must pass:

1. Same world and active entity.
2. Target category accepted by the move.
3. Region and PvP rules.
4. Party/friendly-fire rule.
5. Line of sight when required.
6. Not already hit by this hit group.
7. Target is not in an invulnerable or encounter-excluded state.
8. Weak-point collider check when applicable.

Blocks stop rays/projectiles according to projectile profile. Melee arcs do not pass through full solid walls; a center-to-contact visibility test is required.

## Target ordering

For limited-target moves, candidates are sorted deterministically by:

1. distance to hitbox origin,
2. angle from attacker forward,
3. entity UUID.

This prevents order changes from collection iteration.

## Weak points

Bosses and selected enemies may expose named weak-point volumes through `MobProvider`:

```text
HEAD
CORE
BACK_PLATE
LIMB_LEFT
LIMB_RIGHT
```

Weak points apply authored advantage and may accumulate their own break state. Missing provider data falls back to body hit, never a guessed weak point.

## Projectiles

Projectile runtime records:

- owner character/entity,
- source move and content snapshot,
- position and velocity,
- gravity and drag,
- collision radius,
- lifetime,
- pierce/bounce count,
- ammo instance/category,
- hit group,
- region/party profile.

The server simulates projectile contact. Vanilla projectile entities may be used as carriers/visuals, but their native damage is cancelled.

## Bow

State:

```text
DRAWING -> READY_DRAW -> STRAINED -> RELEASED/CANCELLED
```

- Quick shot is valid after minimum draw.
- Full draw maximizes velocity, range, posture and penetration.
- Full draw may be held freely for 50 ticks.
- After that, `STRAINED` drains 4 stamina per second.
- At zero stamina, the bow lowers without firing.
- No hidden spread. Aim is crosshair and projectile physics.

## Crossbow

State:

```text
UNLOADED -> COCKING -> BOLT_PLACED -> LOCKING -> LOADED -> FIRED
```

Checkpoints persist on the item UUID. Interruptions return to the last completed stage. Loaded state persists across slot change, logout and restart. Ammo is bound at `BOLT_PLACED` commit.

## Friendly targeting and support

- Self and party support actions use explicit target modes.
- `PARTY_CROSSHAIR` selects the closest party member intersecting a small screen-space/crosshair cone.
- Ground support uses a server ray to the first valid surface, capped by range.
- Hostile and friendly candidate lists are separate; ambiguous actions require a mode tag rather than guessing.

## Debug requirements

Admin debug may render hitboxes, sweeps, facing cones, selected target, blocked line of sight and projectile path. Debug visuals are viewer-scoped and rate-limited.
