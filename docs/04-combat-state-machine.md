# Combat State Machine

Combat is modeled as orthogonal state machines. Do not replace them with a large collection of booleans.

## Engagement state

```text
EXPLORATION
  -> ALERT       hostile target acquires player, player receives hostile near-miss, or telegraph targets player
  -> ENGAGED     player deals/receives hostile combat effect or uses an offensive action

ALERT
  -> ENGAGED     hostile effect/action commits
  -> EXPLORATION no hostile interest for 4 seconds

ENGAGED
  -> DISENGAGING no hostile action/damage for 8 seconds and no encounter lock

DISENGAGING
  -> ENGAGED     hostile action/damage resumes
  -> EXPLORATION 2 additional seconds pass, no hostile entity has aggro within 24 blocks
```

An encounter may hold all participants in `ENGAGED` until reset or completion. Safe-zone rules may suppress hostile action but do not silently alter weapon state.

### Activity that refreshes engagement

- Dealing or receiving health damage, posture damage, guard pressure, status buildup or forced movement.
- Starting an offensive or hostile-support action.
- Being the active target of an encounter mechanic.
- Healing or shielding an Engaged party member within the encounter.

Opening inventory does not end engagement. The world continues normally.

## Weapon state

```text
SHEATHED -> DRAWING -> READY -> SHEATHING -> SHEATHED
                      |  ^
                      v  |
                   DISABLED
```

### Slot selection

- Selecting a combat weapon in slots 1–8 starts `DRAWING`.
- Selecting another combat weapon starts `SHEATHING` for the current weapon, then `DRAWING` for the latest selected target.
- Selecting a consumable uses `ITEM_TRANSITION`; it does not perform a ceremonial full sheathe.
- Selecting a normal tool, block, food or empty slot starts `SHEATHING` and returns to exploration controls after completion.
- Selecting slot 9 starts sheathing if required. Scene opening still requires RMB and eligibility.
- Scroll spam updates only the latest desired slot. Intermediate transitions are not executed.

Attack input during `DRAWING` may be buffered. Normal damage does not cancel draw. `HEAVY_STAGGER`, `KNOCKDOWN`, `LAUNCH`, `GRAB`, death and world change cancel it.

## Action state

```text
IDLE
WINDUP
ACTIVE
RECOVERY
CHANNELING
SUSTAINING
ITEM_USE
STAGGERED
KNOCKED_DOWN
GRABBED
DEAD
```

Only one primary action state is active per combatant. Movement overlays such as sprint, dodge and knockback are represented separately but can lock action transitions.

### Action lifecycle

1. Validate state and requirements.
2. Reserve resources.
3. Enter windup.
4. At commit point, spend reserved resources and lock consumables/ammo.
5. Enter active windows and resolve hits.
6. Enter recovery.
7. Open chain/cancel windows according to move definition.
8. Return to idle or start buffered action.

If interrupted before commit, reserved resources are released unless the definition declares a partial cost. If interrupted after commit, the cost remains spent.

## Movement state

```text
NORMAL
SPRINT
DODGE
FORCED_MOVEMENT
ROOTED
AIRBORNE
SWIMMING
MOUNTED
```

V1 combat actions require `NORMAL`, `SPRINT` or an explicitly allowed `DODGE` follow-up. Swimming, mounted and uncontrolled airborne combat use vanilla behavior or are rejected unless a move explicitly supports them.

## UI state

```text
NONE
VANILLA_INVENTORY
SCENE
DIALOGUE
CUTSCENE
TRANSACTION_UI
```

Rules:

- `VANILLA_INVENTORY` is allowed while Engaged. Player movement/input stops naturally; enemies continue.
- `SCENE` is allowed only outside Engaged and in a stable physical state.
- `DIALOGUE` may be ambient during combat, but blocking conversation cannot open while Engaged.
- `CUTSCENE` owns input and applies encounter-specific protection only when explicitly authored.
- Opening one exclusive UI closes or rejects another.

## Encounter state

```text
DORMANT
ARMING
ACTIVE
RESETTING
COMPLETED
COOLDOWN
```

Encounter state controls arena lock, participant set, content snapshot, threat, reward grant and boss retry. `ACTIVE` encounters may force Engagement. Completion and reset are idempotent.

## Safe-zone behavior

- Combat weapons may be drawn for preview and practice if the region allows it.
- Hostile hit resolution against protected entities is rejected before resource-consuming commit where possible.
- F/Q remain combat inputs when a weapon is ready; they are not repurposed by the region.
- Scene opening is permitted if all other eligibility checks pass.

## State compatibility examples

| Combination | Result |
|---|---|
| Engaged + Vanilla inventory | Allowed |
| Engaged + Scene | Rejected or auto-closed |
| Drawing + Dodge | Allowed; draw continues |
| Drawing + Knockdown | Draw cancelled |
| Item use + Dodge | Item use cancelled; no consumption before commit |
| Guard + world chest interaction while Engaged | Guard/combat owns RMB |
| Ready, not Engaged + targeted chest | World interaction owns RMB |
| Scene + incoming damage | Scene closes before applying follow-up input |

## Required recovery

On login, no transient action is resumed. The character loads in:

- `EXPLORATION`
- weapon `SHEATHED`
- action `IDLE`
- UI `NONE`

Persistent encounter and reward records are reconciled separately.
