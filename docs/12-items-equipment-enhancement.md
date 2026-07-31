# Items, Equipment, Durability and Enhancement

## Item model

An item has two layers:

1. `ItemDefinition` — immutable content identity and rules.
2. `ItemInstance` — persistent UUID, rolls, durability, enhancement, ownership and cosmetic state.

Minimal instance fields:

```text
item_uuid
definition_id
definition_revision
owner_character_id or owner_container_id
location_type
location_slot
quality_rolls
enhancement_level
enhancement_path
current_durability
max_durability
loaded_ammo_state
cosmetic_dye_state
created_at
version
```

The Minecraft `ItemStack` is a projection. Its PDC contains a signed/reference token and display cache, not the sole source of truth.

## Equipment slots

### Native-visible gameplay slots

- Main hand
- Off hand
- Head
- Chest
- Legs
- Feet

### Virtual gameplay slots

- Necklace
- Ring I
- Ring II
- Talisman
- Quiver

### Cosmetic slots

- Cosmetic head/body/legs/feet
- Weapon skin
- Back cosmetic
- Aura
- Title presentation

Cosmetic slots never change combat calculations.

## Equip validation

Validation order:

1. Item exists and belongs to the character/inventory transaction.
2. Definition is compatible with current content version.
3. Slot accepts category/tags.
4. Main/off-hand combination is valid.
5. Requirements and handling minimum are met.
6. Unique-equip constraints are satisfied.
7. Region/action/transaction state allows the operation.
8. Resulting load and attunement state is valid.

Equip is transactional. The prior item is moved to a valid inventory slot or the operation fails without partial changes.

## Armor load

Total equipped weight maps to character-relative load ratio:

```text
load_ratio = equipped_weight / load_capacity
```

Default tiers:

- Light: `<= 0.40`
- Medium: `> 0.40 and <= 0.70`
- Heavy: `> 0.70 and <= 1.00`
- Overloaded: `> 1.00`

Load capacity is influenced by bounded conditioning and authored equipment, but cannot grow enough to erase armor tradeoffs.

Armor provides:

- physical armor,
- Poise contribution,
- Guard Stability/efficiency where appropriate,
- narrow resistances,
- Arcane Conductivity,
- authored traits.

## Quiver and ammunition

The Quiver is a virtual gameplay slot and storage profile, not a damage-stat stick.

A Quiver definition controls:

- total capacity,
- compatible ammo families,
- prepared ammo category count,
- ammo switch handling delay,
- optional utility traits that do not add unconditional damage/crit.

Default V1 Quiver:

- capacity: 96 arrows or 64 bolts by family;
- up to four prepared ammo categories;
- basic ammo, elemental/status ammo and utility ammo;
- arrows and bolts are separate inventories but may share crafting components.

Ammo is consumed at projectile release/load commit. Crossbow bolts bind to the item at the `BOLT_PLACED` checkpoint.

Encounter recovery is automatic and deterministic from the ammo definition:

- basic arrow/bolt: 65% recovery chance,
- specialized recoverable ammo: 20%,
- explosive/shattering/ritual ammo: 0%.

Recovery is resolved at encounter end and returned directly to the Quiver; overflow enters normal inventory or PendingRewards. There is no manual ground-arrow pickup in V1. PvP arena snapshots restore permitted ammo after the match and do not consume open-world stock.

## Item rolls and rarity

Items use fixed identity plus limited rolls. V1 rarity labels:

```text
COMMON
CRAFTED
RARE
RELIC
ARTIFACT
```

Rarity is not a universal power ladder:

- Common: stable baseline, no random trait.
- Crafted: maker/process identity and one controlled quality roll.
- Rare: up to two bounded numeric rolls or one minor trait.
- Relic: hand-authored trait plus one bounded roll.
- Artifact: unique authored mechanic; at most one small numeric roll.

No affix soup. A V1 item has at most three variable numeric fields and one authored trait package.

## Durability

Gameplay equipment may have durability:

- Weapon: loses durability on committed successful attacks/hits according to family profile.
- Shield: loses durability on blocked impacts.
- Armor: loses durability from PvE health damage received, distributed by hit profile.
- Catalyst: loses durability on committed spell casts.
- Accessory: loses durability only when its active effect triggers, if its definition opts in.
- Quiver: no durability in V1; ammo is its sink.
- Cosmetic: no durability.
- PvP: no durability loss.

There is no progressive stat decay above zero. At zero:

- Weapon/catalyst combat moves are disabled.
- Armor remains visually equipped but loses traits and most armor/Poise.
- Shield guard becomes invalid.
- Accessory active effects disable.

The item remains owned and repairable.

## Repair

### Current durability

Blacksmith and field kits restore current durability up to max durability.

```text
cost = missing_durability * category_rate * rarity_factor * enhancement_factor
```

Field repair kits are slower and capped at 60% of max durability. Blacksmith service can restore to max.

### Max durability

Enhancement failure may reduce max durability. Restoration requires category-specific restoration material and blacksmith service. Max durability cannot fall below 50% of the definition base.

## Enhancement

Enhancement levels: `+0` through `+10`.

Design bands:

- +1–3: modest potency.
- +4–6: handling/efficiency.
- +7–9: chosen forge path expression.
- +10: Masterwork trait.

Total unconditional raw damage increase from +0 to +10 is targeted at 18% and hard-capped at 20%.

### Forge paths

Weapon examples:

- Heavy — posture/guard pressure, cost and commitment tradeoff.
- Balanced — handling and chain stability.
- Runic — arcane compatibility and attunement tradeoff.

Armor examples:

- Fortified — Poise/guard, heavier.
- Mobile — lower movement penalties, lower peak protection.
- Runed — conductivity/resistance changes.

Path choice occurs at +7 and may be changed only through an expensive reforge that preserves level but resets path-specific calibration.

### Success and failure

- Failure never destroys or downgrades the item.
- Failure reduces max durability according to tier and adds Forge Momentum.
- Forge Momentum is stored on the item instance and increases future success chance up to a cap.
- Successful enhancement consumes/reset momentum for that level.
- Momentum transfers with the traded item and is visible during inspection.

Recommended base success rates appear in `22-default-config.md`.

## Masterwork

At +10 the owner chooses one compatible Masterwork trait from the definition/path pool. It changes mechanics or specialization and is not a plain large damage bonus. Re-selecting requires a rare reforge service.

## Trade inspection

Trade UI must expose:

- definition and stable item name,
- rolls and ranges,
- enhancement/path/masterwork,
- current/max durability,
- loaded crossbow state and bound ammo category,
- dye unlock/state for cosmetics,
- unique-equip or quest restrictions,
- item UUID short fingerprint.

Both parties confirm after the final item set and inspection snapshot. Any modification invalidates confirmation.

## Item quarantine

An incompatible item becomes `QUARANTINED`:

- cannot equip, use, trade, enhance or destroy through normal UI,
- retains UUID and ownership,
- displays recovery code,
- can be migrated or replaced by admin tooling through a journaled transaction.
