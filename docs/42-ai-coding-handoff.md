# AI Coding Handoff

## Feature-completion workflow

Milestones are planning groups, not delivery units. Development must complete one player-facing
feature at a time as a vertical slice. Only one feature may be `IN_PROGRESS`; do not begin an
adjacent feature merely because its kernel, schema or interface can be scaffolded cheaply.

A feature may be marked `COMPLETE` only when all of the following are true:

1. A player can enter, use and finish the primary flow through supported gameplay UI/input. A dev
   command may prepare test data, but it must not substitute for a missing runtime path.
2. Domain rules, the Paper adapter, content definitions and provider boundaries are connected. A
   headless kernel or placeholder UI alone is not a completed feature.
3. Authoritative state survives disconnect and server restart. Value-changing flows also pass
   idempotency, rollback and relevant crash-point tests.
4. Success, rejection, unavailable-state and recovery feedback are visible and actionable to the
   player and operator. Default configuration upgrades must not silently disable the flow.
5. Unit and integration tests pass, followed by a real local Paper client acceptance pass covering
   the happy path, important rejection paths and restart/reconnect behavior.
6. The implementation documents the acceptance evidence and has no known blocker that prevents the
   primary flow from being used as designed.

Use these status labels: `NOT_STARTED`, `IN_PROGRESS`, `AUTOMATED_VERIFIED`, `LIVE_ACCEPTED` and
`COMPLETE`. Automated tests can advance a feature only to `AUTOMATED_VERIFIED`. A failed live check
returns it to `IN_PROGRESS`, and the next feature remains blocked until the failure is fixed and the
full feature acceptance is rerun.

Scaffolding is allowed only as an internal step of the active feature. Never describe a scaffold,
kernel, lab, adapter skeleton or “ready for acceptance” state as complete. Historical milestone
status text and `IMPLEMENTED.md` list code coverage; they do not override this completion gate.

## Required first read

Every coding session must read:

1. `02-system-invariants.md`
2. `03-architecture.md`
3. `04-identifiers-content-contracts.md`
4. the owning subsystem document
5. `35-persistence-transactions.md` when value or persistence is involved
6. `41-cross-system-acceptance.md`
7. `43-content-authoring-tools.md` for content schemas, validators, simulations, labs or dev commands

## Prompt template

```text
Complete feature <player-facing feature name> as one vertical slice within Milestone <N>.

Authoritative docs:
- docs/02-system-invariants.md
- docs/<owning document>.md
- docs/35-persistence-transactions.md
- docs/41-cross-system-acceptance.md
- docs/43-content-authoring-tools.md (when authoring/dev tooling is in scope)

Do not implement adjacent future milestones.
Keep exactly one feature IN_PROGRESS and do not stop at kernel/scaffold readiness.
List the invariants this change enforces.
Use typed result/error codes.
All ownership/currency changes must use TransactionService.
Add unit, integration and crash-point tests.
Run and record local Paper client acceptance before marking the feature COMPLETE.
Document schema/config/content changes and migration impact.
Do not use Bukkit /reload assumptions.
```

## Pull-request contract

Each PR must include:

- scope and non-goals;
- documents/sections implemented;
- state transitions added or changed;
- persistent tables/columns and migration;
- transaction/idempotency behavior;
- threading model;
- provider calls and fallback;
- tests and acceptance scenarios;
- metrics/logs;
- developer inspection/debug path for new runtime state;
- rollback/forward-fix plan.

## Agent stop conditions

The agent must stop and report a spec defect instead of inventing behavior when:

- two authoritative documents conflict;
- an ownership destination is absent;
- an operation can commit partly without a journal plan;
- an external provider is called directly from gameplay;
- a persistent ID must be renamed/reused;
- a requested shortcut violates a system invariant;
- a failure path would delete or duplicate value.

## Recommended first task

Create the Gradle module skeleton, stable ID classes, `Result<T, ErrorCode>`, content manifest parser, provider health interface and test fixtures. Do not add combat listeners, custom items or database writes in the first PR.

## Recommended second task

Implement PostgreSQL migration runner, character lease, transaction journal and test-only item/lot repositories. Prove idempotent grant and item location compare-and-set through integration tests before connecting Oraxen or player events.

## Context-size rule

Give coding agents only the owning documents plus invariants/persistence/acceptance rather than the entire pack for every task. Maintain a short `IMPLEMENTED.md` in the code repository mapping document sections to code/tests and known deviations.


## Content-tool task rule

When implementing content tooling, use the same compiled definition/resolver libraries as runtime code. Do not create a second formula or schema interpretation. Tool output may modify only a local Git working tree, isolated test database or disposable environment; it must never write directly to Production.
