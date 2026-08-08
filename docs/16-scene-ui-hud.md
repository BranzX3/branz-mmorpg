# Scene Hub, UI, HUD and Resource Pack

## Slot 9 Chronicle

Hotbar slot 9 is permanently reserved for the `Adventurer's Chronicle` system item.

- Selecting it does not open a menu.
- Main-hand use requests the Scene Hub.
- It cannot move, drop, swap to off-hand, enter a container, be consumed or be traded.
- Login, respawn and inventory reconciliation restore it if missing and remove duplicates.
- Admin/creative mode may temporarily bypass visual locking only through an explicit permission; persistent ownership rules still apply.

## Scene eligibility

Scene opens only when:

- character session is active,
- not `ENGAGED`, with the remaining status checked by the Scene admission profile,
- action state is Idle,
- grounded and not falling/swimming/flying/mounted/in portal,
- not in exclusive dialogue/cutscene/transaction,
- region permits Scene,
- no hostile entity currently has aggro within 16 blocks,
- world actor, viewpoint and control-overlay providers are available.

Selecting slot 9 triggers weapon sheathing. RMB opens only after sheathing completes.

## Local Scene

The everyday Scene Hub does not teleport the real player.

1. Lock normal movement and combat inputs without closing the Scene.
2. Save return UI/slot/camera presentation state.
3. Find an owner-visible preview location 2.75 blocks in front.
4. If blocked, test candidate yaw offsets: `0, +35, -35, +70, -70, 180` degrees.
5. Validate floor, headroom and line of sight.
6. Spawn owner-only preview actor facing the viewer.
7. Apply soft local presentation light/particles where supported.
8. Open the Inventory control overlay while preserving the central actor viewport.

If no valid actor location exists, reject opening with a short message and recover every acquired
presentation handle. V1 never substitutes an inventory-only preview and never teleports to a Scene
Pod for the normal Chronicle menu.

## Preview actor

The actor mirrors:

- skin/profile,
- gameplay equipment,
- cosmetics and dye,
- held weapon or selected pose,
- scale/pose supported by provider.

It has no collision, AI, hitbox, persistence or visibility to other players. Preview changes exist in `ScenePreviewState`; only Confirm invokes a transaction.

## Scene close triggers

Close and discard uncommitted preview on:

- damage or hostile effect,
- transition to a profile-forbidden hostile state,
- forced movement/teleport/world change,
- knockback/fall/mount/swim/portal,
- inventory close/Exit,
- disconnect/plugin disable/provider failure.

Back returns to Scene Hub page. Exit closes the full session. Closing a subpage never implicitly commits.

## Scene Hub pages

V1 root menu:

- Character & Equipment
- Wardrobe & Dye
- Combat Arts
- Forms
- Magic & Attunement
- Character information
- Exit

The following are not mandatory V1 Scene workflows:

- Crafting
- Market
- Bank
- Stable
- Party

### Character & Equipment

- Equipment slots and comparison.
- Armor load tier.
- Durability and traits.
- Visible attunement/load constraints.
- Qualitative handling/conditioning feedback.

### Wardrobe & Dye

- Cosmetic slots.
- Preview/unequip.
- Dye channel preview.
- Dye Ticket unlock state is stored per cosmetic item UUID.
- Once unlocked, color editing for that item is free.
- Cosmetic and dye state follow the item when traded.

### Combat Arts

- Known techniques.
- Active moveset preview.
- Mastery qualitative bands.
- Preview is allowed outside Rest Context; unrestricted Combat Art confirmation is not globally
  rest-locked.

### Forms

- Known Forms and active Form preview.
- Preview remains available anywhere the Scene can open.
- Confirm validates Rest Context when the Form policy requires it.

### Magic & Attunement

- Known arts/forms.
- Capacity and active load as visible numbers.
- Resonance/conflict explanation.
- Commit requires Rest Context.

### Character information

- Read-only character identity and combat/build summary.
- It has no Preview/Confirm transaction.

## Inventory UI implementation

The world renders behind the inventory window. Resource-pack UI should leave a transparent/empty central area for the preview actor and place controls around edges.

Navigation opens the next inventory on the next scheduler tick after click handling. All menu items are synthetic protected UI items and cannot be taken.

Support test matrix:

- GUI scales 2–4,
- 16:9, 16:10 and ultrawide,
- Thai and English,
- Force Unicode on/off,
- resource pack loaded/failed,
- vanilla and enhanced visual clients.

## HUD

### Essential combat HUD

- HP
- Stamina
- Mana when unlocked
- Flask allocation/remaining charges
- Current ammo and count
- Guard Stability while guarding/damaged
- Enemy HP and posture for current combat focus
- Boss HP/phase
- Ailment buildup and active statuses
- Action rejection cue

### Optional HUD

- Damage numbers
- Detailed buffs/debuffs
- Quest tracker
- Party frames
- Context prompts
- Technique reminder hints

### Accessibility

Settings:

- camera shake intensity,
- flash reduction,
- vignette strength,
- particle density,
- damage numbers,
- subtitle/combat text,
- high-contrast telegraphs,
- sound cue reinforcement,
- colorblind status palettes where asset system permits.

## Resource-pack policy

The pack is required for normal play. On decline or download failure:

- player remains in a restricted lobby/help state,
- no MMO character session enters the world,
- retry and troubleshooting options are shown.

Pack version and SHA must match the active content manifest. Enhanced EMF/ETF features are optional and never required for mechanics.

## Provider failure

If actor, viewpoint or overlay presentation cannot open, Scene recovery closes every partial handle,
records provider failure and refuses the Scene. Inventory-only Compact 2D is not a replacement for
the world-backed Scene.
