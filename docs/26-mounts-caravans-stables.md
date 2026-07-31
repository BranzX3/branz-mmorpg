# Mounts, Caravans and Stables

## Roles

- Horse — fastest personal road/travel mount, minimal cargo.
- Camel — two passengers, dash, strong endurance and one small trade-cargo slot.
- Mule/Donkey — balanced riding and general cargo.
- Llama — pack animal and caravan member; not a directly controlled riding mount in V1.

Native entities provide movement/passenger animation. MMO data owns identity, stats, equipment, cargo, injury and persistence.

## Mount instance

Each mount UUID stores owner, species/breed, stable, state, stats, equipment and cargo version.

States:

```text
STABLED
ACTIVE
MOUNTED
UNATTENDED
INJURED
RECOVERING
TRANSFER_ESCROW
```

At most one live entity projection exists per UUID.

## Stats

- Speed
- Endurance
- Handling
- Courage
- Cargo Capacity

Breed variance is bounded to roughly 10–20%; training/equipment choices matter more than rare RNG rolls.

## Equipment

Saddle, bridle, barding, pack and horseshoes are Item Engine items. Equipment emphasizes tradeoffs: cargo packs reduce handling; barding adds weight; road shoes differ from rough-terrain shoes.

## Training Lifeskill

Training gains evidence from real travel, route difficulty, care, clean cargo delivery, dash/jump exercises and caravan handling. Rank unlocks better information, equipment, stable services and up to four pack llamas in one caravan. Maximum speed bonuses remain bounded.

## Calling mounts

Whistle works only when the active mount is in the same world, within 96 blocks, pathable and the player is not ENGAGED. The mount navigates toward the player. Distant mounts require a stable or paid stable transfer.

## Combat

Mounted core combat is disabled. Attack intent begins a dismount transition. Enemies may damage rider/mount. Boss rooms, dungeons and restricted regions require dismount.

## Incapacitation

At zero mount health:

- rider dismounts;
- mount becomes INJURED and despawns safely;
- mount returns to its home/nearest recovery stable;
- equipment loses durability;
- general cargo moves to Stable Claim;
- trade cargo condition decreases 15–25% and moves to Stable Claim.

No permanent mount death.

## Caravan

A caravan consists of a leader plus up to four pack llamas. Members have explicit UUID links. If a member becomes unpathable or exceeds separation, the caravan stops and reports the blocked member; it does not silently teleport valuable cargo.

Logout snapshots and stables the caravan at the nearest valid route anchor. Crash recovery reconciles entity projections against database cargo before respawn.

## Stable market

Mount sale requires STABLED state, empty cargo and removed equipment. The mount enters transfer escrow and displays breed, stats, training, injury history and stable origin.
