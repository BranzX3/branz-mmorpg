# Builds, Weapons, Movesets and Forms

## Build composition

A build is the combination of:

```text
main-hand weapon
optional off-hand style
armor load
accessories
active moveset
active form
attuned supernatural effects
prepared ammunition
consumables in hotbar 1–8
```

There are no prepared weapon-set slots in V1. Any valid combat weapon placed in hotbar 1–8 may be selected, subject to sheathe/draw timing. This removes the earlier Weapon Set A/B concept.

## Weapon family

A weapon family owns:

- core LMB chain,
- RMB behavior,
- movement identity,
- defensive identity,
- compatible technique branches,
- off-hand compatibility,
- handling requirements,
- animation archetypes.

A named weapon may modify rules but must preserve at least two family invariants.

### V1 families

#### Greatsword

- Wide committed arcs.
- Strong posture and guard pressure.
- Weapon guard.
- Hyper-armor windows.
- High stamina cost and draw commitment.

#### Sword and Shield

- Responsive chain.
- Shield guard and strongest perfect-guard baseline.
- Moderate posture pressure.
- Counter and guard-follow-up branches.

#### Bow

- Quick/full draw continuum.
- Crosshair accuracy with no random spread.
- Virtual quiver and prepared ammo.
- Emergency shove base move.

#### Crossbow

- Multi-stage reload with persistent checkpoints.
- Loaded state belongs to the item UUID and survives swap/logout.
- High burst and penetration with reload commitment.
- Emergency stock bash base move.

#### Staff

- Hybrid close-range staff chain and catalyst casting.
- RMB channels/casts according to active art.
- Lower physical guard but strong arcane interaction.

## Off-hand style

Off-hand changes family style rather than creating an independent button set.

Examples:

- Sword + shield: shield guard and shield techniques.
- Sword + empty: mobile weapon guard and dueling branches.
- Sword + focus: magic-compatible secondary.
- Greatsword requires compatible empty/off-hand charm.
- Bow uses virtual quiver; the physical off-hand slot may contain a compatible charm but not a second weapon.

Invalid combinations disable combat readiness and display a clear reason.

## Moveset branch map

Each weapon family exposes logical slots:

```text
PRIMARY_1
PRIMARY_2
PRIMARY_3
PRIMARY_DIRECTIONAL_FORWARD
PRIMARY_DIRECTIONAL_BACK
SECONDARY
SECONDARY_DIRECTIONAL
DODGE_FOLLOWUP
SIGNATURE_F
UTILITY_Q
DEFENSIVE_FOLLOWUP
FINISHER
```

A technique replaces or augments one declared slot. It does not add a new keyboard key.

V1 active moveset limits:

- Up to 8 replaceable branch techniques.
- One F signature.
- One Q utility.
- One form.
- Passive technique effects are limited by attunement/equipment and content budget.

Core family moves remain available even when no technique occupies a branch.

## Build changes

### Anywhere outside Engaged

- Move items between inventory and hotbar.
- Select another weapon.
- Equip normal gear if not inside an exclusive transaction and the item is valid.
- Change cosmetic appearance through Scene.

### Rest Context required

A Rest Context is an inn, sanctuary, camp, training area or equivalent region/service.

Required for:

- changing active moveset branches,
- changing form,
- changing attunement,
- saving/applying build presets,
- reallocating Flask charges,
- changing prepared ammo list beyond simple cycling.

This preserves preparation while allowing ordinary inventory management in the field.

## Presets

A preset stores references, not item ownership:

- desired equipment UUIDs,
- moveset definition IDs,
- form,
- attunement,
- Flask allocation,
- prepared ammo categories.

Applying validates every reference. Missing items remain unchanged and are reported. Preset application is atomic for virtual build state; physical inventory movement uses a transaction.

## Forms

A form is a mutually exclusive combat ruleset modifier. It must change play pattern and include a tradeoff.

A form may:

- replace selected core branches,
- alter movement curve/facing,
- change resource conversion,
- enable conditional follow-ups,
- change guard/dodge identity,
- alter status interactions.

A form may not be a plain unconditional damage multiplier. Numeric bonuses must support the mechanical identity and remain within global budgets.

Form change requires Rest Context unless performed by an authored in-combat transformation technique. Such a technique has cost, windup, interruption and explicit return behavior.

## Attunement

Attunement is build capacity, not a combat bar.

- Character has visible capacity.
- Supernatural techniques, forms, accessories and persistent magical effects consume capacity.
- Hard conflicts are declared by tags.
- Capacity may grow through major discoveries, not repetitive grinding.
- Active load cannot exceed capacity; invalid builds cannot be committed.

## Weapon swapping in combat

- Selecting another weapon begins sheathe/draw transition.
- Active chain, target assist and input buffer clear.
- Dodge remains available if movement state permits.
- Cross-weapon chain continuation exists only through an explicit technique.
- Loaded crossbow state persists on the item instance.
- Switching to a consumable uses item transition and returns to the last weapon after use unless the player selected another slot.

## Handling requirements

Requirements are soft capability checks:

- Below recommended handling: increased stamina cost, reduced movement control and weaker hyper-armor.
- Severely below minimum: combat-ready state rejected.
- Requirements never create hidden random misses.
- UI reports qualitative causes such as “weight overwhelms your current Might” or “your Coordination is not yet stable enough.”
