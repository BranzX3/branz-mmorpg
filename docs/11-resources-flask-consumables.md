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

## Item freshness

Finished potions and normal ingredients do not expire in real time. `Fresh`, `Pristine`, `Corrupted` and `Infused` are material states. Special Unstable Concoctions may expire at rest, death or expedition end and are always labeled.
