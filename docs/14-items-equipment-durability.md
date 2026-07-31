# Items, Equipment and Durability

## Item classes

### Unique/durable instances

Weapons, armor, accessories, tools, workwear, cosmetics, mount equipment and named items use UUID instances.

### Stackable lots

Materials, food, potions, ammunition and reagents use lot UUID plus quantity and variant state.

## Authoritative item locations

```text
PLAYER_INVENTORY
HOTBAR
NATIVE_EQUIPPED
VIRTUAL_EQUIPPED
CITY_STORAGE
BANK
MARKET_WAREHOUSE
MARKET_ESCROW
TRADE_ESCROW
MOUNT_CARGO
WORKER_RESERVED
CRAFT_RESERVED
PENDING_REWARD
OVERFLOW_CLAIM
WORLD_DROP
QUARANTINED
DESTROYED
```

Location changes require compare-and-set versioning and TransactionService.

## Equipment slots

Native: main hand, off-hand, head, chest, legs, feet.  
Virtual: necklace, ring I, ring II, talisman and quiver.  
Cosmetic virtual slots are separate and have no stats or durability.

## Armor load

Weight produces Light, Medium, Heavy and Overloaded tiers. Load modifies dodge, stamina costs, acceleration, poise and guard. It does not dramatically alter basic attack timing. Heavy magic builds are valid, but mundane metal may reduce conductivity/channel stability unless runic equipment offsets it.

## Quiver and ammunition

Quiver is virtual gameplay equipment and has capacity, supported ammo families, handling and swap speed. It is not a damage stat stick. Arrows and bolts are distinct ammo families that share material/effect definitions.

Players prepare up to four ammo types. Neutral Shift+Q cycles prepared ammo while a ranged weapon is READY. Switching during bow draw or crossbow load queues the new type for the next shot; already loaded crossbow ammo remains locked.

Basic ammunition has partial post-encounter recovery based on definition. Rare/special ammunition is recovered only through declared retrieval or remains consumed.

## Durability

- Weapons lose current durability on committed use that produces a valid active attack.
- Armor loses durability from PvE damage received.
- Shields lose durability from successful blocks.
- Catalysts lose durability from committed spells.
- Lifeskill tools lose durability on successful work commit.
- Accessories lose durability only when their active effect triggers.
- Cosmetics have no durability.
- PvP causes no durability loss.

At zero durability:

- weapons/catalysts disable moveset or casting;
- armor retains appearance but loses traits and most defense/poise;
- accessories disable effects;
- tools cannot commit work.

There is no progressive stat decay above zero.

## Inspection

Every tradeable unique item exposes definition, UUID suffix, rolls, traits, creator, enhancement, forge path, current/max durability, dye state and repair condition before transaction confirmation.
