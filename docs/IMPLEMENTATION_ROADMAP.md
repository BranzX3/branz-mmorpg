# Branz MMORPG — Parallel Implementation Roadmap

Status: Authoritative workstream order  
Related specifications:

- DEVELOPMENT_OWNERSHIP_AND_CONTRACTS.md
- CORE_MMO_SPECIFICATION.md
- COMBAT_SKILL_INPUT_SPECIFICATION.md
- PERMANENT_CHARACTER_CLASS_SPECIFICATION.md
- CLASS_COMPASS_AND_SKILL_TREE_UI_SPECIFICATION.md
- COMBAT_MASTERY_AND_CHARACTER_BUILD_SPECIFICATION.md
- SURVIVAL_SKILL_MASTERY_SPECIFICATION.md
- QUEST_DIALOGUE_CUTSCENE_SPECIFICATION.md
- PHASE_1_FOUNDATION.md

## 1. Workstream order

Core and Quest proceed in parallel after agreeing on API contracts.

| Milestone | Core MMO workstream | Quest workstream | Integration gate |
|---|---|---|---|
| M0 | C0 Foundation adoption | Q0 Module/contracts | Dependency and ownership checks |
| M1 | C1 Player session, permanent-class domain/selection transaction, C2 Attributes; Survival Skill profile contract | Q1 Compiler, Q2 State machine | Player identity, class snapshot/selection contract, lifecycle, and immutable skill snapshot contract |
| M2 | C3 Status, C4 Combat, B0 input contracts; class restrictions and starter definitions | Q3 Objectives, Q4 Conditions/actions | Combat/domain-event envelopes, class query/condition contracts, and fake adapters |
| M3 | C5 Combat input/combos and Class Skill Tree domain, C6 Character Build and Combat Mastery; S1 Survival progression engine and tree | Q5 Persistence/migration | Input arbitration, Skill Point transaction, typed class/mastery query/event, class/build/tree snapshot, and operation ID |
| M4 | C7 Items/loot; slot-9 compass inventory integration and starter delivery | Q6 Dialogue engine, Q7 Renderer/history | Lossless slot reconciliation, item query/reward, pending delivery, and player audience |
| M5 | C8 Gathering/crafting/economy; S2 Mining XP and anti-farm | Q8 NPC/world integration | Gathering, Survival XP, craft, item, region, and interaction events |
| M6 | C9 Mob AI, C10 Encounter/boss | Q9 Tracker/journal, Q10 Cutscene | Mob/boss events and actor/camera adapters |
| M7 | C11 Party/trade | Q11 Party/private scenes | Stable party snapshot contract |
| M8 | C12 Operations, C13 Hardening; K4/B5/S3 UI, admin, telemetry, and smoke tests | Q12–Q15 Tools/editor/hardening | Reference scenarios and final release gate |

Quest development must not wait for a Core implementation when a fake port can
express the contract. Real integration occurs at each gate after both sides pass
their own tests.

## 1.1 Permanent Class and Compass delivery order

Permanent classes belong to Core; inventory presentation belongs to Paper and
cannot be finalized before authoritative item delivery exists.

| Increment | Milestone | Deliverables | Exit criteria |
|---|---|---|---|
| K0 Class contract | M1 | Typed class IDs/snapshots, profile fields, selection state, operation/result contract | A profile loads with exactly one valid selected class or an explicit unselected state |
| K1 Selection domain | M1 | Permanent selection transaction, starter grant plan, class definitions, audit/outbox events | Selection retry returns the original result and cannot duplicate starter grants |
| K2 Class progression | M3 | Class XP/levels, Skill Points, three class trees, node purchase/respec, class-skill bindings | Pure Java tree, restriction, migration, and idempotency tests pass |
| K3 Compass integration | M4 | Protected slot-9 compass, selection/tree inventories, token validation, lossless slot relocation, pending delivery | New and selected profiles reconcile correctly without item loss or duplicate tokens |
| K4 Class hardening | M8 | Repair commands, UI accessibility, telemetry, reconnect/reload/abuse/performance suites | Full class/compass acceptance criteria and Paper smoke tests pass |

Dependencies:

    K0 Class contract
      -> K1 Selection domain
      -> K2 Class progression
      -> K3 Compass integration
      -> K4 Class hardening

K1 produces a starter grant plan but does not assume direct Bukkit inventory
mutation. K3 consumes C7 authoritative inventory and pending-delivery services
before reserving slot 9. Until K3, selection is exercised through a fake or
administrative adapter in integration tests.

## 1.2 Combat Input, Skills, and Mastery delivery order

| Increment | Milestone | Deliverables | Exit criteria |
|---|---|---|---|
| B0 Input contracts | M2 | Immutable input intents, skill slots, targeting context, cast/resource contracts | Contracts contain no Paper objects and deterministic fakes compile |
| B1 Combat pipeline | M2 | Eligibility, target validation, hit/damage, resources, statuses, contribution envelope | Pure Java golden tests cover invalid, cancelled, duplicate, and lethal results |
| B2 Input/skill engine | M3 | LMB/RMB/F/Shift arbitration, combos, charge/channel, cooldowns, bounded buffer | Paper input smoke tests and deterministic combo/cast tests pass |
| B3 Class and weapon progression | M3 | Class-skill bindings, weapon family/type mastery, XP splits, trees, loadout snapshots | Cross-class restrictions and mastery idempotency tests pass |
| B4 Playable class kits | M4 | Warrior/Broadsword, Mage/Fire Staff, Rogue/Daggers starter kits integrated with authoritative items | Every permanent class can complete the combat reference scenario |
| B5 Combat hardening | M8 | Admin repair, telemetry, soak/fault tests, PvE coefficients and balance measurements | Combat, input, class, mastery, and loadout release gates pass |

An input is never a combat result. B2 may create intents only; B1 remains the
authoritative source of damage, resource payment, contribution, and progression.

## 1.3 Survival Skill Mastery delivery order

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

Permanent Class, Combat, and Compass ownership follows the same dependency rule:

    mmorpg-api        class/build/mastery/input snapshots, results, and events
    mmorpg-content    class, skill, combo, tree, weapon, and starter definitions
    mmorpg-storage    class/progression/node/loadout/operation/outbox records
    mmorpg-core       selection, tree, skill, combat, mastery, and build rules
    mmorpg-paper      compass/inventory UI, input listeners, targets, and feedback

Paper may request a mutation through API but never decides class compatibility,
available points, damage, cooldown readiness, mastery XP, or item ownership.

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
| Permanent class selection | Fake starter delivery | Required | Required |
| Class selection retry/starter grant | Failure-injected fake | Required | Smoke |
| Class tree purchase/respec/migration | Required | Required | Renderer smoke |
| Slot-9 compass reconciliation | Fake inventory model | Contract suite | Required |
| Full/valuable slot-9 relocation | Fake pending delivery | Required | Required |
| Combat formulas/contribution | Required | Golden suite | Smoke |
| LMB/RMB/F/Shift arbitration | Intent fake | Contract suite | Required |
| Combo/charge/cooldown | Virtual clock | Required | Required |
| Cross-class item/skill rejection | Required | Required | Smoke |
| Combat Mastery XP/idempotency | Synthetic contribution | Required | Smoke |
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
- Permanent class selection and starter delivery are idempotent and audited.
- Every launch class has a complete valid starter weapon and skill kit.
- Slot-9 reconciliation proves that no normal item is deleted or duplicated.
- Class-tree point spending and respec cannot partially apply.
- Combat inputs cannot apply vanilla and MMO damage to the same target.
- Class, weapon, resource, skill, and mastery compatibility validates together.
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

## 6.2 Permanent Class onboarding reference scenario

Run once for Warrior, Mage, and Rogue:

1. Join with a new profile and wait for the session to become ACTIVE.
2. Preserve or safely relocate any normal item already in hotbar slot 9.
3. Receive exactly one valid Class Selection Compass in slot 9.
4. Preview a class, cancel, and verify that no persistent mutation occurs.
5. Confirm the selected class with one operation ID.
6. Verify permanent class, starter grants, audit, and outbox commit exactly once.
7. Verify slot 9 becomes that class's Skill Tree Compass.
8. Retry/reconnect and verify no duplicate compass, starter item, XP, or point.
9. Attempt a cross-class node, skill, and weapon and verify all fail closed.
10. Spend a valid point, reopen the UI, and verify the rank and modifier once.

## 6.3 Combat and Class Skill reference scenario

Run the class-compatible kit for Warrior/Broadsword, Mage/Fire Staff, and
Rogue/Daggers:

1. LMB produces one basic-attack intent and one authoritative damage result.
2. RMB and F resolve the configured weapon actions without duplicate vanilla behavior.
3. Shift + LMB/RMB/F resolve the selected class's two active slots and ultimate.
4. Invalid target, miss, immunity, silence, stun, and insufficient resource consume nothing.
5. A valid combo resolves inside its server-time window and expires outside it.
6. Contribution grants typed mastery/class progression once.
7. Item movement, death, reconnect, and loadout swap cannot reset cooldowns.
8. Another class cannot equip or activate the tested restricted skill/weapon.

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

For Permanent Class, Combat, and Compass systems, final additionally means:

- All three permanent classes complete onboarding and combat reference scenarios.
- Starter grants, class selection, point spending, and respec are idempotent.
- Slot 9 remains lossless under full inventory, death, reconnect, and tampering.
- Class/weapon/skill restrictions are enforced by Core rather than UI metadata.
- Input arbitration is measured and cannot double-apply vanilla/MMO actions.
- Tree, build, combat, and mastery calculations remain deterministic pure Java.

Final does not mean balance values or content quantities can never change.
Those remain data-driven.

## 8. Launch MVP boundary

The first playable release is intentionally smaller than the complete C0–C13
and Q0–Q15 vision.

### Required for launch

- C0–C2 foundation, player sessions, attributes, and resources
- Permanent Warrior, Mage, or Rogue selection with starter loadout validation
- C3–C4 initial statuses and deterministic basic combat
- C5 input arbitration, combos, cooldown/resources, and class/weapon skill execution
- One validated Class Skill Tree per Warrior, Mage, and Rogue with starter nodes
- Protected slot-9 selection/tree compass with lossless inventory reconciliation
- C6 one complete starter combat kit per class: Warrior/Broadsword,
  Mage/Fire Staff, and Rogue/Daggers
- One Combat Mastery family/type path and valid loadout per launch class
- C7 authoritative item ownership, equipment, basic loot, and pending delivery
- C8 Mining Survival Skill, basic gathering, one crafting profession, and Coins
- C9 one mob family with vanilla presentation fallback
- C10 one three-phase reference boss or encounter
- C12 essential HUD, status, inspect, grant, audit, and repair operations
- Q0–Q9 quest compiler/runtime, objectives, persistence, dialogue, NPC/world
  integration, tracker, and journal
- One complete reference quest and the Survival Skill reference scenario
- Required C13/Q15 correctness, recovery, migration, smoke, and performance gates

### Post-launch

- Additional weapons, Survival Skills, professions, mobs, and encounters
- Advanced cutscene camera and private actor features
- Direct trade after authoritative item operations are proven stable
- Expanded accessibility, analytics, authoring, and QA tools

### Optional/deferred

- Visual local quest editor
- Player market
- Cross-server synchronization
- Mandatory ModelEngine or packet-library presentation
- Branz Idle bridge
- PvP ranking, seasons, guild, and territory systems

An optional or post-launch feature cannot block the launch build unless a
required feature has taken a hard dependency on it. Such a dependency must be
removed or explicitly approved as a roadmap change.

## 9. Current implementation baseline

As of the roadmap review on 2026-07-26:

| Area | Current repository state | Roadmap consequence |
|---|---|---|
| Gradle wrapper | Gradle 9.1 wrapper restored; `clean test shadowJar` passes | I0 complete; retain the clean-build gate |
| mmorpg-api | Foundation content/lifecycle/health/IDs plus immutable Player Profile/Session contracts | Remaining C0 scheduler/transaction/error and K0/B0 contracts remain |
| mmorpg-content | YAML material loading, validation, immutable atomic snapshots | Class, skill, tree, combo, item, mob, quest type registration remains |
| mmorpg-storage | Hikari/Flyway plus V2 player-profile migration and async optimistic-lock repository | MySQL integration/fault tests and class/mastery/tree/item/outbox storage remain |
| mmorpg-core | Lifecycle container, managed foundation services, typed IDs, and tokenized Player Session manager | I1/C0 and I2/C1 are in progress; durable save recovery and remaining foundation ports remain |
| mmorpg-paper | Core bootstrap plus database-enabled async join/quit session adapter and health/status reporting | Compass UI, input, combat, inventory, and scheduler adapters remain |
| Tests | Foundation plus lifecycle and Player Session duplicate/failure/late-callback/retry-state tests | MySQL integration, Class/Combat/Survival/Quest, and Paper behavior suites remain |
| Local Paper | Paper 26.2 starts on localhost:25565 with Core READY and database disabled | Foundation smoke gate passes; persistent gameplay is intentionally offline |

Documentation completion does not mark an implementation milestone complete.
Each increment remains pending until its code, migration, tests, operations, and
measured acceptance gate exist.

## 10. Immediate implementation critical path

Work proceeds in this order unless a fake port allows explicitly parallel pure
Java development:

Current execution status: **I0 complete; I1 and I2 in progress; I3–I10 pending.**

| Order | Implementation package | Required output | Gate before next dependent package |
|---:|---|---|---|
| I0 | Restore build gate | Valid Gradle wrapper; `clean test shadowJar` starts from a clean checkout | Foundation build succeeds |
| I1 | C0 foundation | Lifecycle, service container, operation/event IDs, async/scheduler/transaction ports, health API | Repeated start/stop and failure-state tests pass |
| I2 | C1 player session/storage | Profile migration/repository, tokenized session lifecycle, async load/save, recovery | Concurrent login/logout/failure tests pass |
| I3 | K0/K1 permanent class domain | Typed class definitions/snapshots, unselected state, selection transaction, starter grant plan | Retry/idempotency and content-validation tests pass |
| I4 | C2 attributes/resources | Deterministic modifier engine, HP/Mana/Stamina/Rage/Energy policies | Formula/property and duplicate-modifier tests pass |
| I5 | C3/C4 combat foundation | Status scheduler, target validation, hit/damage/resource/contribution pipeline | Pure Java combat golden tests pass |
| I6 | B0–B3 and K2 | Input/skill/combo engine, three Class Trees, Skill Points, Combat Mastery, builds/loadouts | Class restriction, tree, cast, combo, and mastery tests pass |
| I7 | C7, K3, B4 | Authoritative items/inventory, pending delivery, slot-9 compass UI, three playable starter kits | Lossless compass and per-class combat scenarios pass |
| I8 | C8 and S0–S2 | Mining origin/XP/tree, gathering, basic crafting and Coins | Survival reference scenario and fault tests pass |
| I9 | C9/C10 and Quest integration | Mob family, boss, Q0–Q9 runtime/UI, reference quest | Full reference quest passes with real Core |
| I10 | C12/C13, K4/B5/S3 and Q15 | Admin/repair, telemetry, migrations, soak, backup/restore, release tests | Launch release criteria pass |

### 10.1 Safe parallel work

After I1 contracts stabilize, the following may proceed in parallel using fake
ports and immutable fixtures:

- K0 class content schemas and selection reducer
- B0 input values, combo resolver, and virtual-clock fixtures
- S0 Survival progress values and level/tree formulas
- Q0/Q1 Quest modules and compiler
- Storage migration design with non-overlapping Flyway versions

Parallel work must not edit shared API/bootstrap/schema files without the
shared-file procedure in section 2.

### 10.2 Work that must not be pulled forward

- Slot-9 compass relocation must not ship before C7 pending delivery prevents
  loss of the player's existing item.
- Paper input listeners must not apply damage before the pure Java C4 combat
  pipeline owns validation and mutation.
- Class selection must not deliver starter items outside the authoritative item
  transaction.
- Survival rare-ore XP must not ship before block-origin persistence fails
  closed for unknown origin.
- Quest economic rewards must not use real Core before operation/outbox contracts
  pass their fake and real adapter suites.
