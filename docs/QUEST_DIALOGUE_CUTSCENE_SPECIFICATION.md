# Branz MMORPG — Quest, Dialogue, and Cutscene Specification

Status: Workstream contract  
Owner: Quest/content-tooling developer  
Out of scope: Combat formulas, item ownership, mastery calculation, mob AI, and economy implementation  
Dependency rule: Quest depends on API ports and events, never Core implementation.

## 1. Product goals

1. Content designers create and update quests without writing Java.
2. Quest, dialogue, and cutscene definitions are validated before activation.
3. Runtime is deterministic, resumable, versioned, and safe under retries.
4. Dialogue supports Wynncraft-inspired portrait presentation, manual advance, choices, and history.
5. Cutscenes use a data-driven timeline with skip, cleanup, and disconnect recovery.
6. Engine remains playable without ModelEngine, a custom resource pack, or Branz Idle.
7. A broken content reload never corrupts active or completed progress.
8. Gameplay systems are connected through ports/events so both developer workstreams can proceed independently.

## 2. Target module layout

    mmorpg-quest-api
    mmorpg-quest-core
    mmorpg-quest-storage
    mmorpg-quest-paper

Dependencies:

    quest-api -> mmorpg-api
    quest-core -> quest-api + mmorpg-content
    quest-storage -> quest-api + mmorpg-storage
    quest-paper -> quest-core + quest-storage + Paper API

The root Paper plugin installs adapters and lifecycle wiring only.

## 3. Terminology

| Term | Meaning |
|---|---|
| Definition | Immutable content loaded from YAML |
| Quest progress | Persistent per-player state for one quest |
| Stage | Active section of a quest |
| Objective | Measurable requirement within a stage |
| Action | Idempotent command issued by Quest |
| Condition | Pure query with no mutation |
| Dialogue session | Runtime traversal through a dialogue graph |
| Cutscene session | Runtime playback of a timeline |
| Content revision | Immutable definition snapshot used by a session |
| Operation ID | Stable ID used to deduplicate mutations and rewards |

## 4. Global invariants

- A player has at most one active progress record per quest ID.
- Completed non-repeatable quests cannot restart without an audited admin reset.
- Objective progress never exceeds its validated target.
- Rewards resolve at most once.
- Conditions are pure and cannot modify game state.
- Actions are typed and allowlisted; arbitrary console commands and Java class loading are forbidden.
- Every active session has player UUID, session ID, content revision, and start timestamp.
- Definition IDs are permanent after release or require migration/alias.
- Player-facing text is localization-key compatible.

# Phase Q0 — Module and contract foundation

## Scope

- Create four Quest modules.
- Define pure Java API values and ports.
- Provide fake in-memory implementations for Core and storage.
- Establish lifecycle and health reporting.

## API contracts

Required ports:

    QuestGamePort
    QuestProgressStore
    QuestEventSource
    QuestEventPublisher
    QuestScheduler
    QuestAudience
    QuestActorPort
    QuestCameraPort
    QuestLocationPort

Paper or Core objects may not cross these interfaces.

## Deliverables

- Quest service lifecycle.
- Operation ID and domain event IDs.
- Deterministic clock/scheduler test fixtures.
- Error/result taxonomy.
- Shared package ownership documentation enforced by dependency tests.

## Acceptance

- Quest Core runs entirely in unit tests with fake ports.
- Quest modules compile while mmorpg-core contains no implementation.
- No Quest module imports com.branz.mmorpg.core.

# Phase Q1 — Quest content schema and compiler

## Quest definition

Required top-level fields:

    id
    version
    title
    description
    category
    repeat-policy
    requirements
    start-trigger
    start-stage
    stages
    rewards
    migration-policy

Optional metadata:

    recommended-mastery
    region
    estimated-duration
    storyline
    tags
    icon
    tracking-priority

## Stage definition

    id
    activation-actions
    objectives
    completion-policy
    completion-actions
    next
    failure
    checkpoint

Completion policies:

    ALL
    ANY
    COUNT
    EXPRESSION

Expression support is a constrained typed expression tree, not an arbitrary script.

## Compiler pipeline

    scan files
    -> parse YAML
    -> schema validation
    -> local semantic validation
    -> resolve references
    -> graph validation
    -> capability validation
    -> immutable compiled catalog
    -> atomic activation

## Required validation

- Missing/duplicate quest, stage, objective, dialogue, action, or choice ID.
- Unknown type or field.
- Invalid ContentId.
- Missing start stage.
- Unreachable stage.
- Unconditional cycle without bounded/repeat intent.
- Stage with no completion path.
- Unknown item, mob, skill, mastery, region, NPC, dialogue, or cutscene reference.
- Invalid reward amount.
- Non-idempotent action in a retryable lifecycle point.
- Repeatable quest with non-repeat-safe reward.
- Content capability unsupported by installed adapter.

## Diagnostics

Every diagnostic includes:

    severity
    stable error code
    source file
    line/column where available
    content ID
    field path
    human-readable resolution

## Acceptance

- Valid and invalid fixtures cover every validation category.
- Compilation order does not change output.
- Failed candidate does not replace active catalog.
- Compiled definitions are immutable.

# Phase Q2 — Quest runtime state machine

## Quest states

    LOCKED
    AVAILABLE
    ACTIVE
    READY_TO_TURN_IN
    COMPLETING
    COMPLETED
    FAILED
    ABANDONED
    MIGRATION_REQUIRED

Not every state is persisted for every quest, but transitions are explicit.

## Transition command

Every mutation is represented by:

    command ID
    player UUID
    quest ID
    expected progress revision
    command type
    payload
    timestamp

Result:

    APPLIED
    ALREADY_APPLIED
    REJECTED
    CONFLICT
    RETRYABLE_FAILURE
    PERMANENT_FAILURE

Optimistic progress revision prevents concurrent lost updates.

## Stage lifecycle

    enter stage
    -> run activation actions
    -> subscribe/index objectives
    -> process eligible events
    -> evaluate completion
    -> run completion actions
    -> transition next stage or ready-to-turn-in

Actions that can fail create pending operations. The state machine must not
advance past a required failed action.

## Repeat policies

    ONCE
    REPEATABLE
    DAILY
    WEEKLY
    SEASONAL

Period keys use server-configured timezone and an injectable game clock.
Repeat progress is stored by occurrence ID; changing wall time backward cannot duplicate rewards.

## Abandon/reset

- Abandon is permitted only when definition allows it.
- Quest-owned temporary items and actors use tagged cleanup actions.
- Reset is an audited admin operation.
- Resetting one quest cannot blindly delete shared items or flags.

## Acceptance

- Every legal and illegal transition tested.
- Duplicate/out-of-order events do not over-progress.
- Concurrent commands resolve by optimistic revision.
- Runtime contains no Paper types.

# Phase Q3 — Objective engine

## Objective contract

Each objective type provides:

    validate definition
    create initial state
    declare event interests
    reduce event into new state
    test completion
    produce player-facing progress

Objective reducers are deterministic and side-effect free.

## Initial objective types

| Type | Event/query |
|---|---|
| talk | Dialogue/NPC completion |
| kill | MobKilled |
| defeat-boss | BossDefeated |
| collect | ItemAcquired or authoritative inventory query |
| possess | QuestGamePort inventory query |
| consume | Explicit completion action, not ItemRemoved counting alone |
| interact | WorldObjectInteracted |
| enter-region | RegionEntered |
| use-skill | SkillUsed with valid effect |
| craft | CraftCompleted |
| reach-mastery | MasteryChanged or current snapshot |
| wait | QuestScheduler/game clock |
| choose | Dialogue choice result |

## Collection semantics

Collect and possess are distinct:

- Collect counts eligible acquisitions after objective activation.
- Possess checks current authoritative inventory.
- Consume atomically removes items during stage completion.

Definitions specify accepted sources if required. Admin-granted or traded items
may be accepted or rejected by policy.

## Party credit

Objective definition selects:

    PERSONAL
    PARTY_IN_RANGE
    ENCOUNTER_ELIGIBLE
    PARTY_SHARED

Eligibility is determined from the immutable event payload, not current party
membership after the event.

## Event indexing

Objectives are indexed by event type and relevant content ID. Processing one
mob kill must not scan every quest on the server.

## Acceptance

- Each objective type has golden reducer tests.
- Event deduplication tested.
- Active objective lookup remains bounded with large synthetic catalogs.

# Phase Q4 — Condition and action registry

## Conditions

Initial pure conditions:

    quest state/stage/completion
    flag value
    item possession
    mastery level
    player/world/region
    permission/entitlement
    time window
    party size
    content unlock
    boolean all/any/not

Condition evaluation returns:

    TRUE
    FALSE
    UNAVAILABLE
    ERROR

UNAVAILABLE covers an optional subsystem not installed. Definitions must declare
whether unavailable means false or is a compile-time error.

## Actions

Initial actions:

    set/remove flag
    start/advance/complete quest
    grant/take item
    grant currency
    grant mastery XP
    unlock content
    start dialogue
    start cutscene
    activate tracker
    teleport through approved location ID
    spawn/despawn quest actor
    play sound/effect

## Action properties

Every action type declares:

- Validation schema
- Idempotency behavior
- Retry safety
- Whether it requires Paper thread
- Whether it is reversible
- Failure policy

Action chains support:

    REQUIRED_SEQUENCE
    BEST_EFFORT_SEQUENCE

No distributed rollback is claimed across world presentation and SQL. Required
economic mutations use pending operations/outbox; presentation is recoverable cleanup.

## Acceptance

- Unknown actions fail compilation.
- Retry applies economic result once.
- Required failure blocks transition.
- Best-effort presentation failure is logged without duplicating rewards.

# Phase Q5 — Quest persistence and migration

## Tables

    quest_progress
      player_uuid
      quest_id
      definition_version
      progress_revision
      state
      stage_id
      occurrence_id
      objective_state_json
      flags_json
      started_at
      updated_at
      completed_at

    quest_processed_events
      player_uuid
      event_id
      processed_at
      expiry_bucket

    quest_pending_operations
      operation_id
      player_uuid
      quest_id
      operation_type
      payload_json
      state
      attempts
      next_attempt_at
      last_error

    quest_history
      player_uuid
      quest_id
      occurrence_id
      outcome
      started_at
      completed_at

JSON stores bounded polymorphic objective state; indexed query fields remain columns.

## Save policy

- State mutation and pending operation creation share a transaction where possible.
- Runtime may cache active progress but database remains recovery authority.
- Logout flush is bounded.
- Startup resumes pending operations.
- Processed event retention is bounded by occurrence/expiry rules.

## Definition migration

Definition changes are classified:

    SAFE
    REQUIRES_MAPPING
    BREAKING

Safe:

- Text change
- Metadata change
- Addition of unreachable future stage

Mapping required:

- Stage/objective ID rename
- Objective target/type change
- Graph route replacement

Breaking:

- Removal of active state without mapping
- Semantic replacement that cannot preserve progress

Migration definition maps old version and stage/objective state to a new version.
Failure places progress in MIGRATION_REQUIRED; it never resets silently.

## Admin recovery

    /branz quest inspect <player> <quest>
    /branz quest migrate <player> <quest>
    /branz quest stage <player> <quest> <stage>
    /branz quest reset <player> <quest>
    /branz quest retry-reward <operation-id>

All mutations require reason and audit.

## Acceptance

- MySQL Testcontainers covers migrations and repositories.
- Crash between progress update and reward delivery recovers without duplication.
- Definition migrations have fixtures for every shipped version transition.

# Phase Q6 — Dialogue graph engine

## Node types

    line
    choice
    condition
    action
    wait
    jump
    end

Dialogue definition:

    id
    version
    start node
    nodes
    presentation defaults
    interruption policy
    history policy

Line node:

    node ID
    speaker/NPC ID
    portrait expression
    localization key/text
    voice/sound hook
    advance mode
    next

Choice:

    choice ID
    text
    conditions
    disabled reason
    actions
    next
    history behavior

## Session

    session ID
    player UUID
    dialogue ID/version
    current node
    visited nodes
    selected choices
    content revision
    sequence number
    started/last-active time

Client input includes session ID and expected sequence number. Repeated Sneak,
button, or packet input cannot advance multiple nodes.

## Advance modes

    MANUAL
    AUTO_AFTER_DURATION
    EXTERNAL_SIGNAL
    CHOICE

Accessibility settings:

- Manual, auto, or fast mode.
- Text speed.
- Allow skip for previously read dialogue.
- Portrait/VFX intensity.
- Sound/subtitle alternatives.

Sneak advance has edge detection and configurable debounce. Holding Sneak does
not traverse multiple nodes.

## Interruption

Definition chooses:

    CANCEL_ON_MOVE
    CANCEL_ON_DISTANCE
    PAUSE_ON_COMBAT
    CANCEL_ON_COMBAT
    BLOCKING

Death, teleport, world change, logout, NPC removal, reload, and shutdown each
have explicit pause/cancel/resume behavior.

## Acceptance

- Graph validation catches dead ends and cycles.
- Choice conditions/actions and stale input tested.
- Dialogue can run headlessly with a recording audience.

# Phase Q7 — Dialogue presentation and history

## Renderer abstraction

    DialogueRenderer
      show line
      show choices
      show waiting
      clear

Implementations:

1. Basic renderer: Adventure title/subtitle/action bar/chat.
2. Wynn-inspired renderer: resource-pack font glyph, frame, portrait, speaker, prompt.
3. Vanilla Dialog renderer: confirmation/multi-action choices where appropriate.

The engine does not know renderer technology.

## Presentation rules

- Normal conversation keeps the world visible.
- Modal Vanilla Dialog is reserved for choices, confirmation, and complex input.
- Combat dialogue uses non-modal subtitle/action bar.
- UI scale and portrait visibility are configurable.
- If the resource pack is unavailable, the basic renderer remains functional.

## History

History record:

    player UUID
    dialogue ID/version
    node ID
    speaker ID
    selected choice ID if any
    timestamp

History is bounded and paginated. Important storyline text may store a rendered
snapshot; other history resolves localization from the compatible definition version.

## Acceptance

- Renderer contract tests.
- Basic renderer is launch-required.
- Resource-pack renderer failure falls back without losing session state.
- History remains readable after compatible text updates.

# Phase Q8 — NPC and world-object integration

## NPC abstraction

NPC definition contains:

    NPC ID
    display name
    base appearance
    interaction radius
    dialogue/quest offers
    marker policy
    spawn/location policy
    animation hooks

NPC actor ID is stable content identity; runtime entity UUID may change.

## Interaction

    Paper interaction
    -> resolve actor/object ID
    -> validate player/session/range/world
    -> resolve prioritized offer
    -> start dialogue/quest through runtime command

Offer priority:

1. Active quest continuation/turn-in.
2. Main storyline availability.
3. Side quest availability.
4. Contextual ambient dialogue.

## Markers

Marker states:

    AVAILABLE
    IN_PROGRESS
    READY_TO_TURN_IN
    COMPLETED/AMBIENT

Markers are viewer-specific. Presentation may use displays, packets, resource-pack
icons, or fallback particles without changing quest logic.

## World objects and regions

- Objects use stable configured IDs, not raw block coordinates in quest definitions.
- Location registry maps stable ID to world/position/rotation.
- Region adapter emits entry transitions with hysteresis to avoid boundary spam.
- Admin tools capture and validate locations.

## Acceptance

- Duplicate click, NPC despawn, chunk unload, distance, party, and marker visibility tested.
- Quest remains independent of Citizens/ModelEngine.

# Phase Q9 — Quest tracker, journal, and discovery UI

## Tracker

- Player selects one primary tracked quest; optional secondary objectives are configurable.
- Updates are event-driven and coalesced.
- Tracker shows objective text, progress, direction/region when available, and next action.
- Hidden objectives do not leak content.
- Unavailable path reports a useful reason.

## Journal

Sections:

    recommended
    available
    active
    ready to turn in
    completed
    dialogue history
    lore/codex

Filters:

    storyline
    side
    region
    mastery recommendation
    completed

## Navigation

Navigation points to stable location/region IDs. It does not promise exact
pathfinding. Cross-world destination shows route/portal hint rather than a false direction.

## Acceptance

- UI pagination and stale-click tokens tested.
- Quest progress is readable without resource pack.
- Tracker does not perform full catalog scans per tick.

# Phase Q10 — Cutscene timeline engine

## Definition

    id
    version
    scope
    skippable policy
    setup
    actors
    timeline tracks
    checkpoints
    final state
    skip state
    cleanup
    disconnect recovery

Scope:

    PRIVATE
    PARTY
    PUBLIC
    INSTANCE

## Session state

    PREPARING
    PLAYING
    PAUSED
    SKIPPING
    COMPLETING
    CLEANING
    COMPLETE
    FAILED

Timeline time uses a monotonic cutscene clock. Lag may delay presentation but
must preserve action ordering. Actions at the same timestamp use explicit track priority.

## Initial track/action types

Camera:

    cut
    move
    look-at
    follow
    orbit
    shake
    fade
    restore

Actor:

    spawn
    despawn
    move
    teleport
    look-at
    equip
    animate
    visible
    speak

World/presentation:

    sound
    particle
    display
    client-side block change
    door/light hook

Player:

    freeze input policy
    invulnerability token
    hide/show participants
    teleport
    dialogue
    objective/action signal

## Camera

Camera is an adapter. Preferred implementation uses a private camera entity or
supported Paper/client mechanism. Packet/NMS code is isolated and version-specific.
Failure restores normal player camera.

## Final-state requirement

Every cutscene explicitly defines:

- State after normal completion.
- State after skip.
- State after disconnect/rejoin.
- Cleanup of actors, camera, visibility, invulnerability, movement locks, scheduled work, and client-side changes.

Skip does not merely cancel tasks. It applies the canonical skip/final state and
then runs idempotent cleanup.

## Acceptance

- Deterministic timeline tests with virtual clock.
- Skip at every action boundary reaches a valid final state.
- Disconnect, death, world unload, plugin disable, actor failure, and renderer failure tested.
- Cleanup may run multiple times safely.

# Phase Q11 — Party choices and private/instance scenes

## Party dialogue

Policies:

    LEADER_DECIDES
    MAJORITY_VOTE
    UNANIMOUS
    INDIVIDUAL_BRANCH

Vote definitions specify timeout and tie behavior. Eligibility snapshot is
captured at vote start; late members do not alter the vote.

## Private actors

- Viewer-scoped actors never mutate public world state.
- Actor identity remains stable across respawn.
- Private block changes are restored on session end/rejoin.
- Fallback presentation remains available if packet adapter fails.

## Instance coordination

Quest requests an instance through a port. It does not own dungeon/encounter
internals. Instance ID and participant eligibility are persisted when recovery requires it.

## Acceptance

- Party leave/join/leader change/timeout/disconnect tested.
- Two players can run different private states at the same world location.

# Phase Q12 — Authoring and administration tools

## Commands

    /branz quest validate [id]
    /branz quest reload
    /branz quest list-errors
    /branz quest preview <id>
    /branz quest start <player> <id>
    /branz quest stage <player> <id> <stage>
    /branz quest objective <player> <id> <objective> <value>
    /branz dialogue preview <id> [node]
    /branz cutscene preview <id>
    /branz cutscene seek <time>
    /branz cutscene skip

Preview actions are marked synthetic and cannot grant production rewards.

## Director Wand

Capabilities:

- Capture location and rotation.
- Select NPC/world object.
- Create named marker.
- Capture camera keyframe.
- Preview actor path.
- Open context editor.

Captured content is written to a staging area, validated, and explicitly promoted.
The live catalog is never partially edited.

## Location/actor tools

    create/rename/delete marker
    capture current transform
    show marker particles
    validate world and bounds
    export YAML fragment

Destructive rename/delete checks references before applying.

## Acceptance

- Preview cannot mutate real quest rewards/progress unless explicitly run as a player test.
- Every admin mutation is audited.
- Content reload reports file and field diagnostics.

# Phase Q13 — Visual local editor

This phase begins only after YAML schemas and timeline semantics are stable.

## Architecture

- Local-only editor.
- Reads/writes the same typed schema as manual YAML.
- Does not introduce a second content format.
- Communicates with local server through an authenticated development-only channel.
- Production server does not expose editor endpoints.

## Views

- Quest stage graph.
- Dialogue node graph.
- Cutscene multi-track timeline.
- Actor/location browser.
- Validation panel.
- Localization preview.
- Diff before save/promote.

## Acceptance

- Round-trip does not lose unknown supported fields or reorder semantics.
- Generated content passes the normal compiler.
- Editor cannot bypass validation or promotion workflow.

# Phase Q14 — Localization, accessibility, analytics, and QA

## Localization

- Player-facing text uses localization keys.
- Default locale is complete and validated.
- Placeholder names/types are checked at compile time where possible.
- Fallback chain is deterministic.
- Content IDs are not translated.

## Accessibility

- Manual/auto/fast dialogue modes.
- Adjustable text timing.
- Portrait/VFX intensity.
- Skip for previously read eligible scenes.
- Sound events have visual/text alternative where important.
- No essential information depends on color alone.

## Analytics

Track:

- Quest started/completed/abandoned.
- Stage duration and common failure/drop-off.
- Objective event volume.
- Choice distribution.
- Dialogue skip/advance mode.
- Cutscene completion/skip/failure.
- Pending reward retry/failure.

Analytics never changes quest outcome.

## QA tooling

- Export a quest path report.
- Detect unreachable content.
- Simulate conditions and branches.
- Batch migrate fixture player states.
- Generate checklist of external references and required content.

## Acceptance

- Every shipped quest has automated compile tests and a QA path checklist.
- Accessibility settings persist per player.
- Analytics failure cannot block progress.

# Phase Q15 — Hardening and release gate

## Required test suites

- Compiler fixture suite.
- State-machine/property tests.
- Objective reducer tests.
- Duplicate and out-of-order event tests.
- MySQL migration/repository tests.
- Pending reward fault injection.
- Dialogue stale-input and interruption tests.
- Cutscene virtual-time, skip, cleanup, and recovery tests.
- Paper NPC/UI smoke test.
- Performance test with large quest catalog and many active objectives.

## Performance targets

- Objective event routing uses indexed candidates, not full scans.
- Normal event processing target under 0.25 ms p95 per event before storage I/O.
- No per-player/per-quest repeating Paper task.
- Dialogue/cutscene scheduling uses centralized bounded queues.
- UI updates are coalesced.

## Release criteria

- No known reward duplication, silent reset, stuck movement/camera, unrecoverable migration, or main-thread I/O defect.
- Active progress survives restart and compatible content update.
- At least one complete reference quest validates all launch primitives:

      NPC dialogue
      choice
      region
      interaction
      kill objective
      item possession/consumption
      boss completion
      reward
      dialogue history
      skippable cutscene

- Operational runbook covers invalid content, stuck progress, pending reward, failed cutscene, and rollback to previous content snapshot.
- gradlew clean test shadowJar passes from a clean checkout.

## Explicitly deferred

- AI-generated production dialogue.
- Arbitrary scripting language.
- Remote/cloud authoring service.
- Cross-server quest sessions.
- Mandatory voice acting.
- Quest dependency on Branz Idle.
