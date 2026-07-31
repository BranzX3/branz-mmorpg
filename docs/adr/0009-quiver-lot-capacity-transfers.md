# ADR 0009 — Quiver Lot Capacity Transfers

## Decision

Ammo carried for ranged use occupies the authoritative `QUIVER` lot location. Its location reference
is the equipped Quiver item UUID. Inventory lots cannot be consumed by Bow release; a player must
preview and explicitly confirm a compatible lot transfer in Scene first.

One `lot.transfer` journal transaction locks the equipped Quiver item version and source lot version,
then checks capacity and applies either a full move or partial split. Full moves retain the source lot
UUID. Partial moves decrement the source and create a deterministic child lot whose lineage records
the parent lot UUID, split transaction UUID and complete parent lineage. Withdrawal uses the same
operation in reverse and projects at most 64 units into one verified-free inventory slot per commit.

The training Quiver has capacity 96. Capacity is the sum of positive lots owned by the character at
`QUIVER:<quiver-item-uuid>`. It is checked inside the PostgreSQL transaction after acquiring the
Quiver item row as a serialization lock. Preparation payload edits use the same item version, so a
transfer and preparation change cannot overwrite one another.

## Rationale

The Quiver item UUID is the stable container identity: swapping equipment naturally switches both
prepared state and stored lots without character-global shadow state. Locking the item row prevents
two different source lots from racing past capacity even though their own CAS versions do not
conflict. Preserving lot identity on full moves and explicit lineage on splits maintains auditability
without merging unrelated provenance.

Scene retains transfer intent only in memory. Back, Exit, damage, teleport, disconnect or crash drops
the preview; only Confirm invokes the value transaction. Database truth is reloaded before Bukkit
inventory projection is reconciled.

## Compatibility and migration

No SQL migration is required. Location types are stored as text and `QUIVER` is an additive
runtime-recognized value. The persistence API adds `LotQuantityTransfer`, `lot.transfer`, capacity and
destination-occupied errors. Reconciliation recognizes `QUIVER` as valid. Runtime versions that do
not recognize this location must not be deployed after ammo transfers begin because they would
quarantine valid lots or make them unavailable.

Rollback requires stopping active sessions. Stored lots remain authoritative and must be moved back
to inventory with a compatible recovery tool or runtime before deploying older code; they must never
be copied or quantity-restored manually.

## Failure and recovery

Quiver lock, source decrement/move, child insert, audit append and journal commit share one database
transaction. A crash before commit rolls all of them back. Retrying the idempotency key replays one
terminal result and never creates a second child. Stale source/container versions, changed ownership
or location, full capacity and occupied withdrawal slots reject without a journal residue or local
projection change.

Stored lots remain attached to an unequipped Quiver UUID and return when that same item is equipped.
Cross-owner Quiver transfer, lot merging, trade/container ownership propagation, encounter recovery
and Pending Rewards overflow are later contracts.
