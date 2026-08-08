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
- `MMO_BOOTSTRAP_INVALID_CONTENT_SMOKE_V1`
- `MMO_CONTENT_VALIDATE_FIXTURE_V1`

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

The smoke actions use only the repository-authored fixed properties:

- `:mmo-bootstrap:runServer -PsmokeTest=true`
- `:mmo-bootstrap:runServer -PsmokeTest=true -PsmokeInvalidContent=true`

They never accept a remote content path or arbitrary Gradle arguments.
