# Quests, Dialogue and NPCs

## Quest states

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

Quest instances store definition/content version, branch flags, objective state and reward grant IDs.

## Objective types

Kill, collect, reach, interact, observe, escort, defend, technique use, training completion, dialogue choice, puzzle, ritual, craft, deliver, hunt, fish, farm, trade cargo and boss condition.

## Failure and retry

Definitions declare whether failure auto-resets, returns to checkpoint, requires reacquisition or is permanent narrative state. Core gameplay access cannot be permanently lost through an irreversible dialogue choice.

## Party sharing

Each objective declares sharing policy. Escort/defend/boss state may be shared; personal dialogue and authored items remain individual. Party changes never copy a unique quest item.

## Dialogue modes

- Ambient
- Conversation
- Cinematic
- Combat
- Whisper

Dialogue conditions may read knowledge, technique, rank band, affinity, reputation, world flags and quest state. Hidden exact progression values are not shown.

## NPC profiles

Composition flags support:

- static/physical;
- invulnerable;
- combatable;
- trainer;
- merchant/service;
- per-player marker/state;
- portrait/emotion;
- face player;
- memory flags.

## Cutscenes

Cutscenes are skippable after required state synchronization. Skip moves to an authored safe beat and commits no reward twice. Disconnect restores control and resumes/restarts from a safe checkpoint.

## Journal

Journal shows active/completed stories, objective clues, known NPCs and regional threads. It avoids exact GPS for exploration-oriented objectives and never displays Death Pouch location.

## Migration

When quest definitions change, migration maps active node/objective IDs. Missing mappings quarantine the quest instance for admin repair rather than silently resetting player progress.
