# ADR 0014: Ember Art and Runic Imbuement Runtime

Status: Accepted

## Context

Fire Lance established the catalyst durability and commit-before-effect boundary, but Milestone 5
also requires a representative Ember art across common Action RPG deliveries and one Runic
Imbuement family. Those effects must share mana, attunement, interruption and server-owned target
authority instead of becoming Paper-only special cases.

## Decision

- The V1 training set contains Cinder Snap (`INSTANT`/`DIRECT`), Fire Lance
  (`CHARGE`/`PROJECTILE`), Scorching Ground (`WINDUP`/`ZONE`), Flame Torrent
  (`CHANNEL`/`BEAM`) and Runic Ember Edge (`INSTANT`/`IMBUE`).
- `SpellCastEngine` owns mana reservation, commit, windup/charge readiness, channel pulse upkeep,
  bounded pulse count, recovery and pre-commit refund. `SUSTAIN` and `RITUAL` remain outside the
  normal combat runtime and fail explicitly.
- With a Staff ready, F cycles only spells in the committed attunement set. RMB starts the selected
  spell, releases a ready Charge, or stops an active Channel. Chronicle remains the authority for
  changing the attunement set at a Rest Context.
- Every spell commit compare-and-sets the equipped catalyst payload before any projectile, direct
  hit, zone, beam pulse or Imbuement is made visible. One successful spell commit consumes one
  authored catalyst durability; channel pulses then pay their own authored mana upkeep.
- Projectiles reuse swept server collision. Direct and Beam deliveries use one server ray target in
  V1. Zones use a geometry-free deterministic pulse clock, server area query, stable distance/UUID
  ordering, an authored target cap and a maximum of four active zones per caster.
- Runic Ember Edge is an encounter-scoped coating with authored duration and charges. A qualifying
  physical hit consumes exactly one charge and adds a separately resolved Fire damage/posture
  packet based on weapon power. It never multiplies the physical packet. Weapon swap, death,
  forced teleport, logout or expiry clears the transient coating.
- Active Form mana scaling applies to both the initial spell cost and channel pulse upkeep.

## Failure and recovery

Insufficient mana/attunement, incompatible or broken catalyst, active action lock, projectile/zone
cap, movement interruption or stale catalyst CAS rejects without creating an effect. Cancellation
before commit releases reserved mana. If interruption races a database commit, the durable catalyst
wear and mana commit remain terminal but no live effect is fabricated for an invalid session.

Committed projectiles, zones, channels and Imbuements are encounter state and are not reconstructed
after process restart. Durable Staff UUID/catalyst wear, equipment, Quiver lots, Crossbow checkpoint
and character build/attunement are restored from PostgreSQL, so reconnect cannot duplicate a
transient effect or reset its paid durable inputs.

## Compatibility and migration

No SQL migration is required. The spell schema adds optional direct, channel, zone and Imbuement
profiles with bounded units. The example manifest grows from one to five spells and 30 to 34 total
definitions. Older runtimes do not understand these additive delivery profiles and must not host
the same active content snapshot.
