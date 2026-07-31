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
- not Engaged/Alert,
- action state is Idle,
- grounded and not falling/swimming/flying/mounted/in portal,
- not in exclusive dialogue/cutscene/transaction,
- region permits Scene,
- no hostile entity currently has aggro within 16 blocks,
- preview provider or compact fallback is available.

Selecting slot 9 triggers weapon sheathing. RMB opens only after sheathing completes.

## Local Scene

The everyday Scene Hub does not teleport the real player.

1. Freeze combat inputs; inventory UI naturally captures controls.
2. Save return UI/slot/camera presentation state.
3. Find an owner-visible preview location 2.75 blocks in front.
4. If blocked, test candidate yaw offsets: `0, +35, -35, +70, -70, 180` degrees.
5. Validate floor, headroom and line of sight.
6. Spawn owner-only preview actor facing the viewer.
7. Apply soft local presentation light/particles where supported.
8. Open the custom inventory UI.

If no full-body location exists, use Compact Preview at 1.6 blocks with upper-body framing. If even compact mode is invalid, reject opening with a short message. V1 never teleports to a Scene Pod for the normal Chronicle menu.

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
- Engagement/Alert transition,
- movement/teleport/world change,
- knockback/fall/mount/swim/portal,
- inventory close/Exit,
- disconnect/plugin disable/provider failure.

Back returns to Scene Hub page. Exit closes the full session. Closing a subpage never implicitly commits.

## Scene Hub pages

V1 root menu:

- Character & Equipment
- Wardrobe & Dye
- Combat Arts
- Magic & Attunement
- Journal & Pending Rewards
- Settings & Help
- Exit

Contextual services remain world-bound:

- Rest preparation
- Blacksmith
- Alchemy
- Bank/trade
- Teaching

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
- Changes disabled outside Rest Context with explanation.

### Magic & Attunement

- Known arts/forms.
- Capacity and active load as visible numbers.
- Resonance/conflict explanation.
- Commit requires Rest Context.

### Journal & Pending Rewards

- Quest journal.
- Knowledge/Codex entries.
- Pending reward claims.
- Renown/titles may be a subpage.

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

## Presentation fallback

If advanced packet/preview features fail:

- Scene opens in compact 2D equipment UI.
- Gameplay remains available.
- Admin health status records provider failure.
- No transaction is attempted from an invalid preview session.
