# Resources, Flask, Consumables and Status

## HP

- Reference maximum: 1,000 before bounded equipment effects.
- Natural combat regeneration: none.
- Out-of-combat regeneration begins after 10 seconds without engagement and restores 2% max HP per second only in safe/rest contexts.
- Open-world passive regeneration outside rest contexts is 0.5% per second after 20 seconds, stopping at 80% HP.
- Healing sources are Flask, magic, consumables, rest and authored effects.

Self-HP-cost actions cannot reduce the user below 1 HP. They are rejected if the required cost cannot be paid safely.

## Stamina

- Base maximum: 100.
- Regen delay after stamina spend: 20 ticks.
- Regen: 18 per second while normal, 10 while walking with guard-ready posture, zero while sprinting/guarding/channeling unless modified.
- At zero stamina, actions requiring stamina are rejected and `EXHAUSTED` lasts 20 ticks after stamina becomes positive.
- Negative stamina is not allowed.

Reference costs:

- Light dodge: 24.
- Medium dodge: 28.
- Heavy dodge: 32.
- Light attack: 6–10.
- Heavy attack: 16–28.
- Greatsword committed technique: 25–40.

Endurance and mastery together may reduce a cost by at most 15%.

## Mana

Mana exists after the character learns a mana-using foundation.

- Base maximum once unlocked: 100.
- Combat regeneration: 2 per second after a 40-tick delay.
- Out-of-combat regeneration: 8 per second after a 20-tick delay.
- Focus Channel may restore up to 12 per second but locks action, slows movement and is interruptible.
- Action-based recovery may be authored, but total burst recovery is capped by profile.
- At zero mana, spells reject cleanly; no negative mana.

## Attunement

Attunement is described in `08-builds-weapons-forms.md`. It is visible capacity and does not regenerate.

## Expedition Flask

Each character owns one non-tradeable persistent Flask profile. The hotbar Flask item is a representation of that profile.

### Charges

- Base total charges: 6.
- V1 progression cap: 9.
- At Rest Context, allocate each charge as Healing, Mana or Stamina.
- Allocation changes are free; refilling missing charges consumes Infusion Stock or NPC service.

Effects per charge:

- Healing: restore 35% max HP over 40 ticks; first 20% applies at commit, remainder over time.
- Mana: restore 45% max mana over 30 ticks.
- Stamina: restore 70 stamina immediately at commit and reduce exhaustion by 10 ticks.

### Use timeline

- Windup: 12 ticks.
- Commit: tick 12.
- Recovery: 10 ticks.
- Walking allowed at 60% speed.
- Sprint, dodge, jump, stagger or weapon attack cancels before commit.
- Before commit: no charge spent.
- After commit: charge spent and recovery remains.

### Refill and boss retries

Preparing a full Flask at a Rest Context consumes the required stock once and creates a `prepared_flask_snapshot` for the current expedition/checkpoint.

- Open-world death does not refill spent charges.
- If all Healing charges are zero at respawn, Mercy grants one temporary Healing charge that disappears at the next Rest Context.
- A registered boss checkpoint restores the prepared snapshot on a full encounter wipe without consuming additional stock.
- Leaving the checkpoint expedition or changing allocation creates a new preparation and consumes stock as needed.

This keeps boss learning affordable without removing the open-world supply sink.

## Normal consumables

All hotbar slots 1–8 may hold consumables. Inventory remains normal and can be opened while Engaged.

Categories:

```text
BODY_TONIC
ELEMENTAL_WARD
WEAPON_COATING
UTILITY
REMEDY
BOMB
```

Only one active effect per buff category. Applying a new effect replaces the old effect after confirmation in safe UI; in combat, the new effect simply replaces it and remaining duration is lost.

- Finished consumables do not expire.
- Normal ingredients do not rot.
- `Fresh` is a source/state tag used for recipes, not a real-time timer.
- `Unstable` concoctions may expire on rest, death or expedition end and must state that clearly.
- Potions do not cure immediate CC such as Stagger, Knockdown or Guard Break.

Default stack limits:

- Flask representation: 1.
- Potion/remedy/tonic: 3 per stack.
- Bomb: 5.
- Coating: 3.
- Common material: 64 unless definition overrides.

## Status framework

Ailments use buildup. When buildup reaches threshold, the active status triggers and buildup resets or carries overflow according to definition.

V1 ailments:

### Burn

Damage over time; repeated buildup extends duration within cap. Countered by Fire resistance, roll/douse interactions and Burn remedy.

### Bleed

Stores wound stacks; movement-heavy actions trigger bursts. Countered by bandage/remedy and reduced exertion.

### Poison

Longer damage over time and reduced received healing. Countered by antidote and Poison resistance.

### Frost

Reduces stamina regeneration and movement responsiveness; threshold burst briefly roots normal enemies but converts to slow/posture pressure on bosses.

### Shock

Increases guard pressure taken and may interrupt channels at high intensity. Countered by grounding ward.

### Corruption

Reduces maximum recoverable HP temporarily and interacts with Void magic. Countered by specialized cleansing at higher tiers.

## Status rules

Each definition declares:

- threshold,
- buildup decay delay/rate,
- duration,
- stack/reapplication behavior,
- resistance mapping,
- cleanse tags,
- boss conversion,
- PvP coefficient,
- persistence policy.

Default:

- Buildup begins decaying 60 ticks after last application.
- Active status is cleared on death.
- Open-world logout stores remaining active duration up to 10 minutes; arena statuses clear at match end.
- Every ailment has at least one non-potion counterplay method.

## Economy role

NPCs sell basic Infusion Stock and weak remedies at a price ceiling. Player alchemists create specialized cures, preventive tonics, coatings, utility items and tradeoff elixirs. Mandatory universal damage potions are not part of V1.
