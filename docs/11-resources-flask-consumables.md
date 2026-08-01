# Resources, Flask and Consumables

## Health

Health is the defeat resource. HP-cost techniques cannot reduce the user below 1 HP. At or below 10% HP, the player enters Critical State: heartbeat/vignette feedback, normal HP-cost skills disabled and declared forms may gain special interactions.

## Stamina

Base stamina is 100.

- engaged regeneration: 8 per second after a 1-second spend delay;
- exploration regeneration: 12 per second;
- no regeneration while sprinting, guarding, charging a strained bow or performing a stamina-locked action;
- reaching zero causes Exhausted for 20 ticks and blocks high-cost actions.

Costs are tuned so a fresh medium-load character can perform roughly three dodges, four committed greatsword attacks or guard several normal boss hits, but not all simultaneously.

## Mana

Mana unlocks when the character learns a mana-using art. Base mana is 100.

- exploration regeneration: 8 per second;
- engaged regeneration: 2 per second after 3 seconds without committing a spell;
- Focus Channel through a catalyst restores 6 per second, slows movement and is interrupted by stagger;
- action-based recovery may be granted by specific forms or techniques.

## Expedition Flask

One reusable Flask belongs to the character. Base capacity is five charges allocated at Rest Context among:

- Healing: restore 35% maximum HP;
- Mana: restore 40% maximum mana;
- Stamina: restore 60 stamina and clear Exhausted.

Flask use timeline defaults:

```text
28-tick windup
commit at tick 18
20-tick recovery
walk with slow; no sprint
jump or dodge cancels before commit
```

The weapon is unavailable during use. After recovery the previous combat slot is selected unless the player chose another slot.

## Refill model

Preparing a Flask at a sanctuary/camp consumes Infusion Stock for missing charges. Basic stock is common, craftable and sold by NPCs with a price ceiling. Mercy service guarantees a minimum two-charge preparation for broke players.

A boss checkpoint records the prepared Flask snapshot. Confirmed encounter wipes restore that snapshot without consuming new stock. Voluntary retreat, world activity and ordinary deaths do not create free stock. Non-Flask consumables are never restored.

### V1 Flask preparation kernel

Every capacity slot is allocated to exactly one Healing, Mana or Stamina dose. Reallocation carries
only charges that still fit the same dose allocation. Rest then fills missing charges in stable
Healing → Mana → Stamina order, consuming one Infusion Stock per charge. When explicitly eligible,
Mercy service may raise the resulting total to two charges without fabricating or consuming stock;
it never fills above two for free.

Consumption removes exactly one available charge and returns the authored restoration intent:
35% maximum health, 40% maximum mana, or 60 stamina plus Exhausted cleanse. Resource caps,
Corruption healing reduction and the durable transaction are applied by the later live adapter; the
pure kernel never mutates Bukkit state.

## Consumable categories

Only one active effect per category:

- Body Tonic
- Elemental Ward
- Weapon Coating
- Utility Preparation
- Meal

A new effect replaces the previous effect in its category after confirmation where replacement would destroy a rare item.

## Use rules

Every gameplay slot 1–8 may carry consumables. Players can open vanilla inventory while ENGAGED and move backups to the hotbar; the world and enemies continue. Consumables have windup, commit and recovery. Before commit the item is not consumed; after commit it is consumed even if recovery is interrupted.

Food is primarily out-of-combat. Potions cannot remove immediate physical CC such as knockdown, grab or Guard Break.

### V1 use and category kernel

The default Flask timeline is 28 windup ticks, commit at offset 18 and 20 recovery ticks. An
interrupt before offset 18 cancels without consumption. Reaching offset 18 commits once; commit wins
over an interrupt on that exact tick, so any interruption afterward cannot refund the charge/item.
Jump/dodge policy and weapon-slot restoration belong to the live adapter.

Only one unexpired effect exists per consumable category. Applying a new effect replaces only that
category and preserves the other four. Replacing an active rare effect requires an explicit
confirmation result before the incoming value can commit.

## Item freshness

Finished potions and normal ingredients do not expire in real time. `Fresh`, `Pristine`, `Corrupted` and `Infused` are material states. Special Unstable Concoctions may expire at rest, death or expedition end and are always labeled.
