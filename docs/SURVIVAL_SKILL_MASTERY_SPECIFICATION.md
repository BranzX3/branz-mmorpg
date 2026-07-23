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

## 6. Levels and Skill Points

Each Survival Skill stores independent total XP, level, unspent points, unlocked
nodes, and content revision.

Suggested initial level curve:

    xp_required(level) = round(75 * level ^ 1.55)

The level cap and thresholds are content-driven. Configured level milestones
grant Survival Skill Points. Levels and unlocked nodes never decrease on death.

A respec, if enabled, must be transactional, audited, have an explicit cost,
and never leave the player with a partially reset tree.

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

## 8. Content Definitions

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

    player_uuid
    skill_id
    level
    total_xp
    unspent_points
    tree_revision
    updated_at

Node ranks are keyed by player UUID, skill ID, and node ID.

XP grants, level-ups, point grants, node purchases, and respecs require unique
operation IDs. Progress, audit data, and domain events are committed atomically.
Retrying an operation returns its original result without granting anything
twice.

Database failure follows the normal fail-closed player-session policy. The
system must not substitute a blank profile or grant speculative XP.

## 10. Events and API

Core publishes immutable events after the authoritative transaction commits:

| Event | Required data |
|---|---|
| SurvivalXpGranted | Event ID, operation ID, player, skill, source, XP, timestamp |
| SurvivalSkillLevelChanged | Player, skill, old/new level, total XP |
| SurvivalSkillNodeUnlocked | Player, skill, node, rank, remaining points |

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

## 12. Acceptance Criteria

- Natural stone broken with a valid pickaxe grants exactly 1 configured XP.
- Configured rare ores grant more XP than common stone.
- Placed or restored rare ores cannot grant rare-tier XP by default.
- Cancelled block-break events grant no XP.
- Fortune and Silk Touch cannot duplicate XP.
- Duplicate operation IDs cannot grant XP or skill points twice.
- Level-up and skill-point grants are atomic.
- Node purchase cannot consume points without granting the node rank.
- Content validation rejects cycles and broken prerequisites.
- Logout, reconnect, reload, failed save, and shutdown preserve progress.
- Pure Java tests cover formulas, levels, trees, and idempotency.
- Paper smoke tests cover valid, invalid, placed, and cancelled block breaks.
- No SQL, filesystem access, or content parsing occurs on a Paper tick thread.
