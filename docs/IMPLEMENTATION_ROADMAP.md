# Branz MMORPG — Parallel Implementation Roadmap

Status: Authoritative workstream order  
Related specifications:

- DEVELOPMENT_OWNERSHIP_AND_CONTRACTS.md
- CORE_MMO_SPECIFICATION.md
- SURVIVAL_SKILL_MASTERY_SPECIFICATION.md
- QUEST_DIALOGUE_CUTSCENE_SPECIFICATION.md
- PHASE_1_FOUNDATION.md

## 1. Workstream order

Core and Quest proceed in parallel after agreeing on API contracts.

| Milestone | Core MMO workstream | Quest workstream | Integration gate |
|---|---|---|---|
| M0 | C0 Foundation adoption | Q0 Module/contracts | Dependency and ownership checks |
| M1 | C1 Player session, C2 Attributes; Survival Skill profile contract | Q1 Compiler, Q2 State machine | Player identity, lifecycle, and immutable skill snapshot contract |
| M2 | C3 Status, C4 Combat | Q3 Objectives, Q4 Conditions/actions | Domain-event envelopes and fake adapters |
| M3 | C5 Skills, C6 Combat Mastery; S1 Survival progression engine and tree | Q5 Persistence/migration | Mastery query/event, tree snapshot, and operation ID |
| M4 | C7 Items/loot | Q6 Dialogue engine, Q7 Renderer/history | Item query/reward and player audience |
| M5 | C8 Gathering/crafting/economy; S2 Mining XP and anti-farm | Q8 NPC/world integration | Gathering, Survival XP, craft, item, region, and interaction events |
| M6 | C9 Mob AI, C10 Encounter/boss | Q9 Tracker/journal, Q10 Cutscene | Mob/boss events and actor/camera adapters |
| M7 | C11 Party/trade | Q11 Party/private scenes | Stable party snapshot contract |
| M8 | C12 Operations, C13 Hardening; S3 Survival UI, admin, telemetry, and smoke tests | Q12–Q15 Tools/editor/hardening | Reference scenarios and final release gate |

Quest development must not wait for a Core implementation when a fake port can
express the contract. Real integration occurs at each gate after both sides pass
their own tests.

## 1.1 Survival Skill Mastery delivery order

Survival Skill Mastery belongs to the Core workstream and is delivered in four
increments:

| Increment | Milestone | Deliverables | Exit criteria |
|---|---|---|---|
| S0 Profile contract | M1 | Skill ID, immutable progress snapshot, level/XP/point values, persistence model | A player session loads skill progress without Paper types |
| S1 Progression engine | M3 | XP formula, level curve, points, mastery-tree DAG, node purchase and respec transactions | Pure Java formula, tree-validation, and idempotency tests pass |
| S2 Mining integration | M5 | Pickaxe tags, block-source definitions, rarity XP, origin tracking, anti-farm decay, committed events | Natural stone grants configured 1 XP; rare ore grants more; placed or cancelled blocks grant none |
| S3 Operations and release | M8 | Player UI, admin inspect/repair/reset, audit, telemetry, Paper adapter, performance and abuse tests | Survival Skill acceptance criteria and Paper smoke tests pass |

Mining is required for the initial release. Woodcutting, Excavation, Foraging,
and Fishing may reuse the engine only after their source definitions,
anti-exploit policies, and tests pass the same gate.

Dependencies:

    S0 Profile contract
      -> S1 Progression engine
      -> S2 Mining integration
      -> S3 Operations and release

S1 may use immutable fake tools and sources before C7 Items is complete. S2
cannot pass integration until authoritative item identity, gathering-source
origin, and transactional reward services are available.

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

Survival Skill ownership follows the module boundaries:

    mmorpg-api        immutable progress/tree snapshots, queries, and events
    mmorpg-content    gathering-source and mastery-tree definitions
    mmorpg-storage    progress, node rank, operation, audit, and outbox records
    mmorpg-core       formulas, progression, validation, and anti-farm policy
    mmorpg-paper      block/tool adapters, player UI, and commands

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
| Survival profile/query | Useful | Required | Not required |
| Survival XP/level/tree | Fake source/tool | Required | Not required |
| Mining block eligibility | Fake block origin | Required | Required |
| Survival anti-farm/idempotency | Synthetic actions | Required | Smoke |
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
- Survival XP and tree mutations use idempotent operation IDs.
- Gathering sources prove their origin before valuable XP or bonus yield.
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

## 6.1 Survival Skill reference scenario

Survival progression uses a separate scenario so ordinary Mining does not depend
on quest completion:

1. Join with an ACTIVE player session and a valid pickaxe.
2. Break natural stone and receive exactly 1 configured Mining XP.
3. Break a configured rare ore and receive its larger XP value.
4. Place and re-break the same ore type and receive no rare-tier XP.
5. Reach a configured milestone and receive its skill point exactly once.
6. Unlock `branz:mining_stoneworker` and observe its bounded effect.
7. Retry the same operation IDs and verify no XP, point, or rank duplication.
8. Reconnect and verify level, XP, points, and node ranks are preserved.

## 7. Definition of final per system

A system is final when:

- Its public contract and invariants are stable.
- Persistence and migration are implemented.
- Error/recovery/admin paths exist.
- Tests meet the phase specification.
- Performance is measured.
- Documentation matches implementation.
- It can be consumed by the other workstream without importing implementation.

For Survival Skill Mastery, final additionally means:

- Natural and registered source origin is authoritative and tested.
- XP, levels, points, node purchases, and respecs are idempotent and audited.
- Mastery trees reject cycles, broken prerequisites, and unbounded effects.
- Placed, restored, cancelled, duplicated, and rate-limited actions cannot
  produce unintended progression.
- Mining runs through Paper while formulas and tree rules remain pure Java.

Final does not mean balance values or content quantities can never change.
Those remain data-driven.
