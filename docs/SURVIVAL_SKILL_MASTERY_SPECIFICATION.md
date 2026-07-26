# Branz MMORPG — Survival Skill Mastery Specification

Status: Proposed  
Owner: Core MMO developer  
Depends on: Player Session, Content, Storage, Item, and Gathering systems

## 1. Purpose

Survival Skill Mastery turns normal Minecraft survival activities into MMO-style
progression. Players gain experience from valid actions performed with Minecraft
equipment, level individual Survival Skills, and spend points in a Mastery Tree.

The first implementation target is **Mining**:

- Breaking stone with a valid pickaxe grants 1 base Mining XP.
- Rarer ores grant more Mining XP.
- Player-placed or repeatedly exploited blocks do not provide normal XP.
- Skill progression and rewards are controlled by the server.

Survival Skill Mastery is separate from combat weapon mastery.

## 2. Initial Survival Skills

| Skill | Equipment | Eligible activities |
|---|---|---|
| Mining | Pickaxe | Stone, ores, mineral nodes |
| Woodcutting | Axe | Logs, stems, registered trees |
| Excavation | Shovel | Dirt, sand, gravel, clay |
| Foraging | Hoe or hand | Crops, herbs, plants |
| Fishing | Fishing rod | Fish, treasure, salvage |

Mining is required for the first release. Other skills reuse the same engine and
must remain disabled until their definitions and anti-exploit rules are ready.

## 3. Mining XP

Core evaluates a block break only after Paper confirms that the event was not
cancelled. The client, item lore, NBT, and packets are never authoritative.

Suggested initial values:

| Tier | Examples | Base XP |
|---|---|---:|
| Common | Stone, cobblestone, deepslate | 1 |
| Uncommon | Coal ore, copper ore | 3 |
| Rare | Iron ore, redstone ore, lapis ore | 6 |
| Epic | Gold ore, diamond ore, emerald ore | 15 |
| Legendary | Custom MMO ore or event mineral | 40+ |

All values are data-driven. Silk Touch, Fortune, and additional item drops do
not multiply mastery XP unless a content definition explicitly permits it.

XP is calculated as:

    awarded_xp = floor(
        base_xp
        * region_multiplier
        * node_quality_multiplier
        * anti_farm_multiplier
        * event_multiplier
    )

The result cannot be negative. Every multiplier must be finite, bounded, and
included in audit data.

## 4. Eligibility

XP is granted only when:

1. The player has an ACTIVE MMO session.
2. The block has an enabled Survival Skill source definition.
3. The held tool matches the configured tool or tool tag.
4. The source is naturally generated or is a registered gathering node.
5. World, region, permission, cooldown, and protection rules allow the action.
6. The block break and its authoritative reward complete successfully.
7. The operation ID has not been processed previously.

Wrong tools may produce normal Minecraft behavior while granting zero mastery
XP, unless the source definition requires the break to be rejected entirely.

## 5. Anti-farm Rules

- Player-placed blocks grant zero XP by default.
- Plugin-restored or regenerated blocks must retain a trusted origin marker.
- If valuable-block origin cannot be verified, rare-tier XP must not be granted.
- Repeated actions in the same location and time window may receive decay.
- Trivial sources may grant reduced or zero XP at high skill levels.
- Admin preview, rollback, and test actions grant no normal XP.
- One block break can produce at most one committed XP operation.
- Implausible action rates generate staff telemetry.

Anti-farm rejection stores an internal reason such as `PLACED_BLOCK`,
`DUPLICATE_OPERATION`, `INVALID_TOOL`, `SOURCE_COOLDOWN`, or `RATE_LIMITED`.

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

<<<<<<< HEAD
### 5.2 Suggested initial Mining tiers
=======
## 6. Levels and Skill Points
>>>>>>> parent of 3846639 (74)

Each Survival Skill stores independent total XP, level, unspent points, unlocked
nodes, and content revision.

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
<<<<<<< HEAD
=======

A respec, if enabled, must be transactional, audited, have an explicit cost,
and never leave the player with a partially reset tree.
>>>>>>> parent of 3846639 (74)

## 7. Mastery Tree

Each skill owns a directed acyclic graph of mastery nodes. A node contains:

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

Suggested effects:

| Node | Effect |
|---|---|
| Stoneworker | +2% mining speed per rank for common blocks |
| Efficient Swing | Reduces configured tool or stamina cost |
| Ore Sense | Shows a bounded hint for nearby eligible rare nodes |
| Prospector | Small capped chance for configured bonus material |
| Deep Delver | Unlocks configured deep-region nodes |
| Geologist | Chance to obtain configured geology by-products |

Tree effects cannot bypass region protection, mint arbitrary currency, execute
console commands, or create unbounded item, speed, XP, or damage multipliers.

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

<<<<<<< HEAD
## 8. Content definitions
=======
## 8. Content Definitions
>>>>>>> parent of 3846639 (74)

Example gathering source:

```yaml
id: branz:diamond_ore_mining
type: survival_gathering
skill: branz:mining
block: minecraft:diamond_ore
required_tool_tag: branz:pickaxe
tier: epic
base_xp: 15
eligible_origins: [NATURAL, REGISTERED_NODE]
```

Example tree node:

```yaml
id: branz:mining_stoneworker
type: survival_skill_node
skill: branz:mining
max_rank: 3
point_cost_per_rank: 1
requires_level: 2
effect:
  type: gathering_speed_bonus
  target_tags: [branz:common_mining]
  percent_per_rank: 2
  cap_percent: 6
```

Reload validation rejects duplicate IDs, unknown skills, unknown tools or tags,
negative XP, cyclic trees, unreachable nodes, invalid prerequisites, and
unbounded effects. Failed reload retains the previous content snapshot.

## 9. Persistence and Transactions

Skill progress is keyed by player UUID and skill ID:

<<<<<<< HEAD
Skill progress keyed by player UUID and skill ID:

    player_uuid, skill_id, level, total_xp, unspent_points,
    schema_version, tree_revision, updated_at
=======
    player_uuid
    skill_id
    level
    total_xp
    unspent_points
    schema_version
    tree_revision
    updated_at

Node ranks are keyed by player UUID, skill ID, and node ID.
>>>>>>> parent of 3846639 (74)

XP grants, level-ups, point grants, node purchases, and respecs require unique
operation IDs. Progress, audit data, and domain events are committed atomically.
Retrying an operation returns its original result without granting anything
twice.

Operation-result and outbox retention must be longer than the maximum supported
retry/recovery window. Purging requires a recorded high-water mark and cannot
remove an operation that may still be retried by an active recovery job.

Database failure follows the normal fail-closed player-session policy. The
system must not substitute a blank profile or grant speculative XP.

<<<<<<< HEAD
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

=======
>>>>>>> parent of 3846639 (74)
## 10. Events and API

Core publishes immutable events after the authoritative transaction commits.
All events use the common envelope from
`DEVELOPMENT_OWNERSHIP_AND_CONTRACTS.md`, including event ID, operation ID,
event version, timestamp, aggregate sequence, and content revision.

| Event | Payload data |
|---|---|
| SurvivalXpGranted | Player, skill, source, base XP, multipliers, awarded XP, resulting total XP |
| SurvivalSkillLevelChanged | Player, skill, old/new level, total XP, points granted |
| SurvivalSkillNodeUnlocked | Player, skill, node, old/new rank, points spent, points remaining |
<<<<<<< HEAD
| GatheringNodeHarvested | Node instance, definition, harvester, yields, respawn_at |
| GatheringNodeRespawned | Node instance, definition, timestamp |
=======
>>>>>>> parent of 3846639 (74)

Quest and Paper consume public contracts from `mmorpg-api` and must not import
Core implementation classes.

## 11. UI and Administration

The player UI shows skill level, XP progress, available points, node ranks,
requirements, and recent XP gains. Updates are event-driven and coalesced.

Required admin commands:

    /branz survival inspect <player> [skill]
    /branz survival grant-xp <player> <skill> <amount> <reason>
    /branz survival tree <player> <skill>
    /branz survival reset <player> <skill> <reason>
    /branz survival source inspect <player>

Mutation commands require permission, reason, and audit records. Reset requires
explicit confirmation.

<<<<<<< HEAD
    /branz life inspect <player> [skill]
    /branz life grant-xp <player> <skill> <amount> <reason>
    /branz life tree <player> <skill>
    /branz life reset <player> <skill> <reason>

Mutating commands require permission, a reason, and an audit record. Reset
requires explicit confirmation.

=======
>>>>>>> parent of 3846639 (74)
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
<<<<<<< HEAD
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
=======
>>>>>>> parent of 3846639 (74)
- Logout, reconnect, reload, failed save, and shutdown preserve progress.
- Pure Java tests cover formulas, levels, trees, and idempotency.
- Paper smoke tests cover valid, invalid, placed, and cancelled block breaks.
- No SQL, filesystem access, or content parsing occurs on a Paper tick thread.
