# Rest, Camps and Checkpoints

## Rest Context

Rest Context is required for applying full build presets, changing Form, changing Attunement, reallocating Flask charges and preparing expedition supplies.

Rest Context is a world/service interaction boundary. Chronicle may inspect or link to Rest-owned information, but opening Chronicle is not itself the Rest interaction that refills Flask or consumes expedition stock.

## Sanctuary / Inn

Provides:

- full safe-zone rest;
- build and Flask preparation;
- respawn binding;
- city storage/bank where available;
- basic repair and condition recovery;
- rest-based status cleansing;
- party regroup.

The player initiates preparation from the Sanctuary/Inn world/service interaction. The server then opens the owned Rest preparation surface and commits durable state before presentation completion.

## Field Camp

A player or party may deploy a camp in allowed regions using Camp Supplies.

Camp provides:

- partial Flask refill from owned Infusion Stock;
- meals and basic cooking;
- form/attunement/moveset preparation;
- field repair within caps;
- party respawn option where region allows;
- local storage cache with small capacity.

Camp is not absolute immunity. It has an alert radius and closes preparation if hostiles approach. Camps cannot be placed in towns, boss arenas, roads, protected landmarks, near another camp, inside dungeons without an authored camp anchor or during ENGAGED state.

## Ownership

Solo camp belongs to the character. Party camp belongs to the party session and snapshots owner permissions. Logout removes an empty temporary camp after a grace period; stored value moves to Camp Claim/Overflow if necessary.

## Boss checkpoint

An authored checkpoint records:

- party encounter membership;
- prepared Flask allocation/charges;
- selected build preset references;
- encounter-only reset flags.

A confirmed wipe restores the Flask snapshot and resets encounter state. It does not restore potions, coatings, meals, ammunition beyond encounter recovery, durability, cargo or world resources.

## Respawn

Priority:

1. active eligible boss checkpoint after encounter death;
2. active eligible field camp;
3. bound sanctuary;
4. regional fallback sanctuary.

Death Pouch still forms at death location under its rules. Respawn never teleports trade cargo; cargo follows mount/death logistics.

## Rest transaction

Rest applies changes atomically after validating safety and owned resources. Interrupt before commit changes nothing. Rest commit persists character build, Flask and respawn state before presentation completes.

### V1 local Flask Rest transaction

The local Sanctuary/Rest interaction exposes Expedition Flask preparation only while the character is in `EXPLORATION` and the Rest Context is valid. Allocation preview keeps all five slots assigned. Confirmation atomically consumes exact versioned Infusion Stock lots and replaces the versioned Flask document; stale stock or state rolls back the entire request. No-stock characters below two total charges may receive the server-validated Mercy minimum. A successful allocation/refill clears the previous boss checkpoint snapshot so it cannot restore an obsolete preparation. See ADR 0024 and ADR 0025.

Chronicle may display the current Expedition Flask allocation/charges, but it is not the required primary workflow for refill/allocation.
