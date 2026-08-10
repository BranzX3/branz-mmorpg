# Physical Gameplay Item Authority Acceptance

This document is the acceptance owner for the ADR 0025 physical gameplay item authority refactor.
It does not replace the global completion gate in `42-ai-coding-handoff.md`.

## Current status

`AUTOMATED_VERIFIED` for the currently authored gameplay content.

The automated implementation is present on `newmmo` through the following bounded merges:

- PR #21 — selected signed hotbar item is weapon/Crossbow/Staff/durability authority; legacy
  `NATIVE_EQUIPPED/MAIN_HAND` migrates before Player Session readiness.
- PR #22 — ordinary world mobs use their canonical Bukkit health; hidden 1000-HP combat health is
  retained only for explicitly tagged training dummies.
- PR #23 — authored shields move atomically between character inventory and native `OFF_HAND` from
  physical F-key intent; Chronicle cannot mutate native combat/armor slots.
- PR #24 — whole signed stackable lots move transactionally between physical inventory slots and
  selected consumables must match authoritative slot/UUID/version/quantity truth.
- PR #25 — blocked-impact shield durability is proven from the physical OFF_HAND transaction through
  idempotent wear and database restart.

The corresponding full-repository `./gradlew clean build` gates passed in CI runs #650, #664, #673,
#694 and #697.

This status is **not** `LIVE_ACCEPTED` or `COMPLETE`. A real local Paper client pass is still
required by `42-ai-coding-handoff.md`.

## Authority contract under acceptance

1. Hotbar slots 1–8 are normal physical gameplay slots. Slot 9 remains Chronicle-only.
2. A weapon is usable only when the selected physical stack verifies its signed projection and the
   same UUID/definition/version/location exists in the active authoritative Player Session.
3. Persistent `MAIN_HAND` is retired. Legacy rows migrate to a free character inventory slot before
   projection is unlocked.
4. Whole stackable lots may move between database-empty inventory slots. Split, merge, lot swap and
   mixed unique/lot permutations are intentionally fail-closed until their own transaction contract
   exists.
5. A selected consumable must match authoritative lot UUID, definition, version, quantity and slot
   before its use timeline can start.
6. An authored shield enters/leaves native `OFF_HAND` only through the physical swap transaction.
   Staff F-key remains owned by Staff spell cycling.
7. Chronicle cannot commit `MAIN_HAND`, `OFF_HAND`, `HEAD`, `CHEST`, `LEGS` or `FEET` changes.
8. Ordinary non-player world entities use their actual current/max health for MMO damage. Only an
   entity tagged `branzmmo.training_dummy` may use the training hidden-health runtime.
9. Weapon and shield durability spend the exact durable item UUID after the successful authoritative
   combat outcome. PvP suppression and broken-item readiness rules remain authoritative.
10. A persistence/reload failure never promotes the local Bukkit inventory to authority.

## Authored armor boundary

The active V1 item model/content currently defines an authored shield profile but no `armor_profile`
or equivalent authored HEAD/CHEST/LEGS/FEET compatibility profile, and the active example content
contains no MMO armor definitions. Therefore native armor mutation is deliberately **fail-closed**:
Chronicle cannot write those slots and runtime does not infer armor compatibility from Bukkit
`Material` names.

Do not add a Material heuristic to make armor appear supported. When MMO armor is introduced, its
slot/profile schema and physical click adapter are a separate authored-content feature and must pass
this same transaction/reconnect acceptance discipline.

## Required local Paper client pass

Use a LOCAL or INTEGRATION server with the normal dev-value preparation path. Dev commands may grant
values but must not substitute for the physical player actions below.

### A. Legacy main-hand migration

1. Start from a character containing one pre-refactor `NATIVE_EQUIPPED/MAIN_HAND` weapon.
2. Join normally and wait for `MMO character ready`.
3. Verify no persistent MAIN_HAND remains and the same weapon UUID appears in one free physical
   inventory slot.
4. Verify payload/durability is unchanged except for the expected location/version transition.
5. Disconnect/reconnect, then restart the server and reconnect again. The UUID and physical inventory
   location must remain canonical; the weapon must never be forced back to hotbar slot 1.

### B. Physical weapon hotbar

1. Move one MMO-owned Training Sword from inventory into each of at least two different hotbar slots
   within 1–8 using normal cursor pickup/place.
2. Reconnect after each final placement. The same UUID must remain in the committed slot.
3. Select the sword and draw/use its real combat input. A miss must not spend durability.
4. Hit an eligible target. Exactly one wear commit must occur for the action UUID even when one move
   can resolve multiple targets.
5. Wear the weapon to zero. Further combat moves must be rejected as broken without deleting or
   auto-unequipping the item.
6. Attempt to place an MMO value into Chronicle slot 9. The move must be rejected/reconciled and
   Chronicle must remain present.

### C. Whole physical consumable lot

1. Grant one authored consumable lot with quantity greater than one in normal inventory.
2. Move the **whole stack** into a free hotbar slot 1–8, reconnect and verify the same lot UUID,
   quantity and slot.
3. Right-click the selected authoritative stack and allow the use to reach commit. The lot/effect
   transaction must occur exactly once.
4. Attempt half-stack pickup, split, merge and lot-to-lot swap. These operations must be rejected and
   canonical DB quantity/location must be reprojected without duplication or loss.
5. Move the whole remaining stack to another free slot and restart the server. The final slot must
   survive.

### D. Physical shield OFF_HAND

1. Put the authored Training Shield in a hotbar slot 1–8, select it and press F.
2. The exact shield UUID must leave character inventory and become `NATIVE_EQUIPPED/OFF_HAND` before
   its physical offhand projection is authoritative.
3. Select an empty hotbar slot and press F to unequip; repeat with two different shields to exercise
   atomic shield-to-shield swap.
4. Equip the Training Shield again, block a real eligible impact and verify exactly one durability
   spend for that impact UUID.
5. Wear the shield to zero. Guard must become invalid while the shield remains owned and repairable.
6. With a Staff selected, press F and verify Staff spell cycling still owns the input; the Staff must
   not enter OFF_HAND.
7. Restart with a shield equipped and verify exact OFF_HAND UUID/durability reconstruction.

### E. Ordinary world-mob health

1. Spawn or find an ordinary untagged cow (and preferably one hostile mob).
2. Hit it through the MMO melee path and observe health decrease from the entity's actual current
   health, not from a hidden 1000-HP training pool.
3. Deal lethal MMO damage and verify the entity dies once through the normal world entity lifecycle.
4. Repeat with projectile or Staff spell damage when practical.
5. Separately verify an explicitly tagged training dummy still uses its intended training-health
   runtime.

### F. Chronicle/native-slot boundary

1. Open Chronicle through slot 9.
2. Verify physical weapon/shield/native armor slots cannot be committed from Chronicle.
3. Verify existing virtual/build/cosmetic changes still use their normal Chronicle transaction path.
4. Close/reopen/reconnect and confirm physical inventory/OFF_HAND truth is unchanged by Chronicle
   inspection.

## Pass/fail evidence

Record, for each section A–F:

- player UUID/character ID;
- content version and server commit;
- item/lot UUIDs involved;
- pre/post authoritative locations and versions;
- reconnect/restart result;
- visible rejection/recovery message for negative cases;
- terminal log excerpt only when it helps identify the exact failed invariant.

If any section fails, return this feature to `IN_PROGRESS`, fix the underlying authority boundary and
rerun the complete A–F pass. Only after the real client pass may the roadmap advance this feature to
`LIVE_ACCEPTED`/`COMPLETE` and unblock renewed Chronicle acceptance.
