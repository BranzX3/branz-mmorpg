# Input Resolution

## Universal combat grammar

| Intent | Default physical action |
|---|---|
| Primary chain | LMB |
| Weapon secondary / guard / aim / cast | RMB |
| Signature technique | Swap-hand action (`F` by default) |
| Auxiliary technique | Drop action (`Q` by default) |
| Directional branch | movement snapshot + action |
| Dodge | Shift down-edge while directional input is active |
| Sneak | Shift with no directional input |
| Cycle prepared ammo | neutral Shift + Q while ranged weapon is READY |

Tutorial text refers to semantic actions and may show the client's current keybind where available.

## Priority

For intents received in the same resolution frame:

1. Forced interruption or death.
2. UI close/cancel caused by danger.
3. Dodge.
4. Perfect guard/parry response.
5. Buffered legal follow-up.
6. Signature/Auxiliary technique.
7. Directional primary/secondary branch.
8. Neutral primary/secondary.
9. World interaction.
10. Vanilla fallback.

## Direction sampling

Direction is sampled at intent acceptance, not active frame. A four-way snapshot is used for branch selection: forward, back, left, right. Diagonals resolve by dominant component; ties prefer forward/back over lateral. Content cannot require narrower than a 45-degree sector.

## Buffer

- One buffered primary combat intent per player.
- Default buffer window: 8 ticks before a legal chain/cancel window.
- A newer intent replaces an older buffer only when it has higher priority or explicitly targets the same branch family.
- Dodge always clears attack buffers unless the move definition allows `dodge_followup`.
- Weapon swap, hard CC, death, Scene open and encounter reset clear all buffers.

## Tap/hold

Server thresholds:

- Tap: released before 8 ticks.
- Hold: retained for 8 or more ticks.
- Charge moves read continuous hold but quantize tiers at declared tick thresholds.

No content may require a one-tick release window.

## RMB and world interaction

- ENGAGED: combat secondary always owns RMB.
- READY but not ENGAGED: a clearly targeted door, button, container, NPC, station or mount within vanilla reach receives world interaction priority; hostile entities and ambiguous targets retain combat priority.
- SHEATHED: vanilla/world interaction priority.

## Q/drop safety

- In READY, Q invokes Auxiliary and never drops the held item.
- In SHEATHED/EXPLORATION, Q uses vanilla drop except for protected system items.
- Hold Q for 20 ticks while SHEATHED to drop a protected-but-droppable unique item, followed by confirmation where value exceeds the configured threshold.
- Slot 9 Chronicle can never drop.

## Packet deduplication

InputRouter assigns a monotonically increasing per-session sequence, merges duplicate Bukkit/packet observations and rejects stale sequences. Rate limits apply to semantic intents rather than raw packet count.
