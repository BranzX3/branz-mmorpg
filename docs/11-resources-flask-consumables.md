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

The durable document can also hold one prepared Flask snapshot bound to a concrete checkpoint
instance UUID. Capture copies both allocation and remaining charges. Restore requires an explicitly
confirmed full wipe and the same checkpoint instance; ordinary death and a mismatched checkpoint
cannot mint charges. The environment-gated
`/mmo consumable checkpoint <capture|restore> <checkpoint-uuid> [operation-uuid]` fixture exercises
this journaled boundary. The Milestone 7 encounter controller will own the live full-party wipe
signal; callers cannot select a different snapshot at restore time.

## V1 live Flask hotbar adapter

After Player Session projection, the server materializes exactly one character-bound Expedition
Flask in hotbar slots 1-8. It is not an item/lot and cannot be dropped, stored, transferred or moved
to off-hand. Sneak + right-click cycles allocated Healing, Mana and Stamina doses; right-click starts
the selected dose after the weapon is fully sheathed.

The live adapter owns the shared combat action during 28-tick windup and 20-tick recovery, slows
walking to 60% and interrupts on sprint, jump, hotbar selection, applied CC, death or forced
teleport. Offset 18 wins over an interrupt on the same tick. At commit it decrements the selected
charge through the durable expedition-state transaction, waits for PostgreSQL acknowledgement and
Player Session reload, and only then applies the authored restoration. Database failure never heals
the player. Recovery begins at acknowledgement, and an interruption after commit never refunds.

Normal completion returns to the previous combat slot unless the player deliberately chose another
slot. Reconnect removes any stale representation and reconstructs one from durable Flask truth.
ADR 0023 owns this input and recovery boundary.

## V1 atomic Rest preparation adapter

The Chronicle's Expedition Flask page is available at Rest Context and previews a fixed-capacity
allocation among Healing, Mana and Stamina. It displays owned Infusion Stock, current Rest
eligibility and whether the server-side two-charge Mercy rule can apply.

Confirm consumes the exact versioned `material.infusion_stock` inventory lots and replaces the
versioned expedition-state document in one journaled database transaction. Stock lots use a stable
lock order. A stale lot, moved stock, stale Flask document or failed write rolls back both the stock
and Flask update. Successful preparation clears any prior boss-checkpoint Flask snapshot, reloads
the Player Session and then reconciles the hotbar representation. Exact retry cannot consume stock
twice. ADR 0024 owns this atomic boundary.

## V1 live normal-consumable adapter

Signed projected lots with an authored `consumable_profile` can be moved to gameplay hotbar slots
and used with right-click. The live action shares Flask action ownership, follows the definition's
windup/commit/recovery ticks and interrupts on jump, sprint, selection/inventory change, applied CC,
death or forced teleport. Meals require exploration. Vanilla item consumption is always cancelled.

At the exact commit tick, one journaled JDBC transaction consumes one versioned owned lot unit and
replaces only the matching durable effect category. Recovery begins after PostgreSQL acknowledgement
and Player Session reload. Sneak + right-click explicitly confirms replacement of a rare active
effect; the service validates confirmation again. Active durations checkpoint every 100 ticks and
expired effects are durably removed. Offline wall-clock time does not advance them; a crash can
recover up to one checkpoint interval. ADR 0025 owns this boundary.

## Item freshness

Finished potions and normal ingredients do not expire in real time. `Fresh`, `Pristine`, `Corrupted` and `Infused` are material states. Special Unstable Concoctions may expire at rest, death or expedition end and are always labeled.
