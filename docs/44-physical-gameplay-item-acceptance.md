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

Acceptance support was then hardened without changing gameplay authority semantics:

- PR #29 — bootstrap smoke uses an isolated disposable embedded PostgreSQL directory and fails when
  database startup/migration fails instead of treating a clean Paper shutdown as sufficient.
- PR #30 — LOCAL/INTEGRATION dev acceptance can run `/mmo physical status` to read stable
  item/lot UUID, authoritative location, version, resolved durability/quantity, last transaction and
  content version from the active Player Session. Inspection refuses while a durable value mutation
  is in flight and does not expose raw payload JSON.

The runtime revision pinned for the next complete live pass is:

```text
0851f599caf8565d78338a53c9917f9c982d6f4a
```

A documentation-only evidence commit after this revision does not invalidate the runtime revision.
Any later runtime source, migration, configuration-default or active-content change does invalidate
it and requires the complete A-F pass to run on the new revision.

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

For every target-runtime section, capture `/mmo physical status` immediately before and after each
relevant value-changing action, then again after the required reconnect/restart. If inspection says
that an authoritative value transaction is still in progress, wait for commit and retry; a stale
pre-commit snapshot is not acceptance evidence.

### A. Legacy main-hand migration

1. Seed the character on the exact pre-PR #21 runtime
   `8c5a04271f9385730aff0b3332608812a216dc95` with one fresh, unused Training Sword committed to
   `NATIVE_EQUIPPED/MAIN_HAND` through the old supported Chronicle transaction.
2. Reconnect on that legacy runtime so its MAIN_HAND row is reconstructed as the signed physical
   held item. Run `/paper dumpitem` while holding it and record the complete BranzMMO projection
   `value_id`, `definition_id`, `authority_version` and `content_version`. The eight-character
   Chronicle label is supporting evidence only, not the authoritative UUID.
3. Stop Paper normally, preserve the same normal embedded PostgreSQL run directory, switch to the
   accepted runtime revision and join the same character.
4. Run `/mmo physical status`. Verify no persistent MAIN_HAND remains and the exact full UUID from
   the legacy signed projection appears exactly once in a free `CHARACTER_INVENTORY/slot:n`.
5. The live check must show the expected version transition and resolved durability `120/120` for the
   fresh unused Training Sword. Raw database payload JSON is intentionally not exposed by the live
   inspector; raw payload preservation remains an automated migration invariant.
6. Disconnect/reconnect, then restart the server and reconnect again. The same UUID, physical
   inventory location, version and durability must remain canonical; the weapon must never be forced
   back to player-facing hotbar slot 1.
7. On an isolated/disposable acceptance database, also prove the full-inventory migration
   precondition fails closed without deleting, duplicating or inventing a destination for the item.

### B. Physical weapon hotbar

1. Move one MMO-owned Training Sword from inventory into each of at least two different hotbar slots
   within 1–8 using normal cursor pickup/place.
2. After each committed move, capture `/mmo physical status`; reconnect after each final placement
   and require the same UUID/location/version to reconstruct.
3. Select the sword and draw/use its real combat input. A miss must not spend durability; inspector
   output before/after the MISS must show no wear transition.
4. Hit an eligible target. Exactly one wear commit must occur for the action UUID even when one move
   can resolve multiple targets; inspector output must show the corresponding version/durability
   transition.
5. Wear the weapon to zero. Further combat moves must be rejected as broken without deleting or
   auto-unequipping the item.
6. Attempt to place an MMO value into Chronicle slot 9. The move must be rejected/reconciled and
   Chronicle must remain present without any ownership/location loss.
7. Restart and verify the exact broken sword UUID, final physical slot, version and durability again.

### C. Whole physical consumable lot

1. Grant one authored consumable lot with quantity greater than one in normal inventory and capture
   its initial `/mmo physical status` line.
2. Move the **whole stack** into a free hotbar slot 1–8, inspect after commit, reconnect and verify
   the same lot UUID, quantity, version and slot.
3. Right-click the selected authoritative stack and allow the use to reach commit. The lot/effect
   transaction must occur exactly once; inspector output must show the one authoritative decrement.
4. Attempt half-stack pickup, split, merge and lot-to-lot swap. These operations must be rejected and
   canonical DB quantity/location must be reprojected without duplication or loss. Inspect after the
   rejection classes rather than trusting only the visible stack.
5. Move the whole remaining stack to another free slot and restart the server. The final UUID,
   quantity, version and slot must survive.

### D. Physical shield OFF_HAND

1. Put the authored Training Shield in a hotbar slot 1–8, select it and press F.
2. The exact shield UUID must leave character inventory and become `NATIVE_EQUIPPED/OFF_HAND` before
   its physical offhand projection is authoritative; verify the committed state with `/mmo physical
   status`.
3. Select an empty hotbar slot and press F to unequip; repeat with two different shields to exercise
   atomic shield-to-shield swap. Inspection must always show one exact UUID in OFF_HAND and the other
   in inventory, never duplicate or missing.
4. Equip the Training Shield again, block a real eligible impact and verify exactly one durability
   spend for that impact UUID/version transition.
5. Wear the shield to zero. Guard must become invalid while the shield remains owned and repairable.
6. With a Staff selected, press F and verify Staff spell cycling still owns the input; the Staff must
   not enter OFF_HAND and its authoritative location must remain unchanged.
7. Restart with a shield equipped and verify exact OFF_HAND UUID/version/durability reconstruction.

### E. Ordinary world-mob health

1. Spawn or find an ordinary untagged cow (and preferably one hostile mob).
2. Hit it through the MMO melee path and observe health decrease from the entity's actual current
   health, not from a hidden 1000-HP training pool.
3. Deal lethal MMO damage and verify the entity dies once through the normal world entity lifecycle.
4. Repeat with projectile or Staff spell damage when practical.
5. Separately verify an explicitly tagged training dummy still uses its intended training-health
   runtime.
6. When a durable item participates in the check, compare `/mmo physical status` before/after so an
   unrelated durability or ownership change cannot hide inside the world-health acceptance result.

### F. Chronicle/native-slot boundary

1. Capture `/mmo physical status` before opening Chronicle through slot 9.
2. Verify physical weapon/shield/native armor slots cannot be committed from Chronicle.
3. Verify existing virtual/build/cosmetic changes still use their normal Chronicle transaction path.
4. Close/reopen Chronicle and compare inspector output. Inspection alone must not change physical
   inventory/OFF_HAND UUID/location/version.
5. Disconnect/reconnect and compare again; the same physical authority state must reconstruct.

## Pass/fail evidence

Record, for each section A–F:

- player UUID/character ID;
- content version and accepted runtime revision;
- item/lot UUIDs involved;
- pre/post authoritative locations and versions;
- pre/post resolved durability or quantity where applicable;
- `/mmo physical status` output before/after/reconnect/restart on the accepted runtime;
- for A only, the legacy held-item `/paper dumpitem` projection UUID/version evidence;
- reconnect/restart result;
- visible rejection/recovery message for negative cases;
- terminal log excerpt only when it helps identify the exact failed invariant.

Screenshots and Bukkit inventory appearance are supporting evidence only. Do not expose raw payload
JSON just to strengthen an acceptance record; the stable authority fields above plus the automated
persistence/migration tests own that decision.

If any section fails, return this feature to `IN_PROGRESS`, fix the underlying authority boundary and
rerun the complete A-F pass. Only after the real client pass may the roadmap advance this feature to
`LIVE_ACCEPTED`/`COMPLETE` and unblock renewed Chronicle acceptance.
