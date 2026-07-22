# Branz MMORPG — Core MMO Specification

Status: Workstream contract  
Owner: Core MMO developer  
Out of scope: Quest, dialogue, cutscene, and quest authoring tools  
Dependency rule: Core publishes services/events; it does not depend on Quest.

## 1. Product principles

1. Standalone classless action MMORPG.
2. Weapon choice defines primary combat identity.
3. Using a weapon or skill advances its mastery.
4. Active play always provides a complete source for every required material.
5. Branz Idle is optional and may only provide alternative crafting materials through a future bridge.
6. Server is authoritative for stats, combat, item ownership, rewards, and progression.
7. Definitions and balance are data-driven; valuable state is transactional.
8. No required Redis, MongoDB, proxy, or external web service for the local launch target.

## 2. Target module layout

    mmorpg-api
    mmorpg-content
    mmorpg-storage
    mmorpg-core
    mmorpg-paper

The owner may create internal packages or modules, but public contracts remain
in mmorpg-api and Paper types remain in mmorpg-paper.

## 3. Global non-functional requirements

- Main-thread work attributable to MMO systems targets under 4 ms at p95 with 50 concurrent players.
- No SQL, filesystem, JSON, or YAML parsing on a Paper tick thread.
- Runtime calculations are deterministic for the same inputs and random seed.
- Every economic mutation is idempotent and auditable.
- Shutdown refuses new mutations, drains bounded work, flushes dirty sessions, then closes storage.
- Player load failure fails closed: gameplay mutation is disabled, never replaced with a blank profile.
- Content reload failure retains the previous snapshot.
- Public API uses immutable values and does not expose mutable internal collections.

# Phase C0 — Foundation adoption

## Scope

- Adopt Java 25, Paper 26.2, Gradle 9.1, MySQL 8, HikariCP, and Flyway.
- Retain dependency direction documented in DEVELOPMENT_OWNERSHIP_AND_CONTRACTS.
- Replace the legacy MMOPlayer shape before it becomes public compatibility debt.
- Establish service lifecycle: NEW, STARTING, READY, STOPPING, STOPPED, FAILED.
- Establish structured error codes and operation IDs.

## Deliverables

- Core service container/bootstrap.
- Clock, random source, ID generator, scheduler, and transaction abstractions.
- Health/status API.
- Admin status command integration.
- Test fixtures for deterministic clocks and random rolls.

## Acceptance

- Core can start and stop repeatedly in tests without leaked executors.
- Failure in a required service prevents READY state.
- Quest modules can compile against API without importing core implementation.
- Build gate passes.

# Phase C1 — Player profile and runtime session

## Model

Persistent profile:

    player_uuid
    last_known_name
    schema_version
    created_at
    last_seen_at
    selected_loadout_id
    respawn_point_id
    settings

Runtime session:

    player UUID
    monotonically increasing session token
    lifecycle state
    immutable profile snapshot
    mutable combat resources
    dirty components
    content revision

## Lifecycle

    ABSENT
      -> LOADING
      -> ACTIVE
      -> SAVING
      -> CLOSED

Failure states:

    LOAD_FAILED
    SAVE_RETRY_PENDING
    CONFLICTED

Only one ACTIVE session may exist for a UUID. Late callbacks must compare the
session token. Duplicate login either closes the previous session safely or
rejects the new one according to configuration.

## Persistence

- Profile creation uses insert-if-absent.
- Profile load is asynchronous.
- Periodic saving uses dirty-component snapshots, not one SQL update per tick.
- Logout save has bounded retry and durable recovery record.
- Profile schema version is explicit.
- Name is presentation metadata, never identity.

## Admin tools

    /branz player inspect <player>
    /branz player save <player>
    /branz player reload <player>
    /branz player repair <player>

Reload cannot discard dirty state without explicit confirmation.

## Acceptance

- New, returning, simultaneous, failed-load, failed-save, logout, kick, and shutdown cases tested.
- Blank-profile fallback is impossible.
- No Bukkit Player object is retained after logout.

# Phase C2 — Attribute and resource system

## Attribute model

An attribute value is resolved from:

    base
    + flat modifiers
    -> additive percentage group
    -> multiplicative modifiers
    -> clamp
    -> derived value

Every modifier contains:

    stable modifier ID
    source type and source ID
    operation
    value
    stacking group
    priority
    optional expiry

Required attributes:

- Maximum Health
- Maximum Mana
- Maximum Stamina
- Physical Power
- Magic Power
- Defense
- Magic Resistance
- Critical Chance
- Critical Damage
- Cooldown Recovery
- Movement Speed
- Attack Speed
- Healing Power
- Crowd-Control Resistance

## Resource rules

- Current HP, Mana, and Stamina are runtime values.
- Max-value reduction clamps current value; it does not preserve an invalid ratio unless configured.
- Regeneration uses elapsed server ticks and explicit combat/out-of-combat rules.
- NaN, infinity, negative maximums, and overflow are rejected.
- Every percentage stat has a documented cap.

Initial caps:

| Stat | Cap |
|---|---:|
| Critical Chance | 60% |
| Cooldown Recovery | 35% |
| Movement Speed bonus | 30% |
| Crowd-Control Resistance | 60% |

## Events

- AttributeChanged
- ResourceChanged
- ResourceDepleted
- ModifierAdded/Removed

Events are coalesced where appropriate to avoid HUD spam.

## Acceptance

- Modifier ordering and removal are deterministic.
- Equipment swap cannot duplicate modifiers.
- Expired modifiers are cleaned safely.
- Numeric property/fuzz tests cover invalid values.

# Phase C3 — Status effects

## Model

Status definitions specify:

- ID and display data
- positive, negative, or neutral category
- duration policy
- stack policy
- maximum stacks
- refresh policy
- periodic interval
- attribute modifiers
- dispel tags
- crowd-control category

Stack policies:

    UNIQUE
    REFRESH_DURATION
    ADD_STACK_REFRESH
    INDEPENDENT_STACKS
    REPLACE_WEAKER

Required initial effects:

- Burn
- Bleed
- Poison
- Slow
- Root
- Stun
- Silence
- Shield
- Regeneration
- Vulnerability

## Invariants

- Tick effects use a central scheduler/wheel, not one task per effect.
- Source attribution is retained for kill and contribution calculation.
- Offline duration behavior is explicit per definition.
- Crowd-control duration applies resistance once.
- Immunity prevents application and emits a rejected result.

## Acceptance

- Apply, refresh, stack, expire, cleanse, death, logout, reload, and immunity tested.
- Ten thousand synthetic effects can advance without unbounded task creation.

# Phase C4 — Combat engine

## Authoritative pipeline

    intent
    -> eligibility
    -> target validation
    -> hit resolution
    -> base power
    -> offensive modifiers
    -> critical
    -> mitigation
    -> shields
    -> health mutation
    -> threat/contribution
    -> events and feedback

Damage types:

    PHYSICAL
    MAGIC
    TRUE
    ENVIRONMENTAL

Defense formula:

    reduction = defense / (defense + K)

K is content-tier data. Reduction and minimum final damage are capped.

## Combat state

- Enter combat after dealing or receiving eligible combat effect.
- Exit after configurable inactivity with no hostile pending effect.
- Combat state controls regeneration, equipment swapping, logout behavior, and selected UI.
- Safe-zone rules are checked before damage mutation.

## Hit and target rules

- Server validates distance, world, line of sight, target state, faction, party, safe zone, and invulnerability.
- No client-reported damage is trusted.
- One cast cannot hit the same target more often than its definition permits.
- Projectile ownership survives deflection where applicable.
- Friendly fire is policy-driven.

## Death

- Core produces a structured death context.
- Default death does not drop MMO equipment.
- Mastery level never decreases.
- Any death penalty applies only to configured progress/resource and is idempotent.
- Respawn destination is resolved through a port so Quest may later influence it without Core depending on Quest.

## PvP

PvP coefficients and eligibility are separate from PvE. PvP may remain disabled
at launch, but the damage context must support it without branching the entire engine.

## Acceptance

- Formula golden tests.
- Hit deduplication, friendly fire, safe-zone, shield, death, simultaneous lethal damage, and disconnect tested.
- Combat engine can run as pure Java tests without Paper.

# Phase C5 — Skill execution engine

## Skill state machine

    READY
      -> CASTING
      -> ACTIVE
      -> RECOVERY
      -> COOLDOWN
      -> READY

Interrupt transitions may occur from CASTING or CHANNELING. Every cast has a
unique cast ID and immutable content revision.

## Definition

Skill definitions contain:

- Input slot and tags
- Cast/channel/recovery time
- Resource costs
- Cooldown and cooldown group
- Targeting shape
- Range and line-of-sight policy
- Effect graph
- Animation/VFX/sound hooks
- Interrupt policy
- Movement policy
- PvE/PvP coefficients

Initial effect nodes:

    damage
    heal
    apply_status
    remove_status
    dash
    knockback
    spawn_projectile
    area_query
    conditional
    sequence
    parallel

Definitions are declarative. Arbitrary Java class names or unrestricted console
commands are forbidden in content.

## Cooldowns and resources

- Costs are reserved/committed at the documented cast phase.
- Failed eligibility consumes nothing.
- Interrupted skill refunds according to definition.
- Cooldowns use monotonic time and remain tracked after item switching.
- Cooldown reduction observes caps and never creates zero/negative duration.

## Acceptance

- All state transitions tested.
- Spam, double input, item swap, death, stun, silence, logout, and reload tested.
- Effect graph validates cycles and unresolved references.

# Phase C6 — Weapon, loadout, and mastery

## Build structure

    Basic attack
    Weapon skill slot 1
    Weapon skill slot 2
    Armor/utility skill
    Passive specialization
    Consumable

Weapon hierarchy:

    family -> weapon type -> item variant

Example:

    sword -> broadsword -> flamebound_broadsword

Family mastery is shared within the family. Weapon-type mastery controls
type-specific unlocks. Skill proficiency is optional and cannot become an
unbounded damage multiplier.

## Mastery

- XP is awarded for valid contribution, not raw input count.
- Training dummy contribution can be marked non-progression.
- Repeated trivial targets receive an anti-farm multiplier.
- Support, healing, mitigation, control, and objective contribution are eligible.
- Level unlocks mechanics early; upper levels primarily specialize or prestige.
- Maximum same-tier combat power gap from mastery targets 15–25%.
- Unlocked levels never decrease on death.

Initial curve:

    required XP = round(100 * level^1.65)

Curve values remain data-driven.

## Loadout restrictions

- One active weapon for the initial release.
- Loadout changes are blocked in combat.
- Cooldowns and statuses cannot be reset through item movement.
- Invalid loadout fails closed and reports exact missing definitions.

## Reference content

- Broadsword: balanced counter/duelist.
- Bow: ranged positioning/precision.
- Fire Staff: area damage and Mana management.

Reference content validates the engine; it does not define the final quantity of weapons.

## Acceptance

- Mastery grant is idempotent.
- Anti-farm rules tested.
- Loadout persistence, validation, swap restrictions, and unlocks tested.
- Quest may query mastery through API without importing implementation.

# Phase C7 — Item, equipment, inventory, and loot

## Item categories

    MATERIAL
    WEAPON
    ARMOR
    ACCESSORY
    CONSUMABLE
    QUEST_TOKEN
    COSMETIC

Fungible materials use definition ID plus quantity. Unique equipment uses:

    item_instance_uuid
    definition_id
    quality/roll seed
    bound owner if any
    durability
    created source
    schema version

Minecraft ItemStack is a presentation token. PDC includes signed/validated IDs;
valuable ownership is reconciled with authoritative state.

## Equipment

Slots:

    main hand
    off hand
    helmet
    chest
    boots
    accessory 1
    accessory 2
    consumable

Two-handed weapons reserve the off-hand slot. Equip is a transaction:

    validate -> remove old modifiers -> apply item -> apply new modifiers -> persist -> publish

Rollback restores the previous valid loadout.

## Loot

Loot definition supports:

- Weighted entries
- Guaranteed entries
- Quantity ranges
- Conditions
- Pity/caps where required
- Personal or party ownership
- Contribution eligibility
- Source audit

Rolls resolve once using a stored roll ID. Reopening UI cannot reroll.

## Delivery

- Inventory space is checked.
- Overflow routes to mailbox/pending claim.
- Valuable drops are never spawned as untracked public entities by default.
- Pickup does not mint a second authoritative instance.

## Acceptance

- Duplication and fault-injection tests cover equip, drop, pickup, reconnect, overflow, and retry.
- Unknown/tampered PDC item is quarantined or converted according to policy.
- Future Idle bridge can request material creation through API, but Core has no Idle dependency.

# Phase C8 — Gathering, crafting, profession, and economy

## Gathering

Every required crafting material has at least one active MMO source:

- World resource node
- Mob family
- Dungeon/encounter
- Salvage
- Vendor or exchange where appropriate

Idle output is never the only source.

Gathering validates tool, region, cooldown, ownership, and depletion. Resource
nodes have server-authoritative respawn and anti-macro telemetry.

## Crafting

Recipe specifies:

- Inputs and quantities
- Optional catalysts
- Currency fee
- Station
- Profession requirement
- Duration if any
- Output
- Binding and quality policy

Craft transaction:

    validate recipe revision
    -> lock/consume inputs
    -> charge fee
    -> resolve output once
    -> persist ownership
    -> deliver or queue claim
    -> audit

No paid currency purchases mastery, random rolls, boss components, or a higher power ceiling.

## Professions

Initial candidates:

- Blacksmithing
- Alchemy
- Tailoring
- Cooking
- Enchanting

Profession XP comes from valid crafts with diminishing returns for trivial recipes.

## Economy

- Gameplay Coins and premium Credits are separate.
- Currency uses an immutable ledger.
- Negative balances are forbidden unless a specific currency supports debt.
- Vendor sources and sinks are tagged for telemetry.
- Player market is a later subphase and must use escrow.

## Acceptance

- Craft fault injection proves no input loss or output duplication.
- Every launch recipe has a complete MMO acquisition path.
- Material faucet/sink simulation is documented.

# Phase C9 — Mob and AI engine

## Definition

Mob definition includes:

- Base stats and scaling policy
- Faction and target policy
- Movement/navigation profile
- Abilities and weighted/conditional selection
- Aggro, leash, reset, and home region
- Status immunity/resistance
- Loot and contribution rules
- Presentation hooks

AI states:

    IDLE
    PATROL
    ALERT
    PURSUE
    CAST
    RECOVER
    RETREAT
    RESET
    DEAD

## Requirements

- AI decision frequency is bounded and separate from presentation tick rate.
- Pathfinding requests are rate-limited.
- Reset restores canonical state and grants no exploitative duplicate reward.
- Mob runtime holds definition revision.
- ModelEngine is an adapter; gameplay remains functional with a vanilla presentation.

## Acceptance

- Aggro, target switching, leash, reset, unload/reload, death, and reward tested.
- Mob engine degrades safely when presentation integration is unavailable.

# Phase C10 — Encounter, dungeon, and boss engine

## Encounter state

    WAITING
    PREPARING
    ACTIVE
    SUCCESS
    FAILED
    CLEANING
    CLOSED

Boss definitions compose:

- Phase conditions
- Ability sets
- Arena rules
- Spawned actors/adds
- Failure conditions
- Enrage/pressure
- Checkpoints if permitted
- Reward eligibility

## Contribution

Eligible contribution includes:

- Damage
- Effective healing
- Prevented damage
- Control/interrupt
- Encounter objective

Contribution has anti-tagging thresholds and party policy. Reward resolution
stores a unique encounter completion ID.

## Instance policy

- World/public encounter and private/party instance are distinct modes.
- Cleanup is idempotent.
- Player reconnect, party change, server shutdown, and abandoned instance have explicit recovery.
- Quest receives boss/encounter completion events but cannot mutate encounter internals.

## Acceptance

- At least one three-phase reference boss.
- Wipe/reset/reconnect/reward retry tested.
- Encounter cleanup leaves no actors, tasks, forced chunks, or player locks.

# Phase C11 — Social, party, trade, and market

## Party

- Stable party ID, leader, members, invitations, and revision.
- Reward range and contribution policy are explicit.
- Party changes during an encounter do not retroactively change eligibility.
- Cross-world and offline behavior documented.

## Direct trade

State:

    REQUESTED -> OPEN -> BOTH_CONFIRMED -> COMMITTING -> COMPLETE

Any offer mutation clears confirmation. Commit uses escrow/transaction and is
idempotent. Logout or timeout cancels safely.

## Market

Market is optional after direct trade is stable:

- Listing escrow
- Fee and expiry
- Atomic purchase
- Mailbox delivery
- Price/volume telemetry
- Admin freeze and investigation

## Acceptance

- Concurrency and fault-injection tests prevent dupes.
- Bound, quest, invalid, equipped, or locked items cannot be traded.

# Phase C12 — HUD, UI, administration, and operations

## HUD

- Action Bar: HP and active resource/status.
- Boss Bar: encounter objective/boss state.
- Item cooldown component or approved resource-pack surface.
- Updates are event-driven/coalesced, not blindly rendered every tick.
- UI has text/sound alternatives for important state.

## Administration

Required capabilities:

- Inspect player/session/stats/modifiers.
- Grant/revoke item, currency, mastery with audit reason.
- Spawn/inspect/reset mob and encounter.
- Validate/reload content.
- Inspect pending rewards/mailbox.
- Repair stuck equipment or transaction.

Destructive commands require confirmation or explicit identifiers.

## Telemetry

Track:

- Combat duration and deaths
- Skill usage/hit rate
- Mastery XP sources
- Material faucets/sinks
- Craft volume/failures
- Mob/encounter reset and reward failures
- Tick cost and queue depth

No secret or credential appears in logs.

## Acceptance

- All admin mutation paths are audited.
- HUD remains within performance budget.
- Operational runbook covers startup, shutdown, DB outage, bad content, and repair.

# Phase C13 — Hardening and release gate

## Required tests

- Unit and property tests for formulas.
- MySQL Testcontainers migrations and repositories.
- Fault injection around inventory/economy/reward operations.
- Concurrent login and mutation tests.
- Soak test with synthetic sessions, mobs, statuses, and skill casts.
- Paper smoke test.
- Content compatibility and reload test.

## Release criteria

- No open critical dupe, loss, corruption, privilege, or main-thread I/O issue.
- Database backup/restore rehearsal completed.
- Migrations validated on a copy of production-like data.
- Performance targets measured, not assumed.
- All public API changes documented.
- gradlew clean test shadowJar passes from a clean checkout.

## Explicitly deferred

- Redis and multi-server synchronization
- Cross-server party/market
- Mandatory ModelEngine/packet implementation
- Branz Idle bridge
- PvP ranking and seasons
- Guild/territory warfare
- Quest, dialogue, and cutscene implementation
