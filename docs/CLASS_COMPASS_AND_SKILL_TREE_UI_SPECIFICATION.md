# Branz MMORPG — Class Compass and Skill Tree UI Specification

Status: Proposed workstream contract  
Owner: Core MMO and Paper integration developers  
Depends on: Player Session, Permanent Character Class, Class Skill Tree, Inventory, Content, and Storage

## 1. Purpose

When a player joins with an ACTIVE profile, hotbar slot 9 contains a protected
special compass. Before permanent class selection, the compass opens the Class
Selection UI for Warrior, Mage, and Rogue. After selection commits, the same
reserved slot contains a Skill Tree compass that opens the selected class's
Skill Tree.

Minecraft inventory indices are zero-based internally:

    Player-facing hotbar slot: 9
    Bukkit storage index: 8

The compass is a UI token. It does not store authoritative class, level, Skill
Points, unlocked nodes, or skill ranks. Those values come from the loaded player
profile and immutable Core snapshots.

## 2. Compass States

The reserved item has two logical states:

    CLASS_SELECTION_COMPASS
      -> class selection confirmed and committed
      -> CLASS_SKILL_TREE_COMPASS

### 2.1 Class Selection Compass

Suggested presentation:

    Material: minecraft:compass
    Name: Choose Your Class
    Lore:
      - Select Warrior, Mage, or Rogue
      - Your choice is permanent
      - Right-click to open

It is shown only while `class_id` is absent.

### 2.2 Class Skill Tree Compass

Suggested presentation:

    Material: minecraft:compass
    Name: <Class Name> Skill Tree
    Lore:
      - Class Level: <level>
      - Skill Points: <unspent>
      - Right-click to open

It is shown only after a class selection is authoritative. Its visible lore is
presentation and may be stale for a short coalescing interval; Core snapshots
remain authoritative.

Custom model data or a resource-pack appearance may be configured but cannot be
required for gameplay.

## 3. Join Lifecycle

The Paper join event does not immediately mint or place the compass. It waits
for the asynchronous profile load:

    Paper join
    -> create session token
    -> load profile asynchronously
    -> verify the same session token
    -> enter ACTIVE
    -> read authoritative class snapshot
    -> reconcile reserved slot 9

If profile loading fails, gameplay remains fail-closed and no selection compass
is issued from a blank profile.

Reconciliation rules:

- No selected class: place the Class Selection Compass.
- Selected class: place the matching Class Skill Tree Compass.
- Existing valid compass with correct token state: update presentation in place.
- Missing or invalid token: repair it from authoritative state.
- Duplicate compass tokens: retain one in slot 9 and remove/quarantine duplicates
  without affecting authoritative progression.

## 4. Reserved Slot Policy

Hotbar slot 9 is reserved while the compass feature is enabled.

The compass cannot be:

- Dropped
- Moved through normal inventory clicks
- Swapped to another hotbar slot
- Placed in a container
- Used in crafting, anvil, grindstone, smithing, enchanting, or trading
- Consumed
- Lost on death
- Collected by a hopper or other entity

Number-key swaps, drag events, creative inventory actions, off-hand swaps,
death drops, item pickups, and plugin-driven inventory changes are reconciled.

If slot 9 contains a normal item when the ACTIVE session is reconciled:

1. Attempt to move it into another free player inventory slot.
2. If inventory is full, route it to authoritative pending delivery/mailbox.
3. Only after safe relocation, place the compass in slot 9.
4. If relocation cannot be guaranteed, leave the player UI locked, record an
   actionable diagnostic, and retry; never delete the normal item.

The compass does not replace valuable items silently.

## 5. Token Identity and Validation

The Paper item contains a namespaced PDC marker sufficient to identify its UI
purpose and presentation revision:

    token_type
    token_version
    player_uuid
    session/profile binding marker
    presentation_revision
    integrity signature or server validation data

PDC is not authoritative progression storage. A copied, renamed, edited, or
foreign player's compass cannot select a class, spend points, or read private
state.

Every click revalidates:

- ACTIVE player session and session token
- Player UUID/token ownership
- Correct reserved slot
- Expected compass state
- Current authoritative class/tree snapshot
- UI nonce if a menu session is already open

## 6. Interaction

Default controls:

| Action | Result |
|---|---|
| Right-click compass | Open the appropriate Class or Skill Tree UI |
| Left-click compass | No progression mutation; optional help feedback |
| Drop compass | Cancel and reconcile |
| Move compass | Cancel and reconcile |

Compass interaction takes priority over weapon RMB only when the authoritative
held item is the reserved compass. It never starts a combat cast.

The event is cancelled before vanilla compass behavior. Opening the UI occurs on
the owning Paper scheduler.

## 7. Class Selection UI

The initial selection inventory displays three class entries:

| Class | Initial identity | Example starter weapons |
|---|---|---|
| Warrior | Durable physical frontline | Broadsword, Greatsword |
| Mage | Magic damage, area control, utility | Fire Staff, Arcane Staff |
| Rogue | Mobility, precision, combo pressure | Daggers, Short Sword |

Each entry shows:

- Class name and localized description
- Role weights
- Primary resources
- Starter weapon/loadout
- Class Skill 1, Class Skill 2, and Ultimate preview
- Permanent-choice warning

Clicking a class opens a confirmation screen. It does not select immediately.

Confirmation requires a distinct final action:

    Preview <Class>
    -> Confirm permanent selection
    -> Commit selection operation

The confirmation UI states that normal gameplay cannot change the class.
Closing, disconnecting, timing out, or pressing cancel performs no mutation.

## 8. Class Selection Transaction

The confirmed selection request contains:

    operation ID
    player UUID
    session token
    selected class ID
    expected profile revision
    expected content revision
    UI nonce

Core validates that the player has no selected class and that the class,
starter loadout, skills, inventory delivery, and current content revision are
valid.

The transaction:

    store permanent class
    -> initialize class level and tree
    -> grant starter unlocks
    -> create/deliver starter loadout
    -> audit
    -> write outbox events
    -> commit

After commit, Paper:

1. Verifies the session token again.
2. Closes the selection UI.
3. Reconciles slot 9 to the Class Skill Tree Compass.
4. Shows the selected class and starter information.

Retrying the same operation returns its original result and cannot duplicate
class selection, Skill Points, starter items, or unlocks.

## 9. Skill Tree UI

Right-clicking the post-selection compass opens an immutable snapshot of the
selected class tree.

The UI displays:

- Permanent class and Class Level
- Total Class XP and next-level progress
- Unspent Class Skill Points
- Warrior, Mage, or Rogue branches
- Node icon, rank, maximum rank, point cost, and effect
- Level and prerequisite requirements
- Mutually exclusive Keystone information
- Locked, available, purchased, equipped, and disabled states

Suggested visual states:

| State | Presentation |
|---|---|
| Locked | Gray icon with missing requirements |
| Available | Highlighted icon with point cost |
| Purchased | Class-colored icon with current rank |
| Max rank | Completed visual |
| Equipped | Distinct marker |
| Migration disabled | Warning icon and repair reason |

The inventory UI is a renderer. Clicking an icon sends a requested node ID and
expected tree revision; it never sends trusted point totals or ranks.

## 10. Skill Point Spending

Class Skill Points upgrade class-specific attributes and unlock or modify class
skills.

Allowed node effects:

- Bounded attribute modifiers such as Health, Defense, Magic Power, Movement,
  Critical Chance, resource maximum, or regeneration
- Unlock Class Skill 1, Class Skill 2, Passive, or Ultimate
- Increase a skill rank
- Select a validated skill variant
- Improve bounded resource generation, cost, duration, range, or coefficient

Every status/stat node uses the normal Core attribute modifier model with stable
source IDs and global caps. A tree cannot write final Bukkit attributes
directly.

Purchase flow:

    click node
    -> show exact effect/cost confirmation when configured
    -> validate UI nonce and tree revision
    -> validate permanent class
    -> validate class level and prerequisites
    -> validate exclusion group and available points
    -> atomically spend points and grant rank
    -> rebuild affected modifiers/skill snapshot
    -> publish event
    -> render fresh tree snapshot

One operation cannot consume points without granting the rank. Double-clicks,
shift-clicks, lag, reconnect, or retries cannot apply the purchase twice.

## 11. Class-Specific Examples

### Warrior

- `Reinforced Guard`: spend points to increase bounded Defense while guarding.
- `Whirlwind`: unlock the Shift + LMB class skill.
- `Warbreaker`: unlock the Shift + F ultimate.

### Mage

- `Mana Efficiency`: spend points to reduce configured Mana cost within cap.
- `Mana Shield`: unlock the Shift + RMB class skill.
- `Meteor`: unlock the Shift + F ultimate.

### Rogue

- `Flowing Combo`: improve bounded Energy/combo behavior.
- `Eviscerate`: unlock the Shift + LMB class skill.
- `Shadow Step`: unlock the Shift + F ultimate.

Cross-class nodes are neither visible as purchasable nor accepted by Core. A
Warrior cannot submit a Mage node ID to bypass the UI.

## 12. UI Session Safety

Each open menu has:

    UI nonce
    player UUID
    session token
    menu type
    expected profile revision
    expected class/tree revision
    opened timestamp

Inventory clicks are cancelled by default inside managed menus. Unknown slots,
player-inventory shift-clicks, number keys, double clicks, drag actions, and
stale menu events cannot mutate progression.

The menu closes or refreshes when:

- Session token changes
- Player logs out, dies, or changes world under configured policy
- Class/tree/profile revision changes
- Content reload invalidates the rendered snapshot
- Purchase/respec completes
- UI timeout expires

## 13. Recovery and Administration

Required commands:

    /branz class-compass inspect <player>
    /branz class-compass repair <player>
    /branz class inspect <player>
    /branz class-tree inspect <player>
    /branz class-tree repair <player> <reason>

Compass repair only reconciles the UI token. It cannot change class, XP, points,
or nodes.

Staff tools report:

- Expected and actual slot-9 item
- Duplicate or invalid tokens
- Current class/profile/tree revision
- Pending starter delivery
- Last selection/purchase operation
- Tree migration or modifier repair state

## 14. Performance

- Join reconciliation occurs after profile load and targets under 1 ms at p95 on
  the owning Paper thread, excluding inventory rendering.
- Menu rendering uses immutable in-memory snapshots and performs no SQL, YAML,
  filesystem access, or blocking wait on the Paper thread.
- Compass lore updates are event-driven and coalesced.
- Opening a menu does not create an unbounded scheduler task.
- Duplicate-token scans are bounded to the player's inventory.
- Load tests cover join/reconnect storms, menu spam, double-click purchases,
  full inventories, pending delivery, and content reload.

## 15. Acceptance Criteria

- An ACTIVE new player receives the Class Selection Compass in hotbar slot 9.
- Bukkit storage index 8 is used for player-facing slot 9.
- Profile load failure does not create a blank class-selection state.
- Existing normal slot-9 items relocate or enter pending delivery without loss.
- The compass cannot be dropped, moved, traded, crafted, stored, or lost on death.
- Right-click opens the correct selection or tree UI and never casts a weapon skill.
- Warrior, Mage, and Rogue show permanent-choice previews and confirmation.
- Cancelling or closing selection performs no mutation.
- Confirmed class selection commits once and cannot duplicate starter rewards.
- After selection, slot 9 changes to the selected Class Skill Tree Compass.
- The Skill Tree UI shows authoritative level, points, nodes, ranks, and requirements.
- Skill Points upgrade bounded status nodes and unlock class-specific skills.
- A Warrior cannot purchase Mage/Rogue nodes, with equivalent checks for all classes.
- Stale UI, double-click, reconnect, and retry cannot spend points twice.
- Missing, copied, tampered, and duplicate compasses are reconciled safely.
- Compass repair never changes authoritative progression.
- Core transaction tests and Paper inventory/UI smoke tests pass.
