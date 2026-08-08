# Combat State Machine

State is represented by orthogonal machines, not one enum with every combination.

## Engagement state

```text
EXPLORATION → ALERT → ENGAGED → DISENGAGING → EXPLORATION
```

- `EXPLORATION`: no hostile claim; out-of-combat regeneration and world interaction rules.
- `ALERT`: a hostile has acquired or threatened the player, but no committed hostile exchange has occurred.
- `ENGAGED`: player dealt/received hostile damage, committed a hostile technique, entered an encounter lock or is actively on a threat table.
- `DISENGAGING`: no recent hostile act; waiting for the exit conditions.

Default exit requires all of:

- eight seconds since last hostile commit or damage;
- no hostile entity currently owns threat on the player;
- no encounter hard lock;
- player is not downed.

Boss/arena encounters may hold ENGAGED until reset, victory or wipe.

## Weapon state

```text
SHEATHED
SHEATHING
DRAWING
READY
DISABLED
```

Selecting a combat weapon begins draw. Selecting another combat weapon sheaths then draws the latest requested slot. Selecting a tool, consumable, empty slot or Chronicle begins sheathe. The transition controller stores only the latest target slot and rejects scroll-spam bypass.

An attack during DRAWING may buffer one opener. Dodge continues the draw unless the dodge causes knockdown or launch. Stagger, knockdown, launch, grab, death and forced teleport cancel draw.

## Action state

```text
IDLE
WINDUP
ACTIVE
RECOVERY
CHANNELING
STAGGERED
KNOCKED_DOWN
GRABBED
DOWNED
DEAD
```

Only one primary action timeline owns attack movement and hit generation. Secondary passive systems may run but cannot start another primary action without a valid cancel window.

## UI state

```text
NONE
VANILLA_INVENTORY
SCENE
DIALOGUE
CUTSCENE
MARKET
CRAFTING
```

- `VANILLA_INVENTORY` is allowed while ENGAGED; world simulation continues.
- `SCENE`, `MARKET`, `CRAFTING` and full dialogue are disallowed while ENGAGED.
- Damage closes Scene, Market, Crafting and non-combat Dialogue.
- Combat dialogue uses a presentation overlay and never owns inventory.

## Encounter state

```text
DORMANT
FORMING
ACTIVE
VICTORY_PENDING
VICTORY
RESETTING
FAILED
```

Reward eligibility freezes at `VICTORY_PENDING`; reward grants are idempotent. Reset clears threat, provider entities, temporary zones and encounter-only resources before returning to `DORMANT`.

## State compatibility examples

| Combination | Allowed |
|---|---|
| ENGAGED + VANILLA_INVENTORY | Yes |
| ENGAGED + SCENE | No |
| DRAWING + DODGE | Yes |
| KNOCKED_DOWN + DRAWING | No; draw cancels |
| ALERT + Local Scene | Profile-dependent; `ENGAGED` is always rejected |
| READY + world interaction | Yes outside ENGAGED under interaction priority |
