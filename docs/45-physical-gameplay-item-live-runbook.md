# Physical Gameplay Item Live Acceptance Runbook

This runbook operationalizes the real local Paper client pass owned by
`44-physical-gameplay-item-acceptance.md`. It does not replace that document and does not change the
feature status by itself.

The acceptance target for this runbook is the exact `newmmo` commit under test. For the first pass
created with this document, that commit is:

```text
037897c39f45ddbb5b7da2a49f7ef6d1f69cfcdd
```

The legacy seed boundary for section A is the exact `newmmo` parent state immediately before PR #21
introduced physical hotbar weapon authority and `LegacyMainHandMigrationService`:

```text
8c5a04271f9385730aff0b3332608812a216dc95
```

Do not replace the legacy seed with a new-code fixture. Section A is an upgrade test: old supported
runtime writes the retired location, then the current runtime must migrate it.

## Safety and acceptance rules

- Use a dedicated clean acceptance clone, not a developer working tree with uncommitted changes.
- Use `environment: LOCAL` or `INTEGRATION`. Dev commands may seed values but may not replace cursor
  movement, hotbar selection, right-click use, F-key swap, combat input, reconnect or restart steps.
- Keep the same ignored `mmo-bootstrap/run` directory when switching from the legacy seed commit to
  the target commit. Its embedded PostgreSQL data is the durable upgrade boundary.
- Never delete `mmo-bootstrap/run/plugins/BranzMMO/embedded-postgres` between the legacy seed and
  target migration check.
- Stop Paper normally before switching commits. Do not use Bukkit `/reload` as an upgrade path.
- Do not mark the feature `LIVE_ACCEPTED` from screenshots alone. Each section must include the
  authoritative UUID/location/version evidence required by `44-physical-gameplay-item-acceptance.md`.
- If any section fails, record the first violated invariant, return the feature to `IN_PROGRESS`, fix
  it, and rerun the complete A-F pass.

## Local runtime preparation

Requirements are JDK 25 and a Minecraft 26.2 client. The checked-in local configuration already
uses `environment: LOCAL`, enables dev tools, disables resource-pack delivery for local development,
and uses durable embedded PostgreSQL under the Paper run directory.

From the repository root on Windows:

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-25"
.\gradlew.bat :mmo-bootstrap:runServer
```

The Paper runner writes its runtime state only under the ignored `mmo-bootstrap/run` directory. The
default active content is `example-content/milestone-1`.

Join with an operator/test account. Before every acceptance section:

1. Run `/mmo health`.
2. Require `Character DB session: READY` and a ready combat session.
3. Verify Chronicle occupies hotbar slot 9.
4. Record player UUID, character ID, server commit and content version in the evidence sheet.

The persisted value seeding path is:

```text
/mmo dev -> Persisted Test Item -> select the authored definition
```

A normal click grants one unique item or one lot unit. Shift-click grants quantity 64 for a
stackable lot. The grant is committed through the character persistence transaction path before its
Bukkit projection appears.

Authored values used in this pass:

```text
weapon.training_sword          UNIQUE_DURABLE, max durability 120
equipment.training_shield      UNIQUE_DURABLE, max durability 180
consumable.training_body_tonic STACKABLE_LOT
weapon.training_staff          Staff F-key ownership negative case
```

## A. Real legacy MAIN_HAND upgrade migration

Section A must start from the old supported implementation, not from the current server.

### A0. Seed the legacy database

In the dedicated acceptance clone, stop Paper and switch to the exact pre-PR #21 commit while
preserving `mmo-bootstrap/run`:

```powershell
git fetch origin
git switch --detach 8c5a04271f9385730aff0b3332608812a216dc95
.\gradlew.bat :mmo-bootstrap:runServer
```

Join the test character and wait for the session to become ready.

1. Run `/mmo dev` -> `Persisted Test Item` and grant `weapon.training_sword`.
2. Right-click Chronicle and open `Character & Equipment`.
3. Select the Training Sword entry. The old runtime previews it as MAIN_HAND.
4. Click `Confirm Scene transaction` and require the success feedback `Equipment committed.`.
5. Close and reopen Chronicle. Require the committed MAIN_HAND indicator to still name the same
   item UUID prefix.
6. Disconnect/reconnect once on the legacy server and require the old runtime to reconstruct the
   equipped item.
7. Record the full item UUID and its pre-upgrade authoritative state as
   `NATIVE_EQUIPPED/MAIN_HAND`, including item version and durability payload.
8. Stop Paper normally.

Do not delete or move `mmo-bootstrap/run` after this point.

### A1. Upgrade the same durable runtime state

Switch the same acceptance clone to the exact target commit and boot the same run directory:

```powershell
git switch --detach 037897c39f45ddbb5b7da2a49f7ef6d1f69cfcdd
.\gradlew.bat :mmo-bootstrap:runServer
```

Join the same player and wait for `MMO character ready` / `/mmo health` readiness.

Pass only when all are true:

- no persistent MAIN_HAND remains;
- the exact same item UUID appears in one free physical inventory slot 0-35 excluding Chronicle
  slot 8 (player-facing hotbar slot 9);
- definition and durability payload are unchanged;
- version advanced only as required by the location transition;
- disconnect/reconnect keeps that exact committed inventory slot;
- full server stop/restart and reconnect keeps that exact slot again;
- the item is never forced back into hotbar slot 1 by migration or projection.

Also run one negative precondition check on a disposable character or isolated acceptance database:
fill every authoritative character inventory slot except Chronicle before upgrade. Migration must
fail closed instead of deleting, duplicating or inventing a destination for the legacy item.

## B. Physical Training Sword hotbar

On the target commit, grant a fresh `weapon.training_sword` through Persisted Test Item.

1. Move it with normal Minecraft cursor pickup/place into one gameplay hotbar slot in 1-8.
2. Reconnect and require the same UUID in the committed slot.
3. Move the same sword normally into a second different gameplay hotbar slot in 1-8.
4. Reconnect again and require the same UUID in the second slot.
5. Select it and use the real combat input. A MISS must not change durability/version as weapon
   wear.
6. Hit one eligible target. Exactly one durability wear commit must occur for the action UUID even
   if that move resolves multiple targets.
7. Repeat until durability reaches zero. The sword must remain owned and physically present, while
   further weapon actions reject it as broken.
8. Attempt to move it into Chronicle slot 9. The move must be rejected/reconciled and Chronicle must
   remain canonical in slot 9.
9. Restart Paper and require exact sword UUID, final slot and broken durability state to reconstruct.

Record action UUID for one MISS and one HIT, plus pre/post item version and durability.

## C. Whole physical consumable lot

Grant `consumable.training_body_tonic` with Shift-click so the authoritative lot quantity is greater
than one.

1. Move the whole physical stack into one free gameplay hotbar slot in 1-8.
2. Reconnect and require the same lot UUID, quantity and slot.
3. Select it and right-click normally. Allow the authored timeline to reach commit.
4. Require exactly one lot decrement/effect commit for the use operation.
5. Attempt half-stack pickup, split, merge and lot-to-lot swap. Every unsupported operation must be
   rejected and the canonical DB quantity/location must be reprojected without loss or duplication.
6. Move the whole remaining stack to another free gameplay slot.
7. Restart Paper and require the same lot UUID, final quantity and final slot.

Do not use `/mmo consumable simulate` as evidence for the physical use path.

## D. Physical shield OFF_HAND

Grant two `equipment.training_shield` items and one `weapon.training_staff`.

1. Put one Training Shield in a gameplay hotbar slot, select it and press F.
2. Require that exact UUID to leave character inventory and commit to
   `NATIVE_EQUIPPED/OFF_HAND` before the native offhand projection becomes authoritative.
3. Select an empty gameplay slot and press F to unequip. Require the same UUID back in one canonical
   inventory slot.
4. Equip shield A, then exercise shield-to-shield swap with shield B and require atomic ownership:
   one exact UUID in OFF_HAND, one exact UUID in inventory, never duplicate or missing.
5. Equip one shield and block a real eligible impact. Require exactly one durability wear commit for
   the impact UUID.
6. Wear it to zero. Guard must become invalid while the same shield remains owned and repairable.
7. Select the Training Staff and press F. Staff spell cycling must own the input; the Staff must not
   move to OFF_HAND.
8. Restart with a non-broken shield equipped and require exact OFF_HAND UUID and durability to
   reconstruct.

## E. Ordinary world-mob canonical health

Spawn or find an ordinary untagged cow. Prefer repeating the check with one hostile vanilla mob.

1. Confirm the entity does not have the `branzmmo.training_dummy` tag.
2. Hit it through the selected physical MMO weapon path.
3. Require damage to reduce Bukkit entity current health from its actual current/max health rather
   than from the training hidden 1000-HP runtime.
4. Deal lethal MMO damage and require one normal Bukkit entity death lifecycle.
5. Repeat with projectile or Staff spell damage when practical.
6. Separately exercise an explicitly tagged training dummy and require its authored training-health
   behavior to remain intact.

Record entity UUID/type, pre/post current health and the combat action/projectile/spell operation ID
when available.

## F. Chronicle/native-slot boundary

1. Open Chronicle through immutable physical slot 9.
2. Open Character & Equipment.
3. Verify ordinary weapon, shield and native armor slots cannot be committed from Chronicle.
4. Verify existing virtual/build/cosmetic paths still follow their supported Chronicle transaction
   path.
5. Close/reopen Chronicle and confirm inspection alone changes no physical inventory or OFF_HAND
   UUID/location/version.
6. Disconnect/reconnect and repeat the same comparison.

The current authored V1 content has no MMO armor compatibility profile. HEAD/CHEST/LEGS/FEET must
remain fail-closed; do not treat a Bukkit Material heuristic as armor acceptance.

## Evidence sheet

Create one record per A-F section with this minimum structure:

```text
section:
server_commit:
content_version:
player_uuid:
character_id:
value_definition:
value_uuid_or_lot_uuid:
operation_or_action_uuid:
pre_location:
pre_version:
pre_quantity_or_durability:
post_location:
post_version:
post_quantity_or_durability:
reconnect_result:
restart_result:
negative_case_and_visible_feedback:
relevant_log_excerpt:
verdict: PASS | FAIL
```

A screenshot or Bukkit inventory observation is supporting evidence only. It is not a substitute for
full authoritative UUID/location/version evidence.

## Completion gate

After A-F all PASS on the same target commit/content snapshot:

1. attach or record the evidence in the repository acceptance history;
2. mark physical gameplay item authority `LIVE_ACCEPTED`;
3. run the full automated build/test gate again if the accepted commit has changed;
4. only then mark the feature `COMPLETE` and unblock renewed Chronicle acceptance in the delivery
   queue.

If source changes are required after any failed section, the prior live pass is stale. Rerun the
complete A-F pass on the new accepted commit.
