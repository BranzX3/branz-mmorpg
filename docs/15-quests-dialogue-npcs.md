# Quest, Dialogue, NPC and Cutscene Runtime

## Quest state

```text
LOCKED
AVAILABLE
ACTIVE
OBJECTIVE_COMPLETE
READY_TO_TURN_IN
COMPLETED
FAILED
ABANDONED
```

State changes are persisted and idempotent. A quest instance records definition version and migrates explicitly when content changes.

## Objective types

V1 supports reusable objective handlers:

- Kill target/tag
- Collect/possess item/material
- Reach region
- Interact with registered object
- Observe/inspect
- Dialogue choice/node
- Use technique/spell/form
- Complete training challenge
- Craft recipe/category
- Deliver item
- Defend target/area
- Escort authored NPC path
- Complete encounter/boss phase
- Ritual/puzzle signal

Objectives declare sharing, visibility, failure and reset policy.

## Quest items

Quest items are persistent item instances or virtual quest tokens according to definition.

- Physical quest items use normal ownership rules but are non-tradeable/non-salvageable.
- Virtual tokens are preferred when physical handling adds no gameplay value.
- Abandon behavior is explicit: remove, retain or archive.
- Completion consumes required items transactionally.

## Failure and retry

- Narrative quests do not fail permanently by default.
- Timed/escort/defense objectives may enter FAILED and offer retry from a checkpoint.
- Permanent branch choices may alter story flags but cannot permanently lock core weapon/magic families.
- Disconnect pauses or safely fails temporary scene objectives according to definition.

## Dialogue modes

- `AMBIENT` — non-blocking nearby line.
- `CONVERSATION` — player-focused branching dialogue.
- `CINEMATIC` — controlled presentation/camera when available.
- `COMBAT` — boss/encounter lines without blocking input.
- `WHISPER` — private narrative/system communication.

Dialogue nodes contain localized text keys, speaker, portrait/emotion, conditions, actions and choices. Logic uses condition/action registries, not embedded scripting code.

## Conditions

Standard conditions:

- quest/state/flag,
- technique/form knowledge,
- mastery/conditioning readiness band,
- faction reputation/renown,
- item possession/equipment,
- encounter completion,
- region/time/weather provider signal,
- party state,
- content migration flag.

Exact hidden values are not exposed in text conditions; use readiness bands.

## Actions

Standard actions:

- set flag,
- advance objective,
- start/complete/fail quest,
- grant reward through reward pipeline,
- start encounter,
- begin training/teaching,
- open service UI,
- change NPC presentation,
- play scene/cutscene,
- teleport only through explicit world service and safe checks.

## NPC profiles

Composition-based flags:

- stationary/mobile,
- physical/invulnerable,
- combatable/trainer,
- dialogue/service/mentor,
- per-player visibility/state,
- face-player range,
- portrait/emotion set,
- marker policy.

Most V1 narrative NPCs are stationary, invulnerable and face the active player. Combatable NPCs must be encounter-owned.

## NPC memory

Per-character NPC memory stores compact stable flags and timestamps, not arbitrary object graphs. It supports recognition, prior choices, teaching history and service unlocks.

## Quest journal

Journal provides:

- active quests and objectives,
- tracked quest,
- completed story archive,
- known NPC/region notes,
- authored hints.

Hints are diegetic and do not reveal exact coordinates unless the content intentionally grants them. Death Pouches never appear as map markers.

## Markers

Markers are per-player presentation. States:

- available,
- active objective,
- ready to turn in,
- hidden.

Marker visibility follows conditions and should not leak undiscovered content.

## Cutscenes

- Must be skippable unless the scene is a very short encounter sync.
- Skip jumps to a declared safe endpoint and executes required state actions exactly once.
- Disconnect/restart resumes at safe endpoint, not mid-camera path.
- Combat protection is explicit and encounter-scoped; cutscene state is not universal invulnerability.
- Packet camera effects have a fallback presentation using titles/dialogue and player view.

## Content migration

Quest definition changes provide a migration map for active nodes/objectives. If no safe migration exists, the instance is paused and surfaced to admin repair; it is never silently reset.
