# Scene, UI, HUD and Accessibility

## Scene definition

A Scene is a world-backed presentation session. It is not an inventory menu. A Scene composes:

- a world scene or authored environment;
- the real player actor or an owner-only preview actor visible in the world;
- camera/viewpoint configuration;
- actor and environment lifecycle;
- an Inventory or Dialogue control overlay;
- interruption and recovery lifecycle.

The overlay routes controls but never owns the actor, camera, environment or authoritative state.

## Scene architecture

`SceneEngine` orchestrates a `SceneProfile` and one `SceneSession` per player. Opening acquires the
environment, actor, viewpoint and overlay in that order. Closing releases overlay, viewpoint, actor
and environment in reverse order through idempotent `SceneRecovery`.

`SceneProfile` defines topology, entry mode, supported modes, movement ownership and per-mode
interaction semantics. `SceneSession` stores identity, profile, active mode, lifecycle phase,
revision and feature-owned preview state where applicable. Provider callbacks must carry the
session identity; stale callbacks cannot mutate a newer session.

Supported interaction models:

- `READ_ONLY`: presentation without authoritative mutation;
- `PREVIEW_COMMIT`: isolated draft, actor preview, validation and explicit transaction;
- `IMMEDIATE_ACTION`: each action commits independently and idempotently;
- `DIALOGUE`: dialogue state and authored choices;
- `CINEMATIC`: authored environment, actor and camera timeline.

Preview/Confirm is not a universal Scene behavior.

## Local Character Scene Hub

Hotbar slot 9 contains `Adventurer's Chronicle`. Selecting it begins weapon sheathe. Right-click
requests the Local Character Scene when the character/resource pack is ready, the player is not
`ENGAGED`, and the current status permits Scene admission.

The real player remains at the current world position and is never made invulnerable. An owner-only
Paper Mannequin mirrors skin and equipment in a validated visible position in front of the player.
The viewpoint frames that actor without teleporting the player. The Inventory overlay leaves the
actor viewport visually clear and provides controls around it.

Normal movement input is locked while the Scene is active; normal movement input does not close the
Scene. Damage, forced movement, teleport, world change, death, disconnect, actor/session
invalidation or provider failure closes the Scene and invokes recovery.

V1 Local Character modes:

- Equipment — `PREVIEW_COMMIT`;
- Wardrobe/Dye — `PREVIEW_COMMIT`;
- Combat Arts — `PREVIEW_COMMIT`;
- Forms — `PREVIEW_COMMIT`, Rest Context checked at Confirm when required;
- Magic/Attunement — `PREVIEW_COMMIT`, Rest Context checked at Confirm;
- Character information — `READ_ONLY`.

The Scene Hub itself does not require Rest Context. A mode may freely preview outside Rest Context;
only Confirm of a rest-locked mutation is rejected. Equipment and unrestricted Combat Art changes
do not inherit a global Rest lock.

Existing Flask, Journal and Settings inventory pages are compatibility overlays, not mandatory V1
Scene modes and not precedent for expanding Scene scope.

## Fixed Private Scene

Fixed Private Scenes own a validated private environment and return policy. V1 uses them for:

- Character creation — `PREVIEW_COMMIT`;
- Appearance preview — `PREVIEW_COMMIT` or `READ_ONLY` according to the profile.

Any return anchor is profile-owned and must be validated before recovery restores the player.

## Narrative Scene

Narrative Scenes use authored environment, actor and camera presentation for:

- important dialogue — `DIALOGUE`;
- cutscenes — `CINEMATIC`.

Dialogue or cinematic overlays may expose choices, continue and skip controls without becoming the
Scene renderer.

## V1 scope boundary

Crafting, Market, Bank, Stable and Party are not mandatory Scene workflows in V1. They remain their
own world or UI workflows unless a later requirement explicitly assigns a Scene profile.

## Preview and transaction authority

In a `PREVIEW_COMMIT` mode, preview changes update only the presentation actor. Confirm revalidates
ownership, content version, mode-specific location requirements and current authoritative state
before committing. Back discards the current draft. Exit discards every uncommitted draft.

If a durable commit has already started when interruption occurs, recovery must not pretend to roll
it back. The transaction completes or reconciles through its owning feature, while stale Scene
callbacks are ignored and the live character projection reloads from authoritative truth.

## Recovery

Recovery must be idempotent and safe from partial opening. It:

1. rejects new actions and stale callbacks;
2. closes the control overlay;
3. detaches/restores the viewpoint;
4. removes the preview actor;
5. releases the environment;
6. releases movement/input ownership;
7. discards uncommitted preview;
8. reconciles any in-flight authoritative transaction.

Local Scene recovery never teleports the player back after forced movement. Fixed Private recovery
uses only its validated return policy.

## HUD and accessibility

Combat HUD is a separate presentation channel, not a Scene overlay. Essential HUD includes HP,
Stamina, Mana, Flask charges, ammo, Guard Stability, enemy HP/posture, boss phase, ailments and clear
action rejection feedback.

Settings include camera shake, flash intensity, vignette, particle density, damage numbers,
hold/toggle preferences, text profile, high-contrast telegraphs, audio cue strength and tutorial
replay. Gameplay telegraphs never rely solely on color.

## Resource pack

The resource pack is required for normal play and supplies the overlay layout that preserves the
central actor viewport. Decline/failure places the character in a limited lobby with retry/help;
gameplay worlds do not start with missing essential Scene/HUD assets.
