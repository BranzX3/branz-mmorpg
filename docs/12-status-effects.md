# Status Effects

V1 has six core ailments. Ailments use buildup followed by an active effect. Immediate CC is handled by the combat CC system, not this system.

## Shared model

Each status declares:

```text
buildup maximum
buildup decay delay/rate
active duration
reapplication behavior
resistance channel
cleanse tags
PvE/PvP profile
visual and audio cues
persistence/reset rule
```

Buildup is visible when meaningful. Triggering consumes buildup and applies the active effect. Reapplication either refreshes, intensifies to a capped tier or is rejected by status definition.

## Burn

Fire damage over time and reduced natural stamina regeneration. Water, rolling effects and burn remedies reduce duration. Bosses may use Burn as phase pressure but cannot stack infinite tiers.

## Bleed

Physical damage on declared exertion events such as sprint, dodge or committed attack, with a small periodic floor. Bandage/remedy cleanses. Heavy armor may resist buildup but does not grant immunity.

## Poison

Steady health damage and reduced incoming healing. Antidote clears active poison; basic remedy removes buildup or shortens duration.

## Frost

Reduces acceleration and stamina recovery; triggering at high buildup creates a short brittle window that increases posture damage received. Heat and frost remedy counter it.

## Shock

Interferes with mana/channel stability and adds guard pressure on the next guarded hit. Grounding consumables and non-conductive equipment reduce buildup.

## Corruption

Reduces maximum effective Flask healing and may enable region-specific hallucination/presentation. Cleansing requires specialized remedy, sanctuary service or quest-specific ritual.

## Resistance

Resistance reduces buildup, not active damage alone. General formula:

```text
appliedBuildup = baseBuildup × clamp(0.40, 1 - resistance, 1.30)
```

Every status must have at least one non-potion counterplay through positioning, equipment, environment or encounter mechanics.

## V1 deterministic ailment kernel

The server advances each ailment from monotonic ticks. Buildup waits its authored decay delay, then
loses `decay_per_tick × elapsed_ticks` without becoming negative. Applied buildup uses the shared
resistance clamp exactly once. Reaching the maximum consumes buildup and activates tier 1 for the
authored duration.

Reapplication is one of `REFRESH`, `INTENSIFY` or `REJECT`. Refresh resets duration at the same tier;
Intensify raises tier only to its authored cap and refreshes duration; Reject consumes the new
threshold but leaves the current active projection unchanged. Cleanse tags clear both buildup and
active state. Death clears definitions marked `CLEAR_ON_DEATH`; only explicitly authored persistent
effects such as some Corruption profiles survive.

## Death and rest

Ordinary ailments clear on death. Corruption may persist according to content but must provide an accessible cleanse. Sanctuary rest clears normal buildup; active effects follow their definitions.
