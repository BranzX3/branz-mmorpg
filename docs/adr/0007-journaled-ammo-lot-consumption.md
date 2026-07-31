# ADR 0007 — Journaled Ammo-Lot Consumption

## Decision

A Bow release must commit one unit from the projectile definition's exact `ammo_category` before
the server creates the projectile. The commit uses the existing PostgreSQL transaction journal and
a version/owner/location compare-and-set update on one deterministic character-inventory lot.
Retrying the same projectile UUID replays the committed journal entry and never subtracts again.

The Paper adapter captures the shot origin, direction, charge and projectile UUID before dispatching
the blocking transaction off the server thread. The action remains locked in `AMMO COMMITTING`
until the main-thread completion. A successful commit refreshes the immutable character snapshot
and Bukkit projection without re-running initial character-ready handlers, then launches the
captured shot. Missing ammo, stale state or database failure creates no projectile.

## Rationale

Ammo is authoritative value and the owning item specification makes projectile release its commit
point. Launching first would permit free shots when persistence fails; blocking the Paper thread
would violate the runtime boundary. Using the projectile UUID as transaction identity gives the
shot, journal and audit row one stable idempotency key while preserving deterministic lot choice.

An exhausted lot remains as a zero-quantity `DESTROYED` tombstone with its owner, lineage and last
transaction. It is excluded from inventory projection and normal ammo selection but remains
available to audit and reconciliation.

## Compatibility and migration

No SQL migration is required. `commodity_lot.quantity`, version and free-text location columns
already support the atomic update; `DESTROYED` is an additive runtime-recognized location value.
The public persistence API adds `consumeLot` and `VALUE_INSUFFICIENT_QUANTITY`. Older runtime code
continues to read non-exhausted rows and must not be deployed after rows begin using `DESTROYED`.

Rollback is code-first only after active sessions stop. Destroyed rows must remain intact; a
rollback tool may treat them as historical non-projectable rows but must never restore their
quantity. Prepared-Quiver state, ammo cycling and encounter recovery require their own additive
persistence contract later.

## Failure and recovery

Mutation, audit append and journal commit share one database transaction. A crash before commit
rolls back quantity and journal together; a retry then performs one mutation. A crash after commit
replays the terminal entry. Disconnect, death or world change while the asynchronous commit is in
flight cancels transient projectile launch; if the database commit already won, the released ammo
remains consumed because release was irrevocable.
