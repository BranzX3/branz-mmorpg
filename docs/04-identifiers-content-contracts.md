# Identifiers and Content Contracts

## Stable identifiers

IDs are lowercase dotted namespaces:

```text
weapon.greatsword.iron_wolf
move.greatsword.rising_cleave
spell.ember.lance
status.burn
scene.character.hub
city.frostpeak
trade.frostpeak.steel_crate
lifeskill.mining
```

Display names and localization keys may change; stable IDs do not.

## Definition and instance identity

- Definition ID describes a type of thing.
- Item UUID identifies one durable/unique instance.
- Lot UUID identifies one stackable commodity lot.
- Mount UUID identifies one mount.
- Worker UUID identifies one hired worker.
- Transaction ID identifies one atomic value operation.
- Encounter ID identifies one combat encounter instance.

## Content snapshots

At runtime, parsed definitions form an immutable `ContentSnapshot`:

```text
contentVersion
schemaVersion
gitCommit
resourcePackHash
definition registries
compatibility ranges
```

A character action stores the snapshot version it used when the action can outlive a tick, such as an encounter, crafting batch, worker job, market listing or quest session.

## Compatibility

Definitions declare minimum plugin schema. Startup fails before players load when content is incompatible. Removed IDs require aliases and migration windows. Unknown persisted items become quarantined placeholders retaining UUID, ownership and serialized payload.

## Validation classes

- Syntax: type, range and required fields.
- References: every referenced ID exists and is the correct category.
- Invariants: cosmetic has no combat stats, parry windows cannot be enhanced, trade cargo cannot be marketable.
- Budget: move timing, spell target count, status strength and reward value remain within global caps.
- Migration: persistent changes have deterministic conversions.
