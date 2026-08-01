# Weapons, Builds and Forms

## Weapon families in V1

### Greatsword

Slow committed arcs, high posture damage, directional momentum and limited weapon guard. Family invariants: broad coverage, commitment, high force.

### Sword and Shield

Stable guard, fast counter branches, shield pressure and controlled offense. Family invariants: defensive conversion, reliable interruption resistance, moderate reach.

### Bow

Quick shot to full draw, free hold for 3 seconds, then strained hold drains stamina. Full draw improves velocity, range, posture and penetration, not hidden accuracy. Base emergency move is Bow Shove.

### Crossbow

Load → ready → fire → reload with three checkpoints. Loaded state persists by item UUID. Base emergency move is Stock Bash. Light, heavy and repeating variants share the ammo engine.

### Staff

Hybrid melee/catalyst family. LMB provides short-range staff chain; RMB casts the active art through the staff. Family invariants: spacing, mana conversion and channel stability.

## Hotbar weapons

There is no abstract Weapon Set A/B in V1. Any compatible combat weapon in slots 1–8 may be selected. Swapping performs sheathe/draw, resets normal chains and clears the input buffer. Cross-weapon continuation requires an explicit transition technique.

## Moveset branch slots

Each active weapon build contains:

- primary chain branch;
- forward, back and lateral primary branches;
- secondary branch;
- dodge follow-up;
- defensive response;
- F Signature;
- Q Auxiliary;
- up to two passive technique modifiers.

A technique replaces or modifies a logical branch; it does not add arbitrary buttons.

## Forms

A Form changes behavior, tradeoffs and branch relationships. Forms may alter stance, movement, resource conversion, guard profile or spell resonance. Forms do not provide a flat universal damage tier.

Form changes require Rest Context, except techniques explicitly declared as combat form transitions. A combat transition has a timeline, resource cost and vulnerability.

A Form must be permanently learned from its authored mentor, discovery, boss-knowledge or faction
quest source before it can be selected. Knowing a compatible weapon family does not imply knowing
its Forms. Build preview/commit and combat activation all enforce the same Knowledge gate.

## Attunement

Attunement Capacity is visible and limits supernatural load. Spells, magical forms, summon contracts and supernatural item traits consume capacity. Mundane techniques do not.

## Build presets

Players may save named presets containing moveset, form, attunement, Flask allocation and preferred equipment references. Applying a preset requires Rest Context and available owned items. Missing or unavailable items are reported; the system never fabricates or relocates them silently.
