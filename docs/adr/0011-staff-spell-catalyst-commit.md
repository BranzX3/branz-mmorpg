# ADR 0011 - Staff Spell and Catalyst Commit Boundary

## Decision

The first magic vertical slice compiles `spell.*` content into an immutable `SpellEngine` registry.
`spell.ember.fire_lance` is a charge cast requiring the `STAFF` and `EMBER` catalyst tags plus two
points of active attunement capacity. The deterministic cast runtime reserves mana at start, permits
release only after the authored minimum charge, commits mana at release and refunds the reservation
when interrupted before commit. The local training adapter supplies an explicit two-point
attunement fixture until persistent build attunement is delivered in its own Milestone 5 slice.

A catalyst is an Item Engine profile containing compatibility tags, bounded channel stability and
durability cost per committed spell. A Staff may own both a weapon profile and a catalyst profile;
this is intentional because the family has an LMB physical chain and RMB casting. Ammo and Quiver
profiles remain incompatible with catalyst profiles.

Catalyst durability belongs to the unique item payload. A legacy payload starts at the active item
definition's base maximum. Fire Lance release first compare-and-sets the equipped Staff item UUID,
version, owner, main-hand location and exact old payload in PostgreSQL. Paper creates no projectile
and mana does not commit until this durable operation succeeds. The resulting Fire projectile uses
the shared authoritative projectile/collision/HP/posture engines and the arcane damage channel, so
vanilla armor does not mitigate it.

## Rationale

The Staff item UUID is the only safe owner of wear across equipment changes, reconnects and server
restarts. Updating its existing JSON payload avoids a parallel durability source of truth. Reserving
mana before the database boundary prevents overspending while still allowing a clean refund when a
cast is cancelled. Delaying projectile creation until after the value mutation follows the same
commit-before-effect rule used by Bow ammunition and Crossbow fire.

Spell phases, requirements, projectile physics, output channel and profiles remain content data.
The magic module contains no Bukkit types; the Paper adapter only translates verified intent and
renders the server result.

## Compatibility and migration

No SQL migration is required. The item schema adds optional `catalyst_profile` fields and the spell
schema adds the first complete charge-projectile contract. Catalyst state is additive JSON under
`catalyst`; legacy item payloads decode as full durability. The example snapshot adds
`weapon.training_staff`, `move.training_staff.primary_1` and `spell.ember.fire_lance`.

Older runtimes preserve unknown JSON but do not enforce catalyst wear or spell mana. Rollback is
therefore safe only with player sessions stopped; an older runtime must not host gameplay against a
database after catalyst durability has begun changing.

## Failure and recovery

The item payload update, audit row and journal terminal state share one PostgreSQL transaction.
Retrying the same operation ID replays one result without additional wear. A stale item version,
changed owner/location, swapped catalyst, broken catalyst or concurrent value mutation rejects
without committing mana or creating a projectile.

If interruption occurs while the database operation is already in flight, the runtime waits for its
terminal result. Success commits mana and wear together but suppresses the live projectile when the
player/session/world is no longer valid; failure refunds the mana reservation. A process crash after
the durable commit behaves like every already-committed projectile at server shutdown: wear remains
committed and no projectile is reconstructed.
