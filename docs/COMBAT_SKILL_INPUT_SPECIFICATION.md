# Branz MMORPG — Combat and Skill Input Specification

Status: Proposed workstream contract  
Owner: Core MMO and Paper integration developers  
Depends on: Combat, Skill Execution, Permanent Class, Character Build, Equipment, Player Session, and Content

## 1. Purpose

Combat uses familiar Minecraft inputs instead of commands. The initial control
scheme combines left click, right click, swap-hand (`F`), and sneak (`Shift`) to
provide basic attacks, weapon skills, permanent class skills, and optional input
combos.

Every character permanently selects Warrior, Mage, or Rogue. **Class Skills**
come from that selection and class progression. Weapon Skills come from the
active weapon and Combat Mastery. Loadout validation may require compatible
weapon or armor tags before a Class Skill can be used.

The server is authoritative. Paper input events create intents only; an input
never directly applies damage, consumes resources, or grants mastery XP.

## 2. Default Input Map

| Input | Slot | Default behavior |
|---|---|---|
| LMB | Basic Attack | Normal weapon attack and optional combo/resource generation |
| RMB | Weapon Skill 1 / Guard | Primary weapon action, block, aim, or cast |
| F | Weapon Skill 2 / Utility | Dash, counter, projectile, stance, or weapon utility |
| Shift + LMB | Class Skill 1 | Offensive class skill |
| Shift + RMB | Class Skill 2 | Defense, buff, control, or movement class skill |
| Shift + F | Class Ultimate | High-impact class skill with explicit charge and cooldown |

The mapping is a default profile, not hard-coded combat logic. Content binds a
logical action slot to a skill ID; player settings may select another approved
control profile without changing authoritative skill definitions.

One active weapon is supported for the initial release. Because `F` is reserved
by the default combat profile, normal hand swapping is cancelled while the
player is in an ACTIVE MMO combat context. Inventory and equipment services
remain the authoritative way to change a validated loadout.

## 3. Skill Sources

### 3.1 Weapon actions

Weapon actions are supplied by the active weapon type and loadout:

- Basic attack
- Weapon Skill 1
- Weapon Skill 2 or utility
- Optional weapon combo actions

Changing weapon type changes these actions. Item rarity may select an approved
variant or bounded modifier, but rarity alone cannot inject arbitrary executable
behavior or bypass mastery unlocks.

Examples:

| Weapon | Input | Example action |
|---|---|---|
| Greatsword | F | Ground Smash |
| Fire Staff | RMB | Fireball Shot |
| Daggers | LMB → RMB | Eviscerate |

### 3.2 Permanent class actions

Class actions come from the permanent Warrior, Mage, or Rogue selection and its
class progression. They remain available only while the active loadout satisfies
their weapon, armor, resource, and unlock requirements.

Initial class examples:

| Class | Input | Example action |
|---|---|---|
| Warrior | Shift + LMB | Whirlwind |
| Mage | Shift + RMB | Mana Shield |
| Rogue | Shift + F | Shadow Step |

Switching to an incompatible loadout disables the action with an exact reason;
it does not silently replace it with an unrelated skill.

## 4. Input Intent Model

Paper translates platform events into immutable intents:

    inputId
    playerId
    sessionToken
    inputType
    pressedAtTick
    monotonicTimestamp
    sneaking
    hand
    heldItemInstanceId
    targetHint
    worldAndPosition
    inputProfileRevision
    contentRevision

`targetHint` is untrusted. Core revalidates target, distance, world, line of
sight, faction, safe zone, invulnerability, and hit frequency before mutation.

Inputs are accepted only for an ACTIVE session. Late callbacks and queued
intents compare the session token, held authoritative item, loadout revision,
and immutable skill content revision.

## 5. Input Arbitration

Minecraft inputs overlap with vanilla actions. The Paper adapter resolves one
input through this priority order:

1. Server safety, protection, and administrative locks
2. Active cast/channel/targeting interaction
3. Required inventory, container, NPC, or world-object interaction
4. Bound MMO skill or combo continuation
5. Vanilla action permitted by the active control profile

An input produces at most one authoritative combat action unless a skill
definition explicitly contains multiple effect nodes.

### 5.1 LMB rules

- Entity attack becomes a basic-attack intent; vanilla damage is cancelled when
  the MMO combat engine owns that target interaction.
- Swinging in air may advance a configured combo but cannot report a hit.
- Block breaking remains available outside combat ownership and must not become
  a combat hit merely because the player swings a tool.
- Mining and Combat Mastery cannot both claim the same result.

### 5.2 RMB rules

- Container, door, button, NPC, quest object, and protected world interaction
  takes priority unless the player is in an explicit targeting/casting mode.
- Food, potion, bow, shield, bucket, and other vanilla-use behavior must declare
  whether the MMO profile passes through, replaces, or rejects the action.
- Guard is a stateful action with server timestamps, not continuous client trust.

### 5.3 F rules

- A bound `F` action cancels the vanilla swap-hand event.
- If no action is bound and the control profile permits vanilla swapping, the
  equipment service validates the resulting loadout before allowing it.
- Repeated `F` events cannot reset cooldowns, duplicate off-hand items, or
  trigger the same cast twice.

### 5.4 Shift modifier

Sneak state is sampled and revalidated when the intent is accepted. `Shift`
selects a different logical action slot; it does not represent a fixed class.
Sneak movement behavior may continue unless the selected skill locks movement.

## 6. Combo Input System

Combo definitions are declarative finite-state machines.

Examples:

| Sequence | Example result |
|---|---|
| LMB → RMB | Heavy Strike |
| RMB → LMB | Parry and Counter |
| LMB → LMB → RMB | Weapon finisher |

A combo definition contains:

    combo ID
    required weapon/loadout tags
    ordered input steps
    minimum and maximum delay per step
    reset timeout
    priority
    consume policy
    resulting skill ID
    miss/cancel policy

Rules:

- Timing uses monotonic server time.
- Default combo windows are content-configured and bounded.
- A sequence cannot begin before the previous accepted input.
- Inventory changes, death, stun, logout, world change, loadout revision change,
  or timeout resets the pending combo.
- Input spam cannot queue an unbounded number of steps.
- At most one combo candidate consumes an input.
- Ambiguous prefixes wait only for the configured bounded resolution window.
- Basic-attack feedback may be delayed only when the control profile declares
  that a combo has priority.

Core exposes a deterministic combo resolver that can be tested without Paper.

## 7. Hold, Charge, and Channel Input

Minecraft/Paper does not provide a universal trusted “button is still held”
signal for every item and input. Charge and channel skills therefore use an
explicit server-owned protocol:

    PRESS/START
      -> CHARGING or CHANNELING
      -> RELEASE/COMMIT
      -> RECOVERY

Only item actions with a reliable release/cancel event may use true hold-release
behavior. Other bindings use one of these configured modes:

- Press once to start, press again to release
- Press once and auto-release at maximum charge
- Hold through a supported vanilla item-use mechanic

The UI must show which mode is active. Movement, damage, stun, silence, item
change, logout, and timeout apply the skill's interrupt/refund policy.

Charge power is derived from server elapsed time and clamped between configured
minimum and maximum values.

## 8. Resources

Initial resources:

| Resource | Purpose |
|---|---|
| Mana | Magic, healing, support, and selected build skills |
| Stamina | Physical skills, guard, movement, and dodge actions |
| Rage | Generated by configured valid combat contribution and spent by selected weapon/build skills |
| Energy | Fast-regenerating or combo-generated resource for selected weapon types |

Rage and Energy are optional build resources, not universal requirements.
Definitions declare which resource they use.

Basic attacks may generate a resource only after a valid authoritative combat
effect. Swinging in air, hitting an invalid target, or replaying a hit ID grants
nothing.

Eligibility and payment order:

    validate session/loadout/state/target
    -> verify cooldown and resource
    -> reserve configured cost
    -> begin cast
    -> commit or refund according to interrupt policy

Failed eligibility consumes no resource and starts no cooldown.

## 9. Cooldown and Input Buffer

- Cooldowns use monotonic server time.
- Each cast has a unique cast ID and immutable content revision.
- Cooldowns may be individual or use a shared cooldown group.
- Equipment movement and weapon swapping cannot reset a cooldown.
- Cooldown recovery observes the global attribute cap.
- Client animation or UI state never decides readiness.

An optional bounded input buffer may retain one eligible next action during
recovery. Default buffer duration is content/configuration data. Newer input may
replace the buffered action only under the declared policy.

No player may have an unbounded input, combo, cast, projectile, or feedback
queue.

## 10. Ultimate Skills

The `Shift + F` slot is reserved for a high-impact build action. An ultimate may
use cooldown, resource, encounter charge, or a combination.

Ultimate rules:

- The active build must explicitly bind and unlock it.
- Charge comes only from configured authoritative contribution.
- Death, encounter reset, loadout change, and logout behavior are explicit.
- Re-equipping an item cannot refill charge or reset cooldown.
- Ultimate effects obey the same targeting, safe-zone, PvE/PvP, and idempotency
  rules as all other skills.

“Ultimate” is an input/presentation category and does not bypass normal skill
validation.

## 11. Content Definitions

Example control profile:

```yaml
id: branz:default_action_controls
type: combat_input_profile
bindings:
  LMB: BASIC_ATTACK
  RMB: WEAPON_SKILL_1
  SWAP_HAND: WEAPON_SKILL_2
  SNEAK_LMB: BUILD_SKILL_1
  SNEAK_RMB: BUILD_SKILL_2
  SNEAK_SWAP_HAND: ULTIMATE
combo_window_millis: 450
input_buffer_millis: 150
```

Example combo:

```yaml
id: branz:broadsword_heavy_strike
type: combat_combo
required_tags: [branz:broadsword]
steps:
  - input: LMB
  - input: RMB
    max_delay_millis: 450
result_skill: branz:heavy_strike
reset_timeout_millis: 600
priority: 100
```

Validation rejects unknown input names, missing skill slots, invalid timing,
unbounded buffers, ambiguous combos without a priority rule, impossible
hold/release bindings, cyclic skill graphs, and references outside the same
compatible content snapshot.

## 12. Feedback and Accessibility

Accepted or rejected intents produce coalesced player feedback:

- Cast bar or charge indicator
- Cooldown and resource status
- Combo-step cue
- Invalid-target or locked-state reason
- Sound and text alternative for important visual cues

Players may choose approved alternative control profiles. Remapping changes
logical bindings only and cannot reduce server validation, cooldowns, costs, or
combo timing requirements.

## 13. API and Events

Public immutable values:

- `CombatInputIntent`
- `CombatInputProfile`
- `InputResolution`
- `ComboStateSnapshot`
- `SkillSlot`
- `CastSnapshot`

Events use the shared domain-event envelope:

| Event | Payload |
|---|---|
| CombatInputAccepted | Player, input ID/type, resolved slot, cast/combo ID |
| CombatInputRejected | Player, input ID/type, reason; internal/rate-limited |
| ComboAdvanced | Player, combo ID, step, expiry |
| ComboResolved | Player, combo ID, resulting skill/cast ID |
| CastStarted | Player, cast ID, skill ID, input source |
| CastInterrupted | Player, cast ID, reason, refund result |

High-frequency input events are not required to be persistent economic events.
Progression and resource mutations remain transactional and auditable through
their authoritative combat/skill events.

## 14. Performance and Abuse Controls

- Input arbitration and combo resolution target under 0.20 ms at p95 per input.
- The combat input contribution remains within the Core global 4 ms p95 budget
  at 50 concurrent players.
- Combo and input-buffer state is bounded per player and cleared on session end.
- Duplicate input IDs and cast IDs are rejected.
- Implausible input rates are suppressed and recorded for telemetry.
- No SQL, filesystem access, YAML parsing, or blocking wait occurs on the Paper
  tick/owning thread.
- UI feedback is coalesced and does not publish one persistent event per swing.

## 15. Acceptance Criteria

- LMB performs one authoritative basic attack and never applies vanilla plus MMO
  damage to the same target.
- RMB interaction with containers/NPCs follows the documented arbitration rule.
- Bound `F` activates Weapon Skill 2 and cannot swap or duplicate off-hand items.
- Shift combinations resolve skills from the player's permanent class.
- LMB → RMB resolves Heavy Strike only inside its configured timing window.
- Reversed or expired sequences do not activate the wrong combo.
- Death, stun, logout, item change, and loadout revision reset pending combos.
- Charge duration comes from monotonic server time and remains clamped.
- Invalid input, miss, immunity, and input spam grant no resource or mastery XP.
- Failed eligibility consumes no resource and starts no cooldown.
- Item movement cannot reset cooldown, charge, cast, or ultimate state.
- Input queues remain bounded under spam and reconnect tests.
- Combo and skill resolution pass pure Java deterministic tests.
- Paper smoke tests cover entity attack, air swing, block break, interaction,
  swap hand, sneak combinations, held-item change, and cancelled events.
