# Architecture

## Module layout

```text
mmo-platform
├─ mmo-api
├─ mmo-bootstrap
├─ mmo-character
├─ mmo-combat
├─ mmo-magic
├─ mmo-items
├─ mmo-progression
├─ mmo-quests
├─ mmo-scenes
├─ mmo-lifeskills
├─ mmo-market
├─ mmo-worldloop
├─ mmo-social
├─ mmo-persistence
├─ mmo-content
├─ mmo-devtools
└─ mmo-integrations
   ├─ integration-oraxen
   ├─ integration-mythicmobs
   ├─ integration-packetevents
   ├─ integration-worldguard
   └─ integration-wallet
```

## Ownership boundaries

| Domain | Owner |
|---|---|
| Player combat timelines and damage | `mmo-combat` |
| Spell runtime and attunement | `mmo-magic` |
| Item identity, locations and equipment | `mmo-items` |
| Combat mastery and conditioning | `mmo-progression` |
| Lifeskill rank, nodes, farming and workers | `mmo-lifeskills` |
| Orders, escrow, price history and commissions | `mmo-market` |
| Camps, navigation, travel, encounters and city services | `mmo-worldloop` |
| Party, LFG, guild and activity membership | `mmo-social` |
| Quest/dialogue state | `mmo-quests` |
| Scene sessions and HUD presentation | `mmo-scenes` |
| Leases, journals, migrations and repositories | `mmo-persistence` |

## Provider interfaces

Gameplay code uses these interfaces:

```java
interface AssetProvider {}
interface MobProvider {}
interface PacketProvider {}
interface RegionProvider {}
interface WalletProvider {}
interface NpcProvider {}
interface ResourcePackProvider {}
interface ClockProvider {}
```

Providers return typed results and health status. Missing optional providers degrade presentation; missing required providers place the server in safe maintenance mode rather than continuing with partial ownership behavior.

## Core services

```text
CharacterSessionService
ContentSnapshotService
TransactionService
ItemLocationService
EncounterService
ActionTimelineService
DefinitionRegistry
AuditService
ReconciliationService
```

## Domain events

Events are immutable records published after transaction commit where applicable. Important events include:

- `CharacterSessionActivated`
- `CombatEngagementChanged`
- `ActionStarted/Committed/Cancelled`
- `ItemLocationChanged`
- `RewardGranted`
- `MarketOrderFilled`
- `LifeskillEvidenceRecorded`
- `CampRestCompleted`
- `MountStateChanged`
- `QuestStateChanged`

Events are not commands. A listener cannot assume it may reverse the originating transaction.

## Threading

- Minecraft entity/world mutations execute on the appropriate server thread/region scheduler.
- Database and content parsing execute asynchronously.
- Async results re-enter the server thread only after confirming the character session and content snapshot are still valid.
- No async task retains mutable Bukkit entities.

## Failure modes

- Database unavailable: block new sessions and value-changing actions; allow existing players a short read-only grace before safe disconnect.
- Asset provider unavailable: preserve items as barrier/fallback representations and disable creation of missing assets.
- Packet provider unavailable: disable Scene Preview and advanced camera effects; core ownership remains available.
- Mob provider unavailable: stop new encounters and safely reset active provider-owned encounters.
- Wallet provider unavailable: disable purchases, fees and death-wallet transfer; never infer balances.
