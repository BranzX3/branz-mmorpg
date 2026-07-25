# Branz MMORPG — Development Ownership and Integration Contracts

Status: Locked baseline  
Runtime baseline: Paper 26.2, Java 25, Gradle 9.1  
Architecture rule: Core MMO and Quest are independently testable subsystems connected through stable API contracts.

## 1. Ownership

| Area | Owner | Primary modules |
|---|---|---|
| Shared foundation | Coordinated | mmorpg-api, mmorpg-content, mmorpg-storage, root Gradle |
| Core MMO | Core developer | mmorpg-core, future MMO-specific modules |
| Quest/content tooling | Quest developer | mmorpg-quest-api, mmorpg-quest-core, mmorpg-quest-storage, mmorpg-quest-paper |
| Paper bootstrap | Coordinated integration surface | mmorpg-paper |

Neither workstream may import implementation classes owned by the other.
Cross-system calls must go through mmorpg-api or mmorpg-quest-api.

## 2. Package boundaries

Core-owned packages:

    com.branz.mmorpg.api.player
    com.branz.mmorpg.api.stat
    com.branz.mmorpg.api.combat
    com.branz.mmorpg.api.skill
    com.branz.mmorpg.api.survival
    com.branz.mmorpg.api.item
    com.branz.mmorpg.api.mob
    com.branz.mmorpg.core

Quest-owned packages:

    com.branz.mmorpg.quest.api
    com.branz.mmorpg.quest.core
    com.branz.mmorpg.quest.storage
    com.branz.mmorpg.paper.quest

Shared packages require coordination:

    com.branz.mmorpg.api.content
    com.branz.mmorpg.content
    com.branz.mmorpg.storage
    com.branz.mmorpg.paper

## 3. Stable identity rules

- Players are identified by UUID, never display name.
- Every content object uses ContentId in namespace:value format.
- Runtime entity UUIDs are not content IDs.
- Unique equipment uses a persistent item-instance UUID in addition to its definition ID.
- Quest definitions, stages, nodes, objectives, choices, actions, mobs, skills, regions, and recipes have stable IDs.
- Renaming a shipped ID requires an explicit alias or migration.

## 4. Core services consumed by Quest

Quest depends on narrow capabilities rather than the complete Core implementation.

    public interface QuestGamePort {
        PlayerSnapshot player(UUID playerId);
        SurvivalSkillSnapshot survivalSkill(UUID playerId, ContentId skillId);
        boolean hasItem(UUID playerId, ContentId itemId, long amount);
        CompletionStage<MutationResult> takeItem(
                UUID playerId, ContentId itemId, long amount, OperationId operationId);
        CompletionStage<MutationResult> grantItem(
                UUID playerId, ContentId itemId, long amount, OperationId operationId);
        CompletionStage<MutationResult> grantCurrency(
                UUID playerId, ContentId currencyId, long amount, OperationId operationId);
        CompletionStage<MutationResult> grantCombatMasteryXp(
                UUID playerId, ContentId masteryId, long amount, OperationId operationId);
        CompletionStage<MutationResult> grantSurvivalSkillXp(
                UUID playerId, ContentId skillId, long amount, OperationId operationId);
        boolean hasUnlock(UUID playerId, ContentId unlockId);
    }

All reward or cost mutations require an idempotent OperationId. Retrying the
same operation must return the original result without applying it twice.
Snapshot and query methods read already-loaded session state and must not perform
SQL. Mutation methods are asynchronous because their completion may require a
database transaction. Paper adapters must re-enter the owning scheduler and
verify the player session token before touching Bukkit state.

Combat mastery IDs and Survival Skill IDs occupy distinct typed domains.
`branz:sword` cannot be passed where `branz:mining` is required merely because
both values use the `ContentId` representation.

## 5. Domain events published by Core

Core publishes immutable events after the authoritative transaction succeeds:

Every persistent domain event uses a common envelope:

    eventId
    operationId
    eventType
    eventVersion
    occurredAt
    aggregateType
    aggregateId
    aggregateSequence
    contentRevision
    payload

`operationId` may be absent only for a genuinely observational event that did
not result from a mutation. Event IDs are globally unique. Aggregate sequence is
monotonic within one aggregate and is used for ordering checks, not global
ordering.

| Event | Required fields |
|---|---|
| MobKilled | event ID, killer/contributors, mob definition ID, runtime entity ID, location, timestamp |
| BossDefeated | event ID, encounter ID, boss ID, eligible contributors, timestamp |
| ItemAcquired | event ID, player, item ID, amount, source, timestamp |
| ItemRemoved | event ID, player, item ID, amount, reason, timestamp |
| SkillUsed | event ID, player, skill ID, valid effect result, timestamp |
| CraftCompleted | event ID, player, recipe ID, output IDs, timestamp |
| CombatMasteryXpGranted | event ID, operation ID, player, mastery ID/kind, source, awarded XP |
| CombatMasteryLevelChanged | event ID, operation ID, player, mastery ID/kind, old/new level, total XP |
| CombatMasteryNodeUnlocked | event ID, operation ID, player, mastery ID, node ID, rank, points remaining |
| ActiveBuildChanged | event ID, operation ID, player, previous/new build revision, weapon and skill IDs |
| SurvivalXpGranted | event ID, operation ID, player, skill ID, source ID, awarded XP, timestamp |
| SurvivalSkillLevelChanged | event ID, operation ID, player, skill ID, old/new level, total XP |
| SurvivalSkillNodeUnlocked | event ID, operation ID, player, skill ID, node ID, rank, points remaining |
| PlayerDied | event ID, player, cause category, killer ID if present |
| RegionEntered | event ID, player, region ID, from/to location |
| WorldObjectInteracted | event ID, player, object ID, hand, timestamp |

Events are at-least-once compatible. Consumers deduplicate by event ID.
An outbox publisher preserves aggregate order. Consumers must tolerate duplicate
delivery and must not assume ordering between different aggregates.
Core must not publish progress events for cancelled, rolled-back, synthetic,
or administratively previewed actions unless explicitly marked.

## 6. Quest events published to Core/UI

| Event | Purpose |
|---|---|
| QuestStarted | tracking, analytics, NPC state |
| QuestStageChanged | HUD and world presentation |
| QuestObjectiveProgressed | tracker update |
| QuestReadyToTurnIn | NPC marker and navigation |
| QuestCompleted | unlocks and analytics |
| QuestAbandoned | cleanup |
| QuestRewardFailed | support alert and recovery |
| DialogueStarted/Ended | movement/UI coordination |
| CutsceneStarted/Ended | combat and visibility coordination |

## 7. Threading contract

- Paper entity, inventory, UI, and world mutation runs on the owning server thread.
- SQL and file I/O never run on a Paper tick thread.
- Domain calculations remain platform-independent and synchronous.
- API methods that may persist state return an asynchronous result and never
  hide blocking I/O behind a synchronous interface.
- Async completion must re-enter the correct Paper scheduler before touching Bukkit objects.
- Event payloads must not contain mutable Bukkit objects.
- Player logout invalidates the runtime session token; late async callbacks must verify it.

## 8. Transaction contract

- Database transactions are owned by the service performing the mutation.
- No transaction remains open while waiting for player input or Paper scheduling.
- Quest reward delivery uses an outbox/pending-operation record.
- Inventory delivery that cannot complete routes to an authoritative mailbox or pending claim; it is never silently dropped.
- Currency, item consumption, crafting, trade, and quest rewards are audited.
- Combat Mastery and Survival Skill mutations are audited and stored with their
  operation result so retries return the original outcome.

## 9. Content reload contract

- YAML is parsed and validated into an immutable candidate snapshot.
- All cross-references are resolved before activation.
- Activation is an atomic snapshot swap.
- Existing sessions retain the snapshot revision with which they began unless their subsystem documents safe live migration.
- Invalid content never replaces the active snapshot.
- Reload does not migrate persistent player data automatically.

## 10. Shared definition of done

Every phase must include:

1. Written API and invariants.
2. Typed configuration/content schema.
3. Validation and actionable diagnostics.
4. Persistence and migration where applicable.
5. Failure, retry, logout, reload, and shutdown behavior.
6. Admin inspect/repair tools.
7. Unit tests and relevant integration tests.
8. Performance budget.
9. Updated documentation.
10. Successful gradlew clean test shadowJar.

Engine contracts may become final. Balance values and content quantities remain
data-driven and may be tuned without breaking API compatibility.
