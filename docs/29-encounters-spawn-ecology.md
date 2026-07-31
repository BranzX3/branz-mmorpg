# Encounters, Spawns and Enemy Ecology

## Territory definitions

A territory declares biome/region, population pools, patrol paths, time/weather modifiers, leash, safe boundaries, elite rules, reset policy and content snapshot.

## Population controller

Normal enemies are maintained toward target population over time, not spawned directly beside players. Spawn anchors validate distance, visibility, region rules and nearby population. Restart does not instantly refill every territory.

## Patrol and ecology

Bandits patrol roads and camps; beasts inhabit feeding/den regions; corrupted creatures intensify under authored conditions. Ecology is rule-driven presentation and population behavior, not a full biological simulation.

## Encounter formation

An encounter forms when territory enemies share threat/objective context or an authored boss trigger activates. Participants, threat, boundary, checkpoint and reward pool are recorded.

## Leash and exploit rules

- Enemies cannot be safely killed from unreachable terrain indefinitely.
- Leaving the boundary begins retreat/reset; damage dealt during invalid unreachable state may be rejected.
- Bosses cannot be dragged into cities or across region protection.
- Re-entering during reset does not preserve free posture/damage progress.

## Elite and boss spawning

Elites use authored conditions, population pressure or activity events. Bosses use explicit spawn/instance rules and do not randomly appear on new players. Encounter lock, wipe, re-entry and checkpoint behavior are declared per boss.

## Threat

Threat includes damage, posture, effective healing/support, guard/provoke and scripted fixation. It decays/resets only by encounter rules. Stealth/vanish cannot erase threat while carrying objectives or cargo unless content explicitly allows it.

## Restart recovery

On startup, encounter journals classify active instances:

- resumable authored instance;
- safe reset with participant notification;
- victory pending reward reconciliation;
- failed/quarantined.

World mobs never independently grant a second reward after provider/entity reconstruction.
