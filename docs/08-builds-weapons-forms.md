# Builds, Weapons, Movesets and Forms

## Build composition

A build is the combination of:

```text
currently selected physical weapon from hotbar 1–8
optional physical off-hand style
native armor load
virtual accessories
active moveset
active form
attuned supernatural effects
prepared ammunition
physical consumables in hotbar 1–8
```

There are no prepared weapon-set slots in V1. Any valid combat weapon physically present in hotbar 1–8 may be selected, subject to sheathe/draw timing. The selected hotbar item plus its authoritative item UUID/definition/payload is the weapon authority; Chronicle does not equip an ordinary weapon into a persistent main-hand loadout.

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

## Physical weapon selection

Weapon selection is derived from the normal inventory/hotbar projection rather than a Scene equipment transaction.

Authoritative resolution order:

1. Read the selected gameplay hotbar slot, excluding Chronicle slot 9.
2. Read the signed/reference token of the physical item projected in that slot.
3. Resolve the exact authoritative owned `ItemInstance` and active `ItemDefinition`.
4. Validate durability, family, handling requirements, off-hand compatibility and build state.
5. Enter sheathe/draw timing before the new weapon becomes combat-ready.

Moving the same item UUID to another hotbar position does not create an equip transaction or reset durable weapon state. Durability, enhancement, loaded crossbow state and other persistent payload remain attached to the item UUID.

## Off-hand style

Off-hand changes family style rather than creating an independent button set. Off-hand is a physical native-equipment interaction: placing/removing an item through the normal inventory expresses equip intent and the server validates/commits the authoritative native equipment location before reconciling presentation.

Examples:

- Sword + shield: shield guard and shield techniques.
- Sword + empty: mobile weapon guard and dueling branches.
- Sword + focus: magic-compatible secondary.
- Greatsword requires compatible empty/off-hand charm.
- Bow uses virtual quiver; the physical off-hand slot may contain a compatible charm but not a second weapon.

Invalid combinations disable combat readiness and display a clear reason. Rejection restores/reconciles authoritative inventory/off-hand truth rather than leaving a ghost visual equip.

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

- Move physical items between inventory and hotbar.
- Select another physical weapon from hotbar 1–8.
- Equip or remove valid native off-hand/armor through normal inventory interactions if not inside an exclusive transaction.
- Use ordinary physical consumables from hotbar subject to their timelines.
- Inspect physical equipment through Chronicle.
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

- desired physical equipment UUID references where supported,
- virtual accessory/quiver references,
- moveset definition IDs,
- form,
- attunement,
- Flask allocation,
- prepared ammo categories.

Applying validates every reference. Missing physical items are reported rather than fabricated or teleported from an invalid location. Preset application is atomic for virtual build state; physical inventory/equipment movement uses the appropriate authoritative transaction/reconciliation boundary.

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

- Selecting another physical weapon slot begins sheathe/draw transition.
- Active chain, target assist and input buffer clear.
- Dodge remains available if movement state permits.
- Cross-weapon chain continuation exists only through an explicit technique.
- Loaded crossbow state persists on the item instance UUID.
- Switching to a consumable uses item transition and returns to the last weapon after use unless the player selected another slot.
- Scroll spam cannot cause the runtime to use an item other than the authoritative item currently resolved from the selected slot after transition validation.

## Handling requirements

Requirements are soft capability checks:

- Below recommended handling: increased stamina cost, reduced movement control and weaker hyper-armor.
- Severely below minimum: combat-ready state rejected.
- Requirements never create hidden random misses.
- UI reports qualitative causes such as “weight overwhelms your current Might” or “your Coordination is not yet stable enough.”
