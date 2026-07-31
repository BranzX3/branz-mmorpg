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
.\gradlew.bat :mmo-content:run --args="validate example-content\milestone-1"

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

The current training adapter intentionally cancels vanilla entity damage while a combat weapon is
Ready or an MMO action is active. It emits the authored hitbox tick into the deterministic trace,
resolves an authoritative ARC against server bounding boxes/line of sight and calculates the
canonical physical damage breakdown. It keeps isolated 1,000-HP training accounting rather than
applying vanilla damage; persistent MMO health/death is a later combat/encounter boundary. The
client swing never declares a hit.

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
