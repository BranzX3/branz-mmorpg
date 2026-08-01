# Magic Runtime

## Principles

- Magic uses the same action/state, hit, resource and interruption systems as weapons.
- Learning an art grants knowledge; attunement and catalyst determine active access.
- V1 magic must be usable on a vanilla client without a custom targeting screen.
- Spells are data-driven and server-authoritative.
- A Spell must be permanently learned from its authored source before it can consume Attunement or
  become the selected cast; catalyst ownership alone never grants the art.

## Spell definition

```yaml
id: spell.ember.fire_lance
art: magic.ember
cast_type: CHARGE
target_type: CROSSHAIR
delivery: PROJECTILE
requirements:
  catalyst_tags: [STAFF, FIRE_FOCUS]
  attunement: 2
cost:
  mana: 18
phases:
  windup_ticks: 8
  minimum_charge_ticks: 8
  maximum_charge_ticks: 30
  recovery_ticks: 12
interruption:
  flinch: true
  poise_threshold_multiplier: 0.75
projectile: projectile.fire_lance
impact: impact.fire_lance
```

## Cast types

- `INSTANT` — commits immediately after a short validation/startup.
- `WINDUP` — fixed cast time then release.
- `CHARGE` — release between minimum and maximum charge.
- `CHANNEL` — repeated effect while held and resources remain.
- `SUSTAIN` — toggled/persistent effect with upkeep and cancellation rules.
- `RITUAL` — Rest/World interaction, not normal combat casting.

## Target types

- `SELF`
- `CROSSHAIR`
- `ENTITY_HOSTILE`
- `ENTITY_PARTY`
- `GROUND`
- `AREA_AROUND_SELF`
- `CONE`

Ground targeting uses a server ray to the first valid surface. A short owner-only marker previews the point during windup. The player confirms by release/commit; there is no mouse cursor mode.

## Delivery types

- `DIRECT`
- `PROJECTILE`
- `BEAM`
- `ZONE`
- `IMBUE`
- `SUMMON_TEMPORARY`

V1 temporary summons are encounter-scoped entities with no persistent inventory, trading or autonomous progression. Maximum two controlled summons per character, with a global encounter cap.

## Catalysts

Catalyst compatibility is tag-based:

- Staff: full spellcasting and physical staff chain.
- Focus off-hand: enables compatible sword/focus hybrid spells.
- Talisman/accessory: passive or limited ritual access.

Catalyst durability decreases when a spell commits. A broken catalyst disables catalyst-required spells.

## Staff input model

- LMB: physical/magical staff core chain.
- RMB press/hold/release: active art's primary cast behavior.
- F: signature spell/technique.
- Q: utility spell.
- Directional branches: art-specific mobility or close-range variants.

The moveset system still owns branch replacement. Magic does not create a second skill bar.

## Interruption

A spell declares which effects interrupt it.

Default:

- Flinch interrupts fragile casts.
- Stagger and higher interrupt all normal casts.
- Dodge cancels unless the spell has an authored dodge-cast window.
- Normal health damage alone does not interrupt if poise/composure prevents the required control state.
- Silence prevents new spell starts and ends channels, but does not remove already committed projectiles/zones.

Composure and equipment may improve interruption threshold up to the caps in progression specs. They do not make a cast universally uninterruptible.

## Mana reservation and commit

- Windup/charge reserves mana.
- Mana is spent at commit/release.
- Cancel before commit returns reservation.
- Channel pays an initial commit and then per pulse.
- If upkeep cannot be paid, the sustain/channel ends cleanly.

## Resonance and affinity

Affinity is broad familiarity with an art and unlocks content/qualitative efficiency. Resonance is an active-build interaction between tags.

Examples:

- Ember weapon coating + Fire Lance may enable an explosion follow-up.
- Frost form + Burn effect may create Steam, clearing both and creating concealment.
- Void and healing tags may conflict, increasing attunement or reducing efficiency.

Resonance must declare both benefit and cost/condition. It is not an uncontrolled multiplicative damage stack.

## Element interactions

V1 supports a small authored table:

- Fire removes Frost buildup and may create Steam zones.
- Frost suppresses Burn duration but takes a burst of posture when rapidly thawed.
- Shock is stronger against Wet targets and may chain only within strict target caps.
- Void/Corruption interactions affect recoverable HP but never permanently reduce character maximum HP.

Interactions are definition-driven events with recursion depth capped at 2 to prevent infinite chains.

## Healing magic

- Party targeting uses `PARTY_CROSSHAIR` or area rules.
- Healing generates encounter contribution and threat.
- Arena healing uses the PvP multiplier.
- Repeated external healing may trigger a short diminishing-return profile on the recipient to prevent invulnerable stacking.
- V1 has no combat resurrection and no downed state.

## Safety limits

- Maximum active zones per caster: 4.
- Maximum active projectiles per caster: 32; excess rejects or replaces oldest according to spell definition.
- Maximum chain targets: 5.
- Maximum temporary summons per caster: 2.
- Area target checks are distributed and budgeted; no per-tick full-world scans.

## Milestone 5 training implementation

The `newmmo` training snapshot exercises the normal combat runtime with Cinder Snap
(`INSTANT`/`DIRECT`), Fire Lance (`CHARGE`/`PROJECTILE`), Scorching Ground
(`WINDUP`/`ZONE`), Flame Torrent (`CHANNEL`/`BEAM`) and Runic Ember Edge
(`INSTANT`/`IMBUE`). With a Staff ready, F cycles the character's committed attuned spells and RMB
starts, releases or stops the selected cast.

Direct and Beam are single-target in this V1 adapter. Zones retain the global four-per-caster cap,
authored pulse/target bounds and deterministic server ordering. Runic Ember Edge is an
encounter-scoped four-charge coating: each physical target hit consumes one charge and resolves a
separate Fire packet. Live effects clear on session/encounter invalidation; catalyst wear and the
attuned character build remain durable.
