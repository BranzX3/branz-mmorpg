# Runtime Architecture

## Deployment topology

V1 runs as a single Paper server backed by PostgreSQL and external object storage/CDN for resource packs. A proxy may exist for connection routing, but no cross-server character migration or inventory sharing is implemented in V1.

```text
Minecraft clients
        |
      Paper
        |
+---------------- Core MMO ----------------+
| character | combat | items | magic | UI  |
+------------------------------------------+
  |          |          |          |
PostgreSQL  Providers   Oraxen    Metrics/logs
                         |
                    Resource-pack CDN
```

## Gradle modules

```text
mmo-api
mmo-bootstrap
mmo-character
mmo-combat
mmo-moves
mmo-magic
mmo-items
mmo-equipment
mmo-progression
mmo-economy
mmo-party
mmo-encounters
mmo-quests
mmo-scenes
mmo-persistence
mmo-content
mmo-admin
mmo-integrations
  integration-oraxen
  integration-packetevents
  integration-mythicmobs
  integration-worldguard
  integration-wallet
```

Modules may depend on `mmo-api` and lower-level domain modules. Integration modules depend inward on interfaces; domain modules never depend on integration implementations.

## Core services

### CharacterSessionService

Owns login acquisition, session lease, loaded aggregate, live state token and logout flush. One session token is generated per successful lease.

### CombatRuntime

Owns state machines, input routing, action scheduling, hit resolution, resources, defense, CC and combat telemetry. It does not create loot.

### EncounterService

Owns encounter membership, content snapshot, threat adapter, wipe/reset, checkpoints, contribution and completion events.

### ItemService

Owns item definitions and item instances. It may create display `ItemStack`s through `AssetProvider`, but persistent state lives in the item repository.

### TransactionService

Owns atomic transfer and mutation of valuable state. Transactions are idempotent through a caller-supplied idempotency key.

### SceneService

Owns local Scene eligibility, preview actor lifecycle, preview state, menu navigation and commit/cancel operations.

### ContentService

Loads an immutable content bundle, validates compatibility, exposes registries and controls snapshot switching.

## Provider interfaces

Required provider boundaries:

```java
interface AssetProvider {}
interface PreviewActorProvider {}
interface PacketProvider {}
interface MobProvider {}
interface RegionProvider {}
interface WalletProvider {}
interface NpcProvider {}
interface ResourcePackProvider {}
```

Provider capability discovery is explicit. Example: if `PreviewActorProvider` is unavailable, the Character Scene may open in compact 2D mode, but equipment transactions remain functional.

## Domain events

Events are immutable records. Internal events use an in-process event bus; durable business events are appended to an outbox in the same database transaction as state changes.

Important events:

- `CharacterSessionOpened`
- `CharacterSessionClosed`
- `WeaponStateChanged`
- `EngagementChanged`
- `ActionStarted`, `ActionCommitted`, `ActionEnded`, `ActionInterrupted`
- `HitResolved`
- `EncounterJoined`, `EncounterCompleted`, `EncounterReset`
- `RewardGranted`
- `ItemTransferred`, `ItemMutated`
- `SceneOpened`, `SceneClosed`, `SceneCommitted`
- `MasteryEvidenceAccepted`
- `DeathPouchCreated`, `DeathPouchRecovered`, `DeathPouchExpired`

Domain events do not expose Bukkit classes.

## Threading model

- Database work, content parsing and file hashing occur off the main thread.
- Live Bukkit/entity/inventory changes occur on the correct Paper scheduler context.
- An async callback carries `character_id` and `session_token`; stale tokens are discarded.
- Each combatant has one serialized command lane. Commands may be queued from events but are applied in deterministic tick order.
- Cross-character transactions lock records in a stable sorted order to avoid deadlocks.

## Dependency failure modes

| Provider | Failure behavior |
|---|---|
| AssetProvider | Block creation of affected new items; preserve existing item state; use missing-asset visual |
| PreviewActorProvider | Fall back to compact Scene UI without 3D preview |
| PacketProvider | Disable optional camera/advanced effects; combat remains functional |
| MobProvider | Disable encounter starts that require it; do not unload characters |
| RegionProvider | Default to conservative rules: no PvP, no Scene in unknown restricted region, normal PvE elsewhere |
| WalletProvider | Freeze wallet transactions and death-pouch creation; do not guess balances |

## Stable identifiers

Use lowercase namespaced strings:

```text
weapon.greatsword.iron_wolf
move.greatsword.light_1
technique.greatsword.rising_moon
form.greatsword.iron_tempest
status.burn
scene.character_hub
quest.red_harbor.introduction
```

IDs are never reused for a different meaning.

## Configuration ownership

- Content definitions own authored gameplay content.
- `22-default-config.md` values become server configuration defaults.
- Runtime configuration may adjust numeric balance within declared safe bounds.
- Invariants, state transitions and persistent schemas are not runtime toggles.
