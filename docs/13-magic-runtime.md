# Magic Runtime

## Spell model

Spell definitions compose independent dimensions.

### Cast type

- Instant
- Windup
- Charge
- Channel
- Sustain
- Ritual

### Target type

- Self
- Crosshair entity
- Crosshair point
- Ground area
- Cone
- Projectile
- Tethered ally

### Delivery

- Direct
- Projectile
- Beam
- Zone
- Summon
- Imbue

## Requirements

A spell may require catalyst tags, form, attunement, affinity, knowledge, resource and region/ritual conditions. Requirement failure returns a visible reason; it never silently consumes resources.

## Casting and interruption

Spells use the same ActionTimeline and commit semantics as weapon moves. Definitions declare whether movement, damage, flinch, stagger, silence, weapon swap or loss of target interrupts. Composure and catalyst stability can resist low-tier interruption within bounded caps.

## Targeting

Vanilla-client ground targeting uses the crosshair ray intersection with valid blocks plus a preview marker visible only to the caster. Confirm occurs on release/second input according to spell. Invalid terrain does not consume resources.

Ally-target spells prioritize the crosshair ally, then the lowest-angle party member within the spell cone when soft assistance is enabled. No spell auto-selects through walls.

## Catalysts

Staff, focus, tome and rune catalysts define handling, channel stability, spell tags and resource conversion. They are Item Engine items with durability consumed on committed spell use.

## Attunement and affinity

Attunement limits active supernatural load. Affinity is broad knowledge/comfort that reduces cost modestly and unlocks forms or variants. Resonance grants conditional synergies and conflicts; it never stacks unrestricted flat damage.

## Summons

V1 runtime supports summons but V1 content uses at most one short-lived summon per caster. Summons:

- belong to a character and encounter;
- cannot hold inventory or trade;
- inherit a bounded stat profile;
- obey target caps and despawn on logout, encounter reset or content mismatch;
- cannot generate progression by fighting trivial enemies unattended.

## Safety caps

- area target cap defaults to eight;
- chain target cap defaults to five;
- persistent zones per caster default to two;
- total summon count per caster defaults to one;
- channels have maximum duration and periodic costs.

## V1 content

The runtime is general, while V1 ships one full Ember art and one Runic Imbuement family to validate instant, projectile, zone, channel and weapon-spell integration.
