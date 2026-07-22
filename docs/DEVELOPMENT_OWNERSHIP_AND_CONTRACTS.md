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
        boolean hasItem(UUID playerId, ContentId itemId, long amount);
        MutationResult takeItem(UUID playerId, ContentId itemId, long amount, OperationId operationId);
        MutationResult grantItem(UUID playerId, ContentId itemId, long amount, OperationId operationId);
        MutationResult grantCurrency(UUID playerId, ContentId currencyId, long amount, OperationId operationId);
        MutationResult grantMasteryXp(UUID playerId, ContentId masteryId, long amount, OperationId operationId);
        boolean hasUnlock(UUID playerId, ContentId unlockId);
    }

All reward or cost mutations require an idempotent OperationId. Retrying the
same operation must return the original result without applying it twice.

## 5. Domain events published by Core

Core publishes immutable events after the authoritative transaction succeeds:

| Event | Required fields |
|---|---|
| MobKilled | event ID, killer/contributors, mob definition ID, runtime entity ID, location, timestamp |
| BossDefeated | event ID, encounter ID, boss ID, eligible contributors, timestamp |
| ItemAcquired | event ID, player, item ID, amount, source, timestamp |
| ItemRemoved | event ID, player, item ID, amount, reason, timestamp |
| SkillUsed | event ID, player, skill ID, valid effect result, timestamp |
| CraftCompleted | event ID, player, recipe ID, output IDs, timestamp |
| MasteryChanged | event ID, player, mastery ID, old/new level and XP |
| PlayerDied | event ID, player, cause category, killer ID if present |
| RegionEntered | event ID, player, region ID, from/to location |
| WorldObjectInteracted | event ID, player, object ID, hand, timestamp |

Events are at-least-once compatible. Consumers deduplicate by event ID.
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
- Async completion must re-enter the correct Paper scheduler before touching Bukkit objects.
- Event payloads must not contain mutable Bukkit objects.
- Player logout invalidates the runtime session token; late async callbacks must verify it.

## 8. Transaction contract

- Database transactions are owned by the service performing the mutation.
- No transaction remains open while waiting for player input or Paper scheduling.
- Quest reward delivery uses an outbox/pending-operation record.
- Inventory delivery that cannot complete routes to an authoritative mailbox or pending claim; it is never silently dropped.
- Currency, item consumption, crafting, trade, and quest rewards are audited.

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
