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

### V1 consumable authoring contract

Stackable item definitions opt into use behavior with `consumable_profile`. The profile declares
one of the five category IDs, positive windup/commit/effect-duration ticks, non-negative recovery
ticks and whether destroying the active effect requires rare-replacement confirmation. Runtime
compilation rejects commit offsets after windup and rejects consumable profiles on unique items.

The example content bundle authors one inspectable item in every category plus
`material.infusion_stock`. Infusion Stock is an inventory lot consumed by Rest preparation, not an
active category effect. The character-owned reusable Flask is durable character state and is never
represented as a duplicable inventory item.

## V1 durable expedition state

The authoritative Player Session owns one versioned expedition-state document containing the
current Flask allocation/charges, active category effects and ailment state. PostgreSQL stores this
document through the shared transaction journal with an immutable operation UUID, optimistic
version check, audit row and active content version. A successful write reloads database truth
before the live snapshot is published; a failed or stale write leaves the previous snapshot active.

Consumable effect durations are persisted as remaining ticks, not server-global tick deadlines.
This makes reconnect and process restart deterministic: offline wall-clock time does not consume a
duration in V1. The later live adapter is responsible for periodically checkpointing elapsed
remaining time at value-changing boundaries and for removing expired effects.

The environment-gated `/mmo consumable persist [operation-uuid]` command writes an inspectable
Flask/effect/ailment fixture through this exact Player Session path. `/mmo consumable status` reads
the active reloaded snapshot and exposes its durable version and remaining values for restart
acceptance testing. These commands are diagnostics, not production item-use sources.

## Item freshness

Finished potions and normal ingredients do not expire in real time. `Fresh`, `Pristine`, `Corrupted` and `Infused` are material states. Special Unstable Concoctions may expire at rest, death or expedition end and are always labeled.
