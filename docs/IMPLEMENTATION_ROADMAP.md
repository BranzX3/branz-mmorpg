# Branz MMORPG — Parallel Implementation Roadmap

Status: Authoritative workstream order  
Related specifications:

- DEVELOPMENT_OWNERSHIP_AND_CONTRACTS.md
- CORE_MMO_SPECIFICATION.md
- SURVIVAL_SKILL_MASTERY_SPECIFICATION.md (Life Skill Mastery)
- QUEST_DIALOGUE_CUTSCENE_SPECIFICATION.md
- PHASE_1_FOUNDATION.md

## 1. Workstream order

Core and Quest proceed in parallel after agreeing on API contracts.

| Milestone | Core MMO workstream | Quest workstream | Integration gate |
|---|---|---|---|
| M0 | C0 Foundation adoption | Q0 Module/contracts | Dependency and ownership checks |
| M1 | C1 Player session, C2 Attributes; Life Skill profile contract | Q1 Compiler, Q2 State machine | Player identity, lifecycle, and immutable skill snapshot contract |
| M2 | C3 Status, C4 Combat | Q3 Objectives, Q4 Conditions/actions | Domain-event envelopes and fake adapters |
| M3 | C5 Skills, C6 Combat Mastery; S1 Life Skill progression engine and tree | Q5 Persistence/migration | Mastery query/event, tree snapshot, and operation ID |
| M4 | C7 Items/loot | Q6 Dialogue engine, Q7 Renderer/history | Item query/reward and player audience |
| M5 | C8 Gathering/crafting/economy; S2 Mining nodes | Q8 NPC/world integration | Gathering, Life Skill XP, craft, item, region, and interaction events |
| M6 | C9 Mob AI, C10 Encounter/boss | Q9 Tracker/journal, Q10 Cutscene | Mob/boss events and actor/camera adapters |
| M7 | C11 Party/trade | Q11 Party/private scenes | Stable party snapshot contract |
| M8 | C12 Operations, C13 Hardening; S3 Life Skill UI, admin, telemetry, and smoke tests | Q12–Q15 Tools/editor/hardening | Reference scenarios and final release gate |

Quest development must not wait for a Core implementation when a fake port can
express the contract. Real integration occurs at each gate after both sides pass
their own tests.

## 1.1 Life Skill Mastery delivery order

Life Skill Mastery belongs to the Core workstream and is delivered in four
increments:

| Increment | Milestone | Deliverables | Exit criteria |
|---|---|---|---|
| S0 Profile contract | M1 | Skill ID, immutable progress snapshot, level/XP/point values, persistence model | A player session loads skill progress without Paper types |
| S1 Progression engine | M3 | XP formula, level curve, points, mastery-tree DAG, node purchase and respec transactions | Pure Java formula, tree-validation, and idempotency tests pass |
| S2 Mining nodes | M5 | Node definitions, node instances and placement commands, reservation/contest, depletion and respawn, yields, committed events | Ordinary blocks grant zero; a registered node grants its configured XP; contested harvest resolves to exactly one winner |
| S3 Operations and release | M8 | Player UI, admin inspect/repair/reset, node repair tooling, audit, telemetry, Paper adapter, performance and abuse tests | Life Skill acceptance criteria and Paper smoke tests pass |

Mining is required for the initial release. Woodcutting, Excavation, Foraging,
and Fishing may reuse the engine only after their node definitions,
anti-exploit policies, and tests pass the same gate.

Dependencies:

    S0 Profile contract
      -> S1 Progression engine
      -> S2 Mining nodes
      -> S3 Operations and release

S1 may use immutable fake tools and nodes before C7 Items is complete. S2 cannot
pass integration until authoritative item identity and transactional reward
services are available, because node yields are real items.

S2 no longer depends on per-block origin tracking. Under the node model a
player-placed block is simply not a registered node instance, so it grants
nothing by construction rather than by detection.

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

Life Skill ownership follows the module boundaries:

    mmorpg-api        immutable progress/tree snapshots, queries, and events
    mmorpg-content    gathering-node and mastery-tree definitions
    mmorpg-storage    progress, node rank, node instances, operation, audit, and outbox records
    mmorpg-core       formulas, progression, reservation rules, validation, and anti-farm policy
    mmorpg-paper      interaction/tool adapters, node presentation, player UI, and commands

Node definitions are content; node instances are world state owned by storage
and mutated only through admin commands. A content reload never moves, deletes,
or respawns a placed node.

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
| Life Skill profile/query | Useful | Required | Not required |
| Life Skill XP/level/tree | Fake node/tool | Required | Not required |
| Node eligibility and yields | Fake node instance | Required | Required |
| Node reservation and contest | Simulated concurrency | Required | Required |
| Node depletion/respawn/restart | Fake clock | Required | Smoke |
| Life Skill anti-farm/idempotency | Synthetic actions | Required | Smoke |
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
- Life Skill XP and tree mutations use idempotent operation IDs.
- XP and yields come only from registered node instances.
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

## 6.1 Life Skill reference scenario

Life Skill progression uses a separate scenario so Mining does not depend on
quest completion:

1. Join with an ACTIVE player session and a valid pickaxe.
2. Break ordinary stone in the world and receive no XP at all.
3. Harvest a placed `branz:stone_deposit` node and receive its configured XP
   and yields.
4. Harvest a placed `branz:iron_vein` node and receive its larger XP value.
5. Attempt to harvest the same node again before respawn and be refused.
6. Have a second player interact with the same node in the same tick; exactly
   one wins the reservation and the other loses nothing.
7. Interrupt a harvest mid-channel and verify no XP, no yields, and the node
   returns to AVAILABLE.
8. Reach a configured milestone and receive its skill point exactly once.
9. Unlock `branz:mining_stoneworker` and observe its bounded effect.
10. Retry the same operation IDs and verify no XP, yield, point, or rank
    duplication.
11. Restart the server and verify respawn timers survived and progress is intact.

## 7. Definition of final per system

A system is final when:

- Its public contract and invariants are stable.
- Persistence and migration are implemented.
- Error/recovery/admin paths exist.
- Tests meet the phase specification.
- Performance is measured.
- Documentation matches implementation.
- It can be consumed by the other workstream without importing implementation.

For Life Skill Mastery, final additionally means:

- Only registered node instances can grant XP; every other world interaction
  grants nothing.
- Node reservation, depletion, and respawn are authoritative, contested safely,
  and survive restart.
- XP, levels, points, yields, node purchases, and respecs are idempotent and
  audited.
- Mastery trees reject cycles, broken prerequisites, and unbounded effects.
- Interrupted, duplicated, orphaned, and rate-limited actions cannot produce
  unintended progression.
- Mining runs through Paper while formulas, reservation rules, and tree rules
  remain pure Java.

Final does not mean balance values or content quantities can never change.
Those remain data-driven.
