# Product Scope

## Product statement

Core MMO is a classless high-fantasy action MMO runtime built for Paper. Normal combat should feel fluid and expressive; elite and boss combat should reward reading telegraphs, positioning, guard, dodge, parry, posture pressure and prepared builds.

The game uses Minecraft's world and inventory as physical context while replacing level-centric MMO progression with weapon knowledge, techniques, forms, hidden mastery, body conditioning, equipment identity and player preparation.

## Player identity

- One Minecraft account owns one persistent character.
- There is no class selection and no manual STR/DEX/INT allocation.
- A character may eventually learn all weapon and magic disciplines.
- The active build is limited by held equipment, prepared moves, form, attunement and runtime resources.
- Veteran power is primarily breadth, mechanical knowledge and flexibility. Numeric progression is bounded.
- Renown represents recognition and history, not combat level.

## Combat pillars

1. **Readable commitment** — every move has windup, active and recovery phases.
2. **Minecraft-native control grammar** — hotbar selection, LMB, RMB, swap-hand action, drop action and sneak action.
3. **No random accuracy** — hitboxes, facing, movement and line of sight determine contact.
4. **Conditional advantage** — counter, back attack, weak point and posture break replace random critical chance.
5. **Build expression without button bloat** — techniques replace branches in a compact moveset.
6. **Server authority** — clients present animation and effects but do not decide outcomes.
7. **Low irreversible punishment** — no item destruction, no enhancement downgrade and no permanent class lock.

## V1 includes

- Character persistence and one-character policy.
- Classless equipment and moveset system.
- Draw/sheathe combat readiness and engagement state.
- Melee, ranged and one complete magic runtime.
- Dodge, guard, perfect guard, parry technique, posture, poise and crowd control.
- Equipment, virtual accessory slots, cosmetics and Scene Hub.
- Item UUIDs, durability, repair, enhancement and limited rolls.
- Flask, consumables, ailments, remedies and alchemy station crafting.
- Personal loot, persistent pending rewards and trade-safe item inspection.
- Party of up to five, encounter membership, boss checkpoints and reward eligibility.
- Quest, dialogue and stationary NPC runtime.
- PvE death pouch.
- Duels and arena PvP with a separate balance profile.
- Oraxen-backed resource pack and content pipeline.
- PostgreSQL persistence, transaction journal, admin repair and observability.

## V1 explicitly excludes

- Open-world PvP, criminal flags and territory warfare.
- Auction house, mail and cross-server trade.
- Multi-shard or cross-server character sessions.
- Player housing, guild wars and raids larger than one party.
- Downed/crawl/revive state.
- Offline crafting.
- Free-form summons with independent persistent inventories.
- Dynamic NPC schedules and full world-state simulation.
- A client mod requirement.

## Product constraints

- The required resource pack may change visuals and GUI textures but cannot be trusted for gameplay logic.
- Vanilla inventory behavior remains recognizable. Opening inventory does not pause the world.
- Hotbar slot 9 is reserved for the Core Scene item. Gameplay uses slots 1–8.
- The server must fail safely when an optional integration is unavailable.
- All important systems require admin inspection and repair commands.

## Success criteria

A V1 release is successful when a new player can:

1. Join, receive the resource pack and complete onboarding.
2. Learn a weapon foundation, equip a valid build and understand the input grammar.
3. Defeat normal enemies and an elite using dodge, guard and posture mechanics.
4. Obtain, repair, enhance, trade and visually customize an item without duplication or loss.
5. Join a party, complete a boss encounter, receive personal rewards and retry from a checkpoint.
6. Progress a technique and hidden conditioning through meaningful play with understandable qualitative feedback.
7. Open the local Character Scene, preview gear and cosmetics, commit or cancel safely.
8. Disconnect or experience a controlled server restart without losing ownership, encounter rewards or scene state.
