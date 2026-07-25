# Branz MMORPG — Combat Mastery and Character Build Specification

Status: Proposed workstream contract  
Owner: Core MMO developer  
Depends on: Player Session, Attributes, Combat, Skills, Items, Equipment, and Content

## 1. Purpose

Each character permanently selects Warrior, Mage, or Rogue. Combat identity is
produced by that class together with the current weapon, equipped skills, armor
and utility choices, specialization, and earned Combat Mastery.

Changing a valid loadout changes the build within the selected class. It does
not change the permanent class or grant another class's skills.

Combat Mastery is separate from Survival Skill Mastery:

- Combat Mastery advances through valid combat contribution.
- Survival Skill Mastery advances through gathering and survival activities.
- The two systems use separate IDs, trees, caps, events, and balance budgets.

## 2. Character Build Model

A combat build contains:

    active weapon
    permanent character class
    weapon family
    weapon type
    basic attack
    weapon skill slot 1
    weapon skill slot 2
    armor or utility skill
    passive specialization
    consumable
    equipment-derived attributes

The server derives a read-only `BuildSnapshot` from authoritative equipment,
unlocks, cooldowns, status effects, and content revision. A client cannot submit
its own attributes, mastery rank, role, or skill effects.

### 2.1 Build identity

The UI shows the permanent class and may also show a descriptive build title
such as `Flame Vanguard`, `Arcane Scholar`, or `Shadow Duelist`. The title is
presentation metadata derived from tags and grants no hidden statistics.

Example:

    Broadsword
    + counter specialization
    + medium armor
    + mobility utility
    + Warrior
    = Flame Vanguard presentation

Changing the loadout recalculates the title and effects from the new immutable
snapshot.

### 2.2 Role model

Roles describe build capabilities inside the permanent class:

    DAMAGE
    TANK
    SUPPORT
    CONTROL
    HYBRID

A build may have several role weights. Class weapon/skill restrictions remain
authoritative even when a build has unconventional role weights.

## 3. Combat Mastery Hierarchy

Combat progression has three bounded layers:

    weapon family mastery
      -> weapon type mastery
         -> optional skill proficiency

Example:

    sword
      -> broadsword
         -> flame counter

### 3.1 Weapon family mastery

Family mastery is shared across related weapons and represents transferable
knowledge. It unlocks core mechanics and broad specialization choices.

Examples:

- Sword
- Bow
- Staff

### 3.2 Weapon type mastery

Weapon type mastery advances one combat style within a family.

Examples:

- Sword → Broadsword
- Sword → Greatsword
- Staff → Fire Staff
- Staff → Healing Staff

It unlocks type-specific skills, passive nodes, and utility. Item rarity alone
does not grant mastery levels.

### 3.3 Skill proficiency

Skill proficiency is optional. It may unlock variants, cosmetics, quality-of-life
effects, or small bounded specialization bonuses. Repeated skill use cannot
become an unlimited damage multiplier.

## 4. Combat Mastery XP

XP is awarded for valid server-confirmed contribution, not input count.

Eligible contribution:

- Effective damage against an eligible hostile target
- Effective healing of eligible allies
- Damage prevented through shields or mitigation
- Valid crowd control and interrupts
- Encounter objectives
- Tanking or threat contribution where the encounter supports it

Ineligible contribution:

- Missed, cancelled, immune, blocked, or invalid skill attempts
- Overhealing with no effective health restored
- Damage against non-progression training targets
- Friendly, owned, synthetic, or administratively spawned targets unless enabled
- Replaying the same operation or hit ID
- Input spam without an authoritative combat result

### 4.1 XP calculation

    awarded_xp = floor(
        base_contribution_xp
        * target_tier_multiplier
        * encounter_multiplier
        * anti_farm_multiplier
        * participation_multiplier
    )

The result must be finite, non-negative, bounded per combat result, and committed
with an idempotent operation ID.

XP is divided by contribution policy between:

- Weapon family mastery
- Weapon type mastery
- Optional skill proficiency

The split percentages are content-driven and must total 100%.

### 4.2 Anti-farm rules

- Repeated trivial targets receive deterministic decay.
- The same target death or encounter completion resolves mastery rewards once.
- Training dummies grant zero progression by default.
- Summoned, owned, rapidly respawning, or colluding targets may be excluded.
- Participation must exceed configured minimum contribution.
- Disconnect/reconnect cannot reset target or encounter decay.
- Party contribution cannot be duplicated by changing party membership.

Suppressed XP records an internal reason for audit and staff inspection.

## 5. Levels, Power Budget, and Unlocks

Combat Mastery uses cumulative total-XP thresholds with checked 64-bit
arithmetic. Curves and level caps are content-driven.

Suggested initial family curve:

    total_xp_required(level) = round(100 * level ^ 1.65)

Mastery unlocks mechanics early. Higher levels primarily unlock specialization,
sidegrades, prestige, cosmetics, and small bounded bonuses.

The maximum same-tier combat-power difference attributable to Combat Mastery
targets 15–25% between a new eligible user and a fully mastered user. Exact
budgets are documented per attribute/effect and validated during content load.

Levels and unlocked nodes never decrease on death. Death penalties may affect a
separate configured recoverable progress pool, but never total earned mastery,
level, unlocked skills, or purchased nodes.

## 6. Combat Mastery Tree

Each weapon family and weapon type may define a directed acyclic mastery tree.
Nodes contain stable IDs, ranks, point costs, level requirements, prerequisites,
and declarative bounded effects.

Example Broadsword tree:

    Broadsword
    ├─ Duelist
    │  ├─ Precise Counter
    │  └─ Riposte Momentum
    ├─ Vanguard
    │  ├─ Guarded Advance
    │  └─ Stagger Resistance
    └─ Flamebound
       ├─ Ember Edge
       └─ Controlled Burn

Branch names are specializations inside the permanent class. A player may
respec according to policy or level another class-compatible weapon type.

Tree effects cannot:

- Execute arbitrary Java classes or console commands
- Bypass safe zones, equipment rules, or target validation
- Reset cooldowns by moving items
- Grant unbounded attack speed, cooldown recovery, damage, healing, or control
- Mint items or currency directly

Tree revision and migration follow the same safe migration rules as Survival
Skill trees: aliases for renamed nodes, explicit removed-node policy,
idempotent refunds, no partial migration, and failed reload retaining the prior
content snapshot.

## 7. Loadout Rules

- One active weapon is supported for the initial release.
- Only unlocked and content-valid skills may be equipped.
- Loadout changes are blocked during combat, casting, death handling, and other
  configured locked states.
- Cooldowns, statuses, costs, and mastery state are keyed independently of the
  physical item slot and cannot be reset by item movement.
- Equipping a two-handed weapon reserves the off-hand slot.
- Invalid or missing definitions fail closed and report exact diagnostics.
- A loadout swap is atomic: remove old effects, validate and apply the new
  snapshot, persist, then publish the change.
- Failure restores the previous valid loadout without duplicated modifiers.

## 8. Attributes and Effect Application

Combat Mastery never mutates final Paper attributes directly. Tree and mastery
effects produce stable modifiers for the Core attribute engine.

Every modifier includes:

    modifier ID
    source mastery or node ID
    operation
    value
    stacking group
    priority
    optional cap

Rebuilding a loadout removes modifiers by stable source ID before applying the
new snapshot. Login, reload, respec, equipment swap, and repair must therefore
be repeatable without stacking duplicates.

## 9. Persistence

Family and type progress is stored separately:

    player_uuid
    mastery_id
    mastery_kind
    level
    total_xp
    unspent_points
    schema_version
    tree_revision
    updated_at

Node ranks use `(player_uuid, mastery_id, node_id)`. Optional skill proficiency
uses a separate skill ID so replacing a weapon item does not erase progression.

XP grants, level changes, point grants, node purchases, respecs, and loadout
changes are transactional, idempotent, and audited. Database failure follows the
fail-closed player-session policy.

## 10. API and Events

Public immutable values:

- `CombatMasterySnapshot`
- `CombatMasteryTreeSnapshot`
- `CombatMasteryNodeSnapshot`
- `BuildSnapshot`
- `CombatContribution`
- `CombatMasteryGrantResult`

All persistent events use the common domain-event envelope.

| Event | Payload |
|---|---|
| CombatMasteryXpGranted | Player, mastery ID/kind, source, contribution, awarded XP, resulting total |
| CombatMasteryLevelChanged | Player, mastery ID/kind, old/new level, points granted |
| CombatMasteryNodeUnlocked | Player, mastery ID, node, old/new rank, points remaining |
| ActiveBuildChanged | Player, previous/new build revision, weapon and equipped skill IDs |

Events publish only after the authoritative transaction commits. Consumers
deduplicate by event ID.

## 11. Player UI and Administration

The player UI shows:

- Current weapon family and type mastery
- Total XP, level progress, and available points
- Mastery-tree branches and prerequisites
- Equipped skills and locked-state reason
- Descriptive build title and role weights

Required admin commands:

    /branz combat-mastery inspect <player> [mastery]
    /branz combat-mastery grant-xp <player> <mastery> <amount> <reason>
    /branz combat-mastery tree <player> <mastery>
    /branz combat-mastery reset <player> <mastery> <reason>
    /branz build inspect <player>
    /branz build repair <player> <reason>

All mutations require permission, reason, operation ID, and audit record.

## 12. Performance

- Formula, unlock, and modifier resolution remain pure Java.
- Build recalculation targets under 1 ms at p95 for the initial content set.
- Normal combat ticks do not rebuild an unchanged mastery tree or loadout.
- XP events are coalesced for UI but every economic/progression mutation remains
  recoverable and auditable.
- No SQL, filesystem access, or content parsing occurs on a Paper tick thread.
- Soak tests include 50 concurrent players, rapid weapon swaps outside combat,
  contribution events, reconnects, reloads, and duplicate operation delivery.

## 13. Acceptance Criteria

- A new player must select Warrior, Mage, or Rogue before combat progression.
- A player can equip any valid unlocked starter weapon allowed by that class.
- Switching weapon type changes the active build without deleting prior mastery.
- Switching loadout cannot grant skills or weapons restricted to another class.
- Valid damage, healing, mitigation, control, and objective contribution award
  configured mastery XP.
- Misses, invalid targets, cancelled effects, and input spam award zero XP.
- One combat result or encounter completion cannot grant mastery twice.
- Family, type, and optional skill XP splits total exactly 100%.
- Mastery level, points, unlocks, and tree purchases are transactional.
- Equipment movement cannot reset cooldowns or duplicate modifiers.
- Loadout failure restores the previous valid build.
- Tree validation rejects cycles, broken prerequisites, and unbounded effects.
- Death never reduces total earned mastery, level, or unlocked nodes.
- Quest can query or grant typed Combat Mastery XP without importing Core.
- Core rules pass pure Java tests and Paper adapters pass loadout/combat smoke tests.
