# Physical Gameplay Item Live Acceptance Runbook

This runbook operationalizes the real local Paper client pass owned by
`44-physical-gameplay-item-acceptance.md`. It does not replace that document and does not change the
feature status by itself.

The acceptance **runtime revision** is the exact gameplay/runtime source revision under test. A
later documentation-only commit does not change this revision. For the next complete A-F pass, the
runtime revision is:

```text
0851f599caf8565d78338a53c9917f9c982d6f4a
```

If runtime source, migrations, configuration defaults or active content change after that revision,
pin the new runtime revision and rerun the complete A-F pass. Do not invalidate a pass only because
acceptance notes or evidence files were committed afterward.

The legacy seed boundary for section A is the exact `newmmo` parent state immediately before PR #21
introduced physical hotbar weapon authority and `LegacyMainHandMigrationService`:

```text
8c5a04271f9385730aff0b3332608812a216dc95
```

Do not replace the legacy seed with a new-code fixture. Section A is an upgrade test: old supported
runtime writes the retired location, then the accepted runtime must migrate it.

## Safety and acceptance rules

- Use a dedicated clean acceptance clone, not a developer working tree with uncommitted changes.
- Use `environment: LOCAL` or `INTEGRATION`. Dev commands may seed values but may not replace cursor
  movement, hotbar selection, right-click use, F-key swap, combat input, reconnect or restart steps.
- Keep the same ignored `mmo-bootstrap/run` directory when switching from the legacy seed commit to
  the runtime revision. Its normal `embedded-postgres` directory is the durable upgrade boundary.
- Never delete `mmo-bootstrap/run/plugins/BranzMMO/embedded-postgres` between the legacy seed and
  target migration check. `smoke-embedded-postgres` is disposable smoke state and is unrelated to
  the live acceptance database.
- Stop Paper normally before switching commits. Do not use Bukkit `/reload` as an upgrade path.
- Do not mark the feature `LIVE_ACCEPTED` from screenshots alone. Each section must include the
  authoritative UUID/location/version evidence required by `44-physical-gameplay-item-acceptance.md`.
- `/mmo physical status` is read-only acceptance instrumentation. It reads the active authoritative
  Player Session and refuses to emit records while a durable value transaction is in flight.
- Never copy raw item payload JSON, credentials or large server logs into acceptance evidence. The
  inspector intentionally reports only the fields needed for the authority decision.
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

Join with an operator/test account. Before every target-runtime acceptance section:

1. Run `/mmo health`.
2. Require `Character DB session: READY` and a ready combat session.
3. Verify Chronicle occupies hotbar slot 9.
4. Run `/mmo physical status` and save the complete inspector output for the section.
5. Record player UUID, character ID, runtime revision and content version in the evidence sheet.

After every value-changing action, wait until the action has committed and run `/mmo physical
status` again. If it reports that an authoritative value transaction is still in progress, that is
not evidence; retry only after the transaction finishes. Repeat the inspector after every reconnect
or restart that the section requires.

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

Section A must start from the old supported implementation, not from the accepted runtime revision.
The old runtime does not contain `/mmo physical status`; its signed physical projection is the
read-only bridge used to identify the exact legacy row without modifying old code or querying the
database directly.

### A0. Seed and identify the legacy database row

In the dedicated acceptance clone, stop Paper and switch to the exact pre-PR #21 commit while
preserving `mmo-bootstrap/run`:

```powershell
git fetch origin
git switch --detach 8c5a04271f9385730aff0b3332608812a216dc95
.\gradlew.bat :mmo-bootstrap:runServer
```

Join the test character and wait for the session to become ready.

1. Run `/mmo dev` -> `Persisted Test Item` and grant one fresh `weapon.training_sword`. Do not attack,
   repair or otherwise mutate this sword before the upgrade.
2. Right-click Chronicle and open `Character & Equipment`.
3. Select the Training Sword entry. The old runtime previews it as MAIN_HAND.
4. Click `Confirm Scene transaction` and require the success feedback `Equipment committed.`.
5. Close and reopen Chronicle. Require the committed MAIN_HAND indicator to retain the same UUID
   prefix shown for the selected sword.
6. Disconnect/reconnect once on the legacy server. The old runtime must reconstruct that committed
   MAIN_HAND as the signed physical item in player-facing hotbar slot 1 and select that slot.
7. While holding that reconstructed sword, run `/paper dumpitem`. In the dumped held-item data,
   require and record the complete values associated with these BranzMMO projection keys:
   `projection_value_id`, `projection_definition_id`, `projection_authority_version` and
   `projection_content_version`. Do not accept the eight-character Chronicle prefix as the UUID.
8. Record the legacy authoritative evidence as `NATIVE_EQUIPPED/MAIN_HAND`, using the full signed
   projection UUID and authority version. Because this sword was granted fresh and never used, the
   live durability baseline is the authored `120/120`.
9. Stop Paper normally.

The signed legacy projection is evidence of the exact authoritative value identity/version that the
old runtime reconstructed from MAIN_HAND. The live pass does **not** expose or copy raw database
payload JSON. Raw payload preservation remains an automated migration invariant; the client pass
checks the same UUID, expected location/version transition and resolved durability before/after the
upgrade.

Do not delete or move `mmo-bootstrap/run` after this point.

### A1. Upgrade the same durable runtime state

Switch the same acceptance clone to the accepted runtime revision and boot the same run directory:

```powershell
git switch --detach 0851f599caf8565d78338a53c9917f9c982d6f4a
.\gradlew.bat :mmo-bootstrap:runServer
```

Join the same player and wait for `MMO character ready` / `/mmo health` readiness. Run `/mmo
physical status` before moving or using any item.

Pass only when all are true:

- no `NATIVE_EQUIPPED/MAIN_HAND` record remains in `/mmo physical status`;
- the exact full UUID captured from the legacy signed projection appears exactly once in one free
  `CHARACTER_INVENTORY/slot:n` location, with `n` in 0-35 excluding Chronicle slot 8;
- definition/content identity remain the expected Training Sword values;
- resolved durability is still `120/120` for the fresh, unused migration sword;
- version advanced only as required by the location transition relative to the captured legacy
  authority version;
- disconnect/reconnect keeps that exact UUID, committed inventory slot, version and durability;
- full server stop/restart and reconnect keeps that same authoritative state again;
- the item is never forced back into player-facing hotbar slot 1 by migration or projection.

Also run one negative precondition check on a disposable character or isolated acceptance database:
fill every authoritative character inventory slot except Chronicle before upgrade. Migration must
fail closed instead of deleting, duplicating or inventing a destination for the legacy item.

## B. Physical Training Sword hotbar

On the runtime revision, grant a fresh `weapon.training_sword` through Persisted Test Item, then run
`/mmo physical status` to capture its initial UUID/location/version/durability.

1. Move it with normal Minecraft cursor pickup/place into one gameplay hotbar slot in 1-8.
2. After commit, run `/mmo physical status`; reconnect and require the same UUID in the committed
   slot, then inspect again.
3. Move the same sword normally into a second different gameplay hotbar slot in 1-8. Inspect after
   commit and after the second reconnect.
4. Select it and use the real combat input. A MISS must not change durability/version as weapon wear;
   capture inspector output before and after the MISS.
5. Hit one eligible target. Exactly one durability wear commit must occur for the action UUID even
   if that move resolves multiple targets; capture the new version/durability.
6. Repeat until durability reaches zero. The sword must remain owned and physically present, while
   further weapon actions reject it as broken.
7. Attempt to move it into Chronicle slot 9. The move must be rejected/reconciled, Chronicle must
   remain canonical in slot 9 and authoritative item truth must remain lossless.
8. Restart Paper and require exact sword UUID, final slot and broken durability state to reconstruct;
   confirm with `/mmo physical status`.

Record action UUID for one MISS and one HIT, plus pre/post item version and durability.

## C. Whole physical consumable lot

Grant `consumable.training_body_tonic` with Shift-click so the authoritative lot quantity is greater
than one, then record the initial lot line from `/mmo physical status`.

1. Move the whole physical stack into one free gameplay hotbar slot in 1-8 and inspect after commit.
2. Reconnect and require the same lot UUID, quantity and slot; inspect again.
3. Select it and right-click normally. Allow the authored timeline to reach commit.
4. Require exactly one lot decrement/effect commit for the use operation and verify the lot
   version/quantity change with `/mmo physical status`.
5. Attempt half-stack pickup, split, merge and lot-to-lot swap. Every unsupported operation must be
   rejected and the canonical DB quantity/location must be reprojected without loss or duplication;
   inspect after each rejection class.
6. Move the whole remaining stack to another free gameplay slot and inspect after commit.
7. Restart Paper and require the same lot UUID, final quantity and final slot; inspect again.

Do not use `/mmo consumable simulate` as evidence for the physical use path.

## D. Physical shield OFF_HAND

Grant two `equipment.training_shield` items and one `weapon.training_staff`, then record their initial
item lines from `/mmo physical status`.

1. Put one Training Shield in a gameplay hotbar slot, select it and press F.
2. Require that exact UUID to leave character inventory and commit to
   `NATIVE_EQUIPPED/OFF_HAND` before the native offhand projection becomes authoritative; verify with
   `/mmo physical status`.
3. Select an empty gameplay slot and press F to unequip. Require the same UUID back in one canonical
   inventory slot and inspect again.
4. Equip shield A, then exercise shield-to-shield swap with shield B and require atomic ownership:
   one exact UUID in OFF_HAND, one exact UUID in inventory, never duplicate or missing.
5. Equip one shield and block a real eligible impact. Require exactly one durability wear commit for
   the impact UUID and inspect version/durability after commit.
6. Wear it to zero. Guard must become invalid while the same shield remains owned and repairable.
7. Select the Training Staff and press F. Staff spell cycling must own the input; the Staff must not
   move to OFF_HAND. Inspector output must confirm unchanged Staff ownership/location.
8. Restart with a non-broken shield equipped and require exact OFF_HAND UUID and durability to
   reconstruct; confirm with `/mmo physical status`.

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

Run `/mmo physical status` before and after the damage checks when a durable weapon/shield is involved
so unrelated item authority changes cannot hide inside this section. Record entity UUID/type,
pre/post current health and the combat action/projectile/spell operation ID when available.

## F. Chronicle/native-slot boundary

1. Run `/mmo physical status` and save the complete baseline.
2. Open Chronicle through immutable physical slot 9.
3. Open Character & Equipment.
4. Verify ordinary weapon, shield and native armor slots cannot be committed from Chronicle.
5. Verify existing virtual/build/cosmetic paths still follow their supported Chronicle transaction
   path.
6. Close/reopen Chronicle and run `/mmo physical status`; inspection alone must not change physical
   inventory or OFF_HAND UUID/location/version.
7. Disconnect/reconnect, run the inspector again and require the same physical authority state.

The current authored V1 content has no MMO armor compatibility profile. HEAD/CHEST/LEGS/FEET must
remain fail-closed; do not treat a Bukkit Material heuristic as armor acceptance.

## Evidence sheet

Create one record per A-F section with this minimum structure:

```text
section:
runtime_revision:
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
inspection_before:
inspection_after:
reconnect_result:
restart_result:
negative_case_and_visible_feedback:
relevant_log_excerpt:
verdict: PASS | FAIL
```

For A0, `inspection_before` is the relevant `/paper dumpitem` signed-projection evidence from the
legacy runtime. For the accepted runtime and sections B-F, use `/mmo physical status`.

A screenshot or Bukkit inventory observation is supporting evidence only. It is not a substitute for
full authoritative UUID/location/version evidence. Raw payload JSON is deliberately not acceptance
evidence; record resolved durability/quantity and the minimum stable authority fields instead.

## Completion gate

After A-F all PASS on the same runtime revision/content snapshot:

1. attach or record the evidence in the repository acceptance history;
2. mark physical gameplay item authority `LIVE_ACCEPTED`;
3. run the full automated build/test gate again if the accepted runtime revision has changed;
4. only then mark the feature `COMPLETE` and unblock renewed Chronicle acceptance in the delivery
   queue.

If runtime source, migrations, configuration defaults or active content change after any failed or
successful section, the prior live pass is stale. Rerun the complete A-F pass on the new runtime
revision. Documentation-only evidence commits do not change the accepted runtime revision.
