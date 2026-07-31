# AI Coding Handoff

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
Implement Milestone <N>, task <bounded task name>.

Authoritative docs:
- docs/02-system-invariants.md
- docs/<owning document>.md
- docs/35-persistence-transactions.md
- docs/41-cross-system-acceptance.md
- docs/43-content-authoring-tools.md (when authoring/dev tooling is in scope)

Do not implement adjacent future milestones.
List the invariants this change enforces.
Use typed result/error codes.
All ownership/currency changes must use TransactionService.
Add unit, integration and crash-point tests.
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
