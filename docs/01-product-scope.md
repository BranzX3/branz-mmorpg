# Product Scope

## Vision

Core MMO is a high-fantasy, classless action MMO layered on Minecraft. Normal combat provides fluid power-fantasy chains; elites and bosses demand telegraph reading, positioning, guard, dodge, posture pressure and punishment. Character identity comes from weapon family, learned techniques, forms, magic arts, equipment and preparation rather than a class selection screen.

## Full V1 player loop

```text
Choose an activity or contract
→ prepare build, Flask, food, tools and mount
→ travel through discovered routes
→ fight, gather, hunt, fish, farm or trade
→ return to camp or city
→ process, craft, repair, enhance and store
→ use, sell, commission or package cargo
→ gain combat breadth, lifeskill rank, renown and civic influence
→ unlock mentors, recipes, workshops, routes and harder encounters
```

V1 is considered playable only when this loop is recoverable across disconnects and server restarts.

## Character principles

- One Minecraft account maps to one persistent character.
- No permanent class, manual STR/DEX/INT allocation or alternate-character system.
- A character can eventually learn every combat and lifeskill discipline.
- Active strength is constrained by equipped items, moveset branches, form, attunement, preparation and carried supplies.
- Long-term advantage is breadth, knowledge, efficiency and flexibility; raw combat power has bounded progression.
- Combat Mastery and Body Conditioning exact values are hidden; lifeskill Rank and Mastery are visible.

## World principles

- World danger is fixed by region and encounter, not scaled to individual players.
- Progress gates are diegetic: keys, rituals, transport, faction access, knowledge and world actions.
- Roads, settlements, storage locations, markets and trade routes matter spatially.
- Convenience must not erase travel, regional production or preparation.

## Full V1 systems

V1 includes combat, magic, equipment, quests, personal rewards, party play, scene UI, all listed lifeskills, farming, limited workers, markets, regional trade, mounts, camps, navigation, city storage, freight, activity boards, factions, civic influence and basic guilds.

## Explicit V1 non-goals

These are not secretly required by “full V1”:

- Open-world PvP, criminality, territory war or castle siege.
- Large raids above ten players.
- Player housing interiors and freeform land ownership.
- Ocean sailing, ship combat and BDO-style bartering.
- Mounted combat.
- Permanent mount death or deep genetic breeding.
- Player loans, interest, futures or real-money trading.
- Marriage, pet collection or battle-pass systems.
- Multi-shard/cross-server character migration.

Interfaces may anticipate these systems, but V1 runtime must not implement speculative behavior that weakens delivery or safety.

## Server topology

V1 runs as one authoritative Paper shard backed by PostgreSQL and external resource-pack hosting. Services are modular but not distributed. Every active character holds a database-backed session lease to prevent duplicate login ownership.
