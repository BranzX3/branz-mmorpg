# Local Development

Requirements:

- JDK 25
- PowerShell on Windows, or a POSIX shell

Set `JAVA_HOME` to JDK 25 when an older Java runtime appears earlier on `PATH`.

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-25"
.\gradlew.bat build
```

Useful commands:

```powershell
# Apply Java formatting
.\gradlew.bat spotlessApply

# Validate a compiled content manifest
.\gradlew.bat :mmo-content:run --args="validate C:\absolute\path\to\content-manifest.json"

# Validate a content directory and inspect reverse references
.\gradlew.bat :mmo-content:run --args="validate C:\absolute\content-root"
.\gradlew.bat :mmo-content:run --args="references material.iron_ore C:\absolute\content-root"
.\gradlew.bat :mmo-content:run --args="validate $((Resolve-Path example-content\milestone-1).Path)"

# Regenerate editor schemas and write validation reports
.\gradlew.bat :mmo-content:generateContentSchemas
.\gradlew.bat :mmo-content:run --args="report C:\absolute\content-root C:\absolute\report-output"

# Search or export the compiled content catalog
.\gradlew.bat :mmo-content:run --args="search iron C:\absolute\content-root"
.\gradlew.bat :mmo-content:run --args="catalog C:\absolute\content-root C:\absolute\catalog-output"

# Serve one immutable catalog snapshot on loopback only (default port 8765)
.\gradlew.bat :mmo-content:run --args="serve-catalog C:\absolute\content-root 8765"

# Boot Paper locally and shut it down after plugin enable
.\gradlew.bat :mmo-bootstrap:runServer -PsmokeTest=true

# Prove invalid content enters maintenance before sessions can activate
.\gradlew.bat :mmo-bootstrap:runServer -PsmokeTest=true -PsmokeInvalidContent=true

# Start a local server for manual client testing (Ctrl+C / stop when finished)
.\gradlew.bat :mmo-bootstrap:runServer

# Use another local content root instead of the bundled Milestone 1 fixture
.\gradlew.bat :mmo-bootstrap:runServer -PcontentPath="C:\absolute\content-root"

# Run real disposable PostgreSQL migration integration tests (Docker is not required)
.\gradlew.bat :mmo-persistence:test
```

The catalog service is read-only and binds only to the local loopback interface. Its JSON API is
available under `/api/v1`; restart the command after source content changes to load a new immutable
snapshot.

The Paper runner writes only to the ignored `mmo-bootstrap/run` directory. No task in this
repository deploys to a remote environment.

The persistence tests start an isolated embedded PostgreSQL process, apply migrations, and stop it
after the test suite. PostgreSQL binaries are resolved as pinned test dependencies from Maven
Central and cached by Gradle; no local database, container daemon or remote database is mutated.

## Current in-game Milestone 3 shell

Local configuration defaults to `environment: LOCAL`, `dev-tools.enabled: true` and resource-pack
delivery disabled. It starts an embedded PostgreSQL whose durable data lives at
`mmo-bootstrap/run/plugins/BranzMMO/embedded-postgres`. The `runServer` task defaults to
`example-content/milestone-1`; pass `-PcontentPath=...` to test another content root. After joining
with an operator/test account:

1. Run `/mmo health` and verify runtime/content/item counts plus
   `Character DB session: READY`.
2. Verify `Adventurer's Chronicle` occupies hotbar slot 9.
3. Try click, drag, number-key swap, off-hand swap and drop; the Chronicle must remain in slot 9.
4. Right-click the Chronicle while stationary on the ground.
5. Open every Scene Hub page, use Back, and verify Exit closes the session.
6. Reopen the Scene and test movement, damage, teleport and disconnect interruption.
7. Run `/mmo dev`, open `Persisted Test Item`, and grant `material.iron_ore` plus
   `weapon.training_blade`.
8. Verify signed purple test projections appear outside Chronicle slot 9.
9. Verify the test projection cannot be moved, dropped, swapped to off-hand, placed or consumed.
10. Disconnect and reconnect; verify the projections are reconstructed from PostgreSQL.
11. Open Chronicle -> Character & Equipment, select the training blade, and leave with Back/Exit.
    Reopen it and verify main hand is unchanged because preview did not commit.
12. Select the training blade again and choose `Confirm equipment transaction`. Verify it becomes
    the native main-hand item in hotbar slot 1.
13. Stop and restart `runServer`, reconnect, and verify the inventory and equipped blade remain.
14. Hold hotbar slot 1 and wait for the `Training blade READY` action-bar message.
15. Left-click once. Verify action-bar state advances through Windup, Active and Recovery, then
    returns to idle; `/mmo health` shows the current weapon/action and stamina.
16. Switch away during Windup and verify the action cancels. Repeat after the commit tick and
    verify the committed stamina remains spent.
17. Select the blade, immediately left-click during Drawing, and verify one opener is buffered and
    starts only after READY.
18. Open `/mmo dev` -> `Training Move Tester` to start the same compiled move without relying on
    an arm-swing event.
19. Spawn a non-player living target about two blocks ahead, for example
    `/summon minecraft:zombie ~ ~ ~2`, face it and attack. Verify the action bar reports
    `HIT targets=1 damage=...` and `/mmo health` retains the same last resolution.
20. Move the target behind the player, outside 2.8 blocks or behind a solid wall; repeat and verify
    `MISS`. Put several targets in the 95-degree cone and verify no more than four are selected.
21. While still in `EXPLORATION`, press directional Shift and verify vanilla sneak remains intact.
22. Enter `ALERT`/`ENGAGED` with the zombie, hold a movement key and press Shift. Verify a Medium
    dodge moves in the captured direction, spends 30 stamina and `/mmo health` reports
    `dodge=MEDIUM/...` through recovery.
23. Dodge directly into a solid wall and verify no step passes through it; the recovery still
    completes normally.
24. Let the zombie's entity attack land on the startup tick and verify it is not avoided. Repeat
    during the four Medium i-frame ticks and verify `DODGE iframe tick=...` is retained by
    `/mmo health`.
25. Start the training slash and try to dodge before authored tick 9; verify rejection. Retry at or
    after tick 9 and verify the attack cancels while already committed stamina remains spent.
26. Change `combat.training-dodge-load` to `LIGHT`, `HEAVY` and `OVERLOADED` between local restarts;
    verify costs 25/35/40 and confirm Overloaded movement has no i-frame immunity.
27. Stay `ENGAGED`, face the zombie and right-click once to enter training weapon guard. The
    Paper-only adapter uses right-click toggle; right-click again releases it.
28. Let a front hit arrive within four server ticks of guard start. Verify `PERFECT_GUARD`, zero
    damage, five stamina spent and Stability reduced by five.
29. Start guard early, wait beyond the four-tick window and take a front hit. Verify `GUARDED`,
    20% chip, ten stamina spent and ten Stability pressure.
30. Keep guard active but turn the zombie outside the 120-degree front cone; verify the hit is not
    guarded. The exact 60-degree edge remains valid.
31. Repeat normal blocks with full stamina until `GUARD_BREAK`; verify guard rejects during its
    24-tick break, then Stability returns at 35 and begins delayed recovery.
32. Verify `/mmo health` reports `guard=PERFECT(...)`, `GUARDING(...)`, `BROKEN(...)` or
    `INACTIVE(...)`; starting an attack while active guard is rejected, while dodge releases guard.
33. Strike the same training target nine times without waiting three seconds between hits. Verify
    the ninth hit reports `posture=BROKEN(60t)`; later hits during that window deal the 1.35
    posture-break damage bonus but cannot extend the broken window.
34. Let a damaged, unbroken target go untouched for 60 ticks. Verify posture then regenerates at
    25/second; killing, unloading or invalidating the target clears its isolated posture/health.
35. Perfect-guard a non-player attacker and verify its action-bar response reports eight posture
    damage (for a fresh target, `attacker-posture=92.0/100.0`).
36. Take an ordinary unguarded mob hit. Verify hidden poise triggers `CC FLINCH ... duration=6t`,
    the active move/buffer/guard/dodge is interrupted and `/mmo health` shows `cc=FLINCH(...)`
    without printing an exact poise value.
37. Deplete Guard Stability and verify Guard Break applies `HEAVY_STAGGER` for 24 PvE ticks. Inputs
    remain action-locked until the server-owned CC expires, after which `/mmo health` returns to
    `cc=NONE`.
38. Run `/mmo combat debug` and perform a training slash. Verify only the command viewer sees the
    ARC outline plus target markers; run the command again to disable it. An operator may inspect
    another online session with `/mmo combat debug <player>`.
39. Complete or cancel a training move, then run `/mmo combat trace export`. Verify replay succeeds
    before a canonical `.trace` file appears under
    `plugins/BranzMMO/combat-traces`; use the optional player argument to export an inspected
    online session.
40. Run `.\gradlew.bat :mmo-combat:test --tests com.branz.mmorpg.combat.acceptance.TrainingWeaponAcceptanceKitTest`.
    Verify the delayed draw/buffer/replay, jitter priority and guard/CC interrupt cases all pass
    without a live client or wall clock.
41. Enable `/mmo combat debug`, begin a training slash and move or turn before its hitbox tick.
    Verify the previous/current ARC outlines and cloud sweep path are viewer-only, and a target
    crossed between the two server poses resolves once rather than tunnelling or double-hitting.
42. Teleport during Windup with `/tp` and verify the move/buffer/guard/dodge state resets. Repeat a
    normal directional dodge and verify its four plugin-owned movement steps remain allowed.
43. Run `/mmo health` with a fresh session and verify `health=1000.0/1000.0`. Take an ordinary
    unguarded zombie hit and verify it applies 100 internal damage while the vanilla hit itself is
    cancelled; ten such accepted hits from full health produce normal death.
44. Repeat a front hit during normal guard and verify only 20 chip damage reaches MMO HP; repeat
    inside the perfect-guard window and verify MMO HP is unchanged.
45. Take non-entity environmental damage and verify the action bar reports `ENVIRONMENT` with 50
    MMO damage per vanilla damage point. Confirm `/mmo health` and the vanilla heart ratio agree.
46. Leave combat after taking damage. Verify HP does not recover before 400 quiet ticks, then rises
    at five HP/second in `EXPLORATION` and stops at 800/1000.
47. Die with the Chronicle, equipped blade, persisted test items and experience present. Verify no
    item/experience drop is created, then respawn and confirm full MMO HP/resources plus a safe
    weapon transition from the selected slot.
48. Strike one fresh training target repeatedly and verify `health=current/1000.0` falls by the
    applied deterministic damage. The exact lethal hit must kill the Bukkit target and clear its
    isolated HP/posture state.
49. Open `/mmo dev` -> `Persisted Test Item`, grant `weapon.training_bow` and
    `equipment.training_quiver`, then Shift-click `ammo.training_arrow` to grant one 64-unit lot.
    In Chronicle -> Character & Equipment, equip and confirm the Bow, reopen, then equip and confirm
    the Quiver as a separate transaction. Preview `[Inventory -> Quiver]` on the Arrow and confirm;
    then left-click the stored lot to prepare it and confirm again. Verify `/mmo health` reports
    `ammo.training_arrow=64` and `quiver=64/96` before drawing.
50. Right-click air once to begin the local training draw, then right-click again before five
    server ticks and verify `BOW TOO_EARLY` with no projectile. Repeat after at least five ticks and
    verify `/mmo health` reports Bow recovery, one active projectile, selected ammo `=63` and
    `quiver=63/96` only after the journaled release.
51. Release at tick five and then at/after tick twenty against the same target. Verify the full shot
    travels faster, applies greater posture/penetration contribution, uses exact crosshair aim and
    reports `PROJECTILE HIT damage=... health=... posture=...` without a vanilla arrow hit.
52. Put a solid wall between the Bow and target and verify the projectile terminates at the wall.
    Enable `/mmo combat debug` and verify only the subscribed viewer receives the flame path marker
    while normal crit particles remain ordinary shot presentation.
53. Hold from full draw for three seconds by delaying the second RMB. Verify `BOW STRAINED` then
    drains four stamina/second; when the last stamina is spent the Bow lowers without firing.
54. Swap weapon, dodge, take hard CC, teleport, die or disconnect while drawing. Verify draw/recovery
    cancels and owned projectiles are removed on session/world teardown. `/mmo health` must show
    `bow=IDLE` and `projectiles=0` after cleanup.
55. In Chronicle, right-click the stored Arrow lot to preview withdrawal and confirm it. With the
    prepared category now empty, complete a valid draw/release and verify `BOW NO AMMO`, no
    projectile and no recovery. Inventory ammo must not satisfy the shot and `/mmo health` must show
    the prepared ID at `=0` with `quiver=0/96`.
56. Grant one normal-click training Arrow, store and confirm it, then release once. Observe the
    short `ammo=1(COMMITTING)` state if the local database completion crosses a server tick. Verify
    the projectile appears only after commit, the Quiver lot becomes a destroyed tombstone and
    `ammo=0`, `quiver=0/96` remain after reconnect/restart.
57. During `AMMO COMMITTING`, spam RMB and invoke another persisted dev grant. Verify the action/value
    locks prevent a second projectile and a second value transaction. One arrow produces at most
    one projectile and exactly one `lot.consume` audit/journal commit.
58. Stop the server immediately around a release and restart. Verify the authoritative outcome is
    either an unconsumed arrow with no committed journal or one consumed arrow with one committed
    journal; retry/reconnect must never subtract the same lot version twice.
59. Shift-click both `ammo.training_arrow` and `ammo.training_bodkin_arrow` to grant two 64-unit
    lots. Store and confirm the Arrow first; verify `quiver=64/96`. Preview storing the Bodkin and
    verify the exact transfer is `x32`; confirm and verify `quiver=96/96` while the other 32 Bodkin
    units remain in inventory. Attempting another store must report that the Quiver is full and must
    not create a transaction preview.
60. Right-click the stored 64-unit Arrow lot and verify a `WITHDRAW x64` preview appears. Press Back
    and reopen to prove load remains `96/96`. Repeat and Confirm; verify the lot returns to one free
    inventory slot and load becomes `32/96`. Store that Arrow lot again and verify load returns to
    exactly `96/96` without changing its UUID.
61. Left-click stored Bodkin to add it to the existing prepared Arrow list, then confirm the
    preparation transaction. Reopen Chronicle and verify both categories, their stored quantities
    and the selected category survived close/reopen.
62. With the Bow READY, stand still, hold sneak and scroll one hotbar step. Verify the proposed
    hotbar slot does not change, the action bar names the newly selected category and `/mmo health`
    reports that exact ammo ID and quantity. Scroll both directions across the list boundary and
    verify selection wraps deterministically.
63. Enter `ENGAGED`, commit an ammo switch and immediately attempt to draw. Verify the draw is
    rejected for the authored six handling ticks, then succeeds on the next tick. Repeat while in
    `EXPLORATION` and verify no post-commit handling delay is added.
64. Begin a Bow draw and attempt stationary sneak+scroll. Verify the slot remains owned by ammo
    input but the switch reports `AMMO SWITCH ACTION LOCKED`; the in-progress draw keeps its original
    selected category. Moving while sneak+scrolling must not claim ammo-cycle input.
65. Fire once with each prepared category. Verify only the selected `QUIVER:<item-uuid>` lot
    decrements, the
    projectile is created after the commit and `/mmo health` follows the selected category's exact
    remaining quantity/load. Inventory remainder and the other prepared category must not decrement.
66. Disconnect/reconnect and restart Paper after selecting the Bodkin Arrow. Verify Chronicle and
    `/mmo health` restore that selection from the equipped Quiver item payload. Swap away and back to
    the same Quiver and verify both its item-owned preparation and stored lot load return.
67. Open `/mmo dev` -> `Persisted Test Item`, grant `weapon.training_crossbow` and
    `equipment.training_bolt_quiver`, then Shift-click `ammo.training_bolt` for one 64-unit lot.
    Equip and confirm the Crossbow and Bolt Quiver in separate Scene transactions, store the Bolt lot,
    prepare it and verify `/mmo health` reports `crossbow=UNLOADED`, `ammo.training_bolt=64` and
    `quiver=64/64`.
68. Hold hotbar slot 1, wait for READY and right-click once. Verify `COCKING` lasts 12 authored ticks,
    then `BOLT COMMITTING` subtracts exactly one stored Bolt and continues through eight `LOCKING`
    ticks to `crossbow=LOADED`. No projectile may exist during reload.
69. Swap away and back after `LOADED`, then disconnect/reconnect and restart Paper. Verify the same
    Crossbow item UUID remains `LOADED` with the same bound Bolt category and quantity 63; changing
    the selected prepared category must not replace its bound Bolt.
70. Right-click the loaded Crossbow. Observe `crossbow=FIRED(COMMITTING)` if the local transaction
    crosses a tick, then verify the projectile appears only after the item payload commits to
    `UNLOADED`. The hit uses Crossbow move damage/posture, enters ten recovery ticks and does not
    subtract a second Bolt.
71. Interrupt during `COCKING` by weapon swap, dodge, hard CC, teleport or death and verify the item
    returns to `UNLOADED` without spending a Bolt. Repeat after `BOLT_PLACED` while `LOCKING`; verify
    it returns to the persisted `BOLT_PLACED` checkpoint and the next RMB resumes locking without
    spending another Bolt.
72. Stop/restart around the `BOLT_PLACED` commit. Verify database truth is either `UNLOADED` with 64
    Bolts or `BOLT_PLACED` with 63 Bolts, never a mixed pair. Reconnect/retry must not decrement the
    same lot version twice.
73. Remove or empty the equipped Bolt Quiver and attempt to reload. Verify `CROSSBOW NO QUIVER` or
    `CROSSBOW NO PREPARED BOLT`, no checkpoint mutation and no projectile. Inventory Bolt lots must
    never satisfy reload.
74. Open `/mmo dev` -> `Persisted Test Item`, grant `weapon.training_staff`, equip it as main hand
    through the Scene and wait for `STAFF READY`. Verify `/mmo health` reports `spell=IDLE` and
    `mana=100 (reserved=0)`.
75. LMB with the Staff and verify `move.training_staff.primary_1` opens its authored ARC, spends ten
    stamina at commit and resolves BLUNT damage/posture through the existing melee authority.
76. RMB once and verify `FIRE LANCE WINDUP` reserves 18 mana without reducing the current total.
    RMB again before eight charge ticks and verify `FIRE LANCE NOT READY`; `/mmo health` must still
    show `mana=100 (reserved=18)`.
77. RMB after the minimum charge (or wait for the 30-tick maximum). Verify `FIRE LANCE COMMITTING`
    appears before the projectile, then Fire particles launch only after PostgreSQL commits. Status
    must show mana 82, zero reserved mana and authored recovery.
78. Hit a training mob and verify vanilla armor is not used for Fire damage while server collision,
    HP, posture, projectile cap and conditional posture-break advantage still apply.
79. Swap weapons or trigger hard CC before release. Verify the spell cancels, mana returns to 100
    and catalyst durability does not change. Repeat during the in-flight commit and verify the
    terminal result never leaves a mana reservation or creates a projectile for an invalid session.
80. Cast once, disconnect/reconnect and restart Paper. Re-equip the same Staff UUID and cast again;
    the action bar must show catalyst durability continuing from 99/100 to 98/100 rather than
    resetting. Exhausted durability must report `CATALYST BROKEN` without mana reservation.
81. Use `/mmo dev` to grant `weapon.training_greatsword`. In Chronicle equipment, preview an empty
    off hand and the Greatsword before one Confirm. Verify the main-hand UUID persists, the off hand
    is empty and `/mmo health` reaches `GREATSWORD READY` after draw.
82. LMB with Greatsword and verify `move.training_greatsword.committed_cleave` spends 24 stamina,
    uses the wide authored ARC and produces more posture/guard pressure than the training Sword.
    RMB while Engaged must use the narrower, lower-stability Greatsword weapon guard.
83. Grant `weapon.training_sword` and `equipment.training_shield`. Preview both in the same Scene
    session and Confirm once. Verify signed IRON_SWORD/SHIELD projections appear in main/off hand,
    LMB runs `move.training_sword_shield.primary_1` and RMB uses the Shield's 145-degree guard with
    four exact perfect-guard ticks.
84. Attempt Greatsword with the Shield still selected and Sword & Shield with an empty off hand.
    Verify Scene rejects both combinations and no item location/version changes. Use the empty
    off-hand preview to change builds atomically.
85. Disconnect/reconnect and restart Paper after equipping Sword and Shield. Verify both exact item
    UUIDs return to their native slots. Preview empty off hand and Confirm; the same Shield UUID must
    move to one free authoritative inventory slot without duplicating its projection.
86. Return to the world spawn Rest Context in `EXPLORATION`, open Chronicle slot 9 and enter Combat
    Arts. Select the Technique and Form compatible with the equipped Staff, preview them, close the
    inventory and reopen it; database truth must remain unchanged until Confirm.
87. Confirm the Staff Technique plus Ember Channel Form. Reopen Chronicle and verify both selections
    are restored. Equip another weapon family and verify an incompatible build is rejected visibly
    before combat readiness rather than silently using the wrong move.
88. In Magic Attunement select Fire Lance, confirm, then cast with the Staff. Remove its attunement
    in a new preview and confirm; the same RMB must report that Fire Lance is not attuned. Exceeding
    capacity or selecting authored conflicting tags must reject without changing the committed row.
89. Enter `ENGAGED` or leave the configured spawn radius and attempt to open/commit build editing.
    Verify Chronicle reports that a Rest Context is required. Disconnect/reconnect and restart Paper
    after a valid build commit; the Technique, Form, attunement set and build version must return.
90. At the Rest Context, attune Cinder Snap, Fire Lance, Scorching Ground, Flame Torrent and/or
    Runic Ember Edge within the six-point capacity, then equip the Staff. Press F and verify only the
    committed attuned spells cycle; `/mmo health` must show the exact selected stable ID.
91. Select Cinder Snap and RMB while aiming at a training mob within six blocks. Verify eight mana
    and one catalyst durability commit before one Direct Fire hit. Aim at empty space and verify a
    visible MISS with no client-declared target.
92. Select Scorching Ground and RMB. Move more than 0.2 blocks during its ten-tick windup and verify
    cancellation refunds mana and does not create a zone. Cast while rooted and verify the zone
    appears only after catalyst commit, pulses every 20 ticks for 120 ticks, targets at most six per
    pulse and a fifth active zone is rejected.
93. Select Flame Torrent and RMB. Verify ten initial mana commits, then two Form-scaled mana per
    four-tick pulse, at most ten pulses and one crosshair target per pulse. RMB again to end early;
    insufficient upkeep, hard CC, dodge, swap, teleport or death must end it without a leaked mana
    reservation.
94. Select Runic Ember Edge and RMB. Verify 14 mana and one catalyst durability commit before
    `/mmo health` shows four Imbuement charges. LMB hits must consume exactly one charge per target
    and add a separate Fire packet without multiplying physical damage. Swap, die, teleport, logout
    or wait 240 ticks and verify the encounter-scoped coating clears.
95. Complete one hostile encounter with each of Greatsword, Sword and Shield, Bow, Crossbow and
    Staff, then reconnect and restart Paper. Verify exact equipment UUIDs, Quiver quantities and
    selected category, Crossbow checkpoint, Staff catalyst durability and character build return;
    transient projectiles, zones, channels and Runic coatings must not be reconstructed.

The current training adapter intentionally cancels vanilla entity damage while a combat weapon is
Ready or an MMO action is active. It emits the authored hitbox tick into the deterministic trace,
resolves an authoritative ARC against server bounding boxes/line of sight and calculates the
canonical physical damage breakdown. Managed players and training targets use server-owned
1,000-HP runtimes; vanilla hearts/entities are presentation and death projections, never combat
authority. Combat HP remains transient across logout/restart. Death-pouch wallet mutation,
sanctuary routing and crash-resumable encounter HP remain later transactional boundaries. The
client swing never declares a hit. The Basic Bow uses the same damage/HP/posture authorities and a
server-simulated projectile; because test projections deliberately block native item use, this
local Paper adapter uses first-RMB draw and second-RMB release. Confirmed Scene transactions move or
split compatible lots from inventory into the equipped Quiver UUID under the authored capacity.
Prepared state is item-owned and selected with stationary sneak+scroll; Bow release commits one unit
from only the selected stored category before projectile creation. Crossbow reload binds one selected
stored Bolt at `BOLT_PLACED`, persists `LOADED` on the item UUID and clears it before projectile
creation. Staff casting reserves mana before release, commits one item-owned catalyst durability
through PostgreSQL before any Direct, Projectile, Zone, Beam or Imbuement effect and uses the same
authoritative HP/posture engines with a separate arcane damage channel. F cycles committed attuned
Staff spells; Runic coatings and live spell effects remain encounter-scoped. Technique, Form and
Magic Attunement selections are
character-owned versioned state edited only from a Rest Context; learned-content gating belongs to
Milestone 6. Encounter recovery and lot merging remain later slices. Greatsword
and Sword & Shield use the same move/damage authority, while one shared item loadout policy validates
their empty-off-hand or Shield requirement before Scene commit and combat readiness.

Live engagement check:

1. Run `/mmo health` before combat and confirm `Combat session: EXPLORATION`.
2. Let a hostile mob acquire the player without landing a hit; confirm `ALERT`.
3. Commit the training attack or receive an entity hit; confirm `ENGAGED`.
4. Move until the mob clears its target. Confirm `DISENGAGING` and a decreasing `exit=...t`
   value in `/mmo health`.
5. Wait eight seconds without a hostile commit, damage, threat owner or encounter lock and confirm
   the state returns to `EXPLORATION`.

To test resource-pack admission, set `resource-pack.enabled: true`, configure an HTTP(S) URL and put
the exact 64-character SHA-256 from the active `content-manifest.json` in
`resource-pack.sha256`. A mismatch enters maintenance before new MMO sessions.

The current Scene uses the compact 2D fallback. The dev spawner creates signed PostgreSQL-backed
item/lot rows with test provenance and an audit journal. Their Bukkit projections are removed at
session end and rebuilt from database truth on the next session. Transfer and gameplay use remain
blocked for test-provenance values.

Paper uses the verified Minecraft player UUID as `CharacterId` for V1. Proxy authentication is
therefore expected to complete before the backend connection; this repository does not duplicate
the login credential system.

## Milestone 6 progression evidence lab

After joining the local Paper server as an operator/test account with an active character session:

96. Open `/mmo dev` and click `Progression Evidence Lab`. Verify the default `meaningful` scenario
    reports `ACCEPTED`, a positive award and `UNFAMILIAR->DEVELOPING`, followed by the explicit
    `Simulation only` notice.
97. Run `/mmo progression simulate dummy-intro`; verify it awards only the final point up to the
    introductory limit. Run the same command with `dummy-capped`; verify award `0` and reason
    `TRAINING_DUMMY_FAMILIARITY_COMPLETE`.
98. Simulate `invulnerable`, `loop`, `zero-risk` and `low-challenge`; every scenario must report
    `SUPPRESSED`, award `0` and its exact reason code.
99. Simulate `repeated`; verify the candidate remains accepted with a small positive award, proving
    repetition/daily decay is soft rather than a hard cap.

The simulation lab is non-authoritative test presentation: it creates no evidence row and cannot
alter a character. The following developer-only commands exercise the durable path; the live combat
outcome checks below exercise the server-authored adapter.

100. Run `/mmo progression status`; a new character must report `no meaningful evidence yet`.
101. Choose a UUID and run `/mmo progression record meaningful <uuid>`. Verify a positive
     `PERSISTED` award, then run `/mmo progression status` and verify a qualitative
     `mastery.greatsword` readiness band without an exact value.
102. Run the exact record command again. Verify `PERSISTED REPLAY`, the same decision and no band or
     track-version advance. Reusing that UUID for another scenario must report
     `PROGRESSION_EVIDENCE_ID_CONFLICT` and leave the previous state intact.
103. Disconnect/reconnect, then stop and restart Paper. `/mmo progression status` must restore the
     same qualitative band. Record `invulnerable` with a fresh UUID and verify a zero-award
     suppressed row without track advancement.
104. Equip each supported combat family and defeat a naturally hostile mob through the MMO combat
     runtime. Verify chat reports only `Combat learning recorded: <track>=<band>` and
     `/mmo progression status` contains the matching Mastery plus mapped Conditioning track.
105. Hit one durable hostile with repeated Zone/Channel pulses or a piercing projectile. Defeat it
     and verify one outcome summary is persisted: contacts sharing an action UUID must not become
     separate evidence candidates.
106. Tag a fresh hostile with `branzmmo.training_dummy`, defeat it repeatedly and verify it cannot
     advance beyond introductory familiarity. Repeat with `branzmmo.zero_risk` and verify no track
     advancement.
107. Hit a hostile, disengage until combat returns to `EXPLORATION`, then re-engage the same entity.
     Verify the Retreat and later Victory use separate encounter/evidence identities without an
     idempotency conflict.
108. Die after landing a valid action and verify Defeat evidence is bounded and qualitative. Force
     teleport during another active segment and verify Abandoned produces no award.

## Milestone 6 learning, teaching and Renown kernel lab

The following commands are environment-gated pure simulations. They do not write knowledge, a
teaching session, teacher rewards or Renown to PostgreSQL.

109. Open `/mmo dev` and click `Teaching & Renown Lab`. Verify Teaching reports
     `READY_TO_COMMIT`, Renown reports a fresh award of 20 and both print `Simulation only`.
110. Run `/mmo teaching simulate success`; verify a demonstration followed by three unique
     successful student actions reaches `READY_TO_COMMIT`. Run `duplicate-action`; three reports
     sharing one action UUID must remain `INVALID_PHASE` because only one unique success counted.
111. Simulate `missing-teacher`, `unready-teacher` and `student-prerequisite`; verify stable
     `TEACHER_MISSING_KNOWLEDGE`, `TEACHER_NOT_READY` and `STUDENT_NOT_ELIGIBLE` results.
112. Simulate `expired` and `disconnect`; both must reject completion without granting knowledge or
     a teacher reward.
113. Run `/mmo renown simulate fresh`, `repeat-1`, `repeat-2` and `exhausted`; verify awards
     20, 10, 5 and 0. Run `duplicate`; verify `DUPLICATE_DEED`, award 0 and unchanged total.

The durable developer fixture requires two online operator/test accounts with ready Player Sessions.
It represents an already validated completion intent; it is disabled outside permitted non-production
environments and is not the live challenge-input path.

114. As the teacher run `/mmo teaching status` and
     `/mmo teaching status <student>`; verify both start with learned `none` and Renown 0.
115. Choose two UUIDs and run `/mmo teaching record <student> <teaching-session-uuid> <deed-uuid>`.
     Verify `Teaching PERSISTED`, the student receives `technique.greatsword.cleave`, the teacher
     receives 20 Renown and both `/mmo teaching status` views update from reloaded Player Sessions.
116. Repeat the exact command. Verify `PERSISTED REPLAY`, no second Knowledge row, no additional
     Renown and no projection version advance. Reuse only one UUID with changed input and verify the
     stable conflict reason with no partial reward.
117. Have the student open Chronicle at Rest Context and select Greatsword Cleave. Verify it can be
     committed after learning; an untaught Technique must report `BUILD_KNOWLEDGE_REQUIRED`.
118. Disconnect/reconnect both players and restart Paper. Verify the student's learned Technique and
     teacher's Renown return. Repeating the exact record after restart must remain a replay.

The following checks exercise the normal live action bridge. On fresh profiles, use the student
from step 115 as the live teacher (that character now owns Greatsword Cleave) and the original
teacher as the live student (that character does not own it).

119. Grant and equip `weapon.training_greatsword` for both players through `/mmo dev` and Chronicle.
     Let both combat sessions return to `EXPLORATION`, finish active actions and stand within 16
     blocks in the same world.
120. As the live teacher run
     `/mmo teaching start <student> technique.greatsword.cleave`. Verify both receive the
     demonstration prompt; `/mmo teaching session` must report `DEMONSTRATION`, progress `0/3` and
     the immutable teaching-session UUID.
121. Have the teacher land one LMB Greatsword Cleave on a living target. Verify the session changes
     to `STUDENT_CHALLENGE`. A miss or another move must not change the phase.
122. Have the student land one cleave across two nearby targets. Verify progress is only `1/3`
     because both contacts share one server action UUID. Land two more separate cleaves and verify
     progress reaches `3/3`, prints `COMMITTING`, then `Teaching persisted`.
123. Run `/mmo teaching status` for both characters. Verify the student permanently owns
     `technique.greatsword.cleave`, the teacher received the resolved mentorship Renown and the live
     session is gone. Reconnect/restart and verify both results remain.
124. With two otherwise eligible fresh participants, start another session and test
     `/mmo teaching cancel`, separation beyond 16 blocks, disconnect and ten-minute expiry. Each
     must remove the session without Knowledge or Renown.

The Form/Spell acquisition fixture is available only when development tools are enabled and the
player has `branzmmo.dev`. It resolves the policy authored in the active content snapshot; it does
not accept a caller-selected source identity.

125. On a fresh character at Rest Context, try to select `form.iron_root` with a Greatsword and
     commit. Verify `BUILD_KNOWLEDGE_REQUIRED`; owning/equipping the weapon must not teach the Form.
126. Choose an acquisition UUID and run
     `/mmo knowledge acquire FORM form.iron_root <uuid>`. Verify `Knowledge learned`, then repeat
     the exact command and verify `Knowledge replay confirmed` with only one Knowledge/journal row.
127. Repeat the same Form with a new UUID. Verify `KNOWLEDGE_ALREADY_LEARNED` and no new row. Reopen
     Chronicle, select Iron Root and verify the build now commits at Rest Context.
128. Run `/mmo knowledge acquire SPELL spell.ember.cinder_snap <uuid>` on a fresh acquisition UUID.
     Equip a Staff, attune Cinder Snap and verify the cast becomes available. Disconnect/reconnect
     and restart Paper; both learned keys and the prepared build must return.
129. With Cinder Snap learned but Staff Mastery still Unfamiliar, run
     `/mmo knowledge acquire SPELL spell.ember.fire_lance <uuid>`. Verify
     `MASTERY_NOT_READY: mastery.staff>=DEVELOPING` and no durable grant.
130. In a PostgreSQL integration fixture, reuse the Form acquisition UUID for a different Spell or
     character. Verify `ACQUISITION_ID_CONFLICT` and atomic rollback.

The following Consumable Lab commands are environment-gated pure simulations. They do not consume
live inventory, change Bukkit resources or write PostgreSQL.

131. Run `/mmo consumable simulate flask`. Verify `COMMITTED`, `charges=4/5` and
     `maximum-health=0.35`.
132. Run `/mmo consumable simulate timeline`. Verify `INTERRUPTED_AFTER_COMMIT`,
     `commit-now=true` and `consumed=true`, demonstrating exact commit-tick priority.
133. Run `/mmo consumable simulate ailment`. Verify Burn is active at tier 1 with buildup consumed
     to zero.
134. Run `/mmo consumable simulate category`. Verify
     `RARE_REPLACEMENT_CONFIRMATION_REQUIRED` without replacing the rare Body Tonic.
135. Run `/mmo health`. Verify content `v1.milestone-1.example.4`, `definitions=46`, `items=19`
     and `ailments=6`.
136. Run `/mmo consumable simulate ailment`. Verify the authored `status.burn` reaches active tier
     1; startup must enter maintenance if any one of the six status files is missing or invalid.
137. Offline, run
     `.\gradlew.bat :mmo-content:run --args="validate $((Resolve-Path example-content\milestone-1).Path)"`.
     Verify the immutable `.4` snapshot reports 46 definitions and no diagnostics.
138. Run `/mmo consumable status`; on a new character verify durable version `0`, Flask `0/5`, no
     category effects and no ailments.
139. Run `/mmo consumable persist <new-uuid>`, wait for the green PostgreSQL confirmation, then run
     `/mmo consumable status`. Verify version `1`, Flask `3/5`, one Body Tonic effect and Burn plus
     Corruption state.
140. Disconnect/reconnect and verify the same status. Stop Paper cleanly, run it again against the
     same embedded PostgreSQL directory, reconnect and verify the values and version remain. Use a
     new operation UUID for a deliberate later mutation; UUID reuse is reserved for retrying the
     same in-flight request.
141. Run `/mmo consumable checkpoint capture <checkpoint-uuid> <operation-uuid>` and verify status
     prints that checkpoint UUID. Spend or replace the current Flask fixture with a different fresh
     operation UUID, then run `/mmo consumable checkpoint restore <checkpoint-uuid>
     <operation-uuid>` and verify the exact captured allocation/charges return.
142. Repeat the exact restore command after its successful Player Session reload. Verify the same
     version and success replay rather than an idempotency conflict or another version increment.
     Restore with another checkpoint UUID and verify `FLASK_CHECKPOINT_MISMATCH` with no mutation.
143. Disconnect/reconnect and restart Paper. Verify the prepared checkpoint UUID and captured Flask
     remain available. Ordinary respawn/death must not invoke this fixture or restore charges; the
     live confirmed party-wipe signal is deferred to the encounter controller.
144. Reconnect with a ready Player Session. Verify exactly one `Expedition Flask` representation is
     present in gameplay hotbar slots 1-8, its lore matches `/mmo consumable status`, and it cannot
     be dropped, shift-moved to a container, placed in off-hand or duplicated across reconnect.
145. Select the Flask and use sneak + right-click to cycle Healing, Mana and Stamina. Right-click a
     charged dose after the weapon finishes sheathing. Verify movement slows during windup; sprint,
     jump or selecting another hotbar slot before offset 18 cancels without changing durable version
     or charges.
146. Use a fresh charged dose without interruption. At offset 18 verify the action reports
     `COMMITTING`; only after PostgreSQL confirmation should HP, Mana or Stamina restore and
     `/mmo consumable status` show one fewer selected charge. Interrupt during commit/recovery and
     verify the charge remains spent and no second restoration occurs.
147. Complete a use normally and verify the previous combat slot returns. Select another slot during
     use and verify it remains selected. Disconnect/reconnect and restart Paper; the spent charge
     remains spent and exactly one refreshed Flask representation returns.
148. In `EXPLORATION` near world spawn, open Chronicle slot 9 and select `Expedition Flask`. Verify
     the page shows the current five-slot Healing/Mana/Stamina allocation, owned Infusion Stock and
     `Rest Context ready`. Move slots among all three dose types; the displayed total must stay five.
149. In the environment-gated dev menu grant at least five `material.infusion_stock`, return to the
     Flask page and confirm. Verify the green acknowledgement reports consumed stock, the hotbar
     Flask shows the chosen full allocation and `/mmo consumable status` reports the new durable
     version. Moving away from spawn or entering combat must reject without consuming stock.
150. Deliberately race the confirmation against another durable character mutation or repeat the
     same prepared transaction in the integration fixture. Verify stale/busy failure leaves both
     stock and Flask unchanged, while exact operation replay does not consume stock twice.
151. Spend the Flask below two total charges, remove all Infusion Stock and confirm at Rest. Verify
     Mercy grants exactly two charges without stock. Disconnect/reconnect and restart Paper; verify
     the chosen allocation, charges and remaining stock reload exactly once from database truth.
152. From the environment-gated dev menu, grant at least two each of
     `consumable.training_body_tonic`, `consumable.training_elemental_ward`,
     `consumable.training_weapon_coating`, `consumable.training_utility_preparation` and
     `food.training_meal`. Move a signed stack to gameplay hotbar slots 1-8 and verify it retains its
     database-authoritative lore.
153. With the weapon fully sheathed, right-click a Body Tonic. Interrupt before its authored commit
     tick by jumping, sprinting or selecting another slot; verify the stack quantity, durable version
     and `/mmo consumable status` remain unchanged.
154. Use the Tonic again without interruption. Verify `COMMITTING` occurs at the authored tick, the
     stack decrements by exactly one only after PostgreSQL acknowledgement, BODY_TONIC appears in
     `/mmo consumable status`, and recovery completes afterward. Repeat the exact operation in the
     integration fixture and verify it cannot consume a second unit.
155. Use one item from each other category and verify all five categories coexist while a new item
     replaces only its own category. Attempt to replace the active rare Utility Preparation with a
     normal right-click and verify no item is consumed; sneak + right-click must explicitly confirm.
     Enter `ENGAGED` and verify a Meal is rejected until returning to `EXPLORATION`.
156. Leave effects active for more than 100 ticks, disconnect/reconnect and restart Paper. Verify
     remaining ticks resume from the latest bounded checkpoint, offline wall-clock time does not
     reduce them and expired effects are durably removed. Tamper with or duplicate a signed client
     projection and verify PostgreSQL version checks prevent a second effect/item consumption.
157. Run `./gradlew :mmo-worldloop:test` and inspect the boss lifecycle cases. Verify a connected
     survivor prevents wipe, reconnect before the 1,200-tick deadline preserves the attempt and
     expiry contributes to wipe only after every locked participant is unavailable.
158. Verify the reset case emits every locked participant exactly once, exact reset-operation replay
     emits no restore participants and completion advances the attempt once. Verify cross-command
     operation UUID reuse fails closed.
159. Verify victory can freeze a pending wipe, requests reward reconciliation once and makes every
     later reset invalid. Replaying the victory or reward operation must produce no second effect or
     reward grant.
160. In `LOCAL` with dev tools enabled, open `/mmo dev` and select `Boss Encounter Lab`, or run
     `/mmo encounter start <new-uuid>` directly. Verify the green ACTIVE message appears only after
     the Player Session checkpoint commit and `/mmo encounter status` reports attempt 1 with the
     same encounter/checkpoint UUID.
161. Spend an Expedition Flask charge, then die or run `/mmo encounter defeat`. Verify solo enters
     confirmed wipe, restores the exact prepared Flask after PostgreSQL acknowledgement and reports
     attempt 2. Verify normal consumable effects and ailments shown by `/mmo consumable status` are
     unchanged.
162. Start with `/mmo encounter start <new-uuid> <player-one> <player-two>`. Defeat one participant
     and verify the active survivor prevents wipe/restore. Defeat the survivor and verify both
     prepared Flasks restore once; an offline participant must remain pending until reconnect and
     Player Session readiness.
163. Quit/rejoin before 1,200 ticks and verify the same attempt remains active. Run `/mmo encounter
     boundary` followed by `/mmo encounter rejoin` and verify the same result; allow the grace to
     expire while every other participant is defeated and verify one confirmed wipe occurs.
164. Start a fresh encounter and run `/mmo encounter victory` before wipe. Verify status is
     `VICTORY_PENDING`, death cannot trigger Flask restore, then run `/mmo encounter rewards
     <new-grant-uuid>` and verify the empty lab reconciliation completes once. Actual personal loot
     is intentionally deferred to the reward slice.
165. Run the V0009 embedded PostgreSQL repository integration test. Verify encounter creation writes
     version 1 with one committed journal/audit row, and exact request replay reports replay without
     another state version or audit row.
166. Replace the record through `RESETTING` and verify `findRecoverable` returns it in stable order;
     replace it with `COMPLETED` and verify recovery excludes it. A stale expected version and a
     reused operation ID with changed payload must both leave the latest row unchanged.
167. Start a boss lab encounter, spend Flask charges and stop Paper while the encounter is `ACTIVE`.
     Restart against the same embedded PostgreSQL directory, reconnect within the fresh 1,200-tick
     grace and verify `/mmo encounter status` shows the same encounter/attempt with no Flask restore.
168. Stop Paper after the last defeat is durably `WIPE_PENDING` but before reset begins. Restart and
     verify the controller commits `RESETTING` before restoring any Flask. Stop again after one party
     member receives the restore; restart and verify already-restored state is skipped, remaining
     members restore once and the next attempt is committed once.
169. Queue near-simultaneous `/mmo encounter defeat <player>` commands for two participants. Verify
     both updates survive PostgreSQL acknowledgement in FIFO order and one confirmed wipe occurs;
     no stale participant snapshot may overwrite the other.
170. Stop Paper in `VICTORY_PENDING`, restart and verify death cannot trigger reset/Flask restore.
     Reconcile the empty lab reward using a stable grant UUID, restart again and verify the completed
     encounter is excluded from recovery and its participant locks are released.
171. Run `./gradlew :mmo-social:test` and inspect the downed flow. Verify the first non-Execute lethal
     event in a two-player encounter emits DOWNED with a 300-tick deadline, while solo and Execute
     emit death immediately.
172. Begin an ally revive, interrupt it before 80 ticks and verify the revive allowance remains
     unused. Restart and commit a channel; verify the output requests exactly 25% health, marks the
     revive consumed and grants 60 ticks of protection.
173. Apply hostile action to end protection, then apply another lethal event and verify immediate
     death. Advance an unfinished revive exactly at the 300-tick downed deadline and verify expiry
     wins with no heal output. Replay operation UUIDs and verify no effect is emitted twice.
174. In `LOCAL` with two ready players, run `/mmo encounter start <new-uuid> <player-one>
     <player-two>`, then `/mmo downed down <player-one>`. Verify PlayerDeathEvent does not fire,
     positional movement is locked and the action bar counts down from 15 seconds.
175. As the active ally, run `/mmo downed revive <player-one>`. Stand still and avoid damage for four
     seconds; verify the target returns at 25% combat health with three seconds of damage protection.
     Repeat on a fresh attempt but move or receive damage before commit; verify the channel cancels
     and `/mmo downed status` still reports `reviveConsumed=false` for the target.
176. After a committed revive, deal or simulate another lethal event with `/mmo downed down
     <player-one>` and verify normal death reaches the boss lifecycle. On another fresh attempt run
     `/mmo downed execute <player-one>` and verify it bypasses DOWNED immediately. Let a first downed
     timer expire and verify it also becomes real death.
177. During revive protection, take incoming entity/environment damage and verify health is not
     reduced. Run `/mmo downed hostile` or land a successful hostile combat action and verify the
     next hit applies normally. Defeat the final participant and verify the existing durable boss
     wipe and prepared-Flask restore advance to a new attempt with a fresh revive allowance.
178. Run the V0010 embedded PostgreSQL repository integration test. Verify the first downed-state
     create references an existing V0009 boss encounter and writes version 1 with one committed
     journal/audit row. Exact request replay must not add another version or audit entry.
179. Replace the state with attempt 2 and verify `findRecoverable` returns it in stable order. Mark
     the same row non-recoverable and verify recovery excludes it while direct lookup retains the
     final version and audit history.
180. Attempt a stale expected version and reuse a committed operation UUID with changed payload.
     Verify both fail closed without changing the latest downed row, transaction journal or audit.

To use an external PostgreSQL, set `database.mode: EXTERNAL`, configure `database.jdbc-url`,
`database.username` and `database.password`, and retain `database.run-migrations: true`. Embedded
mode is rejected outside `LOCAL` and `INTEGRATION`.

## Bootstrap content and providers

Normal local startup reads `plugins/BranzMMO/content` unless `mmo.content.path` supplies an
absolute content root. The plugin registers its login gate before asynchronously loading content.
Sessions remain blocked while startup is running or when validation enters maintenance.

`content-manifest.json` may pin enabled integration versions in `providerVersions` using the keys
`oraxen`, `mythicmobs`, `packetevents`, `worldguard` and `wallet`. Enabled providers and their
required/optional policy are configured in `plugins/BranzMMO/config.yml`. An exact pin mismatch is
reported as unavailable; an unavailable required provider enters maintenance, while an unavailable
optional provider starts in degraded mode.
