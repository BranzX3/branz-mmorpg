# Scene, UI, HUD and Accessibility

## Chronicle

Hotbar slot 9 contains `Adventurer's Chronicle`. Selecting it begins weapon sheathe. Right-click opens the Local Scene Hub when the player is grounded, stationary enough, not ENGAGED/ALERT, not mounted, not swimming/falling/portal-traveling and not in a restricted region.

## Local Scene Hub

The real player stays at the current world position. A packet Preview Actor mirroring skin, equipment and cosmetics appears approximately 2.5–3 blocks in a validated visible position. Inventory UI provides controls while the world remains the background.

No teleport is used for daily Scene Hub. Fixed studio pods are reserved for character creation and authored cinematic appearance services.

## Scene modes

- Hub
- Equipment
- Wardrobe/Dye
- Combat Arts
- Magic/Attunement
- Journal/Knowledge
- Renown/Titles
- Settings

The Scene session remains alive while modes change. Preview state is separate from committed equipment/cosmetic state.

## Safety

Damage, hostile target acquisition within danger threshold, knockback, teleport, mount state change, death, world unload or disconnect closes Scene and discards uncommitted preview. Real player is never invulnerable.

If no valid full-body preview position exists, use a compact bust preview. If neither is valid, show a clear refusal; do not unpredictably teleport to a pod.

## Transaction controls

`Preview` changes actor only. `Confirm` validates ownership/location and commits through TransactionService. `Back` discards the current mode preview and returns to Hub. `Exit` discards all uncommitted state.

## HUD

Essential HUD:

- HP, Stamina, Mana when unlocked;
- Flask allocation/charges;
- selected ammo and count;
- active consumable/status cues;
- enemy HP and posture;
- boss phase/major telegraph cues;
- Guard Stability while guarding;
- party/downed state;
- action rejection reason.

Optional HUD:

- damage numbers;
- quest tracker;
- market/freight/worker notifications;
- lifeskill work progress;
- route/cargo condition.

## Accessibility

Settings include camera shake, flash intensity, vignette, particle density, damage numbers, hold/toggle preferences where feasible, text size/profile, high-contrast telegraphs, audio cue strength and tutorial replay. Gameplay telegraphs never rely solely on color.

## Resource pack

The resource pack is required for normal play. Decline/failure places the character in a limited lobby with retry/help; gameplay worlds are not entered with missing essential UI/assets. Pack hash/version is validated at session start.
