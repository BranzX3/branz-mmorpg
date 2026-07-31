# V1 Full Specification Status

Status: **Complete implementation baseline; ready for milestone coding**

## Covered gameplay loop

The specification now covers:

- character/session and content identity;
- combat, weapons, defense, magic and progression;
- item lifecycle, enhancement, rewards and Death Pouch;
- quests, NPCs, party, LFG, downed state and limited PvP;
- Slot 9 Local Scene Hub, wardrobe, HUD and accessibility;
- gathering, fishing, hunting, processing, cooking, alchemy, smithing, farming and workers;
- Central Exchange, unique market, commissions, five-city economy and regional cargo;
- mounts, llama caravans, stable recovery and Training;
- sanctuaries, camps, boss checkpoints, navigation, travel and encounters;
- city storage, bank, market warehouse, freight, overflow and notifications;
- activity boards, Civic Influence, factions and basic guilds;
- persistence, transactions, content pipeline, designer-facing authoring tools, security, testing and operations.

## No architecture-blocking unknowns

All decisions required to begin implementation have a baseline. Remaining values are content/balance tuning or art direction and must not be interpreted by coding agents as permission to redesign ownership, inputs or state machines.

## Intentional non-goals

Open-world PvP, territory war, large raids, housing, ocean sailing/bartering, mounted combat, permanent mount death, deep breeding, player finance and multi-shard networking remain outside V1.

## Required change process

An ADR and migration review are required for:

- hotbar/input ownership;
- persistent IDs and item/lot locations;
- transaction/escrow/reward semantics;
- state-machine transitions;
- random accuracy/critical policy;
- item destruction/downgrade policy;
- Scene teleport/vulnerability policy;
- market/cargo boundary;
- worker offline rules;
- PvP scope and server topology.

## Recommended starting point

Implement Milestones 0–2 only. Require the coding agent to read:

1. `02-system-invariants.md`
2. `03-architecture.md`
3. `04-identifiers-content-contracts.md`
4. `35-persistence-transactions.md`
5. `36-content-dev-pipeline.md`
6. `39-implementation-roadmap.md`
7. `43-content-authoring-tools.md`

Do not implement combat before content snapshots, leases and transaction tests pass.
