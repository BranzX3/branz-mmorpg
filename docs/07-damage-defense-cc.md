# Damage, Defense and Crowd Control

## Reference scale

Internal values are not limited to vanilla hearts.

- Reference player HP: 1,000.
- Reference stamina: 100.
- Reference mana: 100 when unlocked.
- Same-threat normal enemy hit: 80–120 damage.
- Same-threat heavy hit: 180–250 damage.
- Clearly telegraphed boss heavy hit: 350–550 damage.
- Intended content must not use unavoidable full-health one-shots.

## Damage packet

A resolved attack produces components:

```text
physical: Slash / Pierce / Blunt
arcane: Fire / Frost / Storm / Void / PureArcane
health_damage
posture_damage
poise_damage
guard_pressure
status_buildup
forced_movement
```

Physical and arcane components are mitigated independently and summed.

## Raw damage

```text
raw = weapon_power
    * move_coefficient
    * enhancement_multiplier
    * condition_multiplier
    * profile_multiplier
```

- No random variance.
- Broken weapon has no valid combat move and therefore produces no weapon damage.
- Mastery/conditioning may affect handling and efficiency within limits, not multiply raw power without bound.

## Conditional advantage

V1 has no random critical chance.

Default bonuses:

- Counter hit: +20%.
- Back attack: +15%.
- Weak point: +30%.
- Posture-broken target: +25%.

Bonuses add into one advantage pool capped at +60%, unless a finisher defines its own damage package. Back attack requires impact direction within 70 degrees of the target's rear and valid line of sight.

## Armor mitigation

```text
effective_armor = max(0, armor * (1 - penetration_pct) - penetration_flat)
mitigation = effective_armor / (effective_armor + 200)
```

Caps:

- PvE physical mitigation: 65%.
- PvP physical mitigation: 50%.
- `penetration_pct` cap: 40%.
- `penetration_flat` is content-budgeted and cannot reduce armor below zero.

Physical type resistance then applies as a multiplier. Gear-provided resistance is normally 0–35%; authored vulnerabilities may reach -25%.

## Arcane resistance

Each arcane school uses:

```text
final_component = raw_component * (1 - clamp(resistance, -0.50, 0.65))
```

Player gear should normally remain between -10% and +35%. Encounter mechanics may temporarily exceed normal gear ranges.

## Guard

Guard is directional. Default guard cone is 120 degrees centered on facing.

### Weapon guard

- Blocks 80% physical health damage.
- Blocks 50% arcane damage when the weapon supports arcane guard; otherwise 20%.
- Remaining damage is chip damage.
- Receives 100% authored guard pressure.

### Shield guard

- Blocks 95% physical health damage.
- Blocks 65% arcane damage before shield traits.
- Receives 75% authored guard pressure.
- Movement speed is reduced while held.

Guard Stability maximum is normally 100. When pressure reduces it to zero, `GUARD_BREAK` occurs for 24 ticks and stability returns at 35 after the break.

Stability recovery:

- Begins 30 ticks after last blocked impact.
- 20 per second while not guarding.
- 8 per second while guarding and not being hit.

Holding guard pauses normal stamina regeneration. After two continuous seconds, guard drains 4 stamina per second to prevent indefinite passive holding.

## Perfect guard and parry

Perfect guard is a universal timing property of guard startup:

- Weapon guard window: 3 ticks.
- Shield guard window: 5 ticks.
- Successful perfect guard: zero chip, 50% guard pressure, attacker posture damage and a brief defender advantage.
- Unparryable/unperfect-guardable attacks are telegraphed with a distinct cue.

Parry is an equipped technique, not the same system. It may counter selected melee categories, has greater reward and greater whiff recovery. Enhancement and mastery never increase the timing window; they may reduce resource cost within global caps.

## Dodge and load tiers

| Load | Total | I-frames | Distance | Notes |
|---|---:|---:|---:|---|
| Light | 14 ticks | ticks 4–9 | 4.2 blocks | fastest recovery |
| Medium | 16 | ticks 5–10 | 3.5 | baseline |
| Heavy | 18 | ticks 6–9 | 2.6 | armored step, high poise |
| Overloaded | 20 | none | 1.4 | stumble; no invulnerability |

I-frames prevent authored dodgeable damage and status buildup, not environmental void, suffocation or scripted arena failure. Dodge does not pass through solid blocks. Passing through entities is allowed only when the collision path is valid.

## Enemy posture

Enemies may have a visible posture bar.

- Posture damage reduces the bar.
- Recovery begins after 60 ticks without posture damage.
- Normal enemies recover 25%/s, elites 12%/s, bosses per phase definition.
- At zero posture, the enemy enters `POSTURE_BROKEN` for an authored duration and opens finishers/advantage.
- Bosses gain short posture-break immunity after recovering to prevent chain loops.

## Player poise

Player poise is hidden short-term resistance, not a visible posture bar.

- Incoming poise damage accumulates for 10 ticks.
- Threshold derives from armor load, Fortitude, move hyper-armor and effects.
- Below threshold: no flinch.
- Crossing threshold: Flinch, Stagger or Heavy Stagger according to impact severity.
- Accumulation decays 30% per second after the 10-tick window.

## Crowd-control hierarchy

From lowest to highest:

```text
FLINCH
STAGGER
HEAVY_STAGGER
KNOCKBACK
KNOCKDOWN
LAUNCH
GRAB
```

Control effects such as `ROOT`, `SILENCE` and `DISARM` are separate restrictions.

Rules:

- Higher severity replaces lower severity.
- Equal/lower severity during active hard CC is ignored unless the effect declares combo continuation.
- After hard CC ends, players gain 24 ticks of hard-CC immunity in PvE and 30 ticks in PvP.
- Bosses use authored immunities and convert invalid hard CC into posture damage.
- PvP durations are multiplied by 0.65 and repeated categories gain diminishing returns: 100%, 50%, immune for 8 seconds.

## Hyper armor

A move may define poise multipliers during specific ticks. Hyper armor reduces poise damage but does not prevent health damage, guard pressure, grabs marked as valid, death or arena mechanics.

## PvP profile

Default arena/duel profile:

- Health damage multiplier: 0.70.
- Healing multiplier: 0.60.
- Guard pressure multiplier: 0.85.
- Posture/poise values use dedicated PvP coefficients.
- CC duration multiplier: 0.65.
- No durability loss and no death pouch.
