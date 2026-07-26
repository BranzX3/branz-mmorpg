# Branz MMORPG — Life Skill Mastery Specification

Status: Proposed
Owner: Core MMO developer
Depends on: Player Session, Content, Storage, Item, and Region systems
Supersedes: the block-break model of the previous revision

## 1. Purpose

Life Skill Mastery is MMO progression earned from **MMO life-skill content**, not
from ordinary Minecraft survival play. The world contains hand-placed
**gathering nodes** — an ore vein, a herb patch, a fishing spot — and harvesting
one is the only action that grants Life Skill XP. The model is BDO-style: nodes
occupy fixed positions, are shared by everyone on the server, deplete when
harvested, and respawn on a timer.

Breaking a stone block in the world is ordinary Minecraft. It grants nothing.

The first implementation target is **Mining**.

### 1.1 Why this model

The previous revision derived XP from block breaks anywhere in the world, which
forced the engine to answer a question it cannot answer reliably: *was this block
naturally generated, or did the player place it a moment ago?* Getting that wrong
means place-break-place-break yields unlimited XP, and getting it right requires
per-block origin tracking across restarts, world edits, and rollbacks.

Node-based gathering removes the question. A placed block is not a registered
node, so it grants zero **by construction** rather than by heuristic. Throughput
is bounded by respawn timers, which are content data, instead of by anti-exploit
detection. This is both safer and considerably less work.

## 2. Initial Life Skills

| Skill | Tool | Node kinds |
|---|---|---|
| Mining | Pickaxe | Ore veins, mineral deposits |
| Woodcutting | Axe | Registered trees, sap taps |
| Excavation | Shovel | Dig sites, ruins |
| Foraging | Hoe or hand | Herb patches, wild crops |
| Fishing | Fishing rod | Fishing spots |

Mining is required for the first release. The others reuse the same engine and
stay disabled until their node definitions and content exist.

## 3. Gathering nodes

A node has two halves, and keeping them separate is the central design rule:

| Half | What it is | Where it lives | Changes via |
|---|---|---|---|
| **Node definition** | Kind of node: skill, tier, XP, yields, tool, harvest time, respawn window | YAML content | `/branz reload` |
| **Node instance** | One physical node at one coordinate | Database (world state) | admin commands |

A definition is content and may be reloaded atomically. An instance is world
state, survives reload untouched, and is never expressed in YAML — a content
reload must never move, delete, or respawn a node that exists in the world.

### 3.1 Instance record

    node_instance_id
    definition_id
    world_uid, x, y, z
    state
    respawn_at
    reserved_by, reserved_until
    last_harvested_by, last_harvested_at
    created_by, created_at

### 3.2 Node lifecycle

    AVAILABLE
      -> RESERVED   (a player began harvesting)
      -> DEPLETED   (harvest committed)
      -> AVAILABLE  (respawn_at reached)

    RESERVED -> AVAILABLE   (harvest interrupted or reservation expired)
    any      -> BROKEN      (world no longer matches the definition)

`BROKEN` is not a failure of the player's action; it is an operator alert. A node
whose world block was removed by terrain editing must refuse to be harvested and
appear in admin listings, never grant XP against a block that is not there.

### 3.3 Contest and reservation

Nodes are shared world objects. Competition for a rich vein is intended.

Harvesting takes time (`harvest_time_ms`), and the node is **reserved at the
start of the channel, not at the end**. Two players interacting in the same tick
resolve through one atomic compare-and-set on node state: exactly one wins and
begins channelling, the other is told the node is taken and loses nothing. The
alternative — both channel, one is robbed at the finish — punishes a player for
work already done and is rejected.

Reservation rules:

- A reservation carries `reserved_until = now + harvest_time_ms + grace`.
- An expired reservation returns the node to AVAILABLE. A crashed or
  disconnected player never locks a node permanently.
- Interruption (moving beyond leash range, taking damage, swapping tools,
  logging out) releases the reservation and grants nothing.
- One player holds at most one reservation at a time.

### 3.4 Harvest interaction

Harvesting is an **interaction with the node's block, never a block break**. The
block is not destroyed; on depletion its presentation swaps to the definition's
depleted appearance and swaps back on respawn.

This is deliberate: no block is broken, so there is nothing to restore, no
interaction with world protection or rollback tooling, and no way for a
half-completed harvest to leave a hole in the world. It also means node blocks
can be protected against ordinary breaking wholesale — breaking a node block is
simply denied.

Harvest sequence:

1. Player interacts with a node block.
2. Server validates eligibility (§4) and atomically reserves the node.
3. Channel runs for `harvest_time_ms`, reduced by mastery effects within caps.
4. On completion, one transaction commits: node depleted with `respawn_at`,
   yields granted, XP granted, audit written, events published.
5. Any failure rolls back and releases the reservation. Partial credit does not
   exist.

## 4. Eligibility

XP and yields are granted only when:

1. The player has an ACTIVE MMO session.
2. The target is a node instance in state AVAILABLE.
3. The node's definition is enabled in the active content snapshot.
4. The held tool matches the definition's required tool tag.
5. The player meets the definition's required skill level and any region or
   unlock requirement.
6. Region, permission, and protection rules allow the interaction.
7. The reservation was won by this player and has not expired.
8. The harvest channel completed without interruption.
9. The operation ID has not been processed before.

A wrong tool produces a clear refusal message. It never silently grants zero.

## 5. XP and throughput

    awarded_xp = floor(
        base_xp
        * node_quality_multiplier
        * region_multiplier
        * diminishing_multiplier
        * event_multiplier
    )

Every multiplier is finite, bounded, and recorded in audit data. The result is
never negative.

<<<<<<< HEAD
### 5.1 Authoritative block origin

Core consumes block provenance through a platform port:

    interface BlockOriginPort {
        BlockOrigin origin(BlockPosition position);
        void recordPlacement(BlockPosition position, BlockOrigin origin);
        void recordRemoval(BlockPosition position, OperationId operationId);
        void move(BlockPosition from, BlockPosition to, OperationId operationId);
    }

The platform-independent origin values are:

    NATURAL
    PLAYER_PLACED
    REGISTERED_NODE
    PLUGIN_CREATED
    RESTORED
    UNKNOWN

`UNKNOWN` fails closed for rare, epic, and legendary XP. Common sources may use
the configured conservative fallback, which cannot exceed common-tier XP.

The Paper adapter must define behavior for:

- Player placement, piston movement, falling blocks, and block transformation
- Silk Touch placement and movement of mined blocks
- Explosion, TNT, fire, fluid, and entity-caused removal
- WorldEdit or other plugin-created and restored blocks
- Chunk unload/reload and server restart
- World regeneration and deletion
- Registered node depletion and respawn

Moving a tracked block moves its origin; it never converts a placed block into a
natural block. Explosion or indirect destruction attributes the action to a
player only when a trusted server-side cause chain exists. Otherwise it grants
no Survival XP.

Origin records for non-natural blocks and registered nodes survive chunk unload
and restart. Cleanup is keyed by world identity, chunk, block coordinates, and
world-generation epoch so stale records cannot affect a recreated world.

## 6. Levels and Skill Points
=======
Suggested initial Mining tiers:
>>>>>>> 14f48819ebb179329fe30a79707d68429f4dc351

| Tier | Example node | Base XP | Respawn |
|---|---|---:|---|
| Common | Stone deposit | 1 | 30s |
| Uncommon | Coal vein | 3 | 60s |
| Rare | Iron vein | 6 | 3m |
| Epic | Diamond vein | 15 | 10m |
| Legendary | Event mineral | 40+ | 30m+ |

**Respawn timers are the throughput cap.** A region's XP per hour is the sum of
its nodes divided by their respawn windows, which is a number content designers
can compute in advance rather than discover after launch.

Because throughput is already bounded, anti-farm rules stay minimal:

- Optional per-player diminishing returns for repeatedly harvesting the same
  node instance within a window, so camping one respawn is worse than rotating.
- Trivial-tier nodes may grant reduced XP at high skill level.
- Admin preview, test, and rollback actions grant nothing.
- One harvest commits at most one XP operation.
- Implausible harvest rates raise staff telemetry rather than silently
  cancelling, so automation is investigated instead of guessed at.

Rejections record a reason: `NODE_TAKEN`, `NODE_DEPLETED`, `INVALID_TOOL`,
`LEVEL_TOO_LOW`, `INTERRUPTED`, `DUPLICATE_OPERATION`, `RATE_LIMITED`,
`NODE_BROKEN`.

## 6. Levels and skill points

Each Life Skill stores independent total XP, level, unspent points, unlocked
nodes, and content revision.

<<<<<<< HEAD
The level curve is a **cumulative total-XP threshold**. A player reaches level
`L` when `total_xp >= total_xp_required(L)`.

    total_xp_required(level) = round(75 * level ^ 1.55)

Initial golden values:

| Level | Required total XP |
|---:|---:|
| 1 | 75 |
| 2 | 220 |
| 3 | 412 |
| 4 | 643 |
| 5 | 909 |
| 6 | 1,206 |
| 7 | 1,531 |
| 8 | 1,883 |
| 9 | 2,260 |
| 10 | 2,661 |

The level cap and thresholds are content-driven. Configured level milestones
grant Survival Skill Points. Levels and unlocked nodes never decrease on death.
Total XP uses a signed 64-bit integer, cannot be negative, and uses checked
arithmetic. A grant may cross multiple levels in one transaction; every crossed
milestone grants its configured points exactly once. One committed operation
emits one XP event and zero or more ordered level-change events.

At level cap, XP is clamped to the configured cap threshold unless prestige is
enabled. Overflow, negative grants through the normal gameplay API, NaN
multipliers, and non-finite formula inputs are rejected. Administrative revoke
or repair is a separate audited operation and cannot reduce a level or invalidate
an unlocked node unless an explicit reset policy permits it.
=======
    xp_required(level) = round(75 * level ^ 1.55)

Cap and thresholds are content-driven. Milestone levels grant Life Skill Points.
Levels and unlocked mastery nodes never decrease on death.
>>>>>>> 14f48819ebb179329fe30a79707d68429f4dc351

A respec, if enabled, is transactional, audited, has an explicit cost, and never
leaves a partially reset tree.

## 7. Mastery tree

Each skill owns a directed acyclic graph of mastery nodes containing:

- Stable content ID and display metadata
- Maximum rank and point cost
- Required skill level
- Prerequisite nodes and ranks
- Optional achievement or region requirement
- Declarative, bounded effects

Initial Mining example:

    Mining
    ├─ Stoneworker (3 ranks)
    │  └─ Efficient Swing (2 ranks)
    ├─ Ore Sense (1 rank)
    │  └─ Prospector (3 ranks)
    └─ Deep Delver (1 rank)
       └─ Geologist (3 ranks)

| Node | Effect |
|---|---|
| Stoneworker | Reduces harvest time on common nodes, capped |
| Efficient Swing | Reduces configured tool or stamina cost |
| Ore Sense | Bounded hint toward nearby AVAILABLE rare nodes |
| Prospector | Small capped chance of bonus yield |
| Deep Delver | Unlocks configured deep-region nodes |
| Geologist | Chance of configured by-products |

Tree effects cannot bypass region protection, mint currency, execute console
commands, reduce harvest time to zero, or create unbounded yield, XP, speed, or
damage multipliers.

<<<<<<< HEAD
### 7.1 Tree revision and migration

Published tree definitions have an immutable revision. Existing player progress
stores the revision under which it was last validated.

Content reload validates a migration plan before activating a tree change:

- Added nodes require no player migration.
- Renamed nodes require an explicit stable-ID alias.
- Removed nodes require `REFUND`, `REPLACE`, or `RETAIN_DISABLED` policy.
- Increased costs never create a negative point balance.
- Decreased costs refund the configured difference exactly once.
- Changed prerequisites cannot silently remove an unlocked rank.
- Changed effects are removed and reapplied once from the new validated snapshot.

If no safe migration plan exists, reload is rejected and the previous snapshot
remains active. Migration is idempotent per player, skill, source revision, and
target revision. A failed player migration leaves that player on the previous
revision and disables new tree mutations until repair; it does not partially
apply ranks, effects, or refunds.

## 8. Content Definitions
=======
## 8. Content definitions
>>>>>>> 14f48819ebb179329fe30a79707d68429f4dc351

Node definition:

```yaml
id: branz:iron_vein
type: gathering_node
skill: branz:mining
tier: rare
base_xp: 6
required_tool_tag: branz:pickaxe
required_level: 10
harvest_time_ms: 3200
respawn_seconds: 180
respawn_jitter_seconds: 30
presentation:
  available_block: minecraft:iron_ore
  depleted_block: minecraft:stone
  hologram: "<gray>Iron Vein"
yields:
  - item: branz:raw_iron_chunk
    amount: [1, 3]
  - item: branz:geode_fragment
    amount: [1, 1]
    chance: 0.05
```

Mastery tree node:

```yaml
id: branz:mining_stoneworker
type: life_skill_node
skill: branz:mining
max_rank: 3
point_cost_per_rank: 1
requires_level: 2
effect:
  type: harvest_time_reduction
  target_tags: [branz:common_mining]
  percent_per_rank: 2
  cap_percent: 6
```

Reload validation rejects duplicate IDs, unknown skills, unknown tools, tags, or
items, negative XP, non-positive harvest or respawn times, cyclic trees,
unreachable nodes, invalid prerequisites, and unbounded effects. A failed reload
retains the previous snapshot.

**Reload against live instances**: if a reload removes a definition that placed
instances still reference, those instances become BROKEN and are reported. The
reload is not rejected for this — world state is not content — but the operator
is told exactly which instances are now orphaned.

## 9. Persistence and transactions

<<<<<<< HEAD
    player_uuid
    skill_id
    level
    total_xp
    unspent_points
    schema_version
    tree_revision
    updated_at
=======
Skill progress keyed by player UUID and skill ID:
>>>>>>> 14f48819ebb179329fe30a79707d68429f4dc351

    player_uuid, skill_id, level, total_xp, unspent_points, tree_revision, updated_at

Node ranks keyed by player UUID, skill ID, and node ID. Node instances as in
§3.1.

<<<<<<< HEAD
Operation-result and outbox retention must be longer than the maximum supported
retry/recovery window. Purging requires a recorded high-water mark and cannot
remove an operation that may still be retried by an active recovery job.

Database failure follows the normal fail-closed player-session policy. The
system must not substitute a blank profile or grant speculative XP.

## 10. Events and API

Core publishes immutable events after the authoritative transaction commits.
All events use the common envelope from
`DEVELOPMENT_OWNERSHIP_AND_CONTRACTS.md`, including event ID, operation ID,
event version, timestamp, aggregate sequence, and content revision.
=======
- Harvest commits node depletion, yields, XP, audit, and events in one
  transaction. There is no state in which the node is depleted but the player
  was not paid.
- XP grants, level-ups, point grants, node purchases, and respecs use unique
  operation IDs per `EXTERNAL_PLUGIN_INTEGRATION_CONTRACT` §3. A retry returns
  the original result and grants nothing twice.
- Yields that cannot be delivered to a full inventory route to the authoritative
  mailbox or pending claim; they are never dropped silently.
- On boot, node state is recomputed from `respawn_at`. Downtime does not
  respawn every node at once, and it does not lose timers either.
- Storage failure follows the fail-closed session policy: no blank profile, no
  speculative XP.

## 10. Events and API

Published after the authoritative transaction commits:
>>>>>>> 14f48819ebb179329fe30a79707d68429f4dc351

| Event | Payload data |
|---|---|
<<<<<<< HEAD
| SurvivalXpGranted | Player, skill, source, base XP, multipliers, awarded XP, resulting total XP |
| SurvivalSkillLevelChanged | Player, skill, old/new level, total XP, points granted |
| SurvivalSkillNodeUnlocked | Player, skill, node, old/new rank, points spent, points remaining |
=======
| LifeSkillXpGranted | Event ID, operation ID, player, skill, node instance, node definition, XP, timestamp |
| LifeSkillLevelChanged | Player, skill, old/new level, total XP |
| LifeSkillNodeUnlocked | Player, skill, mastery node, rank, remaining points |
| GatheringNodeHarvested | Node instance, definition, harvester, yields, respawn_at |
| GatheringNodeRespawned | Node instance, definition, timestamp |
>>>>>>> 14f48819ebb179329fe30a79707d68429f4dc351

Quest and Paper consume `mmorpg-api` contracts only.

## 11. UI and administration

Player UI shows skill level, XP progress, available points, mastery ranks,
requirements, and recent gains. Updates are event-driven and coalesced. A
reserved node shows its channel progress; a depleted node shows its respawn.

Node authoring commands — nodes are placed by hand, so these are the primary
content-creation tool and deserve to be good:

    /branz node place <definitionId>          place at the targeted block
    /branz node remove                        remove the targeted node
    /branz node move                          move the targeted node to your target block
    /branz node inspect                       definition, state, respawn, last harvester
    /branz node list <definitionId|region>    listing with coordinates
    /branz node respawn <all|targeted>        force respawn, audited
    /branz node broken                        every BROKEN instance, for repair

Progression commands:

<<<<<<< HEAD
## 12. Performance Budget

- Survival processing attributable to one accepted block break targets under
  0.25 ms at p95 on the owning Paper thread, excluding vanilla block handling.
- No SQL, filesystem access, YAML parsing, or blocking wait occurs on the owning
  Paper thread.
- One player, block, or anti-farm window does not create an individual scheduler
  task; expiry uses bounded buckets or periodic batch cleanup.
- Origin lookup for a loaded chunk is memory-backed and targets under 0.10 ms at
  p95. Persistence flush is asynchronous and bounded.
- With 50 concurrent players and 20 eligible actions per second in aggregate,
  the persistence queue remains bounded and exposes depth, oldest age, failures,
  and retry count.
- Logout and shutdown drain or durably record pending mutations within the
  configured shutdown budget. No accepted operation is silently discarded.
- A soak test covers at least 100,000 synthetic actions, chunk unload/reload,
  origin movement, duplicate operations, and tree effect recalculation.

## 13. Acceptance Criteria

- Natural stone broken with a valid pickaxe grants exactly 1 configured XP.
- Configured rare ores grant more XP than common stone.
- Placed or restored rare ores cannot grant rare-tier XP by default.
- Cancelled block-break events grant no XP.
- Fortune and Silk Touch cannot duplicate XP.
- Duplicate operation IDs cannot grant XP or skill points twice.
- A single grant crossing multiple levels awards every milestone exactly once.
- XP overflow and negative gameplay grants are rejected.
- Level-up and skill-point grants are atomic.
- Node purchase cannot consume points without granting the node rank.
- Content validation rejects cycles and broken prerequisites.
- Tree revision migration is idempotent and cannot partially refund or apply ranks.
- Piston movement preserves placed-block origin across source and destination.
- Unknown rare-source origin grants no rare-tier XP.
=======
    /branz life inspect <player> [skill]
    /branz life grant-xp <player> <skill> <amount> <reason>
    /branz life tree <player> <skill>
    /branz life reset <player> <skill> <reason>

Mutating commands require permission, a reason, and an audit record. Reset
requires explicit confirmation.

## 12. Acceptance criteria

- Breaking an ordinary world block grants zero XP, in every skill, always.
- Harvesting a registered node grants exactly its configured XP.
- A rare node grants more than a common node.
- A depleted node grants nothing until `respawn_at` has passed.
- Two players interacting in the same tick: exactly one reserves it, the other
  receives `NODE_TAKEN` and loses nothing.
- An interrupted or abandoned channel grants nothing and releases the node
  within the reservation grace period.
- A disconnect mid-channel never locks a node permanently.
- Duplicate operation IDs cannot grant XP, yields, or points twice.
- Level-up and point grants are atomic with the harvest that caused them.
- Node purchase cannot consume points without granting the rank.
- Content validation rejects cycles, broken prerequisites, and unbounded effects.
- A reload that orphans placed instances marks them BROKEN and reports them
  instead of granting XP against a missing block.
- Server restart preserves respawn timers; nodes neither all respawn at once nor
  stay depleted forever.
>>>>>>> 14f48819ebb179329fe30a79707d68429f4dc351
- Logout, reconnect, reload, failed save, and shutdown preserve progress.
- Pure Java tests cover XP formulas, levels, trees, reservation state machine,
  and idempotency.
- Paper smoke tests cover harvest, contest, interruption, depletion, respawn,
  wrong tool, and a broken node.
- No SQL, filesystem access, or content parsing occurs on a Paper tick thread.
