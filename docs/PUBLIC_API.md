# Public API Guide

`mmorpg-api` and `mmorpg-quest-api` are the only platform-independent public
contracts. Consumers should obtain services through Paper's Services Manager
and must not call Core, Storage, or Paper implementation classes.

Registered services include content, life skills, combat mastery, loadouts,
inventory, loot, equipment, gathering, crafting, economy, encounters, party,
trade, telemetry, quests, and quest content.

## Contract rules

- Every valuable mutation requires a durable `OperationId`.
- Reusing the same operation ID returns the existing outcome; it must not mint
  another result.
- `ContentId` always uses `namespace:value`.
- Snapshots and returned collections are immutable.
- Storage failure is not permission to invent default player state.
- Currency operations go through `EconomyPaymentPort`/`AdminCurrencyPort`,
  which delegate to BranzWallet. MMORPG has no balance or ledger table.
- Paper objects must not cross into API/Core.
- Quest economic/persistent actions use `PendingQuestOperation`; presentation
  actions are best-effort and recovery cleanup is idempotent.

## Quest content and events

`QuestContentService` exposes the active atomic catalog and reload diagnostics.
`QuestService` supports start, event processing, turn-in, abandon, retry-safe
pending actions, audited migration, stage repair, and objective repair.

Publish immutable `QuestEvent` values with a stable event UUID whenever the
source has a durable operation. Party and encounter eligibility must be the
snapshot captured at event time.

Quest modules depend only on public game ports and API types. They never import
`com.branz.mmorpg.core`.

## Compatibility

Adding default interface methods and new records is preferred. Removing or
renaming an existing method, enum constant, content ID, stage ID, or objective
ID requires a documented compatibility migration. Quest definition changes use
`SAFE`, `REQUIRES_MAPPING`, or `BREAKING`; active incompatible state becomes
`MIGRATION_REQUIRED` and never resets silently.
