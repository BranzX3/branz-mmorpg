# Damage, Defense and Crowd Control

## Damage channels

An attack result can produce:

- Health damage
- Enemy posture damage
- Guard pressure
- Status buildup
- Forced movement/CC

Physical tags are Slash, Pierce and Blunt. Elements are separate channels such as Fire, Frost, Storm and Arcane.

## Health damage

```text
raw = weaponPower × moveCoefficient + flatTechniquePower
armorAfterPen = max(0, armor × (1 - penetrationPercent) - flatPenetration)
mitigation = min(0.70, armorAfterPen / (armorAfterPen + 100))
physicalAfterArmor = rawPhysical × (1 - mitigation)
final = sum(channelAfterDefense) × advantageMultiplier × profileMultiplier
```

Elemental resistance is clamped from -30% vulnerability to 60% resistance. Penetration percent is capped at 60%.

## Conditional advantage

There is no random critical chance.

- Counter hit: 1.20
- Back attack: 1.15
- Weak point: 1.25
- Posture-break punish: 1.35
- Finisher: move-defined

The strongest modifier applies fully; the second strongest contributes half of its excess over 1.0. Total generic advantage is capped at 1.60 before move-specific finisher rules.

## Dodge

| Load | Travel | i-frames | Stamina |
|---|---:|---:|---:|
| Light | long | 6 ticks | 25 |
| Medium | medium | 4 ticks | 30 |
| Heavy | short step | 2 ticks | 35 |
| Overloaded | short reposition | 0 | 40 |

I-frames begin after one startup tick. Dodge does not pass through solid blocks; entity phasing is allowed only against non-boss bodies to prevent trapping. PvP uses the same server window but longer recovery.

## Guard

Guard checks a 120-degree front arc by default. Successful guard:

- reduces health damage according to guard type;
- spends stamina;
- applies guard pressure to Guard Stability;
- may leak elemental or unblockable damage.

Guard Stability regenerates after 1.5 seconds without guard pressure. Depletion causes Guard Break and a heavy stagger. Shield guard has higher stability and lower chip; weapon guard has lower stability but faster recovery.

## Perfect guard and parry

- Perfect Guard is a universal timed guard during the first 4 ticks of guard startup. It reduces stamina/pressure, creates a small enemy posture response and never guarantees a counter.
- Parry is an equipped technique with a declared window and eligible attack tags. It can create a strong punish, deflect declared projectiles and has a longer failed recovery.
- Boss attacks declare `guardable`, `perfect_guardable` and `parryable` independently.

## Posture and poise

- Enemies have visible Posture; break causes a vulnerability state and possible finisher.
- Players have hidden short-term Poise accumulation. Poise resists flinch and low-tier stagger but never grants permanent immunity.
- Guard Stability exists only while defending.

Enemy posture begins regenerating after 3 seconds without posture damage; bosses may override by phase. Player poise accumulation clears quickly after pressure stops.

## CC hierarchy

```text
FLINCH < STAGGER < HEAVY_STAGGER < KNOCKBACK < KNOCKDOWN < LAUNCH < GRAB
```

Root, Silence and Interrupt are control tags rather than physical severity. A stronger effect replaces a weaker effect; repeated equal/lower effects during immunity contribute reduced duration or are rejected. Bosses use armor phases and explicit susceptibility rather than blanket immunity.

## PvP profile

Default arena/duel profile:

- health damage multiplier: 0.65;
- posture and guard pressure: 0.75;
- CC duration: 0.60;
- healing received from Flask: 0.70;
- no durability loss;
- status buildup preserved but active duration shortened.
