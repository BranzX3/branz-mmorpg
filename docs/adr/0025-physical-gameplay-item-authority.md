# ADR 0025 — Physical Gameplay Item Authority

## Status

Accepted for refactor implementation.

## Context

The player-facing runtime currently mixes two incompatible interaction models:

1. ordinary Minecraft inventory items that can be held and used directly; and
2. MMO equipment state that moves a durable item into an authoritative `MAIN_HAND` equipment slot and then projects it back into the hotbar.

The second model makes ordinary weapons and consumables feel like deck/loadout selections rather than physical inventory. It also creates runtime divergence: combat readiness is derived from persistent equipment state while the player interacts with a projected `ItemStack`.

The authored V1 build specification already states that there are no prepared weapon-set slots and that any valid combat weapon in hotbar slots 1–8 may be selected subject to sheathe/draw timing. This ADR makes that rule authoritative across inventory, combat and Scene code.

A separate defect exposed the same boundary problem in world combat: vanilla melee damage is cancelled while the MMO combat adapter is active, but ordinary non-player mobs are currently tracked through a training-only 1000-HP runtime. Training fixtures must not be production world-health authority.

## Decision

### 1. Physical gameplay items are manipulated physically

The following are ordinary player-facing physical items:

- weapons;
- shields and compatible off-hand gear;
- native armor;
- normal potions, coatings, meals, throwables and other consumables;
- tools and lifeskill work items;
- the character-bound Flask representation.

The player moves these through the normal Minecraft inventory/hotbar/off-hand/armor interaction surfaces. The server validates every authoritative ownership/location mutation and reconciles the Bukkit projection after commit.

### 2. Selected hotbar item is weapon authority

There is no persistent V1 `MAIN_HAND` weapon loadout slot.

For combat, the active weapon is resolved from:

```text
selected hotbar slot 1–8
+ signed/reference item projection in that slot
+ authoritative owned ItemInstance/definition/payload
+ current sheathe/draw/action state
```

Changing selected slot expresses intent to sheathe/draw or use the new physical item. A valid weapon may exist in any gameplay hotbar slot 1–8. Slot 9 remains Chronicle-owned.

Weapon durability, loaded crossbow state, enhancement and other durable weapon state remain attached to the same item UUID regardless of hotbar position.

### 3. Native equipment remains physical

Off-hand and armor remain authoritative native equipment locations because Minecraft exposes those physical equipment slots directly.

Moving a valid item into off-hand or armor expresses an equip intent. The server validates ownership, definition compatibility, item version, load/handling constraints and resulting build legality before committing the native equipment location. Failure restores/reconciles the previous authoritative projection.

### 4. Chronicle owns build configuration, not ordinary physical handling

Chronicle may inspect all character equipment, but its editable equipment responsibilities are limited to virtual/build/cosmetic state such as:

- Necklace, Ring I, Ring II and Talisman;
- Quiver and prepared ammunition configuration where the owning rule requires Scene/Rest Context;
- Combat Arts;
- Forms;
- Magic/Attunement;
- Wardrobe, Dye and cosmetic slots;
- build presets and character information.

Chronicle is not the primary workflow for equipping weapons, shields, native armor or ordinary consumables.

### 5. Consumables are hotbar gameplay

Normal consumables are moved into hotbar slots 1–8 and used from the physical item representation. A use timeline commits the durable lot/effect transaction before presentation of consumption.

The Expedition Flask remains a character-bound gameplay representation, but refill/allocation is a Rest interaction owned by sanctuary/camp/checkpoint preparation. Chronicle may inspect Flask state but is not required as the primary refill interaction.

### 6. World combat must use the correct combatant health authority

Training health profiles and training-target maps are dev/test fixtures only.

Production combat must classify targets before applying damage:

- player: MMO Player Session health authority;
- managed MMO mob: the managed mob combat authority owned by its encounter/entity runtime;
- ordinary vanilla/world mob: a world-mob adapter whose health is derived from and reconciled with the real server entity health/attributes.

An MMO-owned attack may suppress vanilla melee damage only when the MMO runtime actually claims that attack. One attack must resolve damage through exactly one authority path.

## Persistence and migration

Existing item UUIDs, durability, enhancement, loaded state and payloads are preserved.

Items currently persisted in `NATIVE_EQUIPPED/MAIN_HAND` must be migrated/reconciled back to character inventory without creating a new UUID or losing payload state. Off-hand and native armor remain native-equipped. Virtual and cosmetic equipment locations remain unchanged.

A migration/reconciliation must be idempotent and safe across restart.

## Consequences

- `EquipmentSlot.MAIN_HAND` may remain temporarily for compatibility/migration code but must stop being runtime weapon authority before Training Sword acceptance.
- `CombatSessionController` must stop assuming that only hotbar index 0 can be the combat weapon.
- Scene equipment UI must stop being the primary physical equip workflow.
- durability services must resolve the active physical weapon UUID rather than the persistent `MAIN_HAND` slot.
- test fixtures may continue to use training health profiles, but production world combat cannot.

## Acceptance boundary

The refactor is ready for player acceptance only when a dev-granted Training Sword is an MMO-owned item immediately, can be moved to any hotbar slot 1–8, can be selected/drawn and kill an ordinary cow through one authoritative combat path without Chronicle equipment confirmation. Shield/off-hand, native armor and normal consumables must likewise operate through physical inventory interactions and survive reconnect/restart without duplication or ghost state.
