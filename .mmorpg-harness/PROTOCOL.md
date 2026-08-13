# Branz MMORPG Deterministic Harness v1

## Authority and isolation

This harness is owned only by `BranzX3/branz-mmorpg`.

- Control branch: `HARNESS_MMORPG_CONTROL`
- Worker root: `%LOCALAPPDATA%\\BranzMMORPGHarness`
- Dedicated worker clone: `%LOCALAPPDATA%\\BranzMMORPGHarness\\repo`
- Scheduled Task: `BranzMMORPGHarnessDaemon`
- Mutex: `Local\\BranzMMORPGHarnessDaemon`
- Active authority: `.mmorpg-harness/ACTIVE_TASK.json`
- Task manifests: `.mmorpg-harness/tasks/<TASK_ID>.json`
- Evidence: `.mmorpg-harness/results/<TASK_ID>/`

The harness must never read from, write to, stop, reconfigure, or reuse `%LOCALAPPDATA%\\TradebotHarness`, `TradebotHarnessDaemon`, or the Tradebot harness mutex.

GitHub remote state is authoritative. The daemon fetches `origin` itself before reading control runtime or task authority. The developer working copy is never used as a worker and is never cleaned, reset, stashed, switched, or committed by this harness.

## Security model

Remote task JSON is a capability selector, not a remote shell.

A task manifest may contain exactly:

- `protocol_version`
- `task_id`
- `task_branch`
- `base_commit`
- `action_id`
- `writable_paths`

The runner rejects unknown keys and rejects any manifest containing command-like keys such as `commands`, `argv`, `shell`, `script`, `powershell`, `gradle_args`, `java_args`, `env`, or `environment`.

`action_id` must name a reviewed built-in action compiled into `.mmorpg-harness/runner.py`. Built-in actions own their argv, timeouts, environment policy, and allowed write policy. A task cannot broaden behavior with arguments.

Initial allowlist:

- `HARNESS_CANARY_V1`
- `MMO_GRADLE_VERIFY_V1`
- `MMO_GRADLE_TEST_ALL_V1`
- `MMO_GRADLE_TEST_COMBAT_V1`
- `MMO_GRADLE_TEST_SCENES_V1`
- `MMO_GRADLE_TEST_PERSISTENCE_V1`
- `MMO_GRADLE_TEST_CONTENT_V1`
- `MMO_GRADLE_TEST_ITEMS_V1`
- `MMO_GRADLE_TEST_QUESTS_V1`
- `MMO_GRADLE_TEST_LIFESKILLS_V1`
- `MMO_GRADLE_TEST_MARKET_V1`
- `MMO_BOOTSTRAP_SHADOWJAR_V1`
- `MMO_BOOTSTRAP_SMOKE_V1`
- `MMO_BOOTSTRAP_COMBAT_ACCEPTANCE_V1` — **deprecated; do not dispatch**. The current
  implementation does not consume the `combatAcceptance` Gradle property, so this legacy action
  starts an ordinary Paper server and eventually times out. Use `MMO_GRADLE_TEST_COMBAT_V1`
  followed by `MMO_BOOTSTRAP_SMOKE_V1` for deterministic automated combat/bootstrap preflight.
  A real combat acceptance remains a local Minecraft-client gate and must not be inferred from
  either automated action.
- `MMO_BOOTSTRAP_INVALID_CONTENT_SMOKE_V1`
- `MMO_CONTENT_VALIDATE_FIXTURE_V1`
- `MMO_CLIENT_ACCEPTANCE_COMPILE_V1` — compile the checked-in standalone Minecraft 26.2 Fabric Client GameTest project using its own pinned Gradle wrapper. No remote argv or environment overrides are accepted.
- `MMO_CLIENT_ACCEPTANCE_INGRESS_V1` — run a fresh offline local Paper 26.2 server and the checked-in Fabric Client GameTest with fixed commands; OP and XP-level 7 are console-only acceptance staging, `/mmo physical status` is typed through real client chat input, and Paper/client logs are retained as evidence.
- `MMO_CLIENT_ACCEPTANCE_PRIMARY_INPUT_V1` — reuse the fixed real-client ingress path with `-PphysicalPrimaryInputAcceptance=true` on both Paper and the checked-in client test; require TransactionService-backed training-blade persistence, authoritative selected-slot projection observed by both server and client, a real client left-mouse press, then Paper evidence that the existing `PlayerAnimationEvent.ARM_SWING` path resolved to `SemanticInput.PRIMARY` and was accepted by `InputRouter.routeFrame`. This remains a bounded primary-input authority gate, not full A–F acceptance.
- `MMO_CLIENT_ACCEPTANCE_PRIMARY_HIT_V1` — extend the reviewed physical PRIMARY path with fixed B4 target staging only: two immobile eligible iron golems are summoned by repository-owned Paper-console commands inside the authored Training Blade ARC, XP level 11 releases the checked-in client to send one real LMB, both targets must show canonical health change, the successful-action observer must fire exactly once, and the client must prove the same Training Blade UUID/location/content with version `v+1`, durability `d-1`, and a changed transaction id. No task-controlled command, target, position, marker, regex, argv, or environment input is accepted. This is Section B4 only.
- `MMO_CLIENT_ACCEPTANCE_CHRONICLE_SLOT_V1` — reuse the fixed real-client ingress path with server-side `physicalHotbarAcceptance=true` staging and client-side `physicalChronicleSlotAcceptance=true`; require a real InventoryScreen Training Blade pickup and physical Chronicle-slot click, visible rejection, Chronicle preservation, reconciliation, reconnect reconstruction, byte-stable Training Blade authority, exactly three `/mmo physical status` commands, and zero `PHYSICAL_AUTHORITY_HOTBAR_MOVE_COMMITTED_SERVER` markers. No task-controlled argv, slot, command, regex, environment, or path input is accepted.
- `MMO_CLIENT_ACCEPTANCE_HOTBAR_MOVES_V1` — reuse the fixed real-client ingress path with `-PphysicalHotbarAcceptance=true`; require two real InventoryScreen cursor pickup/place moves through currently empty gameplay hotbar destinations, two reconnect reconstructions, exactly five `/mmo physical status` snapshots, one stable Training Blade UUID/durability, slot continuity `0,d1,d1,d2,d2` where `d1` and `d2` are distinct gameplay slots 0–7, version sequence `v,v+1,v+1,v+2,v+2`, and authoritative server commits exactly matching `0→d1` then `d1→d2`. This is the bounded Section B1-B2 gate only.

The deprecated combat-acceptance action remains compiled only for protocol compatibility with
historical task manifests. New tasks must not select it. Removing the compiled action is a separate
runner cleanup and must pass the harness self-test before control-branch promotion.

Adding a capability requires changing and reviewing runner code on the control branch first.

## Deterministic task flow

1. daemon ensures the dedicated worker clone exists;
2. daemon fetches GitHub and self-updates daemon/runner/selftest from the control branch;
3. runner independently fetches GitHub;
4. runner reads remote `ACTIVE_TASK.json` from the control branch;
5. `IDLE` and `PAUSED` execute nothing;
6. `ACTIVE` must reference a manifest under `.mmorpg-harness/tasks/`;
7. runner validates exact schema, task ID, branch prefix, base SHA, action allowlist and static write policy;
8. any dirty substantive worker state fails closed;
9. runner switches only within the dedicated worker clone;
10. task branch must already exist remotely;
11. task branch HEAD must exactly equal `base_commit` before action execution;
12. built-in action executes with fixed argv and timeout;
13. stdout/stderr, environment, Git state, hashes, control/task provenance and action metadata are captured;
14. unexpected tracked/untracked writes outside the static action policy fail the run;
15. only authorized evidence is staged;
16. evidence is committed as `harness-result(<TASK_ID>): <PASS|FAIL>`;
17. task branch is pushed to GitHub;
18. an interrupted push is retried deterministically from the existing local result commit.

No automatic `reset --hard`, `clean`, stash, rebase, merge commit, force push, branch deletion, source repair, or fallback command is permitted.

## Evidence bundle

Every executed task writes:

- `result.json`
- `stdout.log`
- `stderr.log`
- `environment.json`
- `dirty_manifest.json`
- `hashes.json`

Actions may add action-specific files only inside the task result directory.

`result.json` records protocol version, action ID, control commit, task manifest SHA-256, expected base commit, actual starting commit, ending commit, execution status, blocker code, timing, fixed command identity and write policy.

## Fail-closed rules

The runner blocks on malformed remote JSON, protocol mismatch, unknown fields, command-like fields, unknown action IDs, action/write-policy mismatch, missing task branches, base commit mismatch, dirty worker state, timeouts, unexpected writes, staging outside policy, commit failure, or push failure.

A task action failure is evidence and is committed/pushed as `execution_status=FAIL`. The runner never edits source to make a test pass.

## Project-specific assumptions

The current implementation branch uses Gradle multi-project build `mmo-platform`, JDK 25, Paper 26.2, `mmo-bootstrap:runServer`, embedded PostgreSQL integration tests and fixed local smoke properties. Harness Gradle actions use the checked-in wrapper and an isolated `GRADLE_USER_HOME` under the MMORPG WorkerRoot.

The supported deterministic smoke actions use only repository-authored fixed properties:

- `:mmo-bootstrap:runServer -PsmokeTest=true`
- `:mmo-bootstrap:runServer -PsmokeTest=true -PsmokeInvalidContent=true`

The historical `-PcombatAcceptance=true` invocation is not a supported smoke path because the
current `mmo-bootstrap` build does not map that property to a JVM acceptance mode or deterministic
shutdown lifecycle.

Smoke actions never accept a remote content path or arbitrary Gradle arguments.

The client acceptance compile action is isolated from the production multi-project wrapper. It executes only `acceptance/physical-client-26.2/gradlew[.bat] --no-daemon --console=plain build` from that fixed directory and records the nested wrapper hashes in action metadata.
