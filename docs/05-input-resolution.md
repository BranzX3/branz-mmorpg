# Input Resolution

## Universal grammar

| Client action | Combat meaning |
|---|---|
| LMB / attack | Primary chain or contextual follow-up |
| RMB / use | Weapon secondary, guard, draw/charge/reload/cast |
| Swap-hand action (default F) | Signature technique |
| Drop action (default Q) | Utility technique |
| Sneak action (default Shift) + direction | Dodge |
| Hotbar select/scroll | Select weapon, consumable, tool or Scene Chronicle |

The server refers to actions by semantic name and displays the player's configured keybind when available through client text conventions.

## Context ownership

### Exploration

- Vanilla block/entity interaction has priority.
- LMB behaves normally unless an authored non-combat item intercepts it.
- Q drops the item normally, except protected items.
- Shift sneaks normally.

### Combat Ready, not Engaged

- LMB/F/Q are combat-owned.
- RMB targets a hard world interactable first: NPC, container, door, button, lever, workstation or explicitly registered interactable within normal reach. Otherwise RMB is combat-owned.
- Directional Shift tap performs dodge. Stationary Shift hold performs crouch/brace.
- `Sneak + scroll` while a bow/crossbow is ready and idle cycles prepared ammunition. Slot selection is restored immediately and no weapon swap occurs.

### Engaged

- LMB/RMB/F/Q are combat-owned.
- World interaction never steals RMB except encounter-specific interactables marked `combat_interactable`.
- Directional Shift performs dodge.
- Stationary Shift performs crouch/brace but does not grant invulnerability.
- Inventory may still be opened using the vanilla inventory key.

## Direction snapshot

At action request, movement direction is sampled from the latest server-known input state and normalized into:

```text
FORWARD
BACK
LEFT
RIGHT
FORWARD_LEFT
FORWARD_RIGHT
BACK_LEFT
BACK_RIGHT
NEUTRAL
```

Diagonal branches must be explicitly authored; otherwise the resolver chooses the dominant axis. Direction is captured when the input enters the resolver, not when the move eventually starts.

## Shift tap and hold

- Dodge candidate window: 6 ticks from sneak press.
- If meaningful horizontal movement is present at press or begins within 3 ticks, request a dodge.
- If no movement appears and sneak remains held for 5 ticks, enter crouch/brace.
- Releasing before either condition does nothing.
- After a dodge request is accepted, the same sneak press cannot also crouch.
- Exploration always uses vanilla sneak and bypasses this resolver.

## Input priority

When multiple valid requests arrive in the same server tick:

1. Forced cancellation or death
2. Scene/UI emergency close
3. Dodge
4. Guard/perfect-guard start or defensive release
5. Authored dodge follow-up
6. Buffered chain follow-up
7. F signature
8. Q utility
9. Directional primary/secondary
10. Neutral primary/secondary
11. World/vanilla fallback where allowed

A move may override local priority through a narrow explicit `context_override`, but content cannot reorder death, forced interruption or transaction safety.

## Buffer

- Capacity: one action request.
- Default queue window: final 8 ticks of recovery.
- A request outside a legal queue window is rejected with no buffer.
- A new higher-priority request replaces a buffered lower-priority request.
- Equal/lower-priority input does not replace the existing buffer unless the existing request is the same repeatable chain input; then the timestamp refreshes.
- Buffer expires after 12 ticks or when weapon, form, moveset, target world or character session changes.
- Dodge may bypass the normal buffer only during an authored dodge-cancel window.

## Tap/hold actions

RMB-based charge actions use:

- `press`: begin validation/windup or guard startup.
- `held`: update charge/channel.
- `release`: fire/release if minimum charge is met; otherwise cancel according to definition.

Packet/event duplicates are collapsed by a per-hand, per-action deduplication key over a 2-tick window.

## Q behavior and item dropping

- Combat Ready/Engaged: Q always requests the configured utility branch. If no utility is equipped, the request fails quietly with a short feedback cue. It never drops the item.
- Exploration: vanilla drop behavior.
- Dropping a combat weapon while Ready requires first selecting a non-combat slot or using inventory drag/drop. This prevents accidental loss during combat.
- Slot 9 Chronicle can never be dropped.

## Ammunition selection

V1 avoids stealing F or Q from weapon techniques.

- Hold sneak while stationary and scroll to cycle the virtual quiver's prepared ammo list.
- Allowed only while bow/crossbow is `READY`, action is `IDLE`, and not reloading/drawing.
- While Engaged, the switch has a 6-tick handling lock before the next shot can begin.
- Switching during an active draw is rejected; content may add a dedicated technique later.
- Crossbow ammo is locked at the load commit point.

## Rejection feedback

Every rejected action returns a machine-readable reason and optional player cue:

```text
NO_STAMINA
NO_MANA
WRONG_WEAPON
WEAPON_NOT_READY
ACTION_LOCKED
NO_TARGET
OUT_OF_RANGE
REGION_BLOCKED
TECHNIQUE_NOT_EQUIPPED
ATTUNEMENT_CONFLICT
INVALID_MOVEMENT_STATE
```

Repeated identical feedback is rate-limited to once per 10 ticks.

## Ping policy

The server does not rewind melee combat in V1. It provides:

- input buffer grace,
- perfect-guard grace capped by profile,
- projectile interpolation for presentation only,
- deterministic server hit resolution.

No client-declared hit or timestamp is trusted.
