# Branz MMORPG — Parallel Implementation Roadmap

Status: Authoritative workstream order  
Related specifications:

- DEVELOPMENT_OWNERSHIP_AND_CONTRACTS.md
- CORE_MMO_SPECIFICATION.md
- QUEST_DIALOGUE_CUTSCENE_SPECIFICATION.md
- PHASE_1_FOUNDATION.md

## 1. Workstream order

Core and Quest proceed in parallel after agreeing on API contracts.

| Milestone | Core MMO workstream | Quest workstream | Integration gate |
|---|---|---|---|
| M0 | C0 Foundation adoption | Q0 Module/contracts | Dependency and ownership checks |
| M1 | C1 Player session, C2 Attributes | Q1 Compiler, Q2 State machine | Player identity and lifecycle contract |
| M2 | C3 Status, C4 Combat | Q3 Objectives, Q4 Conditions/actions | Domain-event envelopes and fake adapters |
| M3 | C5 Skills, C6 Mastery | Q5 Persistence/migration | Mastery query/event and operation ID |
| M4 | C7 Items/loot | Q6 Dialogue engine, Q7 Renderer/history | Item query/reward and player audience |
| M5 | C8 Crafting/economy | Q8 NPC/world integration | Craft, item, region, interaction events |
| M6 | C9 Mob AI, C10 Encounter/boss | Q9 Tracker/journal, Q10 Cutscene | Mob/boss events and actor/camera adapters |
| M7 | C11 Party/trade | Q11 Party/private scenes | Stable party snapshot contract |
| M8 | C12 Operations, C13 Hardening | Q12–Q15 Tools/editor/hardening | Full reference quest and release gate |

Quest development must not wait for a Core implementation when a fake port can
express the contract. Real integration occurs at each gate after both sides pass
their own tests.

## 2. Shared-file policy

Files requiring coordination:

    settings.gradle.kts
    root build.gradle.kts
    mmorpg-api
    mmorpg-content shared type registration
    mmorpg-storage shared lifecycle
    mmorpg-paper bootstrap/plugin.yml

Preferred procedure:

1. Propose the shared contract in documentation/API.
2. Add backward-compatible API where possible.
3. Merge the small shared change.
4. Each workstream consumes it independently.

Large feature implementations do not belong in shared bootstrap files.

## 3. Branch policy

Recommended branch names:

    codex/quest-system
    codex/core-mmo

Both branches start from the same reviewed foundation commit. Rebase/merge the
shared API contract before integration work. Do not copy implementation classes
between branches.

## 4. Integration test matrix

| Test | Fake Core | Real Core | Paper |
|---|---:|---:|---:|
| Quest compiler | Required | Not required | Not required |
| Quest state machine | Required | Contract suite | Not required |
| Objective reducers | Required | Event contract suite | Not required |
| Rewards/idempotency | Required | Required | Smoke |
| Dialogue graph | Required | Not required | Renderer smoke |
| NPC/region | Fake location/actor | Event adapter | Required |
| Cutscene timeline | Virtual ports | Actor integration | Required |
| Full reference quest | Useful | Required | Required |

The same contract test suite should run against fake and real implementations of
QuestGamePort.

## 5. Merge gates

An integration gate passes when:

- Both workstreams build independently.
- Public API changes are documented.
- Contract tests pass against fake and real adapter.
- No implementation dependency crosses ownership boundary.
- Database migrations have unique ordered versions and were tested together.
- Content schemas resolve cross-system IDs against one catalog snapshot.
- gradlew clean test shadowJar passes.

## 6. Reference quest used for final integration

Working ID:

    branz:broken_seal

Flow:

1. Speak to Elaria.
2. Choose whether to ask for context or accept immediately.
3. Enter the Old Ruins region.
4. Inspect the Broken Altar object.
5. Play a skippable Guardian awakening cutscene.
6. Defeat the Seal Guardian.
7. Collect or possess the configured fragment.
8. Return to Elaria.
9. Consume the fragment if required.
10. Deliver idempotent currency, item, and mastery rewards.
11. Unlock the next region/story flag.

This quest validates dialogue, choices, regions, interactions, cutscene, mob/boss
events, items, rewards, history, persistence, restart recovery, and administration.

## 7. Definition of final per system

A system is final when:

- Its public contract and invariants are stable.
- Persistence and migration are implemented.
- Error/recovery/admin paths exist.
- Tests meet the phase specification.
- Performance is measured.
- Documentation matches implementation.
- It can be consumed by the other workstream without importing implementation.

Final does not mean balance values or content quantities can never change.
Those remain data-driven.
