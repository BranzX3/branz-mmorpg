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

Suggested initial Mining tiers:

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

    xp_required(level) = round(75 * level ^ 1.55)

Cap and thresholds are content-driven. Milestone levels grant Life Skill Points.
Levels and unlocked mastery nodes never decrease on death.

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

## 8. Content definitions

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

Skill progress keyed by player UUID and skill ID:

    player_uuid, skill_id, level, total_xp, unspent_points, tree_revision, updated_at

Node ranks keyed by player UUID, skill ID, and node ID. Node instances as in
§3.1.

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

| Event | Required data |
|---|---|
| LifeSkillXpGranted | Event ID, operation ID, player, skill, node instance, node definition, XP, timestamp |
| LifeSkillLevelChanged | Player, skill, old/new level, total XP |
| LifeSkillNodeUnlocked | Player, skill, mastery node, rank, remaining points |
| GatheringNodeHarvested | Node instance, definition, harvester, yields, respawn_at |
| GatheringNodeRespawned | Node instance, definition, timestamp |

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
- Logout, reconnect, reload, failed save, and shutdown preserve progress.
- Pure Java tests cover XP formulas, levels, trees, reservation state machine,
  and idempotency.
- Paper smoke tests cover harvest, contest, interruption, depletion, respawn,
  wrong tool, and a broken node.
- No SQL, filesystem access, or content parsing occurs on a Paper tick thread.
